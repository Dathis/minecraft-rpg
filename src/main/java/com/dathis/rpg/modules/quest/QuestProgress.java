package com.dathis.rpg.modules.quest;

import com.dathis.rpg.RpgPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Прогресс игроков: plugins/MinecraftRPG/data/quest-players.yml.
 * Изменения копятся в памяти и пишутся на диск раз в 30 секунд, при выходе игрока и при выключении.
 */
final class QuestProgress {

    private static final String FILE = "quest-players";

    /** Квесты одного игрока. */
    static final class PlayerQuests {
        /** id квеста → прогресс по каждой цели (для «Принести» не используется: считается по инвентарю). */
        final Map<String, int[]> active = new LinkedHashMap<>();
        final Set<String> completed = new LinkedHashSet<>();
        /** id квеста → когда сдан последний раз (мс). Для повторяемых квестов. */
        final Map<String, Long> lastDone = new HashMap<>();
        /** id квеста → сколько раз сдан. */
        final Map<String, Integer> times = new HashMap<>();

        /** Забыть квест полностью (сброс админом). */
        void forget(String questId) {
            active.remove(questId);
            completed.remove(questId);
            lastDone.remove(questId);
            times.remove(questId);
        }

        void clear() {
            active.clear();
            completed.clear();
            lastDone.clear();
            times.clear();
        }
    }

    private final RpgPlugin plugin;
    private final Map<UUID, PlayerQuests> players = new HashMap<>();
    private final Set<UUID> dirty = new HashSet<>();

    QuestProgress(RpgPlugin plugin) {
        this.plugin = plugin;
    }

    PlayerQuests get(UUID uuid) {
        return players.computeIfAbsent(uuid, this::read);
    }

    void changed(UUID uuid) {
        dirty.add(uuid);
    }

    /** Прогресс цели квеста, массив подгоняется под текущее число целей (админ мог их поменять). */
    int[] counts(PlayerQuests data, Quest quest) {
        int[] counts = data.active.get(quest.id);
        if (counts == null) {
            return null;
        }
        if (counts.length != quest.objectives.size()) {
            int[] resized = new int[quest.objectives.size()];
            System.arraycopy(counts, 0, resized, 0, Math.min(counts.length, resized.length));
            data.active.put(quest.id, resized);
            counts = resized;
        }
        return counts;
    }

    private PlayerQuests read(UUID uuid) {
        PlayerQuests data = new PlayerQuests();
        ConfigurationSection s = plugin.store().get(FILE).getConfigurationSection("players." + uuid);
        if (s == null) {
            return data;
        }
        data.completed.addAll(s.getStringList("completed"));
        ConfigurationSection active = s.getConfigurationSection("active");
        if (active != null) {
            for (String questId : active.getKeys(false)) {
                List<Integer> list = active.getIntegerList(questId);
                data.active.put(questId, list.stream().mapToInt(Integer::intValue).toArray());
            }
        }
        ConfigurationSection last = s.getConfigurationSection("last");
        if (last != null) {
            for (String questId : last.getKeys(false)) {
                data.lastDone.put(questId, last.getLong(questId));
            }
        }
        ConfigurationSection times = s.getConfigurationSection("times");
        if (times != null) {
            for (String questId : times.getKeys(false)) {
                data.times.put(questId, times.getInt(questId));
            }
        }
        return data;
    }

    private void write(UUID uuid, PlayerQuests data) {
        YamlConfiguration yaml = plugin.store().get(FILE);
        String path = "players." + uuid;
        yaml.set(path, null);
        yaml.set(path + ".completed", List.copyOf(data.completed));
        for (Map.Entry<String, int[]> entry : data.active.entrySet()) {
            yaml.set(path + ".active." + entry.getKey(), java.util.Arrays.stream(entry.getValue()).boxed().toList());
        }
        data.lastDone.forEach((questId, time) -> yaml.set(path + ".last." + questId, time));
        data.times.forEach((questId, count) -> yaml.set(path + ".times." + questId, count));
    }

    void flush() {
        if (dirty.isEmpty()) {
            return;
        }
        for (UUID uuid : dirty) {
            PlayerQuests data = players.get(uuid);
            if (data != null) {
                write(uuid, data);
            }
        }
        dirty.clear();
        plugin.store().save(FILE);
    }

    /** Игрок вышел: сохранить и выгрузить. */
    void unload(UUID uuid) {
        flush();
        players.remove(uuid);
    }
}
