package com.dathis.rpg.core.platform;

import com.dathis.rpg.RpgPlugin;
import org.bukkit.entity.Player;
import org.geysermc.floodgate.api.FloodgateApi;

import java.util.UUID;

/**
 * Отличает Bedrock-игроков (через Floodgate) от Java-игроков.
 *
 * Правило для всех модулей: интерфейс должен работать у обоих.
 * Используем сундучные меню (Inventory GUI), чат, титулы, боссбары, NPC-сущности.
 * Не используем то, чего нет у Bedrock: кастомные шрифты/ресурспаки как единственный способ показать
 * информацию, книги с кликабельным текстом как единственный способ выбора, клиентские моды.
 * Для Bedrock позже можно добавить нативные формы Floodgate (Cumulus) там, где это удобнее.
 */
public final class PlatformService {

    private final boolean floodgate;

    public PlatformService(RpgPlugin plugin) {
        this.floodgate = plugin.getServer().getPluginManager().getPlugin("floodgate") != null;
    }

    public boolean isFloodgateAvailable() {
        return floodgate;
    }

    public boolean isBedrock(Player player) {
        return isBedrock(player.getUniqueId());
    }

    public boolean isBedrock(UUID uuid) {
        if (!floodgate) {
            return false;
        }
        return FloodgateApi.getInstance().isFloodgatePlayer(uuid);
    }
}
