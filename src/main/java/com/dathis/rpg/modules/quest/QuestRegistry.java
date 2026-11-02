package com.dathis.rpg.modules.quest;

import com.dathis.rpg.RpgPlugin;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** Шаблоны квестов и доски заданий: plugins/MinecraftRPG/data/quests.yml. */
final class QuestRegistry {

    private static final String FILE = "quests";

    private final RpgPlugin plugin;
    private final Map<String, Quest> quests = new LinkedHashMap<>();
    /** Блоки-доски заданий: «world;x;y;z». */
    private final Set<String> boards = new LinkedHashSet<>();

    QuestRegistry(RpgPlugin plugin) {
        this.plugin = plugin;
    }

    void load() {
        quests.clear();
        boards.clear();
        boards.addAll(plugin.store().get(FILE).getStringList("boards"));
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

    // ---------- доска заданий ----------

    /** Квесты пула доски, которые можно выдавать (настроены). Порядок постоянный — для одинакового выбора. */
    List<Quest> boardPool() {
        return quests.values().stream().filter(q -> q.board && q.ready())
                .sorted(Comparator.comparing(q -> q.id)).toList();
    }

    /** Задания доски на этот игровой день: count случайных из пула, одинаковые для всех игроков и досок. */
    List<Quest> boardToday(long dayIndex, int count) {
        List<Quest> pool = new ArrayList<>(boardPool());
        java.util.Collections.shuffle(pool, new Random(dayIndex * 7919L + 17L));
        return pool.subList(0, Math.min(Math.max(0, count), pool.size()));
    }

    static String key(Block block) {
        return block.getWorld().getName() + ";" + block.getX() + ";" + block.getY() + ";" + block.getZ();
    }

    boolean isBoard(Block block) {
        return !boards.isEmpty() && boards.contains(key(block));
    }

    /** Добавить или убрать доску. true — блок стал доской. */
    boolean toggleBoard(Block block) {
        String key = key(block);
        boolean added = boards.add(key);
        if (!added) {
            boards.remove(key);
        }
        writeBoards();
        return added;
    }

    void removeBoard(String key) {
        if (boards.remove(key)) {
            writeBoards();
        }
    }

    List<String> boards() {
        return new ArrayList<>(boards);
    }

    /** Место доски по ключу или null, если мир не загружен. */
    Location boardLocation(String key) {
        String[] parts = key.split(";");
        if (parts.length != 4) {
            return null;
        }
        World world = plugin.getServer().getWorld(parts[0]);
        try {
            return world == null ? null : new Location(world, Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void writeBoards() {
        plugin.store().get(FILE).set("boards", List.copyOf(boards));
        plugin.store().save(FILE);
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
