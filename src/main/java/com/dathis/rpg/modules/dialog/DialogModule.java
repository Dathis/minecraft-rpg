package com.dathis.rpg.modules.dialog;

import com.dathis.rpg.core.module.AbstractModule;

/**
 * Диалоги.
 *
 * План:
 * - дерево реплик с вариантами ответа;
 * - выбор ответа через сундучное меню (работает в Java и Bedrock), позже — формы Floodgate для Bedrock.
 */
public final class DialogModule extends AbstractModule {

    public DialogModule() {
        super("dialogs", "Диалоги");
    }

    @Override
    protected void onEnable() {
        // TODO: реализовать модуль
    }
}
