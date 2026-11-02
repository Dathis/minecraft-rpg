package com.dathis.rpg.modules.mana;

import com.dathis.rpg.core.input.Numbers;
import com.dathis.rpg.core.progression.ManaService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;

/**
 * /mana — своя мана. Админу: /mana set|give|take|fill [игрок] [число] (проверить панель и способности).
 */
final class ManaCommand implements TabExecutor {

    private static final String ADMIN = "rpg.admin";
    private static final List<String> SUBS = List.of("set", "give", "take", "fill");

    private final ManaModule module;

    ManaCommand(ManaModule module) {
        this.module = module;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player player) {
                show(sender, player);
            } else {
                send(sender, "<yellow>/mana [игрок] | set|give|take [игрок] [число] | fill [игрок]");
            }
            return true;
        }
        if (!sender.hasPermission(ADMIN)) {
            send(sender, "<red>Это только для администратора. Своя мана: <white>/mana");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (!SUBS.contains(sub)) {
            Player target = module.plugin().getServer().getPlayerExact(args[0]);
            if (target == null) {
                send(sender, "<red>Игрок не в сети: <white>" + args[0]);
            } else {
                show(sender, target);
            }
            return true;
        }
        boolean needsNumber = !sub.equals("fill");
        if (args.length < 2 || (needsNumber && args.length < 3)) {
            send(sender, "<red>Использование: <white>/mana " + sub + " [игрок]" + (needsNumber ? " [число]" : ""));
            return true;
        }
        Player target = module.plugin().getServer().getPlayerExact(args[1]);
        if (target == null) {
            send(sender, "<red>Игрок не в сети: <white>" + args[1]);
            return true;
        }
        ManaService mana = module.mana();
        if (sub.equals("fill")) {
            mana.fill(target);
        } else {
            OptionalDouble value = Numbers.parse(args[2]);
            if (value.isEmpty() || value.getAsDouble() < 0) {
                send(sender, "<red>Нужно число от 0.");
                return true;
            }
            double amount = value.getAsDouble();
            switch (sub) {
                case "set" -> mana.set(target, amount);
                case "give" -> mana.restore(target, amount);
                default -> mana.set(target, mana.mana(target) - amount);
            }
        }
        send(sender, "<green>Мана игрока <white>" + target.getName() + "</white>: "
                + Numbers.format(Math.floor(mana.mana(target))) + " / " + Numbers.format(Math.floor(mana.max(target))));
        return true;
    }

    private void show(CommandSender to, Player player) {
        ManaService mana = module.mana();
        boolean self = to == player;
        send(to, "<blue>" + (self ? "Ваша мана" : "Мана " + player.getName()) + ": <white>"
                + Numbers.format(Math.floor(mana.mana(player))) + " / " + Numbers.format(Math.floor(mana.max(player)))
                + "<gray>, восстановление " + Numbers.format(mana.regen(player)) + " в секунду");
    }

    private void send(CommandSender to, String text) {
        module.plugin().messages().send(to, text);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission(ADMIN)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            SUBS.stream().filter(s -> s.startsWith(prefix)).forEach(out::add);
            module.plugin().getServer().getOnlinePlayers().stream().map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix)).forEach(out::add);
        } else if (args.length == 2 && SUBS.contains(args[0].toLowerCase(Locale.ROOT))) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            module.plugin().getServer().getOnlinePlayers().stream().map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix)).forEach(out::add);
        }
        return out;
    }
}
