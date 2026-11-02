package com.dathis.rpg.modules.hud;

import com.dathis.rpg.RpgPlugin;
import com.dathis.rpg.core.combat.HealthService;
import com.dathis.rpg.core.gui.Icons;
import com.dathis.rpg.core.input.Numbers;
import com.dathis.rpg.core.progression.LevelService;
import com.dathis.rpg.core.progression.ManaService;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Боковая панель справа экрана: уровень, ХП, мана и строки других модулей (текущий квест, позже деньги).
 *
 * У каждого игрока свой scoreboard. Строки сделаны классическим способом, который Geyser точно
 * передаёт на Bedrock: запись — невидимый код цвета, а текст строки — префикс команды (team) этой записи.
 * В Java числа справа скрыты (NumberFormat.blank), на Bedrock клиент их всё равно рисует.
 *
 * Если игроку поставил свой scoreboard другой плагин, панель его не трогает.
 */
final class SidebarHud implements Runnable, Listener {

    /** Bedrock и старые клиенты показывают не больше 15 строк. */
    private static final int MAX_LINES = 15;
    private static final String[] ENTRIES = new String[MAX_LINES];

    static {
        String codes = "0123456789abcdef";
        for (int i = 0; i < MAX_LINES; i++) {
            ENTRIES[i] = "§" + codes.charAt(i) + "§r";
        }
    }

    private static final class Board {
        final Scoreboard scoreboard;
        final Objective objective;
        final List<String> shown = new ArrayList<>();

        Board(Scoreboard scoreboard, Objective objective) {
            this.scoreboard = scoreboard;
            this.objective = objective;
        }
    }

    private final RpgPlugin plugin;
    private final Map<UUID, Board> boards = new HashMap<>();

    SidebarHud(RpgPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            update(player);
        }
    }

    private void update(Player player) {
        Board board = boards.get(player.getUniqueId());
        Scoreboard current = player.getScoreboard();
        boolean ours = board != null && current == board.scoreboard;
        boolean main = current == plugin.getServer().getScoreboardManager().getMainScoreboard();
        if (!ours && !main) {
            return; // панель другого плагина
        }
        if (!plugin.sidebar().isShownTo(player)) {
            if (ours) {
                player.setScoreboard(plugin.getServer().getScoreboardManager().getMainScoreboard());
            }
            boards.remove(player.getUniqueId());
            return;
        }
        if (board == null || !ours) {
            board = create();
            boards.put(player.getUniqueId(), board);
            player.setScoreboard(board.scoreboard);
        }
        draw(board, lines(player));
    }

    private Board create() {
        Scoreboard scoreboard = plugin.getServer().getScoreboardManager().getNewScoreboard();
        String title = plugin.getConfig().getString("hud.sidebar.title", "<gold><bold>Персонаж");
        Objective objective = scoreboard.registerNewObjective("rpg_hud", Criteria.DUMMY, Icons.text(title));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        objective.numberFormat(NumberFormat.blank());
        return new Board(scoreboard, objective);
    }

    private void draw(Board board, List<String> lines) {
        if (lines.size() > MAX_LINES) {
            lines = lines.subList(0, MAX_LINES);
        }
        if (lines.equals(board.shown)) {
            return;
        }
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String entry = ENTRIES[i];
            if (i < board.shown.size() && Objects.equals(board.shown.get(i), line)) {
                // текст тот же, но номер строки мог измениться
                board.objective.getScore(entry).setScore(lines.size() - i);
                continue;
            }
            Team team = board.scoreboard.getTeam("line" + i);
            if (team == null) {
                team = board.scoreboard.registerNewTeam("line" + i);
                team.addEntry(entry);
            }
            team.prefix(Icons.text(line));
            board.objective.getScore(entry).setScore(lines.size() - i);
        }
        for (int i = lines.size(); i < board.shown.size(); i++) {
            board.scoreboard.resetScores(ENTRIES[i]);
        }
        board.shown.clear();
        board.shown.addAll(lines);
    }

    /** Строки панели: уровень, ХП, мана, затем куски других модулей. */
    private List<String> lines(Player player) {
        List<String> out = new ArrayList<>();
        LevelService levels = plugin.levels();
        if (levels.isActive()) {
            int level = levels.level(player);
            String line = "<gray>Уровень <gold>" + level;
            if (level < levels.maxLevel()) {
                line += " <dark_gray>(" + (int) Math.floor(levels.progress(player) * 100) + "%)";
            }
            out.add(line);
        }
        HealthService health = plugin.health();
        double hp = Math.ceil(health.health(player));
        double maxHp = Math.ceil(health.maxHealth(player));
        double share = maxHp <= 0 ? 0 : hp / maxHp;
        String color = share > 0.5 ? "<green>" : share > 0.25 ? "<yellow>" : "<red>";
        out.add("<gray>ХП " + color + Numbers.format(hp) + "<gray> / " + color + Numbers.format(maxHp));
        ManaService mana = plugin.mana();
        if (mana.isActive()) {
            out.add("<gray>Мана <blue>" + Numbers.format(Math.floor(mana.mana(player)))
                    + "<gray> / <blue>" + Numbers.format(Math.floor(mana.max(player))));
        }
        List<String> sections = plugin.sidebar().sectionLines(player);
        if (!sections.isEmpty()) {
            out.add("");
            out.addAll(sections);
        }
        String footer = plugin.getConfig().getString("hud.sidebar.footer", "");
        if (footer != null && !footer.isBlank()) {
            out.add("");
            out.add(footer);
        }
        return out;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        boards.remove(event.getPlayer().getUniqueId());
    }

    /** Модуль выключается: вернуть всем общий scoreboard. */
    void hideAll() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            Board board = boards.get(player.getUniqueId());
            if (board != null && player.getScoreboard() == board.scoreboard) {
                player.setScoreboard(plugin.getServer().getScoreboardManager().getMainScoreboard());
            }
        }
        boards.clear();
    }
}
