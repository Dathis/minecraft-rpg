package com.dathis.rpg.modules.npc;

import com.dathis.rpg.core.gui.ChoiceMenu;
import com.dathis.rpg.core.gui.Icons;
import com.dathis.rpg.core.gui.Menu;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

/** Первое окно /create-npc. */
final class NpcMainMenu extends Menu {

    private final NpcModule module;

    NpcMainMenu(NpcModule module) {
        super(3, Icons.text("<dark_gray>NPC"));
        this.module = module;
    }

    @Override
    protected void render() {
        for (int i = 0; i < size(); i++) {
            set(i, Icons.filler());
        }
        set(11, Icons.of(Material.VILLAGER_SPAWN_EGG, "<green>Новый NPC",
                        "<gray>Торговец, квестодатель или просто житель:",
                        "<gray>тип, имя, скин, броня, оружие"),
                p -> new NpcEditorMenu(module, new NpcTemplate()).open(p));
        set(15, Icons.of(Material.CHEST, "<gold>Сохранённые NPC",
                        "<gray>Всего: <white>" + module.registry().all().size(),
                        "<gray>Изменить, поставить в мир"),
                p -> list(module, this).open(p));
    }

    static Menu list(NpcModule module, Menu back) {
        List<ChoiceMenu.Choice> choices = new ArrayList<>();
        for (NpcTemplate npc : module.registry().all()) {
            choices.add(new ChoiceMenu.Choice(
                    Icons.withHint(module.icon(npc), "", "<dark_gray>ID: " + npc.id,
                            "<gray>В мире: <white>" + module.registry().placementsOf(npc.id).size(),
                            "<green>Нажмите, чтобы открыть"),
                    p -> new NpcEditorMenu(module, npc.copy()).open(p)));
        }
        return new ChoiceMenu("Сохранённые NPC", choices, back);
    }
}
