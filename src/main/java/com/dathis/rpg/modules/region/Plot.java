package com.dathis.rpg.modules.region;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Участок игрока на земле под участки. Квадрат по X/Z на всю высоту мира.
 * Строить внутри могут только владелец и добавленные им игроки.
 */
final class Plot {

    final String id;
    /** id земли (Area вида LAND), на которой стоит участок. */
    final String land;
    final PlotSize size;
    String world;
    int minX, minZ, maxX, maxZ;
    UUID owner;
    String ownerName;
    /** Кому владелец разрешил строить: uuid → ник. */
    final Map<UUID, String> members = new LinkedHashMap<>();

    Plot(String id, String land, PlotSize size, String world, int minX, int minZ, int side) {
        this.id = id;
        this.land = land;
        this.size = size;
        this.world = world;
        this.minX = minX;
        this.minZ = minZ;
        this.maxX = minX + side - 1;
        this.maxZ = minZ + side - 1;
    }

    boolean contains(Block block) {
        int x = block.getX(), z = block.getZ();
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ && block.getWorld().getName().equals(world);
    }

    boolean overlaps(String world, int minX, int minZ, int maxX, int maxZ) {
        return this.world.equals(world) && minX <= this.maxX && maxX >= this.minX
                && minZ <= this.maxZ && maxZ >= this.minZ;
    }

    boolean isOwner(Player player) {
        return player.getUniqueId().equals(owner);
    }

    boolean canBuild(Player player) {
        return isOwner(player) || members.containsKey(player.getUniqueId());
    }

    int side() {
        return maxX - minX + 1;
    }

    String sizeText() {
        return side() + "x" + (maxZ - minZ + 1);
    }

    String coordsText() {
        return "X " + minX + ".." + maxX + ", Z " + minZ + ".." + maxZ;
    }

    World bukkitWorld() {
        return Bukkit.getWorld(world);
    }

    /** Углы для подсветки: по высоте показываем полосу вокруг игрока, вся высота мира в частицах не видна. */
    Location corner1(int y) {
        return new Location(bukkitWorld(), minX, y, minZ);
    }

    Location corner2(int y) {
        return new Location(bukkitWorld(), maxX, y, maxZ);
    }

    /** Точка для телепорта: центр участка на самом верхнем блоке. */
    Location center() {
        World w = bukkitWorld();
        if (w == null) {
            return null;
        }
        int x = (minX + maxX) / 2, z = (minZ + maxZ) / 2;
        return new Location(w, x + 0.5, w.getHighestBlockYAt(x, z) + 1, z + 0.5);
    }

    void save(ConfigurationSection s) {
        s.set("land", land);
        s.set("size", size.name());
        s.set("world", world);
        s.set("min", List.of(minX, minZ));
        s.set("max", List.of(maxX, maxZ));
        s.set("owner", owner == null ? null : owner.toString());
        s.set("owner-name", ownerName);
        s.set("members", null);
        if (!members.isEmpty()) {
            ConfigurationSection ms = s.createSection("members");
            members.forEach((uuid, name) -> ms.set(uuid.toString(), name));
        }
    }

    static Plot load(String id, ConfigurationSection s) {
        PlotSize size = PlotSize.parse(s.getString("size"));
        List<Integer> min = s.getIntegerList("min");
        List<Integer> max = s.getIntegerList("max");
        String land = s.getString("land");
        if (size == null || land == null || min.size() != 2 || max.size() != 2) {
            return null;
        }
        Plot plot = new Plot(id, land, size, s.getString("world", "world"), min.get(0), min.get(1), 1);
        plot.maxX = max.get(0);
        plot.maxZ = max.get(1);
        try {
            String owner = s.getString("owner");
            plot.owner = owner == null ? null : UUID.fromString(owner);
        } catch (IllegalArgumentException e) {
            return null;
        }
        plot.ownerName = s.getString("owner-name", "?");
        ConfigurationSection ms = s.getConfigurationSection("members");
        if (ms != null) {
            for (String key : ms.getKeys(false)) {
                try {
                    plot.members.put(UUID.fromString(key), ms.getString(key, "?"));
                } catch (IllegalArgumentException ignored) {
                    // повреждённая запись — пропускаем
                }
            }
        }
        return plot;
    }
}
