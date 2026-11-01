package com.dathis.rpg.modules.npc;

import com.dathis.rpg.core.gui.Icons;
import com.dathis.rpg.core.gui.Menu;
import org.bukkit.Material;

import java.util.List;

/** Список сделок торговца. Сделка: 1-2 предмета-цены → товар. */
final class TradesMenu extends Menu {

    static final int MAX_TRADES = 45;

    private final NpcModule module;
    private final NpcEditorMenu editor;

    TradesMenu(NpcModule module, NpcEditorMenu editor) {
        super(6, Icons.text("<dark_gray>Торговля NPC"));
        this.module = module;
        this.editor = editor;
    }

    @Override
    protected void render() {
        List<NpcTrade> trades = editor.draft().trades;
        for (int i = 0; i < MAX_TRADES && i < trades.size(); i++) {
            NpcTrade trade = trades.get(i);
            set(i, Icons.withHint(trade.result, "", "<yellow>Цена:", "<white>" + NpcEditorMenu.itemName(trade.cost1),
                            trade.cost2 == null ? "" : "<white>+ " + NpcEditorMenu.itemName(trade.cost2),
                            "", "<green>Нажмите, чтобы изменить"),
                    p -> new TradeEditMenu(module, this, editor, trade).open(p));
        }
        for (int i = MAX_TRADES; i < size(); i++) {
            set(i, Icons.filler());
        }
        set(45, Icons.of(Material.ARROW, "<gray>К редактору NPC"), editor::open);
        if (trades.size() < MAX_TRADES) {
            set(49, Icons.of(Material.LIME_CONCRETE, "<green>Новая сделка"),
                    p -> new TradeEditMenu(module, this, editor, null).open(p));
        }
        set(53, Icons.of(Material.BOOK, "<yellow>Подсказка",
                "<gray>Сделка: что игрок отдаёт (1-2 предмета)",
                "<gray>и что получает. Количество в стаке =",
                "<gray>сколько нужно отдать / сколько получит.",
                "<gray>Подходят предметы из /create-item.",
                "<gray>После правок нажмите «Сохранить»",
                "<gray>в редакторе NPC."));
    }
}
