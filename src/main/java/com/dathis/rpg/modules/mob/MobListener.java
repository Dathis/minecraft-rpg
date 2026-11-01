package com.dathis.rpg.modules.mob;

import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.ThreadLocalRandom;

/** Урон, лут и яйца призыва кастомных мобов. */
final class MobListener implements Listener {

    private final MobModule module;

    MobListener(MobModule module) {
        this.module = module;
    }

    /** Урон моба (в ближнем бою или его снарядом) — случайный от min до max. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        Entity source = event.getDamager();
        if (source instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            source = shooter;
        }
        CustomMob mob = module.registry().of(source);
        if (mob == null) {
            return;
        }
        double damage = mob.damageMax > mob.damageMin
                ? ThreadLocalRandom.current().nextDouble(mob.damageMin, mob.damageMax)
                : mob.damageMin;
        event.setDamage(damage);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(EntityDeathEvent event) {
        CustomMob mob = module.registry().of(event.getEntity());
        if (mob == null) {
            return;
        }
        if (!mob.vanillaDrops) {
            event.getDrops().clear();
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (MobDrop drop : mob.drops) {
            if (drop.chance >= 100 || random.nextDouble(100) < drop.chance) {
                event.getDrops().add(drop.item.clone());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUseEgg(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta()) {
            return;
        }
        CustomMob mob = module.registry().of(item.getItemMeta());
        if (mob == null) {
            return;
        }
        // Своё яйцо никогда не работает как ванильное (в том числе по спавнеру)
        event.setCancelled(true);
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Block clicked = event.getClickedBlock();
        if (clicked == null) {
            return;
        }
        Block target = clicked.getRelative(event.getBlockFace());
        module.spawn(mob, target.getLocation().add(0.5, 0, 0.5));
        Player player = event.getPlayer();
        if (player.getGameMode() != GameMode.CREATIVE) {
            item.subtract(1);
        }
    }

    /** Клик яйцом по мобу в ванилле спавнит детёныша — для своих яиц это отключаем. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEggOnEntity(PlayerInteractEntityEvent event) {
        ItemStack item = event.getPlayer().getInventory().getItem(event.getHand());
        if (item.hasItemMeta() && module.registry().of(item.getItemMeta()) != null) {
            event.setCancelled(true);
        }
    }

    /** Раздатчик со своим яйцом ничего не спавнит. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDispense(BlockDispenseEvent event) {
        ItemStack item = event.getItem();
        if (item.hasItemMeta() && module.registry().of(item.getItemMeta()) != null) {
            event.setCancelled(true);
        }
    }
}
