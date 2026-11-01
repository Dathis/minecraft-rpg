package com.dathis.rpg.core.module;

import com.dathis.rpg.RpgPlugin;

/** Отдельная часть плагина (зоны, NPC, мобы...), которую можно включить или выключить в config.yml. */
public interface RpgModule {

    /** Ключ модуля в config.yml (modules.&lt;id&gt;) и имя файла данных. */
    String id();

    /** Человекочитаемое название для /rpg modules. */
    String displayName();

    void enable(RpgPlugin plugin);

    void disable();
}
