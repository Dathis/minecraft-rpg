package com.dathis.rpg.core.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/** Не даёт забирать предметы из меню и передаёт клики кнопкам. */
public final class MenuListener implements Listener {

    private final Plugin plugin;

    public MenuListener(Plugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Menu menu)) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            event.setCancelled(true);
            return;
        }
        Inventory clicked = event.getClickedInventory();
        // Открывать другое окно прямо внутри клика нельзя, поэтому действия переносим на следующий тик.
        if (clicked == event.getView().getBottomInventory()) {
            if (event.isShiftClick()) {
                // Shift-клик по своему предмету — «положить в окно». Сам предмет остаётся у игрока.
                event.setCancelled(true);
                ItemStack item = event.getCurrentItem();
                if (item != null && !item.isEmpty()) {
                    ItemStack copy = item.clone();
                    plugin.getServer().getScheduler().runTask(plugin, () -> menu.shiftAdd(player, copy));
                }
            } else if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
                event.setCancelled(true);
            }
            // Остальные клики в своём инвентаре разрешены: так можно взять предмет на курсор
            return;
        }
        event.setCancelled(true);
        if (clicked != event.getView().getTopInventory()) {
            return;
        }
        int slot = event.getSlot();
        ItemStack cursor = event.getCursor().isEmpty() ? null : event.getCursor().clone();
        plugin.getServer().getScheduler().runTask(plugin, () -> menu.click(player, slot, cursor));
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Menu menu)) {
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        boolean touchesTop = event.getRawSlots().stream().anyMatch(raw -> raw < topSize);
        if (!touchesTop) {
            return;
        }
        event.setCancelled(true);
        // Небольшое «перетаскивание» на один слот окна клиент присылает вместо клика — считаем это кликом
        if (event.getRawSlots().size() == 1 && event.getWhoClicked() instanceof Player player) {
            int slot = event.getRawSlots().iterator().next();
            ItemStack cursor = event.getOldCursor().clone();
            plugin.getServer().getScheduler().runTask(plugin, () -> menu.click(player, slot, cursor));
        }
    }
}
