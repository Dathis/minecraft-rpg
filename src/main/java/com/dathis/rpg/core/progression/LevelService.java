package com.dathis.rpg.core.progression;

import com.dathis.rpg.RpgPlugin;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Уровни и опыт персонажа (не ванильный опыт).
 *
 * Как устроено:
 * - уровень и опыт внутри уровня хранятся в PersistentDataContainer игрока ({@code rpg_level}, {@code rpg_xp}),
 *   поэтому переживают рестарт без отдельного файла;
 * - сколько опыта нужно на следующий уровень, берётся из config.yml (формула или своя таблица);
 * - уровень и опыт за моба отдаёт модуль мобов через {@link MobSource};
 * - что происходит при получении опыта и новом уровне (надписи, звук, полоска опыта) делает модуль levels
 *   через {@link Listener}.
 *
 * Пока модуль levels выключен, требования к уровню не проверяются, опыт не начисляется.
 */
public final class LevelService {

    /** Уровень моба и сколько опыта он даёт (до штрафа за разницу уровней). */
    public record MobLevel(int level, long xp) {
    }

    /** Источник уровня мобов. Возвращает null, если сущность «не его». */
    public interface MobSource {
        MobLevel of(Entity entity);
    }

    /** Что сделать, когда игрок получил опыт (модуль levels показывает это игроку). */
    public interface Listener {
        /**
         * @param gained сколько опыта начислено
         * @param note   пояснение для игрока (MiniMessage) или null
         * @param levels сколько уровней игрок получил этим опытом (0 — ни одного)
         */
        void onXp(Player player, long gained, String note, int levels);
    }

    private final RpgPlugin plugin;
    private final NamespacedKey levelKey;
    private final NamespacedKey xpKey;
    private final List<MobSource> sources = new CopyOnWriteArrayList<>();
    private Listener listener;
    private boolean active;

    public LevelService(RpgPlugin plugin) {
        this.plugin = plugin;
        this.levelKey = new NamespacedKey(plugin, "rpg_level");
        this.xpKey = new NamespacedKey(plugin, "rpg_xp");
    }

    /** Включён ли модуль levels. */
    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active, Listener listener) {
        this.active = active;
        this.listener = active ? listener : null;
    }

    public void addSource(MobSource source) {
        sources.add(source);
    }

    public void removeSource(MobSource source) {
        sources.remove(source);
    }

    // ---------- настройки ----------

    /** Последний уровень (по умолчанию 20). */
    public int maxLevel() {
        return Math.max(1, Math.min(1000, plugin.getConfig().getInt("levels.max-level", 20)));
    }

    /**
     * Сколько опыта нужно, чтобы с уровня level перейти на следующий.
     * Своя таблица levels.xp-table (если задана) или формула xp-base * level ^ xp-exponent.
     */
    public long xpToNext(int level) {
        List<Long> table = plugin.getConfig().getLongList("levels.xp-table");
        if (!table.isEmpty()) {
            int index = Math.min(table.size(), Math.max(1, level)) - 1;
            return Math.max(1, table.get(index));
        }
        double base = Math.max(1, plugin.getConfig().getDouble("levels.xp-base", 100));
        double exponent = Math.max(0, plugin.getConfig().getDouble("levels.xp-exponent", 1.5));
        return Math.max(1, Math.round(base * Math.pow(Math.max(1, level), exponent)));
    }

    /** Сколько всего опыта нужно, чтобы с первого уровня дойти до level. */
    public long totalXpFor(int level) {
        long total = 0;
        for (int l = 1; l < level; l++) {
            total += xpToNext(l);
        }
        return total;
    }

    /** Разница уровней, с которой опыт за моба срезается (по умолчанию 5). */
    public int penaltyDifference() {
        return Math.max(1, plugin.getConfig().getInt("levels.penalty.level-difference", 5));
    }

    /** Сколько процентов опыта остаётся при штрафе (по умолчанию 25). */
    public double penaltyPercent() {
        return Math.max(0, Math.min(100, plugin.getConfig().getDouble("levels.penalty.xp-percent", 25)));
    }

    // ---------- уровень игрока ----------

    public int level(Player player) {
        Integer stored = player.getPersistentDataContainer().get(levelKey, PersistentDataType.INTEGER);
        return Math.max(1, Math.min(maxLevel(), stored == null ? 1 : stored));
    }

    /** Опыт, набранный на текущем уровне. */
    public long xp(Player player) {
        Long stored = player.getPersistentDataContainer().get(xpKey, PersistentDataType.LONG);
        return stored == null ? 0 : Math.max(0, stored);
    }

    /** Доля полоски до следующего уровня, 0..1 (на последнем уровне — 1). */
    public double progress(Player player) {
        int level = level(player);
        if (level >= maxLevel()) {
            return 1;
        }
        return Math.max(0, Math.min(1, (double) xp(player) / xpToNext(level)));
    }

    /** Можно ли игроку пользоваться вещью / брать квест с таким требованием. Модуль выключен — всегда можно. */
    public boolean meets(Player player, int required) {
        return !active || required <= 1 || level(player) >= required;
    }

    /** Ставит уровень (опыт внутри уровня сбрасывается). */
    public void setLevel(Player player, int level) {
        store(player, Math.max(1, Math.min(maxLevel(), level)), 0);
    }

    // ---------- опыт ----------

    /**
     * Начисляет опыт и поднимает уровни. На последнем уровне опыт больше не копится.
     *
     * @param note пояснение для игрока (например «квест») или null
     * @return сколько уровней получено
     */
    public int giveXp(Player player, long amount, String note) {
        if (!active || amount <= 0) {
            return 0;
        }
        int level = level(player);
        int max = maxLevel();
        if (level >= max) {
            store(player, max, 0);
            if (listener != null) {
                listener.onXp(player, 0, note, 0);
            }
            return 0;
        }
        long xp = xp(player) + amount;
        int gained = 0;
        while (level < max && xp >= xpToNext(level)) {
            xp -= xpToNext(level);
            level++;
            gained++;
        }
        if (level >= max) {
            xp = 0;
        }
        store(player, level, xp);
        if (listener != null) {
            listener.onXp(player, amount, note, gained);
        }
        return gained;
    }

    /** Уровень и опыт моба или null, если моб «ничей» (ванильный). */
    public MobLevel mobLevel(Entity entity) {
        for (MobSource source : sources) {
            MobLevel level = source.of(entity);
            if (level != null) {
                return level;
            }
        }
        return null;
    }

    /** Множитель опыта за моба такого уровня: 1 или штраф, если разница уровней 5 и больше. */
    public double killMultiplier(int playerLevel, int mobLevel) {
        return Math.abs(mobLevel - playerLevel) >= penaltyDifference() ? penaltyPercent() / 100.0 : 1;
    }

    private void store(Player player, int level, long xp) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        pdc.set(levelKey, PersistentDataType.INTEGER, level);
        pdc.set(xpKey, PersistentDataType.LONG, Math.max(0, xp));
    }
}
