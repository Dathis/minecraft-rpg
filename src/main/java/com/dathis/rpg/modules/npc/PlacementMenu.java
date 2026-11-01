package com.dathis.rpg.modules.npc;

import com.dathis.rpg.core.gui.Icons;
import com.dathis.rpg.core.gui.Menu;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Одна точка NPC в мире: телепорт, перенос, удаление. Открывается и по Shift + ПКМ по NPC. */
final class PlacementMenu extends Menu {

    private final NpcModule module;
    private final NpcPlacement placement;
    private final Menu back;
    private boolean confirmDelete;

    PlacementMenu(NpcModule module, NpcPlacement placement, Menu back) {
        super(3, Icons.text("<dark_gray>NPC: точка " + placement.id));
        this.module = module;
        this.placement = placement;
        this.back = back;
    }

    @Override
    protected void render() {
        for (int i = 0; i < size(); i++) {
            set(i, Icons.filler());
        }
        NpcTemplate npc = module.registry().get(placement.npcId);
        if (npc != null) {
            set(4, Icons.withHint(module.icon(npc), "", "<gray>Точка " + placement.id + ": " + placement.coordsText()));
            set(10, Icons.of(Material.WRITABLE_BOOK, "<yellow>Изменить NPC",
                    "<gray>Открыть редактор шаблона.", "<gray>Изменения коснутся всех его точек"),
                    p -> new NpcEditorMenu(module, npc.copy()).open(p));
        }
        set(12, Icons.of(Material.ENDER_PEARL, "<yellow>Телепорт к NPC"), p -> {
            Location location = placement.location();
            if (location != null) {
                p.closeInventory();
                p.teleport(location);
            }
        });
        set(14, Icons.of(Material.ARMOR_STAND, "<yellow>Перенести сюда",
                "<gray>NPC встанет на ваше место", "<gray>и будет смотреть туда же, куда вы"), p -> {
            p.closeInventory();
            module.movePlacement(placement, p.getLocation());
            module.plugin().messages().send(p, "<green>NPC перенесён.");
        });
        set(16, Icons.of(confirmDelete ? Material.TNT : Material.BARRIER,
                confirmDelete ? "<red><bold>Точно убрать?" : "<red>Убрать NPC отсюда",
                confirmDelete ? "<gray>Нажмите ещё раз для подтверждения" : "<gray>Шаблон NPC останется"), this::remove);
        if (back != null) {
            set(18, Icons.of(Material.ARROW, "<gray>Назад"), back::open);
        }
    }

    private void remove(Player player) {
        if (!confirmDelete) {
            confirmDelete = true;
            redraw();
            return;
        }
        module.removePlacement(placement);
        module.plugin().messages().send(player, "<yellow>NPC убран с точки " + placement.id + ".");
        player.closeInventory();
    }
}
