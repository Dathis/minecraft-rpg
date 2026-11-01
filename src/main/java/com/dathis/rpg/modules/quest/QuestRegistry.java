package com.dathis.rpg.modules.quest;

import com.dathis.rpg.RpgPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Шаблоны квестов: plugins/MinecraftRPG/data/quests.yml. */
final class QuestRegistry {

    private static final String FILE = "quests";

    private final RpgPlugin plugin;
    private final Map<String, Quest> quests = new LinkedHashMap<>();

    QuestRegistry(RpgPlugin plugin) {
        this.plugin = plugin;
    }

    void load() {
        quests.clear();
        ConfigurationSection root = plugin.store().get(FILE).getConfigurationSection("quests");
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section != null) {
                quests.put(id, Quest.load(id, section));
            }
        }
    }

    void save(Quest quest) {
        if (quest.id == null) {
            int n = 1;
            while (quests.containsKey("quest" + n)) {
                n++;
            }
            quest.id = "quest" + n;
        }
        quests.put(quest.id, quest.copy());
        writeFile();
    }

    void delete(String id) {
        quests.remove(id);
        for (Quest quest : quests.values()) {
            if (id.equals(quest.requiredQuest)) {
                quest.requiredQuest = null;
            }
        }
        writeFile();
    }

    Quest get(String id) {
        return id == null ? null : quests.get(id);
    }

    List<Quest> all() {
        return new ArrayList<>(quests.values());
    }

    List<Quest> ofNpc(String npcId) {
        return quests.values().stream().filter(q -> npcId.equals(q.npcId)).toList();
    }

    private void writeFile() {
        YamlConfiguration yaml = plugin.store().get(FILE);
        yaml.set("quests", null);
        ConfigurationSection root = yaml.createSection("quests");
        for (Quest quest : quests.values()) {
            quest.save(root.createSection(quest.id));
        }
        plugin.store().save(FILE);
    }
}
