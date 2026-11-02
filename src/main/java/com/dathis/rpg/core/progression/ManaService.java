package com.dathis.rpg.core.progression;

import com.dathis.rpg.RpgPlugin;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Мана игрока — ресурс для способностей (этап 3 плана) и расходников.
 *
 * Как устроено (как своё ХП):
 * - текущая мана хранится в PersistentDataContainer игрока ({@code rpg_mana}), поэтому переживает рестарт;
 * - максимум и восстановление считаются вживую: база из config.yml + за каждый уровень после первого
 *   + бонусы модулей ({@link Provider}, например броня из /create-item с «+Мана» и «Восстановление маны»);
 * - восстанавливает ману раз в секунду модуль mana.
 *
 * Пока модуль mana выключен, {@link #spend} всегда разрешает трату (способности работают без маны).
 */
public final class ManaService {

    /** Сколько даёт модуль: max — к максимуму маны, regen — маны в секунду. */
    public record Bonus(double max, double regen) {
    }

    /** Источник бонусов к мане. Возвращает null, если ему нечего дать. */
    public interface Provider {
        Bonus bonus(Player player);
    }

    private final RpgPlugin plugin;
    private final NamespacedKey manaKey;
    private final List<Provider> providers = new CopyOnWriteArrayList<>();
    private boolean active;

    public ManaService(RpgPlugin plugin) {
        this.plugin = plugin;
        this.manaKey = new NamespacedKey(plugin, "rpg_mana");
    }

    /** Включён ли модуль mana. */
    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void addProvider(Provider provider) {
        providers.add(provider);
    }

    public void removeProvider(Provider provider) {
        providers.remove(provider);
    }

    // ---------- статы ----------

    /** Максимум маны: mana.base + mana.per-level × (уровень − 1) + бонусы. */
    public double max(Player player) {
        double base = plugin.getConfig().getDouble("mana.base", 100);
        double perLevel = plugin.getConfig().getDouble("mana.per-level", 10);
        double value = base + Math.max(0, perLevel) * (plugin.levels().level(player) - 1);
        for (Provider provider : providers) {
            Bonus bonus = provider.bonus(player);
            if (bonus != null) {
                value += bonus.max();
            }
        }
        return Math.max(0, value);
    }

    /** Восстановление маны в секунду: mana.regen + бонусы. */
    public double regen(Player player) {
        double value = plugin.getConfig().getDouble("mana.regen", 2);
        for (Provider provider : providers) {
            Bonus bonus = provider.bonus(player);
            if (bonus != null) {
                value += bonus.regen();
            }
        }
        return Math.max(0, value);
    }

    // ---------- текущая мана ----------

    /** Текущая мана. Новый игрок начинает с полной. */
    public double mana(Player player) {
        double max = max(player);
        Double stored = player.getPersistentDataContainer().get(manaKey, PersistentDataType.DOUBLE);
        return stored == null ? max : Math.max(0, Math.min(max, stored));
    }

    /** Ставит ману (обрезается до 0..максимум). */
    public void set(Player player, double value) {
        player.getPersistentDataContainer().set(manaKey, PersistentDataType.DOUBLE, Math.max(0, Math.min(max(player), value)));
    }

    public void fill(Player player) {
        set(player, max(player));
    }

    public void restore(Player player, double amount) {
        if (amount > 0) {
            set(player, mana(player) + amount);
        }
    }

    /** Хватает ли маны. Пока модуль выключен — всегда хватает. */
    public boolean has(Player player, double amount) {
        return !active || amount <= 0 || mana(player) >= amount;
    }

    /**
     * Тратит ману, если её хватает.
     *
     * @return true — потрачено (или модуль выключен), false — не хватает маны
     */
    public boolean spend(Player player, double amount) {
        if (!active || amount <= 0) {
            return true;
        }
        double current = mana(player);
        if (current < amount) {
            return false;
        }
        set(player, current - amount);
        return true;
    }
}
