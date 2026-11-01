package com.dathis.rpg.modules.quest;

import com.dathis.rpg.core.gui.Icons;
import com.dathis.rpg.core.gui.Menu;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Награда предметами. Положите предмет из инвентаря в слот (или shift-клик) — он попадёт в награду
 * с тем же количеством. Нажмите пустым курсором по награде, чтобы убрать её.
 */
final class RewardsMenu extends Menu {

    private final QuestEditorMenu editor;

    RewardsMenu(QuestEditorMenu editor) {
        super(3, Icons.text("<dark_gray>Награда за квест"));
        this.editor = editor;
    }

    @Override
    protected void render() {
        var rewards = editor.draft().rewards;
        for (int i = 0; i < Quest.MAX_REWARDS; i++) {
            int index = i;
            ItemStack icon = i < rewards.size()
                    ? Icons.withHint(rewards.get(i), "", "<gray>Положите другой предмет, чтобы заменить",
                    "<gray>Нажмите пустым курсором, чтобы убрать")
                    : Icons.of(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "<gray>Пусто",
                    "<green>Положите сюда предмет из инвентаря");
            setInput(i, icon, (p, cursor) -> {
                if (cursor == null) {
                    if (index < rewards.size()) {
                        rewards.remove(index);
                    }
                } else if (index < rewards.size()) {
                    rewards.set(index, cursor);
                } else {
                    rewards.add(cursor);
                }
                redraw();
            });
        }
        for (int i = Quest.MAX_REWARDS; i < size(); i++) {
            set(i, Icons.filler());
        }
        set(18, Icons.of(Material.ARROW, "<gray>К квесту"), editor::open);
        set(22, Icons.of(Material.BUCKET, "<red>Убрать всё"), p -> {
            rewards.clear();
            redraw();
        });
        set(26, Icons.of(Material.BOOK, "<yellow>Подсказка",
                "<gray>Количество в стаке = сколько получит игрок.",
                "<gray>Подходят предметы из /create-item.",
                "<gray>Опыт настраивается в редакторе квеста."));
    }

    @Override
    protected void quickAdd(Player player, ItemStack item) {
        if (editor.draft().rewards.size() < Quest.MAX_REWARDS) {
            editor.draft().rewards.add(item.clone());
            redraw();
        }
    }
}
