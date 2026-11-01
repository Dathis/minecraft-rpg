package com.dathis.rpg.modules.quest;

import com.dathis.rpg.core.gui.ChoiceMenu;
import com.dathis.rpg.core.gui.Icons;
import com.dathis.rpg.core.gui.Menu;
import com.dathis.rpg.core.input.Numbers;
import com.dathis.rpg.core.selection.SelectionWand;
import com.dathis.rpg.modules.mob.MobModule;
import com.dathis.rpg.modules.npc.NpcModule;
import com.dathis.rpg.modules.npc.NpcTemplate;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalDouble;

/** Настройка одной цели квеста. Кнопки зависят от типа цели. */
final class ObjectiveMenu extends Menu {

    private final QuestEditorMenu editor;
    private final QuestModule module;
    private final Objective objective;

    ObjectiveMenu(QuestEditorMenu editor, Objective objective) {
        super(3, Icons.text("<dark_gray>Цель: " + objective.type.label));
        this.editor = editor;
        this.module = editor.module();
        this.objective = objective;
    }

    @Override
    protected void render() {
        for (int i = 0; i < size(); i++) {
            set(i, Icons.filler());
        }
        set(4, Icons.of(objective.type.icon, module.objectiveText(objective, 0),
                objective.complete() ? "<green>Цель настроена" : "<red>Цель ещё не настроена"));

        switch (objective.type) {
            case KILL -> {
                set(10, Icons.of(Material.ZOMBIE_SPAWN_EGG, "<yellow>Кастомный моб",
                        "<gray>Из /create-mob", "", "<green>Нажмите, чтобы выбрать"), this::chooseCustomMob);
                set(11, Icons.of(Material.COW_SPAWN_EGG, "<yellow>Обычный моб",
                        "<gray>Любой ванильный (не кастомный)", "", "<green>Нажмите, чтобы выбрать"), this::chooseVanillaMob);
                amountButton(13, "Сколько убить");
            }
            case BRING -> {
                ItemStack icon = objective.item != null
                        ? Icons.withHint(objective.item, "", "<aqua>Этот предмет нужно принести",
                        "<gray>Положите другой, чтобы заменить")
                        : Icons.of(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "<gray>Предмет: не выбран",
                        "<green>Положите сюда предмет из инвентаря",
                        "<gray>(или shift-клик по нему)");
                setInput(10, icon, (p, cursor) -> {
                    if (cursor != null) {
                        setItem(cursor);
                    }
                    redraw();
                });
                set(11, Icons.of(Material.BOOK, "<yellow>Как сравниваются предметы",
                        "<gray>Предмет из /create-item — по шаблону,",
                        "<gray>обычный — по виду (название не важно)."));
                amountButton(13, "Сколько принести");
            }
            case TALK -> set(10, Icons.of(Material.VILLAGER_SPAWN_EGG, "<yellow>С кем поговорить",
                    "", "<green>Нажмите, чтобы выбрать NPC"), this::chooseNpc);
            case EXPLORE -> {
                set(10, Icons.of(Material.BLAZE_ROD, "<gold>Получить палочку выделения",
                        "<gray>Удар по блоку — первый угол",
                        "<gray>ПКМ по блоку — второй угол"), p -> {
                    p.getInventory().addItem(module.plugin().selectionWand().create());
                    module.plugin().messages().send(p, "<green>Палочка выдана. Выделите область и вернитесь:"
                            + " <white>/create-quest</white> → квест → цель.");
                    p.closeInventory();
                });
                set(11, Icons.of(Material.LIME_CONCRETE, "<green>Взять область из выделения",
                        objective.hasArea() ? "<gray>Сейчас: <white>" + objective.world + " " + objective.minX + " "
                                + objective.minY + " " + objective.minZ + " → " + objective.maxX + " " + objective.maxY
                                + " " + objective.maxZ : "<gray>Сейчас: <red>не задана"), p -> {
                    SelectionWand.Selection selection = module.plugin().selectionWand().selection(p);
                    if (selection == null || !selection.complete()) {
                        module.plugin().messages().send(p, "<red>Сначала выделите область палочкой.</red>");
                        return;
                    }
                    objective.setArea(selection.first, selection.second);
                    redraw();
                });
                set(12, Icons.of(Material.NAME_TAG, "<yellow>Название места",
                        "<gray>Сейчас: <white>" + objective.areaName,
                        "<gray>Игрок увидит «Разведать: ...»", "", "<green>Нажмите и напишите в чат"),
                        p -> module.plugin().chatInput().ask(p, "<yellow>Как называется место?", text -> {
                            objective.areaName = text;
                            open(p);
                        }, () -> open(p)));
                if (objective.hasArea()) {
                    set(14, Icons.of(Material.SPYGLASS, "<yellow>Показать границы", "<gray>На 10 секунд"),
                            this::showBorders);
                }
            }
        }

        set(18, Icons.of(Material.ARROW, "<gray>К квесту"), editor::open);
        set(26, Icons.of(Material.BARRIER, "<red>Удалить цель"), p -> {
            editor.draft().objectives.remove(objective);
            editor.open(p);
        });
    }

    @Override
    protected void quickAdd(Player player, ItemStack item) {
        if (objective.type == Objective.Type.BRING) {
            setItem(item);
            redraw();
        }
    }

    private void setItem(ItemStack item) {
        ItemStack copy = item.clone();
        if (objective.item == null && copy.getAmount() > 1) {
            objective.amount = copy.getAmount();
        }
        copy.setAmount(1);
        objective.item = copy;
    }

    private void amountButton(int slot, String label) {
        set(slot, Icons.of(Material.PAPER, "<yellow>" + label,
                        "<gray>Сейчас: <white>" + objective.amount, "", "<green>Нажмите и напишите число"),
                p -> module.plugin().chatInput().ask(p, "<yellow>" + label + "? (1-10000)", text -> {
                    OptionalDouble value = Numbers.parse(text);
                    if (value.isPresent() && value.getAsDouble() >= 1 && value.getAsDouble() <= 10000) {
                        objective.amount = (int) value.getAsDouble();
                    } else {
                        module.plugin().messages().send(p, "<red>Нужно число от 1 до 10000.</red>");
                    }
                    open(p);
                }, () -> open(p)));
    }

    private void chooseCustomMob(Player player) {
        MobModule mobs = module.mobs();
        if (mobs == null) {
            module.plugin().messages().send(player, "<red>Модуль мобов выключен.</red>");
            return;
        }
        List<ChoiceMenu.Choice> choices = new ArrayList<>();
        for (MobModule.MobInfo info : mobs.mobInfos()) {
            choices.add(new ChoiceMenu.Choice(Icons.withHint(info.icon(), "", "<green>Выбрать"), p -> {
                objective.target = "mob:" + info.id();
                open(p);
            }));
        }
        new ChoiceMenu("Кастомный моб", choices, this).open(player);
    }

    private void chooseVanillaMob(Player player) {
        List<ChoiceMenu.Choice> choices = new ArrayList<>();
        Arrays.stream(EntityType.values())
                .filter(t -> t != EntityType.PLAYER && t.isAlive() && t.isSpawnable() && MobModule.eggMaterial(t) != null)
                .sorted(Comparator.comparing(Enum::name))
                .forEach(type -> choices.add(new ChoiceMenu.Choice(
                        Icons.of(MobModule.eggMaterial(type), "<yellow><lang:" + type.translationKey() + ">"), p -> {
                    objective.target = "type:" + type.name();
                    open(p);
                })));
        new ChoiceMenu("Обычный моб", choices, this).open(player);
    }

    private void chooseNpc(Player player) {
        NpcModule npcs = module.npcs();
        if (npcs == null) {
            module.plugin().messages().send(player, "<red>Модуль NPC выключен.</red>");
            return;
        }
        List<ChoiceMenu.Choice> choices = new ArrayList<>();
        for (NpcTemplate npc : npcs.npcs()) {
            choices.add(new ChoiceMenu.Choice(Icons.withHint(npcs.icon(npc), "", "<green>Выбрать"), p -> {
                objective.target = npc.id();
                open(p);
            }));
        }
        new ChoiceMenu("С кем поговорить", choices, this).open(player);
    }

    private void showBorders(Player player) {
        player.closeInventory();
        World world = module.plugin().getServer().getWorld(objective.world);
        if (world == null) {
            return;
        }
        Location a = new Location(world, objective.minX, objective.minY, objective.minZ);
        Location b = new Location(world, objective.maxX, objective.maxY, objective.maxZ);
        new BukkitRunnable() {
            private int times;

            @Override
            public void run() {
                if (!player.isOnline() || ++times > 20) {
                    cancel();
                    return;
                }
                SelectionWand.outline(player, a, b, Particle.FLAME);
            }
        }.runTaskTimer(module.plugin(), 0L, 10L);
    }
}
