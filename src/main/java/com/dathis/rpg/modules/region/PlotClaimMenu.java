package com.dathis.rpg.modules.region;

import com.dathis.rpg.core.gui.Icons;
import com.dathis.rpg.core.gui.Menu;
import com.dathis.rpg.core.selection.SelectionWand;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

/** Подтверждение выбранного места: посмотреть границы и занять участок (бумага тратится). */
final class PlotClaimMenu extends Menu {

    private final RegionModule module;
    private final PlotMapMenu back;
    private final Area land;
    private final PlotSize size;
    private final int minX, minZ, side;

    PlotClaimMenu(RegionModule module, PlotMapMenu back, Area land, PlotSize size, int minX, int minZ, int side) {
        super(3, Icons.text("<dark_gray>Занять это место?"));
        this.module = module;
        this.back = back;
        this.land = land;
        this.size = size;
        this.minX = minX;
        this.minZ = minZ;
        this.side = side;
    }

    private int maxX() {
        return minX + side - 1;
    }

    private int maxZ() {
        return minZ + side - 1;
    }

    @Override
    protected void render() {
        for (int i = 0; i < size(); i++) {
            set(i, Icons.filler());
        }
        set(4, Icons.of(Material.PAPER, "<gold>" + size.title + " участок " + side + "x" + side,
                "<gray>Земля: <white>" + land.name,
                "<gray>X <white>" + minX + ".." + maxX() + "<gray>, Z <white>" + minZ + ".." + maxZ(),
                "<gray>Высота: <white>вся"));
        set(11, Icons.of(Material.SPYGLASS, "<yellow>Показать границы",
                "<gray>Подсветит место на 10 секунд.",
                "<gray>Чтобы вернуться к выбору, снова",
                "<gray>используйте бумагу"), p -> {
            p.closeInventory();
            showBorders(module, p, land.world, minX, minZ, maxX(), maxZ());
            module.plugin().messages().send(p, "<yellow>Границы подсвечены огоньками.");
        });
        set(13, Icons.of(Material.LIME_CONCRETE, "<green><bold>Занять участок",
                "<gray>Бумага на участок потратится,",
                "<gray>и здесь сможете строить вы",
                "<gray>и те, кого вы добавите"), this::claim);
        set(15, Icons.of(Material.ARROW, "<gray>Назад к карте"), back::open);
    }

    private void claim(Player player) {
        var messages = module.plugin().messages();
        if (module.areas().get(land.id) != land) {
            messages.send(player, "<red>Этой земли больше нет.</red>");
            player.closeInventory();
            return;
        }
        Plot taken = module.plots().overlapping(land.world, minX, minZ, maxX(), maxZ());
        if (taken != null) {
            messages.send(player, "<red>Это место только что занял " + taken.ownerName + ". Выберите другое.</red>");
            back.open(player);
            return;
        }
        int max = module.maxPlotsPerPlayer();
        if (max > 0 && module.plots().ownedCount(player.getUniqueId()) >= max
                && !player.hasPermission("rpg.admin")) {
            messages.send(player, "<red>У вас уже максимум участков: " + max + ".</red>");
            player.closeInventory();
            return;
        }
        if (!module.deeds().take(player, new PlotDeeds.Deed(land.id, size))) {
            messages.send(player, "<red>У вас нет бумаги на этот участок.</red>");
            player.closeInventory();
            return;
        }
        Plot plot = module.plots().create(land, size, minX, minZ, side, player);
        player.closeInventory();
        messages.send(player, "<green>Участок ваш! " + plot.coordsText()
                + ". Управление участком: <white>/plot");
        player.showTitle(net.kyori.adventure.title.Title.title(Icons.text("<green>Участок ваш!"),
                Icons.text("<gray>" + land.name + ", " + plot.sizeText())));
        showBorders(module, player, plot.world, plot.minX, plot.minZ, plot.maxX, plot.maxZ);
    }

    /** Подсветка квадрата участка на 10 секунд: полоса высотой в несколько блоков вокруг игрока. */
    static void showBorders(RegionModule module, Player player, String worldName, int x1, int z1, int x2, int z2) {
        World world = module.plugin().getServer().getWorld(worldName);
        if (world == null) {
            return;
        }
        int y = player.getLocation().getBlockY();
        SelectionWand.showFor(module.plugin(), player,
                new Location(world, x1, y - 2, z1), new Location(world, x2, y + 3, z2));
    }
}
