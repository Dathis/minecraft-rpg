package com.dathis.rpg.modules.quest;

import com.dathis.rpg.core.storage.ItemCodec;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Шаблон квеста. Он же черновик в редакторе. */
final class Quest {

    static final int MAX_OBJECTIVES = 7;
    static final int MAX_REWARDS = 14;

    /** Как часто квест можно выполнять. */
    enum Mode {
        ONCE("Один раз"),
        REPEAT("Повторяемый"),
        DAILY("Ежедневный"),
        WEEKLY("Еженедельный");

        final String label;

        Mode(String label) {
            this.label = label;
        }
    }

    String id;                 // null, пока не сохранён
    String name = "Новый квест";
    String npcId;              // кто выдаёт и принимает квест
    /** Что говорит NPC. Каждая строка — абзац, при показе переносится по ширине. */
    List<String> description = new ArrayList<>();
    List<Objective> objectives = new ArrayList<>();
    List<ItemStack> rewards = new ArrayList<>();
    int rewardXp;
    /** Квест, который нужно выполнить раньше (цепочка). */
    String requiredQuest;
    /** С какого уровня персонажа доступен (0 и 1 — с любого). */
    int requiredLevel;

    Mode mode = Mode.ONCE;
    /** REPEAT: через сколько секунд после сдачи квест снова доступен. */
    int cooldown = 3600;
    /** Награда за второе и следующие выполнения, % от обычной (предметы и опыт). */
    int repeatRewardPercent = 100;
    /** Сколько раз всего можно выполнить повторяемый квест. 0 — без ограничения. */
    int maxRepeats;
    /** Квест в пуле доски заданий: доска каждый день показывает несколько случайных квестов из пула. */
    boolean board;

    boolean repeatable() {
        return mode != Mode.ONCE;
    }

    /** Кто выдаёт: NPC или доска (квест с доски может обойтись без NPC). */
    boolean hasGiver() {
        return npcId != null || board;
    }

    boolean ready() {
        return hasGiver() && !objectives.isEmpty() && objectives.stream().allMatch(Objective::complete);
    }

    Quest copy() {
        Quest c = new Quest();
        c.id = id;
        c.name = name;
        c.npcId = npcId;
        c.description = new ArrayList<>(description);
        for (Objective objective : objectives) {
            c.objectives.add(objective.copy());
        }
        for (ItemStack reward : rewards) {
            c.rewards.add(reward.clone());
        }
        c.rewardXp = rewardXp;
        c.requiredQuest = requiredQuest;
        c.requiredLevel = requiredLevel;
        c.mode = mode;
        c.cooldown = cooldown;
        c.repeatRewardPercent = repeatRewardPercent;
        c.maxRepeats = maxRepeats;
        c.board = board;
        return c;
    }

    void save(ConfigurationSection s) {
        s.set("name", name);
        s.set("npc", npcId);
        s.set("description", description);
        s.set("objectives", null);
        for (int i = 0; i < objectives.size(); i++) {
            objectives.get(i).save(s.createSection("objectives." + i));
        }
        s.set("rewards", rewards.stream().map(ItemCodec::encode).toList());
        s.set("reward-xp", rewardXp);
        s.set("required-quest", requiredQuest);
        s.set("required-level", requiredLevel);
        s.set("mode", mode.name());
        s.set("cooldown", cooldown);
        s.set("repeat-reward-percent", repeatRewardPercent);
        s.set("max-repeats", maxRepeats);
        s.set("board", board);
    }

    static Quest load(String id, ConfigurationSection s) {
        Quest q = new Quest();
        q.id = id;
        q.name = s.getString("name", id);
        q.npcId = s.getString("npc");
        q.description = new ArrayList<>(s.getStringList("description"));
        ConfigurationSection objectives = s.getConfigurationSection("objectives");
        if (objectives != null) {
            for (String key : objectives.getKeys(false)) {
                ConfigurationSection section = objectives.getConfigurationSection(key);
                Objective objective = section == null ? null : Objective.load(section);
                if (objective != null) {
                    q.objectives.add(objective);
                }
            }
        }
        for (String data : s.getStringList("rewards")) {
            ItemStack item = ItemCodec.decode(data);
            if (item != null) {
                q.rewards.add(item);
            }
        }
        q.rewardXp = Math.max(0, s.getInt("reward-xp", 0));
        q.requiredQuest = s.getString("required-quest");
        q.requiredLevel = Math.max(0, s.getInt("required-level", 0));
        try {
            q.mode = Mode.valueOf(s.getString("mode", "ONCE").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            q.mode = Mode.ONCE;
        }
        q.cooldown = Math.max(60, s.getInt("cooldown", 3600));
        q.repeatRewardPercent = Math.max(0, Math.min(100, s.getInt("repeat-reward-percent", 100)));
        q.maxRepeats = Math.max(0, s.getInt("max-repeats", 0));
        q.board = s.getBoolean("board", false);
        return q;
    }
}
