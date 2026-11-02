package com.dathis.rpg.modules.quest;

import com.dathis.rpg.core.gui.ChoiceMenu;
import com.dathis.rpg.core.gui.Icons;
import com.dathis.rpg.core.gui.Menu;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** Первое окно /create-quest. */
final class QuestMainMenu extends Menu {

    private final QuestModule module;

    QuestMainMenu(QuestModule module) {
        super(3, Icons.text("<dark_gray>Квесты"));
        this.module = module;
    }

    @Override
    protected void render() {
        for (int i = 0; i < size(); i++) {
            set(i, Icons.filler());
        }
        set(10, Icons.of(Material.WRITABLE_BOOK, "<green>Новый квест",
                        "<gray>Цели: убить, принести, поговорить,", "<gray>разведать, добыть, скрафтить.",
                        "<gray>Награды, цепочки, повторение"),
                p -> new QuestEditorMenu(module, new Quest(), new QuestMainMenu(module)).open(p));
        set(12, Icons.of(Material.BOOKSHELF, "<gold>Все квесты",
                        "<gray>Всего: <white>" + module.registry().all().size()),
                p -> list(module).open(p));
        set(14, Icons.of(Material.OAK_HANGING_SIGN, "<gold>Доски заданий",
                        "<gray>Досок: <white>" + module.registry().boards().size(),
                        "<gray>Квестов в пуле: <white>" + module.registry().boardPool().size(),
                        "<gray>Каждый день: <white>" + module.boardSize() + " случайных",
                        "", "<green>Нажмите, чтобы настроить"),
                p -> boards(module).open(p));
        set(16, Icons.of(Material.BLAZE_ROD, "<gold>Палочка выделения",
                        "<gray>Для целей «Разведать»"),
                p -> {
                    p.getInventory().addItem(module.plugin().selectionWand().create());
                    p.closeInventory();
                });
    }

    /** Доски заданий: сделать доской блок, на который смотришь; список досок (телепорт, удалить). */
    static Menu boards(QuestModule module) {
        Menu back = new QuestMainMenu(module);
        List<ChoiceMenu.Choice> choices = new ArrayList<>();
        choices.add(new ChoiceMenu.Choice(Icons.of(Material.LIME_CONCRETE, "<green>Сделать доской блок, на который смотрю",
                "<gray>Поставьте блок (кафедра, табличка,", "<gray>бочка...), посмотрите на него,",
                "<gray>откройте это окно и нажмите.", "<gray>То же: <white>/create-quest board"), p -> {
            String result = toggleBoard(module, p);
            module.plugin().messages().send(p, result);
            boards(module).open(p);
        }));
        choices.add(new ChoiceMenu.Choice(Icons.of(Material.BOOK, "<yellow>Какие квесты на доске",
                "<gray>В редакторе квеста кнопка", "<gray>«Доска заданий» добавляет его в пул.",
                "<gray>Каждый день в " + module.plugin().getConfig().getString("quests.daily-reset", "00:00")
                        + " доска берёт", "<gray>из пула " + module.boardSize() + " случайных (quests.board.daily-count).",
                "<gray>Одинаковые у всех досок и игроков.", "", "<green>Нажмите, чтобы посмотреть пул"),
                p -> pool(module).open(p)));
        for (String key : module.registry().boards()) {
            Location location = module.registry().boardLocation(key);
            Material icon = location == null ? Material.BARRIER : location.getBlock().getType();
            if (!icon.isItem() || icon.isAir()) {
                icon = Material.OAK_SIGN;
            }
            String where = key.replace(';', ' ');
            choices.add(new ChoiceMenu.Choice(Icons.of(icon, "<gold>Доска: <white>" + where,
                    location == null ? "<red>Мир не загружен" : "", "<green>Нажмите: телепорт или удалить"), p -> {
                List<ChoiceMenu.Choice> actions = new ArrayList<>();
                if (location != null) {
                    actions.add(new ChoiceMenu.Choice(Icons.of(Material.ENDER_PEARL, "<aqua>Телепорт к доске"), pl -> {
                        pl.closeInventory();
                        pl.teleport(location.clone().add(0.5, 1, 0.5));
                    }));
                }
                actions.add(new ChoiceMenu.Choice(Icons.of(Material.BARRIER, "<red>Удалить доску",
                        "<gray>Блок останется, но перестанет", "<gray>быть доской"), pl -> {
                    module.registry().removeBoard(key);
                    module.plugin().messages().send(pl, "<yellow>Доска удалена: " + where);
                    boards(module).open(pl);
                }));
                new ChoiceMenu("Доска " + where, actions, boards(module)).open(p);
            }));
        }
        return new ChoiceMenu("Доски заданий", choices, back);
    }

    /** Пул доски: квесты с флагом «доска», сегодняшние отмечены. */
    private static Menu pool(QuestModule module) {
        List<Quest> today = module.boardToday();
        List<ChoiceMenu.Choice> choices = new ArrayList<>();
        Menu back = boards(module);
        for (Quest quest : module.registry().all()) {
            if (!quest.board) {
                continue;
            }
            choices.add(new ChoiceMenu.Choice(Icons.withHint(module.book(null, quest), "",
                    today.contains(quest) ? "<green>Сегодня на доске" : quest.ready() ? "<gray>Сегодня не выпал"
                            : "<red>Не настроен — на доску не попадёт",
                    "<dark_gray>ID: " + quest.id, "<green>Нажмите, чтобы изменить"),
                    p -> new QuestEditorMenu(module, quest.copy(), back).open(p)));
        }
        return new ChoiceMenu("Пул доски заданий", choices, back);
    }

    /** Сделать доской блок, на который смотрит админ, или убрать доску. Возвращает сообщение. */
    static String toggleBoard(QuestModule module, Player player) {
        Block block = player.getTargetBlockExact(8);
        if (block == null || block.getType().isAir()) {
            return "<red>Посмотрите на блок (до 8 блоков) и попробуйте снова.</red>";
        }
        boolean added = module.registry().toggleBoard(block);
        return added ? "<green>Блок стал доской заданий. Игроки нажимают на него (ПКМ / тап)."
                : "<yellow>Блок больше не доска заданий.";
    }

    static Menu list(QuestModule module) {
        Menu back = new QuestMainMenu(module);
        List<ChoiceMenu.Choice> choices = new ArrayList<>();
        for (Quest quest : module.registry().all()) {
            choices.add(new ChoiceMenu.Choice(Icons.withHint(module.book(null, quest), "", "<dark_gray>ID: " + quest.id,
                    "<green>Нажмите, чтобы изменить"), p -> new QuestEditorMenu(module, quest.copy(), back).open(p)));
        }
        return new ChoiceMenu("Все квесты", choices, back);
    }
}
