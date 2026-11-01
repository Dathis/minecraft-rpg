package com.dathis.rpg.modules.mob;

import com.dathis.rpg.RpgPlugin;
import com.dathis.rpg.core.input.Numbers;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import com.dathis.rpg.core.module.AbstractModule;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Кастомные мобы. Админ создаёт их в окне /create-mob на основе ванильного моба:
 * название, ранг (обычный, элитный, босс), ХП, урон от и до, лут с шансами.
 * Спавн — через яйцо призыва или /create-mob spawn.
 */
public final class MobModule extends AbstractModule {

    /** Предел ванильного атрибута max_health. */
    static final double MAX_HEALTH_LIMIT = 1024;

    private MobRegistry registry;

    public MobModule() {
        super("mobs", "Кастомные мобы");
    }

    @Override
    protected void onEnable() {
        registry = new MobRegistry(plugin);
        registry.load();
        listen(new MobListener(this));
        listen(new NaturalSpawnBlocker(
                plugin.getConfig().getBoolean("spawning.block-natural-monsters", true),
                plugin.getConfig().getBoolean("spawning.block-natural-animals", false)));

        PluginCommand command = plugin.getCommand("create-mob");
        if (command != null) {
            CreateMobCommand executor = new CreateMobCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }
    }

    @Override
    protected void onDisable() {
        PluginCommand command = plugin.getCommand("create-mob");
        if (command != null) {
            command.setExecutor(null);
            command.setTabCompleter(null);
        }
    }

    RpgPlugin plugin() {
        return plugin;
    }

    MobRegistry registry() {
        return registry;
    }

    /** Спавнит моба по шаблону. */
    Entity spawn(CustomMob mob, Location location) {
        Class<? extends Entity> type = mob.type.getEntityClass();
        if (type == null) {
            return null;
        }
        // randomizeData=false: без случайной брони, оружия, детёнышей и жокеев — только то, что задал админ
        return location.getWorld().spawn(location, type, false, entity -> apply(mob, entity));
    }

    private void apply(CustomMob mob, Entity entity) {
        entity.getPersistentDataContainer().set(registry.idKey(), PersistentDataType.STRING, mob.id);
        entity.customName(mob.displayName());
        entity.setCustomNameVisible(true);
        if (entity instanceof Ageable ageable) {
            ageable.setAdult();
        }
        if (entity instanceof LivingEntity living) {
            double health = Math.max(1, Math.min(MAX_HEALTH_LIMIT, mob.health));
            AttributeInstance max = living.getAttribute(Attribute.MAX_HEALTH);
            if (max != null) {
                max.setBaseValue(health);
                living.setHealth(health);
            }
            living.setRemoveWhenFarAway(false);
            EntityEquipment equipment = living.getEquipment();
            if (equipment != null) {
                equipment.clear();
                mob.equipment.forEach((slot, item) -> {
                    equipment.setItem(slot, item.clone());
                    // Снаряжение не выпадает при смерти: что падает, задаётся в луте
                    equipment.setDropChance(slot, 0f);
                });
            }
        }
    }

    static Material eggMaterial(EntityType type) {
        Material egg = Bukkit.getItemFactory().getSpawnEgg(type);
        if (egg == null) {
            egg = Material.matchMaterial(type.name() + "_SPAWN_EGG");
        }
        return egg;
    }

    /** Яйцо призыва этого моба. */
    ItemStack egg(CustomMob mob) {
        Material material = eggMaterial(mob.type);
        ItemStack stack = new ItemStack(material != null ? material : Material.EGG);
        stack.editMeta(meta -> {
            meta.displayName(Component.text("Яйцо призыва: ").color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false).append(mob.displayName()));
            meta.lore(summary(mob));
            if (mob.id != null) {
                meta.getPersistentDataContainer().set(registry.idKey(), PersistentDataType.STRING, mob.id);
            }
        });
        return stack;
    }

    static List<Component> summary(CustomMob mob) {
        List<Component> lines = new ArrayList<>();
        lines.add(line("Ранг: " + mob.tier.label, NamedTextColor.GRAY));
        lines.add(line("ХП: " + Numbers.format(mob.health), NamedTextColor.GREEN));
        lines.add(line("Урон: " + Numbers.format(mob.damageMin) + " - " + Numbers.format(mob.damageMax), NamedTextColor.RED));
        lines.add(line("Снаряжение: " + mob.equipment.size() + " шт.", NamedTextColor.AQUA));
        lines.add(line("Лут: " + mob.drops.size() + (mob.vanillaDrops ? " + ванильный" : ""), NamedTextColor.GOLD));
        return lines;
    }

    private static Component line(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    boolean giveEgg(Player player, String id, int amount) {
        CustomMob mob = registry.get(id);
        if (mob == null) {
            return false;
        }
        ItemStack egg = egg(mob);
        egg.setAmount(Math.max(1, Math.min(64, amount)));
        player.getInventory().addItem(egg).values()
                .forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
        return true;
    }
}
