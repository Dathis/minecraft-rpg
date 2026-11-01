package com.dathis.rpg.core.command;

import com.dathis.rpg.RpgPlugin;
import com.dathis.rpg.core.module.RpgModule;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

/**
 * /rpg — главная команда. Подкоманды модулей (/rpg region ..., /rpg npc ...) будут добавляться сюда по мере
 * появления модулей. Команды набираются одинаково с Java и Bedrock.
 */
public final class RpgCommand implements TabExecutor {

    private static final List<String> SUBCOMMANDS = List.of("help", "modules", "reload");

    private final RpgPlugin plugin;

    public RpgCommand(RpgPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "modules" -> {
                if (!requireAdmin(sender)) return true;
                plugin.messages().send(sender, "<yellow>Модули:</yellow>");
                for (RpgModule module : plugin.modules().all()) {
                    boolean on = plugin.modules().isEnabled(module.id());
                    sender.sendMessage(plugin.messages().parse(
                            (on ? " <green>●</green> " : " <red>○</red> ") + module.displayName()
                                    + " <gray>(" + module.id() + ")</gray>"));
                }
            }
            case "reload" -> {
                if (!requireAdmin(sender)) return true;
                plugin.reload();
                plugin.messages().send(sender, "<green>Конфигурация перезагружена.</green>");
            }
            default -> {
                plugin.messages().send(sender, "<yellow>Команды:</yellow>");
                sender.sendMessage(plugin.messages().parse(" <white>/rpg help</white> <gray>— эта справка</gray>"));
                if (sender.hasPermission("rpg.admin")) {
                    sender.sendMessage(plugin.messages().parse(" <white>/rpg modules</white> <gray>— список модулей</gray>"));
                    sender.sendMessage(plugin.messages().parse(" <white>/rpg reload</white> <gray>— перезагрузить конфиг</gray>"));
                }
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String typed = args[0].toLowerCase(Locale.ROOT);
        return SUBCOMMANDS.stream()
                .filter(s -> s.startsWith(typed))
                .filter(s -> s.equals("help") || sender.hasPermission("rpg.admin"))
                .toList();
    }

    private boolean requireAdmin(CommandSender sender) {
        if (sender.hasPermission("rpg.admin")) {
            return true;
        }
        plugin.messages().send(sender, "<red>Недостаточно прав.</red>");
        return false;
    }
}
