package com.dathis.rpg.modules.region;

import com.dathis.rpg.core.gui.Icons;
import com.dathis.rpg.core.gui.Menu;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Список участков. Два вида:
 * - land == null: «мои участки» игрока (/plot) — свои и те, где он помощник;
 * - land != null: все участки на земле (окно админа из /create-region).
 */
final class PlotListMenu extends Menu {

    private static final int MAX_SHOWN = 45;

    private final RegionModule module;
    private final Area land;
    private final Player viewer;

    PlotListMenu(RegionModule module, Area land, Player viewer) {
        super(6, Icons.text(land == null ? "<dark_gray>Мои участки" : "<dark_gray>Участки: " + land.name));
        this.module = module;
        this.land = land;
        this.viewer = viewer;
    }

    @Override
    protected void render() {
        List<Plot> list = land == null ? module.plots().of(viewer.getUniqueId()) : module.plots().inLand(land.id);
        for (int i = 0; i < MAX_SHOWN && i < list.size(); i++) {
            Plot plot = list.get(i);
            Area plotLand = module.areas().get(plot.land);
            boolean own = viewer.getUniqueId().equals(plot.owner);
            List<String> lore = new ArrayList<>();
            lore.add("<gray>Земля: <white>" + (plotLand == null ? plot.land : plotLand.name));
            lore.add("<gray>Размер: <white>" + plot.size.title + " " + plot.sizeText());
            lore.add("<gray>Где: <white>" + plot.coordsText());
            if (!own) {
                lore.add(land == null ? "<aqua>Вы помощник на этом участке" : "<gray>Владелец: <white>" + plot.ownerName);
            }
            lore.add("");
            lore.add("<green>Нажмите, чтобы открыть");
            set(i, Icons.of(own ? Material.OAK_DOOR : Material.SPRUCE_DOOR,
                    (own ? "<green>" : "<yellow>") + "Участок " + plot.ownerName, lore),
                    p -> new PlotMenu(module, plot, p, back -> new PlotListMenu(module, land, back).open(back)).open(p));
        }
        for (int i = MAX_SHOWN; i < size(); i++) {
            set(i, Icons.filler());
        }
        if (land != null) {
            set(45, Icons.of(Material.ARROW, "<gray>К земле"), p -> new AreaMenu(module, land).open(p));
            set(53, Icons.of(Material.BOOK, "<yellow>Участки на этой земле",
                    "<gray>Всего: <white>" + list.size(),
                    "<gray>Откройте участок, чтобы посмотреть",
                    "<gray>его, телепортироваться или удалить"));
            return;
        }
        if (list.isEmpty()) {
            set(22, Icons.of(Material.MAP, "<yellow>У вас пока нет участков",
                    "<gray>Купите бумагу на участок у торговца",
                    "<gray>и используйте её на нужной земле"));
        }
        set(49, Icons.of(Material.PAPER, "<green>Выбрать место по бумаге в руке",
                "<gray>Возьмите бумагу на участок в руку",
                "<gray>и нажмите здесь"), p -> module.useDeed(p, p.getInventory().getItemInMainHand()));
        set(53, Icons.of(Material.BOOK, "<yellow>Как получить участок",
                "<gray>1. Купите бумагу на участок",
                "<gray>   (малый, средний или большой).",
                "<gray>2. Возьмите её в руку и нажмите ПКМ",
                "<gray>   (на Bedrock - нажмите по блоку).",
                "<gray>3. Выберите свободное место на карте",
                "<gray>   и займите его."));
    }
}
