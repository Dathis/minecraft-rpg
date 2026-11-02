package com.dathis.rpg.modules.ability;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

/**
 * /create-ability                  — окно создания способностей
 * /create-ability list             — список способностей
 * /create-ability give &lt;id&gt; [игрок] — выдать руну
 */
final class CreateAbilityCommand implements TabExecutor {

    private final AbilityModule module;

    CreateAbilityCommand(AbilityModule module) {
        this.module = module;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        var messages = module.plugin().messages();
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                messages.send(sender, "<red>Окно можно открыть только в игре.</red>");
                return true;
            }
            new AbilityMainMenu(module).open(player);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "list" -> {
                List<Ability> abilities = module.registry().all();
                messages.send(sender, "<yellow>Способности: <white>" + abilities.size());
                for (Ability ability : abilities) {
                    sender.sendMessage(messages.parse(" <gray>" + ability.id + "</gray> <white>"
                            + AbilityRegistry.escape(ability.name) + "</white> <dark_gray>(" + ability.mechanic.label + ")"));
                }
            }
            case "give" -> {
                if (args.length < 2) {
                    messages.send(sender, "<red>Использование: /" + label + " give <id> [игрок]</red>");
                    return true;
                }
                Player target = args.length >= 3 ? Bukkit.getPlayerExact(args[2])
                        : sender instanceof Player p ? p : null;
                if (target == null) {
                    messages.send(sender, "<red>Игрок не найден.</red>");
                    return true;
                }
                if (module.give(target, args[1])) {
                    messages.send(sender, "<green>Руна выдана: " + args[1] + " -> " + target.getName());
                } else {
                    messages.send(sender, "<red>Нет способности с ID " + args[1] + ". Смотрите /" + label + " list</red>");
                }
            }
            default -> messages.send(sender, "<red>Использование: /" + label + " [list | give <id> [игрок]]</red>");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return filter(List.of("list", "give"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            return filter(module.registry().all().stream().map(a -> a.id).toList(), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[2]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String typed) {
        String t = typed.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(t)).toList();
    }
}
