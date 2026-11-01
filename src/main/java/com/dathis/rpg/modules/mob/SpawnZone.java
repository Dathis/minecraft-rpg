package com.dathis.rpg.modules.mob;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.List;

/** Зона спавна: в ней держится до {@code maxAlive} мобов одного шаблона, убитые возвращаются через respawn-секунд. */
final class SpawnZone {

    final String id;
    String mobId;
    String world;
    int minX, minY, minZ, maxX, maxY, maxZ;
    int maxAlive = 3;
    int respawnSeconds = 60;

    /** Когда можно вернуть убитых (время в мс). В файл не сохраняется: после рестарта зона просто заполнится. */
    final List<Long> pendingRespawns = new ArrayList<>();

    SpawnZone(String id, String mobId, String world, Location a, Location b) {
        this.id = id;
        this.mobId = mobId;
        this.world = world;
        setArea(a, b);
    }

    private SpawnZone(String id) {
        this.id = id;
    }

    void setArea(Location a, Location b) {
        world = a.getWorld().getName();
        minX = Math.min(a.getBlockX(), b.getBlockX());
        minY = Math.min(a.getBlockY(), b.getBlockY());
        minZ = Math.min(a.getBlockZ(), b.getBlockZ());
        maxX = Math.max(a.getBlockX(), b.getBlockX());
        maxY = Math.max(a.getBlockY(), b.getBlockY());
        maxZ = Math.max(a.getBlockZ(), b.getBlockZ());
    }

    World bukkitWorld() {
        return Bukkit.getWorld(world);
    }

    /** Блоки зоны целиком (плюс немного места сверху, чтобы мобы, стоящие на верхнем блоке, считались). */
    BoundingBox box() {
        return new BoundingBox(minX, minY, minZ, maxX + 1, maxY + 4, maxZ + 1);
    }

    Location center() {
        World w = bukkitWorld();
        return w == null ? null : new Location(w, (minX + maxX + 1) / 2.0, maxY + 1, (minZ + maxZ + 1) / 2.0);
    }

    String sizeText() {
        return (maxX - minX + 1) + "x" + (maxY - minY + 1) + "x" + (maxZ - minZ + 1);
    }

    String coordsText() {
        return world + " " + minX + " " + minY + " " + minZ + " → " + maxX + " " + maxY + " " + maxZ;
    }

    void save(ConfigurationSection s) {
        s.set("mob", mobId);
        s.set("world", world);
        s.set("min", List.of(minX, minY, minZ));
        s.set("max", List.of(maxX, maxY, maxZ));
        s.set("max-alive", maxAlive);
        s.set("respawn-seconds", respawnSeconds);
    }

    static SpawnZone load(String id, ConfigurationSection s) {
        SpawnZone zone = new SpawnZone(id);
        zone.mobId = s.getString("mob");
        zone.world = s.getString("world", "world");
        List<Integer> min = s.getIntegerList("min");
        List<Integer> max = s.getIntegerList("max");
        if (min.size() != 3 || max.size() != 3 || zone.mobId == null) {
            return null;
        }
        zone.minX = min.get(0);
        zone.minY = min.get(1);
        zone.minZ = min.get(2);
        zone.maxX = max.get(0);
        zone.maxY = max.get(1);
        zone.maxZ = max.get(2);
        zone.maxAlive = Math.max(1, s.getInt("max-alive", 3));
        zone.respawnSeconds = Math.max(1, s.getInt("respawn-seconds", 60));
        return zone;
    }
}
