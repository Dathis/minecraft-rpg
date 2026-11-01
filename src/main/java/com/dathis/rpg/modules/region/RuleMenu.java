package com.dathis.rpg.modules.region;

import com.dathis.rpg.core.gui.Icons;
import com.dathis.rpg.core.gui.Menu;
import com.dathis.rpg.core.input.Numbers;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Правило блока в зоне добычи: замена и возврат. */
final class RuleMenu extends Menu {

    private final RegionModule module;
    private final AreaMenu back;
    private final Area area;
    private final BlockRule rule;
    private boolean confirmDelete;

    RuleMenu(RegionModule module, AreaMenu back, Area area, BlockRule rule) {
        super(3, Icons.text("<dark_gray>Блок " + RegionIcons.pretty(rule.block)));
        this.module = module;
        this.back = back;
        this.area = area;
        this.rule = rule;
    }

    static String replacementText(BlockRule rule) {
        return rule.replacement == null ? "пусто" : RegionIcons.pretty(rule.replacement);
    }

    static String regenText(BlockRule rule) {
        return rule.regenSeconds <= 0 ? "нет" : "через " + Numbers.formatTime(rule.regenSeconds);
    }

    @Override
    protected void render() {
        for (int i = 0; i < size(); i++) {
            set(i, Icons.filler());
        }
        set(4, Icons.of(RegionIcons.item(rule.block), "<white>" + RegionIcons.pretty(rule.block),
                "<gray>Можно ломать в зоне <white>" + area.name,
                "<gray>Добыча падает в инвентарь",
                "<gray>Замена: <white>" + replacementText(rule),
                "<gray>Вернётся: <white>" + regenText(rule)));

        setInput(10, Icons.of(rule.replacement == null ? Material.BARRIER : RegionIcons.item(rule.replacement),
                "<yellow>Замена: <white>" + replacementText(rule),
                "<gray>Что встанет на место сломанного.",
                "<gray>Положите сюда блок из инвентаря,",
                "<gray>чтобы выбрать замену"), (p, cursor) -> {
            if (cursor == null) {
                module.plugin().messages().send(p, "<yellow>Возьмите блок на курсор и положите в этот слот.");
                return;
            }
            setReplacement(p, RegionIcons.fromItem(cursor));
        });
        set(11, Icons.of(Material.SPYGLASS, "<yellow>Замена: блок, на который смотрю",
                "<gray>Посмотрите на блок в мире и нажмите"), p -> setReplacement(p, RegionIcons.lookingAt(p)));
        set(12, Icons.of(Material.OAK_SIGN, "<yellow>Замена: по названию",
                        "<gray>По-английски, например <white>bedrock"),
                p -> module.plugin().chatInput().ask(p, "<yellow>Блок-замена по-английски (например <white>bedrock</white>):",
                        text -> {
                            Material m = RegionIcons.fromName(text);
                            if (m == null) {
                                module.plugin().messages().send(p, "<red>Не нашёл такой блок: " + text + "</red>");
                                open(p);
                            } else {
                                setReplacement(p, m);
                            }
                        }, () -> open(p)));
        set(13, Icons.of(Material.GLASS, "<yellow>Замена: пусто",
                "<gray>На месте блока ничего не появится"), p -> {
            rule.replacement = null;
            module.areas().save();
            redraw();
        });

        set(15, Icons.of(Material.CLOCK, "<yellow>Возврат блока: <white>" + regenText(rule),
                        "<gray>Через сколько блок встанет на место",
                        "", "<green>Нажмите и напишите время",
                        "<gray>Например: <white>30</white>, <white>5m</white>, <white>1h",
                        "<gray>0 — не возвращать"),
                p -> module.plugin().chatInput().ask(p,
                        "<yellow>Через сколько вернуть блок: секунды или с буквой (<white>30</white>, <white>5m</white>, <white>1h</white>), 0 — не возвращать:",
                        text -> {
                            int seconds = Numbers.parseTime(text);
                            if (seconds >= 0 && seconds <= 7 * 86400) {
                                rule.regenSeconds = seconds;
                                module.areas().save();
                            } else {
                                module.plugin().messages().send(p, "<red>Нужно время от 0 до 7 дней.</red>");
                            }
                            open(p);
                        }, () -> open(p)));
        set(16, Icons.of(rule.regenSeconds > 0 ? Material.RED_DYE : Material.LIME_DYE,
                rule.regenSeconds > 0 ? "<yellow>Не возвращать блок" : "<yellow>Возвращать через 60 сек",
                "<gray>Быстрое переключение"), p -> {
            rule.regenSeconds = rule.regenSeconds > 0 ? 0 : 60;
            module.areas().save();
            redraw();
        });

        set(18, Icons.of(Material.ARROW, "<gray>К зоне"), back::open);
        set(22, Icons.of(confirmDelete ? Material.TNT : Material.BARRIER,
                confirmDelete ? "<red><bold>Точно удалить правило?" : "<red>Удалить правило",
                confirmDelete ? "<gray>Нажмите ещё раз" : "<gray>Блок снова нельзя будет ломать"), p -> {
            if (!confirmDelete) {
                confirmDelete = true;
                redraw();
                return;
            }
            area.rules.remove(rule.block);
            module.areas().save();
            back.open(p);
        });
    }

    private void setReplacement(Player player, Material block) {
        if (block == null) {
            module.plugin().messages().send(player, "<red>Это не блок.</red>");
        } else {
            rule.replacement = block;
            module.areas().save();
        }
        open(player);
    }
}
