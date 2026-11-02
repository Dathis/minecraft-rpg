package com.dathis.rpg.modules.ability;

import com.dathis.rpg.core.gui.Icons;
import com.dathis.rpg.core.gui.Menu;
import org.bukkit.Material;

import java.util.List;

/** Список сохранённых способностей. Нажатие открывает редактор. */
final class AbilityListMenu extends Menu {

    private static final int PER_PAGE = 45;

    private final AbilityModule module;
    private final int page;

    AbilityListMenu(AbilityModule module, int page) {
        super(6, Icons.text("<dark_gray>Сохранённые способности"));
        this.module = module;
        this.page = page;
    }

    @Override
    protected void render() {
        List<Ability> abilities = module.registry().all();
        int from = page * PER_PAGE;
        for (int i = 0; i < PER_PAGE && from + i < abilities.size(); i++) {
            Ability ability = abilities.get(from + i);
            set(i, Icons.withHint(module.registry().rune(ability), "",
                            "<dark_gray>" + ability.mechanic.label + ", ID: " + ability.id,
                            "<green>Нажмите, чтобы открыть"),
                    p -> new AbilityEditorMenu(module, ability.copy()).open(p));
        }
        for (int i = PER_PAGE; i < size(); i++) {
            set(i, Icons.filler());
        }
        if (abilities.isEmpty()) {
            set(22, Icons.of(Material.BARRIER, "<gray>Пока ничего нет"));
        }
        if (page > 0) {
            set(45, Icons.of(Material.ARROW, "<yellow>Назад"), p -> new AbilityListMenu(module, page - 1).open(p));
        }
        set(49, Icons.of(Material.BARRIER, "<red>В главное меню"), p -> new AbilityMainMenu(module).open(p));
        if (from + PER_PAGE < abilities.size()) {
            set(53, Icons.of(Material.ARROW, "<yellow>Вперёд"), p -> new AbilityListMenu(module, page + 1).open(p));
        }
    }
}
