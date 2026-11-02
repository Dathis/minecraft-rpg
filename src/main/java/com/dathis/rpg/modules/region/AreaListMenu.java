package com.dathis.rpg.modules.region;

import com.dathis.rpg.core.gui.Icons;
import com.dathis.rpg.core.gui.Menu;
import com.dathis.rpg.core.selection.SelectionWand;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** Список зон одного вида: строительства, добычи или земель под участки. */
final class AreaListMenu extends Menu {

    private static final int MAX_SHOWN = 45;

    private final RegionModule module;
    private final Area.Kind kind;

    AreaListMenu(RegionModule module, Area.Kind kind) {
        super(6, Icons.text("<dark_gray>" + Area.title(kind)));
        this.module = module;
        this.kind = kind;
    }

    @Override
    protected void render() {
        List<Area> list = module.areas().all(kind);
        for (int i = 0; i < MAX_SHOWN && i < list.size(); i++) {
            Area area = list.get(i);
            List<String> lore = new ArrayList<>();
            lore.add("<gray>id: <white>" + area.id);
            lore.add("<gray>Где: <white>" + area.coordsText());
            lore.add("<gray>Размер: <white>" + area.sizeText());
            if (kind == Area.Kind.MINING) {
                lore.add("<gray>Правил: <white>" + area.rules.size());
            }
            if (kind == Area.Kind.LAND) {
                lore.add("<gray>Участков: <white>" + module.plots().inLand(area.id).size());
            }
            lore.add("");
            lore.add("<green>Нажмите, чтобы настроить");
            set(i, Icons.of(icon(area), "<green>" + area.name, lore), p -> new AreaMenu(module, area).open(p));
        }
        for (int i = MAX_SHOWN; i < size(); i++) {
            set(i, Icons.filler());
        }
        set(45, Icons.of(Material.ARROW, "<gray>Назад"), p -> new RegionMainMenu(module).open(p));
        set(47, Icons.of(Material.BLAZE_ROD, "<gold>Получить палочку выделения",
                        "<gray>Удар по блоку — первый угол",
                        "<gray>ПКМ по блоку — второй угол"),
                p -> {
                    p.getInventory().addItem(module.plugin().selectionWand().create());
                    module.plugin().messages().send(p, "<green>Палочка выдана. Выделите область и вернитесь сюда.");
                    p.closeInventory();
                });
        set(49, Icons.of(Material.LIME_CONCRETE, "<green>Создать зону из выделения",
                "<gray>Займёт область, выделенную палочкой"), this::create);
        if (kind == Area.Kind.MINING) {
            set(50, Icons.of(Material.FILLED_MAP, "<green>Правила на весь мир",
                            "<gray>Зона добычи без границ: правила",
                            "<gray>действуют во всём текущем мире.",
                            "<gray>Например, рубить деревья везде"),
                    p -> {
                        Area area = module.areas().createWholeWorld("Весь мир " + p.getWorld().getName(), p.getWorld());
                        module.plugin().messages().send(p, "<green>Зона " + area.id + " на весь мир создана. Добавьте блоки.");
                        new AreaMenu(module, area).open(p);
                    });
        }
        set(53, Icons.of(Material.BOOK, "<yellow>Как это работает", help()));
    }

    private List<String> help() {
        if (kind == Area.Kind.LAND) {
            return List.of("<gray>Большая территория, на которой",
                    "<gray>игроки занимают участки по бумагам",
                    "<gray>(малый, средний, большой).",
                    "<gray>Вне участков земля защищена,",
                    "<gray>как и весь мир. Высота - вся.",
                    "<gray>Совет: стороны кратные 60.");
        }
        if (kind == Area.Kind.BUILD) {
            return List.of("<gray>Весь мир защищён от игроков.",
                    "<gray>В зоне строительства игроки",
                    "<gray>строят, ломают и открывают",
                    "<gray>сундуки, двери и всё остальное.");
        }
        return List.of("<gray>В зоне добычи игроки ломают",
                "<gray>только блоки из правил. Добыча",
                "<gray>падает сразу в инвентарь, на месте",
                "<gray>ставится замена (или пусто), а через",
                "<gray>время блок может вернуться.");
    }

    static Material icon(Area area) {
        if (area.kind == Area.Kind.BUILD) {
            return Material.BRICKS;
        }
        if (area.kind == Area.Kind.LAND) {
            return Material.GRASS_BLOCK;
        }
        if (area.wholeWorld) {
            return Material.FILLED_MAP;
        }
        return area.rules.isEmpty() ? Material.STONE_PICKAXE : RegionIcons.item(area.rules.keySet().iterator().next());
    }

    private void create(Player player) {
        SelectionWand.Selection selection = module.plugin().selectionWand().selection(player);
        if (selection == null || !selection.complete()) {
            module.plugin().messages().send(player,
                    "<red>Сначала выделите область палочкой: удар по блоку и ПКМ по другому блоку.</red>");
            return;
        }
        int n = module.areas().all(kind).size() + 1;
        String name = (switch (kind) {
            case BUILD -> "Зона строительства ";
            case MINING -> "Зона добычи ";
            case LAND -> "Земля ";
        }) + n;
        Area area = module.areas().create(kind, name, selection.first, selection.second);
        module.plugin().messages().send(player, "<green>Зона " + area.id + " создана.");
        new AreaMenu(module, area).open(player);
    }
}
