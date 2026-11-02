package com.dathis.rpg.modules.mana;

import com.dathis.rpg.RpgPlugin;
import com.dathis.rpg.core.module.AbstractModule;
import com.dathis.rpg.core.progression.ManaService;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.scheduler.BukkitTask;

/**
 * Мана игрока: восстановление раз в секунду, полная мана после возрождения, /mana.
 * Сами числа хранит {@link ManaService} (plugin.mana()). Тратить ману будут способности (этап 3)
 * и расходники.
 */
public final class ManaModule extends AbstractModule implements Listener {

    private BukkitTask task;

    public ManaModule() {
        super("mana", "Мана");
    }

    @Override
    protected void onEnable() {
        plugin.mana().setActive(true);
        listen(this);
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::regenerate, 20L, 20L);

        PluginCommand command = plugin.getCommand("mana");
        if (command != null) {
            ManaCommand executor = new ManaCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }
    }

    @Override
    protected void onDisable() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        plugin.mana().setActive(false);
        PluginCommand command = plugin.getCommand("mana");
        if (command != null) {
            command.setExecutor(null);
            command.setTabCompleter(null);
        }
    }

    RpgPlugin plugin() {
        return plugin;
    }

    ManaService mana() {
        return plugin.mana();
    }

    private void regenerate() {
        ManaService mana = plugin.mana();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.isDead()) {
                continue;
            }
            double current = mana.mana(player);
            double max = mana.max(player);
            if (current < max) {
                mana.set(player, current + mana.regen(player));
            } else if (current > max) {
                // максимум упал (сняли броню) — обрезаем
                mana.set(player, max);
            }
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        if (plugin.getConfig().getBoolean("mana.fill-on-respawn", true)) {
            plugin.mana().fill(event.getPlayer());
        }
    }
}
