package com.dathis.rpg.modules.region;

import com.dathis.rpg.RpgPlugin;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Зоны строительства, зоны добычи и земли под участки. Файл data/regions.yml, раздел areas. */
final class AreaRegistry {

    static final String FILE = "regions";

    private final RpgPlugin plugin;
    private final Map<String, Area> areas = new LinkedHashMap<>();

    AreaRegistry(RpgPlugin plugin) {
        this.plugin = plugin;
    }

    void load() {
        areas.clear();
        ConfigurationSection root = plugin.store().get(FILE).getConfigurationSection("areas");
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            Area area = s == null ? null : Area.load(id, s);
            if (area == null) {
                plugin.getLogger().warning("Зона " + id + " в regions.yml повреждена, пропускаю.");
                continue;
            }
            areas.put(id, area);
        }
    }

    void save() {
        YamlConfiguration yaml = plugin.store().get(FILE);
        yaml.set("areas", null);
        ConfigurationSection root = yaml.createSection("areas");
        for (Area area : areas.values()) {
            area.save(root.createSection(area.id));
        }
        plugin.store().save(FILE);
    }

    Area create(Area.Kind kind, String name, Location a, Location b) {
        Area area = new Area(nextId(kind), kind, name, a, b);
        areas.put(area.id, area);
        save();
        return area;
    }

    /** Зона добычи на весь мир (например, рубить деревья можно везде). */
    Area createWholeWorld(String name, World world) {
        Location zero = new Location(world, 0, 0, 0);
        Area area = new Area(nextId(Area.Kind.MINING), Area.Kind.MINING, name, zero, zero);
        area.wholeWorld = true;
        areas.put(area.id, area);
        save();
        return area;
    }

    void delete(Area area) {
        areas.remove(area.id);
        save();
    }

    Area get(String id) {
        return areas.get(id);
    }

    List<Area> all(Area.Kind kind) {
        List<Area> list = new ArrayList<>();
        for (Area area : areas.values()) {
            if (area.kind == kind) {
                list.add(area);
            }
        }
        return list;
    }

    boolean inBuildZone(Block block) {
        for (Area area : areas.values()) {
            if (area.kind == Area.Kind.BUILD && area.contains(block)) {
                return true;
            }
        }
        return false;
    }

    boolean inMiningArea(Block block) {
        for (Area area : areas.values()) {
            if (area.kind == Area.Kind.MINING && area.contains(block)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Правило для блока этого типа в этом месте или null. Сначала смотрим обычные зоны, потом «весь мир»,
     * чтобы правило шахты было важнее общего правила мира.
     */
    BlockRule ruleAt(Block block, Material type) {
        BlockRule global = null;
        for (Area area : areas.values()) {
            if (area.kind != Area.Kind.MINING || !area.contains(block)) {
                continue;
            }
            BlockRule rule = area.rules.get(type);
            if (rule == null) {
                continue;
            }
            if (!area.wholeWorld) {
                return rule;
            }
            if (global == null) {
                global = rule;
            }
        }
        return global;
    }

    private String nextId(Area.Kind kind) {
        String prefix = switch (kind) {
            case BUILD -> "build";
            case MINING -> "mine";
            case LAND -> "land";
        };
        int n = 1;
        while (areas.containsKey(prefix + n)) {
            n++;
        }
        return prefix + n;
    }
}
