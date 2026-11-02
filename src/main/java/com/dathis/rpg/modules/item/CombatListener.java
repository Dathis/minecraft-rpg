package com.dathis.rpg.modules.item;

import com.dathis.rpg.modules.hud.HudModule;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Урон кастомного оружия:
 * 1) случайный урон от min до max (с учётом заряда атаки, как в ванилле — спам кликами бьёт слабее);
 * 2) шанс крита — урон умножается на items.crit-multiplier из config.yml;
 * 3) вампиризм — атакующий лечится на N% от урона удара (до брони цели).
 * Броня цели и своё ХП считаются дальше, в модуле health.
 */
final class CombatListener implements Listener {

    private final ItemModule module;

    CombatListener(ItemModule module) {
        this.module = module;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
            return;
        }
        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }
        CustomItem weapon = module.registry().of(attacker.getInventory().getItemInMainHand());
        if (weapon == null || !weapon.isWeapon()) {
            return;
        }
        if (!module.plugin().levels().meets(attacker, weapon.level)) {
            // Не дорос до оружия — бьёт как рукой, без крита и вампиризма
            event.setDamage(1);
            attacker.sendActionBar(ItemFactory.plain("Нужен уровень " + weapon.level + " для этого оружия")
                    .color(net.kyori.adventure.text.format.NamedTextColor.RED));
            return;
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        double min = Math.min(weapon.damageMin, weapon.damageMax);
        double max = Math.max(weapon.damageMin, weapon.damageMax);
        double damage = max > min ? random.nextDouble(min, max) : min;

        float charge = attacker.getAttackCooldown();
        damage *= 0.2 + charge * charge * 0.8;

        if (weapon.critChance > 0 && random.nextDouble(100) < weapon.critChance) {
            damage *= module.critMultiplier();
            showCrit(attacker, event.getEntity());
            // HUD покажет эту цифру урона золотым
            event.getEntity().setMetadata(HudModule.CRIT_METADATA, new FixedMetadataValue(module.plugin(), true));
            Entity target = event.getEntity();
            module.plugin().getServer().getScheduler().runTask(module.plugin(),
                    () -> target.removeMetadata(HudModule.CRIT_METADATA, module.plugin()));
        }

        event.setDamage(damage);

        if (weapon.vampirism > 0) {
            heal(attacker, damage * weapon.vampirism / 100.0);
        }
    }

    private void showCrit(Player attacker, Entity target) {
        target.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0, target.getHeight() / 2, 0),
                15, 0.3, 0.3, 0.3, 0.1);
        attacker.playSound(attacker.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 1f);
        attacker.sendActionBar(ItemFactory.plain("Критический удар!").color(net.kyori.adventure.text.format.NamedTextColor.GOLD));
    }

    private void heal(Player player, double amount) {
        module.plugin().health().heal(player, amount);
    }
}
