package com.dathis.rpg.modules.shop;

import com.dathis.rpg.core.module.AbstractModule;

/**
 * Магазины.
 *
 * План:
 * - магазин привязан к NPC и работает как обмен «предмет(ы) → предмет»;
 * - админ настраивает сделки через меню в игре.
 */
public final class ShopModule extends AbstractModule {

    public ShopModule() {
        super("shops", "Магазины");
    }

    @Override
    protected void onEnable() {
        // TODO: реализовать модуль
    }
}
