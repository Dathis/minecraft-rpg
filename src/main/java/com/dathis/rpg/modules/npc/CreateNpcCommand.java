package com.dathis.rpg.modules.npc;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

/**
 * /create-npc              — окно NPC
 * /create-npc list         — список шаблонов
 * /create-npc place &lt;id&gt;  — поставить NPC на своё место
 * /create-npc remove       — убрать ближайшего NPC (до 5 блоков)
 */
final class CreateNpcCommand implements TabExecutor {

    private final NpcModule module;

    CreateNpcCommand(NpcModule module) {
        this.module = module;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        var messages = module.plugin().messages();
        if (args.length == 0) {
            if (sender instanceof Player player) {
                new NpcMainMenu(module).open(player);
            } else {
                messages.send(sender, "<red>Окно можно открыть только в игре.</red>");
            }
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "list" -> {
                List<NpcTemplate> npcs = module.registry().all();
                messages.send(sender, "<yellow>NPC: <white>" + npcs.size());
                for (NpcTemplate npc : npcs) {
                    sender.sendMessage(messages.parse(" <gray>" + npc.id + "</gray> ").append(npc.displayName())
                            .append(messages.parse(" <dark_gray>(точек: " + module.registry().placementsOf(npc.id).size() + ")")));
                }
            }
            case "place" -> {
                if (!(sender instanceof Player player) || args.length < 2) {
                    messages.send(sender, "<red>Использование в игре: /" + label + " place <id></red>");
                    return true;
                }
                NpcTemplate npc = module.registry().get(args[1]);
                if (npc == null) {
                    messages.send(sender, "<red>Нет NPC с ID " + args[1] + ". Смотрите /" + label + " list</red>");
                    return true;
                }
                NpcPlacement placement = module.place(npc, player.getLocation());
                messages.send(sender, "<green>NPC поставлен (точка " + placement.id + ").");
            }
            case "remove" -> {
                if (!(sender instanceof Player player)) {
                    return true;
                }
                NpcPlacement placement = module.nearestPlacement(player, 5);
                if (placement == null) {
                    messages.send(sender, "<red>Рядом (5 блоков) нет NPC.</red>");
                    return true;
                }
                module.removePlacement(placement);
                messages.send(sender, "<yellow>NPC убран с точки " + placement.id + ".");
            }
            default -> messages.send(sender, "<red>Использование: /" + label + " [list | place <id> | remove]</red>");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return filter(List.of("list", "place", "remove"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("place")) {
            return filter(module.registry().all().stream().map(n -> n.id).toList(), args[1]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String typed) {
        String t = typed.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(t)).toList();
    }
}
