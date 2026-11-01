package com.dathis.rpg.modules.mob;

import com.dathis.rpg.core.gui.Icons;
import com.dathis.rpg.core.gui.Menu;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;

/** Первое окно /create-mob. */
final class MobMainMenu extends Menu {

    private final MobModule module;

    MobMainMenu(MobModule module) {
        super(3, Icons.text("<dark_gray>Создание мобов"));
        this.module = module;
    }

    @Override
    protected void render() {
        for (int i = 0; i < size(); i++) {
            set(i, Icons.filler());
        }
        set(11, Icons.of(Material.ZOMBIE_SPAWN_EGG, "<green>Новый моб",
                        "<gray>На основе ванильного моба:",
                        "<gray>название, ранг, ХП, урон, лут"),
                p -> new MobEditorMenu(module, new CustomMob(EntityType.ZOMBIE, "Новый моб")).open(p));
        set(15, Icons.of(Material.CHEST, "<gold>Сохранённые мобы",
                        "<gray>Всего: <white>" + module.registry().all().size(),
                        "<gray>Изменить, получить яйцо, призвать"),
                p -> new MobListMenu(module, 0).open(p));
    }
}
