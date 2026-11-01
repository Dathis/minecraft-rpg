package com.dathis.rpg;

import com.dathis.rpg.core.command.RpgCommand;
import com.dathis.rpg.core.config.Messages;
import com.dathis.rpg.core.gui.MenuListener;
import com.dathis.rpg.core.input.ChatInput;
import com.dathis.rpg.core.module.ModuleManager;
import com.dathis.rpg.core.platform.PlatformService;
import com.dathis.rpg.core.selection.SelectionWand;
import com.dathis.rpg.core.storage.YamlStore;
import com.dathis.rpg.modules.dialog.DialogModule;
import com.dathis.rpg.modules.hud.HudModule;
import com.dathis.rpg.modules.item.ItemModule;
import com.dathis.rpg.modules.mob.MobModule;
import com.dathis.rpg.modules.npc.NpcModule;
import com.dathis.rpg.modules.quest.QuestModule;
import com.dathis.rpg.modules.region.RegionModule;
import com.dathis.rpg.modules.tree.TreeReplantModule;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Точка входа плагина. Сам по себе почти ничего не делает:
 * поднимает общие сервисы (конфиг, сообщения, платформа, хранилище)
 * и включает модули, разрешённые в config.yml.
 */
public final class RpgPlugin extends JavaPlugin {

    private Messages messages;
    private PlatformService platform;
    private YamlStore store;
    private ModuleManager modules;
    private ChatInput chatInput;
    private SelectionWand selectionWand;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        messages = new Messages(this);
        platform = new PlatformService(this);
        store = new YamlStore(this);
        modules = new ModuleManager(this);
        chatInput = new ChatInput(this);
        getServer().getPluginManager().registerEvents(chatInput, this);
        getServer().getPluginManager().registerEvents(new MenuListener(this), this);
        selectionWand = new SelectionWand(this);
        getServer().getPluginManager().registerEvents(selectionWand, this);
        getServer().getScheduler().runTaskTimer(this, selectionWand, 10L, 10L);

        // Порядок важен: NPC нужны диалогам и квестам (торговля — часть NPC).
        modules.register(new RegionModule());
        modules.register(new TreeReplantModule());
        modules.register(new NpcModule());
        modules.register(new DialogModule());
        modules.register(new QuestModule());
        modules.register(new MobModule());
        modules.register(new ItemModule());
        modules.register(new HudModule());
        modules.enableAll();

        PluginCommand rpg = getCommand("rpg");
        if (rpg != null) {
            RpgCommand command = new RpgCommand(this);
            rpg.setExecutor(command);
            rpg.setTabCompleter(command);
        }

        getLogger().info("MinecraftRPG включён. Floodgate: " + (platform.isFloodgateAvailable() ? "найден" : "не найден"));
    }

    @Override
    public void onDisable() {
        // Закрываем наши меню, чтобы после /reload никто не остался в «мёртвом» окне
        getServer().getOnlinePlayers().forEach(p -> {
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof com.dathis.rpg.core.gui.Menu) {
                p.closeInventory();
            }
        });
        if (modules != null) {
            modules.disableAll();
        }
        if (store != null) {
            store.saveAll();
        }
    }

    /** Перезагружает config.yml и перезапускает модули. */
    public void reload() {
        modules.disableAll();
        store.saveAll();
        reloadConfig();
        messages.reload();
        modules.enableAll();
    }

    public Messages messages() {
        return messages;
    }

    public PlatformService platform() {
        return platform;
    }

    public YamlStore store() {
        return store;
    }

    public ChatInput chatInput() {
        return chatInput;
    }

    /** Палочка выделения областей (зоны спавна, области квестов). */
    public SelectionWand selectionWand() {
        return selectionWand;
    }

    public ModuleManager modules() {
        return modules;
    }
}
