package com.dathis.rpg.modules.ability;

import com.dathis.rpg.core.combat.HealthService;
import com.dathis.rpg.core.input.Numbers;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Что делают механики. Всё сделано обычными средствами сервера (скорость, телепорт, урон, частицы, звуки),
 * поэтому одинаково работает в Java и Bedrock.
 *
 * Урон от падения после полёта, прыжка, рывка и телепорта отменяется на несколько секунд.
 */
final class Effects implements Listener {

    /** Скорость, которую сервер ещё принимает без предупреждений. */
    private static final double MAX_VELOCITY = 3.9;
    private static final Particle.DustOptions DRAIN_DUST = new Particle.DustOptions(Color.fromRGB(200, 0, 0), 1.2f);

    private record Flight(boolean hadAllowFlight, BukkitTask warn, BukkitTask end) {
    }

    private final AbilityModule module;
    private final HealthService health;
    private final Map<UUID, Flight> flights = new HashMap<>();
    private final Map<UUID, BukkitTask> drains = new HashMap<>();
    /** До какого времени (мс) игрок не получает урон от падения. */
    private final Map<UUID, Long> noFall = new HashMap<>();

    Effects(AbilityModule module) {
        this.module = module;
        this.health = module.plugin().health();
    }

    /** @return текст ошибки (мана и перезарядка тогда не тратятся) или null, если способность сработала */
    String cast(Player player, Ability ability, LivingEntity clicked) {
        return switch (ability.mechanic) {
            case FLIGHT -> flight(player, ability);
            case LEAP -> leap(player, ability);
            case DASH -> dash(player, ability);
            case BLINK -> blink(player, ability);
            case LIFE_DRAIN -> lifeDrain(player, ability, clicked);
            case HEALTH_SWAP -> healthSwap(player, ability, clicked);
        };
    }

    // ---------- полёт ----------

    private String flight(Player player, Ability ability) {
        if (flights.containsKey(player.getUniqueId())) {
            return "Вы уже летите";
        }
        boolean had = player.getAllowFlight();
        player.setAllowFlight(true);
        if (player.isOnGround()) {
            player.setVelocity(player.getVelocity().setY(0.5));
        }
        player.setFlying(true);
        long ticks = Math.max(20, Math.round(ability.duration * 20));
        var scheduler = module.plugin().getServer().getScheduler();
        BukkitTask warn = ticks > 40 ? scheduler.runTaskLater(module.plugin(), () -> {
            if (player.isOnline()) {
                module.bar(player, "<yellow>Полёт заканчивается...");
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.7f, 0.8f);
            }
        }, ticks - 30) : null;
        BukkitTask end = scheduler.runTaskLater(module.plugin(), () -> endFlight(player, true), ticks);
        flights.put(player.getUniqueId(), new Flight(had, warn, end));
        player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 20, 0.4, 0.1, 0.4, 0.05);
        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 0.7f, 1.4f);
        module.bar(player, "<aqua>Полёт: " + Numbers.format(ability.duration) + " сек");
        return null;
    }

    private void endFlight(Player player, boolean softLanding) {
        Flight flight = flights.remove(player.getUniqueId());
        if (flight == null) {
            return;
        }
        if (flight.warn() != null) {
            flight.warn().cancel();
        }
        flight.end().cancel();
        GameMode mode = player.getGameMode();
        if (!flight.hadAllowFlight() && (mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE)) {
            player.setFlying(false);
            player.setAllowFlight(false);
        }
        if (softLanding && player.isOnline() && !player.isDead()) {
            int seconds = Math.max(0, module.plugin().getConfig().getInt("abilities.flight-slow-fall", 4));
            if (seconds > 0 && !player.isOnGround()) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, seconds * 20, 0, false, false, true));
            }
            protectFall(player, 15);
        }
    }

    // ---------- прыжок и рывок ----------

    private String leap(Player player, Ability ability) {
        Vector forward = horizontalLook(player);
        Vector velocity = forward.multiply(0.6 * ability.power);
        velocity.setY(0.35 + 0.3 * ability.power);
        player.setVelocity(cap(velocity));
        protectFall(player, 8);
        player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 15, 0.3, 0.05, 0.3, 0.02);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GOAT_LONG_JUMP, 1f, 1f);
        return null;
    }

    private String dash(Player player, Ability ability) {
        Vector forward = horizontalLook(player);
        // У земли игрок тормозит быстро: при подскоке 0.2 игрок пролетает примерно 6 × скорость блоков
        Vector velocity = forward.multiply(ability.range / 6.0);
        velocity.setY(0.2);
        player.setVelocity(cap(velocity));
        protectFall(player, 4);
        player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation().add(0, 0.5, 0), 12, 0.2, 0.2, 0.2, 0.05);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_1, 0.8f, 1.3f);
        return null;
    }

    private static Vector horizontalLook(Player player) {
        Vector look = player.getLocation().getDirection().setY(0);
        if (look.lengthSquared() < 1e-4) {
            // Смотрит строго вверх или вниз — направление по повороту тела
            double yaw = Math.toRadians(player.getLocation().getYaw());
            look = new Vector(-Math.sin(yaw), 0, Math.cos(yaw));
        }
        return look.normalize();
    }

    private static Vector cap(Vector velocity) {
        double length = velocity.length();
        return length > MAX_VELOCITY ? velocity.multiply(MAX_VELOCITY / length) : velocity;
    }

    // ---------- телепорт ----------

    private String blink(Player player, Ability ability) {
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        World world = player.getWorld();
        RayTraceResult hit = world.rayTraceBlocks(eye, dir, ability.range, FluidCollisionMode.NEVER, true);
        double reach = hit == null ? ability.range : hit.getHitPosition().distance(eye.toVector());
        Location dest = null;
        // Идём от самой дальней точки назад, пока не найдём место, где игрок помещается
        for (double d = reach - 0.4; d >= 1.5 && dest == null; d -= 0.5) {
            Block block = eye.clone().add(dir.clone().multiply(d)).getBlock();
            dest = standAt(block.getRelative(BlockFace.DOWN));
            if (dest == null) {
                dest = standAt(block);
            }
        }
        if (dest == null || dest.distanceSquared(player.getLocation()) < 2.25) {
            return "Некуда переместиться";
        }
        if (module.isForeignPlot(player, dest)) {
            return "Туда нельзя: чужой участок";
        }
        Location from = player.getLocation();
        dest.setYaw(from.getYaw());
        dest.setPitch(from.getPitch());
        world.spawnParticle(Particle.PORTAL, from.clone().add(0, 1, 0), 40, 0.3, 0.6, 0.3, 0.3);
        world.playSound(from, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.2f);
        player.setFallDistance(0);
        player.teleport(dest, PlayerTeleportEvent.TeleportCause.PLUGIN);
        protectFall(player, 6);
        world.spawnParticle(Particle.REVERSE_PORTAL, dest.clone().add(0, 1, 0), 30, 0.3, 0.6, 0.3, 0.05);
        world.playSound(dest, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.4f);
        return null;
    }

    /** Место для ног: блок ног и головы проходимы и не жидкость. */
    private static Location standAt(Block feet) {
        World world = feet.getWorld();
        if (feet.getY() < world.getMinHeight() || feet.getY() + 1 >= world.getMaxHeight()) {
            return null;
        }
        Block head = feet.getRelative(BlockFace.UP);
        if (!feet.isPassable() || !head.isPassable() || feet.isLiquid() || head.isLiquid()) {
            return null;
        }
        return feet.getLocation().add(0.5, 0, 0.5);
    }

    // ---------- высасывание жизни ----------

    private String lifeDrain(Player player, Ability ability, LivingEntity clicked) {
        LivingEntity target = target(player, ability.range, clicked);
        if (target == null) {
            return "Нет цели перед вами";
        }
        stopDrain(player);
        int pulses = (int) Math.max(1, Math.round(ability.duration * 2));
        double damage = ability.power / 2;
        double maxDistance = ability.range + 4;
        int[] done = {0};
        BukkitTask task = module.plugin().getServer().getScheduler().runTaskTimer(module.plugin(), () -> {
            if (!player.isOnline() || player.isDead() || target.isDead() || !target.isValid()
                    || target.getWorld() != player.getWorld()
                    || target.getLocation().distance(player.getLocation()) > maxDistance
                    || !player.hasLineOfSight(target)) {
                module.bar(player, "<gray>Связь прервалась");
                stopDrain(player);
                return;
            }
            beam(player, target, Particle.DUST, DRAIN_DUST);
            double before = health.health(target);
            target.setNoDamageTicks(0);
            target.damage(damage, DamageSource.builder(DamageType.MAGIC)
                    .withCausingEntity(player).withDirectEntity(player).build());
            double after = target.isDead() ? 0 : health.health(target);
            double dealt = Math.max(0, before - after);
            if (dealt > 0 && ability.percent > 0) {
                health.heal(player, dealt * ability.percent / 100.0);
                player.getWorld().spawnParticle(Particle.HEART, player.getLocation().add(0, 2, 0), 1, 0.2, 0.1, 0.2, 0);
            }
            if (++done[0] >= pulses || target.isDead()) {
                stopDrain(player);
            }
        }, 0L, 10L);
        drains.put(player.getUniqueId(), task);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_EVOKER_CAST_SPELL, 0.8f, 0.7f);
        return null;
    }

    private void stopDrain(Player player) {
        BukkitTask task = drains.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }
    }

    // ---------- обмен здоровьем ----------

    private String healthSwap(Player player, Ability ability, LivingEntity clicked) {
        LivingEntity target = target(player, ability.range, clicked);
        if (target == null) {
            return "Нет цели перед вами";
        }
        if (module.isBoss(target)) {
            return "На боссов не действует";
        }
        double playerMax = health.maxHealth(player);
        double targetMax = health.maxHealth(target);
        double playerShare = health.health(player) / playerMax;
        double targetShare = health.health(target) / targetMax;
        double limit = ability.percent / 100.0;
        // Сдвиг доли: если разница в пределах — ровно меняются местами, иначе только на предел
        double shift = Math.max(-limit, Math.min(limit, targetShare - playerShare));
        if (Math.abs(shift) < 0.005) {
            return "Доли ХП равны, меняться нечем";
        }
        double newPlayer = playerShare + shift;
        double newTarget = Math.max(0.01, targetShare - shift);
        health.setHealth(player, newPlayer * playerMax);
        health.setHealth(target, newTarget * targetMax);

        beam(player, target, Particle.WITCH, null);
        LivingEntity gainer = shift > 0 ? player : target;
        gainer.getWorld().spawnParticle(Particle.HEART, gainer.getLocation().add(0, gainer.getHeight() + 0.3, 0),
                4, 0.3, 0.2, 0.3, 0);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_CAST_SPELL, 0.9f, 1f);
        module.bar(player, "<light_purple>Обмен здоровьем: <white>" + percent(playerShare) + " -> " + percent(newPlayer));
        if (target instanceof Player other) {
            module.bar(other, "<light_purple>" + player.getName() + " обменялся с вами здоровьем: <white>"
                    + percent(targetShare) + " -> " + percent(newTarget));
        }
        return null;
    }

    private static String percent(double share) {
        return Math.round(share * 100) + "%";
    }

    // ---------- цель и частицы ----------

    /** Существо, по которому нажали, или первое живое существо по взгляду в пределах дальности (не сквозь стены). */
    private LivingEntity target(Player player, double range, LivingEntity clicked) {
        if (clicked != null && valid(player, clicked)
                && clicked.getLocation().distance(player.getLocation()) <= range + 1.5) {
            return clicked;
        }
        RayTraceResult result = player.getWorld().rayTrace(player.getEyeLocation(), player.getEyeLocation().getDirection(),
                range, FluidCollisionMode.NEVER, true, 0.6,
                e -> e instanceof LivingEntity living && valid(player, living));
        return result != null && result.getHitEntity() instanceof LivingEntity living ? living : null;
    }

    private boolean valid(Player caster, LivingEntity entity) {
        if (entity == caster || entity.isDead() || entity.isInvulnerable() || entity instanceof ArmorStand) {
            return false;
        }
        if (entity instanceof Player other) {
            GameMode mode = other.getGameMode();
            return mode != GameMode.CREATIVE && mode != GameMode.SPECTATOR && module.affectsPlayers(caster);
        }
        return true;
    }

    /** Линия частиц от груди игрока до цели. */
    private static <T> void beam(Player player, LivingEntity target, Particle particle, T data) {
        Location from = player.getLocation().add(0, player.getHeight() * 0.7, 0);
        Location to = target.getLocation().add(0, target.getHeight() * 0.5, 0);
        Vector step = to.toVector().subtract(from.toVector());
        double length = step.length();
        if (length < 0.1) {
            return;
        }
        step.multiply(0.5 / length);
        Location point = from.clone();
        for (double d = 0; d < length; d += 0.5) {
            player.getWorld().spawnParticle(particle, point, 1, 0, 0, 0, 0, data);
            point.add(step);
        }
    }

    // ---------- урон от падения ----------

    private void protectFall(Player player, int seconds) {
        long until = System.currentTimeMillis() + seconds * 1000L;
        noFall.merge(player.getUniqueId(), until, Math::max);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFall(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL || !(event.getEntity() instanceof Player player)) {
            return;
        }
        Long until = noFall.get(player.getUniqueId());
        if (until == null) {
            return;
        }
        if (until < System.currentTimeMillis()) {
            noFall.remove(player.getUniqueId());
            return;
        }
        event.setCancelled(true);
    }

    // ---------- выход, смерть, выключение ----------

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        endFlight(player, false); // иначе игрок зайдёт снова уже с разрешённым полётом
        stopDrain(player);
        noFall.remove(player.getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        endFlight(event.getPlayer(), false);
        stopDrain(event.getPlayer());
    }

    void stopAll() {
        for (UUID id : new ArrayList<>(flights.keySet())) {
            Player player = module.plugin().getServer().getPlayer(id);
            if (player != null) {
                endFlight(player, true);
            } else {
                Flight flight = flights.remove(id);
                flight.end().cancel();
                if (flight.warn() != null) {
                    flight.warn().cancel();
                }
            }
        }
        List<BukkitTask> tasks = new ArrayList<>(drains.values());
        tasks.forEach(BukkitTask::cancel);
        drains.clear();
    }
}
