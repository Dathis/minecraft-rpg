package com.dathis.rpg.modules.region;

import com.dathis.rpg.core.module.AbstractModule;

/**
 * Зоны и защита мира.
 *
 * План:
 * - весь мир по умолчанию защищён: игроки не ставят и не ломают блоки;
 * - админ выделяет зоны (/rpg region create), где строить и ломать можно;
 * - флаги зоны: build, break, pvp, mob-spawn.
 */
public final class RegionModule extends AbstractModule {

    public RegionModule() {
        super("regions", "Зоны и защита мира");
    }

    @Override
    protected void onEnable() {
        // TODO: реализовать модуль
    }
}
