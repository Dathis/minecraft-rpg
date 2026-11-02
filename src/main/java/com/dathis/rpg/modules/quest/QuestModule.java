package com.dathis.rpg.modules.quest;

import com.dathis.rpg.RpgPlugin;
import com.dathis.rpg.core.gui.ChoiceMenu;
import com.dathis.rpg.core.gui.Icons;
import com.dathis.rpg.core.gui.Menu;
import com.dathis.rpg.core.module.AbstractModule;
import com.dathis.rpg.modules.mob.MobModule;
import com.dathis.rpg.modules.npc.NpcModule;
import com.dathis.rpg.modules.npc.NpcTemplate;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.command.PluginCommand;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;

/**
 * Квесты. Админ создаёт их в /create-quest (или из редактора NPC) и назначает NPC-квестодателю.
 * Игрок нажимает на NPC и видит книги квестов:
 * <ul>
 *     <li>книга с пером — квест доступен;</li>
 *     <li>книга с пером, зачарованная — квест взят;</li>
 *     <li>книга, зачарованная — квест выполнен;</li>
 *     <li>книга без зачарования — квест пока недоступен.</li>
 * </ul>
 * Цели: убить мобов, принести предметы, поговорить с NPC, разведать область. Сдаётся квест тому же NPC.
 */
public final class QuestModule extends AbstractModule {

    enum Status { LOCKED, AVAILABLE, ACTIVE, READY, COMPLETED }

    private static final MiniMessage MM = MiniMessage.miniMessage();
    static final int WRAP = 36;

    private QuestRegistry registry;
    private QuestProgress progress;
    private NamespacedKey itemIdKey;
    private final List<BukkitTask> tasks = new ArrayList<>();

    public QuestModule() {
        super("quests", "Квесты");
    }

    @Override
    protected void onEnable() {
        registry = new QuestRegistry(plugin);
        registry.load();
        progress = new QuestProgress(plugin);
        itemIdKey = new NamespacedKey(plugin, "item_id");
        listen(new QuestListener(this));
        tasks.add(plugin.getServer().getScheduler().runTaskTimer(plugin, this::checkExplore, 20L, 10L));
        tasks.add(plugin.getServer().getScheduler().runTaskTimer(plugin, progress::flush, 600L, 600L));

        NpcModule npcs = npcs();
        if (npcs != null) {
            npcs.setQuestLink(new NpcModule.QuestLink() {
                @Override
                public int questCount(String npcId) {
                    return registry.ofNpc(npcId).size();
                }

                @Override
                public void openQuests(Player player, String npcId, Menu back) {
                    npcQuestsMenu(player, npcId, back).open(player);
                }
            });
        } else {
            plugin.getLogger().warning("Модуль NPC выключен: квесты некому выдавать.");
        }

        command("create-quest", new CreateQuestCommand(this));
        command("quests", new CreateQuestCommand.QuestsCommand(this));
    }

    @Override
    protected void onDisable() {
        tasks.forEach(BukkitTask::cancel);
        tasks.clear();
        progress.flush();
        NpcModule npcs = npcs();
        if (npcs != null) {
            npcs.setQuestLink(null);
        }
        for (String name : List.of("create-quest", "quests")) {
            PluginCommand command = plugin.getCommand(name);
            if (command != null) {
                command.setExecutor(null);
                command.setTabCompleter(null);
            }
        }
    }

    private void command(String name, org.bukkit.command.TabExecutor executor) {
        PluginCommand command = plugin.getCommand(name);
        if (command != null) {
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }
    }

    RpgPlugin plugin() {
        return plugin;
    }

    QuestRegistry registry() {
        return registry;
    }

    QuestProgress progress() {
        return progress;
    }

    /** Модуль NPC, если включён. */
    NpcModule npcs() {
        if (!plugin.modules().isEnabled("npc")) {
            return null;
        }
        return plugin.modules().get("npc").map(m -> (NpcModule) m).orElse(null);
    }

    /** Модуль мобов, если включён (он включается позже квестов, поэтому берём каждый раз). */
    MobModule mobs() {
        if (!plugin.modules().isEnabled("mobs")) {
            return null;
        }
        return plugin.modules().get("mobs").map(m -> (MobModule) m).orElse(null);
    }

    // ---------- состояние квеста для игрока ----------

    Status status(Player player, Quest quest) {
        QuestProgress.PlayerQuests data = progress.get(player.getUniqueId());
        if (data.completed.contains(quest.id)) {
            return Status.COMPLETED;
        }
        if (data.active.containsKey(quest.id)) {
            return allDone(player, quest) ? Status.READY : Status.ACTIVE;
        }
        return lockReason(player, quest) == null ? Status.AVAILABLE : Status.LOCKED;
    }

    /** Почему квест нельзя взять (MiniMessage), или null, если можно. */
    String lockReason(Player player, Quest quest) {
        if (!quest.ready()) {
            return "<red>Квест ещё не настроен";
        }
        if (quest.requiredQuest != null) {
            Quest required = registry.get(quest.requiredQuest);
            if (required != null && !progress.get(player.getUniqueId()).completed.contains(required.id)) {
                return "<red>Сначала выполните: <white>" + required.name;
            }
        }
        if (!plugin.levels().meets(player, quest.requiredLevel)) {
            return "<red>Нужен уровень: <white>" + quest.requiredLevel;
        }
        return null;
    }

    /** Текущий прогресс цели. «Принести» считается по инвентарю. */
    int count(Player player, Quest quest, int index) {
        Objective objective = quest.objectives.get(index);
        if (objective.type == Objective.Type.BRING) {
            return Math.min(objective.amount, countItems(player, objective.item));
        }
        int[] counts = progress.counts(progress.get(player.getUniqueId()), quest);
        return counts == null ? 0 : Math.min(objective.amount, counts[index]);
    }

    boolean allDone(Player player, Quest quest) {
        for (int i = 0; i < quest.objectives.size(); i++) {
            if (count(player, quest, i) < quest.objectives.get(i).amount) {
                return false;
            }
        }
        return true;
    }

    // ---------- действия игрока ----------

    void accept(Player player, Quest quest) {
        if (status(player, quest) != Status.AVAILABLE) {
            return;
        }
        QuestProgress.PlayerQuests data = progress.get(player.getUniqueId());
        data.active.put(quest.id, new int[quest.objectives.size()]);
        progress.changed(player.getUniqueId());
        plugin.messages().send(player, "<yellow>Квест взят: <gold>" + quest.name);
        for (Objective objective : quest.objectives) {
            player.sendMessage(Icons.text(" <yellow>- " + objectiveText(objective, -1)));
        }
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 1f);
        // «Разведать», если игрок уже стоит в области, засчитается следующей проверкой
    }

    void abandon(Player player, Quest quest) {
        QuestProgress.PlayerQuests data = progress.get(player.getUniqueId());
        if (data.active.remove(quest.id) != null) {
            progress.changed(player.getUniqueId());
            plugin.messages().send(player, "<gray>Вы отказались от квеста «" + quest.name + "».");
        }
    }

    boolean turnIn(Player player, Quest quest) {
        if (status(player, quest) != Status.READY) {
            return false;
        }
        for (Objective objective : quest.objectives) {
            if (objective.type == Objective.Type.BRING) {
                takeItems(player, objective.item, objective.amount);
            }
        }
        QuestProgress.PlayerQuests data = progress.get(player.getUniqueId());
        data.active.remove(quest.id);
        data.completed.add(quest.id);
        progress.changed(player.getUniqueId());
        for (ItemStack reward : quest.rewards) {
            player.getInventory().addItem(reward.clone()).values()
                    .forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
        }
        if (quest.rewardXp > 0) {
            if (plugin.levels().isActive()) {
                plugin.levels().giveXp(player, quest.rewardXp, "квест");
            } else {
                player.giveExp(quest.rewardXp);
            }
        }
        player.showTitle(Title.title(Icons.text("<gold>Квест выполнен"), Icons.text("<yellow>" + quest.name)));
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        plugin.messages().send(player, "<green>Квест выполнен: <gold>" + quest.name);
        return true;
    }

    // ---------- прогресс целей ----------

    /** Добавить прогресс цели и сообщить игроку. */
    void advance(Player player, Quest quest, int index, int by) {
        Objective objective = quest.objectives.get(index);
        int[] counts = progress.counts(progress.get(player.getUniqueId()), quest);
        if (counts == null || counts[index] >= objective.amount) {
            return;
        }
        counts[index] = Math.min(objective.amount, counts[index] + by);
        progress.changed(player.getUniqueId());
        String line = objectiveText(objective, counts[index]);
        player.sendActionBar(Icons.text(line));
        if (counts[index] >= objective.amount) {
            plugin.messages().send(player, "<green>Цель выполнена: </green>" + line);
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
            if (allDone(player, quest)) {
                NpcTemplate npc = npcs() == null ? null : npcs().npc(quest.npcId);
                plugin.messages().send(player, "<gold>Квест «" + quest.name + "» можно сдать"
                        + (npc != null ? ": вернитесь к </gold>" + npc.name() : "</gold>"));
            }
        }
    }

    /** Активные квесты игрока (шаблоны, которые ещё существуют). */
    List<Quest> activeQuests(Player player) {
        List<Quest> list = new ArrayList<>();
        for (String id : progress.get(player.getUniqueId()).active.keySet()) {
            Quest quest = registry.get(id);
            if (quest != null) {
                list.add(quest);
            }
        }
        return list;
    }

    private void checkExplore() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            for (Quest quest : activeQuests(player)) {
                for (int i = 0; i < quest.objectives.size(); i++) {
                    Objective objective = quest.objectives.get(i);
                    if (objective.type == Objective.Type.EXPLORE && objective.contains(player.getLocation())) {
                        advance(player, quest, i, 1);
                    }
                }
            }
        }
    }

    // ---------- предметы ----------

    private String itemId(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        return stack.getItemMeta().getPersistentDataContainer().get(itemIdKey, PersistentDataType.STRING);
    }

    /** Подходит ли предмет: кастомный — по id шаблона, обычный — по материалу. */
    boolean matches(ItemStack wanted, ItemStack stack) {
        if (wanted == null || stack == null || stack.isEmpty()) {
            return false;
        }
        String wantedId = itemId(wanted);
        if (wantedId != null) {
            return wantedId.equals(itemId(stack));
        }
        return stack.getType() == wanted.getType() && itemId(stack) == null;
    }

    int countItems(Player player, ItemStack wanted) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (matches(wanted, stack)) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    private void takeItems(Player player, ItemStack wanted, int amount) {
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int i = 0; i < contents.length && amount > 0; i++) {
            ItemStack stack = contents[i];
            if (matches(wanted, stack)) {
                int take = Math.min(amount, stack.getAmount());
                stack.setAmount(stack.getAmount() - take);
                amount -= take;
                contents[i] = stack.getAmount() <= 0 ? null : stack;
            }
        }
        player.getInventory().setStorageContents(contents);
    }

    // ---------- тексты и иконки ----------

    static String itemName(ItemStack item) {
        return MM.serialize(item.effectiveName());
    }

    String targetName(Objective objective) {
        switch (objective.type) {
            case KILL -> {
                if (objective.target == null) {
                    return "<gray>(не выбрано)";
                }
                if (objective.target.startsWith("mob:")) {
                    MobModule mobs = mobs();
                    MobModule.MobInfo info = mobs == null ? null : mobs.mobInfo(objective.target.substring(4));
                    return info == null ? "<gray>(моб удалён)" : MM.serialize(info.name());
                }
                try {
                    EntityType type = EntityType.valueOf(objective.target.substring(objective.target.indexOf(':') + 1));
                    return "<lang:" + type.translationKey() + ">";
                } catch (IllegalArgumentException e) {
                    return "<gray>(неизвестный моб)";
                }
            }
            case BRING -> {
                return objective.item == null ? "<gray>(не выбрано)" : itemName(objective.item);
            }
            case TALK -> {
                NpcModule npcs = npcs();
                NpcTemplate npc = npcs == null || objective.target == null ? null : npcs.npc(objective.target);
                return npc == null ? "<gray>(не выбрано)" : npc.name();
            }
            default -> {
                return objective.areaName;
            }
        }
    }

    /**
     * Строка цели: «Убить Воришка 0/5», «Добыть и принести 20x Пшеница 0/20».
     * Жёлтая, пока не выполнена, зелёная — когда выполнена. progress = -1 — без счётчика.
     */
    String objectiveText(Objective objective, int progress) {
        String name = targetName(objective);
        boolean done = progress >= objective.amount;
        String color = done ? "<green>" : "<yellow>";
        String counter = progress < 0 ? "" : " " + progress + "/" + objective.amount;
        return switch (objective.type) {
            case KILL -> color + "Убить " + name + color + counter;
            case BRING -> color + "Добыть и принести " + objective.amount + "x " + name + color + counter;
            case TALK -> color + "Поговорить: " + name + color + (done ? " (готово)" : "");
            case EXPLORE -> color + "Разведать: " + name + color + (done ? " (готово)" : "");
        };
    }

    /** Книга квеста со всем текстом: слова NPC, задание жёлтым, награда. player = null — без прогресса. */
    ItemStack book(Player player, Quest quest) {
        Status status = player == null ? Status.AVAILABLE : status(player, quest);
        List<String> lore = new ArrayList<>();
        lore.add(switch (status) {
            case AVAILABLE -> "<green>Доступен";
            case ACTIVE -> "<aqua>Выполняется";
            case READY -> "<green><bold>Можно сдать";
            case COMPLETED -> "<dark_green>Выполнен";
            case LOCKED -> "<red>Недоступен";
        });
        NpcModule npcs = npcs();
        NpcTemplate npc = npcs == null || quest.npcId == null ? null : npcs.npc(quest.npcId);
        if (npc != null) {
            lore.add("<gray>Квестодатель: </gray>" + npc.name());
        }
        if (!quest.description.isEmpty()) {
            lore.add("");
            for (String paragraph : quest.description) {
                lore.addAll(Icons.wrap(paragraph, WRAP, "<white>"));
            }
        }
        lore.add("");
        lore.add("<gold>Задание:");
        for (int i = 0; i < quest.objectives.size(); i++) {
            Objective objective = quest.objectives.get(i);
            int count = player == null || status == Status.AVAILABLE || status == Status.LOCKED ? 0
                    : status == Status.COMPLETED ? objective.amount : count(player, quest, i);
            lore.add(objectiveText(objective, count));
        }
        if (quest.objectives.isEmpty()) {
            lore.add("<gray>(целей нет)");
        }
        if (!quest.rewards.isEmpty() || quest.rewardXp > 0) {
            lore.add("");
            lore.add("<gold>Награда:");
            for (ItemStack reward : quest.rewards) {
                lore.add("<white>" + reward.getAmount() + "x " + itemName(reward));
            }
            if (quest.rewardXp > 0) {
                lore.add("<white>Опыт: " + quest.rewardXp);
            }
        }
        if (quest.requiredLevel > 1) {
            lore.add("<gray>С уровня: " + quest.requiredLevel);
        }
        if (status == Status.LOCKED && player != null) {
            lore.add("");
            lore.add(lockReason(player, quest));
        }
        Material material = status == Status.COMPLETED || status == Status.LOCKED ? Material.BOOK : Material.WRITABLE_BOOK;
        boolean glint = status == Status.ACTIVE || status == Status.READY || status == Status.COMPLETED;
        ItemStack book = Icons.of(material, "<gold>" + quest.name, lore);
        if (glint) {
            book.editMeta(meta -> {
                meta.setEnchantmentGlintOverride(true);
                // Для Bedrock надёжнее настоящее (скрытое) зачарование
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            });
        }
        return book;
    }

    /** Список квестов NPC для админа + «Новый квест». */
    Menu npcQuestsMenu(Player player, String npcId, Menu back) {
        List<ChoiceMenu.Choice> choices = new ArrayList<>();
        choices.add(new ChoiceMenu.Choice(Icons.of(Material.LIME_CONCRETE, "<green>Новый квест для этого NPC"), p -> {
            Quest quest = new Quest();
            quest.npcId = npcId;
            new QuestEditorMenu(this, quest, back).open(p);
        }));
        for (Quest quest : registry.ofNpc(npcId)) {
            choices.add(new ChoiceMenu.Choice(Icons.withHint(book(null, quest), "", "<dark_gray>ID: " + quest.id,
                    "<green>Нажмите, чтобы изменить"), p -> new QuestEditorMenu(this, quest.copy(), back).open(p)));
        }
        return new ChoiceMenu("Квесты NPC", choices, back);
    }
}
