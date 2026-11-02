package com.dathis.rpg.modules.health;

import com.dathis.rpg.RpgPlugin;
import com.dathis.rpg.core.combat.HealthService;
import com.dathis.rpg.core.combat.HealthService.Stats;
import com.dathis.rpg.core.input.Numbers;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Перехват урона и лечения для сущностей с нашим ХП.
 *
 * Урон (HIGHEST, после того как оружие и мобы поставили свой урон):
 * 1) урон от окружения (падение, огонь, лава, голод, яд, взрыв TNT...) — доля от максимума, как в ванилле;
 * 2) броня срезает долю урона: броня / (броня + K);
 * 3) эффект «Сопротивление» — минус 20% за уровень, щит — как в ванилле, жёлтые сердца поглощения — тоже доля;
 * 4) результат вычитается из нашего ХП, а ванильный урон подменяется так, чтобы сердца встали на нужную долю.
 *    Ванильные броня, зачарования защиты, сопротивление и поглощение обнуляются (мы их уже учли).
 */
final class HealthListener implements Listener, Runnable {

    /** Причины, от которых защищает броня (как в ванилле: не от падения, голода, яда, утопления...). */
    private static final Set<DamageCause> ARMORED = EnumSet.of(
            DamageCause.ENTITY_ATTACK, DamageCause.ENTITY_SWEEP_ATTACK, DamageCause.PROJECTILE,
            DamageCause.ENTITY_EXPLOSION, DamageCause.BLOCK_EXPLOSION, DamageCause.CONTACT, DamageCause.THORNS,
            DamageCause.FALLING_BLOCK, DamageCause.LIGHTNING, DamageCause.FIRE, DamageCause.FIRE_TICK,
            DamageCause.LAVA, DamageCause.HOT_FLOOR);

    /** Причины, от которых ничто не спасает. */
    private static final Set<DamageCause> UNSTOPPABLE = EnumSet.of(
            DamageCause.KILL, DamageCause.VOID, DamageCause.SUICIDE, DamageCause.WORLD_BORDER);

    private final RpgPlugin plugin;
    private final HealthService health;
    /** ХП до урона: если событие потом кто-то отменил, вернём как было. */
    private final Map<EntityDamageEvent, Double> before = new WeakHashMap<>();

    HealthListener(RpgPlugin plugin, HealthService health) {
        this.plugin = plugin;
        this.health = health;
    }

    @Override
    public void run() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            health.sync(player);
        }
    }

    // ---------- урон ----------

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity) || entity.isDead()) {
            return;
        }
        Stats stats = health.stats(entity);
        if (stats == null) {
            return;
        }
        double raw = event.getDamage();
        if (raw <= 0) {
            return;
        }
        DamageCause cause = event.getCause();
        double vanillaMax = HealthService.vanillaMax(entity);
        double damage = raw;

        // 1) окружение — доля от максимума
        if (!byLivingEntity(event) && plugin.getConfig().getBoolean("health.scale-environment-damage", true)) {
            damage = raw / vanillaMax * stats.maxHealth();
        }

        if (!UNSTOPPABLE.contains(cause)) {
            // 2) броня
            if (ARMORED.contains(cause)) {
                damage *= 1 - health.armorReduction(stats.armor());
            }
            // 3) сопротивление, щит
            PotionEffect resistance = entity.getPotionEffect(PotionEffectType.RESISTANCE);
            if (resistance != null) {
                damage *= Math.max(0, 1 - 0.2 * (resistance.getAmplifier() + 1));
            }
            damage *= 1 - blockedShare(event, raw);
        }

        // 3) поглощение (жёлтые сердца): у нас это та же доля от максимума
        double absorption = entity.getAbsorptionAmount();
        if (absorption > 0 && damage > 0 && !UNSTOPPABLE.contains(cause)) {
            double shield = absorption / vanillaMax * stats.maxHealth();
            double absorbed = Math.min(shield, damage);
            damage -= absorbed;
            entity.setAbsorptionAmount(Math.max(0, absorption - absorbed / stats.maxHealth() * vanillaMax));
        }

        double current = health.current(entity, stats);
        double left = current - damage;
        if (cause == DamageCause.POISON) {
            // Яд в ванилле не убивает — оставляет 1 ХП
            left = Math.max(left, Math.min(current, 1));
            damage = current - left;
        }
        before.put(event, current);
        health.recordDealt(event, damage);

        double vanillaHealth = entity.getHealth();
        if (left <= 0) {
            // Смерть по-настоящему через ванильный урон: сообщение о смерти, тотем, лут
            health.store(entity, 0);
            health.forget(entity);
            setFinalDamage(event, vanillaHealth + 1000);
        } else {
            health.store(entity, left);
            double target = health.mirror(entity, left, stats.maxHealth());
            health.expectMirror(entity, Math.min(vanillaHealth, target));
            setFinalDamage(event, Math.max(0, vanillaHealth - target));
        }
        if (entity instanceof Player player) {
            showBar(player, Math.max(0, left), stats);
        }
    }

    /** Если урон потом отменили (защита региона, другой плагин) — возвращаем ХП. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamageResult(EntityDamageEvent event) {
        Double old = before.remove(event);
        if (old == null || !(event.getEntity() instanceof LivingEntity entity)) {
            return;
        }
        if (event.isCancelled()) {
            health.store(entity, old);
            health.forget(entity);
            return;
        }
        // Ванильное ХП меняется после события — на следующем тике проверим, что сердца на месте
        if (!entity.isDead()) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!entity.isDead()) {
                    health.sync(entity);
                }
            });
        }
    }

    private static boolean byLivingEntity(EntityDamageEvent event) {
        if (!(event instanceof EntityDamageByEntityEvent byEntity)) {
            return false;
        }
        Entity damager = byEntity.getDamager();
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof LivingEntity) {
            return true;
        }
        return damager instanceof LivingEntity;
    }

    /** Какую долю урона ванилла сняла щитом (0..1). */
    @SuppressWarnings({"deprecation", "removal"})
    private static double blockedShare(EntityDamageEvent event, double raw) {
        if (!event.isApplicable(EntityDamageEvent.DamageModifier.BLOCKING)) {
            return 0;
        }
        double blocked = -event.getDamage(EntityDamageEvent.DamageModifier.BLOCKING);
        return Math.max(0, Math.min(1, blocked / raw));
    }

    /**
     * Ставит ванильный урон ровно value: базовый урон = value, а ванильные броня, защита, сопротивление,
     * поглощение и щит обнуляются (мы их уже посчитали сами).
     */
    @SuppressWarnings({"deprecation", "removal"})
    private static void setFinalDamage(EntityDamageEvent event, double value) {
        event.setDamage(value);
        for (EntityDamageEvent.DamageModifier modifier : EntityDamageEvent.DamageModifier.values()) {
            if (modifier != EntityDamageEvent.DamageModifier.BASE && event.isApplicable(modifier)) {
                event.setDamage(modifier, 0);
            }
        }
    }

    // ---------- лечение ----------

    /** Ванильное лечение (еда, зелья, золотое яблоко, маяк) — та же доля от нашего максимума. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRegain(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) {
            return;
        }
        Stats stats = health.stats(entity);
        if (stats == null) {
            return;
        }
        double amount = event.getAmount();
        if (plugin.getConfig().getBoolean("health.scale-vanilla-healing", true)) {
            amount = amount / HealthService.vanillaMax(entity) * stats.maxHealth();
        }
        event.setCancelled(true);
        double current = health.current(entity, stats);
        if (current >= stats.maxHealth()) {
            return;
        }
        health.heal(entity, amount);
        if (entity instanceof Player player) {
            showBar(player, health.current(entity, stats), stats);
        }
    }

    /** Тотем бессмертия: ванилла ставит 1 ХП — берём ту же долю. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onResurrect(EntityResurrectEvent event) {
        LivingEntity entity = event.getEntity();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Stats stats = health.stats(entity);
            if (stats != null && !entity.isDead()) {
                health.store(entity, stats.maxHealth() * entity.getHealth() / HealthService.vanillaMax(entity));
                health.forget(entity);
                health.sync(entity);
            }
        });
    }

    // ---------- игроки ----------

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        health.forget(player);
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                health.fill(player);
            }
        });
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                prepare(player);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        health.forget(event.getPlayer());
    }

    /** Убирает следы старой версии (сжатие сердец, модификаторы ХП и брони) и ставит сердца по нашему ХП. */
    void prepare(Player player) {
        if (player.isHealthScaled()) {
            player.setHealthScaled(false);
        }
        removeModifier(player, Attribute.MAX_HEALTH, new NamespacedKey(plugin, "custom_health"));
        removeModifier(player, Attribute.ARMOR, new NamespacedKey(plugin, "custom_armor"));
        health.forget(player);
        health.sync(player);
    }

    private static void removeModifier(Player player, Attribute attribute, NamespacedKey key) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null) {
            instance.removeModifier(key);
        }
    }

    private void showBar(Player player, double current, Stats stats) {
        if (!plugin.getConfig().getBoolean("health.show-in-action-bar", true)) {
            return;
        }
        double share = current / stats.maxHealth();
        NamedTextColor color = share > 0.5 ? NamedTextColor.GREEN : share > 0.25 ? NamedTextColor.YELLOW : NamedTextColor.RED;
        Component text = Component.text("ХП ", NamedTextColor.GRAY)
                .append(Component.text(Numbers.format(Math.ceil(current)) + " / " + Numbers.format(Math.ceil(stats.maxHealth())), color));
        if (stats.armor() > 0) {
            text = text.append(Component.text("   Броня ", NamedTextColor.GRAY))
                    .append(Component.text(Numbers.format(Math.round(stats.armor())), NamedTextColor.AQUA));
        }
        if (plugin.mana().isActive()) {
            text = text.append(Component.text("   Мана ", NamedTextColor.GRAY))
                    .append(Component.text(Numbers.format(Math.floor(plugin.mana().mana(player))) + " / "
                            + Numbers.format(Math.floor(plugin.mana().max(player))), NamedTextColor.BLUE));
        }
        player.sendActionBar(text);
    }
}
