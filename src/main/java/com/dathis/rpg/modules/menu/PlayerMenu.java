package com.dathis.rpg.modules.menu;

import com.dathis.rpg.core.gui.Icons;
import com.dathis.rpg.core.gui.Menu;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Главное меню игрока (/menu, компас в хотбаре). Кнопки ставятся по центру среднего ряда;
 * кнопки выключенных модулей не показываются. Квесты и участки открываются их командами.
 */
final class PlayerMenu extends Menu {

    private record Button(ItemStack icon, Consumer<Player> action) {
    }

    private final MenuModule module;

    PlayerMenu(MenuModule module) {
        super(3, Icons.text("<dark_gray>Меню"));
        this.module = module;
    }

    @Override
    protected void render() {
        for (int i = 0; i < size(); i++) {
            set(i, Icons.filler());
        }
        List<Button> buttons = new ArrayList<>();
        buttons.add(new Button(Icons.of(Material.PLAYER_HEAD, "<gold>Персонаж",
                "<gray>Уровень, опыт, ХП, броня,",
                "<gray>мана и оружие в руке"),
                p -> new StatsMenu(module, p, true).open(p)));
        if (module.plugin().modules().isEnabled("abilities")) {
            buttons.add(new Button(Icons.of(Material.ENDER_EYE, "<light_purple>Способности",
                    "<gray>Ваши руны: положить в хотбар,", "<gray>перезарядка, мана"),
                    p -> p.performCommand("abilities")));
        }
        if (module.plugin().modules().isEnabled("quests")) {
            buttons.add(new Button(Icons.of(Material.WRITABLE_BOOK, "<yellow>Квесты",
                    "<gray>Журнал взятых и выполненных квестов,", "<gray>задания дня на доске"),
                    p -> p.performCommand("quests")));
        }
        if (module.plugin().modules().isEnabled("regions")) {
            buttons.add(new Button(Icons.of(Material.OAK_DOOR, "<green>Участки",
                    "<gray>Мои участки и помощники"),
                    p -> p.performCommand("plot")));
        }
        buttons.add(new Button(Icons.of(Material.COMPARATOR, "<aqua>Настройки",
                "<gray>Боковая панель, компас-меню"),
                p -> new SettingsMenu(module, p).open(p)));

        // По центру второго ряда через одну клетку: 4 кнопки — 10, 12, 14, 16; 5 кнопок — 9, 11, 13, 15, 17
        int start = 13 - (buttons.size() - 1);
        for (int i = 0; i < buttons.size(); i++) {
            Button button = buttons.get(i);
            set(start + i * 2, button.icon(), button.action());
        }
        set(22, Icons.of(Material.BARRIER, "<red>Закрыть"), Player::closeInventory);
    }
}
