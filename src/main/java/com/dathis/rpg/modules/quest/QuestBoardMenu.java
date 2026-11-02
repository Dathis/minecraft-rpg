package com.dathis.rpg.modules.quest;

import com.dathis.rpg.core.gui.Icons;
import com.dathis.rpg.core.gui.Menu;
import com.dathis.rpg.modules.npc.NpcModule;
import com.dathis.rpg.modules.npc.NpcTemplate;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Окно с книгами квестов:
 * <ul>
 *     <li>у NPC-квестодателя — его квесты, здесь их берут и сдают;</li>
 *     <li>у доски заданий — задания дня (и взятые с доски), здесь их берут и сдают;</li>
 *     <li>журнал /quests — взятые и выполненные квесты игрока;</li>
 *     <li>доска из журнала — задания дня только посмотреть (брать нужно у доски).</li>
 * </ul>
 */
final class QuestBoardMenu extends Menu {

    enum Kind { NPC, BOARD, JOURNAL, BOARD_VIEW }

    private static final int MAX_SHOWN = 45;

    private final QuestModule module;
    private final Player viewer;
    private final NpcTemplate npc; // только для Kind.NPC
    private final Kind kind;

    QuestBoardMenu(QuestModule module, Player viewer, NpcTemplate npc) {
        this(module, viewer, npc, npc != null ? Kind.NPC : Kind.JOURNAL);
    }

    QuestBoardMenu(QuestModule module, Player viewer, NpcTemplate npc, Kind kind) {
        super(rows(module, viewer, npc, kind), title(npc, kind));
        this.module = module;
        this.viewer = viewer;
        this.npc = npc;
        this.kind = kind;
    }

    private static Component title(NpcTemplate npc, Kind kind) {
        return switch (kind) {
            case NPC -> npc.displayName();
            case BOARD, BOARD_VIEW -> Icons.text("<dark_gray>Доска заданий");
            case JOURNAL -> Icons.text("<dark_gray>Мои квесты");
        };
    }

    private static int rows(QuestModule module, Player viewer, NpcTemplate npc, Kind kind) {
        int count = quests(module, viewer, npc, kind).size();
        return Math.max(2, Math.min(6, (Math.min(count, MAX_SHOWN) + 8) / 9 + 1));
    }

    private static List<Quest> quests(QuestModule module, Player viewer, NpcTemplate npc, Kind kind) {
        return switch (kind) {
            case NPC -> module.registry().ofNpc(npc.id());
            case BOARD -> module.boardQuests(viewer);
            case BOARD_VIEW -> module.boardToday();
            case JOURNAL -> {
                List<Quest> list = new ArrayList<>(module.activeQuests(viewer));
                for (String id : module.progress().get(viewer.getUniqueId()).completed) {
                    Quest quest = module.registry().get(id);
                    if (quest != null && !list.contains(quest)) {
                        list.add(quest);
                    }
                }
                yield list;
            }
        };
    }

    /** Здесь можно брать и сдавать квесты (у NPC или у доски). */
    boolean canAct() {
        return kind == Kind.NPC || kind == Kind.BOARD;
    }

    @Override
    protected void render() {
        List<Quest> quests = quests(module, viewer, npc, kind);
        int bottom = size() - 9;
        for (int i = 0; i < bottom; i++) {
            set(i, Icons.of(Material.LIGHT_GRAY_STAINED_GLASS_PANE, " "));
        }
        for (int i = 0; i < quests.size() && i < bottom; i++) {
            Quest quest = quests.get(i);
            QuestModule.Status status = module.status(viewer, quest);
            if (canAct() && status == QuestModule.Status.AVAILABLE) {
                // У квестодателя: прочитал описание на книге, нажал — квест взят, без лишних окон
                set(i, Icons.withHint(module.book(viewer, quest), "", "<green>Нажмите, чтобы взять квест"), p -> {
                    module.accept(p, quest);
                    redraw();
                });
            } else if (canAct() && status == QuestModule.Status.READY) {
                set(i, Icons.withHint(module.book(viewer, quest), "", "<green><bold>Нажмите, чтобы сдать квест"), p -> {
                    module.turnIn(p, quest);
                    redraw();
                });
            } else {
                set(i, Icons.withHint(module.book(viewer, quest), "", "<gray>Нажмите, чтобы открыть подробнее"),
                        p -> new QuestViewMenu(module, quest, this, canAct()).open(p));
            }
        }
        for (int i = bottom; i < size(); i++) {
            set(i, Icons.filler());
        }
        if (quests.isEmpty()) {
            set(4, Icons.of(Material.BOOK, kind == Kind.BOARD || kind == Kind.BOARD_VIEW
                    ? "<gray>Сегодня заданий нет" : "<gray>Квестов пока нет"));
        }
        set(bottom + 8, Icons.of(Material.BOOK, "<yellow>Обозначения",
                "<white>Книга с пером <gray>— можно взять",
                "<white>Книга с пером, сияет <gray>— взят",
                "<white>Книга, сияет <gray>— выполнен",
                "<white>Простая книга <gray>— пока недоступен"));
        NpcModule npcs = module.npcs();
        if (kind == Kind.NPC && npc.hasTrades() && npcs != null) {
            set(bottom + 4, Icons.of(Material.EMERALD, "<green>Торговля"), p -> npcs.openTrade(p, npc));
        }
        if (kind == Kind.BOARD || kind == Kind.BOARD_VIEW) {
            List<String> lore = new ArrayList<>();
            lore.add("<gray>Каждый день доска показывает");
            lore.add("<gray>новые задания. Взять и сдать их");
            lore.add("<gray>можно у любой доски заданий.");
            lore.add("");
            lore.add("<yellow>Новые задания через " + module.untilBoardChange());
            if (kind == Kind.BOARD_VIEW) {
                lore.add("");
                lore.add("<gold>Где доски:");
                List<String> boards = module.registry().boards();
                int shown = 0;
                for (String key : boards) {
                    Location l = module.registry().boardLocation(key);
                    if (l != null && shown < 5) {
                        lore.add("<white>" + l.getWorld().getName() + " " + l.getBlockX() + " " + l.getBlockY()
                                + " " + l.getBlockZ());
                        shown++;
                    }
                }
                if (shown == 0) {
                    lore.add("<gray>(досок пока нет)");
                }
            }
            set(bottom + 4, Icons.of(Material.CLOCK, "<gold>Доска заданий", lore));
        }
        if (kind == Kind.JOURNAL && !module.registry().boardPool().isEmpty()) {
            set(bottom + 4, Icons.of(Material.OAK_HANGING_SIGN, "<gold>Задания дня",
                            "<gray>Что сегодня на доске заданий", "", "<green>Нажмите, чтобы посмотреть"),
                    p -> new QuestBoardMenu(module, p, null, Kind.BOARD_VIEW).open(p));
        }
        if (kind == Kind.BOARD_VIEW) {
            set(bottom, Icons.of(Material.ARROW, "<gray>К журналу"), p -> new QuestBoardMenu(module, p, null).open(p));
        } else {
            set(bottom, Icons.of(Material.BARRIER, "<red>Закрыть"), Player::closeInventory);
        }
    }

    /** Заголовок окна нельзя поменять, поэтому для «назад» создаём окно заново (могли измениться квесты). */
    QuestBoardMenu fresh() {
        return new QuestBoardMenu(module, viewer, npc, kind);
    }

    Player viewer() {
        return viewer;
    }
}
