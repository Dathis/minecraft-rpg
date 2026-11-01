package com.dathis.rpg.core.storage;

import com.dathis.rpg.RpgPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Простое хранилище: у каждого модуля свой файл plugins/MinecraftRPG/data/&lt;модуль&gt;.yml.
 * Всё, что админ настраивает в игре (зоны, NPC, мобы, оружие), сохраняется сюда.
 */
public final class YamlStore {

    private final RpgPlugin plugin;
    private final File dataDir;
    private final Map<String, YamlConfiguration> files = new HashMap<>();

    public YamlStore(RpgPlugin plugin) {
        this.plugin = plugin;
        this.dataDir = new File(plugin.getDataFolder(), "data");
        if (!dataDir.exists() && !dataDir.mkdirs()) {
            plugin.getLogger().warning("Не удалось создать папку " + dataDir);
        }
    }

    public YamlConfiguration get(String name) {
        return files.computeIfAbsent(name, n -> YamlConfiguration.loadConfiguration(file(n)));
    }

    public void save(String name) {
        YamlConfiguration yaml = files.get(name);
        if (yaml == null) {
            return;
        }
        try {
            yaml.save(file(name));
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Не удалось сохранить " + name + ".yml", e);
        }
    }

    public void saveAll() {
        files.keySet().forEach(this::save);
    }

    private File file(String name) {
        return new File(dataDir, name + ".yml");
    }
}
