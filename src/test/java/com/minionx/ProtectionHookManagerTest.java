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
import java.util.UUID;

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

            // Test Placement
            assertTrue(ProtectionHookManager.canPlaceMinion(coopPlayer, testLoc),
                    "Island co-op member must be permitted to place minions on the island");
            assertFalse(ProtectionHookManager.canPlaceMinion(strangerPlayer, testLoc),
                    "Non-island member must be prevented from placing minions on SuperiorSkyblock2 island");

            // Test Access under TEAM_ACCESS mode (minion belongs to AUTHORIZED_UUID, player is co-op)
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
            // In wilderness, getIslandAt returns null
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

    public static class MockSuperiorSkyblockApi {
        public static Object getIslandAt(Location loc) {
            return new MockSuperiorIsland();
        }

        public static Object getWildernessIslandAt(Location loc) {
            return null; // Wilderness outside of islands
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
}
