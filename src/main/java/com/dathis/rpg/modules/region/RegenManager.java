package com.dathis.rpg.modules.region;

import com.dathis.rpg.RpgPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Возврат сломанных блоков через время. Очередь хранится в data/regions.yml (раздел regen),
 * поэтому после рестарта сервера блоки всё равно вернутся. Задача раз в секунду.
 */
final class RegenManager implements Runnable {

    private record Pending(String world, int x, int y, int z, long due, String data) {
        String key() {
            return key(world, x, y, z);
        }

        static String key(String world, int x, int y, int z) {
            return world + ";" + x + ";" + y + ";" + z;
        }
    }

    private final RpgPlugin plugin;
    /** Позиция → что вернуть. Одна позиция ждёт только один возврат (первый, настоящий блок). */
    private final Map<String, Pending> pending = new LinkedHashMap<>();
    private boolean dirty;
    private int ticksSinceSave;

    RegenManager(RpgPlugin plugin) {
        this.plugin = plugin;
    }

    void load() {
        pending.clear();
        for (String line : plugin.store().get(AreaRegistry.FILE).getStringList("regen")) {
            String[] p = line.split(";", 6);
            if (p.length != 6) {
                continue;
            }
            try {
                Pending item = new Pending(p[0], Integer.parseInt(p[1]), Integer.parseInt(p[2]),
                        Integer.parseInt(p[3]), Long.parseLong(p[4]), p[5]);
                pending.put(item.key(), item);
            } catch (NumberFormatException ignored) {
                // повреждённая строка — пропускаем
            }
        }
    }

    void save() {
        List<String> lines = new ArrayList<>();
        for (Pending p : pending.values()) {
            lines.add(p.key() + ";" + p.due() + ";" + p.data());
        }
        plugin.store().get(AreaRegistry.FILE).set("regen", lines);
        plugin.store().save(AreaRegistry.FILE);
        dirty = false;
        ticksSinceSave = 0;
    }

    /** Запомнить блок, чтобы вернуть его через seconds секунд. */
    void schedule(Block block, BlockData original, int seconds) {
        String key = Pending.key(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
        if (pending.containsKey(key)) {
            return;
        }
        pending.put(key, new Pending(block.getWorld().getName(), block.getX(), block.getY(), block.getZ(),
                System.currentTimeMillis() + seconds * 1000L, original.getAsString()));
        dirty = true;
    }

    int count(Area area) {
        int n = 0;
        for (Pending p : pending.values()) {
            if (inside(area, p)) {
                n++;
            }
        }
        return n;
    }

    /** Вернуть сразу все блоки этой зоны. */
    int restoreNow(Area area) {
        int n = 0;
        Iterator<Pending> it = pending.values().iterator();
        while (it.hasNext()) {
            Pending p = it.next();
            if (inside(area, p) && restore(p)) {
                it.remove();
                n++;
            }
        }
        if (n > 0) {
            save();
        }
        return n;
    }

    @Override
    public void run() {
        long now = System.currentTimeMillis();
        Iterator<Pending> it = pending.values().iterator();
        while (it.hasNext()) {
            Pending p = it.next();
            if (p.due() <= now && restore(p)) {
                it.remove();
                dirty = true;
            }
        }
        // Пишем на диск не чаще раза в 10 секунд, чтобы не дёргать файл при каждом ударе киркой
        if (dirty && ++ticksSinceSave >= 10) {
            save();
        }
    }

    private boolean inside(Area area, Pending p) {
        if (!area.world.equals(p.world())) {
            return false;
        }
        return area.wholeWorld || (p.x() >= area.minX && p.x() <= area.maxX && p.y() >= area.minY
                && p.y() <= area.maxY && p.z() >= area.minZ && p.z() <= area.maxZ);
    }

    /** false — мир или чанк не загружен, попробуем позже. */
    private boolean restore(Pending p) {
        World world = Bukkit.getWorld(p.world());
        if (world == null || !world.isChunkLoaded(p.x() >> 4, p.z() >> 4)) {
            return false;
        }
        BlockData data;
        try {
            data = Bukkit.createBlockData(p.data());
        } catch (IllegalArgumentException e) {
            return true; // блок из другой версии игры — просто забываем
        }
        Block block = world.getBlockAt(p.x(), p.y(), p.z());
        block.setBlockData(data, false);
        // Если кто-то стоит на месте блока, поднимаем его наверх, чтобы не застрял
        BoundingBox box = BoundingBox.of(block);
        for (Entity entity : world.getNearbyEntities(box)) {
            if (entity instanceof LivingEntity && entity.getBoundingBox().overlaps(box)) {
                Location to = entity.getLocation();
                to.setY(block.getY() + 1);
                entity.teleport(to);
            }
        }
        return true;
    }
}
