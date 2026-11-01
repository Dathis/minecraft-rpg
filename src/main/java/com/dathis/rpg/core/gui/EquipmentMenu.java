package com.dathis.rpg.core.gui;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/**
 * Снаряжение (мобы, NPC). Возьмите предмет из своего инвентаря и положите в нужный слот
 * (или shift-клик по предмету — встанет в подходящий слот сам). Пустой курсор снимает предмет.
 * Предмет у вас не забирается, в окно кладётся копия. Подходят и предметы из /create-item.
 */
public final class EquipmentMenu extends Menu {

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

    private final Map<EquipmentSlot, ItemStack> equipment;
    private final Menu back;
    private final String[] help;

    /**
     * @param equipment изменяемая карта снаряжения черновика
     * @param back      куда вернуться
     * @param help      строки подсказки (MiniMessage)
     */
    public EquipmentMenu(String title, Map<EquipmentSlot, ItemStack> equipment, Menu back, String... help) {
        super(3, Icons.text("<dark_gray>" + title));
        this.equipment = equipment;
        this.back = back;
        this.help = help;
    }

    @Override
    protected void render() {
        for (int i = 0; i < size(); i++) {
            set(i, Icons.filler());
        }
        for (Slot slot : SLOTS) {
            ItemStack current = equipment.get(slot.slot());
            ItemStack icon = current != null
                    ? Icons.withHint(current, "", "<aqua>" + slot.label(),
                    "<gray>Положите другой предмет, чтобы заменить", "<gray>Нажмите пустым курсором, чтобы снять")
                    : Icons.of(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "<gray>" + slot.label() + ": пусто",
                    "<green>Положите сюда предмет из инвентаря");
            setInput(slot.index(), icon, (p, cursor) -> {
                if (cursor == null) {
                    equipment.remove(slot.slot());
                } else {
                    put(slot.slot(), cursor);
                }
                redraw();
            });
        }
        set(18, Icons.of(Material.ARROW, "<gray>К редактору"), back::open);
        set(22, Icons.of(Material.BUCKET, "<red>Снять всё"), p -> {
            equipment.clear();
            redraw();
        });
        set(26, Icons.of(Material.BOOK, "<yellow>Подсказка", help));
    }

    /** Shift-клик: броня встаёт в свой слот, остальное — в основную руку (или во вторую, если первая занята). */
    @Override
    protected void quickAdd(Player player, ItemStack item) {
        EquipmentSlot slot = item.getType().getEquipmentSlot();
        boolean armor = slot == EquipmentSlot.HEAD || slot == EquipmentSlot.CHEST
                || slot == EquipmentSlot.LEGS || slot == EquipmentSlot.FEET || slot == EquipmentSlot.OFF_HAND;
        if (!armor) {
            slot = equipment.containsKey(EquipmentSlot.HAND) ? EquipmentSlot.OFF_HAND : EquipmentSlot.HAND;
        }
        put(slot, item);
        redraw();
    }

    private void put(EquipmentSlot slot, ItemStack item) {
        ItemStack copy = item.clone();
        copy.setAmount(1);
        equipment.put(slot, copy);
    }
}
