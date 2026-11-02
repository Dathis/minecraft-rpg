package com.dathis.rpg.modules.region;

import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** ПКМ с бумагой на участок в руке открывает карту земли для выбора места. */
final class PlotListener implements Listener {

    private final RegionModule module;
    /** Клик по блоку иногда приходит вместе с кликом «в воздух» — не открываем окно дважды. */
    private final Map<UUID, Long> lastUse = new HashMap<>();

    PlotListener(RegionModule module) {
        this.module = module;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (module.deeds().read(event.getItem()) == null) {
            return;
        }
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        long now = System.currentTimeMillis();
        Long last = lastUse.put(event.getPlayer().getUniqueId(), now);
        if (last != null && now - last < 300) {
            return;
        }
        module.useDeed(event.getPlayer(), event.getItem());
    }
}
