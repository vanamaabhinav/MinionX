package com.minionx.hook;

import com.minionx.MinionX;
import com.minionx.core.Minion;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Universal protection hook manager for land claim and protection plugins:
 * GriefPrevention, BentoBox, Towny, Lands,
 * with cached reflection handles and secure owner-only fallback.
 */
public class ProtectionHookManager {

    private static boolean bentoBoxAvailable = false;
    private static boolean griefPreventionAvailable = false;
    private static boolean townyAvailable = false;
    private static boolean landsAvailable = false;

    // --- Cached Reflection Handles ---
    // GriefPrevention
    private static Object gpDataStore = null;
    private static Method gpGetClaimAt = null;
    private static Method gpAllowBuild = null;
    private static Method gpAllowContainers = null;

    // BentoBox
    private static Object bentoIslandsManager = null;
    private static Method bentoGetIslandAt = null;
    private static Method bentoOptionalIsPresent = null;
    private static Method bentoOptionalGet = null;
    private static Method bentoIslandIsMember = null;

    // Towny
    private static Object townyApi = null;
    private static Method townyGetTownBlock = null;
    private static Method townyCanPlayerBuild = null;

    // Lands
    private static Object landsIntegration = null;
    private static Method landsGetArea = null;
    private static Method landsCanBuild = null;
    private static Method landsCanAccess = null;

    static {
        checkHooks();
    }

    public static void checkHooks() {
        initGriefPrevention();
        initBentoBox();
        initTowny();
        initLands();
    }

    private static void initGriefPrevention() {
        griefPreventionAvailable = Bukkit.getPluginManager().isPluginEnabled("GriefPrevention");
        if (!griefPreventionAvailable) return;
        try {
            Class<?> gpClass = Class.forName("me.ryanhamshire.GriefPrevention.GriefPrevention");
            Field instanceField = gpClass.getField("instance");
            Object gpInstance = instanceField.get(null);
            Field dataStoreField = gpClass.getField("dataStore");
            gpDataStore = dataStoreField.get(gpInstance);

            gpGetClaimAt = gpDataStore.getClass().getMethod("getClaimAt", Location.class, boolean.class, Object.class);
            Class<?> claimClass = Class.forName("me.ryanhamshire.GriefPrevention.Claim");
            gpAllowBuild = claimClass.getMethod("allowBuild", Player.class, Material.class);
            gpAllowContainers = claimClass.getMethod("allowContainers", Player.class);
        } catch (Throwable t) {
            griefPreventionAvailable = false;
            gpDataStore = null;
        }
    }

    private static void initBentoBox() {
        bentoBoxAvailable = Bukkit.getPluginManager().isPluginEnabled("BentoBox");
        if (!bentoBoxAvailable) return;
        try {
            Class<?> bentoClass = Class.forName("world.bentobox.bentobox.BentoBox");
            Object bentoInstance = bentoClass.getMethod("getInstance").invoke(null);
            bentoIslandsManager = bentoInstance.getClass().getMethod("getIslands").invoke(bentoInstance);
            bentoGetIslandAt = bentoIslandsManager.getClass().getMethod("getIslandAt", Location.class);

            Class<?> optionalClass = Class.forName("java.util.Optional");
            bentoOptionalIsPresent = optionalClass.getMethod("isPresent");
            bentoOptionalGet = optionalClass.getMethod("get");

            Class<?> islandClass = Class.forName("world.bentobox.bentobox.database.objects.Island");
            bentoIslandIsMember = islandClass.getMethod("isMember", UUID.class);
        } catch (Throwable t) {
            bentoBoxAvailable = false;
            bentoIslandsManager = null;
        }
    }

    private static void initTowny() {
        townyAvailable = Bukkit.getPluginManager().isPluginEnabled("Towny");
        if (!townyAvailable) return;
        try {
            Class<?> townyApiClass = Class.forName("com.palmergames.bukkit.towny.TownyAPI");
            townyApi = townyApiClass.getMethod("getInstance").invoke(null);
            townyGetTownBlock = townyApiClass.getMethod("getTownBlock", Location.class);

            Class<?> townBlockClass = Class.forName("com.palmergames.bukkit.towny.object.TownBlock");
            townyCanPlayerBuild = townBlockClass.getMethod("canPlayerBuild", Player.class);
        } catch (Throwable t) {
            townyAvailable = false;
            townyApi = null;
        }
    }

    private static void initLands() {
        landsAvailable = Bukkit.getPluginManager().isPluginEnabled("Lands");
        if (!landsAvailable) return;
        try {
            Class<?> landsIntegrationClass = Class.forName("me.angeschossen.lands.api.integration.LandsIntegration");
            if (MinionX.getInstance() != null) {
                landsIntegration = landsIntegrationClass.getConstructor(org.bukkit.plugin.Plugin.class).newInstance(MinionX.getInstance());
                landsGetArea = landsIntegrationClass.getMethod("getArea", Location.class);
                Class<?> areaClass = Class.forName("me.angeschossen.lands.api.land.Area");
                landsCanBuild = areaClass.getMethod("canBuild", Player.class, Location.class);
                landsCanAccess = areaClass.getMethod("canAccess", Player.class, Location.class);
            } else {
                landsAvailable = false;
            }
        } catch (Throwable t) {
            landsAvailable = false;
            landsIntegration = null;
        }
    }

    /**
     * Determines whether a player is authorized to place a minion at a location.
     */
    public static boolean canPlaceMinion(Player player, Location location) {
        if (player == null || location == null) return false;
        if (player.hasPermission("minionx.admin")) return true;

        // 1. GriefPrevention claim check
        if (griefPreventionAvailable && gpDataStore != null && gpGetClaimAt != null && gpAllowBuild != null) {
            try {
                Object claim = gpGetClaimAt.invoke(gpDataStore, location, false, null);
                if (claim != null) {
                    Object denyReason = gpAllowBuild.invoke(claim, player, Material.STONE);
                    if (denyReason != null) return false;
                }
            } catch (Throwable ignored) {}
        }

        // 2. BentoBox Island check
        if (bentoBoxAvailable && bentoIslandsManager != null && bentoGetIslandAt != null) {
            try {
                Object optionalIsland = bentoGetIslandAt.invoke(bentoIslandsManager, location);
                if (optionalIsland != null && (boolean) bentoOptionalIsPresent.invoke(optionalIsland)) {
                    Object island = bentoOptionalGet.invoke(optionalIsland);
                    return (boolean) bentoIslandIsMember.invoke(island, player.getUniqueId());
                }
            } catch (Throwable ignored) {}
        }

        // 3. Towny check
        if (townyAvailable && townyApi != null && townyGetTownBlock != null) {
            try {
                Object townBlock = townyGetTownBlock.invoke(townyApi, location);
                if (townBlock != null && townyCanPlayerBuild != null) {
                    return (boolean) townyCanPlayerBuild.invoke(townBlock, player);
                }
            } catch (Throwable ignored) {}
        }

        // 4. Lands check
        if (landsAvailable && landsIntegration != null && landsGetArea != null && landsCanBuild != null) {
            try {
                Object area = landsGetArea.invoke(landsIntegration, location);
                if (area != null) {
                    return (boolean) landsCanBuild.invoke(area, player, location);
                }
            } catch (Throwable ignored) {}
        }

        return true;
    }

    /**
     * Determines whether a player is authorized to access and open the GUI of a minion.
     */
    public static boolean canAccessMinion(Player player, Minion minion) {
        if (player == null || minion == null) return false;
        if (player.hasPermission("minionx.admin")) return true;

        // Owner can always access their minion
        if (minion.getOwnerId() != null && player.getUniqueId().equals(minion.getOwnerId())) {
            return true;
        }

        // Check configured access mode
        String mode = "TEAM_ACCESS";
        if (MinionX.getInstance() != null) {
            mode = MinionX.getInstance().getConfig().getString("security.access-mode", "TEAM_ACCESS");
        }
        if ("OWNER_ONLY".equalsIgnoreCase(mode)) {
            return false;
        }

        Location loc = minion.getLocation();
        if (loc == null) return false;

        // If unowned legacy minion, allow access only if player has build rights at that location
        if (minion.getOwnerId() == null) {
            return canPlaceMinion(player, loc);
        }

        // 1. GriefPrevention Container check
        if (griefPreventionAvailable && gpDataStore != null && gpGetClaimAt != null && gpAllowContainers != null) {
            try {
                Object claim = gpGetClaimAt.invoke(gpDataStore, loc, false, null);
                if (claim != null) {
                    Object denyReason = gpAllowContainers.invoke(claim, player);
                    return denyReason == null;
                }
            } catch (Throwable ignored) {}
        }

        // 2. BentoBox Island team member check
        if (bentoBoxAvailable && bentoIslandsManager != null && bentoGetIslandAt != null) {
            try {
                Object optionalIsland = bentoGetIslandAt.invoke(bentoIslandsManager, loc);
                if (optionalIsland != null && (boolean) bentoOptionalIsPresent.invoke(optionalIsland)) {
                    Object island = bentoOptionalGet.invoke(optionalIsland);
                    return (boolean) bentoIslandIsMember.invoke(island, player.getUniqueId());
                }
            } catch (Throwable ignored) {}
        }

        // 3. Lands team/access check
        if (landsAvailable && landsIntegration != null && landsGetArea != null && landsCanAccess != null) {
            try {
                Object area = landsGetArea.invoke(landsIntegration, loc);
                if (area != null) {
                    return (boolean) landsCanAccess.invoke(area, player, loc);
                }
            } catch (Throwable ignored) {}
        }

        return false;
    }

    /**
     * Determines whether a player is authorized to pick up / dismantle a minion.
     * Prevents co-op members, trusted claim guests, or strangers from stealing minions.
     */
    public static boolean canPickupMinion(Player player, Minion minion) {
        if (player == null || minion == null) return false;
        if (player.hasPermission("minionx.admin")) return true;

        // Owner can always pick up their minion
        if (minion.getOwnerId() != null && player.getUniqueId().equals(minion.getOwnerId())) {
            return true;
        }

        // If unowned legacy minion, allow pickup only if player has build rights at that location
        if (minion.getOwnerId() == null) {
            return canPlaceMinion(player, minion.getLocation());
        }

        // Check if server explicitly enabled team pickups
        boolean allowTeamPickup = false;
        String mode = "TEAM_ACCESS";
        if (MinionX.getInstance() != null) {
            allowTeamPickup = MinionX.getInstance().getConfig().getBoolean("security.allow-team-pickup", false);
            mode = MinionX.getInstance().getConfig().getString("security.access-mode", "TEAM_ACCESS");
        }

        if (allowTeamPickup || "FULL_COOP".equalsIgnoreCase(mode)) {
            return canAccessMinion(player, minion);
        }

        return false;
    }
}
