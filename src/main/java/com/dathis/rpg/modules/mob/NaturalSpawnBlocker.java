package com.dathis.rpg.modules.mob;

import org.bukkit.entity.Animals;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;

import java.util.EnumSet;
import java.util.Set;

/**
 * Запрещает естественный спавн мобов (тот, что идёт сам по себе в темноте и зависит от сложности).
 * Мобы из яиц, команд, спавнеров и кастомные мобы плагина не затрагиваются.
 */
final class NaturalSpawnBlocker implements Listener {

    private static final Set<SpawnReason> NATURAL = EnumSet.of(
            SpawnReason.NATURAL,      // обычный спавн в темноте, фантомы
            SpawnReason.JOCKEY,       // наездники (паук-жокей и т.п.)
            SpawnReason.PATROL,       // патрули разбойников
            SpawnReason.REINFORCEMENTS, // зомби-подкрепления на сложной сложности
            SpawnReason.TRAP);        // ловушка со скелетами-всадниками в грозу

    private final boolean blockMonsters;
    private final boolean blockAnimals;

    NaturalSpawnBlocker(boolean blockMonsters, boolean blockAnimals) {
        this.blockMonsters = blockMonsters;
        this.blockAnimals = blockAnimals;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (!NATURAL.contains(event.getSpawnReason())) {
            return;
        }
        Entity entity = event.getEntity();
        if ((blockMonsters && entity instanceof Enemy) || (blockAnimals && entity instanceof Animals)) {
            event.setCancelled(true);
        }
    }
}
