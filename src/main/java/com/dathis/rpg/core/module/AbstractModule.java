package com.dathis.rpg.core.module;

import com.dathis.rpg.RpgPlugin;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

import java.util.ArrayList;
import java.util.List;

/** Базовый класс модуля: помнит плагин и снимает свои слушатели событий при выключении. */
public abstract class AbstractModule implements RpgModule {

    private final String id;
    private final String displayName;
    private final List<Listener> listeners = new ArrayList<>();
    protected RpgPlugin plugin;

    protected AbstractModule(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    @Override
    public final String id() {
        return id;
    }

    @Override
    public final String displayName() {
        return displayName;
    }

    @Override
    public final void enable(RpgPlugin plugin) {
        this.plugin = plugin;
        onEnable();
    }

    @Override
    public final void disable() {
        onDisable();
        listeners.forEach(HandlerList::unregisterAll);
        listeners.clear();
    }

    protected void onEnable() {
    }

    protected void onDisable() {
    }

    protected void listen(Listener listener) {
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        listeners.add(listener);
    }
}
