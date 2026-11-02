package com.dathis.rpg.core.combat;

import com.dathis.rpg.RpgPlugin;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Своё ХП и броня без ванильных пределов (20 сердец, 1024 ХП, 30 брони).
 *
 * Как устроено:
 * - настоящее ХП хранится в PersistentDataContainer сущности (ключ {@code rpg_health}), максимум
 *   и броня считаются вживую: у игрока база из config + бонусы от модулей (броня из /create-item),
 *   у кастомного моба всё берётся из шаблона;
 * - ванильное ХП — только «зеркало» для показа: сердца игрока и полоска моба показывают долю
 *   (1000 из 2000 ХП = 5 сердец из 10);
 * - урон и лечение перехватывает модуль health (HealthListener) и пересчитывает в наше ХП.
 *
 * Модули, которые дают статы, регистрируют {@link Provider}. Сущность без провайдера (обычная корова)
 * живёт по ванильным правилам. Игроки — всегда по нашим, пока модуль health включён.
 */
public final class HealthService {

    /** Сколько статов даёт модуль. health — к максимуму ХП, armor — очки брони. */
    public record Bonus(double health, double armor) {
    }

    /** Источник статов. Возвращает null, если сущность «не его». */
    public interface Provider {
        Bonus bonus(LivingEntity entity);
    }

    /** Итоговые статы сущности с нашим ХП. */
    public record Stats(double maxHealth, double armor) {
    }

    /** Меньше этого ванильное «зеркало» не опускается, пока сущность жива (полсердца видно всегда). */
    private static final double MIN_MIRROR = 0.5;

    private final RpgPlugin plugin;
    private final NamespacedKey healthKey;
    private final List<Provider> providers = new CopyOnWriteArrayList<>();
    /** Последнее ванильное значение, которое мы поставили: так видно, что ХП поменял кто-то другой. */
    private final Map<UUID, Double> mirrors = new ConcurrentHashMap<>();
    /** Наш урон по событию (для цифр урона): ванильный финальный урон — это только сдвиг «зеркала». */
    private final Map<EntityDamageEvent, Double> dealt = new WeakHashMap<>();
    private boolean active;

    public HealthService(RpgPlugin plugin) {
        this.plugin = plugin;
        this.healthKey = new NamespacedKey(plugin, "rpg_health");
    }

    public void addProvider(Provider provider) {
        providers.add(provider);
    }

    public void removeProvider(Provider provider) {
        providers.remove(provider);
    }

    /** Включён ли модуль health. Если нет — всё по-ванильному. */
    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
        mirrors.clear();
    }

    // ---------- статы ----------

    /** Статы сущности или null, если у неё ванильное ХП. */
    public Stats stats(LivingEntity entity) {
        if (!active) {
            return null;
        }
        boolean found = entity instanceof Player;
        double health = found ? playerBase() : 0;
        double armor = 0;
        for (Provider provider : providers) {
            Bonus bonus = provider.bonus(entity);
            if (bonus != null) {
                found = true;
                health += bonus.health();
                armor += bonus.armor();
            }
        }
        if (!found) {
            return null;
        }
        // Ванильная броня (обычный железный нагрудник, броня зомби) тоже считается
        AttributeInstance vanillaArmor = entity.getAttribute(Attribute.ARMOR);
        if (vanillaArmor != null) {
            armor += vanillaArmor.getValue();
        }
        return new Stats(Math.max(1, health), Math.max(0, armor));
    }

    public double playerBase() {
        return Math.max(1, plugin.getConfig().getDouble("health.player-base", 20));
    }

    /** Доля урона, которую срезает броня: броня / (броня + K), не больше max-armor-reduction. */
    public double armorReduction(double armor) {
        if (armor <= 0) {
            return 0;
        }
        double k = Math.max(1, plugin.getConfig().getDouble("health.armor-constant", 100));
        double cap = Math.max(0, Math.min(100, plugin.getConfig().getDouble("health.max-armor-reduction", 90))) / 100.0;
        return Math.min(cap, armor / (armor + k));
    }

    // ---------- ХП ----------

    /** Текущее ХП (наше или ванильное). */
    public double health(LivingEntity entity) {
        Stats stats = stats(entity);
        return stats == null ? entity.getHealth() : current(entity, stats);
    }

    /** Максимум ХП (наш или ванильный). */
    public double maxHealth(LivingEntity entity) {
        Stats stats = stats(entity);
        return stats == null ? vanillaMax(entity) : stats.maxHealth();
    }

    /** Очки брони (наши, вместе с ванильными) или ванильные, если у сущности ванильное ХП. */
    public double armor(LivingEntity entity) {
        Stats stats = stats(entity);
        if (stats != null) {
            return stats.armor();
        }
        AttributeInstance armor = entity.getAttribute(Attribute.ARMOR);
        return armor != null ? armor.getValue() : 0;
    }

    /** Лечит на amount нашего ХП (или ванильного, если у сущности ванильное ХП). */
    public void heal(LivingEntity entity, double amount) {
        if (amount <= 0 || entity.isDead()) {
            return;
        }
        Stats stats = stats(entity);
        if (stats == null) {
            entity.setHealth(Math.min(vanillaMax(entity), entity.getHealth() + amount));
            return;
        }
        setHealth(entity, stats, current(entity, stats) + amount);
    }

    /** Ставит ХП (обрезается до 0..максимум) и обновляет сердца. */
    public void setHealth(LivingEntity entity, double value) {
        Stats stats = stats(entity);
        if (stats == null) {
            entity.setHealth(Math.max(0, Math.min(vanillaMax(entity), value)));
            return;
        }
        setHealth(entity, stats, value);
    }

    /** Полное ХП (новый моб, возрождение игрока). */
    public void fill(LivingEntity entity) {
        Stats stats = stats(entity);
        if (stats != null) {
            setHealth(entity, stats, stats.maxHealth());
        }
    }

    void setHealth(LivingEntity entity, Stats stats, double value) {
        store(entity, Math.max(0, Math.min(stats.maxHealth(), value)));
        sync(entity, stats);
    }

    /**
     * Текущее ХП из PDC. Если его ещё нет (моб из старой версии, первый вход игрока),
     * берём ту же долю, что у ванильного ХП.
     */
    public double current(LivingEntity entity, Stats stats) {
        Double stored = entity.getPersistentDataContainer().get(healthKey, PersistentDataType.DOUBLE);
        double value;
        if (stored == null) {
            value = stats.maxHealth() * Math.max(0, entity.getHealth()) / vanillaMax(entity);
        } else {
            value = stored;
        }
        return Math.max(0, Math.min(stats.maxHealth(), value));
    }

    public void store(LivingEntity entity, double value) {
        entity.getPersistentDataContainer().set(healthKey, PersistentDataType.DOUBLE, value);
    }

    /**
     * Подгоняет ванильное ХП (сердца) под наше. Если ванильное ХП с прошлого раза поменял кто-то
     * другой (другой плагин, команда), принимаем эту долю как новое наше ХП.
     */
    public void sync(LivingEntity entity) {
        Stats stats = stats(entity);
        if (stats != null) {
            Double last = mirrors.get(entity.getUniqueId());
            if (last != null && !entity.isDead() && Math.abs(entity.getHealth() - last) > 0.01) {
                store(entity, stats.maxHealth() * entity.getHealth() / vanillaMax(entity));
            }
            sync(entity, stats);
        }
    }

    void sync(LivingEntity entity, Stats stats) {
        if (entity.isDead() || !entity.isValid() && !(entity instanceof Player)) {
            return;
        }
        double target = mirror(entity, current(entity, stats), stats.maxHealth());
        if (target <= 0) {
            return; // смерть сделает событие урона
        }
        if (Math.abs(entity.getHealth() - target) > 1e-6) {
            entity.setHealth(target);
        }
        mirrors.put(entity.getUniqueId(), target);
    }

    /** Ванильное ХП, которое показывает наше: та же доля от ванильного максимума. */
    public double mirror(LivingEntity entity, double current, double max) {
        double vanillaMax = vanillaMax(entity);
        if (current <= 0) {
            return 0;
        }
        double value = vanillaMax * current / max;
        return Math.max(Math.min(MIN_MIRROR, vanillaMax), Math.min(vanillaMax, value));
    }

    /** Запомнить, что после этого урона ванильное ХП станет таким (см. sync). */
    public void expectMirror(LivingEntity entity, double value) {
        mirrors.put(entity.getUniqueId(), value);
    }

    public void forget(LivingEntity entity) {
        mirrors.remove(entity.getUniqueId());
    }

    public static double vanillaMax(LivingEntity entity) {
        AttributeInstance max = entity.getAttribute(Attribute.MAX_HEALTH);
        return max != null ? Math.max(1, max.getValue()) : 20;
    }

    // ---------- урон для HUD ----------

    public void recordDealt(EntityDamageEvent event, double damage) {
        dealt.put(event, damage);
    }

    /** Урон по нашему ХП, если событие прошло через модуль health, иначе ванильный финальный. */
    public double dealt(EntityDamageEvent event) {
        Double value = dealt.get(event);
        return value != null ? value : event.getFinalDamage();
    }
}
