package com.dathis.rpg.core.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Окно-меню в виде сундука. Работает и в Java, и в Bedrock (Geyser показывает его как обычный сундук).
 *
 * Кнопки реагируют только на обычный клик (тап на Bedrock): правый клик и shift-клик на телефоне неудобны,
 * поэтому на них ничего не завязываем.
 */
public abstract class Menu implements InventoryHolder {

    private final Inventory inventory;
    private final Map<Integer, Consumer<Player>> actions = new HashMap<>();

    protected Menu(int rows, Component title) {
        this.inventory = Bukkit.createInventory(this, rows * 9, title);
    }

    /** Заполняет окно кнопками. Вызывается при каждом открытии и обновлении. */
    protected abstract void render();

    public void open(Player player) {
        redraw();
        player.openInventory(inventory);
    }

    /** Перерисовать окно, не закрывая его. */
    public void redraw() {
        inventory.clear();
        actions.clear();
        render();
    }

    protected void set(int slot, ItemStack icon) {
        inventory.setItem(slot, icon);
    }

    protected void set(int slot, ItemStack icon, Consumer<Player> action) {
        inventory.setItem(slot, icon);
        actions.put(slot, action);
    }

    protected int size() {
        return inventory.getSize();
    }

    void click(Player player, int slot) {
        Consumer<Player> action = actions.get(slot);
        if (action != null) {
            action.accept(player);
        }
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
