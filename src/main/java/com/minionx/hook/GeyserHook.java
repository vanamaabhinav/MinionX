package com.minionx.hook;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Hook providing seamless cross-platform support for GeyserMC & Floodgate Bedrock players.
 * Dynamically detects Bedrock clients to adapt touch/controller GUI navigation,
 * custom skull translations, and click event handling without hard dependencies.
 */
public class GeyserHook {

    private static Boolean floodgatePresent = null;
    private static Boolean geyserPresent = null;
    private static Method isFloodgateIdMethod = null;
    private static Object floodgateInstance = null;

    /**
     * Checks if Floodgate is loaded on the server.
     */
    public static boolean isFloodgateAvailable() {
        if (floodgatePresent == null) {
            try {
                if (Bukkit.getServer() == null || Bukkit.getPluginManager() == null) {
                    return false;
                }
                floodgatePresent = Bukkit.getPluginManager().getPlugin("floodgate") != null;
                if (floodgatePresent) {
                    Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
                    Method getInstance = apiClass.getMethod("getInstance");
                    floodgateInstance = getInstance.invoke(null);
                    isFloodgateIdMethod = apiClass.getMethod("isFloodgateId", UUID.class);
                }
            } catch (Throwable t) {
                floodgatePresent = false;
            }
        }
        return floodgatePresent != null && floodgatePresent;
    }

    /**
     * Checks if Geyser-Spigot is loaded on the server.
     */
    public static boolean isGeyserAvailable() {
        if (geyserPresent == null) {
            try {
                if (Bukkit.getServer() == null || Bukkit.getPluginManager() == null) {
                    return false;
                }
                geyserPresent = Bukkit.getPluginManager().getPlugin("Geyser-Spigot") != null;
            } catch (Throwable t) {
                geyserPresent = false;
            }
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
        if (!isFloodgateAvailable()) return false;
        if (isFloodgateIdMethod != null && floodgateInstance != null) {
            try {
                return (boolean) isFloodgateIdMethod.invoke(floodgateInstance, uuid);
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
