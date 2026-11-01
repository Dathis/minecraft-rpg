package com.dathis.rpg.core.storage;

import org.bukkit.inventory.ItemStack;

import java.util.Base64;

/** ItemStack целиком (со всеми компонентами) в строку base64 и обратно — для YAML-файлов. */
public final class ItemCodec {

    private ItemCodec() {
    }

    public static String encode(ItemStack item) {
        return Base64.getEncoder().encodeToString(item.serializeAsBytes());
    }

    /** null, если строка пустая или повреждена. */
    public static ItemStack decode(Object data) {
        if (data == null) {
            return null;
        }
        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(String.valueOf(data)));
        } catch (Exception e) {
            return null;
        }
    }
}
