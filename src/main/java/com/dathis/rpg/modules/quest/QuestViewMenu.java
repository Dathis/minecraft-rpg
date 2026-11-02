package com.dathis.rpg.modules.quest;

import com.dathis.rpg.core.gui.Icons;
import com.dathis.rpg.core.gui.Menu;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Квест подробно: книга с текстом, цели отдельными иконками, награды — настоящие предметы
 * (наведите или нажмите на Bedrock, чтобы увидеть описание). Взять, сдать, отказаться.
 */
final class QuestViewMenu extends Menu {

    private static final int[] OBJECTIVE_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private static final int[] REWARD_SLOTS = {28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};

    private final QuestModule module;
    private final Quest quest;
    private final QuestBoardMenu board;
    private final boolean atNpc;
    private boolean confirmAbandon;

    QuestViewMenu(QuestModule module, Quest quest, QuestBoardMenu board, boolean atNpc) {
        super(6, Icons.text("<dark_gray>Квест: " + quest.name));
        this.module = module;
        this.quest = quest;
        this.board = board;
        this.atNpc = atNpc;
    }

    @Override
    protected void render() {
        for (int i = 0; i < size(); i++) {
            set(i, Icons.filler());
        }
        Player viewer = board.viewer();
        QuestModule.Status status = module.status(viewer, quest);
        set(4, module.book(viewer, quest));

        set(9, Icons.of(Material.TARGET, "<gold>Задание"));
        for (int i = 0; i < quest.objectives.size() && i < OBJECTIVE_SLOTS.length; i++) {
            Objective objective = quest.objectives.get(i);
            int count = status == QuestModule.Status.COMPLETED || status == QuestModule.Status.COOLDOWN ? objective.amount
                    : status == QuestModule.Status.ACTIVE || status == QuestModule.Status.READY ? module.count(viewer, quest, i) : 0;
            set(OBJECTIVE_SLOTS[i], objectiveIcon(objective, count));
        }

        set(27, Icons.of(Material.GOLD_INGOT, "<gold>Награда",
                "<gray>Наведите на предмет (на телефоне —", "<gray>нажмите), чтобы рассмотреть его"));
        List<ItemStack> rewards = new ArrayList<>();
        for (ItemStack reward : quest.rewards) {
            rewards.add(reward.clone());
        }
        if (quest.rewardXp > 0) {
            rewards.add(Icons.of(Material.EXPERIENCE_BOTTLE, "<green>Опыт: " + quest.rewardXp));
        }
        for (int i = 0; i < rewards.size() && i < REWARD_SLOTS.length; i++) {
            set(REWARD_SLOTS[i], rewards.get(i));
        }
        if (rewards.isEmpty()) {
            set(REWARD_SLOTS[0], Icons.of(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "<gray>Без награды"));
        }

        set(45, Icons.of(Material.ARROW, "<gray>Назад"), p -> board.fresh().open(p));
        switch (status) {
            case AVAILABLE -> {
                if (atNpc) {
                    set(49, Icons.of(Material.LIME_CONCRETE, "<green>Взять квест"), p -> {
                        module.accept(p, quest);
                        redraw();
                    });
                }
            }
            case READY -> {
                if (atNpc) {
                    set(49, Icons.of(Material.LIME_CONCRETE, "<green><bold>Сдать квест",
                            "<gray>Получить награду"), p -> {
                        if (module.turnIn(p, quest)) {
                            board.fresh().open(p);
                        } else {
                            redraw();
                        }
                    });
                } else {
                    set(49, Icons.of(Material.CLOCK, "<green>Цели выполнены",
                            "<gray>Вернитесь к " + giver() + ", чтобы сдать"));
                }
            }
            case ACTIVE -> set(49, Icons.of(Material.CLOCK, "<aqua>Квест выполняется",
                    "<gray>Выполните все цели и вернитесь", "<gray>к " + giver()));
            case COMPLETED -> set(49, Icons.of(Material.BOOK, "<dark_green>Квест выполнен"));
            case COOLDOWN -> set(49, Icons.of(Material.CLOCK, "<dark_green>Квест выполнен",
                    "<gold>Снова доступен через " + module.cooldownText(viewer, quest)));
            case LOCKED -> set(49, Icons.of(Material.BARRIER, "<red>Недоступен",
                    module.lockReason(viewer, quest)));
        }
        if (status == QuestModule.Status.ACTIVE || status == QuestModule.Status.READY) {
            set(53, Icons.of(confirmAbandon ? Material.TNT : Material.BARRIER,
                    confirmAbandon ? "<red><bold>Точно отказаться?" : "<red>Отказаться от квеста",
                    confirmAbandon ? "<gray>Нажмите ещё раз. Прогресс пропадёт" : "<gray>Прогресс пропадёт"), p -> {
                if (!confirmAbandon) {
                    confirmAbandon = true;
                    redraw();
                    return;
                }
                module.abandon(p, quest);
                board.fresh().open(p);
            });
        }
    }

    /** Кому сдавать: «квестодателю» или «доске заданий». */
    private String giver() {
        return quest.npcId == null && quest.board ? "доске заданий" : "квестодателю";
    }

    private ItemStack objectiveIcon(Objective objective, int count) {
        Material block = objective.type == Objective.Type.MINE ? objective.block() : null;
        if (block != null && block.isItem()) {
            return Icons.of(block, module.objectiveText(objective, count));
        }
        if ((objective.type == Objective.Type.BRING || objective.type == Objective.Type.CRAFT) && objective.item != null) {
            // показываем сам предмет (с его описанием), а ниже — текст цели
            ItemStack item = objective.item.clone();
            item.setAmount(1);
            return Icons.withHint(item, "", module.objectiveText(objective, count));
        }
        return Icons.of(objective.type.icon, module.objectiveText(objective, count));
    }
}
