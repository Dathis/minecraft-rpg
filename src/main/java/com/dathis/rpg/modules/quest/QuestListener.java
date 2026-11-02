package com.dathis.rpg.modules.quest;

import com.dathis.rpg.modules.mob.MobModule;
import com.dathis.rpg.modules.npc.NpcInteractEvent;
import com.dathis.rpg.modules.npc.NpcTemplate;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Нажатие на NPC (разговор, окно квестов), доску заданий, убийства для «Убить»,
 * сломанные блоки для «Добыть» и крафт для «Скрафтить».
 */
final class QuestListener implements Listener {

    /** Сколько последних поставленных игроками блоков помнить (их ломание не засчитывается). */
    private static final int PLACED_LIMIT = 20_000;
    /** Посевы: засчитываются только созревшие (тростник и кактус — как обычные блоки). */
    private static final Set<org.bukkit.Material> CROPS = java.util.EnumSet.of(org.bukkit.Material.WHEAT,
            org.bukkit.Material.CARROTS, org.bukkit.Material.POTATOES, org.bukkit.Material.BEETROOTS,
            org.bukkit.Material.NETHER_WART, org.bukkit.Material.COCOA, org.bukkit.Material.SWEET_BERRY_BUSH,
            org.bukkit.Material.TORCHFLOWER_CROP, org.bukkit.Material.PITCHER_CROP);

    private final QuestModule module;
    /** Блоки, поставленные игроками с момента запуска: «поставил-сломал» не должно выполнять «Добыть». */
    private final Set<String> placed = new LinkedHashSet<>();

    QuestListener(QuestModule module) {
        this.module = module;
    }

    @EventHandler
    public void onNpc(NpcInteractEvent event) {
        Player player = event.getPlayer();
        NpcTemplate npc = event.getNpc();
        // «Поговорить»: засчитываем разговор с этим NPC
        for (Quest quest : module.activeQuests(player)) {
            for (int i = 0; i < quest.objectives.size(); i++) {
                Objective objective = quest.objectives.get(i);
                if (objective.type == Objective.Type.TALK && npc.id().equals(objective.target)) {
                    module.advance(player, quest, i, 1);
                }
            }
        }
        if (!module.registry().ofNpc(npc.id()).isEmpty()) {
            event.setCancelled(true);
            new QuestBoardMenu(module, player, npc).open(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        Player killer = dead.getKiller();
        if (killer == null) {
            return;
        }
        MobModule mobs = module.mobs();
        String mobId = mobs == null ? null : mobs.mobIdOf(dead);
        String key = mobId != null ? "mob:" + mobId : "type:" + dead.getType().name();
        for (Quest quest : module.activeQuests(killer)) {
            for (int i = 0; i < quest.objectives.size(); i++) {
                Objective objective = quest.objectives.get(i);
                if (objective.type == Objective.Type.KILL && key.equals(objective.target)) {
                    module.advance(killer, quest, i, 1);
                }
            }
        }
    }

    // ---------- доска заданий ----------

    /** Нажатие (ПКМ, на Bedrock — тап) по блоку-доске открывает доску. Раньше защиты мира, чтобы она не мешала. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onBoard(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null || event.getAction() != Action.RIGHT_CLICK_BLOCK || !module.registry().isBoard(block)) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        new QuestBoardMenu(module, player, null, QuestBoardMenu.Kind.BOARD).open(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBoardBroken(BlockBreakEvent event) {
        if (module.registry().isBoard(event.getBlock())) {
            module.registry().removeBoard(QuestRegistry.key(event.getBlock()));
            module.plugin().messages().send(event.getPlayer(), "<yellow>Доска заданий убрана (блок сломан).");
        }
    }

    // ---------- «Добыть» ----------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (CROPS.contains(block.getType())) {
            return; // посевы засчитываются только созревшими, их ставить можно
        }
        placed.add(QuestRegistry.key(block));
        if (placed.size() > PLACED_LIMIT) {
            Iterator<String> oldest = placed.iterator();
            oldest.next();
            oldest.remove();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMine(BlockBreakEvent event) {
        Block block = event.getBlock();
        boolean placedByPlayer = placed.remove(QuestRegistry.key(block));
        Player player = event.getPlayer();
        if (placedByPlayer || player.getGameMode() == org.bukkit.GameMode.CREATIVE) {
            return;
        }
        BlockData data = block.getBlockData();
        if (CROPS.contains(block.getType()) && data instanceof Ageable crop && crop.getAge() < crop.getMaximumAge()) {
            return;
        }
        for (Quest quest : module.activeQuests(player)) {
            for (int i = 0; i < quest.objectives.size(); i++) {
                Objective objective = quest.objectives.get(i);
                if (objective.type == Objective.Type.MINE && objective.block() == block.getType()) {
                    module.advance(player, quest, i, 1);
                }
            }
        }
    }

    // ---------- «Скрафтить» ----------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ItemStack result = event.getCurrentItem();
        if (result == null || result.isEmpty()) {
            return;
        }
        for (Quest quest : module.activeQuests(player)) {
            for (int i = 0; i < quest.objectives.size(); i++) {
                Objective objective = quest.objectives.get(i);
                if (objective.type != Objective.Type.CRAFT || !module.matches(objective.item, result)) {
                    continue;
                }
                int index = i;
                if (event.isShiftClick()) {
                    // Shift-клик крафтит сколько получится: считаем, сколько прибавилось в инвентаре через тик
                    int before = module.countItems(player, objective.item);
                    module.plugin().getServer().getScheduler().runTask(module.plugin(), () -> {
                        int crafted = module.countItems(player, objective.item) - before;
                        if (crafted > 0 && player.isOnline()) {
                            module.advance(player, quest, index, crafted);
                        }
                    });
                } else {
                    module.advance(player, quest, index, result.getAmount());
                }
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        module.progress().unload(event.getPlayer().getUniqueId());
    }
}
