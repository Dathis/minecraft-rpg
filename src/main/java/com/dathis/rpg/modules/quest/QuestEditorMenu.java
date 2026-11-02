package com.dathis.rpg.modules.quest;

import com.dathis.rpg.core.gui.ChoiceMenu;
import com.dathis.rpg.core.gui.Icons;
import com.dathis.rpg.core.gui.Menu;
import com.dathis.rpg.core.input.Numbers;
import com.dathis.rpg.modules.npc.NpcModule;
import com.dathis.rpg.modules.npc.NpcTemplate;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalDouble;
import java.util.function.Consumer;

/** Редактор квеста. */
final class QuestEditorMenu extends Menu {

    private static final int[] OBJECTIVE_SLOTS = {19, 20, 21, 22, 23, 24, 25};

    private final QuestModule module;
    private final Quest draft;
    private final Menu back;
    private boolean confirmDelete;

    QuestEditorMenu(QuestModule module, Quest draft, Menu back) {
        super(6, Icons.text("<dark_gray>Редактор квеста"));
        this.module = module;
        this.draft = draft;
        this.back = back;
    }

    Quest draft() {
        return draft;
    }

    QuestModule module() {
        return module;
    }

    @Override
    protected void render() {
        for (int i = 0; i < size(); i++) {
            set(i, Icons.filler());
        }
        set(4, Icons.withHint(module.book(null, draft), "",
                draft.id == null ? "<dark_gray>Ещё не сохранён" : "<dark_gray>ID: " + draft.id,
                "<gray>Так квест увидит игрок"));

        set(10, Icons.of(Material.NAME_TAG, "<yellow>Название",
                        "<gray>Сейчас: <white>" + draft.name, "", "<green>Нажмите и напишите в чат"),
                p -> ask(p, "<yellow>Введите название квеста:", text -> draft.name = text));

        NpcModule npcs = module.npcs();
        NpcTemplate giver = npcs == null || draft.npcId == null ? null : npcs.npc(draft.npcId);
        set(11, Icons.of(Material.VILLAGER_SPAWN_EGG, "<yellow>Квестодатель",
                        "<gray>Сейчас: " + (giver != null ? giver.name() : draft.board ? "<white>только доска" : "<red>не выбран"),
                        "<gray>У него квест берут и ему сдают",
                        "<gray>(квесту с доски NPC не обязателен)",
                        "", "<green>Нажмите, чтобы выбрать NPC"),
                this::chooseNpc);

        List<String> descriptionLore = new ArrayList<>();
        descriptionLore.add("<gray>Что говорит NPC. Строк: <white>" + draft.description.size());
        descriptionLore.add("<gray>Новый абзац: символ <white>|");
        descriptionLore.add("");
        descriptionLore.add("<green>Нажмите и напишите в чат");
        descriptionLore.add("<gray>(заменит текст, <white>-</white> — очистить)");
        set(12, Icons.of(Material.WRITABLE_BOOK, "<yellow>Описание", descriptionLore),
                p -> ask(p, "<yellow>Напишите, что говорит NPC. Новый абзац — символ <white>|</white>. <white>-</white> — очистить:",
                        text -> draft.description = text.equals("-") ? new ArrayList<>() : split(text)));
        set(13, Icons.of(Material.FEATHER, "<yellow>Дописать описание",
                        "<gray>Добавит абзацы в конец", "<gray>(в чате длина сообщения ограничена)",
                        "", "<green>Нажмите и напишите в чат"),
                p -> ask(p, "<yellow>Что дописать к описанию? Новый абзац — <white>|</white>:",
                        text -> draft.description.addAll(split(text))));

        Quest required = module.registry().get(draft.requiredQuest);
        set(14, Icons.of(Material.BOOK, "<yellow>После какого квеста",
                        "<gray>Сейчас: <white>" + (required != null ? required.name : "нет"),
                        "<gray>Квест станет доступен, когда",
                        "<gray>игрок выполнит выбранный",
                        "", "<green>Нажмите, чтобы выбрать"),
                this::chooseRequired);
        set(15, Icons.of(Material.LADDER, "<yellow>С какого уровня",
                        "<gray>Сейчас: <white>" + (draft.requiredLevel > 1 ? draft.requiredLevel : "любой"),
                        "<gray>Игрок ниже этого уровня",
                        "<gray>не сможет взять квест",
                        "", "<green>Нажмите и напишите число"),
                p -> ask(p, "<yellow>С какого уровня доступен квест? (1-" + module.plugin().levels().maxLevel() + ", 1 — с любого)", text -> {
                    OptionalDouble value = Numbers.parse(text);
                    int max = module.plugin().levels().maxLevel();
                    if (value.isPresent() && value.getAsDouble() >= 0 && value.getAsDouble() <= max) {
                        draft.requiredLevel = (int) value.getAsDouble();
                    } else {
                        module.plugin().messages().send(p, "<red>Нужно число от 1 до " + max + ".</red>");
                    }
                }));
        set(16, Icons.of(Material.EXPERIENCE_BOTTLE, "<green>Награда опытом",
                        "<gray>Сейчас: <white>" + draft.rewardXp + " опыта",
                        "<gray>Опыт персонажа. До 2-го уровня",
                        "<gray>нужно " + module.plugin().levels().xpToNext(1) + ", до 10-го всего "
                                + module.plugin().levels().totalXpFor(Math.min(10, module.plugin().levels().maxLevel())),
                        "", "<green>Нажмите и напишите число"),
                p -> ask(p, "<yellow>Сколько опыта дать за квест? (0 — нисколько)", text -> {
                    OptionalDouble value = Numbers.parse(text);
                    if (value.isPresent() && value.getAsDouble() >= 0 && value.getAsDouble() <= 1_000_000) {
                        draft.rewardXp = (int) value.getAsDouble();
                    } else {
                        module.plugin().messages().send(p, "<red>Нужно число от 0.</red>");
                    }
                }));

        set(18, Icons.of(Material.TARGET, "<gold>Цели",
                "<gray>Нужно выполнить все.", "<gray>До " + Quest.MAX_OBJECTIVES + " целей"));
        for (int i = 0; i < draft.objectives.size() && i < OBJECTIVE_SLOTS.length; i++) {
            Objective objective = draft.objectives.get(i);
            List<String> lore = new ArrayList<>();
            lore.add("<gray>Тип: <white>" + objective.type.label);
            if (!objective.complete()) {
                lore.add("<red>Не настроена");
            }
            lore.add("");
            lore.add("<green>Нажмите, чтобы изменить");
            set(OBJECTIVE_SLOTS[i], Icons.of(objective.type.icon, module.objectiveText(objective, 0), lore),
                    p -> new ObjectiveMenu(this, objective).open(p));
        }
        if (draft.objectives.size() < Quest.MAX_OBJECTIVES) {
            set(OBJECTIVE_SLOTS[draft.objectives.size()], Icons.of(Material.LIME_STAINED_GLASS_PANE,
                    "<green>Добавить цель", "<gray>Убить, принести, поговорить,", "<gray>разведать, добыть, скрафтить"), this::addObjective);
        }

        set(31, Icons.of(Material.CHEST, "<gold>Награда предметами",
                        "<gray>Предметов: <white>" + draft.rewards.size(),
                        "", "<green>Нажмите, чтобы настроить"),
                p -> new RewardsMenu(this).open(p));

        List<String> repeatLore = new ArrayList<>();
        repeatLore.add("<gray>Сейчас: <white>" + module.modeText(draft));
        if (draft.repeatable()) {
            repeatLore.add("<gray>Награда за повтор: <white>" + draft.repeatRewardPercent + "%");
            repeatLore.add("<gray>Сколько раз всего: <white>" + (draft.maxRepeats > 0 ? draft.maxRepeats : "без ограничения"));
        }
        repeatLore.add("");
        repeatLore.add("<green>Нажмите, чтобы настроить");
        set(30, Icons.of(Material.CLOCK, "<aqua>Повторение", repeatLore), p -> new RepeatMenu(this).open(p));

        set(32, Icons.of(draft.board ? Material.OAK_HANGING_SIGN : Material.OAK_SIGN,
                        "<gold>Доска заданий: " + (draft.board ? "<green>в пуле" : "<gray>нет"),
                        "<gray>Квесты из пула доски по очереди",
                        "<gray>появляются на досках заданий:",
                        "<gray>каждый день " + module.boardSize() + " случайных.",
                        "<gray>Лучше сделать квест ежедневным.",
                        "", "<green>Нажмите, чтобы " + (draft.board ? "убрать из пула" : "добавить в пул")),
                p -> {
                    draft.board = !draft.board;
                    if (draft.board && draft.mode == Quest.Mode.ONCE) {
                        // с доски обычно берут каждый день; админ может поменять в «Повторение»
                        draft.mode = Quest.Mode.DAILY;
                        module.plugin().messages().send(p, "<gray>Квест стал ежедневным (можно поменять в «Повторение»).");
                    }
                    redraw();
                });

        set(45, Icons.of(Material.ARROW, "<gray>Назад"), back::open);
        set(47, Icons.of(Material.BOOK, "<yellow>Как это работает",
                "<gray>Игрок нажимает на квестодателя и",
                "<gray>видит книги квестов. Берёт квест,",
                "<gray>выполняет цели и возвращается к",
                "<gray>квестодателю, чтобы сдать и получить",
                "<gray>награду. Предметы для «Принести»",
                "<gray>забираются при сдаче."));
        set(49, Icons.of(Material.LIME_CONCRETE, "<green>Сохранить"), this::save);
        if (draft.id != null) {
            set(53, Icons.of(confirmDelete ? Material.TNT : Material.BARRIER,
                            confirmDelete ? "<red><bold>Точно удалить?" : "<red>Удалить квест",
                            confirmDelete ? "<gray>Нажмите ещё раз для подтверждения" : "<gray>Прогресс игроков пропадёт"),
                    this::delete);
        }
    }

    private void save(Player player) {
        module.registry().save(draft);
        module.plugin().messages().send(player, "<green>Квест сохранён: <white>" + draft.name
                + "</white> <gray>(ID: " + draft.id + ")</gray>");
        if (!draft.ready()) {
            module.plugin().messages().send(player,
                    "<yellow>Квест пока нельзя взять: нужен квестодатель (или доска заданий) и хотя бы одна настроенная цель.");
        }
        redraw();
    }

    private void delete(Player player) {
        if (!confirmDelete) {
            confirmDelete = true;
            redraw();
            return;
        }
        module.registry().delete(draft.id);
        module.plugin().messages().send(player, "<yellow>Квест удалён.");
        back.open(player);
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
                draft.npcId = npc.id();
                open(p);
            }));
        }
        new ChoiceMenu("Кто выдаёт квест", choices, this).open(player);
    }

    private void chooseRequired(Player player) {
        List<ChoiceMenu.Choice> choices = new ArrayList<>();
        choices.add(new ChoiceMenu.Choice(Icons.of(Material.BARRIER, "<yellow>Нет", "<gray>Доступен сразу"), p -> {
            draft.requiredQuest = null;
            open(p);
        }));
        for (Quest quest : module.registry().all()) {
            if (quest.id.equals(draft.id)) {
                continue;
            }
            choices.add(new ChoiceMenu.Choice(Icons.withHint(module.book(null, quest), "", "<dark_gray>ID: " + quest.id,
                    "<green>Выбрать"), p -> {
                draft.requiredQuest = quest.id;
                open(p);
            }));
        }
        new ChoiceMenu("После какого квеста", choices, this).open(player);
    }

    private void addObjective(Player player) {
        List<ChoiceMenu.Choice> choices = new ArrayList<>();
        String[][] help = {
                {"<gray>Убить мобов: кастомных или ванильных"},
                {"<gray>Добыть и принести предметы.", "<gray>Забираются при сдаче квеста"},
                {"<gray>Нажать на нужного NPC"},
                {"<gray>Прийти в область"},
                {"<gray>Сломать блоки: руда, брёвна,", "<gray>созревшие посевы"},
                {"<gray>Скрафтить предметы"}};
        for (Objective.Type type : Objective.Type.values()) {
            choices.add(new ChoiceMenu.Choice(Icons.of(type.icon, "<yellow>" + type.label, help[type.ordinal()]), p -> {
                Objective objective = new Objective(type);
                draft.objectives.add(objective);
                new ObjectiveMenu(this, objective).open(p);
            }));
        }
        new ChoiceMenu("Новая цель", choices, this).open(player);
    }

    void ask(Player player, String prompt, Consumer<String> apply) {
        module.plugin().chatInput().ask(player, prompt, text -> {
            apply.accept(text);
            open(player);
        }, () -> open(player));
    }

    private static List<String> split(String text) {
        return new ArrayList<>(Arrays.stream(text.split("\\|")).map(String::trim).filter(s -> !s.isEmpty()).toList());
    }
}
