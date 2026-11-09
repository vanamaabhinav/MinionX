package com.minionx.util;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

/**
 * Centralized utility for extracting, normalizing, and applying skull skin textures
 * across minion armor stands, in-game creator studios, item stacks, and menus.
 */
@SuppressWarnings("deprecation")
public final class SkullUtil {

    private SkullUtil() {
    }

    /**
     * Extracts the skin texture string from any player head item.
     * Supports Paper PlayerProfile, Bukkit OwnerProfile, GameProfile reflection,
     * skin URLs, and player usernames.
     *
     * @param head Head item stack
     * @return Extracted Base64 texture or username, or {@code null} if none found
     */
    public static String extractTexture(ItemStack head) {
        if (head == null || head.getType() != Material.PLAYER_HEAD) {
            return null;
        }
        if (!head.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = head.getItemMeta();
        if (!(meta instanceof SkullMeta skullMeta)) {
            return null;
        }

        // 1. Try Paper PlayerProfile properties
        try {
            PlayerProfile profile = skullMeta.getPlayerProfile();
            if (profile != null) {
                for (ProfileProperty prop : profile.getProperties()) {
                    if ("textures".equalsIgnoreCase(prop.getName()) && prop.getValue() != null && !prop.getValue().isEmpty()) {
                        return prop.getValue();
                    }
                }
                // Try Paper PlayerTextures skin URL
                try {
                    org.bukkit.profile.PlayerTextures pt = profile.getTextures();
                    if (pt != null && pt.getSkin() != null) {
                        return urlToBase64(pt.getSkin().toString());
                    }
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }

        // 2. Try Bukkit OwnerProfile (modern Spigot / Paper)
        try {
            org.bukkit.profile.PlayerProfile ownerProfile = skullMeta.getOwnerProfile();
            if (ownerProfile != null) {
                org.bukkit.profile.PlayerTextures pt = ownerProfile.getTextures();
                if (pt != null && pt.getSkin() != null) {
                    return urlToBase64(pt.getSkin().toString());
                }
            }
        } catch (Throwable ignored) {
        }

        // 3. Try Reflection on CraftSkullMeta.profile (GameProfile multimap)
        try {
            Field profileField = skullMeta.getClass().getDeclaredField("profile");
            profileField.setAccessible(true);
            Object gameProfile = profileField.get(skullMeta);
            if (gameProfile != null) {
                Method getPropertiesMethod = gameProfile.getClass().getMethod("getProperties");
                Object propertyMap = getPropertiesMethod.invoke(gameProfile);
                if (propertyMap != null) {
                    Method getMethod = propertyMap.getClass().getMethod("get", Object.class);
                    Object props = getMethod.invoke(propertyMap, "textures");
                    if (props instanceof Iterable<?> iterable) {
                        for (Object prop : iterable) {
                            Method getValueMethod = prop.getClass().getMethod("getValue");
                            Object val = getValueMethod.invoke(prop);
                            if (val != null && !val.toString().isEmpty()) {
                                return val.toString();
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        // 4. Try OwningPlayer / Owner Name
        try {
            if (skullMeta.hasOwner()) {
                if (skullMeta.getOwningPlayer() != null && skullMeta.getOwningPlayer().getName() != null) {
                    return skullMeta.getOwningPlayer().getName();
                }
                if (skullMeta.getOwner() != null && !skullMeta.getOwner().isEmpty()) {
                    return skullMeta.getOwner();
                }
            }
        } catch (Throwable ignored) {
        }

        return null;
    }

    /**
     * Applies a skull skin texture onto a {@link SkullMeta} instance.
     * Intelligently resolves Base64 payloads, skin URLs, 64-char hex hashes,
     * Minecraft player names, and UUID strings.
     *
     * @param meta Target SkullMeta to mutate
     * @param texture Skin payload, URL, hash, or player username
     */
    public static void applySkin(SkullMeta meta, String texture) {
        if (meta == null || texture == null || texture.isEmpty()) {
            return;
        }

        // Base64 payloads, Mojang URLs, or 64-character hashes
        if (texture.startsWith("ey") || texture.startsWith("http://") || texture.startsWith("https://")
                || texture.matches("^[0-9a-fA-F]{64}$") || texture.length() > 36) {
            String base64 = normalizeToBase64(texture);
            try {
                if (Bukkit.getServer() != null) {
                    UUID uuid = UUID.nameUUIDFromBytes(base64.getBytes(StandardCharsets.UTF_8));
                    PlayerProfile profile = Bukkit.createProfile(uuid, "");
                    profile.setProperty(new ProfileProperty("textures", base64));
                    meta.setPlayerProfile(profile);
                    return;
                }
            } catch (Throwable ignored) {
            }
            trySetGameProfile(meta, base64);
            return;
        }

        // Minecraft Player Names (3 to 16 alphanumeric / underscore characters)
        if (texture.matches("^[a-zA-Z0-9_]{3,16}$")) {
            try {
                if (Bukkit.getServer() != null) {
                    PlayerProfile profile = Bukkit.createProfile(texture);
                    meta.setPlayerProfile(profile);
                    return;
                }
            } catch (Throwable ignored) {
            }
            try {
                meta.setOwner(texture);
            } catch (Throwable ignored) {
            }
            return;
        }

        // UUID String
        try {
            UUID uuid = UUID.fromString(texture);
            if (Bukkit.getServer() != null) {
                PlayerProfile profile = Bukkit.createProfile(uuid);
                meta.setPlayerProfile(profile);
                return;
            }
        } catch (Throwable ignored) {
        }

        try {
            meta.setOwner(texture);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Creates a textured {@code PLAYER_HEAD} item stack from a texture string.
     *
     * @param texture Texture payload, URL, or player name
     * @return Textured ItemStack
     */
    public static ItemStack createSkull(String texture) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        if (texture == null || texture.isEmpty()) {
            return skull;
        }
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        if (meta != null) {
            applySkin(meta, texture);
            skull.setItemMeta(meta);
        }
        return skull;
    }

    /**
     * Converts a Mojang texture URL to standard Base64 skin JSON.
     *
     * @param url Direct skin image URL
     * @return Base64 encoded payload
     */
    public static String urlToBase64(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + url + "\"}}}";
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Normalizes an input (raw URL, 64-char hash, or Base64) to valid Base64 skin JSON.
     *
     * @param input Raw texture string
     * @return Normalized Base64 payload
     */
    public static String normalizeToBase64(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        if (input.startsWith("ey")) {
            return input;
        }
        if (input.startsWith("http://") || input.startsWith("https://")) {
            return urlToBase64(input);
        }
        if (input.matches("^[0-9a-fA-F]{64}$")) {
            return urlToBase64("http://textures.minecraft.net/texture/" + input);
        }
        return input;
    }

    private static void trySetGameProfile(SkullMeta meta, String base64) {
        try {
            Field profileField = meta.getClass().getDeclaredField("profile");
            profileField.setAccessible(true);
            Class<?> gpClass = Class.forName("com.mojang.authlib.GameProfile");
            Class<?> propClass = Class.forName("com.mojang.authlib.properties.Property");
            UUID uuid = UUID.nameUUIDFromBytes(base64.getBytes(StandardCharsets.UTF_8));
            Object gameProfile = gpClass.getConstructor(UUID.class, String.class).newInstance(uuid, "MinionSkin");
            Object property = propClass.getConstructor(String.class, String.class).newInstance("textures", base64);
            Method getPropertiesMethod = gpClass.getMethod("getProperties");
            Object propMap = getPropertiesMethod.invoke(gameProfile);
            Method putMethod = propMap.getClass().getMethod("put", Object.class, Object.class);
            putMethod.invoke(propMap, "textures", property);
            profileField.set(meta, gameProfile);
        } catch (Throwable ignored) {
        }
    }
}
