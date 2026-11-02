package com.dathis.rpg.modules.region;

import com.dathis.rpg.core.gui.Icons;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * Бумага на участок: обычная бумага, в которой записаны только id земли и размер.
 * Земля и сторона участка берутся «вживую» при использовании.
 */
final class PlotDeeds {

    /** Что записано в бумаге. */
    record Deed(String land, PlotSize size) {
    }

    private final RegionModule module;
    private final NamespacedKey landKey;
    private final NamespacedKey sizeKey;

    PlotDeeds(RegionModule module) {
        this.module = module;
        this.landKey = new NamespacedKey(module.plugin(), "plot_deed_land");
        this.sizeKey = new NamespacedKey(module.plugin(), "plot_deed_size");
    }

    ItemStack create(Area land, PlotSize size) {
        int side = module.plotSide(size);
        ItemStack paper = new ItemStack(Material.PAPER);
        paper.editMeta(meta -> {
            meta.displayName(Icons.text("<gold>Бумага на участок: " + size.title + " " + side + "x" + side));
            meta.lore(List.of(
                    Icons.text("<gray>Земля: <white>" + land.name),
                    Icons.text("<gray>Размер: <white>" + side + "x" + side + "<gray>, вся высота"),
                    Icons.text(""),
                    Icons.text("<yellow>ПКМ с бумагой в руке (на Bedrock -"),
                    Icons.text("<yellow>нажать по блоку) - выбрать место."),
                    Icons.text("<gray>Или команда <white>/plot")));
            meta.setEnchantmentGlintOverride(true);
            meta.addItemFlags(ItemFlag.values());
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(landKey, PersistentDataType.STRING, land.id);
            pdc.set(sizeKey, PersistentDataType.STRING, size.name());
        });
        return paper;
    }

    /** Бумага из предмета или null, если это не бумага на участок. */
    Deed read(ItemStack item) {
        if (item == null || item.getType() != Material.PAPER || !item.hasItemMeta()) {
            return null;
        }
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        String land = pdc.get(landKey, PersistentDataType.STRING);
        PlotSize size = PlotSize.parse(pdc.get(sizeKey, PersistentDataType.STRING));
        return land == null || size == null ? null : new Deed(land, size);
    }

    /** Забрать у игрока одну такую бумагу (сначала из руки). false — бумаги нет. */
    boolean take(Player player, Deed deed) {
        PlayerInventory inv = player.getInventory();
        if (deed.equals(read(inv.getItemInMainHand()))) {
            ItemStack hand = inv.getItemInMainHand();
            hand.setAmount(hand.getAmount() - 1);
            inv.setItemInMainHand(hand.getAmount() > 0 ? hand : null);
            return true;
        }
        ItemStack[] contents = inv.getStorageContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (deed.equals(read(item))) {
                item.setAmount(item.getAmount() - 1);
                inv.setItem(i, item.getAmount() > 0 ? item : null);
                return true;
            }
        }
        return false;
    }
}
