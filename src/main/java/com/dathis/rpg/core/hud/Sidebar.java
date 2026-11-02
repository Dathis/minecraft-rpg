package com.dathis.rpg.core.hud;

import com.dathis.rpg.RpgPlugin;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Общие данные боковой панели (scoreboard справа экрана). Саму панель рисует модуль hud,
 * а другие модули добавляют в неё свои строки через {@link Section}: квесты — текущую цель,
 * позже экономика — деньги. Так модуль hud не знает про остальные модули.
 *
 * Игрок может выключить панель в настройках /menu (флаг в PDC {@code hud_sidebar}).
 */
public final class Sidebar {

    /** Кусок панели. Строки — MiniMessage, пустая строка — отступ. */
    public interface Section {
        void lines(Player player, List<String> out);
    }

    private record Entry(int order, Section section) {
    }

    private final NamespacedKey hiddenKey;
    private final List<Entry> sections = new CopyOnWriteArrayList<>();
    private boolean available;

    public Sidebar(RpgPlugin plugin) {
        this.hiddenKey = new NamespacedKey(plugin, "hud_sidebar_hidden");
    }

    /**
     * Добавить кусок панели. order — место сверху вниз: уровень/ХП/мана рисует сам hud (0..),
     * деньги — 20, квест — 50.
     */
    public void add(int order, Section section) {
        sections.add(new Entry(order, section));
    }

    public void remove(Section section) {
        sections.removeIf(e -> e.section() == section);
    }

    /** Строки всех кусков по порядку. */
    public List<String> sectionLines(Player player) {
        List<Entry> sorted = new ArrayList<>(sections);
        sorted.sort(Comparator.comparingInt(Entry::order));
        List<String> out = new ArrayList<>();
        for (Entry entry : sorted) {
            entry.section().lines(player, out);
        }
        return out;
    }

    /** Показывается ли панель вообще (модуль hud включён и hud.sidebar.enabled). */
    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    /** Хочет ли игрок видеть панель (по умолчанию да). */
    public boolean isShownTo(Player player) {
        return !player.getPersistentDataContainer().has(hiddenKey, PersistentDataType.BYTE);
    }

    public void setShownTo(Player player, boolean shown) {
        if (shown) {
            player.getPersistentDataContainer().remove(hiddenKey);
        } else {
            player.getPersistentDataContainer().set(hiddenKey, PersistentDataType.BYTE, (byte) 1);
        }
    }
}
