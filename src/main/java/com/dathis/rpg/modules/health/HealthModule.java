package com.dathis.rpg.modules.health;

import com.dathis.rpg.core.combat.HealthService;
import com.dathis.rpg.core.module.AbstractModule;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * Своё ХП и броня (см. {@link HealthService}): у игрока может быть 2000 ХП и 1000 брони.
 * Модуль перехватывает весь урон и лечение и пересчитывает их в наше ХП, а сердца показывают долю.
 */
public final class HealthModule extends AbstractModule {

    private HealthListener listener;
    private BukkitTask task;

    public HealthModule() {
        super("health", "Своё ХП и броня");
    }

    @Override
    protected void onEnable() {
        HealthService service = plugin.health();
        service.setActive(true);
        listener = new HealthListener(plugin, service);
        listen(listener);
        // Раз в полсекунды: сердца под новый максимум (надел/снял броню), сжатие сердец старой версии
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, listener, 10L, 10L);
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            listener.prepare(player);
        }
    }

    @Override
    protected void onDisable() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        // Наше ХП остаётся в PDC, ванильное «зеркало» у всех уже стоит в правильной доле
        plugin.health().setActive(false);
        listener = null;
    }
}
