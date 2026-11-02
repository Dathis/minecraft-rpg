package com.dathis.rpg.modules.level;

import com.dathis.rpg.RpgPlugin;
import com.dathis.rpg.core.combat.HealthService;
import com.dathis.rpg.core.gui.Icons;
import com.dathis.rpg.core.module.AbstractModule;
import com.dathis.rpg.core.progression.LevelService;
import net.kyori.adventure.title.Title;
import org.bukkit.Sound;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;

/**
 * Уровни персонажа: опыт за мобов и квесты, уровни у мобов и предметов, штраф за разницу уровней.
 * Сами числа хранит {@link LevelService} (plugin.levels()), здесь — показ игроку, события и /level.
 */
public final class LevelModule extends AbstractModule {

    private LevelListener listener;
    private BukkitTask task;
    /** +ХП за уровень для HealthService. */
    private final HealthService.Provider health = entity -> {
        if (!(entity instanceof Player player)) {
            return null;
        }
        double perLevel = plugin.getConfig().getDouble("levels.health-per-level", 5);
        return new HealthService.Bonus(Math.max(0, perLevel) * (plugin.levels().level(player) - 1), 0);
    };

    public LevelModule() {
        super("levels", "Уровни персонажа");
    }

    @Override
    protected void onEnable() {
        plugin.levels().setActive(true, this::onXp);
        plugin.health().addProvider(health);
        listener = new LevelListener(this);
        listen(listener);
        // Полоска опыта и ник в табе раз в секунду: ванильный опыт могут поменять зачарование, наковальня, команды
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                listener.refresh(player);
            }
        }, 20L, 20L);

        PluginCommand command = plugin.getCommand("level");
        if (command != null) {
            LevelCommand executor = new LevelCommand(this);
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
        plugin.levels().setActive(false, null);
        plugin.health().removeProvider(health);
        if (listener != null) {
            listener.restoreAll();
            listener = null;
        }
        PluginCommand command = plugin.getCommand("level");
        if (command != null) {
            command.setExecutor(null);
            command.setTabCompleter(null);
        }
    }

    RpgPlugin plugin() {
        return plugin;
    }

    LevelService levels() {
        return plugin.levels();
    }

    boolean useXpBar() {
        return plugin.getConfig().getBoolean("levels.use-xp-bar", true);
    }

    boolean showInTab() {
        return plugin.getConfig().getBoolean("levels.show-in-tab", true);
    }

    /** Показ игроку: «+25 опыта» над хотбаром, при новом уровне — надпись, звук и полное ХП. */
    private void onXp(Player player, long gained, String note, int levels) {
        if (listener != null) {
            listener.refresh(player);
        }
        if (gained > 0) {
            String text = "<green>+" + gained + " опыта";
            if (note != null) {
                text += " <gray>(" + note + ")";
            }
            int level = plugin.levels().level(player);
            if (level < plugin.levels().maxLevel()) {
                text += "   <gray>" + plugin.levels().xp(player) + " / " + plugin.levels().xpToNext(level);
            }
            player.sendActionBar(Icons.text(text));
        }
        if (levels <= 0) {
            return;
        }
        int level = plugin.levels().level(player);
        player.showTitle(Title.title(Icons.text("<gold>Уровень " + level), Icons.text("<yellow>Новый уровень!"),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(700))));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        plugin.messages().send(player, "<gold>Новый уровень: <yellow>" + level
                + (level >= plugin.levels().maxLevel() ? " <gray>(максимальный)" : ""));
        double perLevel = plugin.getConfig().getDouble("levels.health-per-level", 5);
        if (perLevel > 0 && plugin.health().isActive()) {
            plugin.messages().send(player, "<gray>Здоровье: <red>+" + com.dathis.rpg.core.input.Numbers.format(perLevel * levels));
        }
        if (plugin.getConfig().getBoolean("levels.heal-on-level-up", true)) {
            // Максимум ХП вырос — сначала даём HealthService пересчитать, потом заполняем
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (player.isOnline() && !player.isDead()) {
                    plugin.health().fill(player);
                    plugin.mana().fill(player);
                }
            });
        }
    }
}
