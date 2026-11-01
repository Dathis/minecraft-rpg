package com.dathis.rpg.modules.quest;

import com.dathis.rpg.modules.mob.MobModule;
import com.dathis.rpg.modules.npc.NpcInteractEvent;
import com.dathis.rpg.modules.npc.NpcTemplate;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Нажатие на NPC (разговор, окно квестов) и убийства для целей «Убить». */
final class QuestListener implements Listener {

    private final QuestModule module;

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

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        module.progress().unload(event.getPlayer().getUniqueId());
    }
}
