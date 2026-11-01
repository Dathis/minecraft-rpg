package com.dathis.rpg.modules.item;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

/**
 * /create-item               — открыть окно создания
 * /create-item list          — список сохранённых предметов
 * /create-item give &lt;id&gt; [игрок] — выдать предмет
 */
final class CreateItemCommand implements TabExecutor {

    private final ItemModule module;

    CreateItemCommand(ItemModule module) {
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
            new MainMenu(module).open(player);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "list" -> {
                List<CustomItem> items = module.registry().all();
                messages.send(sender, "<yellow>Сохранённые предметы: <white>" + items.size());
                for (CustomItem item : items) {
                    sender.sendMessage(messages.parse(" <gray>" + item.id + "</gray> <white>" + item.name
                            + "</white> <dark_gray>(" + EditorMenu.pretty(item.material) + ")"));
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
                    messages.send(sender, "<green>Выдано: " + args[1] + " -> " + target.getName());
                } else {
                    messages.send(sender, "<red>Нет предмета с ID " + args[1] + ". Смотрите /" + label + " list</red>");
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
            return filter(module.registry().all().stream().map(i -> i.id).toList(), args[1]);
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
