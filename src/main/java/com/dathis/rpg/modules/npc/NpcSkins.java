package com.dathis.rpg.modules.npc;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Скин NPC — голова игрока с нужной текстурой в слоте шлема. Админ пишет в чат одно из:
 * <ul>
 *     <li>ник игрока с лицензией: {@code Notch} (скин скачивается у Mojang один раз и сохраняется);</li>
 *     <li>ссылку на текстуру: {@code http://textures.minecraft.net/texture/<hash>};</li>
 *     <li>значение текстуры (base64) — его дают сайты с головами (minecraft-heads.com: поле «Value»)
 *     и mineskin.org.</li>
 * </ul>
 */
final class NpcSkins {

    private static final Pattern NICK = Pattern.compile("[A-Za-z0-9_]{3,16}");
    private static final String TEXTURE_HOST = "textures.minecraft.net/texture/";

    private NpcSkins() {
    }

    /** Разбирает ввод админа. Ответ приходит в основном потоке. */
    static void resolve(Plugin plugin, String input, Consumer<ItemStack> onHead, Consumer<String> onError) {
        String text = input.trim();
        if (NICK.matcher(text).matches()) {
            PlayerProfile profile = Bukkit.createProfile(text);
            profile.update().whenComplete((result, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                String texture = null;
                if (error == null && result instanceof PlayerProfile paper) {
                    for (ProfileProperty property : paper.getProperties()) {
                        if (property.getName().equals("textures")) {
                            texture = property.getValue();
                        }
                    }
                }
                if (texture == null) {
                    onError.accept("Не удалось получить скин игрока " + text + " (нет такого лицензионного ника или нет связи с Mojang).");
                } else {
                    onHead.accept(head(texture));
                }
            }));
            return;
        }
        int at = text.indexOf(TEXTURE_HOST);
        if (at >= 0) {
            String hash = text.substring(at + TEXTURE_HOST.length()).replaceAll("[^0-9a-fA-F]", "");
            if (hash.isEmpty()) {
                onError.accept("В ссылке нет кода текстуры.");
                return;
            }
            String json = "{\"textures\":{\"SKIN\":{\"url\":\"http://" + TEXTURE_HOST + hash + "\"}}}";
            onHead.accept(head(Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8))));
            return;
        }
        try {
            String decoded = new String(Base64.getDecoder().decode(text), StandardCharsets.UTF_8);
            if (decoded.contains("textures")) {
                onHead.accept(head(text));
                return;
            }
        } catch (IllegalArgumentException ignored) {
            // не base64
        }
        onError.accept("Не понял. Напишите ник, ссылку textures.minecraft.net или значение текстуры (Value).");
    }

    /** Голова игрока с текстурой. */
    static ItemStack head(String texture) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        head.editMeta(SkullMeta.class, meta -> {
            PlayerProfile profile = Bukkit.createProfile(UUID.nameUUIDFromBytes(texture.getBytes(StandardCharsets.UTF_8)), null);
            profile.setProperty(new ProfileProperty("textures", texture));
            meta.setPlayerProfile(profile);
        });
        return head;
    }
}
