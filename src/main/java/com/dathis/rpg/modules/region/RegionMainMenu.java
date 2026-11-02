package com.dathis.rpg.modules.region;

import com.dathis.rpg.core.gui.Icons;
import com.dathis.rpg.core.gui.Menu;
import org.bukkit.Material;

/** Первое окно /create-region. */
final class RegionMainMenu extends Menu {

    private final RegionModule module;

    RegionMainMenu(RegionModule module) {
        super(3, Icons.text("<dark_gray>Защита мира и зоны"));
        this.module = module;
    }

    @Override
    protected void render() {
        for (int i = 0; i < size(); i++) {
            set(i, Icons.filler());
        }
        set(4, Icons.of(module.protectWorld() ? Material.SHIELD : Material.GRASS_BLOCK,
                module.protectWorld() ? "<green>Мир защищён" : "<yellow>Защита мира выключена",
                module.protectWorld() ? "<gray>Игроки (не OP) не ломают, не ставят"
                        : "<gray>Включается в config.yml:",
                module.protectWorld() ? "<gray>и не открывают блоки вне зон" : "<white>regions.protect-world: true",
                "<gray>OP в творческом режиме строит как обычно"));
        set(10, Icons.of(Material.BRICKS, "<green>Зоны строительства",
                        "<gray>Всего: <white>" + module.areas().all(Area.Kind.BUILD).size(),
                        "<gray>Внутри игроки строят, ломают",
                        "<gray>и открывают всё, что хотят"),
                p -> new AreaListMenu(module, Area.Kind.BUILD).open(p));
        set(12, Icons.of(Material.IRON_PICKAXE, "<gold>Зоны добычи",
                        "<gray>Всего: <white>" + module.areas().all(Area.Kind.MINING).size(),
                        "<gray>Какие блоки можно ломать, что",
                        "<gray>ставится на их место и вернутся",
                        "<gray>ли они через время"),
                p -> new AreaListMenu(module, Area.Kind.MINING).open(p));
        set(14, Icons.of(Material.GRASS_BLOCK, "<green>Земли под участки",
                        "<gray>Всего: <white>" + module.areas().all(Area.Kind.LAND).size(),
                        "<gray>Игроки покупают бумагу на участок",
                        "<gray>и занимают на земле квадрат",
                        "<gray>15x15, 30x30 или 60x60"),
                p -> new AreaListMenu(module, Area.Kind.LAND).open(p));
        set(16, Icons.of(Material.BLAZE_ROD, "<yellow>Получить палочку выделения",
                        "<gray>Удар по блоку — первый угол",
                        "<gray>ПКМ по блоку — второй угол"),
                p -> {
                    p.getInventory().addItem(module.plugin().selectionWand().create());
                    module.plugin().messages().send(p, "<green>Палочка выдана. Выделите область и откройте /create-region.");
                    p.closeInventory();
                });
    }
}
