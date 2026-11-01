package com.dathis.rpg.modules.item;

import com.dathis.rpg.RpgPlugin;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Все сохранённые предметы. Хранятся в plugins/MinecraftRPG/data/items.yml.
 * На самом ItemStack лежит только id шаблона, поэтому если админ поменяет урон или перки,
 * уже выданное оружие сразу будет бить по-новому.
 */
public final class ItemRegistry {

    private static final String FILE = "items";

    private final RpgPlugin plugin;
    private final NamespacedKey idKey;
    private final Map<String, CustomItem> items = new LinkedHashMap<>();

    ItemRegistry(RpgPlugin plugin) {
        this.plugin = plugin;
        this.idKey = new NamespacedKey(plugin, "item_id");
    }

    NamespacedKey idKey() {
        return idKey;
    }

    void load() {
        items.clear();
        ConfigurationSection root = plugin.store().get(FILE).getConfigurationSection("items");
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section != null) {
                items.put(id, CustomItem.load(id, section));
            }
        }
    }

    void save(CustomItem item) {
        if (item.id == null) {
            item.id = nextId();
        }
        items.put(item.id, item.copy());
        writeFile();
    }

    void delete(String id) {
        items.remove(id);
        writeFile();
    }

    CustomItem get(String id) {
        return items.get(id);
    }

    List<CustomItem> all() {
        return new ArrayList<>(items.values());
    }

    /** Какой шаблон у предмета в руке, или null, если это обычный предмет. */
    CustomItem of(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasItemMeta()) {
            return null;
        }
        String id = stack.getItemMeta().getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
        return id == null ? null : items.get(id);
    }

    private String nextId() {
        int n = 1;
        while (items.containsKey("item" + n)) {
            n++;
        }
        return "item" + n;
    }

    private void writeFile() {
        YamlConfiguration yaml = plugin.store().get(FILE);
        yaml.set("items", null);
        ConfigurationSection root = yaml.createSection("items");
        for (CustomItem item : items.values()) {
            item.save(root.createSection(item.id));
        }
        plugin.store().save(FILE);
    }
}
