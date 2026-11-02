package com.dathis.rpg.modules.item;

import com.dathis.rpg.core.gui.Icons;
import com.dathis.rpg.core.gui.Menu;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Редактор предмета. Сверху превью, ниже кнопки. Текст и числа вводятся через чат,
 * после ввода окно открывается снова.
 */
final class EditorMenu extends Menu {

    private final ItemModule module;
    private final CustomItem draft;
    private boolean confirmDelete;

    EditorMenu(ItemModule module, CustomItem draft) {
        super(5, Icons.text(draft.isWeapon() ? "<dark_gray>Редактор оружия"
                : draft.isArmor() ? "<dark_gray>Редактор брони" : "<dark_gray>Редактор предмета"));
        this.module = module;
        this.draft = draft;
    }

    CustomItem draft() {
        return draft;
    }

    @Override
    protected void render() {
        for (int i = 0; i < size(); i++) {
            set(i, Icons.filler());
        }

        set(4, Icons.withHint(module.factory().build(draft), "",
                "<dark_gray>Так будет выглядеть предмет",
                draft.id == null ? "<dark_gray>Ещё не сохранён" : "<dark_gray>ID: " + draft.id));

        // Основное
        set(19, Icons.of(draft.material, "<yellow>Ванильный предмет",
                        "<gray>Сейчас: <white>" + pretty(draft.material),
                        "", "<green>Нажмите, чтобы выбрать из списка"),
                p -> new MaterialMenu(module, this, draft.isWeapon() || draft.isArmor(), 0, null).open(p));
        set(20, Icons.of(Material.HOPPER, "<yellow>Взять из руки",
                        "<gray>Основой станет предмет,",
                        "<gray>который вы держите в руке"),
                this::takeFromHand);
        set(21, Icons.of(Material.NAME_TAG, "<yellow>Название",
                        "<gray>Сейчас: <white>" + draft.name,
                        "", "<green>Нажмите и напишите в чат"),
                p -> ask(p, "<yellow>Введите название предмета:", text -> draft.name = text));
        set(22, Icons.of(ColorMenu.iconFor(draft.nameColor), "<yellow>Цвет названия",
                        "<gray>Сейчас: <white>" + ColorMenu.label(draft.nameColor),
                        "", "<green>Нажмите, чтобы выбрать"),
                p -> new ColorMenu(module, this).open(p));
        List<String> loreHint = new ArrayList<>();
        loreHint.add("<gray>Строк: <white>" + draft.lore.size());
        loreHint.add("");
        loreHint.add("<green>Нажмите и напишите в чат");
        loreHint.add("<gray>Новая строка: символ <white>|");
        set(23, Icons.of(Material.WRITABLE_BOOK, "<yellow>Описание", loreHint),
                p -> ask(p, "<yellow>Введите описание. Разделяйте строки символом <white>|</white>:",
                        text -> draft.lore = new ArrayList<>(Arrays.stream(text.split("\\|"))
                                .map(String::trim).filter(s -> !s.isEmpty()).toList())));
        set(24, Icons.of(Material.BUCKET, "<yellow>Очистить описание"), p -> {
            draft.lore = new ArrayList<>();
            redraw();
        });

        set(25, Icons.of(Material.EXPERIENCE_BOTTLE, "<gold>Уровень предмета",
                        "<gray>Сейчас: <white>" + (draft.level > 1 ? draft.level : "любой"),
                        "<gray>С какого уровня персонажа оружие",
                        "<gray>бьёт в полную силу, а броня даёт статы.",
                        "<gray>Ниже уровнем: оружие бьёт как рука,",
                        "<gray>броня ничего не даёт.",
                        "", "<green>Нажмите и напишите число"),
                p -> ask(p, "<yellow>С какого уровня предмет? (1-" + module.plugin().levels().maxLevel() + ", 1 — с любого)",
                        text -> draft.level = (int) range(p, text, 1, module.plugin().levels().maxLevel(), draft.level)));

        // Свойства оружия
        if (draft.isWeapon()) {
            set(29, Icons.of(Material.IRON_SWORD, "<red>Урон",
                            "<gray>Сейчас: <white>" + ItemFactory.num(draft.damageMin) + " - " + ItemFactory.num(draft.damageMax),
                            "", "<green>Нажмите и напишите, например <white>10-20"),
                    p -> ask(p, "<yellow>Введите урон в виде <white>мин-макс</white>, например <white>10-20</white>:",
                            this::applyDamage));
            set(31, Icons.of(Material.GOLD_NUGGET, "<yellow>Крит. удар",
                            "<gray>Шанс: <white>" + ItemFactory.num(draft.critChance) + "%",
                            "<gray>Урон крита: <white>x" + ItemFactory.num(module.critMultiplier()),
                            "", "<green>Нажмите и напишите процент, например <white>15"),
                    p -> ask(p, "<yellow>Введите шанс крита в процентах (0-100):",
                            text -> draft.critChance = percent(p, text, draft.critChance)));
            set(33, Icons.of(Material.REDSTONE, "<dark_red>Вампиризм",
                            "<gray>Сейчас: <white>" + ItemFactory.num(draft.vampirism) + "%",
                            "<gray>Лечит на этот % от нанесённого урона",
                            "", "<green>Нажмите и напишите процент, например <white>20"),
                    p -> ask(p, "<yellow>Введите вампиризм в процентах (0-100):",
                            text -> draft.vampirism = percent(p, text, draft.vampirism)));
        }

        // Свойства брони
        if (draft.isArmor()) {
            set(28, Icons.of(Material.SHIELD, "<aqua>Броня",
                            "<gray>Сейчас: <white>+" + ItemFactory.num(draft.armor),
                            "<gray>Броня всех частей складывается, предела нет.",
                            "<gray>Снижает урон: " + reductionHint(100) + " при 100,",
                            "<gray>" + reductionHint(1000) + " при 1000 брони.",
                            "", "<green>Нажмите и напишите число"),
                    p -> ask(p, "<yellow>Сколько очков брони?",
                            text -> draft.armor = range(p, text, 0, 1_000_000, draft.armor)));
            set(29, Icons.of(Material.GOLDEN_APPLE, "<red>Здоровье",
                            "<gray>Сейчас: <white>+" + ItemFactory.num(draft.bonusHealth) + " ХП",
                            "<gray>Добавляется к " + ItemFactory.num(module.plugin().health().playerBase()) + " ХП игрока, пока броня",
                            "<gray>надета. Предела нет: сердца показывают долю.",
                            "", "<green>Нажмите и напишите число"),
                    p -> ask(p, "<yellow>Сколько ХП добавить?",
                            text -> draft.bonusHealth = range(p, text, 0, 1_000_000, draft.bonusHealth)));
            set(30, Icons.of(Material.GLISTERING_MELON_SLICE, "<green>Регенерация",
                            "<gray>Сейчас: <white>+" + ItemFactory.num(draft.regeneration) + " ХП/сек",
                            "<gray>Лечит каждую секунду, пока броня надета.",
                            "<gray>Не работает 5 секунд после получения урона.",
                            "", "<green>Нажмите и напишите число, например <white>0.5"),
                    p -> ask(p, "<yellow>Сколько ХП в секунду восстанавливать?",
                            text -> draft.regeneration = range(p, text, 0, 1_000_000, draft.regeneration)));
            set(32, Icons.of(Material.LAPIS_LAZULI, "<blue>Мана",
                            "<gray>Сейчас: <white>+" + ItemFactory.num(draft.bonusMana),
                            "<gray>Добавляется к максимуму маны, пока броня надета.",
                            "<gray>Мана нужна для способностей.",
                            "", "<green>Нажмите и напишите число"),
                    p -> ask(p, "<yellow>Сколько маны добавить?",
                            text -> draft.bonusMana = range(p, text, 0, 1_000_000, draft.bonusMana)));
            set(33, Icons.of(Material.HEART_OF_THE_SEA, "<blue>Восстановление маны",
                            "<gray>Сейчас: <white>+" + ItemFactory.num(draft.manaRegen) + " в секунду",
                            "<gray>Добавляется к обычному восстановлению маны.",
                            "", "<green>Нажмите и напишите число, например <white>1.5"),
                    p -> ask(p, "<yellow>Сколько маны в секунду добавить?",
                            text -> draft.manaRegen = range(p, text, 0, 1_000_000, draft.manaRegen)));
        }

        // Нижний ряд
        set(36, Icons.of(Material.ARROW, "<gray>Назад"), p -> new MainMenu(module).open(p));
        set(40, Icons.of(Material.LIME_CONCRETE, "<green>Сохранить",
                        "<gray>Сохранит предмет и выдаст вам копию"),
                this::save);
        if (draft.id != null) {
            set(42, Icons.of(Material.CHEST, "<aqua>Получить копию"), p -> module.give(p, draft.id));
            set(44, Icons.of(confirmDelete ? Material.TNT : Material.BARRIER,
                            confirmDelete ? "<red><bold>Точно удалить?" : "<red>Удалить",
                            confirmDelete ? "<gray>Нажмите ещё раз для подтверждения" : "<gray>Удалить шаблон"),
                    this::delete);
        }
    }

    /** «50%» — сколько урона срежет такая броня по формуле из config.yml. */
    private String reductionHint(double armor) {
        return Math.round(module.plugin().health().armorReduction(armor) * 100) + "%";
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

    private void applyDamage(String text) {
        String[] parts = text.replace(" ", "").split("[-–]");
        try {
            double a = Double.parseDouble(parts[0].replace(',', '.'));
            double b = parts.length > 1 ? Double.parseDouble(parts[1].replace(',', '.')) : a;
            if (a < 0 || b < 0) {
                return;
            }
            draft.damageMin = Math.min(a, b);
            draft.damageMax = Math.max(a, b);
        } catch (NumberFormatException ignored) {
            // неверный ввод: оставляем как было, окно откроется снова
        }
    }

    private double percent(Player player, String text, double fallback) {
        try {
            double value = Double.parseDouble(text.replace("%", "").replace(',', '.').trim());
            if (value >= 0 && value <= 100) {
                return value;
            }
        } catch (NumberFormatException ignored) {
            // ниже
        }
        module.plugin().messages().send(player, "<red>Нужно число от 0 до 100.</red>");
        return fallback;
    }

    private double range(Player player, String text, double min, double max, double fallback) {
        try {
            double value = Double.parseDouble(text.replace("%", "").replace(',', '.').trim());
            if (value >= min && value <= max) {
                return value;
            }
        } catch (NumberFormatException ignored) {
            // ниже
        }
        module.plugin().messages().send(player, "<red>Нужно число от " + ItemFactory.num(min) + " до " + ItemFactory.num(max) + ".</red>");
        return fallback;
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
        module.plugin().messages().send(player, "<green>Предмет сохранён: <white>" + draft.name
                + "</white> <gray>(ID: " + draft.id + ")</gray>");
        redraw();
    }

    private void delete(Player player) {
        if (!confirmDelete) {
            confirmDelete = true;
            redraw();
            return;
        }
        module.registry().delete(draft.id);
        module.plugin().messages().send(player, "<yellow>Предмет удалён.</yellow>");
        new ListMenu(module, 0).open(player);
    }

    static String pretty(Material material) {
        return material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }
}
