package com.minionx;

import com.minionx.api.MinionType;
import com.minionx.core.Minion;
import com.minionx.hook.ProtectionHookManager;
import org.bukkit.Location;
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
    }

    @AfterEach
    public void tearDown() {
        // Reset hooks to clean state
        ProtectionHookManager.checkHooks();
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
        // Minion created by AUTHORIZED_UUID
        Minion minion = new Minion(UUID.randomUUID(), AUTHORIZED_UUID, MinionType.IRON, 3, testLoc);

        // Inject simulated SuperiorSkyblock2 handles via reflection
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
            // Restore fields to clean state
            availableField.set(null, false);
            getIslandAtField.set(null, null);
            getPlayerField.set(null, null);
            isMemberField.set(null, null);
            isCoopField.set(null, null);
        }
    }

    // Helper to create lightweight dynamic proxy for org.bukkit.entity.Player
    private Player createMockPlayer(UUID uuid, boolean isAdmin) {
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("getUniqueId".equals(name)) {
                        return uuid;
                    }
                    if ("hasPermission".equals(name) && args != null && args.length > 0) {
                        String perm = String.valueOf(args[0]);
                        if ("minionx.admin".equalsIgnoreCase(perm)) {
                            return isAdmin;
                        }
                        return false;
                    }
                    if ("isOp".equals(name)) {
                        return isAdmin;
                    }
                    if ("getName".equals(name)) {
                        return "TestPlayer_" + uuid.toString().substring(0, 4);
                    }
                    if ("toString".equals(name)) {
                        return "MockPlayer[" + uuid + "]";
                    }
                    return null;
                }
        );
    }

    // Mock reflection helper classes
    public static class MockSuperiorSkyblockApi {
        public static Object getIslandAt(Location loc) {
            return new MockSuperiorIsland();
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
}
