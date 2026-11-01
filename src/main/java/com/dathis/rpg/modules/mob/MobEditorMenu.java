package com.dathis.rpg.modules.mob;

import com.dathis.rpg.core.gui.Icons;
import com.dathis.rpg.core.gui.Menu;
import com.dathis.rpg.core.input.Numbers;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.OptionalDouble;
import java.util.function.Consumer;

/** Редактор моба. Текст и числа вводятся в чат, ранг переключается нажатием. */
final class MobEditorMenu extends Menu {

    private final MobModule module;
    private final CustomMob draft;
    private boolean confirmDelete;

    MobEditorMenu(MobModule module, CustomMob draft) {
        super(5, Icons.text("<dark_gray>Редактор моба"));
        this.module = module;
        this.draft = draft;
    }

    CustomMob draft() {
        return draft;
    }

    @Override
    protected void render() {
        for (int i = 0; i < size(); i++) {
            set(i, Icons.filler());
        }

        set(4, Icons.withHint(module.egg(draft), "",
                "<dark_gray>Тип: " + pretty(draft),
                draft.id == null ? "<dark_gray>Ещё не сохранён" : "<dark_gray>ID: " + draft.id));

        Material egg = MobModule.eggMaterial(draft.type);
        set(19, Icons.of(egg != null ? egg : Material.EGG, "<yellow>Какой это моб",
                        "<gray>Сейчас: <white>" + pretty(draft),
                        "", "<green>Нажмите, чтобы выбрать"),
                p -> new EntityTypeMenu(module, this, 0).open(p));
        set(20, Icons.of(Material.NAME_TAG, "<yellow>Название",
                        "<gray>Сейчас: <white>" + draft.name,
                        "", "<green>Нажмите и напишите в чат"),
                p -> ask(p, "<yellow>Введите название моба:", text -> draft.name = text));
        set(21, Icons.of(tierIcon(draft.tier), "<yellow>Ранг: " + tierLabel(draft.tier),
                        "<white>Обычный</white> <gray>— белое название",
                        "<red>Элитный</red> <gray>— красное название",
                        "<red><bold>Босс</bold></red> <gray>— красное жирное",
                        "", "<green>Нажмите, чтобы переключить"),
                p -> {
                    draft.tier = draft.tier.next();
                    redraw();
                });
        set(22, Icons.of(Material.GOLDEN_APPLE, "<green>ХП",
                        "<gray>Сейчас: <white>" + Numbers.format(draft.health),
                        "<gray>Максимум: " + Numbers.format(MobModule.MAX_HEALTH_LIMIT),
                        "", "<green>Нажмите и напишите число"),
                p -> ask(p, "<yellow>Введите ХП моба (1-" + Numbers.format(MobModule.MAX_HEALTH_LIMIT) + "):", text -> {
                    OptionalDouble value = Numbers.parse(text);
                    if (value.isPresent() && value.getAsDouble() >= 1 && value.getAsDouble() <= MobModule.MAX_HEALTH_LIMIT) {
                        draft.health = value.getAsDouble();
                    } else {
                        module.plugin().messages().send(p, "<red>Нужно число от 1 до " + Numbers.format(MobModule.MAX_HEALTH_LIMIT) + ".</red>");
                    }
                }));
        set(23, Icons.of(Material.IRON_SWORD, "<red>Урон",
                        "<gray>Сейчас: <white>" + Numbers.format(draft.damageMin) + " - " + Numbers.format(draft.damageMax),
                        "", "<green>Нажмите и напишите, например <white>4-8"),
                p -> ask(p, "<yellow>Введите урон моба в виде <white>мин-макс</white>, например <white>4-8</white>:", text -> {
                    double[] range = Numbers.parseRange(text);
                    if (range != null) {
                        draft.damageMin = range[0];
                        draft.damageMax = range[1];
                    } else {
                        module.plugin().messages().send(p, "<red>Не понял число. Пример: 4-8</red>");
                    }
                }));
        set(24, Icons.of(Material.CHEST, "<gold>Лут",
                        "<gray>Предметов: <white>" + draft.drops.size(),
                        "", "<green>Нажмите, чтобы настроить"),
                p -> new DropsMenu(module, this).open(p));
        set(31, Icons.of(Material.IRON_CHESTPLATE, "<aqua>Снаряжение",
                        "<gray>Броня и оружие: <white>" + draft.equipment.size() + " шт.",
                        "<gray>Моб появится только с ним",
                        "", "<green>Нажмите, чтобы настроить"),
                p -> new EquipmentMenu(module, this).open(p));
        if (draft.id != null) {
            set(33, Icons.of(Material.GRASS_BLOCK, "<green>Зоны спавна",
                            "<gray>Зон: <white>" + module.zones().forMob(draft.id).size(),
                            "<gray>Области, где этот моб появляется сам",
                            "<gray>и возвращается после смерти",
                            "", "<green>Нажмите, чтобы настроить"),
                    p -> new ZonesMenu(module, this).open(p));
        } else {
            set(33, Icons.of(Material.GRAY_DYE, "<gray>Зоны спавна",
                    "<gray>Сначала сохраните моба"));
        }
        set(25, Icons.of(draft.vanillaDrops ? Material.ROTTEN_FLESH : Material.BARRIER,
                        "<yellow>Ванильный лут: " + (draft.vanillaDrops ? "<green>включён" : "<red>выключен"),
                        "<gray>Падает ли обычный лут этого моба",
                        "<gray>вместе с вашим", "", "<green>Нажмите, чтобы переключить"),
                p -> {
                    draft.vanillaDrops = !draft.vanillaDrops;
                    redraw();
                });

        set(36, Icons.of(Material.ARROW, "<gray>Назад"), p -> new MobMainMenu(module).open(p));
        set(40, Icons.of(Material.LIME_CONCRETE, "<green>Сохранить",
                "<gray>При первом сохранении выдаст яйцо призыва"), this::save);
        if (draft.id != null) {
            set(38, Icons.of(MobModule.eggMaterial(draft.type) != null ? MobModule.eggMaterial(draft.type) : Material.EGG,
                    "<aqua>Получить яйцо призыва"), p -> module.giveEgg(p, draft.id, 1));
            set(42, Icons.of(Material.BLAZE_POWDER, "<aqua>Призвать рядом со мной"), p -> {
                p.closeInventory();
                module.spawn(module.registry().get(draft.id), p.getLocation());
            });
            set(44, Icons.of(confirmDelete ? Material.TNT : Material.BARRIER,
                            confirmDelete ? "<red><bold>Точно удалить?" : "<red>Удалить",
                            confirmDelete ? "<gray>Нажмите ещё раз для подтверждения" : "<gray>Удалить шаблон моба"),
                    this::delete);
        }
    }

    private void save(Player player) {
        boolean isNew = draft.id == null;
        module.registry().save(draft);
        if (isNew) {
            module.giveEgg(player, draft.id, 1);
        }
        module.plugin().messages().send(player, "<green>Моб сохранён: <white>" + draft.name
                + "</white> <gray>(ID: " + draft.id + ")</gray>");
        redraw();
    }

    private void delete(Player player) {
        if (!confirmDelete) {
            confirmDelete = true;
            redraw();
            return;
        }
        module.zones().deleteForMob(draft.id);
        module.registry().delete(draft.id);
        module.plugin().messages().send(player, "<yellow>Моб удалён. Уже призванные станут обычными.</yellow>");
        new MobListMenu(module, 0).open(player);
    }

    void ask(Player player, String prompt, Consumer<String> apply) {
        module.plugin().chatInput().ask(player, prompt, text -> {
            apply.accept(text);
            open(player);
        }, () -> open(player));
    }

    static String pretty(CustomMob mob) {
        return mob.type.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private static Material tierIcon(CustomMob.Tier tier) {
        return switch (tier) {
            case NORMAL -> Material.IRON_INGOT;
            case ELITE -> Material.GOLD_INGOT;
            case BOSS -> Material.NETHER_STAR;
        };
    }

    private static String tierLabel(CustomMob.Tier tier) {
        return switch (tier) {
            case NORMAL -> "<white>Обычный";
            case ELITE -> "<red>Элитный";
            case BOSS -> "<red><bold>Босс";
        };
    }
}
