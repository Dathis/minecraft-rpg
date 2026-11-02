package com.dathis.rpg.modules.ability;

import com.dathis.rpg.core.gui.Icons;
import com.dathis.rpg.core.gui.Menu;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

/** Первое окно /create-ability: какую механику берём за основу, и список сохранённых способностей. */
final class AbilityMainMenu extends Menu {

    private final AbilityModule module;

    AbilityMainMenu(AbilityModule module) {
        super(3, Icons.text("<dark_gray>Создание способностей"));
        this.module = module;
    }

    @Override
    protected void render() {
        for (int i = 0; i < size(); i++) {
            set(i, Icons.filler());
        }
        int slot = 10;
        for (Mechanic mechanic : Mechanic.values()) {
            List<String> lore = new ArrayList<>(Icons.wrap(mechanic.description, 34, "<gray>"));
            lore.add("");
            lore.add("<green>Нажмите, чтобы создать");
            set(slot++, Icons.of(mechanic.icon, "<light_purple>" + mechanic.label, lore),
                    p -> new AbilityEditorMenu(module, new Ability(mechanic)).open(p));
        }
        set(16, Icons.of(Material.CHEST, "<gold>Сохранённые способности",
                        "<gray>Всего: <white>" + module.registry().all().size(),
                        "<gray>Изменить или получить руну"),
                p -> new AbilityListMenu(module, 0).open(p));
        set(22, Icons.of(Material.BARRIER, "<red>Закрыть"), org.bukkit.entity.Player::closeInventory);
    }
}
