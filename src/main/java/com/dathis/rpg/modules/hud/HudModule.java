package com.dathis.rpg.modules.hud;

import com.dathis.rpg.core.module.AbstractModule;
import org.bukkit.NamespacedKey;
import org.bukkit.scheduler.BukkitTask;

/**
 * Боевой интерфейс:
 * - всплывающие цифры урона над целью (-20, крит — золотым);
 * - название и ХП моба сверху экрана (боссбар), пока игрок на него смотрит;
 * - боковая панель справа: уровень, ХП, мана, текущий квест (SidebarHud).
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
    private SidebarHud sidebar;
    private BukkitTask sidebarTask;

    public HudModule() {
        super("hud", "Цифры урона, ХП цели, боковая панель");
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
        if (plugin.getConfig().getBoolean("hud.sidebar.enabled", true)) {
            sidebar = new SidebarHud(plugin);
            listen(sidebar);
            long period = Math.max(2, plugin.getConfig().getLong("hud.sidebar.update-ticks", 10));
            sidebarTask = plugin.getServer().getScheduler().runTaskTimer(plugin, sidebar, 5L, period);
            plugin.sidebar().setAvailable(true);
        }
    }

    @Override
    protected void onDisable() {
        plugin.sidebar().setAvailable(false);
        if (sidebarTask != null) {
            sidebarTask.cancel();
            sidebarTask = null;
        }
        if (sidebar != null) {
            sidebar.hideAll();
            sidebar = null;
        }
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
