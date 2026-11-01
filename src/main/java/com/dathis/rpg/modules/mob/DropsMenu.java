package com.dathis.rpg.modules.mob;

import com.dathis.rpg.core.gui.Icons;
import com.dathis.rpg.core.gui.Menu;
import com.dathis.rpg.core.input.Numbers;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.OptionalDouble;

/**
 * Лут моба. Положите предмет из инвентаря в пустой слот (или shift-клик по нему) — он добавится,
 * и плагин спросит шанс. Количество в стаке = сколько выпадет. Предмет у вас не забирается.
 * Нажатие на предмет в списке меняет шанс, в режиме удаления — удаляет.
 */
final class DropsMenu extends Menu {

    private static final int MAX_DROPS = 45;

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
        for (int i = 0; i < MAX_DROPS; i++) {
            if (i < drops.size()) {
                MobDrop drop = drops.get(i);
                ItemStack icon = Icons.withHint(drop.item, "",
                        "<gold>Шанс: <white>" + Numbers.format(drop.chance) + "%",
                        deleteMode ? "<red>Нажмите, чтобы удалить" : "<green>Нажмите, чтобы изменить шанс",
                        deleteMode ? "" : "<gray>Положите другой предмет, чтобы заменить");
                setInput(i, icon, (p, cursor) -> {
                    if (deleteMode) {
                        drops.remove(drop);
                        redraw();
                    } else if (cursor != null) {
                        drop.item = cursor;
                        askChance(p, drop);
                    } else {
                        askChance(p, drop);
                    }
                });
            } else {
                setInput(i, null, (p, cursor) -> {
                    if (cursor != null) {
                        add(p, cursor);
                    }
                });
            }
        }
        for (int i = MAX_DROPS; i < size(); i++) {
            set(i, Icons.filler());
        }
        set(45, Icons.of(Material.ARROW, "<gray>К редактору"), editor::open);
        set(49, Icons.of(Material.BOOK, "<yellow>Как добавить лут",
                "<gray>Возьмите предмет из своего инвентаря",
                "<gray>и положите в пустой слот выше,",
                "<gray>или сделайте shift-клик по нему.",
                "<gray>Количество в стаке = сколько выпадет.",
                "<gray>Подходят предметы из /create-item."));
        set(53, Icons.of(deleteMode ? Material.LIME_DYE : Material.LAVA_BUCKET,
                deleteMode ? "<green>Готово" : "<red>Удалить предметы",
                deleteMode ? "<gray>Выйти из режима удаления" : "<gray>Включить режим удаления"), p -> {
            deleteMode = !deleteMode;
            redraw();
        });
    }

    @Override
    protected void quickAdd(Player player, ItemStack item) {
        add(player, item);
    }

    private void add(Player player, ItemStack item) {
        if (editor.draft().drops.size() >= MAX_DROPS) {
            module.plugin().messages().send(player, "<red>Максимум " + MAX_DROPS + " предметов в луте.</red>");
            return;
        }
        MobDrop drop = new MobDrop(item.clone(), 100);
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
