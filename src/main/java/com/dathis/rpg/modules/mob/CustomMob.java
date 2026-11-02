package com.dathis.rpg.modules.mob;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.EntityType;

import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.EnumMap;
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
    double health = 20;      // своё ХП, без предела 1024 (см. HealthService)
    double armor;            // очки брони, снижают урон по формуле из config.yml (health.*)
    double damageMin = 3;
    double damageMax = 5;
    boolean vanillaDrops;    // оставлять ли обычный лут моба
    /** Уровень моба (1..levels.max-level). От него зависит опыт и штраф за разницу уровней. */
    int level = 1;
    /** Опыт за убийство. -1 — считать автоматически по уровню и рангу (см. MobModule.xpFor). */
    long xp = -1;
    /** Враждебный — сам нападает на игрока, которого видит. Иначе только в ответ на удар. */
    boolean hostile;
    List<MobDrop> drops = new ArrayList<>();
    /** Снаряжение: ровно то, с чем моб появится. Пустые слоты остаются пустыми. */
    Map<EquipmentSlot, ItemStack> equipment = new EnumMap<>(EquipmentSlot.class);

    CustomMob(EntityType type, String name) {
        this.type = type;
        this.name = name;
        this.hostile = defaultHostile(type);
    }

    /** По умолчанию враждебны монстры (зомби, скелеты и т.д.), остальные — нет. */
    static boolean defaultHostile(EntityType type) {
        Class<?> clazz = type.getEntityClass();
        return clazz != null && Enemy.class.isAssignableFrom(clazz);
    }

    /** Название с цветом ранга: обычный — белое, элитный — красное, босс — красное жирное. */
    Component displayName() {
        Component text = Component.text(name).decoration(TextDecoration.ITALIC, false);
        text = switch (tier) {
            case NORMAL -> text.color(NamedTextColor.WHITE);
            case ELITE -> text.color(NamedTextColor.RED);
            case BOSS -> text.color(NamedTextColor.RED).decoration(TextDecoration.BOLD, true);
        };
        // Уровень перед названием: «[5] Разбойник»
        return Component.text("[" + level + "] ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, false).append(text);
    }

    CustomMob copy() {
        CustomMob c = new CustomMob(type, name);
        c.id = id;
        c.tier = tier;
        c.health = health;
        c.armor = armor;
        c.damageMin = damageMin;
        c.damageMax = damageMax;
        c.vanillaDrops = vanillaDrops;
        c.level = level;
        c.xp = xp;
        c.hostile = hostile;
        c.drops = new ArrayList<>();
        for (MobDrop drop : drops) {
            c.drops.add(drop.copy());
        }
        c.equipment = new EnumMap<>(EquipmentSlot.class);
        equipment.forEach((slot, item) -> c.equipment.put(slot, item.clone()));
        return c;
    }

    void save(ConfigurationSection s) {
        s.set("type", type.name());
        s.set("name", name);
        s.set("tier", tier.name());
        s.set("health", health);
        s.set("armor", armor);
        s.set("damage-min", damageMin);
        s.set("damage-max", damageMax);
        s.set("vanilla-drops", vanillaDrops);
        s.set("level", level);
        s.set("xp", xp);
        s.set("hostile", hostile);
        List<Map<String, Object>> list = new ArrayList<>();
        for (MobDrop drop : drops) {
            list.add(drop.serialize());
        }
        s.set("drops", list);
        s.set("equipment", null);
        equipment.forEach((slot, item) -> s.set("equipment." + slot.name(), MobDrop.encode(item)));
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
        mob.armor = s.getDouble("armor", 0);
        mob.damageMin = s.getDouble("damage-min", 3);
        mob.damageMax = s.getDouble("damage-max", 5);
        mob.vanillaDrops = s.getBoolean("vanilla-drops", false);
        mob.level = Math.max(1, s.getInt("level", 1));
        mob.xp = s.getLong("xp", -1);
        mob.hostile = s.getBoolean("hostile", defaultHostile(type));
        for (Map<?, ?> raw : s.getMapList("drops")) {
            MobDrop drop = MobDrop.deserialize(raw);
            if (drop != null) {
                mob.drops.add(drop);
            }
        }
        ConfigurationSection eq = s.getConfigurationSection("equipment");
        if (eq != null) {
            for (String key : eq.getKeys(false)) {
                try {
                    ItemStack item = MobDrop.decode(eq.getString(key));
                    if (item != null) {
                        mob.equipment.put(EquipmentSlot.valueOf(key), item);
                    }
                } catch (IllegalArgumentException ignored) {
                    // неизвестный слот — пропускаем
                }
            }
        }
        return mob;
    }
}
