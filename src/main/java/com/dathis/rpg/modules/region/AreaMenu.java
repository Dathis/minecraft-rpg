package com.dathis.rpg.modules.region;

import com.dathis.rpg.core.gui.Icons;
import com.dathis.rpg.core.gui.Menu;
import com.dathis.rpg.core.input.Numbers;
import com.dathis.rpg.core.selection.SelectionWand;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Настройка одной зоны. У зоны добычи снизу список правил и кнопки добавления блока,
 * у земли под участки — бумаги на участки и список участков.
 */
final class AreaMenu extends Menu {

    private static final int RULES_FROM = 18;
    private static final int RULES_TO = 44;

    private final RegionModule module;
    private final Area area;
    private boolean confirmDelete;

    AreaMenu(RegionModule module, Area area) {
        super(area.kind == Area.Kind.MINING ? 6 : 3, Icons.text("<dark_gray>" + area.name));
        this.module = module;
        this.area = area;
    }

    @Override
    protected void render() {
        for (int i = 0; i < size(); i++) {
            set(i, Icons.filler());
        }
        boolean mining = area.kind == Area.Kind.MINING;
        List<String> info = new ArrayList<>();
        info.add("<gray>id: <white>" + area.id);
        info.add("<gray>Где: <white>" + area.coordsText());
        info.add("<gray>Размер: <white>" + area.sizeText());
        if (mining) {
            info.add("<gray>Ждут возврата: <white>" + module.regen().count(area) + " бл.");
        }
        boolean land = area.kind == Area.Kind.LAND;
        int plots = land ? module.plots().inLand(area.id).size() : 0;
        if (land) {
            info.add("<gray>Участков: <white>" + plots);
        }
        set(4, Icons.of(AreaListMenu.icon(area), "<green>" + area.name, info));

        set(10, Icons.of(Material.NAME_TAG, "<yellow>Название",
                        "<gray>Сейчас: <white>" + area.name, "", "<green>Нажмите и напишите в чат"),
                p -> module.plugin().chatInput().ask(p, "<yellow>Новое название зоны:", text -> {
                    area.name = text;
                    module.areas().save();
                    new AreaMenu(module, area).open(p); // заголовок окна берётся из названия
                }, () -> open(p)));
        if (!area.wholeWorld) {
            set(11, Icons.of(Material.SPYGLASS, "<yellow>Показать границы",
                    "<gray>Подсветит зону на 10 секунд"), p -> {
                p.closeInventory();
                SelectionWand.showFor(module.plugin(), p, area.corner1(), area.corner2());
            });
            set(12, Icons.of(Material.ENDER_PEARL, "<yellow>Телепорт в зону"), p -> {
                Location center = area.center();
                if (center != null) {
                    p.closeInventory();
                    p.teleport(center.add(0, 1, 0));
                }
            });
            set(13, Icons.of(Material.BLAZE_ROD, "<yellow>Переместить на выделение",
                    "<gray>Зона займёт текущую область,",
                    "<gray>выделенную палочкой"), p -> {
                SelectionWand.Selection selection = module.plugin().selectionWand().selection(p);
                if (selection == null || !selection.complete()) {
                    module.plugin().messages().send(p, "<red>Сначала выделите новую область палочкой.</red>");
                    return;
                }
                if (land && !module.plots().inLand(area.id).isEmpty()) {
                    module.plugin().messages().send(p, "<red>На земле уже есть участки, её нельзя переместить."
                            + " Создайте новую землю.</red>");
                    return;
                }
                if (mining) {
                    module.regen().restoreNow(area);
                }
                area.setArea(selection.first, selection.second);
                module.areas().save();
                module.plugin().messages().send(p, "<green>Зона перемещена.");
                redraw();
            });
        }
        if (mining) {
            set(14, Icons.of(Material.CLOCK, "<yellow>Вернуть все блоки сейчас",
                    "<gray>Сломанные блоки этой зоны,",
                    "<gray>которые ждут возврата, встанут",
                    "<gray>на место сразу"), p -> {
                int n = module.regen().restoreNow(area);
                module.plugin().messages().send(p, "<green>Возвращено блоков: " + n);
                redraw();
            });
        }
        set(16, Icons.of(confirmDelete ? Material.TNT : Material.BARRIER,
                confirmDelete ? "<red><bold>Точно удалить зону?" : "<red>Удалить зону",
                confirmDelete ? "<gray>Нажмите ещё раз для подтверждения"
                        : land ? "<gray>Участков: " + plots + ", они тоже удалятся" : "<gray>Сломанные блоки сразу вернутся"), p -> {
            if (!confirmDelete) {
                confirmDelete = true;
                redraw();
                return;
            }
            if (mining) {
                module.regen().restoreNow(area);
            }
            if (land) {
                module.plots().deleteLand(area.id);
            }
            module.areas().delete(area);
            module.plugin().messages().send(p, "<yellow>Зона " + area.id + " удалена.");
            new AreaListMenu(module, area.kind).open(p);
        });

        if (!mining) {
            set(18, Icons.of(Material.ARROW, "<gray>К списку зон"), p -> new AreaListMenu(module, area.kind).open(p));
            if (land) {
                renderLand(plots);
            }
            return;
        }

        // Правила
        int slot = RULES_FROM;
        for (BlockRule rule : area.rules.values()) {
            if (slot > RULES_TO) {
                break;
            }
            set(slot++, Icons.of(RegionIcons.item(rule.block), "<white>" + RegionIcons.pretty(rule.block),
                            "<gray>Замена: <white>" + RuleMenu.replacementText(rule),
                            "<gray>Вернётся: <white>" + RuleMenu.regenText(rule),
                            "", "<green>Нажмите, чтобы настроить"),
                    p -> new RuleMenu(module, this, area, rule).open(p));
        }
        for (int i = slot; i <= RULES_TO; i++) {
            set(i, Icons.of(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "<gray>Пустое место для правила"));
        }

        set(45, Icons.of(Material.ARROW, "<gray>К списку зон"), p -> new AreaListMenu(module, area.kind).open(p));
        setInput(47, Icons.of(Material.HOPPER, "<green>Добавить блок: положить сюда",
                "<gray>Возьмите блок из инвентаря",
                "<gray>и положите в этот слот.",
                "<gray>Блок останется у вас"), (p, cursor) -> {
            if (cursor == null) {
                module.plugin().messages().send(p, "<yellow>Возьмите блок на курсор и положите в этот слот.");
                return;
            }
            add(p, RegionIcons.fromItem(cursor));
        });
        set(48, Icons.of(Material.SPYGLASS, "<green>Добавить блок, на который смотрю",
                "<gray>Посмотрите на блок в мире",
                "<gray>(до 8 блоков) и нажмите.",
                "<gray>Подходит для посевов и всего,",
                "<gray>чего нет в инвентаре"), p -> add(p, RegionIcons.lookingAt(p)));
        set(49, Icons.of(Material.OAK_SIGN, "<green>Добавить блок по названию",
                        "<gray>По-английски, например",
                        "<white>stone</white><gray>, <white>oak log</white><gray>, <white>iron ore"),
                p -> module.plugin().chatInput().ask(p,
                        "<yellow>Название блока по-английски (например <white>stone</white>, <white>oak_log</white>):",
                        text -> {
                            Material m = RegionIcons.fromName(text);
                            if (m == null) {
                                module.plugin().messages().send(p, "<red>Не нашёл такой блок: " + text + "</red>");
                                open(p);
                            } else {
                                add(p, m);
                            }
                        }, () -> open(p)));
        set(51, Icons.of(Material.FEATHER, "<yellow>Shift-клик по блоку в инвентаре",
                "<gray>тоже добавит его (только Java)"));
        set(53, Icons.of(Material.BOOK, "<yellow>Как это работает",
                "<gray>Игроки ломают здесь только блоки",
                "<gray>из правил. Добыча падает сразу",
                "<gray>в инвентарь. На месте блока",
                "<gray>ставится замена (или пусто),",
                "<gray>а через заданное время блок",
                "<gray>возвращается (или не возвращается)."));
    }

    /** Кнопки земли под участки: получить бумаги и список участков. */
    private void renderLand(int plots) {
        int slot = 20;
        for (PlotSize size : PlotSize.values()) {
            int side = module.plotSide(size);
            int cells = ((area.maxX - area.minX + 1) / side) * ((area.maxZ - area.minZ + 1) / side);
            set(slot++, Icons.of(Material.PAPER, "<gold>Бумага: " + size.title + " " + side + "x" + side,
                    "<gray>Мест такого размера на земле: <white>" + cells,
                    "", "<green>Нажмите, чтобы получить бумагу.",
                    "<gray>Положите её товаром в торговлю",
                    "<gray>NPC (/create-npc), чтобы игроки",
                    "<gray>её покупали"), p -> {
                for (var left : p.getInventory().addItem(module.deeds().create(area, size)).values()) {
                    p.getWorld().dropItemNaturally(p.getLocation(), left);
                }
                module.plugin().messages().send(p, "<green>Бумага на участок (" + size.title + ") выдана.");
            });
        }
        set(24, Icons.of(Material.OAK_DOOR, "<green>Участки на этой земле",
                "<gray>Всего: <white>" + plots,
                "<gray>Посмотреть, телепорт, удалить"), p -> new PlotListMenu(module, area, p).open(p));
        set(26, Icons.of(Material.BOOK, "<yellow>Как это работает",
                "<gray>1. Получите бумаги (слева) и",
                "<gray>   продайте их через NPC-торговца.",
                "<gray>2. Игрок берёт бумагу в руку, жмёт ПКМ",
                "<gray>   и выбирает свободное место на карте.",
                "<gray>3. На своём участке строит только он",
                "<gray>   и его помощники. Высота - вся.",
                "<gray>Места считаются от угла земли",
                "<gray>с наименьшими X и Z."));
    }

    @Override
    protected void quickAdd(Player player, org.bukkit.inventory.ItemStack item) {
        if (area.kind == Area.Kind.MINING) {
            add(player, RegionIcons.fromItem(item));
        }
    }

    private void add(Player player, Material block) {
        if (block == null) {
            module.plugin().messages().send(player, "<red>Это не блок.</red>");
            open(player);
            return;
        }
        BlockRule rule = area.rules.get(block);
        if (rule == null) {
            if (area.rules.size() > RULES_TO - RULES_FROM) {
                module.plugin().messages().send(player, "<red>В зоне уже максимум правил.</red>");
                open(player);
                return;
            }
            rule = new BlockRule(block);
            rule.regenSeconds = 60;
            area.rules.put(block, rule);
            module.areas().save();
            module.plugin().messages().send(player, "<green>Блок " + RegionIcons.pretty(block)
                    + " добавлен: без замены, вернётся через " + Numbers.formatTime(60) + ".");
        }
        new RuleMenu(module, this, area, rule).open(player);
    }
}
