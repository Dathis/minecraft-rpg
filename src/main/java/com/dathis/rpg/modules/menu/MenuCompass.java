package com.dathis.rpg.modules.menu;

import com.dathis.rpg.core.gui.Icons;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Компас-меню в хотбаре (по умолчанию 9-й слот). Нажатие компасом (ПКМ или ЛКМ, на телефоне — тап)
 * или клик по нему в инвентаре открывает /menu. На Bedrock это удобнее команды.
 *
 * Компас нельзя выбросить, переложить, положить в сундук, рамку или стойку; при смерти он не выпадает
 * и выдаётся снова после возрождения. Предмет помечен PDC {@code menu_item}.
 * Игрок может убрать компас в настройках меню (флаг PDC {@code menu_compass_hidden}).
 */
final class MenuCompass implements Listener {

    /** Защита от двойного срабатывания (событие приходит для обеих рук и от ЛКМ + ПКМ). */
    private static final long OPEN_COOLDOWN_MS = 400;

    private final MenuModule module;
    private final NamespacedKey itemKey;
    private final NamespacedKey hiddenKey;
    private final Map<UUID, Long> lastOpen = new HashMap<>();

    MenuCompass(MenuModule module) {
        this.module = module;
        this.itemKey = new NamespacedKey(module.plugin(), "menu_item");
        this.hiddenKey = new NamespacedKey(module.plugin(), "menu_compass_hidden");
    }

    // ---------- предмет ----------

    ItemStack create() {
        ItemStack stack = Icons.of(Material.COMPASS, "<gold><bold>Меню",
                "<gray>Персонаж, квесты, участки, настройки",
                "",
                "<green>Нажмите этим предметом,",
                "<green>чтобы открыть меню",
                "<dark_gray>Или команда /menu");
        stack.editMeta(meta -> meta.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1));
        return stack;
    }

    boolean isCompass(ItemStack stack) {
        return stack != null && stack.getType() == Material.COMPASS && stack.hasItemMeta()
                && stack.getItemMeta().getPersistentDataContainer().has(itemKey, PersistentDataType.BYTE);
    }

    /** Хочет ли игрок компас (по умолчанию да). */
    boolean wanted(Player player) {
        return !player.getPersistentDataContainer().has(hiddenKey, PersistentDataType.BYTE);
    }

    void setWanted(Player player, boolean wanted) {
        if (wanted) {
            player.getPersistentDataContainer().remove(hiddenKey);
            give(player);
        } else {
            player.getPersistentDataContainer().set(hiddenKey, PersistentDataType.BYTE, (byte) 1);
            remove(player);
        }
    }

    void checkAll() {
        for (Player player : module.plugin().getServer().getOnlinePlayers()) {
            give(player);
        }
    }

    /**
     * Кладёт компас в его слот, лишние копии убирает. Если слот занят другим предметом, переносит
     * предмет в свободное место; если места нет — ждёт (проверка повторится).
     */
    void give(Player player) {
        if (!module.compassEnabled() || !wanted(player)) {
            remove(player);
            return;
        }
        if (player.isDead()) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        int slot = module.compassSlot();
        ItemStack[] contents = inventory.getStorageContents();
        for (int i = 0; i < contents.length; i++) {
            if (i != slot && isCompass(contents[i])) {
                inventory.setItem(i, null);
            }
        }
        if (isCompass(inventory.getItemInOffHand())) {
            inventory.setItemInOffHand(null);
        }
        ItemStack current = inventory.getItem(slot);
        if (isCompass(current)) {
            if (current.getAmount() != 1) {
                current.setAmount(1);
            }
            return;
        }
        if (current != null && !current.isEmpty()) {
            int free = freeSlot(inventory, slot);
            if (free < 0) {
                return; // некуда переложить — попробуем позже
            }
            inventory.setItem(free, current);
        }
        inventory.setItem(slot, create());
    }

    private static int freeSlot(PlayerInventory inventory, int except) {
        ItemStack[] contents = inventory.getStorageContents();
        // Сначала основной инвентарь (9-35), потом хотбар
        for (int i = 9; i < contents.length; i++) {
            if (contents[i] == null || contents[i].isEmpty()) {
                return i;
            }
        }
        for (int i = 0; i < 9; i++) {
            if (i != except && (contents[i] == null || contents[i].isEmpty())) {
                return i;
            }
        }
        return -1;
    }

    void remove(Player player) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getStorageContents();
        for (int i = 0; i < contents.length; i++) {
            if (isCompass(contents[i])) {
                inventory.setItem(i, null);
            }
        }
        if (isCompass(inventory.getItemInOffHand())) {
            inventory.setItemInOffHand(null);
        }
        if (isCompass(player.getItemOnCursor())) {
            player.setItemOnCursor(null);
        }
    }

    private void open(Player player) {
        long now = System.currentTimeMillis();
        Long last = lastOpen.get(player.getUniqueId());
        if (last != null && now - last < OPEN_COOLDOWN_MS) {
            return;
        }
        lastOpen.put(player.getUniqueId(), now);
        // Открывать окно прямо в событии нельзя — на следующем тике
        module.plugin().getServer().getScheduler().runTask(module.plugin(), () -> {
            if (player.isOnline()) {
                module.openMain(player);
            }
        });
    }

    // ---------- события ----------

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        module.plugin().getServer().getScheduler().runTaskLater(module.plugin(), () -> {
            if (player.isOnline()) {
                give(player);
            }
        }, 5L);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        module.plugin().getServer().getScheduler().runTask(module.plugin(), () -> {
            if (player.isOnline()) {
                give(player);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastOpen.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.PHYSICAL || !isCompass(event.getItem())) {
            return;
        }
        // Компас не работает как обычный (магнетит и т.п.) и не ломает блоки
        event.setCancelled(true);
        open(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        ItemStack hand = event.getHand() == EquipmentSlot.OFF_HAND
                ? event.getPlayer().getInventory().getItemInOffHand()
                : event.getPlayer().getInventory().getItemInMainHand();
        // NPC и мобы — как обычно, а в рамку компас не кладём
        if (isCompass(hand) && event.getRightClicked() instanceof ItemFrame) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onArmorStand(PlayerArmorStandManipulateEvent event) {
        if (isCompass(event.getPlayerItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (isCompass(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (isCompass(event.getMainHandItem()) || isCompass(event.getOffHandItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        boolean current = isCompass(event.getCurrentItem());
        boolean cursor = isCompass(event.getCursor());
        boolean hotbar = event.getClick() == ClickType.NUMBER_KEY && event.getHotbarButton() >= 0
                && isCompass(player.getInventory().getItem(event.getHotbarButton()));
        boolean offhand = event.getClick() == ClickType.SWAP_OFFHAND && isCompass(player.getInventory().getItemInOffHand());
        if (!current && !cursor && !hotbar && !offhand) {
            return;
        }
        event.setCancelled(true);
        // Клик по компасу в своём инвентаре (не в сундуке) — открыть меню. На Bedrock так проще всего.
        if (current && event.getView().getTopInventory().getType() == InventoryType.CRAFTING
                && (event.getClick() == ClickType.LEFT || event.getClick() == ClickType.RIGHT)) {
            open(player);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (isCompass(event.getOldCursor())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        event.getDrops().removeIf(this::isCompass);
        event.getItemsToKeep().removeIf(this::isCompass);
    }
}
