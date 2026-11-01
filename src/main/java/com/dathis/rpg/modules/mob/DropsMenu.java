package com.dathis.rpg.modules.mob;

import com.dathis.rpg.core.gui.Icons;
import com.dathis.rpg.core.gui.Menu;
import com.dathis.rpg.core.input.Numbers;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.OptionalDouble;

/**
 * Лут моба. Добавить предмет: взять его в руку (подходят и предметы из /create-item) и нажать «Добавить».
 * Нажатие на предмет в списке меняет шанс, «Удалить» убирает его.
 */
final class DropsMenu extends Menu {

    private static final int MAX_DROPS = 36;

    private final MobModule module;
    private final MobEditorMenu editor;
    private boolean deleteMode;

    DropsMenu(MobModule module, MobEditorMenu editor) {
        super(6, Icons.text("<dark_gray>Лут моба"));
        this.module = module;
        this.editor = editor;
    }

    @Override
    protected void render() {
        var drops = editor.draft().drops;
        for (int i = 0; i < MAX_DROPS && i < drops.size(); i++) {
            MobDrop drop = drops.get(i);
            ItemStack icon = Icons.withHint(drop.item, "",
                    "<gold>Шанс: <white>" + Numbers.format(drop.chance) + "%",
                    deleteMode ? "<red>Нажмите, чтобы удалить" : "<green>Нажмите, чтобы изменить шанс");
            set(i, icon, p -> {
                if (deleteMode) {
                    drops.remove(drop);
                    redraw();
                } else {
                    askChance(p, drop);
                }
            });
        }
        for (int i = MAX_DROPS; i < size(); i++) {
            set(i, Icons.filler());
        }
        set(45, Icons.of(Material.ARROW, "<gray>К редактору"), editor::open);
        set(48, Icons.of(Material.HOPPER, "<green>Добавить предмет из руки",
                "<gray>Возьмите предмет в основную руку.",
                "<gray>Количество в стаке = сколько выпадет.",
                "<gray>Подходят предметы из /create-item"), this::addFromHand);
        set(50, Icons.of(deleteMode ? Material.LIME_DYE : Material.LAVA_BUCKET,
                deleteMode ? "<green>Готово" : "<red>Удалить предметы",
                deleteMode ? "<gray>Выйти из режима удаления" : "<gray>Включить режим удаления"), p -> {
            deleteMode = !deleteMode;
            redraw();
        });
    }

    private void addFromHand(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.isEmpty()) {
            module.plugin().messages().send(player, "<red>Возьмите предмет в основную руку.</red>");
            return;
        }
        if (editor.draft().drops.size() >= MAX_DROPS) {
            module.plugin().messages().send(player, "<red>Максимум " + MAX_DROPS + " предметов в луте.</red>");
            return;
        }
        MobDrop drop = new MobDrop(hand.clone(), 100);
        editor.draft().drops.add(drop);
        askChance(player, drop);
    }

    private void askChance(Player player, MobDrop drop) {
        module.plugin().chatInput().ask(player, "<yellow>Шанс выпадения в процентах (0.1-100):", text -> {
            OptionalDouble value = Numbers.parse(text);
            if (value.isPresent() && value.getAsDouble() > 0 && value.getAsDouble() <= 100) {
                drop.chance = value.getAsDouble();
            } else {
                module.plugin().messages().send(player, "<red>Нужно число больше 0 и не больше 100.</red>");
            }
            open(player);
        }, () -> open(player));
    }
}
