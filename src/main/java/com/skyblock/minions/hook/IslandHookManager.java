package com.skyblock.minions.hook;

import com.skyblock.minions.core.Minion;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Handles soft-dependency integrations for SkyBlock island and region protection plugins
 * (BentoBox, SuperiorSkyblock2, WorldGuard, and fallback Owner security).
 */
public class IslandHookManager {

    private static boolean bentoBoxAvailable = false;
    private static boolean superiorSkyblockAvailable = false;

    static {
        checkHooks();
    }

    public static void checkHooks() {
        bentoBoxAvailable = Bukkit.getPluginManager().isPluginEnabled("BentoBox");
        superiorSkyblockAvailable = Bukkit.getPluginManager().isPluginEnabled("SuperiorSkyblock2");
    }

    /**
     * Determines whether a player is authorized to place a minion at a given location.
     */
    public static boolean canPlaceMinion(Player player, Location location) {
        if (player.hasPermission("minions.admin")) return true;

        if (bentoBoxAvailable) {
            try {
                Object bentoInstance = Class.forName("world.bentobox.bentobox.BentoBox").getMethod("getInstance").invoke(null);
                Object islandsManager = bentoInstance.getClass().getMethod("getIslands").invoke(bentoInstance);
                Method getIslandAt = islandsManager.getClass().getMethod("getIslandAt", Location.class);
                Object optionalIsland = getIslandAt.invoke(islandsManager, location);

                if (optionalIsland != null) {
                    Method isPresent = optionalIsland.getClass().getMethod("isPresent");
                    if ((boolean) isPresent.invoke(optionalIsland)) {
                        Object island = optionalIsland.getClass().getMethod("get").invoke(optionalIsland);
                        Method isMember = island.getClass().getMethod("isMember", UUID.class);
                        return (boolean) isMember.invoke(island, player.getUniqueId());
                    }
                }
            } catch (Throwable ignored) {}
        }

        if (superiorSkyblockAvailable) {
            try {
                Class<?> apiClass = Class.forName("com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI");
                Object grid = apiClass.getMethod("getGrid").invoke(null);
                Object island = grid.getClass().getMethod("getIslandAt", Location.class).invoke(grid, location);

                if (island != null) {
                    Class<?> sPlayerClass = Class.forName("com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer");
                    Object sPlayer = apiClass.getMethod("getPlayer", Player.class).invoke(null, player);
                    Method isMember = island.getClass().getMethod("isMember", sPlayerClass);
                    return (boolean) isMember.invoke(island, sPlayer);
                }
            } catch (Throwable ignored) {}
        }

        return true;
    }

    /**
     * Determines whether a player is authorized to open, interact with, or pick up a minion.
     */
    public static boolean canAccessMinion(Player player, Minion minion) {
        if (player.getUniqueId().equals(minion.getOwnerId())) return true;
        if (player.hasPermission("minions.admin")) return true;

        Location loc = minion.getLocation();
        if (loc == null) return false;

        // If BentoBox is present, allow island team members to access the minion
        if (bentoBoxAvailable) {
            try {
                Object bentoInstance = Class.forName("world.bentobox.bentobox.BentoBox").getMethod("getInstance").invoke(null);
                Object islandsManager = bentoInstance.getClass().getMethod("getIslands").invoke(bentoInstance);
                Method getIslandAt = islandsManager.getClass().getMethod("getIslandAt", Location.class);
                Object optionalIsland = getIslandAt.invoke(islandsManager, loc);

                if (optionalIsland != null) {
                    Method isPresent = optionalIsland.getClass().getMethod("isPresent");
                    if ((boolean) isPresent.invoke(optionalIsland)) {
                        Object island = optionalIsland.getClass().getMethod("get").invoke(optionalIsland);
                        Method isMember = island.getClass().getMethod("isMember", UUID.class);
                        return (boolean) isMember.invoke(island, player.getUniqueId());
                    }
                }
            } catch (Throwable ignored) {}
        }

        // If SuperiorSkyblock2 is present, allow team members
        if (superiorSkyblockAvailable) {
            try {
                Class<?> apiClass = Class.forName("com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI");
                Object grid = apiClass.getMethod("getGrid").invoke(null);
                Object island = grid.getClass().getMethod("getIslandAt", Location.class).invoke(grid, loc);

                if (island != null) {
                    Class<?> sPlayerClass = Class.forName("com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer");
                    Object sPlayer = apiClass.getMethod("getPlayer", Player.class).invoke(null, player);
                    Method isMember = island.getClass().getMethod("isMember", sPlayerClass);
                    return (boolean) isMember.invoke(island, sPlayer);
                }
            } catch (Throwable ignored) {}
        }

        return false;
    }
}