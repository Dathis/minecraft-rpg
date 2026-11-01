package com.dathis.rpg.modules.mob;

import com.dathis.rpg.core.gui.Icons;
import com.dathis.rpg.core.gui.Menu;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Снаряжение моба. Возьмите предмет в руку и нажмите на слот — он встанет туда.
 * Нажатие пустой рукой очищает слот. Подходят и предметы из /create-item.
 */
final class EquipmentMenu extends Menu {

    private record Slot(int index, EquipmentSlot slot, String label, Material placeholder) {
    }

    private static final Slot[] SLOTS = {
            new Slot(10, EquipmentSlot.HEAD, "Шлем", Material.LEATHER_HELMET),
            new Slot(11, EquipmentSlot.CHEST, "Нагрудник", Material.LEATHER_CHESTPLATE),
            new Slot(12, EquipmentSlot.LEGS, "Поножи", Material.LEATHER_LEGGINGS),
            new Slot(13, EquipmentSlot.FEET, "Ботинки", Material.LEATHER_BOOTS),
            new Slot(15, EquipmentSlot.HAND, "Основная рука", Material.WOODEN_SWORD),
            new Slot(16, EquipmentSlot.OFF_HAND, "Вторая рука", Material.SHIELD),
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
                    "<gray>С предметом в руке: заменить", "<gray>Пустой рукой: снять")
                    : Icons.of(Material.GRAY_DYE, "<gray>" + slot.label() + ": пусто",
                    "<green>Возьмите предмет в руку и нажмите");
            set(slot.index(), icon, p -> put(p, slot));
        }
        set(18, Icons.of(Material.ARROW, "<gray>К редактору"), editor::open);
        set(22, Icons.of(Material.BUCKET, "<red>Снять всё"), p -> {
            editor.draft().equipment.clear();
            redraw();
        });
        set(26, Icons.of(Material.BOOK, "<yellow>Подсказка",
                "<gray>Моб появится только с этим снаряжением,",
                "<gray>без случайной ванильной брони и оружия.",
                "<gray>Снаряжение не выпадает при смерти,",
                "<gray>для этого добавьте его в лут."));
    }

    private void put(Player player, Slot slot) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.isEmpty()) {
            editor.draft().equipment.remove(slot.slot());
        } else {
            ItemStack copy = hand.clone();
            copy.setAmount(1);
            editor.draft().equipment.put(slot.slot(), copy);
        }
        redraw();
    }
}
