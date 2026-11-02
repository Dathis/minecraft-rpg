package com.dathis.rpg.modules.ability;

import com.dathis.rpg.RpgPlugin;
import com.dathis.rpg.core.gui.Icons;
import com.dathis.rpg.core.input.Numbers;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Все способности (plugins/MinecraftRPG/data/abilities.yml) и руны — предметы, через которые их применяют.
 *
 * На руне лежат только id способности, ревизия шаблона и случайный id самой руны (чтобы руны не складывались
 * в стопку: число предметов в стопке показывает перезарядку). Числа берутся из шаблона вживую.
 */
final class AbilityRegistry {

    private static final String FILE = "abilities";

    private final RpgPlugin plugin;
    private final NamespacedKey idKey;
    private final NamespacedKey runeKey;
    private final NamespacedKey revKey;
    private final Map<String, Ability> abilities = new LinkedHashMap<>();

    AbilityRegistry(RpgPlugin plugin) {
        this.plugin = plugin;
        this.idKey = new NamespacedKey(plugin, "ability_id");
        this.runeKey = new NamespacedKey(plugin, "ability_rune");
        this.revKey = new NamespacedKey(plugin, "ability_rev");
    }

    void load() {
        abilities.clear();
        ConfigurationSection root = plugin.store().get(FILE).getConfigurationSection("abilities");
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            Ability ability = section == null ? null : Ability.load(id, section);
            if (ability != null) {
                abilities.put(id, ability);
            } else {
                plugin.getLogger().warning("Способность " + id + " пропущена: неизвестная механика");
            }
        }
    }

    void save(Ability ability) {
        if (ability.id == null) {
            ability.id = nextId();
        }
        ability.rev++;
        abilities.put(ability.id, ability.copy());
        writeFile();
    }

    void delete(String id) {
        abilities.remove(id);
        writeFile();
    }

    Ability get(String id) {
        return abilities.get(id);
    }

    List<Ability> all() {
        return new ArrayList<>(abilities.values());
    }

    // ---------- руны ----------

    /** id способности на предмете или null, если это не руна. */
    String idOf(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasItemMeta()) {
            return null;
        }
        return stack.getItemMeta().getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
    }

    boolean isRune(ItemStack stack) {
        return idOf(stack) != null;
    }

    /** Новая руна способности. */
    ItemStack rune(Ability ability) {
        ItemStack stack = new ItemStack(ability.material);
        stack.editMeta(meta -> {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(idKey, PersistentDataType.STRING, ability.id == null ? "draft" : ability.id);
            pdc.set(runeKey, PersistentDataType.STRING, UUID.randomUUID().toString());
            decorate(meta, ability);
        });
        return stack;
    }

    /**
     * Если админ поменял шаблон после выдачи руны — обновляет её вид (предмет, название, описание).
     *
     * @return обновлённая руна, если что-то поменялось, иначе null
     */
    ItemStack refresh(ItemStack stack, Ability ability) {
        Integer rev = stack.getItemMeta().getPersistentDataContainer().get(revKey, PersistentDataType.INTEGER);
        if (rev != null && rev == ability.rev && stack.getType() == ability.material) {
            return null;
        }
        ItemStack updated = stack.getType() == ability.material ? stack.clone() : stack.withType(ability.material);
        updated.editMeta(meta -> decorate(meta, ability));
        return updated;
    }

    private void decorate(ItemMeta meta, Ability ability) {
        NamedTextColor color = NamedTextColor.NAMES.value(ability.nameColor);
        meta.displayName(Component.text(ability.name, color == null ? NamedTextColor.LIGHT_PURPLE : color)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lines = new ArrayList<>();
        for (String line : describe(ability)) {
            lines.add(Icons.text(line));
        }
        meta.lore(lines);
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(revKey, PersistentDataType.INTEGER, ability.rev);
    }

    /** Описание руны: текст админа (или механики), числа и подсказка, как применять. */
    static List<String> describe(Ability ability) {
        List<String> lines = new ArrayList<>();
        if (ability.lore.isEmpty()) {
            lines.addAll(Icons.wrap(ability.mechanic.description, 36, "<gray>"));
        } else {
            for (String line : ability.lore) {
                lines.add("<gray>" + escape(line));
            }
        }
        lines.add("");
        lines.add("<gray>Мана: <aqua>" + Numbers.format(ability.manaCost));
        lines.add("<gray>Перезарядка: <white>" + Numbers.format(ability.cooldown) + " сек");
        for (Mechanic.Param param : ability.mechanic.params) {
            lines.add("<gray>" + param.label() + ": <white>" + Numbers.format(ability.get(param.stat()))
                    + (param.unit().isEmpty() ? "" : " " + param.unit()));
        }
        if (ability.level > 1) {
            lines.add("<gray>Нужен уровень: <gold>" + ability.level);
        }
        lines.add("");
        lines.add("<green>Возьмите в руку и нажмите ПКМ");
        lines.add("<dark_gray>На телефоне: нажмите по экрану");
        return lines;
    }

    /** Текст админа без тегов MiniMessage. */
    static String escape(String text) {
        return text.replace("<", "").replace(">", "");
    }

    private String nextId() {
        int n = 1;
        while (abilities.containsKey("ability" + n)) {
            n++;
        }
        return "ability" + n;
    }

    private void writeFile() {
        YamlConfiguration yaml = plugin.store().get(FILE);
        yaml.set("abilities", null);
        ConfigurationSection root = yaml.createSection("abilities");
        for (Ability ability : abilities.values()) {
            ability.save(root.createSection(ability.id));
        }
        plugin.store().save(FILE);
    }
}
