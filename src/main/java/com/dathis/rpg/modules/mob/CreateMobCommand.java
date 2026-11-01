package com.dathis.rpg.modules.mob;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

/**
 * /create-mob                         — окно создания мобов
 * /create-mob list                    — список мобов
 * /create-mob egg &lt;id&gt; [кол-во] [игрок] — выдать яйца призыва
 * /create-mob spawn &lt;id&gt;              — призвать моба там, куда смотришь
 * /create-mob wand                    — палочка выделения для зон спавна
 */
final class CreateMobCommand implements TabExecutor {

    private final MobModule module;

    CreateMobCommand(MobModule module) {
        this.module = module;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        var messages = module.plugin().messages();
        if (args.length == 0) {
            if (sender instanceof Player player) {
                new MobMainMenu(module).open(player);
            } else {
                messages.send(sender, "<red>Окно можно открыть только в игре.</red>");
            }
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "list" -> {
                List<CustomMob> mobs = module.registry().all();
                messages.send(sender, "<yellow>Мобы: <white>" + mobs.size());
                for (CustomMob mob : mobs) {
                    sender.sendMessage(messages.parse(" <gray>" + mob.id + "</gray> ").append(mob.displayName())
                            .append(messages.parse(" <dark_gray>(" + MobEditorMenu.pretty(mob) + ", " + mob.tier.label + ")")));
                }
            }
            case "egg" -> {
                if (args.length < 2) {
                    messages.send(sender, "<red>Использование: /" + label + " egg <id> [кол-во] [игрок]</red>");
                    return true;
                }
                int amount = 1;
                if (args.length >= 3) {
                    try {
                        amount = Integer.parseInt(args[2]);
                    } catch (NumberFormatException ignored) {
                        // остаётся 1
                    }
                }
                Player target = args.length >= 4 ? Bukkit.getPlayerExact(args[3]) : sender instanceof Player p ? p : null;
                if (target == null) {
                    messages.send(sender, "<red>Игрок не найден.</red>");
                } else if (module.giveEgg(target, args[1], amount)) {
                    messages.send(sender, "<green>Выдано яиц: " + amount + " -> " + target.getName());
                } else {
                    messages.send(sender, "<red>Нет моба с ID " + args[1] + ". Смотрите /" + label + " list</red>");
                }
            }
            case "spawn" -> {
                if (!(sender instanceof Player player) || args.length < 2) {
                    messages.send(sender, "<red>Использование в игре: /" + label + " spawn <id></red>");
                    return true;
                }
                CustomMob mob = module.registry().get(args[1]);
                if (mob == null) {
                    messages.send(sender, "<red>Нет моба с ID " + args[1] + ".</red>");
                    return true;
                }
                var block = player.getTargetBlockExact(30);
                var location = block != null ? block.getLocation().add(0.5, 1, 0.5) : player.getLocation();
                module.spawn(mob, location);
            }
            case "wand" -> {
                if (sender instanceof Player player) {
                    player.getInventory().addItem(module.plugin().selectionWand().create());
                    messages.send(sender, "<green>Палочка выделения выдана.");
                }
            }
            default -> messages.send(sender, "<red>Использование: /" + label + " [list | egg <id> | spawn <id> | wand]</red>");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return filter(List.of("list", "egg", "spawn", "wand"), args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("egg") || args[0].equalsIgnoreCase("spawn"))) {
            return filter(module.registry().all().stream().map(m -> m.id).toList(), args[1]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("egg")) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[3]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String typed) {
        String t = typed.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(t)).toList();
    }
}
