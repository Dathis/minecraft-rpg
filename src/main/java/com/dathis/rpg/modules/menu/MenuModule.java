package com.dathis.rpg.modules.menu;

import com.dathis.rpg.RpgPlugin;
import com.dathis.rpg.core.module.AbstractModule;
import com.dathis.rpg.modules.item.ItemModule;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;

/**
 * Главное меню игрока: /menu и компас в хотбаре, окно персонажа /stats, настройки (боковая панель, компас).
 * Кнопки других систем (квесты, участки) открывают их обычные команды, поэтому модуль не зависит от них
 * и показывает только те кнопки, чьи модули включены.
 */
public final class MenuModule extends AbstractModule {

    private MenuCompass compass;
    private BukkitTask compassTask;

    public MenuModule() {
        super("menu", "Меню игрока");
    }

    @Override
    protected void onEnable() {
        compass = new MenuCompass(this);
        listen(compass);
        if (compassEnabled()) {
            // Раз в 2 секунды: вернуть компас после /clear, смены режима игры и т.п.
            compassTask = plugin.getServer().getScheduler().runTaskTimer(plugin, compass::checkAll, 20L, 40L);
        }
        command("menu", new MenuCommand(this, false));
        command("stats", new MenuCommand(this, true));
    }

    @Override
    protected void onDisable() {
        if (compassTask != null) {
            compassTask.cancel();
            compassTask = null;
        }
        if (compass != null) {
            // Без модуля компас был бы обычным компасом — забираем
            plugin.getServer().getOnlinePlayers().forEach(compass::remove);
            compass = null;
        }
        for (String name : List.of("menu", "stats")) {
            PluginCommand command = plugin.getCommand(name);
            if (command != null) {
                command.setExecutor(null);
                command.setTabCompleter(null);
            }
        }
    }

    private void command(String name, TabExecutor executor) {
        PluginCommand command = plugin.getCommand(name);
        if (command != null) {
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }
    }

    RpgPlugin plugin() {
        return plugin;
    }

    MenuCompass compass() {
        return compass;
    }

    /** Выдавать ли компас-меню (menu.compass в config.yml). */
    boolean compassEnabled() {
        return plugin.getConfig().getBoolean("menu.compass", true);
    }

    /** Слот хотбара для компаса, 1-9 в конфиге (по умолчанию 9-й), здесь 0-8. */
    int compassSlot() {
        return Math.max(1, Math.min(9, plugin.getConfig().getInt("menu.compass-slot", 9))) - 1;
    }

    /** Модуль предметов, если включён (для статов оружия в окне персонажа). */
    ItemModule items() {
        if (!plugin.modules().isEnabled("items")) {
            return null;
        }
        return plugin.modules().get("items").map(m -> (ItemModule) m).orElse(null);
    }

    void openMain(Player player) {
        new PlayerMenu(this).open(player);
    }
}
