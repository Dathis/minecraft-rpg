package com.dathis.rpg.modules.quest;

import com.dathis.rpg.core.gui.Icons;
import com.dathis.rpg.core.gui.Menu;
import com.dathis.rpg.modules.npc.NpcModule;
import com.dathis.rpg.modules.npc.NpcTemplate;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Окно с книгами квестов. Открывается при нажатии на NPC-квестодателя (его квесты)
 * или командой /quests (журнал: взятые и выполненные квесты игрока).
 */
final class QuestBoardMenu extends Menu {

    private static final int MAX_SHOWN = 45;

    private final QuestModule module;
    private final Player viewer;
    private final NpcTemplate npc; // null — журнал

    QuestBoardMenu(QuestModule module, Player viewer, NpcTemplate npc) {
        super(rows(module, viewer, npc), npc != null ? npc.displayName() : Icons.text("<dark_gray>Мои квесты"));
        this.module = module;
        this.viewer = viewer;
        this.npc = npc;
    }

    private static int rows(QuestModule module, Player viewer, NpcTemplate npc) {
        int count = quests(module, viewer, npc).size();
        return Math.max(2, Math.min(6, (Math.min(count, MAX_SHOWN) + 8) / 9 + 1));
    }

    private static List<Quest> quests(QuestModule module, Player viewer, NpcTemplate npc) {
        if (npc != null) {
            return module.registry().ofNpc(npc.id());
        }
        List<Quest> list = new ArrayList<>(module.activeQuests(viewer));
        for (String id : module.progress().get(viewer.getUniqueId()).completed) {
            Quest quest = module.registry().get(id);
            if (quest != null) {
                list.add(quest);
            }
        }
        return list;
    }

    @Override
    protected void render() {
        List<Quest> quests = quests(module, viewer, npc);
        int bottom = size() - 9;
        for (int i = 0; i < bottom; i++) {
            set(i, Icons.of(Material.LIGHT_GRAY_STAINED_GLASS_PANE, " "));
        }
        for (int i = 0; i < quests.size() && i < bottom; i++) {
            Quest quest = quests.get(i);
            QuestModule.Status status = module.status(viewer, quest);
            if (npc != null && status == QuestModule.Status.AVAILABLE) {
                // У квестодателя: прочитал описание на книге, нажал — квест взят, без лишних окон
                set(i, Icons.withHint(module.book(viewer, quest), "", "<green>Нажмите, чтобы взять квест"), p -> {
                    module.accept(p, quest);
                    redraw();
                });
            } else if (npc != null && status == QuestModule.Status.READY) {
                set(i, Icons.withHint(module.book(viewer, quest), "", "<green><bold>Нажмите, чтобы сдать квест"), p -> {
                    module.turnIn(p, quest);
                    redraw();
                });
            } else {
                set(i, Icons.withHint(module.book(viewer, quest), "", "<gray>Нажмите, чтобы открыть подробнее"),
                        p -> new QuestViewMenu(module, quest, this, npc != null).open(p));
            }
        }
        for (int i = bottom; i < size(); i++) {
            set(i, Icons.filler());
        }
        if (quests.isEmpty()) {
            set(4, Icons.of(Material.BOOK, "<gray>Квестов пока нет"));
        }
        set(bottom + 8, Icons.of(Material.BOOK, "<yellow>Обозначения",
                "<white>Книга с пером <gray>— можно взять",
                "<white>Книга с пером, сияет <gray>— взят",
                "<white>Книга, сияет <gray>— выполнен",
                "<white>Простая книга <gray>— пока недоступен"));
        NpcModule npcs = module.npcs();
        if (npc != null && npc.hasTrades() && npcs != null) {
            set(bottom + 4, Icons.of(Material.EMERALD, "<green>Торговля"), p -> npcs.openTrade(p, npc));
        }
        set(bottom, Icons.of(Material.BARRIER, "<red>Закрыть"), Player::closeInventory);
    }

    /** Заголовок окна нельзя поменять, поэтому для «назад» создаём окно заново (могли измениться квесты). */
    QuestBoardMenu fresh() {
        return new QuestBoardMenu(module, viewer, npc);
    }

    Player viewer() {
        return viewer;
    }
}
