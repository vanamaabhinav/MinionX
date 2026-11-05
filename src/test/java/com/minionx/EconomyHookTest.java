package com.minionx;

import com.minionx.hook.EconomyHook;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class EconomyHookTest {

    private UUID playerUuid;
    private Player mockPlayer;

    @BeforeEach
    public void setUp() {
        playerUuid = UUID.randomUUID();
        mockPlayer = (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> {
                    if ("getUniqueId".equals(method.getName())) return playerUuid;
                    if ("getName".equals(method.getName())) return "TestPlayer";
                    return null;
                }
        );
        EconomyHook.setVaultEconomy(null);
    }

    @AfterEach
    public void tearDown() {
        EconomyHook.setVaultEconomy(null);
    }

    @Test
    @DisplayName("Should gracefully handle economy operations when no provider is installed")
    void testNoEconomyProviderFallback() {
        assertFalse(EconomyHook.hasEconomy());
        assertEquals(0.0, EconomyHook.getBalance(mockPlayer));
        assertFalse(EconomyHook.has(mockPlayer, 100.0));
        assertFalse(EconomyHook.withdraw(mockPlayer, 50.0));

        // Negative or zero checks
        assertTrue(EconomyHook.has(mockPlayer, 0.0));
        assertTrue(EconomyHook.withdraw(mockPlayer, 0.0));

        // Null player safety
        assertEquals(0.0, EconomyHook.getBalance(null));
        assertTrue(EconomyHook.has(null, 0.0));
        assertTrue(EconomyHook.withdraw(null, 0.0));

        // Fallback deposit returns success with informative message
        EconomyHook.PayoutResult result = EconomyHook.depositWithResult(mockPlayer, 25.0);
        assertTrue(result.isSuccess());
        assertEquals("No economy provider", result.errorMessage());
    }

    @Test
    @DisplayName("Should integrate with mock Vault economy provider for balances, deposits, and withdrawals")
    void testMockVaultEconomy() {
        MockVaultEconomy mockEco = new MockVaultEconomy();
        EconomyHook.setVaultEconomy(mockEco);

        assertTrue(EconomyHook.hasEconomy());
        assertEquals(0.0, EconomyHook.getBalance(mockPlayer));

        // Deposit 150.0
        EconomyHook.PayoutResult depResult = EconomyHook.depositWithResult(mockPlayer, 150.0);
        assertTrue(depResult.isSuccess());
        assertEquals(150.0, EconomyHook.getBalance(mockPlayer));
        assertTrue(EconomyHook.has(mockPlayer, 100.0));
        assertFalse(EconomyHook.has(mockPlayer, 200.0));

        // Withdraw 50.0
        boolean withdrew = EconomyHook.withdraw(mockPlayer, 50.0);
        assertTrue(withdrew);
        assertEquals(100.0, EconomyHook.getBalance(mockPlayer));

        // Withdraw exceeding balance should fail
        boolean overWithdraw = EconomyHook.withdraw(mockPlayer, 500.0);
        assertFalse(overWithdraw);
        assertEquals(100.0, EconomyHook.getBalance(mockPlayer));
    }

    @Test
    @DisplayName("Should report definitive failure on rejected transaction")
    void testRejectedDeposit() {
        FailingVaultEconomy failingEco = new FailingVaultEconomy();
        EconomyHook.setVaultEconomy(failingEco);

        EconomyHook.PayoutResult result = EconomyHook.depositWithResult(mockPlayer, 100.0);
        assertTrue(result.isDefinitiveFailure());
        assertFalse(result.isSuccess());
        assertEquals("Account frozen", result.errorMessage());
    }

    // --- Mock Vault Economy Implementations ---
    public static class MockEconomyResponse {
        private final boolean success;
        private final String errorMessage;

        public MockEconomyResponse(boolean success, String errorMessage) {
            this.success = success;
            this.errorMessage = errorMessage;
        }

        public boolean transactionSuccess() {
            return success;
        }

        public String errorMessage() {
            return errorMessage;
        }
    }

    public static class MockVaultEconomy {
        private final Map<UUID, Double> balances = new HashMap<>();

        public double getBalance(OfflinePlayer player) {
            return player != null ? balances.getOrDefault(player.getUniqueId(), 0.0) : 0.0;
        }

        public boolean has(OfflinePlayer player, double amount) {
            return getBalance(player) >= amount;
        }

        public MockEconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
            if (player == null) return new MockEconomyResponse(false, "Null player");
            double cur = getBalance(player);
            if (cur < amount) {
                return new MockEconomyResponse(false, "Insufficient funds");
            }
            balances.put(player.getUniqueId(), cur - amount);
            return new MockEconomyResponse(true, null);
        }

        public MockEconomyResponse depositPlayer(OfflinePlayer player, double amount) {
            if (player == null) return new MockEconomyResponse(false, "Null player");
            balances.put(player.getUniqueId(), getBalance(player) + amount);
            return new MockEconomyResponse(true, null);
        }
    }

    public static class FailingVaultEconomy {
        public double getBalance(OfflinePlayer player) {
            return 0.0;
        }

        public MockEconomyResponse depositPlayer(OfflinePlayer player, double amount) {
            return new MockEconomyResponse(false, "Account frozen");
        }
    }
}

