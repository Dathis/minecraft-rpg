package com.dathis.rpg.modules.region;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;

/** Мелочи для окон зон: иконки блоков, названия, выбор блока. */
final class RegionIcons {

    private RegionIcons() {
    }

    /** Не у каждого блока есть предмет (посевы моркови, провод...), для них показываем бумагу. */
    static Material item(Material block) {
        return block.isItem() ? block : Material.PAPER;
    }

    /** STONE_BRICKS → «stone bricks». */
    static String pretty(Material m) {
        return m.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    /** Блок по предмету с курсора (null — это не блок). */
    static Material fromItem(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return null;
        }
        return asBlock(item.getType());
    }

    /** Блок, на который смотрит игрок (до 8 блоков), или null. */
    static Material lookingAt(Player player) {
        Block block = player.getTargetBlockExact(8);
        return block == null ? null : asBlock(block.getType());
    }

    /** Блок по названию из чата: «stone», «oak log», «minecraft:oak_log». */
    static Material fromName(String text) {
        Material m = Material.matchMaterial(text.trim().replace(' ', '_'));
        return m == null ? null : asBlock(m);
    }

    private static Material asBlock(Material m) {
        return m.isBlock() && !m.isAir() && !m.name().startsWith("LEGACY_") ? m : null;
    }
}
