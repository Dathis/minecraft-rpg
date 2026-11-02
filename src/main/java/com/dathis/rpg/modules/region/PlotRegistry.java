package com.dathis.rpg.modules.region;

import com.dathis.rpg.RpgPlugin;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Выкупленные участки. Файл data/plots.yml. */
final class PlotRegistry {

    static final String FILE = "plots";

    private final RpgPlugin plugin;
    private final Map<String, Plot> plots = new LinkedHashMap<>();

    PlotRegistry(RpgPlugin plugin) {
        this.plugin = plugin;
    }

    void load() {
        plots.clear();
        ConfigurationSection root = plugin.store().get(FILE).getConfigurationSection("plots");
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            Plot plot = s == null ? null : Plot.load(id, s);
            if (plot == null) {
                plugin.getLogger().warning("Участок " + id + " в plots.yml повреждён, пропускаю.");
                continue;
            }
            plots.put(id, plot);
        }
    }

    void save() {
        YamlConfiguration yaml = plugin.store().get(FILE);
        yaml.set("plots", null);
        ConfigurationSection root = yaml.createSection("plots");
        for (Plot plot : plots.values()) {
            plot.save(root.createSection(plot.id));
        }
        plugin.store().save(FILE);
    }

    Plot create(Area land, PlotSize size, int minX, int minZ, int side, Player owner) {
        Plot plot = new Plot(nextId(), land.id, size, land.world, minX, minZ, side);
        plot.owner = owner.getUniqueId();
        plot.ownerName = owner.getName();
        plots.put(plot.id, plot);
        save();
        return plot;
    }

    void delete(Plot plot) {
        plots.remove(plot.id);
        save();
    }

    /** Удалить все участки земли (когда админ удаляет саму землю). */
    int deleteLand(String landId) {
        int before = plots.size();
        plots.values().removeIf(p -> p.land.equals(landId));
        int removed = before - plots.size();
        if (removed > 0) {
            save();
        }
        return removed;
    }

    Plot get(String id) {
        return plots.get(id);
    }

    /** Участок, в котором стоит блок, или null. */
    Plot plotAt(Block block) {
        if (plots.isEmpty()) {
            return null;
        }
        for (Plot plot : plots.values()) {
            if (plot.contains(block)) {
                return plot;
            }
        }
        return null;
    }

    /** Первый участок, который пересекается с этим квадратом, или null (место свободно). */
    Plot overlapping(String world, int minX, int minZ, int maxX, int maxZ) {
        for (Plot plot : plots.values()) {
            if (plot.overlaps(world, minX, minZ, maxX, maxZ)) {
                return plot;
            }
        }
        return null;
    }

    List<Plot> inLand(String landId) {
        List<Plot> list = new ArrayList<>();
        for (Plot plot : plots.values()) {
            if (plot.land.equals(landId)) {
                list.add(plot);
            }
        }
        return list;
    }

    int ownedCount(UUID player) {
        int n = 0;
        for (Plot plot : plots.values()) {
            if (player.equals(plot.owner)) {
                n++;
            }
        }
        return n;
    }

    /** Участки, где игрок владелец или помощник. Сначала свои. */
    List<Plot> of(UUID player) {
        List<Plot> own = new ArrayList<>();
        List<Plot> shared = new ArrayList<>();
        for (Plot plot : plots.values()) {
            if (player.equals(plot.owner)) {
                own.add(plot);
            } else if (plot.members.containsKey(player)) {
                shared.add(plot);
            }
        }
        own.addAll(shared);
        return own;
    }

    private String nextId() {
        int n = 1;
        while (plots.containsKey("plot" + n)) {
            n++;
        }
        return "plot" + n;
    }
}
