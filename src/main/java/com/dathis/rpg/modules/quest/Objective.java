package com.dathis.rpg.modules.quest;

import com.dathis.rpg.core.storage.ItemCodec;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;

/** Цель квеста. Какие поля используются, зависит от типа. */
final class Objective {

    enum Type {
        KILL("Убить", Material.IRON_SWORD),
        BRING("Принести", Material.CHEST),
        TALK("Поговорить", Material.VILLAGER_SPAWN_EGG),
        EXPLORE("Разведать", Material.FILLED_MAP);

        final String label;
        final Material icon;

        Type(String label, Material icon) {
            this.label = label;
            this.icon = icon;
        }
    }

    Type type;
    int amount = 1;
    /** KILL: «mob:&lt;id&gt;» (кастомный моб) или «type:ZOMBIE» (ванильный). TALK: id NPC. */
    String target;
    /** BRING: какой предмет принести (кастомный сравнивается по id шаблона, обычный — по материалу). */
    ItemStack item;
    /** EXPLORE: область. */
    String world;
    int minX, minY, minZ, maxX, maxY, maxZ;
    String areaName = "Неизвестное место";

    Objective(Type type) {
        this.type = type;
    }

    boolean hasArea() {
        return world != null;
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

    boolean contains(Location l) {
        return world != null && l.getWorld() != null && l.getWorld().getName().equals(world)
                && l.getBlockX() >= minX && l.getBlockX() <= maxX
                && l.getBlockY() >= minY && l.getBlockY() <= maxY
                && l.getBlockZ() >= minZ && l.getBlockZ() <= maxZ;
    }

    /** Задана ли цель полностью (иначе квест нельзя взять). */
    boolean complete() {
        return switch (type) {
            case KILL, TALK -> target != null;
            case BRING -> item != null;
            case EXPLORE -> hasArea();
        };
    }

    Objective copy() {
        Objective c = new Objective(type);
        c.amount = amount;
        c.target = target;
        c.item = item == null ? null : item.clone();
        c.world = world;
        c.minX = minX;
        c.minY = minY;
        c.minZ = minZ;
        c.maxX = maxX;
        c.maxY = maxY;
        c.maxZ = maxZ;
        c.areaName = areaName;
        return c;
    }

    void save(ConfigurationSection s) {
        s.set("type", type.name());
        s.set("amount", amount);
        s.set("target", target);
        s.set("item", item == null ? null : ItemCodec.encode(item));
        if (hasArea()) {
            s.set("world", world);
            s.set("min", List.of(minX, minY, minZ));
            s.set("max", List.of(maxX, maxY, maxZ));
            s.set("area-name", areaName);
        }
    }

    static Objective load(ConfigurationSection s) {
        Type type;
        try {
            type = Type.valueOf(s.getString("type", "KILL").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
        Objective o = new Objective(type);
        o.amount = Math.max(1, s.getInt("amount", 1));
        o.target = s.getString("target");
        o.item = ItemCodec.decode(s.getString("item"));
        List<Integer> min = s.getIntegerList("min");
        List<Integer> max = s.getIntegerList("max");
        if (s.getString("world") != null && min.size() == 3 && max.size() == 3) {
            o.world = s.getString("world");
            o.minX = min.get(0);
            o.minY = min.get(1);
            o.minZ = min.get(2);
            o.maxX = max.get(0);
            o.maxY = max.get(1);
            o.maxZ = max.get(2);
        }
        o.areaName = s.getString("area-name", o.areaName);
        return o;
    }
}
