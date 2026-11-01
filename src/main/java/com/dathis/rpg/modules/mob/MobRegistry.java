package com.dathis.rpg.modules.mob;

import com.dathis.rpg.RpgPlugin;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.persistence.PersistentDataHolder;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Все сохранённые мобы (plugins/MinecraftRPG/data/mobs.yml).
 * На заспавненном мобе и на яйце призыва лежит только ID шаблона,
 * поэтому изменения урона и лута сразу действуют на уже живых мобов.
 */
final class MobRegistry {

    private static final String FILE = "mobs";

    private final RpgPlugin plugin;
    private final NamespacedKey idKey;
    private final Map<String, CustomMob> mobs = new LinkedHashMap<>();

    MobRegistry(RpgPlugin plugin) {
        this.plugin = plugin;
        this.idKey = new NamespacedKey(plugin, "mob_id");
    }

    NamespacedKey idKey() {
        return idKey;
    }

    void load() {
        mobs.clear();
        ConfigurationSection root = plugin.store().get(FILE).getConfigurationSection("mobs");
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section != null) {
                mobs.put(id, CustomMob.load(id, section));
            }
        }
    }

    void save(CustomMob mob) {
        if (mob.id == null) {
            int n = 1;
            while (mobs.containsKey("mob" + n)) {
                n++;
            }
            mob.id = "mob" + n;
        }
        mobs.put(mob.id, mob.copy());
        writeFile();
    }

    void delete(String id) {
        mobs.remove(id);
        writeFile();
    }

    CustomMob get(String id) {
        return id == null ? null : mobs.get(id);
    }

    List<CustomMob> all() {
        return new ArrayList<>(mobs.values());
    }

    /** Шаблон по сущности или по мете яйца. null, если это обычный моб/предмет. */
    CustomMob of(PersistentDataHolder holder) {
        if (holder == null) {
            return null;
        }
        return get(holder.getPersistentDataContainer().get(idKey, PersistentDataType.STRING));
    }

    private void writeFile() {
        YamlConfiguration yaml = plugin.store().get(FILE);
        yaml.set("mobs", null);
        ConfigurationSection root = yaml.createSection("mobs");
        for (CustomMob mob : mobs.values()) {
            mob.save(root.createSection(mob.id));
        }
        plugin.store().save(FILE);
    }
}
