package com.dathis.rpg.modules.mob;

import com.dathis.rpg.core.gui.Icons;
import com.dathis.rpg.core.gui.Menu;
import org.bukkit.Material;

import java.util.List;

/** Список сохранённых мобов. */
final class MobListMenu extends Menu {

    private static final int PER_PAGE = 45;

    private final MobModule module;
    private final int page;

    MobListMenu(MobModule module, int page) {
        super(6, Icons.text("<dark_gray>Сохранённые мобы"));
        this.module = module;
        this.page = page;
    }

    @Override
    protected void render() {
        List<CustomMob> mobs = module.registry().all();
        int from = page * PER_PAGE;
        for (int i = 0; i < PER_PAGE && from + i < mobs.size(); i++) {
            CustomMob mob = mobs.get(from + i);
            set(i, Icons.withHint(module.egg(mob), "", "<dark_gray>ID: " + mob.id, "<green>Нажмите, чтобы открыть"),
                    p -> new MobEditorMenu(module, mob.copy()).open(p));
        }
        for (int i = PER_PAGE; i < size(); i++) {
            set(i, Icons.filler());
        }
        if (page > 0) {
            set(45, Icons.of(Material.ARROW, "<yellow>Назад"), p -> new MobListMenu(module, page - 1).open(p));
        }
        set(49, Icons.of(Material.BARRIER, "<red>В главное меню"), p -> new MobMainMenu(module).open(p));
        if (from + PER_PAGE < mobs.size()) {
            set(53, Icons.of(Material.ARROW, "<yellow>Вперёд"), p -> new MobListMenu(module, page + 1).open(p));
        }
    }
}
