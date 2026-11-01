package com.dathis.rpg.modules.mob;

import com.dathis.rpg.RpgPlugin;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Зоны спавна (data/zones.yml) и их работа: раз в секунду каждая зона, рядом с которой есть игрок,
 * досоздаёт мобов до нужного количества. Убитый моб возвращается через время респавна зоны.
 */
final class ZoneManager implements Listener, Runnable {

    private static final String FILE = "zones";
    /** Зона работает, только если игрок ближе этого расстояния (в блоках) к её краю. */
    private static final double ACTIVE_DISTANCE = 64;
    /** Насколько далеко от зоны ещё считаем «её» мобов (они могут отойти, преследуя игрока). */
    private static final double COUNT_MARGIN = 48;

    private final MobModule module;
    private final RpgPlugin plugin;
    private final NamespacedKey zoneKey;
    private final Map<String, SpawnZone> zones = new LinkedHashMap<>();

    ZoneManager(MobModule module) {
        this.module = module;
        this.plugin = module.plugin();
        this.zoneKey = new NamespacedKey(plugin, "zone_id");
    }

    void load() {
        zones.clear();
        ConfigurationSection root = plugin.store().get(FILE).getConfigurationSection("zones");
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            SpawnZone zone = section == null ? null : SpawnZone.load(id, section);
            if (zone != null) {
                zones.put(id, zone);
            }
        }
    }

    void save() {
        YamlConfiguration yaml = plugin.store().get(FILE);
        yaml.set("zones", null);
        ConfigurationSection root = yaml.createSection("zones");
        for (SpawnZone zone : zones.values()) {
            zone.save(root.createSection(zone.id));
        }
        plugin.store().save(FILE);
    }

    SpawnZone create(String mobId, Location a, Location b) {
        int n = 1;
        while (zones.containsKey("zone" + n)) {
            n++;
        }
        SpawnZone zone = new SpawnZone("zone" + n, mobId, a.getWorld().getName(), a, b);
        zones.put(zone.id, zone);
        save();
        return zone;
    }

    /** Удаляет зону и её живых мобов (в загруженных чанках). */
    void delete(SpawnZone zone) {
        killMobs(zone);
        zones.remove(zone.id);
        save();
    }

    void deleteForMob(String mobId) {
        for (SpawnZone zone : forMob(mobId)) {
            delete(zone);
        }
    }

    List<SpawnZone> forMob(String mobId) {
        List<SpawnZone> list = new ArrayList<>();
        for (SpawnZone zone : zones.values()) {
            if (zone.mobId.equals(mobId)) {
                list.add(zone);
            }
        }
        return list;
    }

    SpawnZone get(String id) {
        return zones.get(id);
    }

    int killMobs(SpawnZone zone) {
        List<Entity> mobs = mobsOf(zone);
        mobs.forEach(Entity::remove);
        zone.pendingRespawns.clear();
        return mobs.size();
    }

    int aliveCount(SpawnZone zone) {
        return mobsOf(zone).size();
    }

    private List<Entity> mobsOf(SpawnZone zone) {
        World world = zone.bukkitWorld();
        List<Entity> result = new ArrayList<>();
        if (world == null) {
            return result;
        }
        BoundingBox area = zone.box().expand(COUNT_MARGIN);
        for (Entity entity : world.getNearbyEntities(area)) {
            if (entity.isValid() && zone.id.equals(entity.getPersistentDataContainer().get(zoneKey, PersistentDataType.STRING))) {
                result.add(entity);
            }
        }
        return result;
    }

    @Override
    public void run() {
        long now = System.currentTimeMillis();
        for (SpawnZone zone : zones.values()) {
            try {
                tick(zone, now);
            } catch (Exception e) {
                plugin.getLogger().warning("Ошибка в зоне спавна " + zone.id + ": " + e.getMessage());
            }
        }
    }

    private void tick(SpawnZone zone, long now) {
        World world = zone.bukkitWorld();
        CustomMob mob = module.registry().get(zone.mobId);
        if (world == null || mob == null || !isActive(zone, world)) {
            return;
        }
        int alive = aliveCount(zone);
        int deficit = zone.maxAlive - alive;
        if (deficit <= 0) {
            zone.pendingRespawns.clear();
            return;
        }
        // Убитые ждут своего таймера; мобы, которых просто нет (новая зона, рестарт), появляются сразу
        zone.pendingRespawns.sort(Long::compare);
        int waiting = 0;
        boolean hasDue = false;
        for (long time : zone.pendingRespawns) {
            if (time > now) {
                waiting++;
            } else {
                hasDue = true;
            }
        }
        if (deficit - waiting <= 0) {
            return;
        }
        Location spot = findSpot(zone, world);
        if (spot == null) {
            return;
        }
        Entity spawned = module.spawn(mob, spot);
        if (spawned != null) {
            spawned.getPersistentDataContainer().set(zoneKey, PersistentDataType.STRING, zone.id);
            if (hasDue) {
                zone.pendingRespawns.remove(0);
            }
        }
    }

    private boolean isActive(SpawnZone zone, World world) {
        Location center = zone.center();
        if (center == null || !world.isChunkLoaded(center.getBlockX() >> 4, center.getBlockZ() >> 4)) {
            return false;
        }
        BoundingBox near = zone.box().expand(ACTIVE_DISTANCE);
        for (Player player : world.getPlayers()) {
            if (near.contains(player.getLocation().toVector())) {
                return true;
            }
        }
        return false;
    }

    /** Случайное место в зоне: твёрдый блок, над ним два свободных. */
    private Location findSpot(SpawnZone zone, World world) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 12; attempt++) {
            int x = random.nextInt(zone.minX, zone.maxX + 1);
            int z = random.nextInt(zone.minZ, zone.maxZ + 1);
            if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                continue;
            }
            for (int y = zone.maxY + 2; y >= zone.minY - 1; y--) {
                Block ground = world.getBlockAt(x, y, z);
                if (ground.getType().isSolid()
                        && ground.getRelative(0, 1, 0).isPassable() && !ground.getRelative(0, 1, 0).isLiquid()
                        && ground.getRelative(0, 2, 0).isPassable()) {
                    return new Location(world, x + 0.5, y + 1, z + 0.5, random.nextFloat() * 360f, 0f);
                }
            }
        }
        return null;
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        String zoneId = event.getEntity().getPersistentDataContainer().get(zoneKey, PersistentDataType.STRING);
        SpawnZone zone = zoneId == null ? null : zones.get(zoneId);
        if (zone != null) {
            zone.pendingRespawns.add(System.currentTimeMillis() + zone.respawnSeconds * 1000L);
        }
    }

    /** Убирает устаревшие таймеры, если зону уменьшили. */
    void trim(SpawnZone zone) {
        Iterator<Long> it = zone.pendingRespawns.iterator();
        while (zone.pendingRespawns.size() > zone.maxAlive && it.hasNext()) {
            it.next();
            it.remove();
        }
    }
}
