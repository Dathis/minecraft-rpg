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

    /**
     * Разбивает длинный текст на строки для описания предмета (подсказки длиннее ~40 символов неудобно читать,
     * особенно на телефоне). Каждой строке добавляется префикс, например цвет {@code "<gray>"}.
     */
    public static List<String> wrap(String text, int width, String prefix) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.trim().split("\\s+")) {
            if (word.isEmpty()) {
                continue;
            }
            if (line.length() > 0 && line.length() + 1 + word.length() > width) {
                lines.add(prefix + line);
                line.setLength(0);
            }
            if (line.length() > 0) {
                line.append(' ');
            }
            line.append(word);
        }
        if (line.length() > 0) {
            lines.add(prefix + line);
        }
        return lines;
    }

    public static ItemStack filler() {
        return of(Material.GRAY_STAINED_GLASS_PANE, " ");
    }

    public static Component text(String miniMessage) {
        return MM.deserialize(miniMessage).decoration(TextDecoration.ITALIC, false);
    }
}
