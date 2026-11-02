package com.dathis.rpg.modules.ability;

import com.dathis.rpg.core.gui.Icons;
import com.dathis.rpg.core.gui.Menu;
import com.dathis.rpg.core.input.Numbers;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.List;

/**
 * Окно «Способности» (/abilities, кнопка в /menu): все руны в инвентаре игрока. Нажатие кладёт руну
 * в хотбар, откуда её применяют. На Bedrock это проще, чем перетаскивать предметы в инвентаре.
 */
final class PlayerAbilitiesMenu extends Menu {

    private static final int PER_PAGE = 45;

    private final AbilityModule module;
    private final Player owner;

    PlayerAbilitiesMenu(AbilityModule module, Player owner) {
        super(6, Icons.text("<dark_gray>Способности"));
        this.module = module;
        this.owner = owner;
    }

    @Override
    protected void render() {
        PlayerInventory inventory = owner.getInventory();
        List<Integer> slots = new ArrayList<>();
        for (int slot = 0; slot <= 40; slot++) {
            if (module.registry().isRune(inventory.getItem(slot))) {
                slots.add(slot);
            }
        }
        for (int i = 0; i < PER_PAGE && i < slots.size(); i++) {
            int slot = slots.get(i);
            ItemStack rune = inventory.getItem(slot);
            String id = module.registry().idOf(rune);
            Ability ability = module.registry().get(id);
            List<String> hint = new ArrayList<>();
            hint.add("");
            long left = module.cooldownLeft(owner, id);
            if (ability == null) {
                hint.add("<red>Эта руна больше не работает");
            } else if (left > 0) {
                hint.add("<yellow>Перезарядка: ещё " + Numbers.format(Math.ceil(left / 1000.0)) + " сек");
            } else if (!module.plugin().levels().meets(owner, ability.level)) {
                hint.add("<red>Нужен уровень " + ability.level);
            } else {
                hint.add("<green>Готова");
            }
            if (slot < 9) {
                hint.add("<gray>В хотбаре, ячейка " + (slot + 1));
            } else {
                hint.add("<green>Нажмите, чтобы положить в хотбар");
            }
            ItemStack icon = Icons.withHint(rune, hint.toArray(String[]::new));
            icon.setAmount(1);
            set(i, icon, p -> toHotbar(p, slot, id));
        }
        for (int i = PER_PAGE; i < size(); i++) {
            set(i, Icons.filler());
        }
        if (slots.isEmpty()) {
            set(22, Icons.of(Material.BARRIER, "<gray>У вас пока нет рун",
                    "<gray>Руны способностей дают за квесты,",
                    "<gray>их продают торговцы"));
        }
        String mana = module.plugin().mana().isActive()
                ? Numbers.format(Math.floor(module.plugin().mana().mana(owner))) + " / "
                + Numbers.format(Math.floor(module.plugin().mana().max(owner)))
                : "не нужна";
        set(48, Icons.of(Material.LAPIS_LAZULI, "<blue>Мана: <white>" + mana,
                "<gray>Восстанавливается со временем"));
        set(49, Icons.of(Material.BOOK, "<yellow>Как применять",
                "<gray>Положите руну в хотбар, возьмите",
                "<gray>её в руку и нажмите ПКМ.",
                "<gray>На телефоне: нажмите по экрану.",
                "<gray>Высасывание и обмен здоровьем",
                "<gray>бьют по тому, на кого вы смотрите.",
                "",
                "<gray>Число на руне — секунды перезарядки."));
        if (module.plugin().modules().isEnabled("menu")) {
            set(45, Icons.of(Material.ARROW, "<gray>В меню"), p -> p.performCommand("menu"));
        }
        set(53, Icons.of(Material.BARRIER, "<red>Закрыть"), Player::closeInventory);
    }

    /** Переносит руну в хотбар: в свободную ячейку или на место предмета в руке. */
    private void toHotbar(Player player, int from, String id) {
        PlayerInventory inventory = player.getInventory();
        ItemStack rune = inventory.getItem(from);
        if (!id.equals(module.registry().idOf(rune))) {
            redraw(); // инвентарь поменялся, пока окно было открыто
            return;
        }
        if (from < 9) {
            inventory.setHeldItemSlot(from);
            redraw();
            return;
        }
        int compass = module.compassSlot();
        int target = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack item = inventory.getItem(i);
            if (i != compass && (item == null || item.isEmpty())) {
                target = i;
                break;
            }
        }
        if (target < 0) {
            target = inventory.getHeldItemSlot() != compass ? inventory.getHeldItemSlot() : (compass == 0 ? 1 : 0);
        }
        ItemStack previous = inventory.getItem(target);
        inventory.setItem(target, rune);
        inventory.setItem(from, previous);
        inventory.setHeldItemSlot(target);
        module.plugin().messages().send(player, "<green>Руна в хотбаре, ячейка " + (target + 1) + ".");
        redraw();
    }
}
