package com.dathis.rpg.modules.item;

import com.dathis.rpg.core.gui.Icons;
import com.dathis.rpg.core.gui.Menu;
import org.bukkit.Material;

import java.util.List;

/** Список сохранённых предметов. Нажатие открывает редактор, там же можно получить копию. */
final class ListMenu extends Menu {

    private static final int PER_PAGE = 45;

    private final ItemModule module;
    private final int page;

    ListMenu(ItemModule module, int page) {
        super(6, Icons.text("<dark_gray>Сохранённые предметы"));
        this.module = module;
        this.page = page;
    }

    @Override
    protected void render() {
        List<CustomItem> items = module.registry().all();
        int from = page * PER_PAGE;
        for (int i = 0; i < PER_PAGE && from + i < items.size(); i++) {
            CustomItem item = items.get(from + i);
            set(i, Icons.withHint(module.factory().build(item), "",
                            "<dark_gray>ID: " + item.id, "<green>Нажмите, чтобы открыть"),
                    p -> new EditorMenu(module, item.copy()).open(p));
        }
        for (int i = PER_PAGE; i < size(); i++) {
            set(i, Icons.filler());
        }
        if (page > 0) {
            set(45, Icons.of(Material.ARROW, "<yellow>Назад"), p -> new ListMenu(module, page - 1).open(p));
        }
        set(49, Icons.of(Material.BARRIER, "<red>В главное меню"), p -> new MainMenu(module).open(p));
        if (from + PER_PAGE < items.size()) {
            set(53, Icons.of(Material.ARROW, "<yellow>Вперёд"), p -> new ListMenu(module, page + 1).open(p));
        }
    }
}
