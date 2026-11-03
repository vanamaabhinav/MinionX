package com.minionx.hook;

import com.minionx.MinionX;
import com.minionx.core.Minion;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Universal protection hook manager for land claim and protection plugins:
 * WorldGuard, GriefPrevention, BentoBox, Towny, Lands, SuperiorSkyblock2,
 * with cached reflection handles, perimeter/area verification, and secure owner-only fallback.
 */
public class ProtectionHookManager {

    private static boolean worldGuardAvailable = false;
    private static boolean bentoBoxAvailable = false;
    private static boolean griefPreventionAvailable = false;
    private static boolean townyAvailable = false;
    private static boolean landsAvailable = false;
    private static boolean superiorSkyblockAvailable = false;

    // --- Cached Reflection Handles ---
    // WorldGuard 7.x
    private static Object wgRegionContainer = null;
    private static Method wgCreateQuery = null;
    private static Method wgAdaptLocation = null;
    private static Method wgWrapPlayer = null;
    private static Object wgFlagBuild = null;
    private static Object wgFlagInteract = null;
    private static Method wgTestState = null;

    // GriefPrevention
    private static Object gpDataStore = null;
    private static Method gpGetClaimAt = null;
    private static Method gpAllowBuild = null;
    private static Method gpAllowContainers = null;

    // BentoBox
    private static Object bentoIslandsManager = null;
    private static Object bentoIwm = null;
    private static Method bentoInWorld = null;
    private static Method bentoGetIslandAt = null;
    private static Method bentoOptionalIsPresent = null;
    private static Method bentoOptionalGet = null;
    private static Method bentoIslandIsMember = null;
    private static Method bentoIslandGetMembers = null;

    // Towny (PlayerCacheUtil canonical API)
    private static Method townyGetCachePermission = null;
    private static Object townyActionBuild = null;
    private static Object townyActionSwitch = null;

    // Lands
    private static Object landsIntegration = null;
    private static Method landsGetArea = null;
    private static Method landsCanBuild = null;
    private static Method landsCanAccess = null;
    private static Method landsIsTrusted = null;

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

    public static synchronized void checkHooks() {
        initWorldGuard();
        initGriefPrevention();
        initBentoBox();
        initTowny();
        initLands();
        initSuperiorSkyblock();
    }

    private static void initWorldGuard() {
        try {
            worldGuardAvailable = Bukkit.getPluginManager() != null && Bukkit.getPluginManager().isPluginEnabled("WorldGuard");
        } catch (Throwable t) {
            worldGuardAvailable = false;
        }
        if (!worldGuardAvailable) {
            wgRegionContainer = null;
            wgCreateQuery = null;
            wgAdaptLocation = null;
            wgWrapPlayer = null;
            wgFlagBuild = null;
            wgFlagInteract = null;
            wgTestState = null;
            return;
        }
        try {
            Class<?> wgClass = Class.forName("com.sk89q.worldguard.WorldGuard");
            Method getInstance = wgClass.getMethod("getInstance");
            Object wgInstance = getInstance.invoke(null);
            Method getPlatform = wgClass.getMethod("getPlatform");
            Object platform = getPlatform.invoke(wgInstance);
            Method getRegionContainer = platform.getClass().getMethod("getRegionContainer");
            wgRegionContainer = getRegionContainer.invoke(platform);
            wgCreateQuery = wgRegionContainer.getClass().getMethod("createQuery");

            Class<?> adapterClass = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
            wgAdaptLocation = adapterClass.getMethod("adapt", Location.class);

            Class<?> wgpClass = Class.forName("com.sk89q.worldguard.bukkit.WorldGuardPlugin");
            Method inst = wgpClass.getMethod("inst");
            Object wgpInstance = inst.invoke(null);
            wgWrapPlayer = wgpClass.getMethod("wrapPlayer", Player.class);

            Class<?> flagsClass = Class.forName("com.sk89q.worldguard.protection.flags.Flags");
            wgFlagBuild = flagsClass.getField("BUILD").get(null);
            wgFlagInteract = flagsClass.getField("INTERACT").get(null);

            Class<?> queryClass = Class.forName("com.sk89q.worldguard.protection.regions.RegionQuery");
            Class<?> weLocClass = Class.forName("com.sk89q.worldedit.util.Location");
            Class<?> localPlayerClass = Class.forName("com.sk89q.worldguard.LocalPlayer");
            Class<?> stateFlagArrayClass = Class.forName("[Lcom.sk89q.worldguard.protection.flags.StateFlag;");

            try {
                wgTestState = queryClass.getMethod("testState", weLocClass, localPlayerClass, stateFlagArrayClass);
            } catch (NoSuchMethodException e) {
                for (Method m : queryClass.getMethods()) {
                    if ("testState".equals(m.getName()) && m.getParameterCount() >= 3) {
                        wgTestState = m;
                        break;
                    }
                }
            }
        } catch (Throwable t) {
            worldGuardAvailable = false;
            wgRegionContainer = null;
            wgCreateQuery = null;
            wgAdaptLocation = null;
            wgWrapPlayer = null;
            wgFlagBuild = null;
            wgFlagInteract = null;
            wgTestState = null;
        }
    }

    private static boolean checkWorldGuardBuild(Player player, Location location) {
        if (!worldGuardAvailable || wgRegionContainer == null || wgCreateQuery == null ||
            wgAdaptLocation == null || wgWrapPlayer == null || wgFlagBuild == null || wgTestState == null) {
            return true;
        }
        try {
            Object query = wgCreateQuery.invoke(wgRegionContainer);
            Object adaptedLoc = wgAdaptLocation.invoke(null, location);
            Object localPlayer = wgWrapPlayer.invoke(null, player);
            Object flagsArray = Array.newInstance(wgFlagBuild.getClass(), 1);
            Array.set(flagsArray, 0, wgFlagBuild);
            return (boolean) wgTestState.invoke(query, adaptedLoc, localPlayer, flagsArray);
        } catch (Throwable ignored) {
            return true;
        }
    }

    private static boolean checkWorldGuardAccess(Player player, Location location) {
        if (!worldGuardAvailable || wgRegionContainer == null || wgCreateQuery == null ||
            wgAdaptLocation == null || wgWrapPlayer == null || wgFlagInteract == null || wgTestState == null) {
            return true;
        }
        try {
            Object query = wgCreateQuery.invoke(wgRegionContainer);
            Object adaptedLoc = wgAdaptLocation.invoke(null, location);
            Object localPlayer = wgWrapPlayer.invoke(null, player);
            Object flagsArray = Array.newInstance(wgFlagInteract.getClass(), 1);
            Array.set(flagsArray, 0, wgFlagInteract);
            return (boolean) wgTestState.invoke(query, adaptedLoc, localPlayer, flagsArray);
        } catch (Throwable ignored) {
            return true;
        }
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
            bentoIwm = null;
            bentoInWorld = null;
            bentoGetIslandAt = null;
            bentoOptionalIsPresent = null;
            bentoOptionalGet = null;
            bentoIslandIsMember = null;
            bentoIslandGetMembers = null;
            return;
        }
        try {
            Class<?> bentoClass = Class.forName("world.bentobox.bentobox.BentoBox");
            Object bentoInstance = bentoClass.getMethod("getInstance").invoke(null);
            bentoIslandsManager = bentoInstance.getClass().getMethod("getIslands").invoke(bentoInstance);
            bentoGetIslandAt = bentoIslandsManager.getClass().getMethod("getIslandAt", Location.class);

            try {
                Object iwm = bentoInstance.getClass().getMethod("getIWM").invoke(bentoInstance);
                if (iwm != null) {
                    bentoIwm = iwm;
                    bentoInWorld = iwm.getClass().getMethod("inWorld", World.class);
                }
            } catch (Throwable ignored) {
                bentoIwm = null;
                bentoInWorld = null;
            }

            Class<?> optionalClass = Class.forName("java.util.Optional");
            bentoOptionalIsPresent = optionalClass.getMethod("isPresent");
            bentoOptionalGet = optionalClass.getMethod("get");

            Class<?> islandClass = Class.forName("world.bentobox.bentobox.database.objects.Island");
            bentoIslandIsMember = islandClass.getMethod("isMember", UUID.class);
            try {
                bentoIslandGetMembers = islandClass.getMethod("getMembers");
            } catch (Throwable ignored) {
                bentoIslandGetMembers = null;
            }
        } catch (Throwable t) {
            bentoBoxAvailable = false;
            bentoIslandsManager = null;
            bentoIwm = null;
            bentoInWorld = null;
            bentoGetIslandAt = null;
            bentoOptionalIsPresent = null;
            bentoOptionalGet = null;
            bentoIslandIsMember = null;
            bentoIslandGetMembers = null;
        }
    }

    private static boolean isBentoIslandMemberOrCoop(Object island, UUID uuid) {
        if (island == null || uuid == null) return false;
        try {
            if (bentoIslandIsMember != null && (boolean) bentoIslandIsMember.invoke(island, uuid)) {
                return true;
            }
            if (bentoIslandGetMembers != null) {
                Object membersObj = bentoIslandGetMembers.invoke(island);
                if (membersObj instanceof java.util.Map<?, ?> map) {
                    Object rankObj = map.get(uuid);
                    if (rankObj instanceof Number rank) {
                        return rank.intValue() >= 100;
                    }
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static void initTowny() {
        try {
            townyAvailable = Bukkit.getPluginManager() != null && Bukkit.getPluginManager().isPluginEnabled("Towny");
        } catch (Throwable t) {
            townyAvailable = false;
        }
        if (!townyAvailable) {
            townyGetCachePermission = null;
            townyActionBuild = null;
            townyActionSwitch = null;
            return;
        }
        try {
            Class<?> playerCacheUtilClass = Class.forName("com.palmergames.bukkit.towny.utils.PlayerCacheUtil");
            Class<?> actionTypeClass = Class.forName("com.palmergames.bukkit.towny.object.TownyPermission$ActionType");
            Method valueOfMethod = actionTypeClass.getMethod("valueOf", String.class);
            townyActionBuild = valueOfMethod.invoke(null, "BUILD");
            try {
                townyActionSwitch = valueOfMethod.invoke(null, "SWITCH");
            } catch (Throwable t) {
                townyActionSwitch = valueOfMethod.invoke(null, "ITEM_USE");
            }

            for (Method m : playerCacheUtilClass.getMethods()) {
                if ("getCachePermission".equals(m.getName())) {
                    Class<?>[] p = m.getParameterTypes();
                    if (p.length == 4 && p[0].equals(Player.class) && p[1].equals(Location.class) && p[2].equals(Material.class) && p[3].equals(actionTypeClass)) {
                        townyGetCachePermission = m;
                        break;
                    }
                }
            }
        } catch (Throwable t) {
            townyAvailable = false;
            townyGetCachePermission = null;
            townyActionBuild = null;
            townyActionSwitch = null;
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
            landsIsTrusted = null;
            return;
        }
        try {
            Class<?> landsClass = Class.forName("me.angeschossen.lands.api.integration.LandsIntegration");
            if (MinionX.getInstance() != null) {
                Method ofMethod = landsClass.getMethod("of", org.bukkit.plugin.Plugin.class);
                landsIntegration = ofMethod.invoke(null, MinionX.getInstance());
            }

            if (landsIntegration != null) {
                landsGetArea = landsIntegration.getClass().getMethod("getArea", Location.class);
                Class<?> areaClass = Class.forName("me.angeschossen.lands.api.land.Area");

                try {
                    landsCanBuild = areaClass.getMethod("canBuild", Player.class, Location.class);
                    landsCanAccess = areaClass.getMethod("canAccess", Player.class, Location.class);
                } catch (NoSuchMethodException ignored) {
                    landsCanBuild = null;
                    landsCanAccess = null;
                }

                try {
                    landsIsTrusted = areaClass.getMethod("isTrusted", UUID.class);
                } catch (NoSuchMethodException e) {
                    try {
                        landsIsTrusted = areaClass.getMethod("isTrusted", Player.class);
                    } catch (NoSuchMethodException ignored) {
                        landsIsTrusted = null;
                    }
                }
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
            landsIsTrusted = null;
        }
    }

    private static boolean isLandsAuthorized(Object area, Player player, Location location, boolean isBuild) {
        if (area == null || player == null) return false;
        try {
            if (isBuild && landsCanBuild != null) {
                return (boolean) landsCanBuild.invoke(area, player, location);
            }
            if (!isBuild && landsCanAccess != null) {
                return (boolean) landsCanAccess.invoke(area, player, location);
            }
            if (landsIsTrusted != null) {
                if (landsIsTrusted.getParameterTypes()[0].equals(UUID.class)) {
                    return (boolean) landsIsTrusted.invoke(area, player.getUniqueId());
                } else {
                    return (boolean) landsIsTrusted.invoke(area, player);
                }
            }
        } catch (Throwable ignored) {}
        return false;
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

    private static boolean hasPermission(Player player, String permission) {
        if (player == null || permission == null) return false;
        try {
            return Boolean.TRUE.equals(player.hasPermission(permission));
        } catch (Throwable t) {
            return false;
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
     * Determines whether a player is authorized to place a minion at a location,
     * checking the default 5x5 minion operational area (radius = 2).
     */
    public static boolean canPlaceMinion(Player player, Location location) {
        return canPlaceMinion(player, location, 2);
    }

    /**
     * Determines whether a player is authorized to place a minion at a location,
     * verifying both the center placement block and the outer perimeter of the minion's working area.
     * Follows the negative guard veto pattern across all registered providers.
     */
    public static boolean canPlaceMinion(Player player, Location location, int radius) {
        if (player == null || location == null) return false;

        // Admin & permission bypass
        if (hasPermission(player, "minionx.admin") ||
            hasPermission(player, "minionx.bypass.protection") ||
            hasPermission(player, "worldguard.region.bypass.*")) {
            return true;
        }

        // 1. Check center placement point
        if (!canPlaceSinglePoint(player, location)) {
            return false;
        }

        // 2. Check outer perimeter of minion operational working area (prevents border griefing)
        if (radius > 0 && location.getWorld() != null) {
            World world = location.getWorld();
            double x = location.getX();
            double y = location.getY();
            double z = location.getZ();

            Location[] perimeter = new Location[] {
                new Location(world, x - radius, y, z - radius),
                new Location(world, x + radius, y, z - radius),
                new Location(world, x - radius, y, z + radius),
                new Location(world, x + radius, y, z + radius),
                new Location(world, x - radius, y, z),
                new Location(world, x + radius, y, z),
                new Location(world, x, y, z - radius),
                new Location(world, x, y, z + radius)
            };

            for (Location p : perimeter) {
                if (!canPlaceSinglePoint(player, p)) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Internal point check testing whether an individual block coordinate is permitted across all active claim systems.
     */
    private static boolean canPlaceSinglePoint(Player player, Location location) {
        // 0. WorldGuard 7.x region check
        if (worldGuardAvailable && !checkWorldGuardBuild(player, location)) {
            return false;
        }

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

        // 2. BentoBox Island check (with wilderness & co-op guarding)
        if (bentoBoxAvailable && bentoIslandsManager != null && bentoGetIslandAt != null) {
            try {
                if (location.getWorld() != null && bentoIwm != null && bentoInWorld != null) {
                    boolean isBentoWorld = (boolean) bentoInWorld.invoke(bentoIwm, location.getWorld());
                    if (isBentoWorld) {
                        Object optionalIsland = bentoGetIslandAt.invoke(bentoIslandsManager, location);
                        if (optionalIsland == null || !(boolean) bentoOptionalIsPresent.invoke(optionalIsland)) {
                            return false; // Deny placement in BentoBox SkyBlock wilderness
                        }
                        Object island = bentoOptionalGet.invoke(optionalIsland);
                        if (!isBentoIslandMemberOrCoop(island, player.getUniqueId())) {
                            return false;
                        }
                    }
                } else {
                    Object optionalIsland = bentoGetIslandAt.invoke(bentoIslandsManager, location);
                    if (optionalIsland != null && (boolean) bentoOptionalIsPresent.invoke(optionalIsland)) {
                        Object island = bentoOptionalGet.invoke(optionalIsland);
                        if (!isBentoIslandMemberOrCoop(island, player.getUniqueId())) {
                            return false;
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }

        // 3. Towny check (PlayerCacheUtil canonical permission)
        if (townyAvailable && townyGetCachePermission != null && townyActionBuild != null) {
            try {
                boolean canBuild = (boolean) townyGetCachePermission.invoke(null, player, location, Material.STONE, townyActionBuild);
                if (!canBuild) {
                    return false;
                }
            } catch (Throwable ignored) {}
        }

        // 4. Lands check (Area trust / build permission)
        if (landsAvailable && landsIntegration != null && landsGetArea != null) {
            try {
                Object area = landsGetArea.invoke(landsIntegration, location);
                if (area != null && !isLandsAuthorized(area, player, location, true)) {
                    return false;
                }
            } catch (Throwable ignored) {}
        }

        // 5. SuperiorSkyblock2 check (with wilderness & co-op guarding)
        if (superiorSkyblockAvailable && ssGetIslandAt != null) {
            try {
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
     * Follows the affirmative team authorization pattern across all registered providers.
     */
    public static boolean canAccessMinion(Player player, Minion minion) {
        if (player == null || minion == null) return false;
        if (hasPermission(player, "minionx.admin") ||
            hasPermission(player, "minionx.bypass.protection") ||
            hasPermission(player, "worldguard.region.bypass.*")) {
            return true;
        }

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

        // WorldGuard region access check (Flags.INTERACT)
        if (worldGuardAvailable && !checkWorldGuardAccess(player, loc)) {
            return false;
        }

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
                    if (denyReason == null) return true;
                }
            } catch (Throwable ignored) {}
        }

        // 2. BentoBox Island team member / co-op check
        if (bentoBoxAvailable && bentoIslandsManager != null && bentoGetIslandAt != null) {
            try {
                Object optionalIsland = bentoGetIslandAt.invoke(bentoIslandsManager, loc);
                if (optionalIsland != null && (boolean) bentoOptionalIsPresent.invoke(optionalIsland)) {
                    Object island = bentoOptionalGet.invoke(optionalIsland);
                    if (isBentoIslandMemberOrCoop(island, player.getUniqueId())) {
                        return true;
                    }
                }
            } catch (Throwable ignored) {}
        }

        // 3. Lands team/access check
        if (landsAvailable && landsIntegration != null && landsGetArea != null) {
            try {
                Object area = landsGetArea.invoke(landsIntegration, loc);
                if (area != null && isLandsAuthorized(area, player, loc, false)) {
                    return true;
                }
            } catch (Throwable ignored) {}
        }

        // 4. Towny container/switch check
        if (townyAvailable && townyGetCachePermission != null && townyActionSwitch != null) {
            try {
                boolean canSwitch = (boolean) townyGetCachePermission.invoke(null, player, loc, Material.CHEST, townyActionSwitch);
                if (canSwitch) {
                    return true;
                }
            } catch (Throwable ignored) {}
        }

        // 5. SuperiorSkyblock2 team/co-op check
        if (superiorSkyblockAvailable && ssGetIslandAt != null) {
            try {
                Object island = ssGetIslandAt.invoke(null, loc);
                if (island != null && isSuperiorMemberOrCoop(island, player)) {
                    return true;
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
        if (hasPermission(player, "minionx.admin") ||
            hasPermission(player, "minionx.bypass.protection") ||
            hasPermission(player, "worldguard.region.bypass.*")) {
            return true;
        }

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
    public static boolean isWorldGuardAvailable() {
        return worldGuardAvailable;
    }

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
