package com.dathis.rpg.modules.region;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

/**
 * /create-region        — окно защиты мира и зон
 * /create-region list   — список зон
 * /create-region wand   — палочка выделения
 */
final class CreateRegionCommand implements TabExecutor {

    private final RegionModule module;

    CreateRegionCommand(RegionModule module) {
        this.module = module;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        var messages = module.plugin().messages();
        if (args.length == 0) {
            if (sender instanceof Player player) {
                new RegionMainMenu(module).open(player);
            } else {
                messages.send(sender, "<red>Окно можно открыть только в игре.</red>");
            }
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "list" -> {
                for (Area.Kind kind : Area.Kind.values()) {
                    List<Area> list = module.areas().all(kind);
                    messages.send(sender, (kind == Area.Kind.BUILD ? "<yellow>Зоны строительства: " : "<yellow>Зоны добычи: ")
                            + "<white>" + list.size());
                    for (Area area : list) {
                        messages.send(sender, " <gray>" + area.id + "</gray> <gold>" + area.name + "</gold> <dark_gray>("
                                + area.coordsText() + (kind == Area.Kind.MINING ? ", правил: " + area.rules.size() : "") + ")");
                    }
                }
            }
            case "wand" -> {
                if (sender instanceof Player player) {
                    player.getInventory().addItem(module.plugin().selectionWand().create());
                    messages.send(sender, "<green>Палочка выделения выдана.");
                }
            }
            default -> messages.send(sender, "<red>Использование: /" + label + " [list | wand]</red>");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            String t = args[0].toLowerCase(Locale.ROOT);
            return List.of("list", "wand").stream().filter(o -> o.startsWith(t)).toList();
        }
        return List.of();
    }
}
