package com.dathis.rpg.modules.mob;

import com.dathis.rpg.core.gui.Icons;
import com.dathis.rpg.core.gui.Menu;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Снаряжение моба. Возьмите предмет из своего инвентаря и положите в нужный слот
 * (или shift-клик по предмету — встанет в подходящий слот сам). Пустой курсор снимает предмет.
 * Предмет у вас не забирается, в окно кладётся копия. Подходят и предметы из /create-item.
 */
final class EquipmentMenu extends Menu {

    private record Slot(int index, EquipmentSlot slot, String label) {
    }

    private static final Slot[] SLOTS = {
            new Slot(10, EquipmentSlot.HEAD, "Шлем"),
            new Slot(11, EquipmentSlot.CHEST, "Нагрудник"),
            new Slot(12, EquipmentSlot.LEGS, "Поножи"),
            new Slot(13, EquipmentSlot.FEET, "Ботинки"),
            new Slot(15, EquipmentSlot.HAND, "Основная рука"),
            new Slot(16, EquipmentSlot.OFF_HAND, "Вторая рука"),
    };

    private final MobModule module;
    private final MobEditorMenu editor;

    EquipmentMenu(MobModule module, MobEditorMenu editor) {
        super(3, Icons.text("<dark_gray>Снаряжение моба"));
        this.module = module;
        this.editor = editor;
    }

    @Override
    protected void render() {
        for (int i = 0; i < size(); i++) {
            set(i, Icons.filler());
        }
        for (Slot slot : SLOTS) {
            ItemStack current = editor.draft().equipment.get(slot.slot());
            ItemStack icon = current != null
                    ? Icons.withHint(current, "", "<aqua>" + slot.label(),
                    "<gray>Положите другой предмет, чтобы заменить", "<gray>Нажмите пустым курсором, чтобы снять")
                    : Icons.of(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "<gray>" + slot.label() + ": пусто",
                    "<green>Положите сюда предмет из инвентаря");
            setInput(slot.index(), icon, (p, cursor) -> {
                if (cursor == null) {
                    editor.draft().equipment.remove(slot.slot());
                } else {
                    put(slot.slot(), cursor);
                }
                redraw();
            });
        }
        set(18, Icons.of(Material.ARROW, "<gray>К редактору"), editor::open);
        set(22, Icons.of(Material.BUCKET, "<red>Снять всё"), p -> {
            editor.draft().equipment.clear();
            redraw();
        });
        set(26, Icons.of(Material.BOOK, "<yellow>Подсказка",
                "<gray>Положите предмет в слот или сделайте",
                "<gray>shift-клик по нему в своём инвентаре.",
                "<gray>Моб появится только с этим снаряжением.",
                "<gray>При смерти оно не выпадает (для этого есть лут)."));
    }

    /** Shift-клик: броня встаёт в свой слот, остальное — в основную руку (или во вторую, если первая занята). */
    @Override
    protected void quickAdd(Player player, ItemStack item) {
        EquipmentSlot slot = item.getType().getEquipmentSlot();
        boolean armor = slot == EquipmentSlot.HEAD || slot == EquipmentSlot.CHEST
                || slot == EquipmentSlot.LEGS || slot == EquipmentSlot.FEET || slot == EquipmentSlot.OFF_HAND;
        if (!armor) {
            slot = editor.draft().equipment.containsKey(EquipmentSlot.HAND) ? EquipmentSlot.OFF_HAND : EquipmentSlot.HAND;
        }
        put(slot, item);
        redraw();
    }

    private void put(EquipmentSlot slot, ItemStack item) {
        ItemStack copy = item.clone();
        copy.setAmount(1);
        editor.draft().equipment.put(slot, copy);
    }
}
