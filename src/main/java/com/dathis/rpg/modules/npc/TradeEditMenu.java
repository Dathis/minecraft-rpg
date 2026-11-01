package com.dathis.rpg.modules.npc;

import com.dathis.rpg.core.gui.Icons;
import com.dathis.rpg.core.gui.Menu;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Одна сделка: положите предметы из своего инвентаря в слоты цены и товара. */
final class TradeEditMenu extends Menu {

    private final NpcModule module;
    private final TradesMenu back;
    private final NpcEditorMenu editor;
    private final NpcTrade original; // null — новая сделка
    private final NpcTrade draft;

    TradeEditMenu(NpcModule module, TradesMenu back, NpcEditorMenu editor, NpcTrade original) {
        super(3, Icons.text("<dark_gray>Сделка"));
        this.module = module;
        this.back = back;
        this.editor = editor;
        this.original = original;
        this.draft = original == null ? new NpcTrade(null, null, null) : original.copy();
    }

    @Override
    protected void render() {
        for (int i = 0; i < size(); i++) {
            set(i, Icons.filler());
        }
        setInput(10, slotIcon(draft.cost1, "Цена", true), (p, cursor) -> {
            draft.cost1 = cursor;
            redraw();
        });
        setInput(11, slotIcon(draft.cost2, "Цена (второй предмет)", false), (p, cursor) -> {
            draft.cost2 = cursor;
            redraw();
        });
        set(13, Icons.of(Material.ARROW, "<gray>→"));
        setInput(15, slotIcon(draft.result, "Товар", true), (p, cursor) -> {
            draft.result = cursor;
            redraw();
        });
        set(18, Icons.of(Material.ARROW, "<gray>К списку сделок"), back::open);
        set(22, Icons.of(draft.complete() ? Material.LIME_CONCRETE : Material.GRAY_CONCRETE,
                draft.complete() ? "<green>Готово" : "<gray>Готово",
                draft.complete() ? "<gray>Добавить сделку в список" : "<gray>Нужны цена и товар"), this::done);
        if (original != null) {
            set(26, Icons.of(Material.BARRIER, "<red>Удалить сделку"), p -> {
                editor.draft().trades.remove(original);
                back.open(p);
            });
        }
    }

    /** Shift-клик: заполняет первую пустую ячейку (цена, потом товар). */
    @Override
    protected void quickAdd(Player player, ItemStack item) {
        if (draft.cost1 == null) {
            draft.cost1 = item.clone();
        } else if (draft.result == null) {
            draft.result = item.clone();
        } else if (draft.cost2 == null) {
            draft.cost2 = item.clone();
        }
        redraw();
    }

    private void done(Player player) {
        if (!draft.complete()) {
            module.plugin().messages().send(player, "<red>Положите предмет-цену и товар.</red>");
            return;
        }
        int index = original == null ? -1 : editor.draft().trades.indexOf(original);
        if (index >= 0) {
            editor.draft().trades.set(index, draft);
        } else {
            editor.draft().trades.add(draft);
        }
        module.plugin().messages().send(player, "<green>Сделка готова. Не забудьте сохранить NPC.");
        back.open(player);
    }

    private static ItemStack slotIcon(ItemStack item, String label, boolean required) {
        if (item != null) {
            return Icons.withHint(item, "", "<aqua>" + label + ": " + item.getAmount() + " шт.",
                    "<gray>Положите другой предмет, чтобы заменить",
                    "<gray>Нажмите пустым курсором, чтобы убрать");
        }
        return Icons.of(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "<gray>" + label + ": пусто",
                required ? "<gray>Обязательно" : "<gray>Необязательно",
                "<green>Положите сюда предмет из инвентаря",
                "<gray>Количество в стаке важно");
    }
}
