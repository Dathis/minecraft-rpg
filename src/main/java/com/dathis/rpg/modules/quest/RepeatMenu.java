package com.dathis.rpg.modules.quest;

import com.dathis.rpg.core.gui.Icons;
import com.dathis.rpg.core.gui.Menu;
import com.dathis.rpg.core.input.Numbers;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.OptionalDouble;

/** Как часто можно выполнять квест: один раз, с перезарядкой, ежедневно, еженедельно. */
final class RepeatMenu extends Menu {

    private final QuestEditorMenu editor;

    RepeatMenu(QuestEditorMenu editor) {
        super(3, Icons.text("<dark_gray>Повторение квеста"));
        this.editor = editor;
    }

    @Override
    protected void render() {
        for (int i = 0; i < size(); i++) {
            set(i, Icons.filler());
        }
        Quest draft = editor.draft();
        QuestModule module = editor.module();
        var config = module.plugin().getConfig();
        String reset = config.getString("quests.daily-reset", "00:00");

        mode(10, Quest.Mode.ONCE, Material.BOOK, "<gray>Выполнить можно только один раз");
        mode(11, Quest.Mode.REPEAT, Material.CLOCK, "<gray>Снова доступен через заданное",
                "<gray>время после сдачи (перезарядка)");
        mode(12, Quest.Mode.DAILY, Material.SUNFLOWER, "<gray>Раз в день. Сброс каждый день",
                "<gray>в " + reset + " по времени сервера");
        mode(13, Quest.Mode.WEEKLY, Material.MAP, "<gray>Раз в неделю. Сброс в "
                + config.getString("quests.weekly-reset-day", "MONDAY"), "<gray>в " + reset + " по времени сервера");

        if (draft.mode == Quest.Mode.REPEAT) {
            set(15, Icons.of(Material.HOPPER, "<yellow>Перезарядка",
                            "<gray>Сейчас: <white>" + QuestClock.duration(draft.cooldown * 1000L),
                            "<gray>Через сколько после сдачи", "<gray>квест можно взять снова",
                            "", "<green>Нажмите и напишите: 30m, 2h, 1d"),
                    p -> editor.module().plugin().chatInput().ask(p,
                            "<yellow>Перезарядка? Например <white>30m</white>, <white>2h</white>, <white>1d</white> (от 1 минуты):",
                            text -> {
                                int seconds = parseDuration(text);
                                if (seconds >= 60) {
                                    draft.cooldown = seconds;
                                } else {
                                    module.plugin().messages().send(p, "<red>Нужно время от 1 минуты: 30m, 2h, 1d.</red>");
                                }
                                open(p);
                            }, () -> open(p)));
        }
        if (draft.repeatable()) {
            set(16, Icons.of(Material.GOLD_NUGGET, "<yellow>Награда за повтор",
                            "<gray>Сейчас: <white>" + draft.repeatRewardPercent + "%",
                            "<gray>Второе и следующие выполнения",
                            "<gray>дают этот % предметов и опыта.",
                            "<gray>Первое — всегда полную награду.",
                            "", "<green>Нажмите и напишите число 0-100"),
                    p -> module.plugin().chatInput().ask(p, "<yellow>Сколько % награды за повтор? (0-100)", text -> {
                        OptionalDouble value = Numbers.parse(text);
                        if (value.isPresent() && value.getAsDouble() >= 0 && value.getAsDouble() <= 100) {
                            draft.repeatRewardPercent = (int) value.getAsDouble();
                        } else {
                            module.plugin().messages().send(p, "<red>Нужно число от 0 до 100.</red>");
                        }
                        open(p);
                    }, () -> open(p)));
            set(17, Icons.of(Material.REPEATER, "<yellow>Сколько раз всего",
                            "<gray>Сейчас: <white>" + (draft.maxRepeats > 0 ? draft.maxRepeats : "без ограничения"),
                            "<gray>После этого квест считается", "<gray>выполненным навсегда",
                            "", "<green>Нажмите и напишите число (0 — без ограничения)"),
                    p -> module.plugin().chatInput().ask(p, "<yellow>Сколько раз всего можно выполнить? (0 — без ограничения)", text -> {
                        OptionalDouble value = Numbers.parse(text);
                        if (value.isPresent() && value.getAsDouble() >= 0 && value.getAsDouble() <= 100_000) {
                            draft.maxRepeats = (int) value.getAsDouble();
                        } else {
                            module.plugin().messages().send(p, "<red>Нужно число от 0.</red>");
                        }
                        open(p);
                    }, () -> open(p)));
        }

        set(18, Icons.of(Material.ARROW, "<gray>К квесту"), editor::open);
        set(26, Icons.of(Material.BOOK, "<yellow>Как это работает",
                "<gray>Сданный повторяемый квест в книге",
                "<gray>показывает «Снова доступен через ...».",
                "<gray>Когда время пройдёт, его снова можно",
                "<gray>взять у квестодателя или у доски."));
    }

    private void mode(int slot, Quest.Mode mode, Material icon, String... lore) {
        Quest draft = editor.draft();
        boolean selected = draft.mode == mode;
        String[] full = new String[lore.length + 2];
        System.arraycopy(lore, 0, full, 0, lore.length);
        full[lore.length] = "";
        full[lore.length + 1] = selected ? "<green>Выбрано" : "<green>Нажмите, чтобы выбрать";
        ItemStack item = Icons.of(icon, (selected ? "<green><bold>" : "<yellow>") + mode.label, full);
        if (selected) {
            item.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
        }
        set(slot, item, p -> {
            draft.mode = mode;
            redraw();
        });
    }

    /** «30m», «2h», «1d», «90» (секунды) → секунды; -1 если не понятно. */
    static int parseDuration(String text) {
        String t = text.trim().toLowerCase(java.util.Locale.ROOT).replace(" ", "");
        if (t.endsWith("d") || t.endsWith("д")) {
            OptionalDouble days = Numbers.parse(t.substring(0, t.length() - 1));
            return days.isPresent() ? (int) Math.round(days.getAsDouble() * 86400) : -1;
        }
        return Numbers.parseTime(t);
    }
}
