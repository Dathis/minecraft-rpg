package com.dathis.rpg.core.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Быстрое создание иконок для кнопок меню. Текст в формате MiniMessage. */
public final class Icons {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private Icons() {
    }

    public static ItemStack of(Material material, String name, String... lore) {
        return of(material, name, List.of(lore));
    }

    public static ItemStack of(Material material, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material);
        stack.editMeta(meta -> {
            meta.displayName(text(name));
            List<Component> lines = new ArrayList<>();
            for (String line : lore) {
                lines.add(text(line));
            }
            meta.lore(lines);
            meta.addItemFlags(ItemFlag.values());
        });
        return stack;
    }

    /** Добавляет к готовому предмету строки подсказки снизу (для превью). */
    public static ItemStack withHint(ItemStack base, String... hint) {
        ItemStack stack = base.clone();
        stack.editMeta(meta -> {
            List<Component> lines = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
            for (String line : hint) {
                lines.add(text(line));
            }
            meta.lore(lines);
        });
        return stack;
    }

    public static ItemStack filler() {
        return of(Material.GRAY_STAINED_GLASS_PANE, " ");
    }

    public static Component text(String miniMessage) {
        return MM.deserialize(miniMessage).decoration(TextDecoration.ITALIC, false);
    }
}
