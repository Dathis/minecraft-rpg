package com.dathis.rpg.modules.ability;

import org.bukkit.Material;

import java.util.List;

/**
 * Готовые механики способностей. Админ выбирает механику в /create-ability и настраивает её числа.
 * У каждой механики свой набор чисел ({@link Param}): подписи, пределы и значения по умолчанию.
 */
enum Mechanic {

    FLIGHT("Короткий полёт", Material.FEATHER,
            "Несколько секунд свободного полёта, потом плавное падение без урона.",
            30, 20,
            List.of(new Param(Stat.DURATION, "Длительность полёта", "сек", 1, 60, 4, Material.CLOCK,
                    "Сколько секунд можно летать"))),

    LEAP("Длинный прыжок", Material.RABBIT_FOOT,
            "Мощный прыжок вперёд и вверх по направлению взгляда. Урона от падения нет.",
            15, 6,
            List.of(new Param(Stat.POWER, "Сила прыжка", "", 0.5, 4, 1.5, Material.SLIME_BALL,
                    "1 — около 6 блоков, 2 — около 12"))),

    DASH("Рывок", Material.SUGAR,
            "Быстрый рывок вперёд над землёй.",
            10, 5,
            List.of(new Param(Stat.RANGE, "Дальность рывка", "блоков", 2, 20, 8, Material.LEATHER_BOOTS,
                    "Примерно на столько блоков"))),

    BLINK("Телепортация", Material.ENDER_EYE,
            "Мгновенно переносит туда, куда смотрит игрок. Сквозь стены не проходит.",
            20, 8,
            List.of(new Param(Stat.RANGE, "Дальность", "блоков", 2, 50, 10, Material.SPYGLASS,
                    "Самое дальнее место для телепорта"))),

    LIFE_DRAIN("Высасывание жизни", Material.FERMENTED_SPIDER_EYE,
            "Луч к цели: каждую секунду урон, часть урона лечит заклинателя.",
            25, 12,
            List.of(new Param(Stat.POWER, "Урон в секунду", "", 0.5, 100_000, 4, Material.IRON_SWORD,
                    "Магический урон, броня его не снижает"),
                    new Param(Stat.DURATION, "Длительность", "сек", 1, 20, 3, Material.CLOCK,
                            "Сколько секунд держится луч"),
                    new Param(Stat.RANGE, "Дальность", "блоков", 2, 40, 12, Material.SPYGLASS,
                            "Цель должна быть в прямой видимости"),
                    new Param(Stat.PERCENT, "Лечение", "% урона", 0, 100, 50, Material.GLISTERING_MELON_SLICE,
                            "Какая доля урона лечит заклинателя"))),

    HEALTH_SWAP("Обмен здоровьем", Material.GOLDEN_APPLE,
            "Меняет местами долю ХП заклинателя и цели. Не действует на боссов.",
            40, 60,
            List.of(new Param(Stat.RANGE, "Дальность", "блоков", 2, 40, 10, Material.SPYGLASS,
                    "Цель должна быть в прямой видимости"),
                    new Param(Stat.PERCENT, "Предел обмена", "%", 1, 100, 50, Material.COMPARATOR,
                            "На сколько % максимума ХП можно сдвинуть за раз")));

    /** Какие числа есть у способности. Механика использует только свои. */
    enum Stat { POWER, RANGE, DURATION, PERCENT }

    /** Настраиваемое число механики: подпись, единица, пределы, значение по умолчанию, иконка, подсказка. */
    record Param(Stat stat, String label, String unit, double min, double max, double def, Material icon, String hint) {
    }

    final String label;
    final Material icon;
    final String description;
    final double defaultMana;
    final double defaultCooldown;
    final List<Param> params;

    Mechanic(String label, Material icon, String description, double defaultMana, double defaultCooldown,
             List<Param> params) {
        this.label = label;
        this.icon = icon;
        this.description = description;
        this.defaultMana = defaultMana;
        this.defaultCooldown = defaultCooldown;
        this.params = params;
    }

    /** Нужна ли механике цель (существо перед игроком). */
    boolean needsTarget() {
        return this == LIFE_DRAIN || this == HEALTH_SWAP;
    }

    Param param(Stat stat) {
        for (Param param : params) {
            if (param.stat() == stat) {
                return param;
            }
        }
        return null;
    }
}
