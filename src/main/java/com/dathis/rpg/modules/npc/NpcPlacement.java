package com.dathis.rpg.modules.npc;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

/** Место в мире, где стоит NPC по шаблону. */
final class NpcPlacement {

    final String id;
    String npcId;
    String world;
    double x, y, z;
    float yaw;

    NpcPlacement(String id, String npcId, Location location) {
        this.id = id;
        this.npcId = npcId;
        moveTo(location);
    }

    private NpcPlacement(String id) {
        this.id = id;
    }

    void moveTo(Location location) {
        world = location.getWorld().getName();
        x = location.getX();
        y = location.getY();
        z = location.getZ();
        yaw = location.getYaw();
    }

    /** null, если мир не загружен. */
    Location location() {
        World w = Bukkit.getWorld(world);
        return w == null ? null : new Location(w, x, y, z, yaw, 0);
    }

    String coordsText() {
        return world + " " + (int) Math.floor(x) + " " + (int) Math.floor(y) + " " + (int) Math.floor(z);
    }

    void save(ConfigurationSection s) {
        s.set("npc", npcId);
        s.set("world", world);
        s.set("x", x);
        s.set("y", y);
        s.set("z", z);
        s.set("yaw", yaw);
    }

    static NpcPlacement load(String id, ConfigurationSection s) {
        NpcPlacement p = new NpcPlacement(id);
        p.npcId = s.getString("npc");
        p.world = s.getString("world");
        if (p.npcId == null || p.world == null) {
            return null;
        }
        p.x = s.getDouble("x");
        p.y = s.getDouble("y");
        p.z = s.getDouble("z");
        p.yaw = (float) s.getDouble("yaw");
        return p;
    }
}
