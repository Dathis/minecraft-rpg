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
 * /create-region deed &lt;земля&gt; &lt;small|medium|large&gt; [игрок] — выдать бумагу на участок
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
                    messages.send(sender, "<yellow>" + Area.title(kind) + ": <white>" + list.size());
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
            case "deed" -> giveDeed(sender, label, args);
            default -> messages.send(sender, "<red>Использование: /" + label
                    + " [list | wand | deed <земля> <small|medium|large> [игрок]]</red>");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            String t = args[0].toLowerCase(Locale.ROOT);
            return List.of("list", "wand", "deed").stream().filter(o -> o.startsWith(t)).toList();
        }
        if (args[0].equalsIgnoreCase("deed")) {
            String t = args[args.length - 1].toLowerCase(Locale.ROOT);
            if (args.length == 2) {
                return module.areas().all(Area.Kind.LAND).stream().map(a -> a.id).filter(o -> o.startsWith(t)).toList();
            }
            if (args.length == 3) {
                return List.of("small", "medium", "large").stream().filter(o -> o.startsWith(t)).toList();
            }
            if (args.length == 4) {
                return null; // ники игроков
            }
        }
        return List.of();
    }

    private void giveDeed(CommandSender sender, String label, String[] args) {
        var messages = module.plugin().messages();
        if (args.length < 3) {
            messages.send(sender, "<red>Использование: /" + label + " deed <земля> <small|medium|large> [игрок]</red>");
            return;
        }
        Area land = module.areas().get(args[1]);
        if (land == null || land.kind != Area.Kind.LAND) {
            messages.send(sender, "<red>Нет земли под участки с id " + args[1] + ". Список: /" + label + " list</red>");
            return;
        }
        PlotSize size = PlotSize.parse(args[2]);
        if (size == null) {
            messages.send(sender, "<red>Размер: small, medium или large.</red>");
            return;
        }
        Player target = args.length >= 4 ? module.plugin().getServer().getPlayerExact(args[3])
                : sender instanceof Player p ? p : null;
        if (target == null) {
            messages.send(sender, "<red>Игрок не найден.</red>");
            return;
        }
        for (var left : target.getInventory().addItem(module.deeds().create(land, size)).values()) {
            target.getWorld().dropItemNaturally(target.getLocation(), left);
        }
        messages.send(sender, "<green>Бумага на участок (" + size.title + ") выдана: " + target.getName());
    }
}
