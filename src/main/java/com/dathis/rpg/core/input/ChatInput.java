package com.dathis.rpg.core.input;

import com.dathis.rpg.RpgPlugin;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Ввод текста через чат: закрываем окно, просим написать значение, следующее сообщение игрока
 * не уходит в общий чат, а передаётся обработчику. Одинаково удобно на Java и Bedrock (включая телефон).
 */
public final class ChatInput implements Listener {

    private static final Set<String> CANCEL_WORDS = Set.of("отмена", "cancel");

    private record Request(Consumer<String> onInput, Runnable onCancel) {
    }

    private final RpgPlugin plugin;
    private final Map<UUID, Request> pending = new ConcurrentHashMap<>();

    public ChatInput(RpgPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * @param prompt   подсказка в формате MiniMessage
     * @param onInput  вызывается в основном потоке с введённым текстом
     * @param onCancel вызывается, если игрок написал «отмена»
     */
    public void ask(Player player, String prompt, Consumer<String> onInput, Runnable onCancel) {
        player.closeInventory();
        pending.put(player.getUniqueId(), new Request(onInput, onCancel));
        plugin.messages().send(player, prompt);
        plugin.messages().send(player, "<gray>Напишите значение в чат или <white>отмена</white>.</gray>");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Request request = pending.remove(event.getPlayer().getUniqueId());
        if (request == null) {
            return;
        }
        event.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (CANCEL_WORDS.contains(text.toLowerCase(Locale.ROOT))) {
                request.onCancel().run();
            } else {
                request.onInput().accept(text);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
    }
}
