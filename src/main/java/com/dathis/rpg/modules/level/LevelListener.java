package com.dathis.rpg.modules.level;

import com.dathis.rpg.core.gui.Icons;
import com.dathis.rpg.core.progression.LevelService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * Опыт за убийство мобов, полоска опыта и ник в табе.
 *
 * Ванильная полоска опыта показывает наш уровень (цифра) и долю до следующего (полоска), поэтому
 * ванильный опыт (сферы, бутылочки, руда) в этом режиме отключён, а стол зачарования не работает
 * (иначе он брал бы «уровни», которые сразу возвращаются).
 */
final class LevelListener implements Listener {

    private final LevelModule module;

    LevelListener(LevelModule module) {
        this.module = module;
    }

    /** Поставить полоску опыта и ник в табе по нашему уровню. */
    void refresh(Player player) {
        LevelService levels = module.levels();
        int level = levels.level(player);
        if (module.useXpBar()) {
            float progress = (float) Math.max(0, Math.min(1, levels.progress(player)));
            if (player.getLevel() != level) {
                player.setLevel(level);
            }
            if (Math.abs(player.getExp() - progress) > 0.001f) {
                player.setExp(progress);
            }
        }
        if (module.showInTab()) {
            Component name = Component.text("[" + level + "] ", NamedTextColor.GRAY)
                    .append(Component.text(player.getName(), NamedTextColor.WHITE));
            if (!name.equals(player.playerListName())) {
                player.playerListName(name);
            }
        }
    }

    /** Модуль выключается — вернуть обычные ники в табе. */
    void restoreAll() {
        if (!module.showInTab()) {
            return;
        }
        for (Player player : module.plugin().getServer().getOnlinePlayers()) {
            player.playerListName(null);
        }
    }

    // ---------- опыт за мобов ----------

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onKill(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity instanceof Player) {
            return;
        }
        int vanillaXp = event.getDroppedExp();
        if (module.useXpBar()) {
            // Сферы ванильного опыта в этом режиме ничего не дают, поэтому не роняем их вовсе
            event.setDroppedExp(0);
        }
        Player killer = entity.getKiller();
        if (killer == null) {
            return;
        }
        LevelService levels = module.levels();
        LevelService.MobLevel mob = levels.mobLevel(entity);
        if (mob == null) {
            // Обычный ванильный моб: по желанию админа даёт свой ванильный опыт
            if (module.plugin().getConfig().getBoolean("levels.vanilla-mob-xp", false) && vanillaXp > 0) {
                levels.giveXp(killer, vanillaXp, null);
            }
            return;
        }
        int playerLevel = levels.level(killer);
        double multiplier = levels.killMultiplier(playerLevel, mob.level());
        long xp = Math.round(mob.xp() * multiplier);
        String note = null;
        if (multiplier < 1) {
            String percent = Math.round(multiplier * 100) + "%";
            note = mob.level() > playerLevel
                    ? "моб намного сильнее вас, " + percent
                    : "моб намного слабее вас, " + percent;
        }
        if (xp > 0) {
            levels.giveXp(killer, xp, note);
        } else if (mob.xp() > 0) {
            killer.sendActionBar(Icons.text("<gray>Опыта нет: " + (mob.level() > playerLevel
                    ? "моб намного сильнее вас" : "моб намного слабее вас")));
        }
    }

    // ---------- ванильный опыт ----------

    @EventHandler(priority = EventPriority.HIGH)
    public void onVanillaXp(PlayerExpChangeEvent event) {
        if (module.useXpBar()) {
            event.setAmount(0);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (module.useXpBar()) {
            // Полоска — наш уровень: не теряем и не роняем его при смерти
            event.setDroppedExp(0);
            event.setKeepLevel(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        if (module.useXpBar() && module.plugin().getConfig().getBoolean("levels.block-enchanting", true)) {
            event.setCancelled(true);
            module.plugin().messages().send(event.getEnchanter(),
                    "<red>Зачарование за уровни на этом сервере отключено.</red>");
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        module.plugin().getServer().getScheduler().runTask(module.plugin(), () -> {
            if (player.isOnline()) {
                refresh(player);
            }
        });
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        module.plugin().getServer().getScheduler().runTask(module.plugin(), () -> {
            if (player.isOnline()) {
                refresh(player);
            }
        });
    }
}
