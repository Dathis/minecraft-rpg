package com.dathis.rpg.modules.ability;

import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Руны в руке и в инвентаре: нажатие применяет способность, перезаряжающуюся руну нельзя переложить
 * или выбросить, при смерти и выходе стопки рун снова становятся по одной.
 */
final class RuneListener implements Listener {

    private final AbilityModule module;

    RuneListener(AbilityModule module) {
        this.module = module;
    }

    // ---------- применение ----------

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.PHYSICAL || !module.registry().isRune(event.getItem())) {
            return;
        }
        // Руна не ставится как блок, не бросается и не ломает блоки
        event.setCancelled(true);
        boolean left = event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK;
        // ЛКМ — запасной способ: на Bedrock «использовать» в воздухе срабатывает не для всех предметов
        if (left && !module.plugin().getConfig().getBoolean("abilities.cast-on-left-click", true)) {
            return;
        }
        module.cast(event.getPlayer(), event.getItem(), null);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        ItemStack hand = event.getHand() == EquipmentSlot.OFF_HAND
                ? player.getInventory().getItemInOffHand()
                : player.getInventory().getItemInMainHand();
        if (!module.registry().isRune(hand)) {
            return;
        }
        if (event.getRightClicked() instanceof ItemFrame) {
            event.setCancelled(true); // руну в рамку не кладём
            return;
        }
        // NPC (неуязвимые) — как обычно: разговор, торговля. По мобу и игроку — способность с этой целью.
        if (event.getRightClicked() instanceof LivingEntity living && !living.isInvulnerable()) {
            event.setCancelled(true);
            module.cast(player, hand, living);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onArmorStand(PlayerArmorStandManipulateEvent event) {
        if (module.registry().isRune(event.getPlayerItem())) {
            event.setCancelled(true);
        }
    }

    // ---------- перезаряжающаяся руна ----------

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        boolean locked = module.isLocked(player, event.getCurrentItem()) || module.isLocked(player, event.getCursor());
        if (!locked && event.getClick() == ClickType.NUMBER_KEY && event.getHotbarButton() >= 0) {
            locked = module.isLocked(player, player.getInventory().getItem(event.getHotbarButton()));
        }
        if (!locked && event.getClick() == ClickType.SWAP_OFFHAND) {
            locked = module.isLocked(player, player.getInventory().getItemInOffHand());
        }
        if (locked) {
            event.setCancelled(true);
            module.bar(player, "<yellow>Руна перезаряжается, её пока нельзя переложить");
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && module.isLocked(player, event.getOldCursor())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (module.isLocked(event.getPlayer(), event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            module.bar(event.getPlayer(), "<yellow>Руна перезаряжается, её пока нельзя выбросить");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        for (ItemStack drop : event.getDrops()) {
            if (module.registry().isRune(drop)) {
                drop.setAmount(1);
            }
        }
        for (ItemStack kept : event.getItemsToKeep()) {
            if (module.registry().isRune(kept)) {
                kept.setAmount(1);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        module.normalizeRunes(event.getPlayer());
        module.forget(event.getPlayer());
    }
}
