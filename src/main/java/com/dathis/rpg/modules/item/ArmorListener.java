package com.dathis.rpg.modules.item;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Статы кастомной брони. Раз в секунду для каждого игрока суммируются броня, +ХП и регенерация
 * всех надетых кастомных предметов и выставляются как временные модификаторы атрибутов игрока.
 * Так изменения шаблона в /create-item сразу действуют на уже выданную броню.
 *
 * Сердца: при большом ХП Minecraft рисует много рядов сердец, поэтому показ сжимается до
 * items.health-scale ХП (по умолчанию 40 = 2 ряда). Настоящее ХП видно в полоске сверху (HUD).
 */
final class ArmorListener implements Listener, Runnable {

    private static final long REGEN_PAUSE_MS = 5000;

    private record Stats(double armor, double health, double regen) {
        static final Stats ZERO = new Stats(0, 0, 0);
    }

    private final ItemModule module;
    private final NamespacedKey armorKey;
    private final NamespacedKey healthKey;
    private final Map<UUID, Stats> applied = new HashMap<>();
    private final Map<UUID, Long> lastDamage = new HashMap<>();

    ArmorListener(ItemModule module) {
        this.module = module;
        this.armorKey = new NamespacedKey(module.plugin(), "custom_armor");
        this.healthKey = new NamespacedKey(module.plugin(), "custom_health");
    }

    @Override
    public void run() {
        long now = System.currentTimeMillis();
        for (Player player : module.plugin().getServer().getOnlinePlayers()) {
            Stats stats = collect(player);
            apply(player, stats);
            regenerate(player, stats, now);
        }
    }

    private Stats collect(Player player) {
        double armor = 0, health = 0, regen = 0;
        for (ItemStack piece : player.getInventory().getArmorContents()) {
            CustomItem item = module.registry().of(piece);
            if (item != null && item.isArmor()) {
                armor += item.armor;
                health += item.bonusHealth;
                regen += item.regeneration;
            }
        }
        return new Stats(armor, health, regen);
    }

    private void apply(Player player, Stats stats) {
        Stats old = applied.getOrDefault(player.getUniqueId(), Stats.ZERO);
        if (old.armor() != stats.armor()) {
            set(player, Attribute.ARMOR, armorKey, stats.armor());
        }
        if (old.health() != stats.health()) {
            set(player, Attribute.MAX_HEALTH, healthKey, stats.health());
            AttributeInstance max = player.getAttribute(Attribute.MAX_HEALTH);
            if (max != null && player.getHealth() > max.getValue()) {
                player.setHealth(max.getValue());
            }
        }
        applied.put(player.getUniqueId(), stats);
        updateHealthScale(player);
    }

    private static void set(Player player, Attribute attribute, NamespacedKey key, double value) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        instance.removeModifier(key);
        if (value != 0) {
            instance.addTransientModifier(new AttributeModifier(key, value, AttributeModifier.Operation.ADD_NUMBER));
        }
    }

    private void updateHealthScale(Player player) {
        double scale = module.plugin().getConfig().getDouble("items.health-scale", 40);
        AttributeInstance max = player.getAttribute(Attribute.MAX_HEALTH);
        if (scale <= 0 || max == null || max.getValue() <= scale) {
            if (player.isHealthScaled()) {
                player.setHealthScaled(false);
            }
            return;
        }
        if (!player.isHealthScaled() || player.getHealthScale() != scale) {
            player.setHealthScale(scale);
            player.setHealthScaled(true);
        }
    }

    private void regenerate(Player player, Stats stats, long now) {
        if (stats.regen() <= 0 || player.isDead()) {
            return;
        }
        Long hurt = lastDamage.get(player.getUniqueId());
        if (hurt != null && now - hurt < REGEN_PAUSE_MS) {
            return;
        }
        AttributeInstance max = player.getAttribute(Attribute.MAX_HEALTH);
        double cap = max != null ? max.getValue() : 20;
        if (player.getHealth() < cap) {
            player.setHealth(Math.min(cap, player.getHealth() + stats.regen()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            lastDamage.put(player.getUniqueId(), System.currentTimeMillis());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // Модификаторы временные и после входа их нет — пересчитаем на следующем тике
        applied.remove(event.getPlayer().getUniqueId());
        module.plugin().getServer().getScheduler().runTask(module.plugin(), () -> refresh(event.getPlayer()));
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        applied.remove(event.getPlayer().getUniqueId());
        module.plugin().getServer().getScheduler().runTask(module.plugin(), () -> refresh(event.getPlayer()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        applied.remove(event.getPlayer().getUniqueId());
        lastDamage.remove(event.getPlayer().getUniqueId());
    }

    private void refresh(Player player) {
        if (player.isOnline()) {
            apply(player, collect(player));
        }
    }

    /** Снимает все бонусы (при выключении модуля или /rpg reload). */
    void removeAll() {
        for (Player player : module.plugin().getServer().getOnlinePlayers()) {
            set(player, Attribute.ARMOR, armorKey, 0);
            set(player, Attribute.MAX_HEALTH, healthKey, 0);
            AttributeInstance max = player.getAttribute(Attribute.MAX_HEALTH);
            if (max != null && player.getHealth() > max.getValue()) {
                player.setHealth(max.getValue());
            }
            player.setHealthScaled(false);
        }
        applied.clear();
    }
}
