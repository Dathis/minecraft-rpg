package com.dathis.rpg.core.gui;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.function.Consumer;

/**
 * Окно выбора из списка (по 45 на страницу) с кнопками «Назад» и страницами.
 * Каждый вариант — иконка и действие по нажатию.
 */
public final class ChoiceMenu extends Menu {

    public record Choice(ItemStack icon, Consumer<Player> action) {
    }

    private static final int PER_PAGE = 45;

    private final String title;
    private final List<Choice> choices;
    private final Menu back;
    private final int page;

    public ChoiceMenu(String title, List<Choice> choices, Menu back) {
        this(title, choices, back, 0);
    }

    private ChoiceMenu(String title, List<Choice> choices, Menu back, int page) {
        super(6, Icons.text("<dark_gray>" + title));
        this.title = title;
        this.choices = choices;
        this.back = back;
        this.page = page;
    }

    @Override
    protected void render() {
        int from = page * PER_PAGE;
        for (int i = 0; i < PER_PAGE && from + i < choices.size(); i++) {
            Choice choice = choices.get(from + i);
            set(i, choice.icon(), choice.action());
        }
        for (int i = PER_PAGE; i < size(); i++) {
            set(i, Icons.filler());
        }
        if (choices.isEmpty()) {
            set(22, Icons.of(Material.BARRIER, "<gray>Пока ничего нет"));
        }
        if (page > 0) {
            set(45, Icons.of(Material.ARROW, "<yellow>Предыдущая страница"),
                    p -> new ChoiceMenu(title, choices, back, page - 1).open(p));
        }
        if (back != null) {
            set(49, Icons.of(Material.BARRIER, "<red>Назад"), back::open);
        }
        if (from + PER_PAGE < choices.size()) {
            set(53, Icons.of(Material.ARROW, "<yellow>Следующая страница"),
                    p -> new ChoiceMenu(title, choices, back, page + 1).open(p));
        }
    }
}
