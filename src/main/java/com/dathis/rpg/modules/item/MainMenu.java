package com.dathis.rpg.modules.item;

import com.dathis.rpg.core.gui.Icons;
import com.dathis.rpg.core.gui.Menu;
import org.bukkit.Material;

/** Первое окно /create-item: что создаём. */
final class MainMenu extends Menu {

    private final ItemModule module;

    MainMenu(ItemModule module) {
        super(3, Icons.text("<dark_gray>Создание предметов"));
        this.module = module;
    }

    @Override
    protected void render() {
        for (int i = 0; i < size(); i++) {
            set(i, Icons.filler());
        }
        set(11, Icons.of(Material.PAPER, "<white>Обычный предмет",
                        "<gray>Ванильный предмет со своим",
                        "<gray>названием, цветом и описанием"),
                p -> new EditorMenu(module, new CustomItem(CustomItem.Kind.ITEM, Material.PAPER, "Новый предмет")).open(p));
        set(13, Icons.of(Material.IRON_SWORD, "<red>Оружие",
                        "<gray>Всё как у предмета, плюс:",
                        "<gray>урон от и до, крит. удар, вампиризм"),
                p -> new EditorMenu(module, new CustomItem(CustomItem.Kind.WEAPON, Material.IRON_SWORD, "Новое оружие")).open(p));
        set(15, Icons.of(Material.CHEST, "<gold>Сохранённые предметы",
                        "<gray>Всего: <white>" + module.registry().all().size(),
                        "<gray>Получить или изменить"),
                p -> new ListMenu(module, 0).open(p));
    }
}
