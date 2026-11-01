package com.dathis.rpg.modules.mob;

import org.bukkit.inventory.ItemStack;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Предмет, который может выпасть с моба, и шанс в процентах.
 * Хранится весь ItemStack целиком, поэтому подходят и кастомные предметы из /create-item.
 */
final class MobDrop {

    ItemStack item;
    double chance; // 0..100

    MobDrop(ItemStack item, double chance) {
        this.item = item;
        this.chance = chance;
    }

    MobDrop copy() {
        return new MobDrop(item.clone(), chance);
    }

    static String encode(ItemStack item) {
        return Base64.getEncoder().encodeToString(item.serializeAsBytes());
    }

    static ItemStack decode(Object data) {
        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(String.valueOf(data)));
        } catch (Exception e) {
            return null;
        }
    }

    Map<String, Object> serialize() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("item", encode(item));
        map.put("chance", chance);
        return map;
    }

    static MobDrop deserialize(Map<?, ?> map) {
        ItemStack item = decode(map.get("item"));
        if (item == null) {
            return null;
        }
        Object chance = map.get("chance");
        return new MobDrop(item, chance instanceof Number n ? n.doubleValue() : 100);
    }
}
