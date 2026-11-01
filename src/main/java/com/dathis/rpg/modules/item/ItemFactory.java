package com.dathis.rpg.modules.item;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Превращает шаблон в настоящий ItemStack: название, цвет, описание, строки со статами оружия. */
final class ItemFactory {

    private final ItemRegistry registry;

    ItemFactory(ItemRegistry registry) {
        this.registry = registry;
    }

    ItemStack build(CustomItem item) {
        ItemStack stack = new ItemStack(item.material);
        stack.editMeta(meta -> {
            meta.displayName(plain(item.name).color(item.color()));
            meta.lore(lore(item));
            if (item.id != null) {
                meta.getPersistentDataContainer().set(registry.idKey(), PersistentDataType.STRING, item.id);
            }
            if (item.isWeapon()) {
                // Ванильная строка «+7 урона» врёт, урон считаем сами. Чтобы флаг скрытия сработал,
                // у предмета должны быть явные модификаторы, поэтому копируем ванильные (скорость атаки остаётся).
                meta.setAttributeModifiers(item.material.getDefaultAttributeModifiers(EquipmentSlot.HAND));
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            }
            if (item.isArmor()) {
                // Ванильная защита предмета отключается (явный модификатор заменяет ванильные),
                // а настоящие статы игроку даёт ArmorListener, пока броня надета.
                EquipmentSlot slot = item.material.getEquipmentSlot();
                meta.addAttributeModifier(Attribute.ARMOR,
                        new AttributeModifier(registry.baseKey(), 0, AttributeModifier.Operation.ADD_NUMBER, slot.getGroup()));
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            }
        });
        return stack;
    }

    static List<Component> lore(CustomItem item) {
        List<Component> lines = new ArrayList<>();
        for (String line : item.lore) {
            lines.add(plain(line).color(NamedTextColor.GRAY));
        }
        if (item.isWeapon()) {
            if (!lines.isEmpty()) {
                lines.add(Component.empty());
            }
            lines.add(plain("Урон: " + num(item.damageMin) + " - " + num(item.damageMax)).color(NamedTextColor.RED));
            if (item.critChance > 0) {
                lines.add(plain("Крит. удар: " + num(item.critChance) + "%").color(NamedTextColor.YELLOW));
            }
            if (item.vampirism > 0) {
                lines.add(plain("Вампиризм: " + num(item.vampirism) + "%").color(NamedTextColor.DARK_RED));
            }
        }
        if (item.isArmor()) {
            if (!lines.isEmpty()) {
                lines.add(Component.empty());
            }
            if (item.armor > 0) {
                lines.add(plain("Броня: +" + num(item.armor)).color(NamedTextColor.AQUA));
            }
            if (item.bonusHealth > 0) {
                lines.add(plain("Здоровье: +" + num(item.bonusHealth)).color(NamedTextColor.RED));
            }
            if (item.regeneration > 0) {
                lines.add(plain("Регенерация: +" + num(item.regeneration) + " ХП/сек").color(NamedTextColor.GREEN));
            }
        }
        return lines;
    }

    static Component plain(String text) {
        return Component.text(text).decoration(TextDecoration.ITALIC, false);
    }

    /** 10.0 -> "10", 2.5 -> "2.5" */
    static String num(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((long) value);
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
