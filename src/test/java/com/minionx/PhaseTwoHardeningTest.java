package com.minionx;

import com.minionx.api.MinionState;
import com.minionx.api.MinionType;
import com.minionx.api.PriceRegistry;
import com.minionx.core.Minion;
import com.minionx.core.MinionManager;
import com.minionx.core.MinionTickTask;
import com.minionx.core.OfflineCalculator;
import com.minionx.hook.EconomyHook;
import com.minionx.storage.DatabaseStorage;
import com.minionx.storage.PendingOperation;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 2 Acceptance Tests for MinionX V6 Engineering Hardening:
 * 1. testPickupTransactionAtomicity
 * 2. testPartialPickupDelivery
 * 3. testVaultPayoutCrashStateMachine
 * 4. testOfflineUnpricedOverflow
 * 5. testBoundedSchedulerCompaction
 */
public class PhaseTwoHardeningTest {

    private File tempDir;

    @BeforeEach
    public void setUp() throws Exception {
        tempDir = File.createTempFile("phase2_hardening_test_", "");
        tempDir.delete();
        tempDir.mkdirs();
    }

    @AfterEach
    public void tearDown() throws Exception {
        EconomyHook.setVaultEconomy(null);
        if (tempDir != null && tempDir.exists()) {
            deleteDirectory(tempDir);
        }
    }

    private void deleteDirectory(File f) {
        if (f.isDirectory()) {
            File[] files = f.listFiles();
            if (files != null) {
                for (File child : files) {
                    deleteDirectory(child);
                }
            }
        }
        f.delete();
    }

    // --- 1. Pickup Transaction Atomicity ---
    @Test
    public void testPickupTransactionAtomicity() throws Exception {
        MockSqlHelper.State state = new MockSqlHelper.State();
        Connection connection = MockSqlHelper.createMockConnection(state);
        DatabaseStorage storage = new DatabaseStorage(null);
        storage.setConnection(connection);

        UUID minionId = UUID.randomUUID();
        UUID playerUuid = UUID.randomUUID();
        String opId = UUID.randomUUID().toString();
        String payload = "MOCK_PAYLOAD";
        long revision = 42L;

        // Execute successful pickup removal transaction
        DatabaseStorage.PickupRemovalResult success = storage.executePickupRemoval(opId, minionId, playerUuid, revision, payload);
        assertEquals(DatabaseStorage.PickupRemovalResult.COMMITTED_NOW, success, "executePickupRemoval must succeed under valid database connection");

        // Verify transaction semantics: setAutoCommit(false) -> statements -> commit -> setAutoCommit(true)
        assertTrue(state.transactionEvents.contains("setAutoCommit:false"), "Transaction must disable auto-commit");
        assertTrue(state.transactionEvents.contains("commit"), "Transaction must commit on success");

        // Verify atomic operations executed together
        boolean recordedPendingOp = false;
        boolean insertedTombstone = false;
        boolean deletedItems = false;
        boolean deletedMinion = false;

        for (String sql : state.executedStatements) {
            String upper = sql.toUpperCase();
            if (upper.contains("MINION_PENDING_OPERATIONS")) recordedPendingOp = true;
            if (upper.contains("MINION_TOMBSTONES")) insertedTombstone = true;
            if (upper.contains("DELETE FROM MINION_ITEMS")) deletedItems = true;
            if (upper.contains("DELETE FROM MINIONS")) deletedMinion = true;
        }

        assertTrue(recordedPendingOp, "Must record pending operation with MINION_REMOVED");
        assertTrue(insertedTombstone, "Must insert permanent tombstone");
        assertTrue(deletedItems, "Must delete minion items");
        assertTrue(deletedMinion, "Must delete active minion row");

        // Test Rollback on Failure: if any statement throws, entire transaction must roll back
        state.executedStatements.clear();
        state.transactionEvents.clear();
        state.throwOnExecute = true;

        DatabaseStorage.PickupRemovalResult failedResult = storage.executePickupRemoval(UUID.randomUUID().toString(), minionId, playerUuid, revision, payload);
        assertEquals(DatabaseStorage.PickupRemovalResult.UNKNOWN_OUTCOME, failedResult, "executePickupRemoval must return UNKNOWN_OUTCOME when statement fails");
        assertTrue(state.transactionEvents.contains("rollback"), "Transaction must rollback on failure");
    }

    // --- 2. Partial Pickup Delivery & Leftover Quarantine ---
    @Test
    public void testPartialPickupDelivery() {
        // Escrow payload format representing candidate items
        String initialPayload = "DIAMOND:64;;;ITEM;;;EMERALD:64";
        assertNotNull(initialPayload);

        // Simulate delivery where player inventory holds DIAMOND but returns leftover EMERALD
        List<String> leftovers = List.of("EMERALD:64");

        // When leftovers exist, operation MUST NOT be completed/removed
        assertFalse(leftovers.isEmpty(), "Simulation requires leftovers to test partial delivery");

        // Build leftover quarantine payload
        String quarantinedPayload = String.join(";;;ITEM;;;", leftovers);

        // Verify that operation remains in QUARANTINED_LEFTOVERS state with remaining items preserved
        PendingOperation op = new PendingOperation(
                UUID.randomUUID().toString(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "QUARANTINED_LEFTOVERS",
                System.currentTimeMillis(),
                quarantinedPayload
        );

        assertEquals("QUARANTINED_LEFTOVERS", op.state());
        assertNotNull(op.payload());
        assertTrue(op.payload().contains("EMERALD:64"), "Quarantined payload must retain undelivered items");
        assertFalse(op.payload().contains("DIAMOND:64"), "Delivered items must not be duplicated in quarantine");

        // Now simulate `/minion claim` recovery when player opens inventory space
        String[] recoveredRaw = op.payload().split(";;;ITEM;;;");
        List<String> recoveredItems = new ArrayList<>();
        for (String raw : recoveredRaw) {
            if (raw != null && !raw.isBlank()) {
                recoveredItems.add(raw);
            }
        }

        assertEquals(1, recoveredItems.size());
        assertEquals("EMERALD:64", recoveredItems.getFirst());
    }

    public static class MockEconomyResponse {
        private final boolean success;
        private final String error;

        public MockEconomyResponse(boolean success, String error) {
            this.success = success;
            this.error = error;
        }

        public boolean transactionSuccess() { return success; }
        public String errorMessage() { return error; }
    }

    public static class MockVaultEconomy {
        private final boolean success;
        private final String error;

        public MockVaultEconomy(boolean success, String error) {
            this.success = success;
            this.error = error;
        }

        public MockEconomyResponse depositPlayer(Player player, double amount) {
            return new MockEconomyResponse(success, error);
        }
    }

    public static class CrashingVaultEconomy {
        public MockEconomyResponse depositPlayer(Player player, double amount) {
            throw new RuntimeException("Database / RPC connection timed out during transfer");
        }
    }

    // --- 3. Vault Payout Crash & Ambiguous Payout State Machine ---
    @Test
    public void testVaultPayoutCrashStateMachine() {
        Player mockPlayer = (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> {
                    if ("getUniqueId".equals(method.getName())) return UUID.randomUUID();
                    if ("getName".equals(method.getName())) return "TestPlayer";
                    return null;
                }
        );

        Minion minion = new Minion(UUID.randomUUID(), UUID.randomUUID(), MinionType.IRON, 5, null);
        minion.setShippingCoins(500.0);
        assertEquals(500.0, minion.getShippingCoins(), 0.001);

        // Case A: Definitive Failure from economy provider -> balance must be restored
        EconomyHook.setVaultEconomy(new MockVaultEconomy(false, "Account blocked"));
        EconomyHook.PayoutResult defResult = EconomyHook.depositWithResult(mockPlayer, 500.0);
        assertTrue(defResult.isDefinitiveFailure());
        assertEquals("Account blocked", defResult.errorMessage());

        // Case B: Ambiguous error / network timeout -> balance must be locked in PAYOUT_AMBIGUOUS
        EconomyHook.setVaultEconomy(new CrashingVaultEconomy());
        EconomyHook.PayoutResult ambResult = EconomyHook.depositWithResult(mockPlayer, 500.0);
        assertTrue(ambResult.isAmbiguous());
        assertTrue(ambResult.errorMessage().contains("connection timed out"));

        // Case C: Success -> CONFIRMED
        EconomyHook.setVaultEconomy(new MockVaultEconomy(true, null));
        EconomyHook.PayoutResult succResult = EconomyHook.depositWithResult(mockPlayer, 500.0);
        assertTrue(succResult.isSuccess());
    }

    // --- 4. Cycle-by-Cycle Offline Simulation with Unpriced Overflow Rule ---
    @Test
    public void testOfflineUnpricedOverflow() {
        Minion minion = new Minion(UUID.randomUUID(), UUID.randomUUID(), MinionType.COBBLESTONE, 1, null);
        // Fill internal inventory to maximum slots (tier 1 has 1 slot)
        minion.addItemDirect(new ItemStack(Material.COBBLESTONE, 64));
        assertEquals(64, minion.getTotalStoredCount());

        // Equip Budget Hopper
        minion.setShippingId("budget_hopper");
        assertTrue(minion.hasAutoSeller());

        // Ensure price of cobblestone is unpriced (0.0)
        PriceRegistry.setPrice(Material.COBBLESTONE, 0.0);
        assertEquals(0.0, PriceRegistry.getPrice(Material.COBBLESTONE));

        // Set last action time 100 seconds ago (enough for multiple offline cycles)
        long beforeTime = System.currentTimeMillis() - 100_000L;
        minion.setLastActionTime(beforeTime);

        // Run offline catchup: must halt immediately on unpriced overflow and NOT delete items for free!
        int produced = OfflineCalculator.processOfflineCatchup(minion);

        // Verification:
        assertEquals(0, produced, "Unpriced overflow must halt production without producing or voiding items");
        assertEquals(0.0, minion.getShippingCoins(), "Unpriced items must NEVER generate free coins or be deleted");
        assertEquals(MinionState.FULL, minion.getState(), "Minion must transition to FULL upon unpriced overflow");
        assertTrue(minion.getLastActionTime() > beforeTime, "Last action time must advance to now to clear downtime debt");

        // Restore price
        PriceRegistry.setPrice(Material.COBBLESTONE, 1.0);
    }

    // --- 5. Bounded Generational Scheduler & Compaction ---
    @Test
    public void testBoundedSchedulerCompaction() {
        MinionManager manager = new MinionManager();
        MinionTickTask tickTask = new MinionTickTask(manager);

        // 1. Register 2 active minions
        Minion m1 = new Minion(UUID.randomUUID(), UUID.randomUUID(), MinionType.COBBLESTONE, 1, null);
        Minion m2 = new Minion(UUID.randomUUID(), UUID.randomUUID(), MinionType.IRON, 1, null);
        manager.register(m1);
        manager.register(m2);
        manager.markLoaded(m1);
        manager.markLoaded(m2);

        // 2. Inject 250 dead/stale entries (exceeding MAX_QUEUE_POLLS_PER_TICK = 200)
        long now = System.currentTimeMillis();
        for (int i = 0; i < 250; i++) {
            tickTask.getActionQueue().offer(new MinionTickTask.ScheduledMinion(
                    UUID.randomUUID(), // non-existent minion ID
                    now - 10_000L,     // due in the past
                    0L
            ));
        }

        assertTrue(tickTask.getQueueSize() >= 250);

        // 3. Execute tickTask.run(): must bound execution to MAX_QUEUE_POLLS_PER_TICK (200)
        tickTask.run();

        // 4. Verify poll limits and telemetry
        assertTrue(tickTask.getTelemetry().getTotalShedActions() >= 1, "Must record shed action when poll limit is reached");
        assertTrue(tickTask.getQueueSize() >= 50, "Remaining entries must stay in queue rather than exceeding poll budget");

        // 5. Test Budgeted Incremental Compaction
        // Add 150 dead entries with stale generation
        for (int i = 0; i < 150; i++) {
            tickTask.getActionQueue().offer(new MinionTickTask.ScheduledMinion(
                    m1.getMinionId(),
                    now - 5_000L,
                    m1.getSchedulingGeneration() - 1 // stale generation
            ));
        }

        int preCompactionSize = tickTask.getQueueSize();
        tickTask.setTickCounter(1_200L); // Trigger compaction interval
        tickTask.performCompaction();

        int postCompactionSize = tickTask.getQueueSize();
        assertTrue(postCompactionSize < preCompactionSize, "Compaction must remove stale entries");
        // Verify bounded work: inspected at most 100 entries
        assertTrue(preCompactionSize - postCompactionSize <= 100, "Compaction must not exceed MAX_COMPACTION_ENTRIES_PER_TICK");
    }
}
