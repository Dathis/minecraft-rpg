package com.dathis.rpg.modules.item;

import com.dathis.rpg.RpgPlugin;
import com.dathis.rpg.core.module.AbstractModule;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

/**
 * Кастомные предметы и оружие. Админ создаёт их в окне /create-item:
 * ванильная основа, название, цвет названия, описание; у оружия ещё урон от-до, крит и вампиризм.
 */
public final class ItemModule extends AbstractModule {

    private ItemRegistry registry;
    private ItemFactory factory;
    private ArmorListener armor;
    private BukkitTask armorTask;

    public ItemModule() {
        super("items", "Кастомные предметы и оружие");
    }

    @Override
    protected void onEnable() {
        registry = new ItemRegistry(plugin);
        registry.load();
        factory = new ItemFactory(registry);
        listen(new CombatListener(this));
        armor = new ArmorListener(this);
        listen(armor);
        plugin.health().addProvider(armor);
        plugin.mana().addProvider(armor);
        armorTask = plugin.getServer().getScheduler().runTaskTimer(plugin, armor, 20L, 20L);

        PluginCommand command = plugin.getCommand("create-item");
        if (command != null) {
            CreateItemCommand executor = new CreateItemCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }
    }

    @Override
    protected void onDisable() {
        if (armorTask != null) {
            armorTask.cancel();
            armorTask = null;
        }
        if (armor != null) {
            plugin.health().removeProvider(armor);
            plugin.mana().removeProvider(armor);
            armor.clear();
            armor = null;
        }
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

    /** Статы кастомного оружия для окна персонажа. */
    public record WeaponInfo(String name, double damageMin, double damageMax, double critChance,
                             double critMultiplier, double vampirism, int level) {
    }

    /** Статы оружия, если предмет — кастомное оружие, иначе null. */
    public WeaponInfo weapon(ItemStack stack) {
        CustomItem item = registry.of(stack);
        if (item == null || !item.isWeapon()) {
            return null;
        }
        return new WeaponInfo(item.name, item.damageMin, item.damageMax, item.critChance, critMultiplier(),
                item.vampirism, item.level);
    }

    /** Регенерация ХП в секунду от надетой кастомной брони. */
    public double armorRegeneration(Player player) {
        return armor == null ? 0 : armor.regeneration(player);
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
