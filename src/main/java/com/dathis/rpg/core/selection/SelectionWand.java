package com.dathis.rpg.core.selection;

import com.dathis.rpg.RpgPlugin;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Палочка выделения: удар по блоку — первый угол, ПКМ по блоку — второй угол.
 * Пока палочка в руке, границы выделения подсвечиваются частицами.
 * Общая для всех модулей (зоны спавна мобов, области квестов «Разведать»).
 */
public final class SelectionWand implements Listener, Runnable {

    public static final class Selection {
        public Location first;
        public Location second;

        public boolean complete() {
            return first != null && second != null && first.getWorld().equals(second.getWorld());
        }
    }

    private final RpgPlugin plugin;
    private final NamespacedKey wandKey;
    private final Map<UUID, Selection> selections = new HashMap<>();

    public SelectionWand(RpgPlugin plugin) {
        this.plugin = plugin;
        this.wandKey = new NamespacedKey(plugin, "selection_wand");
    }

    public ItemStack create() {
        ItemStack wand = new ItemStack(Material.BLAZE_ROD);
        wand.editMeta(meta -> {
            meta.displayName(com.dathis.rpg.core.gui.Icons.text("<gold>Палочка выделения"));
            meta.lore(java.util.List.of(
                    com.dathis.rpg.core.gui.Icons.text("<gray>Удар по блоку — первый угол"),
                    com.dathis.rpg.core.gui.Icons.text("<gray>ПКМ по блоку — второй угол"),
                    com.dathis.rpg.core.gui.Icons.text("<gray>Для зон спавна, квестов и зон мира")));
            meta.addItemFlags(ItemFlag.values());
            meta.getPersistentDataContainer().set(wandKey, PersistentDataType.BYTE, (byte) 1);
        });
        return wand;
    }

    public Selection selection(Player player) {
        return selections.get(player.getUniqueId());
    }

    private boolean isWand(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(wandKey, PersistentDataType.BYTE);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !isWand(event.getItem()) || event.getClickedBlock() == null) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.hasPermission("rpg.admin")) {
            return;
        }
        Location location = event.getClickedBlock().getLocation();
        Selection selection = selections.computeIfAbsent(player.getUniqueId(), id -> new Selection());
        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            selection.first = location;
            plugin.messages().send(player, "<green>Первый угол: <white>" + coords(location) + size(selection));
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            selection.second = location;
            plugin.messages().send(player, "<green>Второй угол: <white>" + coords(location) + size(selection));
        } else {
            return;
        }
        event.setCancelled(true);
    }

    /** Чтобы в творческом режиме удар палочкой не ломал блок. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onBreak(BlockBreakEvent event) {
        if (isWand(event.getPlayer().getInventory().getItemInMainHand())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        selections.remove(event.getPlayer().getUniqueId());
    }

    /** Подсветка выделения для игроков с палочкой в руке. */
    @Override
    public void run() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            Selection selection = selections.get(player.getUniqueId());
            if (selection == null || !isWand(player.getInventory().getItemInMainHand())) {
                continue;
            }
            if (selection.complete()) {
                outline(player, selection.first, selection.second, Particle.HAPPY_VILLAGER);
            } else {
                Location single = selection.first != null ? selection.first : selection.second;
                if (single != null) {
                    outline(player, single, single, Particle.HAPPY_VILLAGER);
                }
            }
        }
    }

    /** Рисует рёбра коробки частицами, видимыми только этому игроку. */
    public static void outline(Player player, Location a, Location b, Particle particle) {
        World world = a.getWorld();
        if (world == null || !world.equals(player.getWorld())) {
            return;
        }
        double x1 = Math.min(a.getBlockX(), b.getBlockX()), x2 = Math.max(a.getBlockX(), b.getBlockX()) + 1;
        double y1 = Math.min(a.getBlockY(), b.getBlockY()), y2 = Math.max(a.getBlockY(), b.getBlockY()) + 1;
        double z1 = Math.min(a.getBlockZ(), b.getBlockZ()), z2 = Math.max(a.getBlockZ(), b.getBlockZ()) + 1;
        double longest = Math.max(x2 - x1, Math.max(y2 - y1, z2 - z1));
        double step = Math.max(0.5, longest / 40.0);
        for (double x = x1; x <= x2; x += step) {
            dot(player, particle, x, y1, z1); dot(player, particle, x, y2, z1);
            dot(player, particle, x, y1, z2); dot(player, particle, x, y2, z2);
        }
        for (double y = y1; y <= y2; y += step) {
            dot(player, particle, x1, y, z1); dot(player, particle, x2, y, z1);
            dot(player, particle, x1, y, z2); dot(player, particle, x2, y, z2);
        }
        for (double z = z1; z <= z2; z += step) {
            dot(player, particle, x1, y1, z); dot(player, particle, x2, y1, z);
            dot(player, particle, x1, y2, z); dot(player, particle, x2, y2, z);
        }
    }

    /** Подсвечивает коробку частицами FLAME на 10 секунд (кнопка «Показать границы» в меню зон). */
    public static void showFor(RpgPlugin plugin, Player player, Location a, Location b) {
        if (a.getWorld() == null) {
            return;
        }
        new org.bukkit.scheduler.BukkitRunnable() {
            private int times;

            @Override
            public void run() {
                if (!player.isOnline() || ++times > 20) {
                    cancel();
                    return;
                }
                outline(player, a, b, Particle.FLAME);
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }

    private static void dot(Player player, Particle particle, double x, double y, double z) {
        player.spawnParticle(particle, x, y, z, 1, 0, 0, 0, 0);
    }

    private static String coords(Location l) {
        return l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ();
    }

    private static String size(Selection s) {
        if (!s.complete()) {
            return "";
        }
        int dx = Math.abs(s.first.getBlockX() - s.second.getBlockX()) + 1;
        int dy = Math.abs(s.first.getBlockY() - s.second.getBlockY()) + 1;
        int dz = Math.abs(s.first.getBlockZ() - s.second.getBlockZ()) + 1;
        return " <gray>(область " + dx + "x" + dy + "x" + dz + ")";
    }
}
