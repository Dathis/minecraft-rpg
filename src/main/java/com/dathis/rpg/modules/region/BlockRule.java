package com.dathis.rpg.modules.region;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Правило зоны добычи: блок {@link #block} можно сломать. Добыча падает игроку в инвентарь,
 * на месте ставится {@link #replacement} (null — пусто), а через {@link #regenSeconds} секунд
 * блок возвращается (0 — не возвращается).
 */
final class BlockRule {

    final Material block;
    Material replacement;
    int regenSeconds;

    BlockRule(Material block) {
        this.block = block;
    }

    void save(ConfigurationSection s) {
        s.set("replace", replacement == null ? "" : replacement.name());
        s.set("regen-seconds", regenSeconds);
    }

    static BlockRule load(String key, ConfigurationSection s) {
        Material block = Material.matchMaterial(key);
        if (block == null || !block.isBlock() || s == null) {
            return null;
        }
        BlockRule rule = new BlockRule(block);
        String replace = s.getString("replace", "");
        Material replacement = replace.isEmpty() ? null : Material.matchMaterial(replace);
        rule.replacement = replacement != null && replacement.isBlock() && !replacement.isAir() ? replacement : null;
        rule.regenSeconds = Math.max(0, s.getInt("regen-seconds", 0));
        return rule;
    }
}
