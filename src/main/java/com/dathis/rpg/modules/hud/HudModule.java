package com.dathis.rpg.modules.hud;

import com.dathis.rpg.core.module.AbstractModule;
import org.bukkit.NamespacedKey;
import org.bukkit.scheduler.BukkitTask;

/**
 * Боевой интерфейс:
 * - всплывающие цифры урона над целью (-20, крит — золотым);
 * - название и ХП моба сверху экрана (боссбар), пока игрок на него смотрит.
 *
 * Для Bedrock: цифры — это невидимые стойки для брони с именем (Display-сущности Geyser не показывает),
 * полоска ХП — обычный боссбар. И то и другое Geyser передаёт без проблем.
 */
public final class HudModule extends AbstractModule {

    /** Метаданные, которыми другие модули помечают крит (см. CombatListener в modules.item). */
    public static final String CRIT_METADATA = "rpg_crit";

    private DamageIndicators indicators;
    private TargetHealthBar healthBar;
    private BukkitTask healthTask;

    public HudModule() {
        super("hud", "Цифры урона и ХП цели");
    }

    @Override
    protected void onEnable() {
        if (plugin.getConfig().getBoolean("hud.damage-numbers", true)) {
            indicators = new DamageIndicators(plugin, new NamespacedKey(plugin, "damage_indicator"));
            listen(indicators);
        }
        if (plugin.getConfig().getBoolean("hud.target-health", true)) {
            healthBar = new TargetHealthBar(plugin.getConfig().getInt("hud.target-range", 20), plugin.health());
            listen(healthBar);
            healthTask = plugin.getServer().getScheduler().runTaskTimer(plugin, healthBar, 2L, 2L);
        }
    }

    @Override
    protected void onDisable() {
        if (healthTask != null) {
            healthTask.cancel();
            healthTask = null;
        }
        if (healthBar != null) {
            healthBar.hideAll();
            healthBar = null;
        }
        if (indicators != null) {
            indicators.removeAll();
            indicators = null;
        }
    }
}
