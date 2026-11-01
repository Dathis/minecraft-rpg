package com.dathis.rpg.modules.hud;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Полоска сверху экрана с названием и ХП существа, на которое смотрит игрок.
 * После удара цель показывается ещё 3 секунды, даже если отвернуться.
 */
final class TargetHealthBar implements Runnable, Listener {

    private static final long HIT_MEMORY_MS = 3000;

    private record LastHit(UUID target, long time) {
    }

    private final int range;
    private final Map<UUID, BossBar> bars = new HashMap<>();
    private final Map<UUID, LastHit> lastHits = new HashMap<>();

    TargetHealthBar(int range) {
        this.range = range;
    }

    @Override
    public void run() {
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            LivingEntity target = findTarget(player, now);
            if (target == null) {
                hide(player);
            } else {
                show(player, target);
            }
        }
    }

    private LivingEntity findTarget(Player player, long now) {
        Entity looked = player.getTargetEntity(range);
        if (looked instanceof LivingEntity living && isShown(living)) {
            return living;
        }
        LastHit hit = lastHits.get(player.getUniqueId());
        if (hit != null && now - hit.time() < HIT_MEMORY_MS) {
            Entity entity = Bukkit.getEntity(hit.target());
            if (entity instanceof LivingEntity living && isShown(living)
                    && entity.getWorld().equals(player.getWorld())) {
                return living;
            }
        }
        return null;
    }

    private static boolean isShown(LivingEntity entity) {
        return entity.isValid() && !(entity instanceof ArmorStand) && !entity.isInvisible();
    }

    private void show(Player player, LivingEntity target) {
        AttributeInstance maxAttr = target.getAttribute(Attribute.MAX_HEALTH);
        double max = maxAttr != null ? maxAttr.getValue() : Math.max(1, target.getHealth());
        double health = Math.max(0, target.getHealth());
        float progress = (float) Math.max(0, Math.min(1, health / max));

        Component name = target.customName() != null
                ? target.customName()
                : Component.translatable(target.getType().translationKey());
        Component title = Component.empty()
                .append(name.colorIfAbsent(NamedTextColor.WHITE))
                .append(Component.text("   HP ", NamedTextColor.GRAY))
                .append(Component.text(round(health) + " / " + round(max), color(progress)));

        BossBar bar = bars.get(player.getUniqueId());
        if (bar == null) {
            bar = BossBar.bossBar(title, progress, barColor(progress), BossBar.Overlay.PROGRESS);
            bars.put(player.getUniqueId(), bar);
            player.showBossBar(bar);
        } else {
            bar.name(title);
            bar.progress(progress);
            bar.color(barColor(progress));
        }
    }

    private void hide(Player player) {
        BossBar bar = bars.remove(player.getUniqueId());
        if (bar != null) {
            player.hideBossBar(bar);
        }
    }

    void hideAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            hide(player);
        }
        bars.clear();
        lastHits.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player && event.getEntity() instanceof LivingEntity) {
            lastHits.put(player.getUniqueId(), new LastHit(event.getEntity().getUniqueId(), System.currentTimeMillis()));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        bars.remove(event.getPlayer().getUniqueId());
        lastHits.remove(event.getPlayer().getUniqueId());
    }

    private static String round(double value) {
        return String.valueOf((long) Math.ceil(value));
    }

    private static NamedTextColor color(float progress) {
        if (progress > 0.5f) return NamedTextColor.GREEN;
        if (progress > 0.25f) return NamedTextColor.YELLOW;
        return NamedTextColor.RED;
    }

    private static BossBar.Color barColor(float progress) {
        if (progress > 0.5f) return BossBar.Color.GREEN;
        if (progress > 0.25f) return BossBar.Color.YELLOW;
        return BossBar.Color.RED;
    }
}
