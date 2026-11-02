package com.dathis.rpg.modules.ability;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Шаблон способности, созданный админом. Он же черновик в редакторе. */
final class Ability {

    /** Цвета названия по кругу (кнопка «Цвет»): только 16 именованных, их видно и на Bedrock. */
    static final List<String> COLORS = List.of("light_purple", "aqua", "gold", "yellow", "green", "red",
            "blue", "white", "dark_purple", "dark_aqua", "dark_green", "dark_red", "dark_blue", "gray");

    String id;                 // null, пока черновик не сохранён
    Mechanic mechanic;
    Material material;         // предмет-руна
    String name;
    String nameColor = "light_purple";
    List<String> lore = new ArrayList<>();
    double manaCost;
    double cooldown;           // секунды
    int level = 1;             // с какого уровня персонажа работает
    double power;
    double range;
    double duration;
    double percent;
    /** Растёт при каждом сохранении: руны у игроков с другой ревизией обновляют название и описание. */
    int rev;

    Ability(Mechanic mechanic) {
        this.mechanic = mechanic;
        this.material = mechanic.icon;
        this.name = mechanic.label;
        this.manaCost = mechanic.defaultMana;
        this.cooldown = mechanic.defaultCooldown;
        for (Mechanic.Param param : mechanic.params) {
            set(param.stat(), param.def());
        }
    }

    double get(Mechanic.Stat stat) {
        return switch (stat) {
            case POWER -> power;
            case RANGE -> range;
            case DURATION -> duration;
            case PERCENT -> percent;
        };
    }

    void set(Mechanic.Stat stat, double value) {
        switch (stat) {
            case POWER -> power = value;
            case RANGE -> range = value;
            case DURATION -> duration = value;
            case PERCENT -> percent = value;
        }
    }

    String nextColor() {
        int i = COLORS.indexOf(nameColor);
        return COLORS.get((i + 1) % COLORS.size());
    }

    Ability copy() {
        Ability c = new Ability(mechanic);
        c.id = id;
        c.material = material;
        c.name = name;
        c.nameColor = nameColor;
        c.lore = new ArrayList<>(lore);
        c.manaCost = manaCost;
        c.cooldown = cooldown;
        c.level = level;
        c.power = power;
        c.range = range;
        c.duration = duration;
        c.percent = percent;
        c.rev = rev;
        return c;
    }

    void save(ConfigurationSection s) {
        s.set("mechanic", mechanic.name());
        s.set("material", material.name());
        s.set("name", name);
        s.set("name-color", nameColor);
        s.set("lore", lore);
        s.set("mana-cost", manaCost);
        s.set("cooldown", cooldown);
        s.set("level", level);
        for (Mechanic.Param param : mechanic.params) {
            s.set(param.stat().name().toLowerCase(Locale.ROOT), get(param.stat()));
        }
        s.set("rev", rev);
    }

    static Ability load(String id, ConfigurationSection s) {
        Mechanic mechanic;
        try {
            mechanic = Mechanic.valueOf(s.getString("mechanic", "DASH").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
        Ability a = new Ability(mechanic);
        a.id = id;
        Material material = Material.matchMaterial(s.getString("material", mechanic.icon.name()));
        a.material = material == null || !material.isItem() || material.isAir() ? mechanic.icon : material;
        a.name = s.getString("name", mechanic.label);
        a.nameColor = s.getString("name-color", "light_purple");
        a.lore = new ArrayList<>(s.getStringList("lore"));
        a.manaCost = Math.max(0, s.getDouble("mana-cost", mechanic.defaultMana));
        a.cooldown = Math.max(0, s.getDouble("cooldown", mechanic.defaultCooldown));
        a.level = Math.max(1, s.getInt("level", 1));
        for (Mechanic.Param param : mechanic.params) {
            double value = s.getDouble(param.stat().name().toLowerCase(Locale.ROOT), param.def());
            a.set(param.stat(), Math.max(param.min(), Math.min(param.max(), value)));
        }
        a.rev = s.getInt("rev", 0);
        return a;
    }
}
