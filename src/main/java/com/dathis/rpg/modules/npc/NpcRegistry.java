package com.dathis.rpg.modules.npc;

import com.dathis.rpg.RpgPlugin;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Шаблоны NPC и их точки в мире: plugins/MinecraftRPG/data/npcs.yml. */
final class NpcRegistry {

    private static final String FILE = "npcs";

    private final RpgPlugin plugin;
    private final Map<String, NpcTemplate> npcs = new LinkedHashMap<>();
    private final Map<String, NpcPlacement> placements = new LinkedHashMap<>();

    NpcRegistry(RpgPlugin plugin) {
        this.plugin = plugin;
    }

    void load() {
        npcs.clear();
        placements.clear();
        YamlConfiguration yaml = plugin.store().get(FILE);
        ConfigurationSection root = yaml.getConfigurationSection("npcs");
        if (root != null) {
            for (String id : root.getKeys(false)) {
                ConfigurationSection section = root.getConfigurationSection(id);
                if (section != null) {
                    npcs.put(id, NpcTemplate.load(id, section));
                }
            }
        }
        ConfigurationSection places = yaml.getConfigurationSection("placements");
        if (places != null) {
            for (String id : places.getKeys(false)) {
                ConfigurationSection section = places.getConfigurationSection(id);
                NpcPlacement placement = section == null ? null : NpcPlacement.load(id, section);
                if (placement != null) {
                    placements.put(id, placement);
                }
            }
        }
    }

    void save(NpcTemplate npc) {
        if (npc.id == null) {
            npc.id = nextId(npcs, "npc");
        }
        npcs.put(npc.id, npc.copy());
        writeFile();
    }

    /** Удаляет шаблон вместе со всеми его точками. */
    void delete(String id) {
        npcs.remove(id);
        placements.values().removeIf(p -> p.npcId.equals(id));
        writeFile();
    }

    NpcTemplate get(String id) {
        return id == null ? null : npcs.get(id);
    }

    List<NpcTemplate> all() {
        return new ArrayList<>(npcs.values());
    }

    NpcPlacement place(String npcId, Location location) {
        NpcPlacement placement = new NpcPlacement(nextId(placements, "p"), npcId, location);
        placements.put(placement.id, placement);
        writeFile();
        return placement;
    }

    void removePlacement(String id) {
        placements.remove(id);
        writeFile();
    }

    NpcPlacement placement(String id) {
        return id == null ? null : placements.get(id);
    }

    List<NpcPlacement> placements() {
        return new ArrayList<>(placements.values());
    }

    List<NpcPlacement> placementsOf(String npcId) {
        return placements.values().stream().filter(p -> p.npcId.equals(npcId)).toList();
    }

    /** Сохранить файл после изменения точки (перемещение). */
    void writeFile() {
        YamlConfiguration yaml = plugin.store().get(FILE);
        yaml.set("npcs", null);
        ConfigurationSection root = yaml.createSection("npcs");
        for (NpcTemplate npc : npcs.values()) {
            npc.save(root.createSection(npc.id));
        }
        yaml.set("placements", null);
        ConfigurationSection places = yaml.createSection("placements");
        for (NpcPlacement placement : placements.values()) {
            placement.save(places.createSection(placement.id));
        }
        plugin.store().save(FILE);
    }

    private static String nextId(Map<String, ?> map, String prefix) {
        int n = 1;
        while (map.containsKey(prefix + n)) {
            n++;
        }
        return prefix + n;
    }
}
