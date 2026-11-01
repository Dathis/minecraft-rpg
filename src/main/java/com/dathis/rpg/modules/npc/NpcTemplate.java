package com.dathis.rpg.modules.npc;

import com.dathis.rpg.core.gui.Icons;
import com.dathis.rpg.core.storage.ItemCodec;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Шаблон NPC. Он же черновик в редакторе. В мире стоят «точки» ({@link NpcPlacement}),
 * у сущности на точке лежит только id шаблона, поэтому правка шаблона меняет всех его NPC.
 */
public final class NpcTemplate {

    String id;                       // null, пока не сохранён
    EntityType type = EntityType.VILLAGER;
    String name = "Житель";          // MiniMessage, можно с цветом: <gold>Староста
    String greeting = "";            // фраза в чат при нажатии, пусто — молчит
    String profession = "minecraft:none"; // для жителя и зомби-жителя
    String villagerType = "minecraft:plains";
    /** Броня, оружие и голова со скином. */
    Map<EquipmentSlot, ItemStack> equipment = new EnumMap<>(EquipmentSlot.class);
    List<NpcTrade> trades = new ArrayList<>();

    NpcTemplate() {
    }

    public String id() {
        return id;
    }

    /** Имя в формате MiniMessage (для вставки в другие тексты). */
    public String name() {
        return name;
    }

    public Component displayName() {
        return Icons.text(name);
    }

    public boolean hasTrades() {
        return !trades.isEmpty();
    }

    boolean hasSkin() {
        ItemStack head = equipment.get(EquipmentSlot.HEAD);
        return head != null && head.getType() == Material.PLAYER_HEAD;
    }

    NpcTemplate copy() {
        NpcTemplate c = new NpcTemplate();
        c.id = id;
        c.type = type;
        c.name = name;
        c.greeting = greeting;
        c.profession = profession;
        c.villagerType = villagerType;
        equipment.forEach((slot, item) -> c.equipment.put(slot, item.clone()));
        for (NpcTrade trade : trades) {
            c.trades.add(trade.copy());
        }
        return c;
    }

    void save(ConfigurationSection s) {
        s.set("type", type.name());
        s.set("name", name);
        s.set("greeting", greeting);
        s.set("profession", profession);
        s.set("villager-type", villagerType);
        s.set("equipment", null);
        equipment.forEach((slot, item) -> s.set("equipment." + slot.name(), ItemCodec.encode(item)));
        List<Map<String, Object>> list = new ArrayList<>();
        for (NpcTrade trade : trades) {
            list.add(trade.serialize());
        }
        s.set("trades", list);
    }

    static NpcTemplate load(String id, ConfigurationSection s) {
        NpcTemplate npc = new NpcTemplate();
        npc.id = id;
        try {
            npc.type = EntityType.valueOf(s.getString("type", "VILLAGER"));
        } catch (IllegalArgumentException e) {
            npc.type = EntityType.VILLAGER;
        }
        npc.name = s.getString("name", id);
        npc.greeting = s.getString("greeting", "");
        npc.profession = s.getString("profession", "minecraft:none");
        npc.villagerType = s.getString("villager-type", "minecraft:plains");
        ConfigurationSection eq = s.getConfigurationSection("equipment");
        if (eq != null) {
            for (String key : eq.getKeys(false)) {
                try {
                    ItemStack item = ItemCodec.decode(eq.getString(key));
                    if (item != null) {
                        npc.equipment.put(EquipmentSlot.valueOf(key), item);
                    }
                } catch (IllegalArgumentException ignored) {
                    // неизвестный слот — пропускаем
                }
            }
        }
        for (Map<?, ?> raw : s.getMapList("trades")) {
            NpcTrade trade = NpcTrade.deserialize(raw);
            if (trade != null) {
                npc.trades.add(trade);
            }
        }
        return npc;
    }
}
