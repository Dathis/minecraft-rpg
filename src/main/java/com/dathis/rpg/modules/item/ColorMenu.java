package com.dathis.rpg.modules.item;

import com.dathis.rpg.core.gui.Icons;
import com.dathis.rpg.core.gui.Menu;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;

import java.util.List;

/**
 * Выбор цвета названия. 16 стандартных цветов Minecraft одинаково выглядят в Java и Bedrock.
 * Свой HEX тоже можно, но Bedrock покажет ближайший из стандартных.
 */
final class ColorMenu extends Menu {

    private record Option(String key, String label, Material icon) {
    }

    private static final List<Option> OPTIONS = List.of(
            new Option("white", "Белый", Material.WHITE_WOOL),
            new Option("gray", "Серый", Material.LIGHT_GRAY_WOOL),
            new Option("dark_gray", "Тёмно-серый", Material.GRAY_WOOL),
            new Option("black", "Чёрный", Material.BLACK_WOOL),
            new Option("red", "Красный", Material.RED_WOOL),
            new Option("dark_red", "Тёмно-красный", Material.REDSTONE_BLOCK),
            new Option("gold", "Золотой", Material.ORANGE_WOOL),
            new Option("yellow", "Жёлтый", Material.YELLOW_WOOL),
            new Option("green", "Салатовый", Material.LIME_WOOL),
            new Option("dark_green", "Зелёный", Material.GREEN_WOOL),
            new Option("aqua", "Голубой", Material.LIGHT_BLUE_WOOL),
            new Option("dark_aqua", "Бирюзовый", Material.CYAN_WOOL),
            new Option("blue", "Синий", Material.BLUE_WOOL),
            new Option("dark_blue", "Тёмно-синий", Material.LAPIS_BLOCK),
            new Option("light_purple", "Розовый", Material.MAGENTA_WOOL),
            new Option("dark_purple", "Фиолетовый", Material.PURPLE_WOOL));

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29};

    private final ItemModule module;
    private final EditorMenu editor;

    ColorMenu(ItemModule module, EditorMenu editor) {
        super(5, Icons.text("<dark_gray>Цвет названия"));
        this.module = module;
        this.editor = editor;
    }

    @Override
    protected void render() {
        for (int i = 0; i < size(); i++) {
            set(i, Icons.filler());
        }
        for (int i = 0; i < OPTIONS.size(); i++) {
            Option option = OPTIONS.get(i);
            boolean current = option.key().equals(editor.draft().nameColor);
            set(SLOTS[i], Icons.of(option.icon(), "<" + option.key() + ">" + option.label(),
                            current ? "<green>Выбран сейчас" : "<gray>Нажмите, чтобы выбрать"),
                    p -> {
                        editor.draft().nameColor = option.key();
                        editor.open(p);
                    });
        }
        set(33, Icons.of(Material.BRUSH, "<yellow>Свой цвет (HEX)",
                        "<gray>Например: <white>#ff8800",
                        "<gray>На Bedrock покажется ближайший стандартный"),
                p -> module.plugin().chatInput().ask(p, "<yellow>Введите цвет в формате <white>#RRGGBB</white>:", text -> {
                    if (TextColor.fromHexString(text) != null) {
                        editor.draft().nameColor = text.toLowerCase();
                    } else {
                        module.plugin().messages().send(p, "<red>Неверный формат, нужно например #ff8800.</red>");
                    }
                    editor.open(p);
                }, () -> editor.open(p)));
        set(40, Icons.of(Material.ARROW, "<gray>Назад"), editor::open);
    }

    static Material iconFor(String key) {
        return OPTIONS.stream().filter(o -> o.key().equals(key)).map(Option::icon).findFirst().orElse(Material.BRUSH);
    }

    static String label(String key) {
        if (NamedTextColor.NAMES.value(key) == null) {
            return key;
        }
        return OPTIONS.stream().filter(o -> o.key().equals(key)).map(Option::label).findFirst().orElse(key);
    }
}
