package com.minionx.hook;

import com.minionx.MinionX;
import com.minionx.core.Minion;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Universal protection hook manager for land claim and protection plugins:
 * GriefPrevention, BentoBox, Towny, Lands, SuperiorSkyblock2,
 * with cached reflection handles and secure owner-only fallback.
 */
public class ProtectionHookManager {

    private static boolean bentoBoxAvailable = false;
    private static boolean griefPreventionAvailable = false;
    private static boolean townyAvailable = false;
    private static boolean landsAvailable = false;
    private static boolean superiorSkyblockAvailable = false;

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

    // SuperiorSkyblock2
    private static Class<?> ssApiClass = null;
    private static Object ssGridManager = null;
    private static Method ssGetIslandAt = null;
    private static Method ssGetPlayer = null;
    private static Method ssIsIslandsWorld = null;
    private static Method ssIslandIsMember = null;
    private static Method ssIslandIsCoop = null;
    private static boolean ssGetPlayerTakesUuid = false;

    static {
        checkHooks();
    }

    public static void checkHooks() {
        initGriefPrevention();
        initBentoBox();
        initTowny();
        initLands();
        initSuperiorSkyblock();
    }

    private static void initGriefPrevention() {
        try {
            griefPreventionAvailable = Bukkit.getPluginManager() != null && Bukkit.getPluginManager().isPluginEnabled("GriefPrevention");
        } catch (Throwable t) {
            griefPreventionAvailable = false;
        }
        if (!griefPreventionAvailable) {
            gpDataStore = null;
            gpGetClaimAt = null;
            gpAllowBuild = null;
            gpAllowContainers = null;
            return;
        }
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
            gpGetClaimAt = null;
            gpAllowBuild = null;
            gpAllowContainers = null;
        }
    }

    private static void initBentoBox() {
        try {
            bentoBoxAvailable = Bukkit.getPluginManager() != null && Bukkit.getPluginManager().isPluginEnabled("BentoBox");
        } catch (Throwable t) {
            bentoBoxAvailable = false;
        }
        if (!bentoBoxAvailable) {
            bentoIslandsManager = null;
            bentoGetIslandAt = null;
            bentoOptionalIsPresent = null;
            bentoOptionalGet = null;
            bentoIslandIsMember = null;
            return;
        }
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
            bentoGetIslandAt = null;
            bentoOptionalIsPresent = null;
            bentoOptionalGet = null;
            bentoIslandIsMember = null;
        }
    }

    private static void initTowny() {
        try {
            townyAvailable = Bukkit.getPluginManager() != null && Bukkit.getPluginManager().isPluginEnabled("Towny");
        } catch (Throwable t) {
            townyAvailable = false;
        }
        if (!townyAvailable) {
            townyApi = null;
            townyGetTownBlock = null;
            townyCanPlayerBuild = null;
            return;
        }
        try {
            Class<?> townyApiClass = Class.forName("com.palmergames.bukkit.towny.TownyAPI");
            townyApi = townyApiClass.getMethod("getInstance").invoke(null);
            townyGetTownBlock = townyApiClass.getMethod("getTownBlock", Location.class);

            Class<?> townBlockClass = Class.forName("com.palmergames.bukkit.towny.object.TownBlock");
            townyCanPlayerBuild = townBlockClass.getMethod("canPlayerBuild", Player.class);
        } catch (Throwable t) {
            townyAvailable = false;
            townyApi = null;
            townyGetTownBlock = null;
            townyCanPlayerBuild = null;
        }
    }

    private static void initLands() {
        try {
            landsAvailable = Bukkit.getPluginManager() != null && Bukkit.getPluginManager().isPluginEnabled("Lands");
        } catch (Throwable t) {
            landsAvailable = false;
        }
        if (!landsAvailable) {
            landsIntegration = null;
            landsGetArea = null;
            landsCanBuild = null;
            landsCanAccess = null;
            return;
        }
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
                landsIntegration = null;
            }
        } catch (Throwable t) {
            landsAvailable = false;
            landsIntegration = null;
            landsGetArea = null;
            landsCanBuild = null;
            landsCanAccess = null;
        }
    }

    private static void initSuperiorSkyblock() {
        try {
            superiorSkyblockAvailable = Bukkit.getPluginManager() != null && Bukkit.getPluginManager().isPluginEnabled("SuperiorSkyblock2");
        } catch (Throwable t) {
            superiorSkyblockAvailable = false;
        }
        if (!superiorSkyblockAvailable) {
            ssApiClass = null;
            ssGridManager = null;
            ssGetIslandAt = null;
            ssGetPlayer = null;
            ssIsIslandsWorld = null;
            ssIslandIsMember = null;
            ssIslandIsCoop = null;
            ssGetPlayerTakesUuid = false;
            return;
        }
        try {
            ssApiClass = Class.forName("com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI");
            ssGetIslandAt = ssApiClass.getMethod("getIslandAt", Location.class);

            // Optional GridManager integration for world checking
            try {
                Method getGrid = ssApiClass.getMethod("getGrid");
                ssGridManager = getGrid.invoke(null);
                if (ssGridManager != null) {
                    ssIsIslandsWorld = ssGridManager.getClass().getMethod("isIslandsWorld", World.class);
                }
            } catch (Throwable ignored) {
                ssGridManager = null;
                ssIsIslandsWorld = null;
            }

            // Supports getPlayer(Player) or getPlayer(UUID)
            try {
                ssGetPlayer = ssApiClass.getMethod("getPlayer", Player.class);
                ssGetPlayerTakesUuid = false;
            } catch (NoSuchMethodException e) {
                ssGetPlayer = ssApiClass.getMethod("getPlayer", UUID.class);
                ssGetPlayerTakesUuid = true;
            }

            Class<?> superiorPlayerClass = Class.forName("com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer");
            Class<?> islandClass = Class.forName("com.bgsoftware.superiorskyblock.api.island.Island");

            ssIslandIsMember = islandClass.getMethod("isMember", superiorPlayerClass);
            try {
                ssIslandIsCoop = islandClass.getMethod("isCoop", superiorPlayerClass);
            } catch (NoSuchMethodException ignored) {
                ssIslandIsCoop = null;
            }
        } catch (Throwable t) {
            superiorSkyblockAvailable = false;
            ssApiClass = null;
            ssGridManager = null;
            ssGetIslandAt = null;
            ssGetPlayer = null;
            ssIsIslandsWorld = null;
            ssIslandIsMember = null;
            ssIslandIsCoop = null;
            ssGetPlayerTakesUuid = false;
        }
    }

    private static Object getSuperiorPlayer(Player player) {
        if (ssGetPlayer == null || player == null) return null;
        try {
            return ssGetPlayerTakesUuid
                    ? ssGetPlayer.invoke(null, player.getUniqueId())
                    : ssGetPlayer.invoke(null, player);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean isSuperiorMemberOrCoop(Object island, Player player) {
        if (island == null || player == null || ssIslandIsMember == null) return false;
        try {
            Object sPlayer = getSuperiorPlayer(player);
            if (sPlayer == null) return false;
            if ((boolean) ssIslandIsMember.invoke(island, sPlayer)) {
                return true;
            }
            if (ssIslandIsCoop != null && (boolean) ssIslandIsCoop.invoke(island, sPlayer)) {
                return true;
            }
        } catch (Throwable ignored) {}
        return false;
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

        // 5. SuperiorSkyblock2 check
        if (superiorSkyblockAvailable && ssGetIslandAt != null) {
            try {
                // If in a configured SuperiorSkyblock island world, enforce island boundaries
                if (location.getWorld() != null && ssGridManager != null && ssIsIslandsWorld != null) {
                    boolean isIslandWorld = (boolean) ssIsIslandsWorld.invoke(ssGridManager, location.getWorld());
                    if (isIslandWorld) {
                        Object island = ssGetIslandAt.invoke(null, location);
                        if (island == null || !isSuperiorMemberOrCoop(island, player)) {
                            return false; // Deny placement in wilderness or on stranger islands
                        }
                    }
                } else {
                    Object island = ssGetIslandAt.invoke(null, location);
                    if (island != null && !isSuperiorMemberOrCoop(island, player)) {
                        return false;
                    }
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
        if ("PUBLIC".equalsIgnoreCase(mode) || "OPEN".equalsIgnoreCase(mode)) {
            return true;
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

        // 4. Towny check
        if (townyAvailable && townyApi != null && townyGetTownBlock != null && townyCanPlayerBuild != null) {
            try {
                Object townBlock = townyGetTownBlock.invoke(townyApi, loc);
                if (townBlock != null) {
                    return (boolean) townyCanPlayerBuild.invoke(townBlock, player);
                }
            } catch (Throwable ignored) {}
        }

        // 5. SuperiorSkyblock2 team/co-op check
        if (superiorSkyblockAvailable && ssGetIslandAt != null) {
            try {
                Object island = ssGetIslandAt.invoke(null, loc);
                if (island != null) {
                    return isSuperiorMemberOrCoop(island, player);
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

    // Diagnostics / Accessors
    public static boolean isBentoBoxAvailable() {
        return bentoBoxAvailable;
    }

    public static boolean isGriefPreventionAvailable() {
        return griefPreventionAvailable;
    }

    public static boolean isTownyAvailable() {
        return townyAvailable;
    }

    public static boolean isLandsAvailable() {
        return landsAvailable;
    }

    public static boolean isSuperiorSkyblockAvailable() {
        return superiorSkyblockAvailable;
    }
}
