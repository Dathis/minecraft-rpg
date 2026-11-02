package com.dathis.rpg.modules.menu;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

/** /menu — главное меню; /stats [игрок] — окно персонажа (чужое — только админу). */
final class MenuCommand implements TabExecutor {

    private static final String ADMIN = "rpg.admin";

    private final MenuModule module;
    private final boolean stats;

    MenuCommand(MenuModule module, boolean stats) {
        this.module = module;
        this.stats = stats;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            module.plugin().messages().send(sender, "<red>Только для игроков.");
            return true;
        }
        if (!stats) {
            module.openMain(player);
            return true;
        }
        Player target = player;
        if (args.length > 0 && player.hasPermission(ADMIN)) {
            target = module.plugin().getServer().getPlayerExact(args[0]);
            if (target == null) {
                module.plugin().messages().send(player, "<red>Игрок не в сети: <white>" + args[0]);
                return true;
            }
        }
        new StatsMenu(module, target, false).open(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (!stats || args.length != 1 || !sender.hasPermission(ADMIN)) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return module.plugin().getServer().getOnlinePlayers().stream().map(Player::getName)
                .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
