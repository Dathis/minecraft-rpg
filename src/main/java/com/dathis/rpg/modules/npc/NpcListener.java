package com.dathis.rpg.modules.npc;

import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Нажатие на NPC, защита NPC от урона, превращений и прочего. */
final class NpcListener implements Listener {

    private final NpcModule module;
    /** Защита от двойного срабатывания (клиент может прислать два события на одно нажатие). */
    private final Map<UUID, Long> lastClick = new HashMap<>();

    NpcListener(NpcModule module) {
        this.module = module;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (!module.isNpc(event.getRightClicked())) {
            return;
        }
        // Отменяем всегда: никакой ванильной торговли жителя, поводков, бирок и т.п.
        event.setCancelled(true);
        if (event.getHand() == EquipmentSlot.HAND) {
            click(event.getPlayer(), event.getRightClicked());
        }
    }

    /** Стойка для брони присылает только это событие. */
    @EventHandler(priority = EventPriority.LOW)
    public void onInteractAt(PlayerInteractAtEntityEvent event) {
        if (!module.isNpc(event.getRightClicked())) {
            return;
        }
        event.setCancelled(true);
        if (event.getRightClicked() instanceof ArmorStand && event.getHand() == EquipmentSlot.HAND) {
            click(event.getPlayer(), event.getRightClicked());
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onArmorStand(PlayerArmorStandManipulateEvent event) {
        if (module.isNpc(event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    private void click(Player player, Entity entity) {
        long now = System.currentTimeMillis();
        Long last = lastClick.put(player.getUniqueId(), now);
        if (last != null && now - last < 300) {
            return;
        }
        NpcTemplate npc = module.templateOf(entity);
        if (npc == null) {
            return;
        }
        NpcPlacement placement = module.placementOf(entity);
        // Окна открываем на следующем тике, не внутри события
        module.plugin().getServer().getScheduler().runTask(module.plugin(), () -> {
            if (player.hasPermission("rpg.admin") && player.isSneaking() && placement != null) {
                new PlacementMenu(module, placement, null).open(player);
                return;
            }
            if (!npc.greeting.isBlank()) {
                player.sendMessage(npc.displayName().append(module.plugin().messages().parse("<gray>: </gray><white>" + npc.greeting)));
            }
            NpcInteractEvent interact = new NpcInteractEvent(player, npc);
            module.plugin().getServer().getPluginManager().callEvent(interact);
            if (!interact.isCancelled() && npc.hasTrades()) {
                module.openTrade(player, npc);
            }
        });
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onDamage(EntityDamageEvent event) {
        if (module.isNpc(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onCombust(EntityCombustEvent event) {
        if (module.isNpc(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    /** Зомби в воде, пиглин в обычном мире, житель от молнии — NPC не превращаются. */
    @EventHandler(priority = EventPriority.LOW)
    public void onTransform(EntityTransformEvent event) {
        if (module.isNpc(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onTarget(EntityTargetEvent event) {
        if (module.isNpc(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    /** Если в чанке нашлась старая копия NPC (не та, что числится за точкой) — убираем. */
    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (module.isNpc(entity) && !module.isCurrent(entity)) {
                entity.remove();
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastClick.remove(event.getPlayer().getUniqueId());
    }
}
