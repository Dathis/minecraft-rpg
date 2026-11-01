package com.dathis.rpg.modules.region;

import com.dathis.rpg.core.gui.Icons;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Ravager;
import org.bukkit.entity.Silverfish;
import org.bukkit.entity.Wither;
import org.bukkit.entity.WitherSkull;
import org.bukkit.entity.Zombie;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Защита мира. Игроки без права rpg.region.bypass (по умолчанию — все, кроме OP) не ломают, не ставят
 * и не открывают блоки. Исключения:
 * - зона строительства: внутри всё как в обычном Minecraft;
 * - зона добычи: блоки из правил ломаются, добыча идёт в инвентарь, на месте ставится замена, блок может вернуться.
 */
final class ProtectionListener implements Listener {

    private final RegionModule module;
    private final Map<UUID, Long> lastMessage = new HashMap<>();

    ProtectionListener(RegionModule module) {
        this.module = module;
    }

    // ---------- Блоки ----------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        if (module.areas().inBuildZone(block)) {
            return;
        }
        BlockRule rule = miningRule(player, block, block.getType());
        if (rule != null) {
            mine(event, rule);
            return;
        }
        if (!restricted(player, block)) {
            return;
        }
        event.setCancelled(true);
        deny(player, "Здесь нельзя ломать блоки");
    }

    /** Добыча из зоны добычи падает сразу в инвентарь (что не влезло — под ноги). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(BlockDropItemEvent event) {
        if (!module.dropsToInventory()) {
            return;
        }
        Player player = event.getPlayer();
        Block block = event.getBlock();
        if (module.areas().inBuildZone(block) || miningRule(player, block, event.getBlockState().getType()) == null) {
            return;
        }
        for (Item item : event.getItems()) {
            for (ItemStack left : player.getInventory().addItem(item.getItemStack()).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), left);
            }
        }
        event.getItems().clear();
    }

    /** Правило для этого ломания или null. Админ в творческом режиме ломает как обычно (строит карту). */
    private BlockRule miningRule(Player player, Block block, Material type) {
        if (player.getGameMode() == GameMode.CREATIVE && player.hasPermission(RegionModule.BYPASS)) {
            return null;
        }
        return module.areas().ruleAt(block, type);
    }

    private void mine(BlockBreakEvent event, BlockRule rule) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        BlockData original = block.getBlockData().clone();
        if (module.dropsToInventory() && event.getExpToDrop() > 0) {
            player.giveExp(event.getExpToDrop(), true);
            event.setExpToDrop(0);
        }
        Material replacement = rule.replacement;
        int regen = rule.regenSeconds;
        // Блок исчезает после события, поэтому замену ставим на следующем тике
        module.plugin().getServer().getScheduler().runTask(module.plugin(), () -> {
            if (replacement != null) {
                block.setType(replacement, false);
            }
            if (regen > 0) {
                module.regen().schedule(block, original, regen);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (restricted(event.getPlayer(), event.getBlockPlaced())) {
            event.setCancelled(true);
            deny(event.getPlayer(), "Здесь нельзя строить");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (restricted(event.getPlayer(), event.getBlock())) {
            event.setCancelled(true);
            deny(event.getPlayer(), "Здесь нельзя выливать");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (restricted(event.getPlayer(), event.getBlockClicked())) {
            event.setCancelled(true);
            deny(event.getPlayer(), "Здесь нельзя набирать");
        }
    }

    @SuppressWarnings("deprecation") // Material.isInteractable: нам хватает его точности
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        Player player = event.getPlayer();
        if (block == null || !restricted(player, block)) {
            return;
        }
        Material type = block.getType();
        if (event.getAction() == Action.PHYSICAL) {
            // Не вытаптывать грядки и яйца
            if (type == Material.FARMLAND || type == Material.TURTLE_EGG || type == Material.SNIFFER_EGG) {
                event.setCancelled(true);
            }
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (type.isInteractable() && !module.canUse(type)) {
            event.setUseInteractedBlock(Event.Result.DENY);
            if (block.getState() instanceof InventoryHolder) {
                deny(player, "Здесь нельзя открывать");
            }
        }
        ItemStack item = event.getItem();
        if (item != null && changesBlocks(item.getType())) {
            event.setUseItemInHand(Event.Result.DENY);
        }
    }

    /** Предметы, которые меняют блок по ПКМ без установки: топор (обтёсывание), мотыга, лопата, костная мука и т.д. */
    private static boolean changesBlocks(Material item) {
        return Tag.ITEMS_AXES.isTagged(item) || Tag.ITEMS_HOES.isTagged(item) || Tag.ITEMS_SHOVELS.isTagged(item)
                || item == Material.BONE_MEAL || item == Material.FLINT_AND_STEEL || item == Material.FIRE_CHARGE
                || item == Material.HONEYCOMB || item == Material.BRUSH || item == Material.SHEARS;
    }

    // ---------- Сущности-блоки: рамки, картины, стойки ----------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        Player player = playerOf(event.getRemover());
        Block block = event.getEntity().getLocation().getBlock();
        if (player != null ? restricted(player, block)
                : module.protects(block.getWorld()) && !module.areas().inBuildZone(block)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingBreakOther(HangingBreakEvent event) {
        if (event instanceof HangingBreakByEntityEvent || event.getCause() != HangingBreakEvent.RemoveCause.EXPLOSION) {
            return;
        }
        Block block = event.getEntity().getLocation().getBlock();
        if (module.blockExplosions() && module.protects(block.getWorld()) && !module.areas().inBuildZone(block)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        if (event.getPlayer() != null && restricted(event.getPlayer(), event.getBlock())) {
            event.setCancelled(true);
            deny(event.getPlayer(), "Здесь нельзя строить");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityPlace(EntityPlaceEvent event) {
        EntityType type = event.getEntityType();
        if (event.getPlayer() != null && (type == EntityType.ARMOR_STAND || type == EntityType.END_CRYSTAL)
                && restricted(event.getPlayer(), event.getBlock())) {
            event.setCancelled(true);
            deny(event.getPlayer(), "Здесь нельзя строить");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getRightClicked() instanceof Hanging hanging
                && restricted(event.getPlayer(), hanging.getLocation().getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent event) {
        if (restricted(event.getPlayer(), event.getRightClicked().getLocation().getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamageDecoration(EntityDamageByEntityEvent event) {
        Entity target = event.getEntity();
        if (!(target instanceof Hanging) && !(target instanceof ArmorStand)) {
            return;
        }
        Player player = playerOf(event.getDamager());
        if (player != null && restricted(player, target.getLocation().getBlock())) {
            event.setCancelled(true);
        }
    }

    // ---------- Мир: взрывы, огонь, мобы ----------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (module.blockExplosions()) {
            event.blockList().removeIf(this::worldProtected);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (module.blockExplosions()) {
            event.blockList().removeIf(this::worldProtected);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (module.blockFire() && worldProtected(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        Player player = event.getPlayer();
        if (player != null) {
            if (restricted(player, event.getBlock())) {
                event.setCancelled(true);
            }
        } else if (module.blockFire() && worldProtected(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        Entity entity = event.getEntity();
        Block block = event.getBlock();
        if (entity instanceof Player player) {
            if (block.getType() == Material.FARMLAND && restricted(player, block)) {
                event.setCancelled(true);
            }
            return;
        }
        boolean griefer = entity instanceof Enderman || entity instanceof Ravager || entity instanceof Silverfish
                || entity instanceof Wither || entity instanceof WitherSkull || entity instanceof Zombie
                || block.getType() == Material.FARMLAND || block.getType() == Material.TURTLE_EGG;
        if (griefer && module.blockMobGriefing() && worldProtected(block)) {
            event.setCancelled(true);
        }
    }

    /** Пока идёт добыча, листва в зонах добычи не осыпается (иначе дерево облысеет до возврата брёвен). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLeavesDecay(LeavesDecayEvent event) {
        if (module.keepLeaves() && module.areas().inMiningArea(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastMessage.remove(event.getPlayer().getUniqueId());
    }

    // ---------- Общее ----------

    /** Запрещено ли игроку менять этот блок. */
    private boolean restricted(Player player, Block block) {
        return !player.hasPermission(RegionModule.BYPASS) && worldProtected(block);
    }

    /** Блок под защитой: мир защищён и блок не в зоне строительства. */
    private boolean worldProtected(Block block) {
        return module.protects(block.getWorld()) && !module.areas().inBuildZone(block);
    }

    private static Player playerOf(Entity entity) {
        if (entity instanceof Player player) {
            return player;
        }
        if (entity instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }

    /** Сообщение в action bar, не чаще раза в секунду. */
    private void deny(Player player, String text) {
        if (!module.denyMessages()) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastMessage.get(player.getUniqueId());
        if (last != null && now - last < 1000) {
            return;
        }
        lastMessage.put(player.getUniqueId(), now);
        player.sendActionBar(Icons.text("<red>" + text));
    }
}
