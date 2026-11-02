package com.dathis.rpg.modules.region;

import com.dathis.rpg.RpgPlugin;
import com.dathis.rpg.core.module.AbstractModule;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Защита мира и зоны.
 * - Весь мир защищён: игроки без права {@value #BYPASS} (по умолчанию все, кроме OP) не ломают,
 *   не ставят и не открывают блоки.
 * - Зоны строительства: внутри можно всё.
 * - Зоны добычи: админ задаёт, какие блоки ломать, что ставится на их место и вернутся ли они через время.
 * - Земли под участки: игрок с бумагой на участок занимает на земле квадрат и строит там сам.
 * Окно: /create-region, у игроков — /plot.
 */
public final class RegionModule extends AbstractModule {

    /** Право ломать и строить везде (по умолчанию у OP). */
    static final String BYPASS = "rpg.region.bypass";

    private AreaRegistry areas;
    private RegenManager regen;
    private BukkitTask regenTask;
    private PlotRegistry plots;
    private PlotDeeds deeds;

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
    private final Map<PlotSize, Integer> plotSides = new EnumMap<>(PlotSize.class);
    private int maxPlotsPerPlayer;
    private int maxPlotMembers;
    private boolean refundDeed;
    private boolean plotTeleport;

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
        plots = new PlotRegistry(plugin);
        plots.load();
        deeds = new PlotDeeds(this);
        listen(new ProtectionListener(this));
        listen(new PlotListener(this));

        PluginCommand command = plugin.getCommand("create-region");
        if (command != null) {
            CreateRegionCommand executor = new CreateRegionCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }
        PluginCommand plot = plugin.getCommand("plot");
        if (plot != null) {
            PlotCommand executor = new PlotCommand(this);
            plot.setExecutor(executor);
            plot.setTabCompleter(executor);
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
        PluginCommand plot = plugin.getCommand("plot");
        if (plot != null) {
            plot.setExecutor(null);
            plot.setTabCompleter(null);
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
        for (PlotSize size : PlotSize.values()) {
            plotSides.put(size, Math.max(1, Math.min(512, c.getInt(size.configKey(), size.defaultSide))));
        }
        maxPlotsPerPlayer = Math.max(0, c.getInt("plots.max-per-player", 0));
        maxPlotMembers = Math.max(0, Math.min(9, c.getInt("plots.max-members", 9)));
        refundDeed = c.getBoolean("plots.refund-deed-on-release", true);
        plotTeleport = c.getBoolean("plots.allow-teleport", true);
    }

    /** Игрок нажал бумагой на участок (или кнопкой в /plot): открыть карту земли. */
    void useDeed(Player player, ItemStack item) {
        PlotDeeds.Deed deed = deeds.read(item);
        if (deed == null) {
            plugin.messages().send(player, "<red>Возьмите в руку бумагу на участок.</red>");
            return;
        }
        Area land = areas.get(deed.land());
        if (land == null || land.kind != Area.Kind.LAND) {
            plugin.messages().send(player, "<red>Земли из этой бумаги больше нет. Обратитесь к администрации.</red>");
            return;
        }
        PlotMapMenu map = new PlotMapMenu(this, land, deed.size(), player);
        if (map.cells() == 0) {
            plugin.messages().send(player, "<red>Эта земля меньше участка такого размера.</red>");
            return;
        }
        map.open(player);
    }

    /**
     * Чужой ли это участок для игрока (не владелец и не помощник, без rpg.region.bypass).
     * Нужен способностям: телепорт не переносит на чужой участок.
     */
    public boolean isForeignPlot(Player player, org.bukkit.Location location) {
        if (plots == null || location.getWorld() == null || player.hasPermission("rpg.region.bypass")) {
            return false;
        }
        Plot plot = plots.plotAt(location.getBlock());
        return plot != null && !plot.canBuild(player);
    }

    PlotRegistry plots() {
        return plots;
    }

    PlotDeeds deeds() {
        return deeds;
    }

    /** Сторона квадрата участка этого размера (config plots.size-*). */
    int plotSide(PlotSize size) {
        return plotSides.getOrDefault(size, size.defaultSide);
    }

    /** Сколько участков может занять один игрок, 0 — без ограничения. */
    int maxPlotsPerPlayer() {
        return maxPlotsPerPlayer;
    }

    int maxPlotMembers() {
        return maxPlotMembers;
    }

    boolean refundDeed() {
        return refundDeed;
    }

    boolean plotTeleport() {
        return plotTeleport;
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
