package com.dathis.rpg.modules.region;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Область мира, выделенная палочкой.
 * BUILD — зона строительства: внутри игроки строят, ломают и открывают всё, как в обычном Minecraft.
 * MINING — зона добычи: ломать можно только блоки из правил, остальное защищено.
 */
final class Area {

    enum Kind {
        BUILD, MINING
    }

    final String id;
    final Kind kind;
    String name;
    String world;
    int minX, minY, minZ, maxX, maxY, maxZ;
    /** Только для MINING: правила действуют во всём мире {@link #world}, а не в коробке. */
    boolean wholeWorld;
    /** Только для MINING: какой блок можно ломать и что с ним происходит. Порядок сохраняется. */
    final Map<Material, BlockRule> rules = new LinkedHashMap<>();

    Area(String id, Kind kind, String name, Location a, Location b) {
        this.id = id;
        this.kind = kind;
        this.name = name;
        setArea(a, b);
    }

    private Area(String id, Kind kind) {
        this.id = id;
        this.kind = kind;
    }

    void setArea(Location a, Location b) {
        world = a.getWorld().getName();
        minX = Math.min(a.getBlockX(), b.getBlockX());
        minY = Math.min(a.getBlockY(), b.getBlockY());
        minZ = Math.min(a.getBlockZ(), b.getBlockZ());
        maxX = Math.max(a.getBlockX(), b.getBlockX());
        maxY = Math.max(a.getBlockY(), b.getBlockY());
        maxZ = Math.max(a.getBlockZ(), b.getBlockZ());
    }

    boolean contains(Block block) {
        if (!block.getWorld().getName().equals(world)) {
            return false;
        }
        if (wholeWorld) {
            return true;
        }
        int x = block.getX(), y = block.getY(), z = block.getZ();
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    World bukkitWorld() {
        return Bukkit.getWorld(world);
    }

    Location corner1() {
        return new Location(bukkitWorld(), minX, minY, minZ);
    }

    Location corner2() {
        return new Location(bukkitWorld(), maxX, maxY, maxZ);
    }

    Location center() {
        World w = bukkitWorld();
        return w == null ? null : new Location(w, (minX + maxX + 1) / 2.0, maxY + 1, (minZ + maxZ + 1) / 2.0);
    }

    String sizeText() {
        return wholeWorld ? "весь мир" : (maxX - minX + 1) + "x" + (maxY - minY + 1) + "x" + (maxZ - minZ + 1);
    }

    String coordsText() {
        return wholeWorld ? world + " (весь мир)"
                : world + " " + minX + " " + minY + " " + minZ + " → " + maxX + " " + maxY + " " + maxZ;
    }

    void save(ConfigurationSection s) {
        s.set("kind", kind.name());
        s.set("name", name);
        s.set("world", world);
        s.set("min", List.of(minX, minY, minZ));
        s.set("max", List.of(maxX, maxY, maxZ));
        s.set("whole-world", wholeWorld ? true : null);
        s.set("rules", null);
        if (kind == Kind.MINING) {
            ConfigurationSection rs = s.createSection("rules");
            for (BlockRule rule : rules.values()) {
                rule.save(rs.createSection(rule.block.name()));
            }
        }
    }

    static Area load(String id, ConfigurationSection s) {
        Kind kind;
        try {
            kind = Kind.valueOf(s.getString("kind", "BUILD"));
        } catch (IllegalArgumentException e) {
            return null;
        }
        Area area = new Area(id, kind);
        area.name = s.getString("name", id);
        area.world = s.getString("world", "world");
        List<Integer> min = s.getIntegerList("min");
        List<Integer> max = s.getIntegerList("max");
        if (min.size() != 3 || max.size() != 3) {
            return null;
        }
        area.minX = min.get(0);
        area.minY = min.get(1);
        area.minZ = min.get(2);
        area.maxX = max.get(0);
        area.maxY = max.get(1);
        area.maxZ = max.get(2);
        area.wholeWorld = s.getBoolean("whole-world", false);
        ConfigurationSection rs = s.getConfigurationSection("rules");
        if (rs != null) {
            for (String key : rs.getKeys(false)) {
                BlockRule rule = BlockRule.load(key, rs.getConfigurationSection(key));
                if (rule != null) {
                    area.rules.put(rule.block, rule);
                }
            }
        }
        return area;
    }
}
