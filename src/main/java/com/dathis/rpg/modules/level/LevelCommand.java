package com.dathis.rpg.modules.level;

import com.dathis.rpg.core.input.Numbers;
import com.dathis.rpg.core.progression.LevelService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;

/**
 * /level — свой уровень и опыт. Админу ещё:
 * /level &lt;игрок&gt;, /level set &lt;игрок&gt; &lt;уровень&gt;, /level give &lt;игрок&gt; &lt;опыт&gt;, /level reset &lt;игрок&gt;.
 */
final class LevelCommand implements TabExecutor {

    private static final String ADMIN = "rpg.admin";

    private final LevelModule module;

    LevelCommand(LevelModule module) {
        this.module = module;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player player) {
                show(sender, player);
            } else {
                send(sender, "<yellow>/level [игрок] | set [игрок] [уровень] | give [игрок] [опыт] | reset [игрок]");
            }
            return true;
        }
        if (!sender.hasPermission(ADMIN)) {
            send(sender, "<red>Это только для администратора. Свой уровень: <white>/level");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "set", "give", "reset" -> {
                if (args.length < 2 || (!sub.equals("reset") && args.length < 3)) {
                    send(sender, "<red>Использование: <white>/level " + sub + " [игрок]" + (sub.equals("reset") ? "" : " [число]"));
                    return true;
                }
                Player target = module.plugin().getServer().getPlayerExact(args[1]);
                if (target == null) {
                    send(sender, "<red>Игрок не в сети: <white>" + args[1]);
                    return true;
                }
                LevelService levels = module.levels();
                if (sub.equals("reset")) {
                    levels.setLevel(target, 1);
                    changed(target);
                    send(sender, "<green>Уровень игрока <white>" + target.getName() + "</white> сброшен на 1.");
                    return true;
                }
                OptionalDouble value = Numbers.parse(args[2]);
                if (value.isEmpty() || value.getAsDouble() < (sub.equals("set") ? 1 : 0)) {
                    send(sender, "<red>Нужно число" + (sub.equals("set") ? " от 1 до " + levels.maxLevel() : " от 0") + ".");
                    return true;
                }
                if (sub.equals("set")) {
                    int before = levels.level(target);
                    levels.setLevel(target, (int) value.getAsDouble());
                    changed(target);
                    if (levels.level(target) > before) {
                        module.plugin().health().fill(target);
                    }
                    send(sender, "<green>Уровень игрока <white>" + target.getName() + "</white>: " + levels.level(target));
                } else {
                    levels.giveXp(target, (long) value.getAsDouble(), "от администратора");
                    send(sender, "<green>Игрок <white>" + target.getName() + "</white> получил "
                            + (long) value.getAsDouble() + " опыта, уровень " + levels.level(target) + ".");
                }
            }
            default -> {
                Player target = module.plugin().getServer().getPlayerExact(args[0]);
                if (target == null) {
                    send(sender, "<red>Игрок не в сети: <white>" + args[0]);
                    return true;
                }
                show(sender, target);
            }
        }
        return true;
    }

    private void changed(Player target) {
        module.plugin().getServer().getScheduler().runTask(module.plugin(), () -> {
            if (target.isOnline()) {
                module.plugin().health().sync(target);
            }
        });
        // полоска опыта и таб обновятся задачей модуля; сразу — для наглядности
        target.sendActionBar(com.dathis.rpg.core.gui.Icons.text("<gold>Уровень " + module.levels().level(target)));
    }

    private void show(CommandSender to, Player player) {
        LevelService levels = module.levels();
        int level = levels.level(player);
        boolean self = to == player;
        send(to, "<gold>" + (self ? "Ваш уровень" : "Уровень " + player.getName()) + ": <yellow>" + level
                + "<gray> из " + levels.maxLevel());
        if (level < levels.maxLevel()) {
            long xp = levels.xp(player);
            long need = levels.xpToNext(level);
            to.sendMessage(module.plugin().messages().parse(" <gray>Опыт: <white>" + xp + " / " + need
                    + " <gray>(" + Math.round(100.0 * xp / need) + "%), до уровня " + (level + 1)
                    + " осталось <white>" + (need - xp)));
        } else {
            to.sendMessage(module.plugin().messages().parse(" <gray>Достигнут максимальный уровень."));
        }
        double perLevel = module.plugin().getConfig().getDouble("levels.health-per-level", 5);
        if (perLevel > 0 && module.plugin().health().isActive() && level > 1) {
            to.sendMessage(module.plugin().messages().parse(" <gray>Здоровье за уровни: <red>+"
                    + Numbers.format(perLevel * (level - 1))));
        }
        to.sendMessage(module.plugin().messages().parse(" <gray>Опыт дают убийства мобов и квесты. За мобов на "
                + levels.penaltyDifference() + "+ уровней выше или ниже вас — только "
                + Numbers.format(levels.penaltyPercent()) + "% опыта."));
    }

    private void send(CommandSender to, String text) {
        module.plugin().messages().send(to, text);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission(ADMIN)) {
            return out;
        }
        if (args.length == 1) {
            for (String s : List.of("set", "give", "reset")) {
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(s);
                }
            }
            addPlayers(out, args[0]);
        } else if (args.length == 2 && List.of("set", "give", "reset").contains(args[0].toLowerCase(Locale.ROOT))) {
            addPlayers(out, args[1]);
        }
        return out;
    }

    private void addPlayers(List<String> out, String prefix) {
        for (Player player : module.plugin().getServer().getOnlinePlayers()) {
            if (player.getName().toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))) {
                out.add(player.getName());
            }
        }
    }
}
