package com.dathis.rpg.modules.region;

import com.dathis.rpg.RpgPlugin;
import com.dathis.rpg.core.module.AbstractModule;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Защита мира и зоны.
 * - Весь мир защищён: игроки без права {@value #BYPASS} (по умолчанию все, кроме OP) не ломают,
 *   не ставят и не открывают блоки.
 * - Зоны строительства: внутри можно всё.
 * - Зоны добычи: админ задаёт, какие блоки ломать, что ставится на их место и вернутся ли они через время.
 * Окно: /create-region.
 */
public final class RegionModule extends AbstractModule {

    /** Право ломать и строить везде (по умолчанию у OP). */
    static final String BYPASS = "rpg.region.bypass";

    private AreaRegistry areas;
    private RegenManager regen;
    private BukkitTask regenTask;

    private boolean protectWorld;
    private Set<String> worlds;
    private boolean allowDoors;
    private Set<Material> allowedBlocks;
    private boolean blockExplosions;
    private boolean blockFire;
    private boolean blockMobGriefing;
    private boolean keepLeaves;
    private boolean dropsToInventory;
    private boolean denyMessages;

    public RegionModule() {
        super("regions", "Защита мира и зоны");
    }

    @Override
    protected void onEnable() {
        readConfig();
        areas = new AreaRegistry(plugin);
        areas.load();
        regen = new RegenManager(plugin);
        regen.load();
        regenTask = plugin.getServer().getScheduler().runTaskTimer(plugin, regen, 20L, 20L);
        listen(new ProtectionListener(this));

        PluginCommand command = plugin.getCommand("create-region");
        if (command != null) {
            CreateRegionCommand executor = new CreateRegionCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }
    }

    @Override
    protected void onDisable() {
        if (regenTask != null) {
            regenTask.cancel();
            regenTask = null;
        }
        if (regen != null) {
            regen.save();
        }
        PluginCommand command = plugin.getCommand("create-region");
        if (command != null) {
            command.setExecutor(null);
            command.setTabCompleter(null);
        }
    }

    private void readConfig() {
        FileConfiguration c = plugin.getConfig();
        protectWorld = c.getBoolean("regions.protect-world", true);
        worlds = new HashSet<>(c.getStringList("regions.worlds"));
        allowDoors = c.getBoolean("regions.allow-doors", true);
        List<String> allowed = c.isList("regions.allowed-blocks") ? c.getStringList("regions.allowed-blocks")
                : List.of("CRAFTING_TABLE", "ENCHANTING_TABLE", "ANVIL", "CHIPPED_ANVIL", "DAMAGED_ANVIL",
                "GRINDSTONE", "SMITHING_TABLE", "STONECUTTER", "LOOM", "CARTOGRAPHY_TABLE", "ENDER_CHEST");
        allowedBlocks = EnumSet.noneOf(Material.class);
        for (String name : allowed) {
            Material m = Material.matchMaterial(name.toUpperCase(Locale.ROOT));
            if (m != null) {
                allowedBlocks.add(m);
            } else {
                plugin.getLogger().warning("regions.allowed-blocks: неизвестный блок " + name);
            }
        }
        blockExplosions = c.getBoolean("regions.block-explosions", true);
        blockFire = c.getBoolean("regions.block-fire", true);
        blockMobGriefing = c.getBoolean("regions.block-mob-griefing", true);
        keepLeaves = c.getBoolean("regions.keep-leaves-in-mining-zones", true);
        dropsToInventory = c.getBoolean("regions.drops-to-inventory", true);
        denyMessages = c.getBoolean("regions.deny-messages", true);
    }

    RpgPlugin plugin() {
        return plugin;
    }

    AreaRegistry areas() {
        return areas;
    }

    RegenManager regen() {
        return regen;
    }

    /** Защищён ли этот мир (regions.protect-world и список regions.worlds; пустой список — все миры). */
    boolean protects(World world) {
        return protectWorld && (worlds.isEmpty() || worlds.contains(world.getName()));
    }

    boolean protectWorld() {
        return protectWorld;
    }

    /** Можно ли игроку пользоваться этим блоком вне зон строительства (верстак, двери...). */
    boolean canUse(Material type) {
        if (allowedBlocks.contains(type)) {
            return true;
        }
        return allowDoors && (Tag.WOODEN_DOORS.isTagged(type) || Tag.WOODEN_TRAPDOORS.isTagged(type)
                || Tag.FENCE_GATES.isTagged(type) || Tag.BUTTONS.isTagged(type));
    }

    boolean blockExplosions() {
        return blockExplosions;
    }

    boolean blockFire() {
        return blockFire;
    }

    boolean blockMobGriefing() {
        return blockMobGriefing;
    }

    boolean keepLeaves() {
        return keepLeaves;
    }

    boolean dropsToInventory() {
        return dropsToInventory;
    }

    boolean denyMessages() {
        return denyMessages;
    }
}
