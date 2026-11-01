package com.dathis.rpg.modules.quest;

import com.dathis.rpg.core.gui.ChoiceMenu;
import com.dathis.rpg.core.gui.Icons;
import com.dathis.rpg.core.gui.Menu;
import org.bukkit.Material;

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
        set(11, Icons.of(Material.WRITABLE_BOOK, "<green>Новый квест",
                        "<gray>Цели: убить, принести, поговорить,", "<gray>разведать. Награды, цепочки"),
                p -> new QuestEditorMenu(module, new Quest(), new QuestMainMenu(module)).open(p));
        set(13, Icons.of(Material.BOOKSHELF, "<gold>Все квесты",
                        "<gray>Всего: <white>" + module.registry().all().size()),
                p -> list(module).open(p));
        set(15, Icons.of(Material.BLAZE_ROD, "<gold>Палочка выделения",
                        "<gray>Для целей «Разведать»"),
                p -> {
                    p.getInventory().addItem(module.plugin().selectionWand().create());
                    p.closeInventory();
                });
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
