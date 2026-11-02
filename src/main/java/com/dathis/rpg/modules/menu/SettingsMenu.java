package com.dathis.rpg.modules.menu;

import com.dathis.rpg.core.gui.Icons;
import com.dathis.rpg.core.gui.Menu;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Настройки игрока: боковая панель и компас-меню. Каждая кнопка — переключатель одним нажатием. */
final class SettingsMenu extends Menu {

    private final MenuModule module;
    private final Player viewer;

    SettingsMenu(MenuModule module, Player viewer) {
        super(3, Icons.text("<dark_gray>Настройки"));
        this.module = module;
        this.viewer = viewer;
    }

    @Override
    protected void render() {
        for (int i = 0; i < size(); i++) {
            set(i, Icons.filler());
        }
        if (module.plugin().sidebar().isAvailable()) {
            boolean shown = module.plugin().sidebar().isShownTo(viewer);
            set(11, Icons.of(shown ? Material.LIME_DYE : Material.GRAY_DYE,
                            "<yellow>Боковая панель: " + (shown ? "<green>вкл" : "<red>выкл"),
                            "<gray>Уровень, ХП, мана и текущий",
                            "<gray>квест справа экрана",
                            "", "<green>Нажмите, чтобы " + (shown ? "скрыть" : "показать")),
                    p -> {
                        module.plugin().sidebar().setShownTo(p, !module.plugin().sidebar().isShownTo(p));
                        redraw();
                    });
        } else {
            set(11, Icons.of(Material.GRAY_DYE, "<gray>Боковая панель",
                    "<gray>Выключена администратором"));
        }

        if (module.compassEnabled()) {
            boolean wanted = module.compass().wanted(viewer);
            set(15, Icons.of(wanted ? Material.COMPASS : Material.GRAY_DYE,
                            "<yellow>Компас-меню: " + (wanted ? "<green>вкл" : "<red>выкл"),
                            "<gray>Компас в " + (module.compassSlot() + 1) + "-м слоте хотбара",
                            "<gray>открывает это меню.",
                            "<gray>Без него меню открывается командой /menu",
                            "", "<green>Нажмите, чтобы " + (wanted ? "убрать" : "вернуть")),
                    p -> {
                        module.compass().setWanted(p, !module.compass().wanted(p));
                        redraw();
                    });
        }

        set(22, Icons.of(Material.ARROW, "<gray>Назад"), module::openMain);
    }
}
