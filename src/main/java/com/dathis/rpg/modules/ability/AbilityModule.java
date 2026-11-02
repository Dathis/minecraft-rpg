package com.dathis.rpg.modules.ability;

import com.dathis.rpg.RpgPlugin;
import com.dathis.rpg.core.gui.Icons;
import com.dathis.rpg.core.input.Numbers;
import com.dathis.rpg.core.module.AbstractModule;
import com.dathis.rpg.modules.mob.MobModule;
import com.dathis.rpg.modules.region.RegionModule;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Способности персонажа (этап 3 плана). Админ создаёт их в /create-ability: выбирает готовую механику
 * (полёт, прыжок, рывок, телепорт, высасывание жизни, обмен здоровьем) и настраивает числа, ману и перезарядку.
 *
 * Игрок применяет способность ПКМ рукой с руной (на Bedrock — обычное «использовать»). Перезарядка видна
 * числом предметов в стопке руны: 8 = ещё 8 секунд. Пока руна перезаряжается, её нельзя переложить или
 * выбросить (иначе стопку можно было бы разделить и получить лишние руны).
 */
public final class AbilityModule extends AbstractModule {

    /** Защита от двойного срабатывания: событие приходит для обеих рук, от ЛКМ и ПКМ, от клика по мобу. */
    private static final long CAST_DEBOUNCE_MS = 300;

    private AbilityRegistry registry;
    private Effects effects;
    private BukkitTask runeTask;
    /** Конец перезарядки (мс) по игроку и id способности. Живёт в памяти, переживает перезаход. */
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();
    private final Map<UUID, Long> lastCast = new HashMap<>();

    public AbilityModule() {
        super("abilities", "Способности персонажа");
    }

    @Override
    protected void onEnable() {
        registry = new AbilityRegistry(plugin);
        registry.load();
        effects = new Effects(this);
        listen(effects);
        listen(new RuneListener(this));
        // Раз в полсекунды: число в стопке руны = секунды перезарядки, обновить вид рун после правки шаблона
        runeTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickRunes, 10L, 10L);
        command("create-ability", new CreateAbilityCommand(this));
        command("abilities", new AbilitiesCommand(this));
    }

    @Override
    protected void onDisable() {
        if (runeTask != null) {
            runeTask.cancel();
            runeTask = null;
        }
        if (effects != null) {
            effects.stopAll();
        }
        // Без модуля стопки рун никто не вернёт к одной — делаем сейчас
        plugin.getServer().getOnlinePlayers().forEach(this::normalizeRunes);
        for (String name : List.of("create-ability", "abilities")) {
            PluginCommand command = plugin.getCommand(name);
            if (command != null) {
                command.setExecutor(null);
                command.setTabCompleter(null);
            }
        }
    }

    private void command(String name, TabExecutor executor) {
        PluginCommand command = plugin.getCommand(name);
        if (command != null) {
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }
    }

    RpgPlugin plugin() {
        return plugin;
    }

    AbilityRegistry registry() {
        return registry;
    }

    // ---------- применение ----------

    /**
     * Игрок нажал руной.
     *
     * @param clicked существо, по которому нажали (цель для высасывания и обмена), или null
     */
    void cast(Player player, ItemStack rune, LivingEntity clicked) {
        long now = System.currentTimeMillis();
        Long last = lastCast.get(player.getUniqueId());
        if (last != null && now - last < CAST_DEBOUNCE_MS) {
            return;
        }
        lastCast.put(player.getUniqueId(), now);

        String id = registry.idOf(rune);
        Ability ability = id == null ? null : registry.get(id);
        if (ability == null) {
            bar(player, "<red>Эта руна больше не работает");
            return;
        }
        if (plugin.getConfig().getStringList("abilities.disabled-worlds").contains(player.getWorld().getName())) {
            bar(player, "<red>Здесь способности не работают");
            return;
        }
        if (!plugin.levels().meets(player, ability.level)) {
            bar(player, "<red>" + AbilityRegistry.escape(ability.name) + ": нужен уровень " + ability.level);
            return;
        }
        long left = cooldownLeft(player, id);
        if (left > 0) {
            bar(player, "<yellow>" + AbilityRegistry.escape(ability.name) + ": ещё "
                    + Numbers.format(Math.ceil(left / 100.0) / 10.0) + " сек");
            return;
        }
        if (!plugin.mana().has(player, ability.manaCost)) {
            bar(player, "<blue>Не хватает маны: <white>" + Numbers.format(Math.floor(plugin.mana().mana(player)))
                    + " / " + Numbers.format(ability.manaCost));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.8f);
            return;
        }
        String fail = effects.cast(player, ability, clicked);
        if (fail != null) {
            bar(player, "<red>" + fail);
            return;
        }
        plugin.mana().spend(player, ability.manaCost);
        if (ability.cooldown > 0) {
            cooldowns.computeIfAbsent(player.getUniqueId(), u -> new HashMap<>())
                    .put(id, now + Math.round(ability.cooldown * 1000));
        }
        updateRunes(player);
    }

    void bar(Player player, String text) {
        player.sendActionBar(Icons.text(text));
    }

    /** Сколько мс осталось до готовности способности (0 — готова). */
    long cooldownLeft(Player player, String abilityId) {
        Map<String, Long> map = cooldowns.get(player.getUniqueId());
        Long until = map == null ? null : map.get(abilityId);
        return until == null ? 0 : Math.max(0, until - System.currentTimeMillis());
    }

    /** Перезаряжается ли руна: тогда её нельзя переложить или выбросить. */
    boolean isLocked(Player player, ItemStack stack) {
        String id = registry.idOf(stack);
        return id != null && (stack.getAmount() > 1 || cooldownLeft(player, id) > 0);
    }

    // ---------- руны в инвентаре ----------

    private void tickRunes() {
        long now = System.currentTimeMillis();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            Map<String, Long> map = cooldowns.get(player.getUniqueId());
            if (map != null) {
                for (Iterator<Map.Entry<String, Long>> it = map.entrySet().iterator(); it.hasNext(); ) {
                    Map.Entry<String, Long> entry = it.next();
                    if (entry.getValue() <= now) {
                        it.remove();
                        Ability ability = registry.get(entry.getKey());
                        // Короткие перезарядки без звука, чтобы не надоедало
                        if (ability != null && ability.cooldown >= 5) {
                            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.5f, 1.6f);
                        }
                    }
                }
                if (map.isEmpty()) {
                    cooldowns.remove(player.getUniqueId());
                }
            }
            updateRunes(player);
        }
    }

    /**
     * Число предметов в стопке каждой руны = секунды до готовности (1, когда готова).
     * Заодно обновляет вид рун, если админ поменял шаблон.
     */
    void updateRunes(Player player) {
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot <= 40; slot++) {
            ItemStack stack = inventory.getItem(slot);
            String id = registry.idOf(stack);
            if (id == null) {
                continue;
            }
            Ability ability = registry.get(id);
            boolean changed = false;
            if (ability != null) {
                ItemStack refreshed = registry.refresh(stack, ability);
                if (refreshed != null) {
                    stack = refreshed;
                    changed = true;
                }
            }
            long left = cooldownLeft(player, id);
            int amount = left > 0 ? (int) Math.max(1, Math.min(64, Math.ceil(left / 1000.0))) : 1;
            if (stack.getAmount() != amount) {
                stack.setAmount(amount);
                changed = true;
            }
            if (changed) {
                inventory.setItem(slot, stack);
            }
        }
    }

    /** Все руны снова по одной (выход игрока, выключение модуля). */
    void normalizeRunes(Player player) {
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot <= 40; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (registry.isRune(stack) && stack.getAmount() != 1) {
                stack.setAmount(1);
                inventory.setItem(slot, stack);
            }
        }
        ItemStack cursor = player.getItemOnCursor();
        if (registry.isRune(cursor) && cursor.getAmount() != 1) {
            cursor.setAmount(1);
            player.setItemOnCursor(cursor);
        }
    }

    void forget(Player player) {
        lastCast.remove(player.getUniqueId());
    }

    /** Выдаёт игроку руну способности. Если инвентарь полон, руна падает рядом. */
    boolean give(Player player, String id) {
        Ability ability = registry.get(id);
        if (ability == null) {
            return false;
        }
        player.getInventory().addItem(registry.rune(ability)).values()
                .forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
        return true;
    }

    // ---------- связи с другими модулями ----------

    /** Действуют ли способности на других игроков в этом мире. */
    boolean affectsPlayers(Player caster) {
        return plugin.getConfig().getBoolean("abilities.affect-players", true) && caster.getWorld().getPVP();
    }

    boolean isBoss(Entity entity) {
        if (!plugin.modules().isEnabled("mobs")) {
            return false;
        }
        return plugin.modules().get("mobs").map(m -> ((MobModule) m).isBoss(entity)).orElse(false);
    }

    boolean isForeignPlot(Player player, Location location) {
        if (!plugin.modules().isEnabled("regions")) {
            return false;
        }
        return plugin.modules().get("regions").map(m -> ((RegionModule) m).isForeignPlot(player, location)).orElse(false);
    }

    /** Слот компаса-меню в хотбаре (0-8) или -1, если компаса нет. Руны туда не кладём. */
    int compassSlot() {
        if (!plugin.modules().isEnabled("menu") || !plugin.getConfig().getBoolean("menu.compass", true)) {
            return -1;
        }
        return Math.max(1, Math.min(9, plugin.getConfig().getInt("menu.compass-slot", 9))) - 1;
    }
}
