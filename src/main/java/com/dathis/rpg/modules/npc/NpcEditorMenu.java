package com.dathis.rpg.modules.npc;

import com.dathis.rpg.core.gui.ChoiceMenu;
import com.dathis.rpg.core.gui.EquipmentMenu;
import com.dathis.rpg.core.gui.Icons;
import com.dathis.rpg.core.gui.Menu;
import com.dathis.rpg.modules.mob.MobModule;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Редактор шаблона NPC. */
final class NpcEditorMenu extends Menu {

    private static final Map<String, String> PROFESSIONS = Map.ofEntries(
            Map.entry("none", "Без профессии"), Map.entry("armorer", "Бронник"), Map.entry("butcher", "Мясник"),
            Map.entry("cartographer", "Картограф"), Map.entry("cleric", "Священник"), Map.entry("farmer", "Фермер"),
            Map.entry("fisherman", "Рыбак"), Map.entry("fletcher", "Лучник"), Map.entry("leatherworker", "Кожевник"),
            Map.entry("librarian", "Библиотекарь"), Map.entry("mason", "Каменщик"), Map.entry("nitwit", "Глупец"),
            Map.entry("shepherd", "Пастух"), Map.entry("toolsmith", "Инструментальщик"),
            Map.entry("weaponsmith", "Оружейник"));
    private static final Map<String, String> REGIONS = Map.of(
            "plains", "Равнины", "desert", "Пустыня", "jungle", "Джунгли", "savanna", "Саванна",
            "snow", "Снега", "swamp", "Болото", "taiga", "Тайга");

    private final NpcModule module;
    private final NpcTemplate draft;
    private boolean confirmDelete;

    NpcEditorMenu(NpcModule module, NpcTemplate draft) {
        super(5, Icons.text("<dark_gray>Редактор NPC"));
        this.module = module;
        this.draft = draft;
    }

    NpcTemplate draft() {
        return draft;
    }

    @Override
    protected void render() {
        for (int i = 0; i < size(); i++) {
            set(i, Icons.filler());
        }
        set(4, Icons.withHint(module.icon(draft), "",
                draft.id == null ? "<dark_gray>Ещё не сохранён" : "<dark_gray>ID: " + draft.id));

        Material egg = MobModule.eggMaterial(draft.type);
        set(10, Icons.of(draft.type == EntityType.ARMOR_STAND ? Material.ARMOR_STAND : egg != null ? egg : Material.EGG,
                        "<yellow>Кто это",
                        "<gray>Сейчас: <white>" + NpcModule.typeName(draft.type),
                        "<gray>Броню видно на зомби, скелетах,",
                        "<gray>пиглинах и стойке для брони",
                        "", "<green>Нажмите, чтобы выбрать"),
                this::chooseType);
        set(11, Icons.of(Material.NAME_TAG, "<yellow>Имя",
                        "<gray>Сейчас: <white>" + draft.name,
                        "<gray>Можно с цветом: <white>\\<gold>Староста",
                        "", "<green>Нажмите и напишите в чат"),
                p -> ask(p, "<yellow>Введите имя NPC (можно с цветом, например <white>\\<gold>Староста</white>):",
                        text -> draft.name = text));
        set(12, Icons.of(Material.PLAYER_HEAD, "<yellow>Скин",
                        "<gray>Сейчас: <white>" + (draft.hasSkin() ? "голова со скином" : "нет"),
                        "<gray>Скин ставится головой в слот шлема.",
                        "<gray>Напишите в чат ник игрока, ссылку",
                        "<gray>textures.minecraft.net или Value",
                        "<gray>с minecraft-heads.com / mineskin.org.",
                        "<gray><white>-</white> убрать скин",
                        "", "<green>Нажмите и напишите в чат"),
                this::askSkin);
        set(13, Icons.of(Material.IRON_CHESTPLATE, "<aqua>Броня и оружие",
                        "<gray>Предметов: <white>" + draft.equipment.size(),
                        "", "<green>Нажмите, чтобы настроить"),
                p -> new EquipmentMenu("Броня и оружие NPC", draft.equipment, this,
                        "<gray>Положите предмет в слот или сделайте",
                        "<gray>shift-клик по нему в своём инвентаре.",
                        "<gray>Слот шлема занимает голова со скином.").open(p));
        boolean villager = draft.type == EntityType.VILLAGER || draft.type == EntityType.ZOMBIE_VILLAGER;
        if (villager) {
            set(14, Icons.of(Material.LECTERN, "<yellow>Профессия: <white>" + label(PROFESSIONS, draft.profession),
                            "<gray>Меняет одежду жителя", "", "<green>Нажмите, чтобы переключить"),
                    p -> {
                        draft.profession = next(Registry.VILLAGER_PROFESSION, draft.profession);
                        redraw();
                    });
            set(15, Icons.of(Material.GRASS_BLOCK, "<yellow>Местность: <white>" + label(REGIONS, draft.villagerType),
                            "<gray>Меняет цвет одежды жителя", "", "<green>Нажмите, чтобы переключить"),
                    p -> {
                        draft.villagerType = next(Registry.VILLAGER_TYPE, draft.villagerType);
                        redraw();
                    });
        }
        set(16, Icons.of(Material.PAPER, "<yellow>Приветствие",
                        "<gray>Сейчас: <white>" + (draft.greeting.isBlank() ? "нет" : draft.greeting),
                        "<gray>NPC скажет это в чат при нажатии",
                        "<gray><white>-</white> убрать",
                        "", "<green>Нажмите и напишите в чат"),
                p -> ask(p, "<yellow>Что NPC говорит при нажатии? (<white>-</white> — молчит)",
                        text -> draft.greeting = text.equals("-") ? "" : text));

        set(20, Icons.of(Material.EMERALD, "<green>Торговля",
                        "<gray>Сделок: <white>" + draft.trades.size(),
                        "<gray>Если есть сделки, при нажатии на NPC",
                        "<gray>открывается окно торговли как у жителя",
                        "", "<green>Нажмите, чтобы настроить"),
                p -> new TradesMenu(module, this).open(p));
        NpcModule.QuestLink quests = module.questLink();
        if (quests != null && draft.id != null) {
            set(22, Icons.of(Material.WRITABLE_BOOK, "<gold>Квесты",
                            "<gray>Квестов у этого NPC: <white>" + quests.questCount(draft.id),
                            "", "<green>Нажмите, чтобы открыть"),
                    p -> quests.openQuests(p, draft.id, this));
        } else {
            set(22, Icons.of(Material.BOOK, "<gray>Квесты",
                    quests == null ? "<gray>Модуль квестов выключен" : "<gray>Сначала сохраните NPC"));
        }
        if (draft.id != null) {
            set(24, Icons.of(Material.COMPASS, "<aqua>Где стоит в мире",
                            "<gray>Точек: <white>" + module.registry().placementsOf(draft.id).size(),
                            "<gray>Телепорт, перенести, убрать",
                            "", "<green>Нажмите, чтобы открыть"),
                    p -> placements(module, draft.id, this).open(p));
        }

        set(36, Icons.of(Material.ARROW, "<gray>Назад"), p -> new NpcMainMenu(module).open(p));
        set(40, Icons.of(Material.LIME_CONCRETE, "<green>Сохранить",
                "<gray>NPC в мире обновятся сразу"), this::save);
        if (draft.id != null) {
            set(38, Icons.of(Material.ARMOR_STAND, "<aqua>Поставить NPC здесь",
                    "<gray>На вашем месте, лицом туда же,", "<gray>куда смотрите вы",
                    "<gray>(сохранённая версия)"), p -> {
                p.closeInventory();
                NpcPlacement placement = module.place(module.registry().get(draft.id), p.getLocation());
                module.plugin().messages().send(p, "<green>NPC поставлен (точка " + placement.id + ")."
                        + " <gray>Shift + ПКМ по NPC — настройки точки.");
            });
            set(44, Icons.of(confirmDelete ? Material.TNT : Material.BARRIER,
                            confirmDelete ? "<red><bold>Точно удалить?" : "<red>Удалить",
                            confirmDelete ? "<gray>Нажмите ещё раз для подтверждения" : "<gray>NPC исчезнет со всех точек"),
                    this::delete);
        }
    }

    private void save(Player player) {
        module.registry().save(draft);
        module.respawnAll(draft.id);
        module.plugin().messages().send(player, "<green>NPC сохранён: </green>" + draft.name
                + " <gray>(ID: " + draft.id + ")</gray>");
        redraw();
    }

    private void delete(Player player) {
        if (!confirmDelete) {
            confirmDelete = true;
            redraw();
            return;
        }
        module.deleteTemplate(draft.id);
        module.plugin().messages().send(player, "<yellow>NPC удалён.</yellow>");
        new NpcMainMenu(module).open(player);
    }

    private void chooseType(Player player) {
        List<ChoiceMenu.Choice> choices = new ArrayList<>();
        for (EntityType type : NpcModule.TYPES) {
            Material egg = type == EntityType.ARMOR_STAND ? Material.ARMOR_STAND : MobModule.eggMaterial(type);
            choices.add(new ChoiceMenu.Choice(Icons.of(egg != null ? egg : Material.EGG,
                    (type == draft.type ? "<green>" : "<yellow>") + NpcModule.typeName(type)), p -> {
                draft.type = type;
                open(p);
            }));
        }
        new ChoiceMenu("Кто этот NPC", choices, this).open(player);
    }

    private void askSkin(Player player) {
        module.plugin().chatInput().ask(player,
                "<yellow>Скин: напишите ник игрока, ссылку <white>textures.minecraft.net/texture/...</white>"
                        + " или значение текстуры (Value). <white>-</white> — убрать скин.",
                text -> {
                    if (text.equals("-")) {
                        if (draft.hasSkin()) {
                            draft.equipment.remove(EquipmentSlot.HEAD);
                        }
                        open(player);
                        return;
                    }
                    module.plugin().messages().send(player, "<gray>Ищу скин...");
                    NpcSkins.resolve(module.plugin(), text, head -> {
                        draft.equipment.put(EquipmentSlot.HEAD, head);
                        module.plugin().messages().send(player, "<green>Скин установлен. Не забудьте сохранить NPC.");
                        open(player);
                    }, error -> {
                        module.plugin().messages().send(player, "<red>" + error + "</red>");
                        open(player);
                    });
                }, () -> open(player));
    }

    void ask(Player player, String prompt, Consumer<String> apply) {
        module.plugin().chatInput().ask(player, prompt, text -> {
            apply.accept(text);
            open(player);
        }, () -> open(player));
    }

    static Menu placements(NpcModule module, String npcId, Menu back) {
        List<ChoiceMenu.Choice> choices = new ArrayList<>();
        for (NpcPlacement placement : module.registry().placementsOf(npcId)) {
            choices.add(new ChoiceMenu.Choice(Icons.of(Material.COMPASS, "<aqua>Точка " + placement.id,
                    "<gray>" + placement.coordsText(), "", "<green>Нажмите, чтобы открыть"),
                    p -> new PlacementMenu(module, placement, back).open(p)));
        }
        return new ChoiceMenu("Где стоит NPC", choices, back);
    }

    /** Следующее значение реестра по кругу (профессии, местности). */
    private static <T extends Keyed> String next(Registry<T> registry, String current) {
        List<String> keys = new ArrayList<>();
        for (T value : registry) {
            keys.add(value.getKey().toString());
        }
        keys.sort(null);
        if (keys.isEmpty()) {
            return current;
        }
        int index = keys.indexOf(current);
        return keys.get((index + 1) % keys.size());
    }

    private static String label(Map<String, String> labels, String key) {
        String path = key.contains(":") ? key.substring(key.indexOf(':') + 1) : key;
        return labels.getOrDefault(path, path);
    }

    static String itemName(ItemStack item) {
        return item.getAmount() + "x " + net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                .serialize(item.effectiveName());
    }
}
