package com.dathis.rpg.modules.weapon;

import com.dathis.rpg.core.module.AbstractModule;

/**
 * Кастомное оружие.
 *
 * План:
 * - шаблон на основе ванильного предмета: название, описание, урон, скорость атаки;
 * - перки (поджог, вампиризм, замедление и т.д.), хранятся в PersistentDataContainer предмета;
 * - выдача через /rpg weapon give.
 */
public final class WeaponModule extends AbstractModule {

    public WeaponModule() {
        super("weapons", "Кастомное оружие");
    }

    @Override
    protected void onEnable() {
        // TODO: реализовать модуль
    }
}
