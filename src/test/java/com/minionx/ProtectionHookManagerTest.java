package com.minionx;

import com.minionx.api.MinionType;
import com.minionx.core.Minion;
import com.minionx.hook.CollectionsHook;
import com.minionx.hook.IslandHookManager;
import com.minionx.hook.ProtectionHookManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class ProtectionHookManagerTest {

    public static UUID AUTHORIZED_UUID;
    public static UUID COOP_MEMBER_UUID;
    private UUID strangerId;
    private Location testLoc;

    @BeforeEach
    public void setUp() {
        AUTHORIZED_UUID = UUID.randomUUID();
        COOP_MEMBER_UUID = UUID.randomUUID();
        strangerId = UUID.randomUUID();
        testLoc = new Location(null, 100, 65, 100);
        ProtectionHookManager.checkHooks();
        CollectionsHook.checkHooks();
    }

    @AfterEach
    public void tearDown() {
        ProtectionHookManager.checkHooks();
        CollectionsHook.checkHooks();
    }

    @Test
    @DisplayName("Admin player with minionx.admin permission bypasses all placement, access, and pickup checks")
    public void testAdminBypass() {
        Player adminPlayer = createMockPlayer(strangerId, true);
        Minion minion = new Minion(UUID.randomUUID(), AUTHORIZED_UUID, MinionType.DIAMOND, 1, testLoc);

        assertTrue(ProtectionHookManager.canPlaceMinion(adminPlayer, testLoc),
                "Admin must bypass placement restrictions");
        assertTrue(ProtectionHookManager.canAccessMinion(adminPlayer, minion),
                "Admin must bypass access restrictions");
        assertTrue(ProtectionHookManager.canPickupMinion(adminPlayer, minion),
                "Admin must bypass pickup restrictions");
    }

    @Test
    @DisplayName("Minion owner is always authorized to access and pickup their own minion")
    public void testOwnerAlwaysAuthorized() {
        Player ownerPlayer = createMockPlayer(AUTHORIZED_UUID, false);
        Minion minion = new Minion(UUID.randomUUID(), AUTHORIZED_UUID, MinionType.COBBLESTONE, 5, testLoc);

        assertTrue(ProtectionHookManager.canAccessMinion(ownerPlayer, minion),
                "Owner must always be authorized to access their minion");
        assertTrue(ProtectionHookManager.canPickupMinion(ownerPlayer, minion),
                "Owner must always be authorized to pick up their minion");
    }

    @Test
    @DisplayName("Null parameters safely return false without throwing NullPointerException")
    public void testNullSafety() {
        Player player = createMockPlayer(AUTHORIZED_UUID, false);
        Minion minion = new Minion(UUID.randomUUID(), AUTHORIZED_UUID, MinionType.IRON, 1, testLoc);

        assertFalse(ProtectionHookManager.canPlaceMinion(null, testLoc));
        assertFalse(ProtectionHookManager.canPlaceMinion(player, null));
        assertFalse(ProtectionHookManager.canAccessMinion(null, minion));
        assertFalse(ProtectionHookManager.canAccessMinion(player, null));
        assertFalse(ProtectionHookManager.canPickupMinion(null, minion));
        assertFalse(ProtectionHookManager.canPickupMinion(player, null));
    }

    @Test
    @DisplayName("SuperiorSkyblock2 initialization gracefully handles absent plugin environment")
    public void testSuperiorSkyblockGracefulFallback() {
        assertDoesNotThrow(ProtectionHookManager::checkHooks,
                "checkHooks must never throw an exception in headless or absent plugin environments");
        assertFalse(ProtectionHookManager.isSuperiorSkyblockAvailable(),
                "SuperiorSkyblock2 must be false when plugin is not loaded");
    }

    @Test
    @DisplayName("Simulated SuperiorSkyblock2 reflection handles authorize members/coops and reject non-members")
    public void testSimulatedSuperiorSkyblockProtectionReflection() throws Exception {
        Player coopPlayer = createMockPlayer(COOP_MEMBER_UUID, false);
        Player strangerPlayer = createMockPlayer(strangerId, false);
        Minion minion = new Minion(UUID.randomUUID(), AUTHORIZED_UUID, MinionType.IRON, 3, testLoc);

        Field availableField = ProtectionHookManager.class.getDeclaredField("superiorSkyblockAvailable");
        availableField.setAccessible(true);
        Field getIslandAtField = ProtectionHookManager.class.getDeclaredField("ssGetIslandAt");
        getIslandAtField.setAccessible(true);
        Field getPlayerField = ProtectionHookManager.class.getDeclaredField("ssGetPlayer");
        getPlayerField.setAccessible(true);
        Field isMemberField = ProtectionHookManager.class.getDeclaredField("ssIslandIsMember");
        isMemberField.setAccessible(true);
        Field isCoopField = ProtectionHookManager.class.getDeclaredField("ssIslandIsCoop");
        isCoopField.setAccessible(true);

        try {
            availableField.set(null, true);
            Method dummyGetIsland = MockSuperiorSkyblockApi.class.getMethod("getIslandAt", Location.class);
            Method dummyGetPlayer = MockSuperiorSkyblockApi.class.getMethod("getPlayer", Player.class);
            Method dummyIsMember = MockSuperiorIsland.class.getMethod("isMember", Object.class);
            Method dummyIsCoop = MockSuperiorIsland.class.getMethod("isCoop", Object.class);

            getIslandAtField.set(null, dummyGetIsland);
            getPlayerField.set(null, dummyGetPlayer);
            isMemberField.set(null, dummyIsMember);
            isCoopField.set(null, dummyIsCoop);

            assertTrue(ProtectionHookManager.canPlaceMinion(coopPlayer, testLoc),
                    "Island co-op member must be permitted to place minions on the island");
            assertFalse(ProtectionHookManager.canPlaceMinion(strangerPlayer, testLoc),
                    "Non-island member must be prevented from placing minions on SuperiorSkyblock2 island");

            assertTrue(ProtectionHookManager.canAccessMinion(coopPlayer, minion),
                    "Island co-op member must have GUI access under TEAM_ACCESS mode");
            assertFalse(ProtectionHookManager.canAccessMinion(strangerPlayer, minion),
                    "Non-member stranger must be denied GUI access to minion on island");

        } finally {
            availableField.set(null, false);
            getIslandAtField.set(null, null);
            getPlayerField.set(null, null);
            isMemberField.set(null, null);
            isCoopField.set(null, null);
        }
    }

    @Test
    @DisplayName("Simulated SuperiorSkyblock2 prevents placement in SkyBlock wilderness")
    public void testSimulatedSuperiorSkyblockWildernessCheck() throws Exception {
        Player normalPlayer = createMockPlayer(strangerId, false);
        Player adminPlayer = createMockPlayer(strangerId, true);

        World mockWorld = createMockWorld("superior_skyblock_world");
        Location wildernessLoc = new Location(mockWorld, 500, 65, 500);

        Field availableField = ProtectionHookManager.class.getDeclaredField("superiorSkyblockAvailable");
        availableField.setAccessible(true);
        Field gridManagerField = ProtectionHookManager.class.getDeclaredField("ssGridManager");
        gridManagerField.setAccessible(true);
        Field isIslandsWorldField = ProtectionHookManager.class.getDeclaredField("ssIsIslandsWorld");
        isIslandsWorldField.setAccessible(true);
        Field getIslandAtField = ProtectionHookManager.class.getDeclaredField("ssGetIslandAt");
        getIslandAtField.setAccessible(true);

        try {
            availableField.set(null, true);
            gridManagerField.set(null, new MockGridManager());
            isIslandsWorldField.set(null, MockGridManager.class.getMethod("isIslandsWorld", World.class));
            getIslandAtField.set(null, MockSuperiorSkyblockApi.class.getMethod("getWildernessIslandAt", Location.class));

            assertFalse(ProtectionHookManager.canPlaceMinion(normalPlayer, wildernessLoc),
                    "Normal player must NOT be allowed to place minions in SkyBlock wilderness");
            assertTrue(ProtectionHookManager.canPlaceMinion(adminPlayer, wildernessLoc),
                    "Admin must bypass wilderness placement restrictions");
        } finally {
            availableField.set(null, false);
            gridManagerField.set(null, null);
            isIslandsWorldField.set(null, null);
            getIslandAtField.set(null, null);
        }
    }

    @Test
    @DisplayName("BentoBox simulation: Guards against wilderness placement and recognizes co-op ranks")
    public void testBentoBoxCoopAndWildernessGuard() throws Exception {
        Player coopPlayer = createMockPlayer(COOP_MEMBER_UUID, false);
        Player strangerPlayer = createMockPlayer(strangerId, false);
        Minion minion = new Minion(UUID.randomUUID(), AUTHORIZED_UUID, MinionType.IRON, 1, testLoc);

        World bentoWorld = createMockWorld("bskyblock_world");
        Location islandLoc = new Location(bentoWorld, 100, 65, 100);
        Location wildernessLoc = new Location(bentoWorld, 9000, 65, 9000);

        Field availableField = ProtectionHookManager.class.getDeclaredField("bentoBoxAvailable");
        availableField.setAccessible(true);
        Field islandsManagerField = ProtectionHookManager.class.getDeclaredField("bentoIslandsManager");
        islandsManagerField.setAccessible(true);
        Field getIslandAtField = ProtectionHookManager.class.getDeclaredField("bentoGetIslandAt");
        getIslandAtField.setAccessible(true);
        Field isPresentField = ProtectionHookManager.class.getDeclaredField("bentoOptionalIsPresent");
        isPresentField.setAccessible(true);
        Field optionalGetField = ProtectionHookManager.class.getDeclaredField("bentoOptionalGet");
        optionalGetField.setAccessible(true);
        Field isMemberField = ProtectionHookManager.class.getDeclaredField("bentoIslandIsMember");
        isMemberField.setAccessible(true);
        Field getMembersField = ProtectionHookManager.class.getDeclaredField("bentoIslandGetMembers");
        getMembersField.setAccessible(true);
        Field iwmField = ProtectionHookManager.class.getDeclaredField("bentoIwm");
        iwmField.setAccessible(true);
        Field inWorldField = ProtectionHookManager.class.getDeclaredField("bentoInWorld");
        inWorldField.setAccessible(true);

        try {
            availableField.set(null, true);
            islandsManagerField.set(null, new MockBentoIslandsManager());
            getIslandAtField.set(null, MockBentoIslandsManager.class.getMethod("getIslandAt", Location.class));
            isPresentField.set(null, MockOptional.class.getMethod("isPresent"));
            optionalGetField.set(null, MockOptional.class.getMethod("get"));
            isMemberField.set(null, MockBentoIsland.class.getMethod("isMember", UUID.class));
            getMembersField.set(null, MockBentoIsland.class.getMethod("getMembers"));
            iwmField.set(null, new MockBentoIwm());
            inWorldField.set(null, MockBentoIwm.class.getMethod("inWorld", World.class));

            // Test 1: Wilderness in BentoBox world is denied
            assertFalse(ProtectionHookManager.canPlaceMinion(strangerPlayer, wildernessLoc),
                    "Placing minion in BentoBox wilderness must be denied");

            // Test 2: Coop member (rank 100) on island is authorized
            assertTrue(ProtectionHookManager.canPlaceMinion(coopPlayer, islandLoc),
                    "BentoBox co-op member must be allowed to place on island");
            assertTrue(ProtectionHookManager.canAccessMinion(coopPlayer, minion),
                    "BentoBox co-op member must be allowed team access to minion GUI");

            // Test 3: Stranger on island is rejected
            assertFalse(ProtectionHookManager.canPlaceMinion(strangerPlayer, islandLoc),
                    "Stranger must be rejected on BentoBox island");
            assertFalse(ProtectionHookManager.canAccessMinion(strangerPlayer, minion),
                    "Stranger must be denied access to minion on BentoBox island");

        } finally {
            availableField.set(null, false);
            islandsManagerField.set(null, null);
            getIslandAtField.set(null, null);
            isPresentField.set(null, null);
            optionalGetField.set(null, null);
            isMemberField.set(null, null);
            getMembersField.set(null, null);
            iwmField.set(null, null);
            inWorldField.set(null, null);
        }
    }

    @Test
    @DisplayName("Towny simulation: PlayerCacheUtil permission enforcement for build and switch actions")
    public void testTownyPlayerCachePermission() throws Exception {
        Player residentPlayer = createMockPlayer(AUTHORIZED_UUID, false);
        Player strangerPlayer = createMockPlayer(strangerId, false);
        Minion minion = new Minion(UUID.randomUUID(), AUTHORIZED_UUID, MinionType.IRON, 1, testLoc);

        Field availableField = ProtectionHookManager.class.getDeclaredField("townyAvailable");
        availableField.setAccessible(true);
        Field getCachePermField = ProtectionHookManager.class.getDeclaredField("townyGetCachePermission");
        getCachePermField.setAccessible(true);
        Field actionBuildField = ProtectionHookManager.class.getDeclaredField("townyActionBuild");
        actionBuildField.setAccessible(true);
        Field actionSwitchField = ProtectionHookManager.class.getDeclaredField("townyActionSwitch");
        actionSwitchField.setAccessible(true);

        try {
            availableField.set(null, true);
            Method dummyCachePerm = MockTownyPlayerCacheUtil.class.getMethod("getCachePermission",
                    Player.class, Location.class, Material.class, Object.class);
            getCachePermField.set(null, dummyCachePerm);
            actionBuildField.set(null, "BUILD");
            actionSwitchField.set(null, "SWITCH");

            // Placement test
            assertTrue(ProtectionHookManager.canPlaceMinion(residentPlayer, testLoc),
                    "Town resident with BUILD permission must be allowed to place");
            assertFalse(ProtectionHookManager.canPlaceMinion(strangerPlayer, testLoc),
                    "Stranger lacking BUILD permission must be denied placement");

            // Access test
            assertTrue(ProtectionHookManager.canAccessMinion(residentPlayer, minion),
                    "Town resident with SWITCH permission must be granted team access");
            assertFalse(ProtectionHookManager.canAccessMinion(strangerPlayer, minion),
                    "Stranger lacking SWITCH permission must be denied team access");

        } finally {
            availableField.set(null, false);
            getCachePermField.set(null, null);
            actionBuildField.set(null, null);
            actionSwitchField.set(null, null);
        }
    }

    @Test
    @DisplayName("Lands simulation: Modern Area isTrusted and permission enforcement")
    public void testLandsAreaTrustAndNegativeGuard() throws Exception {
        Player trustedPlayer = createMockPlayer(AUTHORIZED_UUID, false);
        Player strangerPlayer = createMockPlayer(strangerId, false);
        Minion minion = new Minion(UUID.randomUUID(), AUTHORIZED_UUID, MinionType.IRON, 1, testLoc);

        Field availableField = ProtectionHookManager.class.getDeclaredField("landsAvailable");
        availableField.setAccessible(true);
        Field integrationField = ProtectionHookManager.class.getDeclaredField("landsIntegration");
        integrationField.setAccessible(true);
        Field getAreaField = ProtectionHookManager.class.getDeclaredField("landsGetArea");
        getAreaField.setAccessible(true);
        Field isTrustedField = ProtectionHookManager.class.getDeclaredField("landsIsTrusted");
        isTrustedField.setAccessible(true);

        try {
            availableField.set(null, true);
            integrationField.set(null, new MockLandsIntegration());
            getAreaField.set(null, MockLandsIntegration.class.getMethod("getArea", Location.class));
            isTrustedField.set(null, MockLandsArea.class.getMethod("isTrusted", UUID.class));

            assertTrue(ProtectionHookManager.canPlaceMinion(trustedPlayer, testLoc),
                    "Trusted Lands member must be permitted to place minions");
            assertFalse(ProtectionHookManager.canPlaceMinion(strangerPlayer, testLoc),
                    "Non-trusted player must be denied minion placement in Lands area");

            assertTrue(ProtectionHookManager.canAccessMinion(trustedPlayer, minion),
                    "Trusted Lands member must be granted minion GUI access");
            assertFalse(ProtectionHookManager.canAccessMinion(strangerPlayer, minion),
                    "Non-trusted player must be denied minion GUI access in Lands area");

        } finally {
            availableField.set(null, false);
            integrationField.set(null, null);
            getAreaField.set(null, null);
            isTrustedField.set(null, null);
        }
    }

    @Test
    @DisplayName("Veto Chain: Multiple plugins active must NOT short-circuit prematurely if one denies")
    public void testVetoChainNoShortCircuit() throws Exception {
        Player testPlayer = createMockPlayer(strangerId, false);

        Field bentoAvailable = ProtectionHookManager.class.getDeclaredField("bentoBoxAvailable");
        bentoAvailable.setAccessible(true);
        Field townyAvailable = ProtectionHookManager.class.getDeclaredField("townyAvailable");
        townyAvailable.setAccessible(true);
        Field townyGetCachePerm = ProtectionHookManager.class.getDeclaredField("townyGetCachePermission");
        townyGetCachePerm.setAccessible(true);
        Field townyActionBuild = ProtectionHookManager.class.getDeclaredField("townyActionBuild");
        townyActionBuild.setAccessible(true);

        try {
            // BentoBox is absent/neutral
            bentoAvailable.set(null, false);

            // Towny is present and explicitly denies build
            townyAvailable.set(null, true);
            townyGetCachePerm.set(null, MockTownyPlayerCacheUtil.class.getMethod("getCachePermission",
                    Player.class, Location.class, Material.class, Object.class));
            townyActionBuild.set(null, "BUILD");

            assertFalse(ProtectionHookManager.canPlaceMinion(testPlayer, testLoc),
                    "Towny veto must be respected even when other plugins are neutral");
        } finally {
            bentoAvailable.set(null, false);
            townyAvailable.set(null, false);
            townyGetCachePerm.set(null, null);
            townyActionBuild.set(null, null);
        }
    }

    @Test
    @DisplayName("IslandHookManager backwards compatibility delegates to ProtectionHookManager")
    public void testIslandHookManagerDelegation() {
        Player adminPlayer = createMockPlayer(strangerId, true);
        Minion minion = new Minion(UUID.randomUUID(), AUTHORIZED_UUID, MinionType.DIAMOND, 1, testLoc);

        assertDoesNotThrow(IslandHookManager::checkHooks);
        assertTrue(IslandHookManager.canPlaceMinion(adminPlayer, testLoc));
        assertTrue(IslandHookManager.canAccessMinion(adminPlayer, minion));
        assertTrue(IslandHookManager.canPickupMinion(adminPlayer, minion));
    }

    @Test
    @DisplayName("CollectionsHook executes safely without errors or exceptions")
    public void testCollectionsHookReflectionSafety() {
        Player player = createMockPlayer(AUTHORIZED_UUID, false);
        Minion minion = new Minion(UUID.randomUUID(), AUTHORIZED_UUID, MinionType.IRON, 1, testLoc);

        assertDoesNotThrow(CollectionsHook::checkHooks);
        assertDoesNotThrow(() -> CollectionsHook.recordCollection(player, minion, Material.COBBLESTONE, 1));
        assertDoesNotThrow(() -> CollectionsHook.recordCollection(null, minion, Material.IRON_INGOT, 5));
    }

    @Test
    @DisplayName("Simulated WorldGuard 7.x: testState BUILD/INTERACT checks and bypass permissions")
    public void testSimulatedWorldGuard7ProtectionAndBypass() throws Exception {
        Player authorizedPlayer = createMockPlayer(AUTHORIZED_UUID, false);
        Player strangerPlayer = createMockPlayer(strangerId, false);
        Player wgBypassPlayer = createMockPlayerWithPerms(strangerId, Set.of("worldguard.region.bypass.*"));
        Player minionxBypassPlayer = createMockPlayerWithPerms(strangerId, Set.of("minionx.bypass.protection"));
        Minion minion = new Minion(UUID.randomUUID(), AUTHORIZED_UUID, MinionType.IRON, 1, testLoc);

        Field availableField = ProtectionHookManager.class.getDeclaredField("worldGuardAvailable");
        availableField.setAccessible(true);
        Field pluginField = ProtectionHookManager.class.getDeclaredField("wgPluginInstance");
        pluginField.setAccessible(true);
        Field containerField = ProtectionHookManager.class.getDeclaredField("wgRegionContainer");
        containerField.setAccessible(true);
        Field createQueryField = ProtectionHookManager.class.getDeclaredField("wgCreateQuery");
        createQueryField.setAccessible(true);
        Field adaptLocField = ProtectionHookManager.class.getDeclaredField("wgAdaptLocation");
        adaptLocField.setAccessible(true);
        Field wrapPlayerField = ProtectionHookManager.class.getDeclaredField("wgWrapPlayer");
        wrapPlayerField.setAccessible(true);
        Field flagBuildField = ProtectionHookManager.class.getDeclaredField("wgFlagBuild");
        flagBuildField.setAccessible(true);
        Field flagInteractField = ProtectionHookManager.class.getDeclaredField("wgFlagInteract");
        flagInteractField.setAccessible(true);
        Field testStateField = ProtectionHookManager.class.getDeclaredField("wgTestState");
        testStateField.setAccessible(true);

        try {
            availableField.set(null, true);
            pluginField.set(null, new MockWorldGuardPlugin());
            containerField.set(null, new MockRegionContainer());
            createQueryField.set(null, MockRegionContainer.class.getMethod("createQuery"));
            adaptLocField.set(null, MockWorldGuardAdapter.class.getMethod("adapt", Location.class));
            wrapPlayerField.set(null, MockWorldGuardPlugin.class.getMethod("wrapPlayer", Player.class));
            flagBuildField.set(null, "BUILD");
            flagInteractField.set(null, "INTERACT");
            testStateField.set(null, MockRegionQuery.class.getMethod("testState", Object.class, Object.class, Object.class));

            // Placement: Authorized player succeeds, stranger fails, bypass player succeeds
            assertTrue(ProtectionHookManager.canPlaceMinion(authorizedPlayer, testLoc),
                    "Authorized player must be permitted to place in WorldGuard region");
            assertFalse(ProtectionHookManager.canPlaceMinion(strangerPlayer, testLoc),
                    "Stranger must be denied placement in WorldGuard region");
            assertTrue(ProtectionHookManager.canPlaceMinion(wgBypassPlayer, testLoc),
                    "Player with worldguard.region.bypass.* must bypass WG placement veto");
            assertTrue(ProtectionHookManager.canPlaceMinion(minionxBypassPlayer, testLoc),
                    "Player with minionx.bypass.protection must bypass WG placement veto");

            // Access: Owner always allowed; for unowned/team checks, testState INTERACT is respected
            assertTrue(ProtectionHookManager.canAccessMinion(authorizedPlayer, minion),
                    "Minion owner must always have GUI access regardless of region interact state");
            Minion unownedMinion = new Minion(UUID.randomUUID(), null, MinionType.IRON, 1, testLoc);
            assertTrue(ProtectionHookManager.canAccessMinion(authorizedPlayer, unownedMinion));
            assertFalse(ProtectionHookManager.canAccessMinion(strangerPlayer, unownedMinion));
        } finally {
            availableField.set(null, false);
            pluginField.set(null, null);
            containerField.set(null, null);
            createQueryField.set(null, null);
            adaptLocField.set(null, null);
            wrapPlayerField.set(null, null);
            flagBuildField.set(null, null);
            flagInteractField.set(null, null);
            testStateField.set(null, null);
        }
    }

    @Test
    @DisplayName("9-point perimeter boundary check denies placement when working area crosses protected border")
    public void testMinionAreaPerimeterCheckVeto() throws Exception {
        Player authorizedPlayer = createMockPlayer(AUTHORIZED_UUID, false);

        // Center point (100, 65, 100) is allowed, but boundary at x=102 crosses into forbidden territory (x > 100)
        World mockWorld = createMockWorld("perimeter_world");
        Location centerLoc = new Location(mockWorld, 100, 65, 100);

        Field availableField = ProtectionHookManager.class.getDeclaredField("worldGuardAvailable");
        availableField.setAccessible(true);
        Field pluginField = ProtectionHookManager.class.getDeclaredField("wgPluginInstance");
        pluginField.setAccessible(true);
        Field containerField = ProtectionHookManager.class.getDeclaredField("wgRegionContainer");
        containerField.setAccessible(true);
        Field createQueryField = ProtectionHookManager.class.getDeclaredField("wgCreateQuery");
        createQueryField.setAccessible(true);
        Field adaptLocField = ProtectionHookManager.class.getDeclaredField("wgAdaptLocation");
        adaptLocField.setAccessible(true);
        Field wrapPlayerField = ProtectionHookManager.class.getDeclaredField("wgWrapPlayer");
        wrapPlayerField.setAccessible(true);
        Field flagBuildField = ProtectionHookManager.class.getDeclaredField("wgFlagBuild");
        flagBuildField.setAccessible(true);
        Field flagInteractField = ProtectionHookManager.class.getDeclaredField("wgFlagInteract");
        flagInteractField.setAccessible(true);
        Field testStateField = ProtectionHookManager.class.getDeclaredField("wgTestState");
        testStateField.setAccessible(true);

        try {
            availableField.set(null, true);
            pluginField.set(null, new MockWorldGuardPlugin());
            containerField.set(null, new MockRegionContainer(new MockPerimeterRegionQuery()));
            createQueryField.set(null, MockRegionContainer.class.getMethod("createQuery"));
            adaptLocField.set(null, MockWorldGuardAdapter.class.getMethod("adapt", Location.class));
            wrapPlayerField.set(null, MockWorldGuardPlugin.class.getMethod("wrapPlayer", Player.class));
            flagBuildField.set(null, "BUILD");
            flagInteractField.set(null, "INTERACT");
            testStateField.set(null, MockPerimeterRegionQuery.class.getMethod("testState", Object.class, Object.class, Object.class));

            // Radius 0: Only center checked -> returns true
            assertTrue(ProtectionHookManager.canPlaceMinion(authorizedPlayer, centerLoc, 0),
                    "Radius 0 should only check center location");

            // Radius 2: Outer perimeter points checked (e.g., x=102 > 100) -> returns false!
            assertFalse(ProtectionHookManager.canPlaceMinion(authorizedPlayer, centerLoc, 2),
                    "Radius 2 should check 9-point perimeter and deny placement when area crosses border");
        } finally {
            availableField.set(null, false);
            pluginField.set(null, null);
            containerField.set(null, null);
            createQueryField.set(null, null);
            adaptLocField.set(null, null);
            wrapPlayerField.set(null, null);
            flagBuildField.set(null, null);
            flagInteractField.set(null, null);
            testStateField.set(null, null);
        }
    }

    private Player createMockPlayerWithPerms(UUID uuid, Set<String> perms) {
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("getUniqueId".equals(name)) return uuid;
                    if ("hasPermission".equals(name) && args != null && args.length > 0) {
                        return perms.contains(String.valueOf(args[0]).toLowerCase());
                    }
                    if ("isOp".equals(name)) return false;
                    if ("getName".equals(name)) return "TestPlayer_" + uuid.toString().substring(0, 4);
                    if ("toString".equals(name)) return "MockPlayer[" + uuid + "]";
                    return null;
                }
        );
    }

    private Player createMockPlayer(UUID uuid, boolean isAdmin) {
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("getUniqueId".equals(name)) return uuid;
                    if ("hasPermission".equals(name) && args != null && args.length > 0) {
                        return "minionx.admin".equalsIgnoreCase(String.valueOf(args[0])) && isAdmin;
                    }
                    if ("isOp".equals(name)) return isAdmin;
                    if ("getName".equals(name)) return "TestPlayer_" + uuid.toString().substring(0, 4);
                    if ("toString".equals(name)) return "MockPlayer[" + uuid + "]";
                    return null;
                }
        );
    }

    private World createMockWorld(String name) {
        return (World) Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[]{World.class},
                (proxy, method, args) -> {
                    if ("getName".equals(method.getName())) return name;
                    return null;
                }
        );
    }

    // --- Mock Classes for Testing ---

    public static class MockSuperiorSkyblockApi {
        public static Object getIslandAt(Location loc) {
            return new MockSuperiorIsland();
        }

        public static Object getWildernessIslandAt(Location loc) {
            return null;
        }

        public static Object getPlayer(Player player) {
            return player;
        }
    }

    public static class MockSuperiorIsland {
        public boolean isMember(Object sPlayer) {
            if (sPlayer instanceof Player p) {
                return ProtectionHookManagerTest.AUTHORIZED_UUID.equals(p.getUniqueId());
            }
            return false;
        }

        public boolean isCoop(Object sPlayer) {
            if (sPlayer instanceof Player p) {
                return ProtectionHookManagerTest.COOP_MEMBER_UUID.equals(p.getUniqueId());
            }
            return false;
        }
    }

    public static class MockGridManager {
        public boolean isIslandsWorld(World world) {
            return world != null && "superior_skyblock_world".equals(world.getName());
        }
    }

    public static class MockBentoIslandsManager {
        public static Object getIslandAt(Location loc) {
            if (loc != null && loc.getX() > 5000) {
                return new MockOptional(null); // Wilderness
            }
            return new MockOptional(new MockBentoIsland());
        }
    }

    public static class MockOptional {
        private final Object value;
        public MockOptional(Object value) { this.value = value; }
        public boolean isPresent() { return value != null; }
        public Object get() { return value; }
    }

    public static class MockBentoIsland {
        public boolean isMember(UUID uuid) {
            return ProtectionHookManagerTest.AUTHORIZED_UUID.equals(uuid);
        }

        public Map<UUID, Integer> getMembers() {
            Map<UUID, Integer> map = new HashMap<>();
            map.put(ProtectionHookManagerTest.AUTHORIZED_UUID, 1000); // Owner
            map.put(ProtectionHookManagerTest.COOP_MEMBER_UUID, 100);  // Coop
            return map;
        }
    }

    public static class MockBentoIwm {
        public boolean inWorld(World world) {
            return world != null && "bskyblock_world".equals(world.getName());
        }
    }

    public static class MockTownyPlayerCacheUtil {
        public static boolean getCachePermission(Player player, Location loc, Material mat, Object action) {
            if (player == null) return false;
            return ProtectionHookManagerTest.AUTHORIZED_UUID.equals(player.getUniqueId());
        }
    }

    public static class MockLandsIntegration {
        public static Object getArea(Location loc) {
            return new MockLandsArea();
        }
    }

    public static class MockLandsArea {
        public boolean isTrusted(UUID uuid) {
            return ProtectionHookManagerTest.AUTHORIZED_UUID.equals(uuid);
        }
    }

    public static class MockWorldGuardAdapter {
        public static Object adapt(Location loc) {
            return loc;
        }
    }

    public static class MockWorldGuardPlugin {
        public Object wrapPlayer(Player player) {
            return player;
        }
    }

    public static class MockRegionContainer {
        private final Object query;

        public MockRegionContainer() {
            this(new MockRegionQuery());
        }

        public MockRegionContainer(Object query) {
            this.query = query;
        }

        public Object createQuery() {
            return query;
        }
    }

    public static class MockRegionQuery {
        public boolean testState(Object loc, Object player, Object flags) {
            if (player instanceof Player p) {
                return ProtectionHookManagerTest.AUTHORIZED_UUID.equals(p.getUniqueId());
            }
            return false;
        }
    }

    public static class MockPerimeterRegionQuery {
        public boolean testState(Object loc, Object player, Object flags) {
            if (loc instanceof Location l) {
                // Deny any coordinates with X > 100 (e.g. 102)
                if (l.getX() > 100.0) {
                    return false;
                }
            }
            if (player instanceof Player p) {
                return ProtectionHookManagerTest.AUTHORIZED_UUID.equals(p.getUniqueId());
            }
            return false;
        }
    }

}
