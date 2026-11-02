package com.dathis.rpg.modules.region;

import com.dathis.rpg.core.gui.Icons;
import com.dathis.rpg.core.gui.Menu;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Карта земли для выбора места участка. Земля делится на клетки размером с участок
 * (от угла земли), одна клетка окна = одно возможное место. Столбцы — X (восток вправо),
 * строки — Z (север сверху), как на обычной карте. Занятые места выбрать нельзя.
 */
final class PlotMapMenu extends Menu {

    private static final int COLS = 9;
    private static final int ROWS = 5;

    private final RegionModule module;
    private final Area land;
    private final PlotSize size;
    private final int side;
    private final int cols;
    private final int rows;
    private final Player viewer;
    private int offCol;
    private int offRow;

    PlotMapMenu(RegionModule module, Area land, PlotSize size, Player viewer) {
        super(6, Icons.text("<dark_gray>" + land.name + ": выбор места"));
        this.module = module;
        this.land = land;
        this.size = size;
        this.side = module.plotSide(size);
        this.cols = (land.maxX - land.minX + 1) / side;
        this.rows = (land.maxZ - land.minZ + 1) / side;
        this.viewer = viewer;
        centerOn(viewer);
    }

    /** Сколько мест такого размера вообще помещается на земле. */
    int cells() {
        return cols * rows;
    }

    /** Показать кусок карты вокруг игрока, если он стоит на этой земле. */
    private void centerOn(Player player) {
        Location l = player.getLocation();
        if (!l.getWorld().getName().equals(land.world)) {
            return;
        }
        int col = Math.floorDiv(l.getBlockX() - land.minX, side);
        int row = Math.floorDiv(l.getBlockZ() - land.minZ, side);
        if (col < 0 || row < 0 || col >= cols || row >= rows) {
            return;
        }
        offCol = clamp(col - COLS / 2, cols - COLS);
        offRow = clamp(row - ROWS / 2, rows - ROWS);
    }

    private static int clamp(int value, int max) {
        return Math.max(0, Math.min(value, Math.max(0, max)));
    }

    @Override
    protected void render() {
        for (int i = 0; i < size(); i++) {
            set(i, Icons.filler());
        }
        Location here = viewer.getLocation();
        int free = 0;
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                int col = offCol + c, row = offRow + r;
                if (col >= cols || row >= rows) {
                    continue;
                }
                cell(r * COLS + c, col, row, here);
            }
        }
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int x = land.minX + col * side, z = land.minZ + row * side;
                if (module.plots().overlapping(land.world, x, z, x + side - 1, z + side - 1) == null) {
                    free++;
                }
            }
        }

        if (offCol > 0) {
            set(45, Icons.of(Material.ARROW, "<yellow>На запад", "<gray>Сдвинуть карту влево"), p -> move(p, -COLS, 0));
        }
        if (offRow > 0) {
            set(46, Icons.of(Material.ARROW, "<yellow>На север", "<gray>Сдвинуть карту вверх"), p -> move(p, 0, -ROWS));
        }
        if (offRow + ROWS < rows) {
            set(47, Icons.of(Material.ARROW, "<yellow>На юг", "<gray>Сдвинуть карту вниз"), p -> move(p, 0, ROWS));
        }
        if (offCol + COLS < cols) {
            set(48, Icons.of(Material.ARROW, "<yellow>На восток", "<gray>Сдвинуть карту вправо"), p -> move(p, COLS, 0));
        }
        set(49, Icons.of(Material.PAPER, "<gold>" + land.name + ": " + size.title + " участок " + side + "x" + side,
                "<gray>Свободных мест: <white>" + free + " <gray>из <white>" + cells(),
                "<gray>Показано столбцов " + (offCol + 1) + "-" + Math.min(cols, offCol + COLS)
                        + " из " + cols + ", строк " + (offRow + 1) + "-" + Math.min(rows, offRow + ROWS) + " из " + rows));
        set(50, Icons.of(Material.COMPASS, "<yellow>К месту, где я стою",
                "<gray>Показать часть карты вокруг вас"), p -> {
            centerOn(p);
            redraw();
        });
        set(53, Icons.of(Material.BOOK, "<yellow>Как выбрать место",
                "<green>Зелёная клетка <gray>- свободное место",
                "<red>Красная клетка <gray>- уже занято",
                "<gray>Блок вместо стекла - вы стоите здесь",
                "<gray>Верх окна - север, право - восток.",
                "<gray>Нажмите на зелёную клетку, чтобы",
                "<gray>посмотреть место и занять его."));
    }

    private void cell(int slot, int col, int row, Location here) {
        int x1 = land.minX + col * side, z1 = land.minZ + row * side;
        int x2 = x1 + side - 1, z2 = z1 + side - 1;
        boolean youAreHere = here != null && here.getWorld().getName().equals(land.world)
                && here.getBlockX() >= x1 && here.getBlockX() <= x2 && here.getBlockZ() >= z1 && here.getBlockZ() <= z2;
        Plot taken = module.plots().overlapping(land.world, x1, z1, x2, z2);
        List<String> lore = new ArrayList<>();
        lore.add("<gray>X <white>" + x1 + ".." + x2 + "<gray>, Z <white>" + z1 + ".." + z2);
        if (youAreHere) {
            lore.add("<aqua>Вы стоите здесь");
        }
        if (taken != null) {
            lore.add("<gray>Занято: участок игрока <white>" + taken.ownerName);
            set(slot, Icons.of(youAreHere ? Material.RED_CONCRETE : Material.RED_STAINED_GLASS_PANE,
                    "<red>Занято", lore));
            return;
        }
        lore.add("");
        lore.add("<green>Нажмите, чтобы выбрать");
        set(slot, Icons.of(youAreHere ? Material.LIME_CONCRETE : Material.LIME_STAINED_GLASS_PANE,
                "<green>Свободно", lore), p -> new PlotClaimMenu(module, this, land, size, x1, z1, side).open(p));
    }

    private void move(Player player, int dc, int dr) {
        offCol = clamp(offCol + dc, cols - COLS);
        offRow = clamp(offRow + dr, rows - ROWS);
        redraw();
    }
}
