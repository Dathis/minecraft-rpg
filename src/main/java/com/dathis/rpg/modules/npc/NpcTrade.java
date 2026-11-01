package com.dathis.rpg.modules.npc;

import com.dathis.rpg.core.storage.ItemCodec;
import org.bukkit.inventory.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;

/** Сделка торговца: один-два предмета-цены → предмет. Количество в стаке = сколько нужно / сколько получишь. */
final class NpcTrade {

    ItemStack cost1;
    ItemStack cost2; // может быть null
    ItemStack result;

    NpcTrade(ItemStack cost1, ItemStack cost2, ItemStack result) {
        this.cost1 = cost1;
        this.cost2 = cost2;
        this.result = result;
    }

    boolean complete() {
        return cost1 != null && result != null;
    }

    NpcTrade copy() {
        return new NpcTrade(clone(cost1), clone(cost2), clone(result));
    }

    private static ItemStack clone(ItemStack item) {
        return item == null ? null : item.clone();
    }

    Map<String, Object> serialize() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("cost1", ItemCodec.encode(cost1));
        if (cost2 != null) {
            map.put("cost2", ItemCodec.encode(cost2));
        }
        map.put("result", ItemCodec.encode(result));
        return map;
    }

    static NpcTrade deserialize(Map<?, ?> map) {
        ItemStack cost1 = ItemCodec.decode(map.get("cost1"));
        ItemStack result = ItemCodec.decode(map.get("result"));
        if (cost1 == null || result == null) {
            return null;
        }
        return new NpcTrade(cost1, ItemCodec.decode(map.get("cost2")), result);
    }
}
