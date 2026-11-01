package com.dathis.rpg.modules.mob;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Шаблон кастомного моба. Он же черновик в редакторе. */
public final class CustomMob {

    /** Ранг моба. От него зависит вид названия. */
    public enum Tier {
        NORMAL("Обычный"),
        ELITE("Элитный"),
        BOSS("Босс");

        final String label;

        Tier(String label) {
            this.label = label;
        }

        Tier next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    String id;               // null, пока не сохранён
    EntityType type;
    String name;
    Tier tier = Tier.NORMAL;
    double health = 20;
    double damageMin = 3;
    double damageMax = 5;
    boolean vanillaDrops;    // оставлять ли обычный лут моба
    List<MobDrop> drops = new ArrayList<>();

    CustomMob(EntityType type, String name) {
        this.type = type;
        this.name = name;
    }

    /** Название с цветом ранга: обычный — белое, элитный — красное, босс — красное жирное. */
    Component displayName() {
        Component text = Component.text(name).decoration(TextDecoration.ITALIC, false);
        return switch (tier) {
            case NORMAL -> text.color(NamedTextColor.WHITE);
            case ELITE -> text.color(NamedTextColor.RED);
            case BOSS -> text.color(NamedTextColor.RED).decoration(TextDecoration.BOLD, true);
        };
    }

    CustomMob copy() {
        CustomMob c = new CustomMob(type, name);
        c.id = id;
        c.tier = tier;
        c.health = health;
        c.damageMin = damageMin;
        c.damageMax = damageMax;
        c.vanillaDrops = vanillaDrops;
        c.drops = new ArrayList<>();
        for (MobDrop drop : drops) {
            c.drops.add(drop.copy());
        }
        return c;
    }

    void save(ConfigurationSection s) {
        s.set("type", type.name());
        s.set("name", name);
        s.set("tier", tier.name());
        s.set("health", health);
        s.set("damage-min", damageMin);
        s.set("damage-max", damageMax);
        s.set("vanilla-drops", vanillaDrops);
        List<Map<String, Object>> list = new ArrayList<>();
        for (MobDrop drop : drops) {
            list.add(drop.serialize());
        }
        s.set("drops", list);
    }

    static CustomMob load(String id, ConfigurationSection s) {
        EntityType type;
        try {
            type = EntityType.valueOf(s.getString("type", "ZOMBIE"));
        } catch (IllegalArgumentException e) {
            type = EntityType.ZOMBIE;
        }
        CustomMob mob = new CustomMob(type, s.getString("name", id));
        mob.id = id;
        try {
            mob.tier = Tier.valueOf(s.getString("tier", "NORMAL"));
        } catch (IllegalArgumentException ignored) {
            mob.tier = Tier.NORMAL;
        }
        mob.health = s.getDouble("health", 20);
        mob.damageMin = s.getDouble("damage-min", 3);
        mob.damageMax = s.getDouble("damage-max", 5);
        mob.vanillaDrops = s.getBoolean("vanilla-drops", false);
        for (Map<?, ?> raw : s.getMapList("drops")) {
            MobDrop drop = MobDrop.deserialize(raw);
            if (drop != null) {
                mob.drops.add(drop);
            }
        }
        return mob;
    }
}
