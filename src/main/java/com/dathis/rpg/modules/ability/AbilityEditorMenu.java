package com.dathis.rpg.modules.ability;

import com.dathis.rpg.core.gui.Icons;
import com.dathis.rpg.core.gui.Menu;
import com.dathis.rpg.core.input.Numbers;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

/**
 * Редактор способности. Сверху превью руны, ниже общие настройки (руна, название, мана, перезарядка, уровень)
 * и числа выбранной механики. Текст и числа вводятся через чат, после ввода окно открывается снова.
 */
final class AbilityEditorMenu extends Menu {

    private static final int[] PARAM_SLOTS = {30, 31, 32, 33};

    private final AbilityModule module;
    private final Ability draft;
    private boolean confirmDelete;

    AbilityEditorMenu(AbilityModule module, Ability draft) {
        super(5, Icons.text("<dark_gray>Способность: " + draft.mechanic.label));
        this.module = module;
        this.draft = draft;
    }

    @Override
    protected void render() {
        for (int i = 0; i < size(); i++) {
            set(i, Icons.filler());
        }
        set(4, Icons.withHint(module.registry().rune(draft), "",
                "<dark_gray>Так будет выглядеть руна",
                draft.id == null ? "<dark_gray>Ещё не сохранена" : "<dark_gray>ID: " + draft.id));

        // Внешний вид
        set(19, Icons.of(draft.material, "<yellow>Предмет руны",
                        "<gray>Сейчас: <white>" + pretty(draft.material),
                        "<gray>Возьмите любой предмет в руку",
                        "<gray>и нажмите здесь",
                        "", "<green>Нажмите, чтобы взять из руки"),
                this::takeFromHand);
        set(20, Icons.of(Material.NAME_TAG, "<yellow>Название",
                        "<gray>Сейчас: <white>" + AbilityRegistry.escape(draft.name),
                        "", "<green>Нажмите и напишите в чат"),
                p -> ask(p, "<yellow>Введите название способности:", text -> draft.name = text));
        set(21, Icons.of(Material.WHITE_DYE, "<" + draft.nameColor + ">Цвет названия",
                        "<gray>Сейчас: <" + draft.nameColor + ">" + draft.nameColor,
                        "", "<green>Нажмите, чтобы сменить"),
                p -> {
                    draft.nameColor = draft.nextColor();
                    redraw();
                });
        List<String> loreHint = new ArrayList<>();
        loreHint.add("<gray>Строк: <white>" + draft.lore.size()
                + (draft.lore.isEmpty() ? " <gray>(описание механики)" : ""));
        loreHint.add("");
        loreHint.add("<green>Нажмите и напишите в чат");
        loreHint.add("<gray>Новая строка: символ <white>|");
        set(22, Icons.of(Material.WRITABLE_BOOK, "<yellow>Описание", loreHint),
                p -> ask(p, "<yellow>Введите описание. Разделяйте строки символом <white>|</white>:",
                        text -> draft.lore = new ArrayList<>(Arrays.stream(text.split("\\|"))
                                .map(String::trim).filter(s -> !s.isEmpty()).toList())));
        set(23, Icons.of(Material.BUCKET, "<yellow>Очистить описание",
                        "<gray>На руне будет описание механики"),
                p -> {
                    draft.lore = new ArrayList<>();
                    redraw();
                });
        int maxLevel = module.plugin().levels().maxLevel();
        set(25, Icons.of(Material.EXPERIENCE_BOTTLE, "<gold>Уровень",
                        "<gray>Сейчас: <white>" + (draft.level > 1 ? draft.level : "любой"),
                        "<gray>С какого уровня персонажа",
                        "<gray>руна работает",
                        "", "<green>Нажмите и напишите число"),
                p -> askNumber(p, "<yellow>С какого уровня способность? (1-" + maxLevel + ", 1 — с любого)",
                        1, maxLevel, v -> draft.level = (int) Math.round(v)));

        // Цена
        set(28, Icons.of(Material.LAPIS_LAZULI, "<blue>Мана",
                        "<gray>Сейчас: <white>" + Numbers.format(draft.manaCost),
                        "<gray>Сколько маны тратит одно применение",
                        "", "<green>Нажмите и напишите число"),
                p -> askNumber(p, "<yellow>Сколько маны стоит способность?", 0, 1_000_000, v -> draft.manaCost = v));
        set(29, Icons.of(Material.CLOCK, "<white>Перезарядка",
                        "<gray>Сейчас: <white>" + Numbers.format(draft.cooldown) + " сек",
                        "<gray>Число предметов на руне показывает,",
                        "<gray>сколько секунд осталось",
                        "", "<green>Нажмите и напишите число секунд"),
                p -> askNumber(p, "<yellow>Перезарядка в секундах (0-3600):", 0, 3600, v -> draft.cooldown = v));

        // Числа механики
        List<Mechanic.Param> params = draft.mechanic.params;
        for (int i = 0; i < params.size() && i < PARAM_SLOTS.length; i++) {
            Mechanic.Param param = params.get(i);
            String unit = param.unit().isEmpty() ? "" : " " + param.unit();
            set(PARAM_SLOTS[i], Icons.of(param.icon(), "<aqua>" + param.label(),
                            "<gray>Сейчас: <white>" + Numbers.format(draft.get(param.stat())) + unit,
                            "<gray>" + param.hint(),
                            "<gray>От " + Numbers.format(param.min()) + " до " + Numbers.format(param.max()),
                            "", "<green>Нажмите и напишите число"),
                    p -> askNumber(p, "<yellow>" + param.label() + " (" + Numbers.format(param.min()) + "-"
                                    + Numbers.format(param.max()) + "):",
                            param.min(), param.max(), v -> draft.set(param.stat(), v)));
        }

        // Нижний ряд
        set(36, Icons.of(Material.ARROW, "<gray>Назад"), p -> new AbilityMainMenu(module).open(p));
        set(40, Icons.of(Material.LIME_CONCRETE, "<green>Сохранить",
                        draft.id == null ? "<gray>Сохранит способность и выдаст вам руну"
                                : "<gray>Руны у игроков обновятся сами"),
                this::save);
        if (draft.id != null) {
            set(42, Icons.of(Material.CHEST, "<aqua>Получить руну",
                            "<gray>Руну можно положить в награду квеста",
                            "<gray>или в товары торговца"),
                    p -> module.give(p, draft.id));
            set(44, Icons.of(confirmDelete ? Material.TNT : Material.BARRIER,
                            confirmDelete ? "<red><bold>Точно удалить?" : "<red>Удалить",
                            confirmDelete ? "<gray>Нажмите ещё раз для подтверждения"
                                    : "<gray>Выданные руны перестанут работать"),
                    this::delete);
        }
    }

    private void takeFromHand(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.isEmpty()) {
            module.plugin().messages().send(player, "<red>Возьмите предмет в основную руку.</red>");
            return;
        }
        draft.material = hand.getType();
        redraw();
    }

    private void askNumber(Player player, String prompt, double min, double max, DoubleConsumer apply) {
        ask(player, prompt, text -> {
            OptionalDouble value = Numbers.parse(text);
            if (value.isPresent() && value.getAsDouble() >= min && value.getAsDouble() <= max) {
                apply.accept(value.getAsDouble());
            } else {
                module.plugin().messages().send(player, "<red>Нужно число от " + Numbers.format(min)
                        + " до " + Numbers.format(max) + ".</red>");
            }
        });
    }

    private void ask(Player player, String prompt, Consumer<String> apply) {
        module.plugin().chatInput().ask(player, prompt, text -> {
            apply.accept(text);
            open(player);
        }, () -> open(player));
    }

    private void save(Player player) {
        boolean isNew = draft.id == null;
        module.registry().save(draft);
        if (isNew) {
            module.give(player, draft.id);
        }
        module.plugin().messages().send(player, "<green>Способность сохранена: <white>"
                + AbilityRegistry.escape(draft.name) + "</white> <gray>(ID: " + draft.id + ")</gray>");
        redraw();
    }

    private void delete(Player player) {
        if (!confirmDelete) {
            confirmDelete = true;
            redraw();
            return;
        }
        module.registry().delete(draft.id);
        module.plugin().messages().send(player, "<yellow>Способность удалена.</yellow>");
        new AbilityListMenu(module, 0).open(player);
    }

    static String pretty(Material material) {
        return material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }
}
