package com.minionx.hook;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Hook providing seamless cross-platform support for GeyserMC and Floodgate Bedrock players.
 * Dynamically detects Bedrock clients via Floodgate 2.0+ (isFloodgatePlayer) or GeyserApi (isBedrockPlayer)
 * to adapt touch/controller GUI navigation, custom skull translations, and click event handling without hard dependencies.
 */
public class GeyserHook {

    private static Boolean floodgatePresent = null;
    private static Object floodgateInstance = null;
    private static Method isFloodgatePlayerMethod = null;

    private static Boolean geyserPresent = null;
    private static Object geyserApiInstance = null;
    private static Method geyserIsBedrockMethod = null;

    static {
        checkHooks();
    }

    public static synchronized void checkHooks() {
        initFloodgate();
        initGeyser();
    }

    private static void initFloodgate() {
        floodgatePresent = false;
        floodgateInstance = null;
        isFloodgatePlayerMethod = null;

        if (Bukkit.getServer() == null || Bukkit.getPluginManager() == null) return;

        try {
            if (Bukkit.getPluginManager().getPlugin("floodgate") != null ||
                Bukkit.getPluginManager().isPluginEnabled("floodgate")) {
                Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
                Method getInstance = apiClass.getMethod("getInstance");
                floodgateInstance = getInstance.invoke(null);

                // Try modern Floodgate 2.0+ method first: isFloodgatePlayer(UUID)
                try {
                    isFloodgatePlayerMethod = apiClass.getMethod("isFloodgatePlayer", UUID.class);
                    floodgatePresent = true;
                } catch (NoSuchMethodException e) {
                    // Fallback to legacy 1.0 method: isFloodgateId(UUID)
                    try {
                        isFloodgatePlayerMethod = apiClass.getMethod("isFloodgateId", UUID.class);
                        floodgatePresent = true;
                    } catch (NoSuchMethodException ignored) {}
                }
            }
        } catch (Throwable t) {
            floodgatePresent = false;
        }
    }

    private static void initGeyser() {
        geyserPresent = false;
        geyserApiInstance = null;
        geyserIsBedrockMethod = null;

        if (Bukkit.getServer() == null || Bukkit.getPluginManager() == null) return;

        try {
            if (Bukkit.getPluginManager().getPlugin("Geyser-Spigot") != null ||
                Bukkit.getPluginManager().isPluginEnabled("Geyser-Spigot")) {
                Class<?> apiClass = Class.forName("org.geysermc.geyser.api.GeyserApi");
                Method apiMethod = apiClass.getMethod("api");
                geyserApiInstance = apiMethod.invoke(null);
                if (geyserApiInstance != null) {
                    geyserIsBedrockMethod = geyserApiInstance.getClass().getMethod("isBedrockPlayer", UUID.class);
                    geyserPresent = true;
                }
            }
        } catch (Throwable t) {
            geyserPresent = false;
        }
    }

    /**
     * Checks if Floodgate is loaded on the server.
     */
    public static boolean isFloodgateAvailable() {
        if (floodgatePresent == null) {
            checkHooks();
        }
        return floodgatePresent != null && floodgatePresent;
    }

    /**
     * Checks if Geyser-Spigot is loaded on the server.
     */
    public static boolean isGeyserAvailable() {
        if (geyserPresent == null) {
            checkHooks();
        }
        return geyserPresent != null && geyserPresent;
    }

    /**
     * Returns true if either Geyser or Floodgate is running on this server.
     */
    public static boolean isCrossPlatformSupported() {
        return isGeyserAvailable() || isFloodgateAvailable();
    }

    /**
     * Returns true if the specified player connected via Minecraft Bedrock Edition.
     */
    public static boolean isBedrockPlayer(Player player) {
        if (player == null) return false;
        return isBedrockPlayer(player.getUniqueId());
    }

    /**
     * Returns true if the UUID belongs to a Bedrock/Floodgate player.
     */
    public static boolean isBedrockPlayer(UUID uuid) {
        if (uuid == null) return false;

        // 1. Try FloodgateApi
        if (isFloodgateAvailable() && isFloodgatePlayerMethod != null && floodgateInstance != null) {
            try {
                if ((boolean) isFloodgatePlayerMethod.invoke(floodgateInstance, uuid)) {
                    return true;
                }
            } catch (Throwable ignored) {}
        }

        // 2. Try GeyserApi
        if (isGeyserAvailable() && geyserIsBedrockMethod != null && geyserApiInstance != null) {
            try {
                if ((boolean) geyserIsBedrockMethod.invoke(geyserApiInstance, uuid)) {
                    return true;
                }
            } catch (Throwable ignored) {}
        }

        return false;
    }

    /**
     * Returns a human-friendly string identifying the player's connection platform.
     */
    public static String getPlatform(Player player) {
        return isBedrockPlayer(player) ? "Bedrock (Geyser)" : "Java Edition";
    }
}
