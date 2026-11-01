package com.dathis.rpg.core.config;

import com.dathis.rpg.RpgPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;

/** Отправка сообщений в формате MiniMessage с общим префиксом. Geyser сам переводит цвета для Bedrock. */
public final class Messages {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final RpgPlugin plugin;
    private String prefix;

    public Messages(RpgPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        prefix = plugin.getConfig().getString("prefix", "<gold>[RPG]</gold> ");
    }

    public Component parse(String miniMessage) {
        return MM.deserialize(miniMessage);
    }

    public void send(CommandSender to, String miniMessage) {
        to.sendMessage(MM.deserialize(prefix + miniMessage));
    }
}
