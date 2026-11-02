package com.dathis.rpg.modules.mob;

import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalType;
import com.destroystokyo.paper.entity.ai.MobGoals;
import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Поведение кастомных мобов:
 * <ul>
 *   <li>сами не бродят — стоят на месте спавна и только оглядываются;</li>
 *   <li>враждебный моб нападает на игрока, которого видит; невраждебный — только в ответ на удар;</li>
 *   <li>отошёл от места спавна дальше радиуса привязи — бросает цель и бежит обратно.</li>
 * </ul>
 * Ванильные задачи «бродить» и «выбирать цель» у моба снимаются, цель ему назначаем только мы.
 * Задачи не сохраняются в мире, поэтому снимаем их каждый раз, когда моб появляется в мире (спавн, загрузка чанка).
 */
final class MobBrain implements Listener, Runnable {

    /** Ближе этого (блоки) — моб уже дома. */
    private static final double HOME_REACHED = 2.5;
    /** Без цели и дальше этого от дома — спокойно идёт домой. */
    private static final double IDLE_DRIFT = 3;
    /** Если за это время не дошёл до дома (застрял, нет пути) — переносим домой. */
    private static final long RETURN_TIMEOUT_MS = 15_000;

    private final MobModule module;
    private final NamespacedKey homeKey;
    private final double leashRadius;
    private final double aggroRange;
    private final double returnSpeed;

    private final Map<UUID, Mob> mobs = new HashMap<>();
    /** Мобы, которые бегут домой после выхода за привязь (время начала). Пока бегут — ни на кого не реагируют. */
    private final Map<UUID, Long> returning = new HashMap<>();
    /** true, пока цель ставим мы сами: такие смены цели не отменяем. */
    private boolean assigning;

    MobBrain(MobModule module) {
        this.module = module;
        this.homeKey = new NamespacedKey(module.plugin(), "mob_home");
        this.leashRadius = Math.max(4, module.plugin().getConfig().getDouble("mobs.leash-radius", 25));
        this.aggroRange = Math.max(0, module.plugin().getConfig().getDouble("mobs.aggro-range", 16));
        this.returnSpeed = Math.max(0.5, module.plugin().getConfig().getDouble("mobs.return-speed", 1.3));
    }

    /** Подхватить мобов, которые уже есть в мире (включение модуля, /rpg reload). */
    void start() {
        for (World world : Bukkit.getWorlds()) {
            for (Mob mob : world.getEntitiesByClass(Mob.class)) {
                track(mob);
            }
        }
    }

    void stop() {
        mobs.clear();
        returning.clear();
    }

    private void track(Mob mob) {
        CustomMob template = mob.isValid() ? module.registry().of(mob) : null;
        if (template == null) {
            return;
        }
        // Название по текущему шаблону (уровень и ранг могли поменяться, пока чанк был выгружен)
        mob.customName(template.displayName());
        if (home(mob) == null) {
            setHome(mob, mob.getLocation());
        }
        calm(mob);
        mobs.put(mob.getUniqueId(), mob);
    }

    @EventHandler
    public void onAdd(EntityAddToWorldEvent event) {
        if (event.getEntity() instanceof Mob mob) {
            // задачи моба меняем уже после добавления в мир
            Bukkit.getScheduler().runTask(module.plugin(), () -> track(mob));
        }
    }

    @EventHandler
    public void onRemove(EntityRemoveFromWorldEvent event) {
        UUID id = event.getEntity().getUniqueId();
        mobs.remove(id);
        returning.remove(id);
    }

    /** Снять ванильные задачи «бродить» и «выбирать цель». Остаются атака, оглядывание, плавание и т.п. */
    private static void calm(Mob mob) {
        MobGoals goals = Bukkit.getMobGoals();
        for (Goal<Mob> goal : new ArrayList<>(goals.getAllGoals(mob))) {
            String key = goal.getKey().getNamespacedKey().getKey().toLowerCase(Locale.ROOT);
            if (goal.getTypes().contains(GoalType.TARGET) || isWander(key)) {
                goals.removeGoal(mob, goal);
            }
        }
    }

    private static boolean isWander(String key) {
        return key.contains("stroll") || key.contains("wander") || key.contains("random_swim")
                || key.contains("random_fly") || key.contains("random_stand") || key.contains("village")
                || key.contains("move_towards_restriction");
    }

    // ---------- каждые полсекунды ----------

    @Override
    public void run() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Mob>> it = mobs.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Mob> entry = it.next();
            Mob mob = entry.getValue();
            CustomMob template = mob.isValid() ? module.registry().of(mob) : null;
            if (template == null) {
                it.remove();
                returning.remove(entry.getKey());
                continue;
            }
            try {
                tick(mob, template, now);
            } catch (Exception e) {
                module.plugin().getLogger().warning("Ошибка поведения моба " + template.id + ": " + e.getMessage());
            }
        }
    }

    private void tick(Mob mob, CustomMob template, long now) {
        Location home = home(mob);
        if (home == null || home.getWorld() != mob.getWorld()) {
            return;
        }
        double dist2 = mob.getLocation().distanceSquared(home);

        Long since = returning.get(mob.getUniqueId());
        if (since != null) {
            if (dist2 <= HOME_REACHED * HOME_REACHED) {
                returning.remove(mob.getUniqueId());
                mob.getPathfinder().stopPathfinding();
            } else if (now - since > RETURN_TIMEOUT_MS) {
                returning.remove(mob.getUniqueId());
                mob.getPathfinder().stopPathfinding();
                mob.teleport(home);
            } else {
                setTarget(mob, null);
                mob.getPathfinder().moveTo(home, returnSpeed);
            }
            return;
        }

        if (dist2 > leashRadius * leashRadius) {
            returning.put(mob.getUniqueId(), now);
            setTarget(mob, null);
            mob.getPathfinder().moveTo(home, returnSpeed);
            return;
        }

        LivingEntity target = mob.getTarget();
        if (target != null && !canChase(mob, target)) {
            setTarget(mob, null);
            target = null;
        }
        if (target == null && template.hostile) {
            Player seen = nearestVisible(mob, home);
            if (seen != null) {
                setTarget(mob, seen);
                target = seen;
            }
        }
        if (target == null && dist2 > IDLE_DRIFT * IDLE_DRIFT && !mob.getPathfinder().hasPath()) {
            // бой закончился — спокойно возвращается на своё место
            mob.getPathfinder().moveTo(home, 1.0);
        }
    }

    private boolean canChase(Mob mob, LivingEntity target) {
        if (!target.isValid() || target.isDead() || target.getWorld() != mob.getWorld()) {
            return false;
        }
        return !(target instanceof Player player) || isFair(player);
    }

    private static boolean isFair(Player player) {
        return player.isOnline() && !player.isDead()
                && (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE);
    }

    /** Ближайший игрок, которого моб видит и который стоит в пределах привязи (иначе моб будет бегать туда-сюда). */
    private Player nearestVisible(Mob mob, Location home) {
        Location at = mob.getLocation();
        Player best = null;
        double bestDist = aggroRange * aggroRange;
        for (Player player : mob.getWorld().getPlayers()) {
            if (!isFair(player)) {
                continue;
            }
            double d = player.getLocation().distanceSquared(at);
            if (d > bestDist || player.getLocation().distanceSquared(home) > leashRadius * leashRadius) {
                continue;
            }
            if (mob.hasLineOfSight(player)) {
                best = player;
                bestDist = d;
            }
        }
        return best;
    }

    private void setTarget(Mob mob, LivingEntity target) {
        if (mob.getTarget() == target) {
            return;
        }
        assigning = true;
        try {
            mob.setTarget(target);
        } finally {
            assigning = false;
        }
    }

    // ---------- события ----------

    /** Удар игрока (рукой или снарядом) — моб отвечает, если сейчас не бежит домой. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHurt(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || !mobs.containsKey(mob.getUniqueId())
                || returning.containsKey(mob.getUniqueId())) {
            return;
        }
        Entity damager = event.getDamager();
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            damager = shooter;
        }
        if (damager instanceof Player player && isFair(player)) {
            setTarget(mob, player);
        }
    }

    /** Ванилла сама цель кастомному мобу не выбирает (в том числе мобы с «мозгом», например пиглины). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTarget(EntityTargetEvent event) {
        if (assigning || event.getTarget() == null || !mobs.containsKey(event.getEntity().getUniqueId())) {
            return;
        }
        event.setCancelled(true);
    }

    // ---------- место спавна ----------

    private Location home(Entity entity) {
        String raw = entity.getPersistentDataContainer().get(homeKey, PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        String[] parts = raw.split(";");
        if (parts.length != 4) {
            return null;
        }
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) {
            return null;
        }
        try {
            return new Location(world, Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), Double.parseDouble(parts[3]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void setHome(Entity entity, Location at) {
        entity.getPersistentDataContainer().set(homeKey, PersistentDataType.STRING,
                at.getWorld().getName() + ";" + at.getX() + ";" + at.getY() + ";" + at.getZ());
    }
}
