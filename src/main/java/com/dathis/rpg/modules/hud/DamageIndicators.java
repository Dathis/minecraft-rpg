package com.dathis.rpg.modules.hud;

import com.dathis.rpg.RpgPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/** Цифры урона, которые всплывают в случайном месте над целью и через секунду исчезают. */
final class DamageIndicators implements Listener {

    private static final int LIFETIME_TICKS = 20;
    private static final double RISE_PER_TICK = 0.04;

    private final RpgPlugin plugin;
    private final NamespacedKey key;
    private final Set<ArmorStand> alive = new HashSet<>();

    DamageIndicators(RpgPlugin plugin, NamespacedKey key) {
        this.plugin = plugin;
        this.key = key;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity target) || target instanceof ArmorStand) {
            return;
        }
        if (!causedByPlayer(event.getDamager())) {
            return;
        }
        boolean crit = target.hasMetadata(HudModule.CRIT_METADATA);
        if (crit) {
            target.removeMetadata(HudModule.CRIT_METADATA, plugin);
        }
        // Урон по нашему ХП (после брони цели), а не сдвиг ванильных сердец
        double damage = plugin.health().dealt(event);
        if (damage <= 0) {
            return;
        }
        spawn(target, damage, crit);
    }

    private static boolean causedByPlayer(Entity damager) {
        if (damager instanceof Player) {
            return true;
        }
        return damager instanceof Projectile projectile && projectile.getShooter() instanceof Player;
    }

    private void spawn(LivingEntity target, double damage, boolean crit) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Location location = target.getLocation().add(
                random.nextDouble(-0.6, 0.6),
                target.getHeight() + random.nextDouble(0.1, 0.5),
                random.nextDouble(-0.6, 0.6));

        Component text = Component.text("-" + format(damage) + (crit ? "!" : ""))
                .color(crit ? NamedTextColor.GOLD : NamedTextColor.RED)
                .decoration(TextDecoration.BOLD, crit);

        ArmorStand stand = target.getWorld().spawn(location, ArmorStand.class, as -> {
            as.setInvisible(true);
            as.setMarker(true);
            as.setSmall(true);
            as.setGravity(false);
            as.setInvulnerable(true);
            as.setSilent(true);
            as.setPersistent(false);
            as.setCanPickupItems(false);
            as.customName(text);
            as.setCustomNameVisible(true);
            as.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        });
        alive.add(stand);

        new BukkitRunnable() {
            private int age;

            @Override
            public void run() {
                if (!stand.isValid() || ++age >= LIFETIME_TICKS) {
                    stand.remove();
                    alive.remove(stand);
                    cancel();
                    return;
                }
                stand.teleport(stand.getLocation().add(0, RISE_PER_TICK, 0));
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /** На всякий случай чистим «застрявшие» цифры, если сервер упал, пока они были в мире. */
    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            if (entity.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) {
                entity.remove();
            }
        }
    }

    void removeAll() {
        alive.forEach(Entity::remove);
        alive.clear();
    }

    /** 20 -> "20", 7.5 -> "7.5", 0.37 -> "0.4" */
    private static String format(double value) {
        if (value >= 10) {
            return String.valueOf(Math.round(value));
        }
        double rounded = Math.round(value * 10) / 10.0;
        return rounded == Math.rint(rounded)
                ? String.valueOf((long) rounded)
                : String.format(Locale.ROOT, "%.1f", rounded);
    }
}
