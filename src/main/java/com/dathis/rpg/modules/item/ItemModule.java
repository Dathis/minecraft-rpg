package com.dathis.rpg.modules.item;

import com.dathis.rpg.RpgPlugin;
import com.dathis.rpg.core.module.AbstractModule;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Кастомные предметы и оружие. Админ создаёт их в окне /create-item:
 * ванильная основа, название, цвет названия, описание; у оружия ещё урон от-до, крит и вампиризм.
 */
public final class ItemModule extends AbstractModule {

    private ItemRegistry registry;
    private ItemFactory factory;

    public ItemModule() {
        super("items", "Кастомные предметы и оружие");
    }

    @Override
    protected void onEnable() {
        registry = new ItemRegistry(plugin);
        registry.load();
        factory = new ItemFactory(registry);
        listen(new CombatListener(this));

        PluginCommand command = plugin.getCommand("create-item");
        if (command != null) {
            CreateItemCommand executor = new CreateItemCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }
    }

    @Override
    protected void onDisable() {
        PluginCommand command = plugin.getCommand("create-item");
        if (command != null) {
            command.setExecutor(null);
            command.setTabCompleter(null);
        }
    }

    RpgPlugin plugin() {
        return plugin;
    }

    ItemRegistry registry() {
        return registry;
    }

    ItemFactory factory() {
        return factory;
    }

    double critMultiplier() {
        return plugin.getConfig().getDouble("items.crit-multiplier", 1.5);
    }

    /** Выдаёт игроку предмет по ID. Если инвентарь полон, предмет падает рядом. */
    boolean give(Player player, String id) {
        CustomItem item = registry.get(id);
        if (item == null) {
            return false;
        }
        ItemStack stack = factory.build(item);
        player.getInventory().addItem(stack).values()
                .forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
        return true;
    }
}
