package com.dathis.rpg.modules.quest;

import com.dathis.rpg.core.module.AbstractModule;

/**
 * Квесты.
 *
 * План:
 * - цели: убить мобов, принести предметы, поговорить с NPC, дойти до точки;
 * - награды: предметы, опыт, деньги;
 * - прогресс игроков сохраняется.
 */
public final class QuestModule extends AbstractModule {

    public QuestModule() {
        super("quests", "Квесты");
    }

    @Override
    protected void onEnable() {
        // TODO: реализовать модуль
    }
}
