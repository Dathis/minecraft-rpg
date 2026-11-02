package com.dathis.rpg.modules.item;

import com.dathis.rpg.core.combat.HealthService;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Статы кастомной брони. Раз в секунду для каждого игрока суммируются броня, +ХП и регенерация
 * всех надетых кастомных предметов. Броню и ХП забирает {@link HealthService} (своё ХП без
 * ванильных пределов), регенерация лечит каждую секунду. Так изменения шаблона в /create-item
 * сразу действуют на уже выданную броню.
 */
final class ArmorListener implements Listener, Runnable, HealthService.Provider {

    private static final long REGEN_PAUSE_MS = 5000;

    private record Stats(double armor, double health, double regen) {
        static final Stats ZERO = new Stats(0, 0, 0);
    }

    private final ItemModule module;
    private final Map<UUID, Stats> worn = new HashMap<>();
    private final Map<UUID, Long> lastDamage = new HashMap<>();
    /** Сколько надетых вещей не подходит по уровню (чтобы предупреждать только при изменении). */
    private final Map<UUID, Integer> tooHigh = new HashMap<>();

    ArmorListener(ItemModule module) {
        this.module = module;
    }

    @Override
    public void run() {
        long now = System.currentTimeMillis();
        for (Player player : module.plugin().getServer().getOnlinePlayers()) {
            Stats stats = collect(player);
            worn.put(player.getUniqueId(), stats);
            regenerate(player, stats, now);
        }
    }

    @Override
    public HealthService.Bonus bonus(LivingEntity entity) {
        if (!(entity instanceof Player player)) {
            return null;
        }
        Stats stats = worn.get(player.getUniqueId());
        if (stats == null) {
            // Только что зашёл — посчитаем сразу, не дожидаясь задачи
            stats = collect(player);
            worn.put(player.getUniqueId(), stats);
        }
        return new HealthService.Bonus(stats.health(), stats.armor());
    }

    private Stats collect(Player player) {
        double armor = 0, health = 0, regen = 0;
        int locked = 0;
        CustomItem lockedItem = null;
        for (ItemStack piece : player.getInventory().getArmorContents()) {
            CustomItem item = module.registry().of(piece);
            if (item != null && item.isArmor()) {
                if (!module.plugin().levels().meets(player, item.level)) {
                    // Не дорос — броня надета, но статов не даёт
                    locked++;
                    lockedItem = item;
                    continue;
                }
                armor += item.armor;
                health += item.bonusHealth;
                regen += item.regeneration;
            }
        }
        Integer before = tooHigh.put(player.getUniqueId(), locked);
        if (lockedItem != null && locked > (before == null ? 0 : before)) {
            module.plugin().messages().send(player, "<red>«" + net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().escapeTags(lockedItem.name) + "» требует уровень "
                    + lockedItem.level + ": пока она ничего не даёт.</red>");
        }
        return health == 0 && armor == 0 && regen == 0 ? Stats.ZERO : new Stats(armor, health, regen);
    }

    private void regenerate(Player player, Stats stats, long now) {
        if (stats.regen() <= 0 || player.isDead()) {
            return;
        }
        Long hurt = lastDamage.get(player.getUniqueId());
        if (hurt != null && now - hurt < REGEN_PAUSE_MS) {
            return;
        }
        HealthService health = module.plugin().health();
        if (health.health(player) < health.maxHealth(player)) {
            health.heal(player, stats.regen());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            lastDamage.put(player.getUniqueId(), System.currentTimeMillis());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        worn.remove(event.getPlayer().getUniqueId());
        lastDamage.remove(event.getPlayer().getUniqueId());
        tooHigh.remove(event.getPlayer().getUniqueId());
    }

    void clear() {
        worn.clear();
        lastDamage.clear();
        tooHigh.clear();
    }
}
