package com.dathis.rpg.modules.tree;

import com.dathis.rpg.core.module.AbstractModule;

/**
 * Автопосадка деревьев.
 *
 * План:
 * - при срубе нижнего блока ствола на его месте сразу появляется саженец нужной породы;
 * - список пород и задержка настраиваются админом.
 */
public final class TreeReplantModule extends AbstractModule {

    public TreeReplantModule() {
        super("tree-replant", "Автопосадка деревьев");
    }

    @Override
    protected void onEnable() {
        // TODO: реализовать модуль
    }
}
