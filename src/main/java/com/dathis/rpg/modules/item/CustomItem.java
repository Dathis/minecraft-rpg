package com.dathis.rpg.modules.item;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

/** Шаблон кастомного предмета, созданный админом. Он же черновик в редакторе. */
public final class CustomItem {

    public enum Kind { ITEM, WEAPON }

    String id;               // null, пока черновик не сохранён
    Kind kind;
    Material material;
    String name;
    String nameColor = "white"; // имя цвета (gold, red...) или #RRGGBB
    List<String> lore = new ArrayList<>();

    // Только для оружия
    double damageMin = 5;
    double damageMax = 8;
    double critChance;       // %, 0..100
    double vampirism;        // % от нанесённого урона, который лечит атакующего

    CustomItem(Kind kind, Material material, String name) {
        this.kind = kind;
        this.material = material;
        this.name = name;
    }

    boolean isWeapon() {
        return kind == Kind.WEAPON;
    }

    TextColor color() {
        return parseColor(nameColor);
    }

    static TextColor parseColor(String value) {
        if (value == null) {
            return NamedTextColor.WHITE;
        }
        NamedTextColor named = NamedTextColor.NAMES.value(value);
        if (named != null) {
            return named;
        }
        TextColor hex = TextColor.fromHexString(value);
        return hex != null ? hex : NamedTextColor.WHITE;
    }

    CustomItem copy() {
        CustomItem c = new CustomItem(kind, material, name);
        c.id = id;
        c.nameColor = nameColor;
        c.lore = new ArrayList<>(lore);
        c.damageMin = damageMin;
        c.damageMax = damageMax;
        c.critChance = critChance;
        c.vampirism = vampirism;
        return c;
    }

    void save(ConfigurationSection s) {
        s.set("kind", kind.name());
        s.set("material", material.name());
        s.set("name", name);
        s.set("name-color", nameColor);
        s.set("lore", lore);
        if (isWeapon()) {
            s.set("damage-min", damageMin);
            s.set("damage-max", damageMax);
            s.set("crit-chance", critChance);
            s.set("vampirism", vampirism);
        }
    }

    static CustomItem load(String id, ConfigurationSection s) {
        Material material = Material.matchMaterial(s.getString("material", "STONE"));
        Kind kind = "WEAPON".equalsIgnoreCase(s.getString("kind")) ? Kind.WEAPON : Kind.ITEM;
        CustomItem item = new CustomItem(kind, material == null ? Material.STONE : material, s.getString("name", id));
        item.id = id;
        item.nameColor = s.getString("name-color", "white");
        item.lore = new ArrayList<>(s.getStringList("lore"));
        item.damageMin = s.getDouble("damage-min", 5);
        item.damageMax = s.getDouble("damage-max", 8);
        item.critChance = s.getDouble("crit-chance", 0);
        item.vampirism = s.getDouble("vampirism", 0);
        return item;
    }
}
