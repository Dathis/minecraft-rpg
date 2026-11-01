package com.dathis.rpg.modules.quest;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

/**
 * /create-quest                       — окно квестов
 * /create-quest list                  — список квестов
 * /create-quest wand                  — палочка выделения (для «Разведать»)
 * /create-quest reset &lt;игрок&gt; [id]    — сбросить прогресс игрока (весь или одного квеста)
 */
final class CreateQuestCommand implements TabExecutor {

    private final QuestModule module;

    CreateQuestCommand(QuestModule module) {
        this.module = module;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        var messages = module.plugin().messages();
        if (args.length == 0) {
            if (sender instanceof Player player) {
                new QuestMainMenu(module).open(player);
            } else {
                messages.send(sender, "<red>Окно можно открыть только в игре.</red>");
            }
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "list" -> {
                List<Quest> quests = module.registry().all();
                messages.send(sender, "<yellow>Квесты: <white>" + quests.size());
                for (Quest quest : quests) {
                    messages.send(sender, " <gray>" + quest.id + "</gray> <gold>" + quest.name
                            + "</gold> <dark_gray>(NPC: " + quest.npcId + ", целей: " + quest.objectives.size() + ")");
                }
            }
            case "wand" -> {
                if (sender instanceof Player player) {
                    player.getInventory().addItem(module.plugin().selectionWand().create());
                    messages.send(sender, "<green>Палочка выделения выдана.");
                }
            }
            case "reset" -> {
                Player target = args.length >= 2 ? Bukkit.getPlayerExact(args[1]) : null;
                if (target == null) {
                    messages.send(sender, "<red>Использование: /" + label + " reset <игрок онлайн> [id квеста]</red>");
                    return true;
                }
                QuestProgress.PlayerQuests data = module.progress().get(target.getUniqueId());
                if (args.length >= 3) {
                    data.active.remove(args[2]);
                    data.completed.remove(args[2]);
                } else {
                    data.active.clear();
                    data.completed.clear();
                }
                module.progress().changed(target.getUniqueId());
                messages.send(sender, "<green>Прогресс квестов игрока " + target.getName() + " сброшен.");
            }
            default -> messages.send(sender, "<red>Использование: /" + label + " [list | wand | reset <игрок> [id]]</red>");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return filter(List.of("list", "wand", "reset"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("reset")) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("reset")) {
            return filter(module.registry().all().stream().map(q -> q.id).toList(), args[2]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String typed) {
        String t = typed.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(t)).toList();
    }

    /** /quests — журнал квестов игрока. */
    static final class QuestsCommand implements TabExecutor {

        private final QuestModule module;

        QuestsCommand(QuestModule module) {
            this.module = module;
        }

        @Override
        public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                                 @NotNull String label, @NotNull String[] args) {
            if (sender instanceof Player player) {
                new QuestBoardMenu(module, player, null).open(player);
            }
            return true;
        }

        @Override
        public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                          @NotNull String label, @NotNull String[] args) {
            return List.of();
        }
    }
}
