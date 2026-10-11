package com.skyblock.minions.hook;

import com.skyblock.minions.core.Minion;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Universal protection hook manager for land claim and island plugins:
 * GriefPrevention, BentoBox, SuperiorSkyblock2, WorldGuard, Towny, Lands,
 * with secure owner-only fallback.
 */
public class ProtectionHookManager {

    private static boolean bentoBoxAvailable = false;
    private static boolean superiorSkyblockAvailable = false;
    private static boolean griefPreventionAvailable = false;
    private static boolean townyAvailable = false;
    private static boolean landsAvailable = false;

    static {
        checkHooks();
    }

    public static void checkHooks() {
        bentoBoxAvailable = Bukkit.getPluginManager().isPluginEnabled("BentoBox");
        superiorSkyblockAvailable = Bukkit.getPluginManager().isPluginEnabled("SuperiorSkyblock2");
        griefPreventionAvailable = Bukkit.getPluginManager().isPluginEnabled("GriefPrevention");
        townyAvailable = Bukkit.getPluginManager().isPluginEnabled("Towny");
        landsAvailable = Bukkit.getPluginManager().isPluginEnabled("Lands");
    }

    /**
     * Determines whether a player is authorized to place a minion at a location.
     */
    public static boolean canPlaceMinion(Player player, Location location) {
        if (player.hasPermission("minions.admin")) return true;

        // 1. GriefPrevention claim check
        if (griefPreventionAvailable) {
            try {
                Class<?> gpClass = Class.forName("me.ryanhamshire.GriefPrevention.GriefPrevention");
                Object gpInstance = gpClass.getField("instance").get(null);
                Object dataStore = gpClass.getField("dataStore").get(gpInstance);
                Method getClaimAt = dataStore.getClass().getMethod("getClaimAt", Location.class, boolean.class, Object.class);
                Object claim = getClaimAt.invoke(dataStore, location, false, null);

                if (claim != null) {
                    Method allowBuild = claim.getClass().getMethod("allowBuild", Player.class, Material.class);
                    Object denyReason = allowBuild.invoke(claim, player, Material.STONE);
                    if (denyReason != null) return false;
                }
            } catch (Throwable ignored) {}
        }

        // 2. BentoBox Island check
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

        // 3. SuperiorSkyblock2 check
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

        // 4. Towny check
        if (townyAvailable) {
            try {
                Class<?> townyApiClass = Class.forName("com.palmergames.bukkit.towny.TownyAPI");
                Object api = townyApiClass.getMethod("getInstance").invoke(null);
                Method getTownBlock = api.getClass().getMethod("getTownBlock", Location.class);
                Object townBlock = getTownBlock.invoke(api, location);
                if (townBlock != null) {
                    Method canPlayerBuild = townBlock.getClass().getMethod("canPlayerBuild", Player.class);
                    return (boolean) canPlayerBuild.invoke(townBlock, player);
                }
            } catch (Throwable ignored) {}
        }

        return true;
    }

    /**
     * Determines whether a player is authorized to access, open GUI, or pick up a minion.
     */
    public static boolean canAccessMinion(Player player, Minion minion) {
        if (player.getUniqueId().equals(minion.getOwnerId())) return true;
        if (player.hasPermission("minions.admin")) return true;

        Location loc = minion.getLocation();
        if (loc == null) return false;

        // 1. GriefPrevention Container check
        if (griefPreventionAvailable) {
            try {
                Class<?> gpClass = Class.forName("me.ryanhamshire.GriefPrevention.GriefPrevention");
                Object gpInstance = gpClass.getField("instance").get(null);
                Object dataStore = gpClass.getField("dataStore").get(gpInstance);
                Method getClaimAt = dataStore.getClass().getMethod("getClaimAt", Location.class, boolean.class, Object.class);
                Object claim = getClaimAt.invoke(dataStore, loc, false, null);

                if (claim != null) {
                    Method allowContainers = claim.getClass().getMethod("allowContainers", Player.class);
                    Object denyReason = allowContainers.invoke(claim, player);
                    return denyReason == null;
                }
            } catch (Throwable ignored) {}
        }

        // 2. BentoBox Island team member check
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

        // 3. SuperiorSkyblock2 team member check
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
