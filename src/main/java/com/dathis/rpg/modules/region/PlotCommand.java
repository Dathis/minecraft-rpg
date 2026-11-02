package com.dathis.rpg.modules.region;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

/**
 * /plot        — мои участки
 * /plot claim  — выбрать место по бумаге в руке (если ПКМ с бумагой не срабатывает)
 */
final class PlotCommand implements TabExecutor {

    private final RegionModule module;

    PlotCommand(RegionModule module) {
        this.module = module;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            module.plugin().messages().send(sender, "<red>Команда только для игроков.</red>");
            return true;
        }
        if (args.length > 0 && args[0].toLowerCase(Locale.ROOT).equals("claim")) {
            module.useDeed(player, player.getInventory().getItemInMainHand());
            return true;
        }
        new PlotListMenu(module, null, player).open(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length == 1 && "claim".startsWith(args[0].toLowerCase(Locale.ROOT))) {
            return List.of("claim");
        }
        return List.of();
    }
}
