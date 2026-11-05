package com.minionx;

import com.minionx.api.PriceRegistry;
import com.minionx.core.MinionXPlaceholderExpansion;
import com.minionx.core.PlaceholderUtil;
import com.minionx.hook.CustomItemHook;
import com.minionx.hook.EconomyHook;
import com.minionx.hook.GeyserHook;
import com.minionx.hook.IslandHookManager;
import com.minionx.hook.MythicMobsHook;
import com.minionx.hook.ProtectionHookManager;
import com.minionx.hook.SkillHook;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verification test suite for soft dependencies hardening:
 * Vault (OfflinePlayer, Player, and String signatures),
 * Geyser & Floodgate, MythicMobs, SkillHook, CustomItemHook,
 * WorldGuard 7.x, minion area protection, and PlaceholderAPI expansion.
 */
public class SoftDependenciesHardeningTest {

    private Player mockPlayer;
    private UUID testPlayerUuid;

    @BeforeEach
    void setUp() {
        testPlayerUuid = UUID.randomUUID();
        mockPlayer = (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class, OfflinePlayer.class},
                (proxy, method, args) -> {
                    if ("getUniqueId".equals(method.getName())) return testPlayerUuid;
                    if ("getName".equals(method.getName())) return "Steve";
                    if ("isOnline".equals(method.getName())) return true;
                    if ("getPlayer".equals(method.getName())) return proxy;
                    return null;
                }
        );
    }

    @AfterEach
    void tearDown() {
        EconomyHook.setVaultEconomy(null);
    }

    // =========================================================================
    // 1. VAULT / ECONOMY HOOK: OfflinePlayer vs Player vs String signatures
    // =========================================================================

    public static class StandardVaultEconomy {
        // VaultAPI's real Economy interface defines OfflinePlayer!
        public double getBalance(OfflinePlayer player) { return 1250.50; }
        public boolean has(OfflinePlayer player, double amount) { return amount <= 1250.50; }
        public Object withdrawPlayer(OfflinePlayer player, double amount) {
            return new MockResponse(amount <= 1250.50, amount <= 1250.50 ? null : "Insufficient funds");
        }
        public Object depositPlayer(OfflinePlayer player, double amount) {
            return new MockResponse(true, null);
        }
    }

    public static class LegacyStringEconomy {
        public double getBalance(String playerName) { return 500.0; }
        public boolean has(String playerName, double amount) { return amount <= 500.0; }
        public Object withdrawPlayer(String playerName, double amount) {
            return new MockResponse(true, null);
        }
        public Object depositPlayer(String playerName, double amount) {
            return new MockResponse(true, null);
        }
    }

    public static class MockResponse {
        private final boolean success;
        private final String error;
        public MockResponse(boolean success, String error) {
            this.success = success;
            this.error = error;
        }
        public boolean transactionSuccess() { return success; }
        public String errorMessage() { return error; }
    }

    @Test
    @DisplayName("Vault: Standard OfflinePlayer interface parameter resolution (Defect 1.1 Fix)")
    void testVaultOfflinePlayerResolution() {
        EconomyHook.setVaultEconomy(new StandardVaultEconomy());
        assertTrue(EconomyHook.hasEconomy());

        assertEquals(1250.50, EconomyHook.getBalance(mockPlayer), 0.001);
        assertTrue(EconomyHook.has(mockPlayer, 500.0));
        assertFalse(EconomyHook.has(mockPlayer, 2000.0));

        assertTrue(EconomyHook.withdraw(mockPlayer, 100.0));
        assertFalse(EconomyHook.withdraw(mockPlayer, 5000.0));

        EconomyHook.PayoutResult payout = EconomyHook.depositWithResult(mockPlayer, 250.0);
        assertTrue(payout.isSuccess());
        assertNull(payout.errorMessage());
    }

    @Test
    @DisplayName("Vault: Legacy String parameter signature fallback")
    void testVaultLegacyStringResolution() {
        EconomyHook.setVaultEconomy(new LegacyStringEconomy());
        assertTrue(EconomyHook.hasEconomy());

        assertEquals(500.0, EconomyHook.getBalance(mockPlayer), 0.001);
        assertTrue(EconomyHook.has(mockPlayer, 250.0));
        assertTrue(EconomyHook.withdraw(mockPlayer, 50.0));

        EconomyHook.PayoutResult payout = EconomyHook.depositWithResult(mockPlayer, 100.0);
        assertTrue(payout.isSuccess());
    }

    @Test
    @DisplayName("Vault: Null player and negative amount edge cases")
    void testVaultEdgeCases() {
        EconomyHook.setVaultEconomy(new StandardVaultEconomy());
        assertEquals(0.0, EconomyHook.getBalance(null), 0.001);
        assertTrue(EconomyHook.has(null, 100.0));
        assertTrue(EconomyHook.has(mockPlayer, -50.0));
        assertTrue(EconomyHook.withdraw(null, 50.0));
        assertTrue(EconomyHook.withdraw(mockPlayer, -10.0));

        EconomyHook.PayoutResult zeroPayout = EconomyHook.depositWithResult(mockPlayer, 0.0);
        assertTrue(zeroPayout.isSuccess());
    }

    // =========================================================================
    // 2. GEYSER & FLOODGATE: Modern isFloodgatePlayer and fallback
    // =========================================================================

    @Test
    @DisplayName("GeyserHook: Standalone fallback returns false without throwing")
    void testGeyserHookSafelyHandlesMissingPlugins() {
        assertDoesNotThrow(() -> {
            GeyserHook.checkHooks();
            assertFalse(GeyserHook.isFloodgateAvailable());
            assertFalse(GeyserHook.isGeyserAvailable());
            assertFalse(GeyserHook.isCrossPlatformSupported());
            assertFalse(GeyserHook.isBedrockPlayer((Player) null));
            assertFalse(GeyserHook.isBedrockPlayer(UUID.randomUUID()));
            assertEquals("Java Edition", GeyserHook.getPlatform(mockPlayer));
        });
    }

    // =========================================================================
    // 3. SKILL HOOK: Null safety and invocation robustness
    // =========================================================================

    @Test
    @DisplayName("SkillHook: Null safety and missing dependency resilience")
    void testSkillHookNullSafety() {
        assertDoesNotThrow(() -> {
            SkillHook.checkHooks();
            assertFalse(SkillHook.isAuraSkillsAvailable());
            assertFalse(SkillHook.isEcoSkillsAvailable());

            // Should safely do nothing without NPE
            SkillHook.addSkillXp(null, "mining", 100.0);
            SkillHook.addSkillXp(mockPlayer, null, 100.0);
            SkillHook.addSkillXp(mockPlayer, "mining", -50.0);
            SkillHook.addSkillXp(mockPlayer, "mining", 100.0);
        });
    }

    // =========================================================================
    // 4. CUSTOM ITEM HOOK: Material fallback and reverse identification
    // =========================================================================

    @Test
    @DisplayName("CustomItemHook: Null safety, vanilla material resolution, and reverse identification")
    void testCustomItemHookResolution() {
        assertDoesNotThrow(() -> {
            CustomItemHook.checkHooks();
            assertFalse(CustomItemHook.isItemsAdderAvailable());
            assertFalse(CustomItemHook.isMmoItemsAvailable());
            assertFalse(CustomItemHook.isEcoItemsAvailable());

            assertNull(CustomItemHook.getItem(null));
            assertNull(CustomItemHook.getItem("   "));
            assertNull(CustomItemHook.getItem("NON_EXISTENT_MATERIAL_XYZ_123"));

            ItemStack diamond = CustomItemHook.getItem("DIAMOND");
            assertNotNull(diamond);
            assertEquals(Material.DIAMOND, diamond.getType());

            // Vanilla item should not be identified as custom item
            assertFalse(CustomItemHook.isCustomItem(diamond));
            assertNull(CustomItemHook.identifyCustomItem(diamond));
            assertNull(CustomItemHook.identifyCustomItem(null));
        });
    }

    // =========================================================================
    // 5. MYTHICMOBS HOOK: Server null safety & PDC Keys
    // =========================================================================

    @Test
    @DisplayName("MythicMobsHook: Server null safety and namespaced key integrity")
    void testMythicMobsHookNullSafety() {
        assertDoesNotThrow(() -> {
            MythicMobsHook.checkHooks();
            assertFalse(MythicMobsHook.isMythicMobsEnabled());
            assertFalse(MythicMobsHook.isMinionSpawnedMob(null));
            assertNull(MythicMobsHook.spawnMob(null, null, false));

            assertEquals("minion_spawned_mob", MythicMobsHook.MINION_MOB_KEY.getKey());
            assertEquals("minion_owner_id", MythicMobsHook.MINION_OWNER_KEY.getKey());
        });
    }

    // =========================================================================
    // 6. PLACEHOLDER EXPANSION & PARSER: Request dispatch & math evaluation
    // =========================================================================

    @Test
    @DisplayName("PlaceholderExpansion: Parameter dispatch and registration metadata")
    void testPlaceholderExpansionDispatch() {
        MinionXPlaceholderExpansion exp = new MinionXPlaceholderExpansion(null);
        assertEquals("minionx", exp.getIdentifier());
        assertEquals("AbhinavVanama", exp.getAuthor());
        assertTrue(exp.persist());
        assertTrue(exp.canRegister());

        // Quarantine checks without server
        assertNotNull(exp.onRequest(null, "quarantine_status"));
        assertEquals("false", exp.onRequest(null, "is_quarantined"));

        // Player with null plugin manager
        assertEquals("0", exp.onRequest(mockPlayer, "placed"));
        assertNull(exp.onRequest(mockPlayer, "invalid_param_xyz"));
    }

    @Test
    @DisplayName("PlaceholderUtil: Cached parsing, math blocks, and null player handling")
    void testPlaceholderUtilCachedParsing() {
        assertDoesNotThrow(() -> {
            PlaceholderUtil.init(null);

            String parsed = PlaceholderUtil.parse("Cost is {amount * 2.5} coins", null, null, Material.EMERALD, 4);
            assertEquals("Cost is 10 coins", parsed);

            String zero = PlaceholderUtil.parse("{amount - 10}", null, null, null, 10);
            assertEquals("0", zero);

            PlaceholderUtil.shutdown();
        });
    }

    // =========================================================================
    // 7. WORLDGUARD & PERIMETER / AREA PROTECTION
    // =========================================================================

    @Test
    @DisplayName("WorldGuard & ProtectionHookManager: Area/perimeter checking and admin bypass")
    void testAreaProtectionAndBypass() {
        assertDoesNotThrow(() -> {
            ProtectionHookManager.checkHooks();
            assertFalse(ProtectionHookManager.isWorldGuardAvailable());

            // Mock world
            World mockWorld = (World) Proxy.newProxyInstance(
                    World.class.getClassLoader(),
                    new Class<?>[]{World.class},
                    (proxy, method, args) -> {
                        if ("getName".equals(method.getName())) return "world";
                        return null;
                    }
            );

            Location loc = new Location(mockWorld, 100, 64, 100);

            // Default placement (checks 5x5 area with radius 2)
            assertTrue(ProtectionHookManager.canPlaceMinion(mockPlayer, loc));
            assertTrue(ProtectionHookManager.canPlaceMinion(mockPlayer, loc, 2));
            assertTrue(ProtectionHookManager.canPlaceMinion(mockPlayer, loc, 4));

            // Null checks
            assertFalse(ProtectionHookManager.canPlaceMinion(null, loc));
            assertFalse(ProtectionHookManager.canPlaceMinion(mockPlayer, null));

            // IslandHookManager delegation
            assertTrue(IslandHookManager.canPlaceMinion(mockPlayer, loc));
            assertTrue(IslandHookManager.canPlaceMinion(mockPlayer, loc, 2));

            // Admin bypass player
            Player adminPlayer = (Player) Proxy.newProxyInstance(
                    Player.class.getClassLoader(),
                    new Class<?>[]{Player.class, OfflinePlayer.class},
                    (proxy, method, args) -> {
                        if ("hasPermission".equals(method.getName())) {
                            String p = (String) args[0];
                            return "minionx.admin".equals(p) || "worldguard.region.bypass.*".equals(p);
                        }
                        if ("getUniqueId".equals(method.getName())) return UUID.randomUUID();
                        return null;
                    }
            );
            assertTrue(ProtectionHookManager.canPlaceMinion(adminPlayer, loc));
            assertTrue(ProtectionHookManager.canPlaceMinion(adminPlayer, loc, 5));
        });
    }

    @Test
    @DisplayName("PriceRegistry: Custom item native ID pricing integration")
    void testPriceRegistryNativeCustomPricing() {
        PriceRegistry.setPrice("CUSTOM_TEST_ITEM", 150.0);
        assertEquals(150.0, PriceRegistry.getPrice("CUSTOM_TEST_ITEM"), 0.001);

        // Multi-segment test: "itemsadder:custom_ores:ruby" -> matches "RUBY" or "CUSTOM_ORES:RUBY"
        PriceRegistry.setPrice("RUBY", 75.0);
        assertEquals(75.0, PriceRegistry.getPrice("RUBY"), 0.001);

        ItemStack diamond = new ItemStack(Material.DIAMOND, 1);
        assertEquals(8.0, PriceRegistry.getPrice(diamond), 0.001);
    }

    @Test
    @DisplayName("ProtectionHookManager: Negative and zero radius sanitization")
    void testRadiusSanitization() {
        World mockWorld = (World) Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[]{World.class},
                (proxy, method, args) -> "world".equals(method.getName()) ? "world" : null
        );
        Location loc = new Location(mockWorld, 0, 64, 0);

        assertDoesNotThrow(() -> {
            assertTrue(ProtectionHookManager.canPlaceMinion(mockPlayer, loc, -5));
            assertTrue(ProtectionHookManager.canPlaceMinion(mockPlayer, loc, 0));
            assertTrue(ProtectionHookManager.canPlaceMinion(mockPlayer, loc, 3));
        });
    }

    @Test
    @DisplayName("ProtectionHookManager & EconomyHook: Comprehensive null argument safety")
    void testOverallNullArgumentSafety() {
        assertDoesNotThrow(() -> {
            assertFalse(ProtectionHookManager.canPlaceMinion(null, null));
            assertFalse(ProtectionHookManager.canPlaceMinion(mockPlayer, null, 2));
            assertFalse(ProtectionHookManager.canPlaceMinion(null, new Location(null, 0, 0, 0), 2));
            assertFalse(ProtectionHookManager.canAccessMinion(null, null));
            assertFalse(ProtectionHookManager.canAccessMinion(mockPlayer, null));
            assertFalse(ProtectionHookManager.canPickupMinion(null, null));
            assertFalse(ProtectionHookManager.canPickupMinion(mockPlayer, null));

            // Economy without provider
            EconomyHook.setVaultEconomy(null);
            assertFalse(EconomyHook.hasEconomy());
            assertEquals(0.0, EconomyHook.getBalance(mockPlayer), 0.001);
            assertTrue(EconomyHook.has(mockPlayer, 0.0));
            assertFalse(EconomyHook.has(mockPlayer, 50.0));
            assertFalse(EconomyHook.withdraw(mockPlayer, 50.0));
            assertTrue(EconomyHook.depositWithResult(mockPlayer, 50.0).isSuccess());
        });
    }
}
