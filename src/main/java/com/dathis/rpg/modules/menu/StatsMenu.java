package com.dathis.rpg.modules.menu;

import com.dathis.rpg.core.combat.HealthService;
import com.dathis.rpg.core.gui.Icons;
import com.dathis.rpg.core.gui.Menu;
import com.dathis.rpg.core.input.Numbers;
import com.dathis.rpg.core.progression.LevelService;
import com.dathis.rpg.core.progression.ManaService;
import com.dathis.rpg.modules.item.ItemModule;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Окно персонажа (/stats, кнопка «Персонаж» в /menu): уровень и опыт, ХП, броня и снижение урона,
 * мана, оружие в руке. Только просмотр; кнопка «Обновить» перерисовывает числа.
 */
final class StatsMenu extends Menu {

    private final MenuModule module;
    /** Чьи статы показываем (админ может смотреть чужие). */
    private final Player target;
    private final boolean backToMenu;

    StatsMenu(MenuModule module, Player target, boolean backToMenu) {
        super(4, Icons.text("<dark_gray>Персонаж: " + target.getName()));
        this.module = module;
        this.target = target;
        this.backToMenu = backToMenu;
    }

    @Override
    protected void render() {
        for (int i = 0; i < size(); i++) {
            set(i, Icons.filler());
        }
        set(4, head());
        set(19, health());
        set(20, armor());
        ManaService mana = module.plugin().mana();
        if (mana.isActive()) {
            set(21, Icons.of(Material.LAPIS_LAZULI, "<blue>Мана",
                    "<gray>Сейчас: <blue>" + Numbers.format(Math.floor(mana.mana(target)))
                            + "<gray> / <blue>" + Numbers.format(Math.floor(mana.max(target))),
                    "<gray>Восстановление: <white>" + Numbers.format(mana.regen(target)) + " в секунду",
                    "",
                    "<dark_gray>Тратится на способности"));
        }
        set(23, weapon());

        if (backToMenu) {
            set(27, Icons.of(Material.ARROW, "<gray>Назад"), module::openMain);
        }
        set(31, Icons.of(Material.CLOCK, "<yellow>Обновить"), p -> redraw());
        set(35, Icons.of(Material.BARRIER, "<red>Закрыть"), Player::closeInventory);
    }

    private ItemStack head() {
        List<String> lore = new ArrayList<>();
        LevelService levels = module.plugin().levels();
        if (levels.isActive()) {
            int level = levels.level(target);
            lore.add("<gray>Уровень: <gold>" + level + "<gray> из " + levels.maxLevel());
            if (level < levels.maxLevel()) {
                lore.add("<gray>Опыт: <white>" + levels.xp(target) + " / " + levels.xpToNext(level)
                        + " <dark_gray>(" + (int) Math.floor(levels.progress(target) * 100) + "%)");
            } else {
                lore.add("<gray>Опыт: <white>максимальный уровень");
            }
        }
        ItemStack stack = Icons.of(Material.PLAYER_HEAD, "<gold>" + target.getName(), lore);
        stack.editMeta(SkullMeta.class, meta -> meta.setOwningPlayer(target));
        return stack;
    }

    private ItemStack health() {
        HealthService health = module.plugin().health();
        double current = Math.ceil(health.health(target));
        double max = Math.ceil(health.maxHealth(target));
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Сейчас: <red>" + Numbers.format(current) + "<gray> / <red>" + Numbers.format(max));
        if (health.isActive()) {
            lore.add("<gray>Базовое: <white>" + Numbers.format(health.playerBase()));
        }
        ItemModule items = module.items();
        double regen = items == null ? 0 : items.armorRegeneration(target);
        if (regen > 0) {
            lore.add("<gray>Регенерация: <green>+" + Numbers.format(regen) + " в секунду");
            lore.add("<dark_gray>(не работает 5 с после урона)");
        }
        return Icons.of(Material.GOLDEN_APPLE, "<red>Здоровье", lore);
    }

    private ItemStack armor() {
        HealthService health = module.plugin().health();
        double armor = health.armor(target);
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Очки брони: <aqua>" + Numbers.format(Math.round(armor)));
        if (health.isActive()) {
            lore.add("<gray>Снижение урона: <white>" + Math.round(health.armorReduction(armor) * 100) + "%");
        }
        return Icons.of(Material.IRON_CHESTPLATE, "<aqua>Броня", lore);
    }

    private ItemStack weapon() {
        ItemStack hand = target.getInventory().getItemInMainHand();
        ItemModule items = module.items();
        ItemModule.WeaponInfo info = items == null || hand.isEmpty() ? null : items.weapon(hand);
        if (info == null) {
            return Icons.of(Material.WOODEN_SWORD, "<gray>Оружие в руке",
                    "<gray>В основной руке нет оружия",
                    "<gray>из кастомных предметов");
        }
        List<String> lore = new ArrayList<>();
        lore.add("<white>" + MiniMessage.miniMessage().escapeTags(info.name()));
        lore.add("<gray>Урон: <red>" + Numbers.format(info.damageMin()) + " - " + Numbers.format(info.damageMax()));
        if (info.critChance() > 0) {
            lore.add("<gray>Крит. удар: <yellow>" + Numbers.format(info.critChance()) + "%"
                    + "<gray> (урон x" + Numbers.format(info.critMultiplier()) + ")");
        }
        if (info.vampirism() > 0) {
            lore.add("<gray>Вампиризм: <dark_red>" + Numbers.format(info.vampirism()) + "%");
        }
        if (info.level() > 1) {
            boolean ok = module.plugin().levels().meets(target, info.level());
            lore.add((ok ? "<gray>" : "<red>") + "Нужен уровень: " + info.level()
                    + (ok ? "" : " (бьёт как рука)"));
        }
        return Icons.of(hand.getType(), "<red>Оружие в руке", lore);
    }
}
