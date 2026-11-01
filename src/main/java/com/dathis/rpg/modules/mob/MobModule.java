package com.dathis.rpg.modules.mob;

import com.dathis.rpg.core.module.AbstractModule;

/**
 * Кастомные мобы.
 *
 * План:
 * - шаблон на основе ванильного моба: тип, имя, ХП, урон, снаряжение, таблица лута;
 * - точки и правила спавна задаёт админ.
 */
public final class MobModule extends AbstractModule {

    public MobModule() {
        super("mobs", "Кастомные мобы");
    }

    @Override
    protected void onEnable() {
        // TODO: реализовать модуль
    }
}
