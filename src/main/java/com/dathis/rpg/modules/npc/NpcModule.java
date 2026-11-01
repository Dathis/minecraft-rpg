package com.dathis.rpg.modules.npc;

import com.dathis.rpg.core.module.AbstractModule;

/**
 * NPC.
 *
 * План:
 * - админ создаёт NPC в игре (/rpg npc create), задаёт имя, скин/тип сущности, роль;
 * - роли: собеседник (диалог), квестодатель, торговец;
 * - NPC — обычные сущности сервера, поэтому видны и в Java, и в Bedrock.
 */
public final class NpcModule extends AbstractModule {

    public NpcModule() {
        super("npc", "NPC");
    }

    @Override
    protected void onEnable() {
        // TODO: реализовать модуль
    }
}
