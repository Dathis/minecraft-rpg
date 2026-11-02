package com.dathis.rpg.modules.ability;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** /abilities — окно с рунами игрока (то же, что кнопка «Способности» в /menu). */
final class AbilitiesCommand implements TabExecutor {

    private final AbilityModule module;

    AbilitiesCommand(AbilityModule module) {
        this.module = module;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            module.plugin().messages().send(sender, "<red>Только для игроков.");
            return true;
        }
        new PlayerAbilitiesMenu(module, player).open(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        return List.of();
    }
}
