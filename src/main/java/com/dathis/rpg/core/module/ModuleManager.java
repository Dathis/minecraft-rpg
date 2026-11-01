package com.dathis.rpg.core.module;

import com.dathis.rpg.RpgPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;

/** Хранит все модули и включает те, что разрешены в config.yml. */
public final class ModuleManager {

    private final RpgPlugin plugin;
    private final Map<String, RpgModule> modules = new LinkedHashMap<>();
    private final List<RpgModule> enabled = new ArrayList<>();

    public ModuleManager(RpgPlugin plugin) {
        this.plugin = plugin;
    }

    public void register(RpgModule module) {
        modules.put(module.id(), module);
    }

    public void enableAll() {
        for (RpgModule module : modules.values()) {
            if (!plugin.getConfig().getBoolean("modules." + module.id(), true)) {
                continue;
            }
            try {
                module.enable(plugin);
                enabled.add(module);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Не удалось включить модуль " + module.id(), e);
            }
        }
    }

    public void disableAll() {
        List<RpgModule> reversed = new ArrayList<>(enabled);
        Collections.reverse(reversed);
        for (RpgModule module : reversed) {
            try {
                module.disable();
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Ошибка при выключении модуля " + module.id(), e);
            }
        }
        enabled.clear();
    }

    public boolean isEnabled(String id) {
        return enabled.stream().anyMatch(m -> m.id().equals(id));
    }

    public Optional<RpgModule> get(String id) {
        return Optional.ofNullable(modules.get(id));
    }

    public List<RpgModule> all() {
        return List.copyOf(modules.values());
    }
}
