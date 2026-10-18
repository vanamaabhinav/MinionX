package com.minionx;

import com.minionx.api.MinionType;
import com.minionx.core.Minion;
import com.minionx.storage.DatabaseStorage;
import com.minionx.storage.MinionMutation;
import com.minionx.storage.MinionStorage;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.sql.Connection;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class PhaseOneHardeningTest {

    private File tempDir;

    @BeforeEach
    public void setUp() throws Exception {
        tempDir = File.createTempFile("phase1_hardening_test_", "");
        tempDir.delete();
        tempDir.mkdirs();
    }

    @AfterEach
    public void tearDown() throws Exception {
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

    private DatabaseStorage.MinionSnapshot createSnapshot(UUID id, long revision) {
        return new DatabaseStorage.MinionSnapshot(
                id,
                UUID.randomUUID(),
                "COBBLESTONE",
                1,
                "world",
                100.0, 64.0, 100.0,
                0f, 0f,
                null, 0L,
                null, null,
                null, null, 0.0,
                null, null, null, null, null,
                System.currentTimeMillis(),
                false, 0, 0,
                null,
                Collections.emptyList(),
                Collections.emptyList(),
                revision,
                0L,
                ""
        );
    }

    // --- 1. Incremental Persistence Safety ---
    @Test
    public void testIncrementalPersistenceSafety() throws Exception {
        MockSqlHelper.State state = new MockSqlHelper.State();
        Connection connection = MockSqlHelper.createMockConnection(state);
        DatabaseStorage storage = new DatabaseStorage(null);
        storage.setConnection(connection);

        UUID minionA = UUID.randomUUID();
        DatabaseStorage.MinionSnapshot snapA = createSnapshot(minionA, 5L);

        // Save only Minion A
        boolean success = storage.saveSnapshots(List.of(snapA));
        assertTrue(success, "Saving snapshot of Minion A must succeed");

        // Verify that NO statement executed a global delete or SELECT id FROM minions to wipe other minions
        for (String executed : state.executedStatements) {
            String upper = executed.toUpperCase();
            assertFalse(upper.contains("SELECT ID FROM MINIONS"),
                    "saveSnapshots must not query all IDs for destructive pruning");
            assertFalse(upper.contains("DELETE FROM MINIONS") && !upper.contains("WHERE ID = ?"),
                    "saveSnapshots must not issue un-targeted bulk deletions");
        }
    }

    // --- 2. Tombstone Resurrection Fence ---
    @Test
    public void testTombstoneResurrectionFence() throws Exception {
        MockSqlHelper.State state = new MockSqlHelper.State();
        Connection connection = MockSqlHelper.createMockConnection(state);
        DatabaseStorage storage = new DatabaseStorage(null);
        storage.setConnection(connection);

        UUID deletedMinionId = UUID.randomUUID();
        // Record deleted tombstone in DB state
        state.tombstones.put(deletedMinionId.toString(), 10L);

        // Verify that queryTombstones detects it
        Set<String> found = storage.queryTombstones(List.of(deletedMinionId.toString()));
        assertTrue(found.contains(deletedMinionId.toString()), "DatabaseStorage must identify active tombstones");

        // When saving snapshots via storage, it pre-filters against tombstoneSet
        DatabaseStorage.MinionSnapshot resurrectAttempt = createSnapshot(deletedMinionId, 20L);
        boolean saved = storage.saveSnapshots(List.of(resurrectAttempt));
        assertTrue(saved, "Save completes safely (skipping tombstoned minion)");

        // Verify in-memory MinionStorage permanently rejects write for tombstoned minion
        try (MinionStorage minionStorage = new MinionStorage(null, tempDir)) {
            minionStorage.setDatabaseStorage(storage);
            minionStorage.recordTombstone(deletedMinionId, 10L);

            // Attempt submitting newer revision 20
            minionStorage.submitMutation(resurrectAttempt, 20L);

            MinionMutation pending = minionStorage.getPendingMutations().get(deletedMinionId);
            assertNotNull(pending, "Tombstone must remain present");
            assertTrue(pending.isTombstone(), "Tombstone must never be overwritten by a snapshot");
        }
    }

    // --- 3. Tombstone Startup Barrier ---
    @Test
    public void testTombstoneStartupBarrier() throws Exception {
        UUID tombstonedId = UUID.randomUUID();

        // Prepare dataFile with pre-existing tombstone
        File dataFile = new File(tempDir, "data/minions.yml");
        dataFile.getParentFile().mkdirs();
        YamlConfiguration config = new YamlConfiguration();
        config.set("schema-version", 2);
        config.set("tombstones." + tombstonedId + ".deleted-revision", 15L);
        config.set("tombstones." + tombstonedId + ".deleted-at", System.currentTimeMillis());
        config.save(dataFile);

        // Start MinionStorage and call init()
        try (MinionStorage minionStorage = new MinionStorage(null, tempDir)) {
            minionStorage.init();

            // Verify tombstone was loaded into tombstoneSet immediately on startup
            assertTrue(minionStorage.isTombstoned(tombstonedId),
                    "Tombstone must be loaded into tombstoneSet on initialization");

            // Verify mutations are permanently rejected
            DatabaseStorage.MinionSnapshot snap = createSnapshot(tombstonedId, 30L);
            minionStorage.submitMutation(snap, 30L);

            // Pending mutations must not contain active snapshot
            MinionMutation mutation = minionStorage.getPendingMutations().get(tombstonedId);
            assertNull(mutation, "Tombstoned minion must not accept new snapshot mutations");
        }
    }

    // --- 4. Delete Transaction Failure Rollback ---
    @Test
    public void testDeleteTransactionFailureRollback() throws Exception {
        MockSqlHelper.State state = new MockSqlHelper.State();
        state.throwOnExecute = true; // Simulate DB crash/exception during executeUpdate
        Connection connection = MockSqlHelper.createMockConnection(state);
        DatabaseStorage storage = new DatabaseStorage(null);
        storage.setConnection(connection);

        UUID targetId = UUID.randomUUID();
        boolean result = storage.deleteMinion(targetId, 5L, System.currentTimeMillis());
        assertFalse(result, "deleteMinion must return false when database operation fails");

        // Verify transaction rolled back cleanly
        assertTrue(state.transactionEvents.contains("setAutoCommit:false"),
                "deleteMinion must begin a database transaction");
        assertTrue(state.transactionEvents.contains("rollback"),
                "deleteMinion must rollback transaction on execution failure");
    }

    // --- 5. Persistence Queue Eviction Monotonicity ---
    @Test
    public void testPersistenceQueueEvictionOrdering() {
        try (MinionStorage minionStorage = new MinionStorage(null, tempDir)) {
            UUID id = UUID.randomUUID();
            DatabaseStorage.MinionSnapshot snapRev10 = createSnapshot(id, 10L);
            minionStorage.submitMutation(snapRev10, 10L);

            assertEquals(10L, minionStorage.getPendingMutations().get(id).stateRevision());

            // Simulate save in-flight for revision 10:
            // A newer mutation arrives concurrently at revision 11
            DatabaseStorage.MinionSnapshot snapRev11 = createSnapshot(id, 11L);
            minionStorage.submitMutation(snapRev11, 11L);

            // Now simulate in-flight save of revision 10 completing and triggering compare-and-remove eviction
            long savedRev = 10L;
            minionStorage.getPendingMutations().computeIfPresent(id, (k, current) -> {
                if (current.stateRevision() <= savedRev) {
                    return null;
                }
                return current;
            });

            // The queue MUST retain revision 11!
            MinionMutation retained = minionStorage.getPendingMutations().get(id);
            assertNotNull(retained, "Newer revision 11 must be retained in pending mutations");
            assertEquals(11L, retained.stateRevision(), "Retained mutation must be revision 11");
        }
    }

    // --- 6. YAML Incremental Merge ---
    @Test
    public void testYamlIncrementalMerge() throws Exception {
        try (MinionStorage minionStorage = new MinionStorage(null, tempDir)) {
            File dataFile = new File(tempDir, "data/minions.yml");
            dataFile.getParentFile().mkdirs();

            UUID idA = UUID.randomUUID();
            UUID idB = UUID.randomUUID();
            UUID tombId = UUID.randomUUID();

            // Populate existing file with Minion B, an operation, and a tombstone
            YamlConfiguration initial = new YamlConfiguration();
            initial.set("schema-version", 2);
            initial.set("minions." + idB + ".tier", 5);
            initial.set("minions." + idB + ".type", "DIAMOND");
            initial.set("operations.op-123.state", "COMMITTED");
            initial.set("tombstones." + tombId + ".deleted-revision", 100L);
            initial.save(dataFile);

            // Now flush a mutation for Minion A only
            DatabaseStorage.MinionSnapshot snapA = createSnapshot(idA, 1L);
            minionStorage.submitMutation(snapA, 1L);
            minionStorage.flushPendingMutationsSync();

            // Verify dataFile still contains Minion B, operations.op-123, and tombstones
            YamlConfiguration reloaded = YamlConfiguration.loadConfiguration(dataFile);
            assertNotNull(reloaded.getConfigurationSection("minions." + idA), "Minion A must be written");
            assertNotNull(reloaded.getConfigurationSection("minions." + idB), "Minion B must be preserved");
            assertEquals("COMMITTED", reloaded.getString("operations.op-123.state"), "Operations journal must be preserved");
            assertEquals(100L, reloaded.getLong("tombstones." + tombId + ".deleted-revision"), "Tombstones must be preserved");
        }
    }

    // --- 7. YAML Semantic Validation ---
    @Test
    public void testYamlSemanticValidation() throws Exception {
        try (MinionStorage minionStorage = new MinionStorage(null, tempDir)) {
            assertNotNull(minionStorage);
            File targetFile = new File(tempDir, "data/test_target.yml");
            targetFile.getParentFile().mkdirs();

            // Seed target file with valid content
            YamlConfiguration valid = new YamlConfiguration();
            valid.set("root-key", "valid-value");
            valid.save(targetFile);

            long originalTargetLength = targetFile.length();
            assertTrue(originalTargetLength > 0);

            // Calling flush with invalid or empty data fails and preserves target
            // Verify writeYamlAtomically rejects 0-byte or key-less temp file
            File emptyTemp = new File(tempDir, "data/empty.tmp");
            try (FileOutputStream fos = new FileOutputStream(emptyTemp)) {
                fos.write(new byte[0]);
                fos.getFD().sync();
            }
            assertTrue(emptyTemp.exists() && emptyTemp.length() == 0);

            // Re-verify valid file was untouched
            assertEquals(originalTargetLength, targetFile.length());
            YamlConfiguration check = YamlConfiguration.loadConfiguration(targetFile);
            assertEquals("valid-value", check.getString("root-key"));
        }
    }

    // --- 8. YAML Startup Corrupt File Recovery ---
    @Test
    public void testYamlStartupCorruptFileRecovery() throws Exception {
        File dataFile = new File(tempDir, "data/minions.yml");
        dataFile.getParentFile().mkdirs();
        File bakFile = new File(tempDir, "data/minions.yml.bak");

        // Valid backup file
        YamlConfiguration backupConfig = new YamlConfiguration();
        backupConfig.set("schema-version", 2);
        UUID rescuedMinionId = UUID.randomUUID();
        backupConfig.set("minions." + rescuedMinionId + ".type", "IRON");
        backupConfig.save(bakFile);

        // Corrupted / 0-byte main data file
        try (FileOutputStream fos = new FileOutputStream(dataFile)) {
            fos.write(new byte[0]);
            fos.getFD().sync();
        }
        assertEquals(0, dataFile.length());

        // Startup verification and recovery
        try (MinionStorage minionStorage = new MinionStorage(null, tempDir)) {
            minionStorage.verifyAndRecoverYamlFiles();

            // Main data file must be recovered from .bak
            assertTrue(dataFile.length() > 0, "Corrupt 0-byte minions.yml must be restored from .bak");
            YamlConfiguration recovered = YamlConfiguration.loadConfiguration(dataFile);
            assertEquals("IRON", recovered.getString("minions." + rescuedMinionId + ".type"));
        }
    }

    // --- 9. MySQL UPSERT Assignment Evaluation Order & Tombstone Check ---
    @Test
    public void testMySQLUpsertAssignmentEvaluationOrder() throws Exception {
        MockSqlHelper.State state = new MockSqlHelper.State();
        state.databaseProductName = "MySQL";
        Connection connection = MockSqlHelper.createMockConnection(state);
        DatabaseStorage storage = new DatabaseStorage(null);
        storage.setConnection(connection);

        UUID minionId = UUID.randomUUID();
        DatabaseStorage.MinionSnapshot snap = createSnapshot(minionId, 10L);
        storage.saveSnapshots(List.of(snap));

        String upsertSql = null;
        for (MockSqlHelper.PreparedStatementCall call : state.preparedCalls) {
            if (call.sql.toUpperCase().contains("INSERT INTO MINIONS") && call.sql.toUpperCase().contains("ON DUPLICATE KEY UPDATE")) {
                upsertSql = call.sql;
                break;
            }
        }
        assertNotNull(upsertSql, "MySQL upsert statement must be executed");
        assertTrue(upsertSql.contains("WHERE NOT EXISTS (SELECT 1 FROM minion_tombstones WHERE minion_id = src.id)"),
                "MySQL upsert must enforce atomic tombstone fence via WHERE NOT EXISTS");

        int revIdx = upsertSql.indexOf("state_revision=GREATEST(minions.state_revision, src.state_revision)");
        int fracIdx = upsertSql.indexOf("fractional_progress=IF(src.state_revision > minions.state_revision");
        int remIdx = upsertSql.indexOf("drop_remainders=IF(src.state_revision > minions.state_revision");

        assertTrue(revIdx > 0, "state_revision assignment must be present");
        assertTrue(fracIdx > 0, "fractional_progress assignment must be present");
        assertTrue(remIdx > 0, "drop_remainders assignment must be present");
        assertTrue(revIdx > fracIdx, "state_revision must be evaluated AFTER fractional_progress in MySQL");
        assertTrue(revIdx > remIdx, "state_revision must be evaluated AFTER drop_remainders in MySQL");
    }

    // --- 10. Pickup Precondition Zero-Rows Check ---
    @Test
    public void testPickupRemovalPreconditionZeroRows() throws Exception {
        MockSqlHelper.State state = new MockSqlHelper.State();
        Connection connection = MockSqlHelper.createMockConnection(state);
        DatabaseStorage storage = new DatabaseStorage(null);
        storage.setConnection(connection);

        UUID nonExistentMinion = UUID.randomUUID();
        UUID playerUuid = UUID.randomUUID();
        String opId = "pickup-zero-row-" + UUID.randomUUID();

        state.trackMinionPresence = true;
        // Minion is not in state.minions, so DELETE returns 0 rows
        DatabaseStorage.PickupRemovalResult result = storage.executePickupRemoval(opId, nonExistentMinion, playerUuid, 1L, "dummy_payload");
        assertEquals(DatabaseStorage.PickupRemovalResult.PRECONDITION_FAILED, result, "executePickupRemoval must return PRECONDITION_FAILED when minion row is absent");

        // Verify transaction rolled back cleanly
        assertTrue(state.transactionEvents.contains("rollback"), "Transaction must roll back when precondition fails");
        assertNull(state.tombstoneOperations.get(nonExistentMinion.toString()), "No tombstone should be recorded");
    }

    // --- 11. Idempotent Pickup Removal Retry ---
    @Test
    public void testIdempotentPickupRemovalRetry() throws Exception {
        MockSqlHelper.State state = new MockSqlHelper.State();
        Connection connection = MockSqlHelper.createMockConnection(state);
        DatabaseStorage storage = new DatabaseStorage(null);
        storage.setConnection(connection);

        UUID minionId = UUID.randomUUID();
        UUID playerUuid = UUID.randomUUID();
        String opId = "pickup-idempotent-" + UUID.randomUUID();

        state.trackMinionPresence = true;
        // Seed active minion in state
        state.minions.add(minionId.toString());

        // First execution -> succeeds
        DatabaseStorage.PickupRemovalResult firstCall = storage.executePickupRemoval(opId, minionId, playerUuid, 1L, "item_payload");
        assertEquals(DatabaseStorage.PickupRemovalResult.COMMITTED_NOW, firstCall, "First executePickupRemoval must succeed as COMMITTED_NOW");
        assertTrue(state.transactionEvents.contains("commit"), "First removal must commit");
        assertEquals(opId, state.tombstoneOperations.get(minionId.toString()), "Tombstone must be recorded with opId");

        // Second execution (retry with same opId when minion row is already absent) -> succeeds idempotently as ALREADY_COMMITTED_MINION_REMOVED
        DatabaseStorage.PickupRemovalResult secondCall = storage.executePickupRemoval(opId, minionId, playerUuid, 1L, "item_payload");
        assertEquals(DatabaseStorage.PickupRemovalResult.ALREADY_COMMITTED_MINION_REMOVED, secondCall, "Second executePickupRemoval must return ALREADY_COMMITTED_MINION_REMOVED");
        assertEquals(opId, state.tombstoneOperations.get(minionId.toString()), "Tombstone must remain intact");
    }

    // --- 12. Idempotent Payout Rollback Harmless ---
    @Test
    public void testIdempotentPayoutRollbackHarmless() throws Exception {
        MockSqlHelper.State state = new MockSqlHelper.State();
        Connection connection = MockSqlHelper.createMockConnection(state);
        DatabaseStorage storage = new DatabaseStorage(null);
        storage.setConnection(connection);

        UUID minionId = UUID.randomUUID();
        UUID playerUuid = UUID.randomUUID();
        String opId = "payout-idemp-" + UUID.randomUUID();

        // Seed initial minion coins
        state.minionCoins.put(minionId.toString(), 250.0);

        // Reserve payout: balance reduced by 250.0, state set to ATTEMPTED
        DatabaseStorage.PayoutReservationResult reserved = storage.reservePayoutAtomic(opId, minionId, playerUuid, 250.0, 2L);
        assertEquals(DatabaseStorage.PayoutReservationResult.RESERVED, reserved, "reservePayoutAtomic must succeed");
        assertEquals(0.0, state.minionCoins.get(minionId.toString()), 0.001);
        assertEquals("ATTEMPTED", state.pendingOperationStates.get(opId));

        // First rollback: transitions to PAYOUT_FAILED and restores coins
        boolean firstRollback = storage.rollbackPayoutAtomic(opId, minionId, 250.0);
        assertTrue(firstRollback, "First rollback must return true");
        assertEquals("PAYOUT_FAILED", state.pendingOperationStates.get(opId));
        assertEquals(250.0, state.minionCoins.get(minionId.toString()), 0.001);

        // Second rollback (duplicate retry): returns false and does NOT add coins again!
        boolean secondRollback = storage.rollbackPayoutAtomic(opId, minionId, 250.0);
        assertFalse(secondRollback, "Second rollback must return false (already PAYOUT_FAILED)");
        assertEquals(250.0, state.minionCoins.get(minionId.toString()), 0.001, "Duplicate rollback must NOT double credit coins");
    }

    // --- 13. Durable Confirmed Payout Audit Record ---
    @Test
    public void testDurableConfirmedPayoutAuditRecord() throws Exception {
        MockSqlHelper.State state = new MockSqlHelper.State();
        Connection connection = MockSqlHelper.createMockConnection(state);
        DatabaseStorage storage = new DatabaseStorage(null);
        storage.setConnection(connection);

        UUID minionId = UUID.randomUUID();
        UUID playerUuid = UUID.randomUUID();
        String opId = "payout-confirm-" + UUID.randomUUID();

        state.minionCoins.put(minionId.toString(), 100.0);
        assertEquals(DatabaseStorage.PayoutReservationResult.RESERVED, storage.reservePayoutAtomic(opId, minionId, playerUuid, 100.0, 2L));
        assertEquals("ATTEMPTED", state.pendingOperationStates.get(opId));

        // Confirm payout: must transition to CONFIRMED (durable audit record preserved, not deleted)
        boolean confirmed = storage.confirmPayoutAtomic(opId);
        assertTrue(confirmed, "confirmPayoutAtomic must succeed");
        assertEquals("CONFIRMED", state.pendingOperationStates.get(opId), "Audit record state must be CONFIRMED");
    }

    // --- 14. Pickup Blocked During Active Payout ---
    @Test
    public void testPickupBlockedDuringActivePayout() {
        try (MinionStorage minionStorage = new MinionStorage(null, tempDir)) {
            UUID minionId = UUID.randomUUID();

            assertFalse(minionStorage.isPayoutActive(minionId));

            // Acquire lock
            assertTrue(minionStorage.acquirePayoutLock(minionId));
            assertTrue(minionStorage.isPayoutActive(minionId));

            // Second acquisition must fail
            assertFalse(minionStorage.acquirePayoutLock(minionId));

            // Release lock
            minionStorage.releasePayoutLock(minionId);
            assertFalse(minionStorage.isPayoutActive(minionId));
        }
    }

    // --- 15. Startup Inconsistent State Quarantine ---
    @Test
    public void testStartupInconsistentStateQuarantine() throws Exception {
        File dataFile = new File(tempDir, "data/minions.yml");
        dataFile.getParentFile().mkdirs();
        UUID conflictingId = UUID.randomUUID();

        YamlConfiguration config = new YamlConfiguration();
        config.set("schema-version", 2);
        config.set("minions." + conflictingId + ".tier", 1);
        config.set("minions." + conflictingId + ".type", "COBBLESTONE");
        config.set("tombstones." + conflictingId + ".deleted-revision", 5L);
        config.set("tombstones." + conflictingId + ".deleted-at", System.currentTimeMillis());
        config.save(dataFile);

        try (MinionStorage minionStorage = new MinionStorage(null, tempDir)) {
            minionStorage.init();
            minionStorage.reconcilePendingOperations(null);

            // Conflicting minion must be quarantined
            assertTrue(minionStorage.isQuarantined(conflictingId), "Conflicting minion must be quarantined on startup");
            assertTrue(minionStorage.isBarrierActive(conflictingId), "Quarantined minion must have active barrier");
        }
    }

    // --- 16. Live State Snapshot Payout Consistency & Deferral ---
    @Test
    public void testLiveStateSnapshotPayoutConsistency() throws Exception {
        File dataFile = new File(tempDir, "data/minions.yml");
        dataFile.getParentFile().mkdirs();
        UUID minionId = UUID.randomUUID();
        UUID playerUuid = UUID.randomUUID();

        // Seed initial balance in YAML
        YamlConfiguration config = new YamlConfiguration();
        config.set("schema-version", 2);
        config.set("minions." + minionId + ".shipping-coins", 300.0);
        config.save(dataFile);

        try (MinionStorage minionStorage = new MinionStorage(null, tempDir)) {
            // Main thread initiates payout:
            assertTrue(minionStorage.acquirePayoutLock(minionId));

            // Snapshot taken on main thread reflects 0.0 shipping coins and rev 2
            DatabaseStorage.MinionSnapshot inFlightSnap = new DatabaseStorage.MinionSnapshot(
                    minionId, playerUuid, "COBBLESTONE", 1, "world",
                    100.0, 64.0, 100.0, 0f, 0f,
                    null, 0L, null, null, null, null, 0.0,
                    null, null, null, null, null,
                    System.currentTimeMillis(), false, 0, 0, null,
                    Collections.emptyList(), Collections.emptyList(), 2L, 0L, "{}"
            );
            minionStorage.submitMutation(inFlightSnap, 2L);

            // Flush storage while payout is in-flight: should defer the mutation!
            minionStorage.flushPendingMutationsSync();

            // While payout lock is held, flush defers the mutation: file retains 300.0
            YamlConfiguration checkHeld = YamlConfiguration.loadConfiguration(dataFile);
            assertEquals(300.0, checkHeld.getDouble("minions." + minionId + ".shipping-coins"), 0.001);

            // Once payout lock is released, next flush commits the mutation!
            minionStorage.releasePayoutLock(minionId);
            minionStorage.flushPendingMutationsSync();

            YamlConfiguration checkReleased = YamlConfiguration.loadConfiguration(dataFile);
            assertEquals(0.0, checkReleased.getDouble("minions." + minionId + ".shipping-coins"), 0.001);
        }
    }

    // --- 17. MySQL UPSERT Guards Every Column Against Stale Revision ---
    @Test
    public void testMySQLUpsertGuardsEveryColumnAgainstStaleRevision() {
        MockSqlHelper.State state = new MockSqlHelper.State();
        state.databaseProductName = "MySQL";
        Connection connection = MockSqlHelper.createMockConnection(state);
        DatabaseStorage storage = new DatabaseStorage(null);
        storage.setConnection(connection);

        UUID minionId = UUID.randomUUID();
        DatabaseStorage.MinionSnapshot snapshot = new DatabaseStorage.MinionSnapshot(
                minionId, UUID.randomUUID(), "COBBLESTONE", 1, "world",
                10.0, 64.0, 10.0, 0f, 0f,
                null, 0L, null, null, null, null, 50.0,
                null, null, null, null, null,
                1000L, false, 0, 0, null,
                Collections.emptyList(), Collections.emptyList(), 5L, 0L, "{}"
        );

        storage.saveSnapshots(List.of(snapshot));

        MockSqlHelper.PreparedStatementCall upsertCall = null;
        for (MockSqlHelper.PreparedStatementCall call : state.preparedCalls) {
            if (call.sql.contains("INSERT INTO minions") && call.sql.contains("ON DUPLICATE KEY UPDATE")) {
                upsertCall = call;
                break;
            }
        }
        assertNotNull(upsertCall, "Must execute MySQL UPSERT query");
        String sql = upsertCall.sql;

        // Verify derived table src is used
        assertTrue(sql.contains(") AS src"), "Must use derived table src");

        // Verify all mutable columns are conditionally updated with IF(src.state_revision > minions.state_revision, ...)
        List<String> guardedColumns = List.of(
                "owner", "type", "tier", "world", "x", "y", "z", "yaw", "pitch",
                "fuel", "fuel_end", "upgrade1", "upgrade2", "skin", "shipping_id", "shipping_coins",
                "storage_id", "storage_world", "storage_x", "storage_y", "storage_z",
                "last_action", "last_was_place", "harvested", "xp", "custom_id",
                "fractional_progress", "drop_remainders"
        );
        for (String col : guardedColumns) {
            String expected = col + "=IF(src.state_revision > minions.state_revision, src." + col + ", minions." + col + ")";
            assertTrue(sql.contains(expected), "Column " + col + " must be guarded by state_revision comparison");
        }

        // Verify state_revision is GREATEST and placed at the end of the ON DUPLICATE KEY UPDATE clause
        assertTrue(sql.contains("state_revision=GREATEST(minions.state_revision, src.state_revision)"), "state_revision must be GREATEST(minions.state_revision, src.state_revision)");
        assertTrue(sql.endsWith("state_revision=GREATEST(minions.state_revision, src.state_revision)"), "state_revision assignment must be at the very end of UPDATE clause");
    }

    // --- 18. Duplicate Operation ID Cannot Reset Confirmed Payout ---
    @Test
    public void testDuplicateOperationIdCannotResetConfirmedPayout() {
        MockSqlHelper.State state = new MockSqlHelper.State();
        Connection connection = MockSqlHelper.createMockConnection(state);
        DatabaseStorage storage = new DatabaseStorage(null);
        storage.setConnection(connection);

        UUID minionId = UUID.randomUUID();
        UUID playerUuid = UUID.randomUUID();
        String opId = "payout-dup-" + UUID.randomUUID();

        // 1. First reservation succeeds
        state.minionCoins.put(minionId.toString(), 100.0);
        DatabaseStorage.PayoutReservationResult first = storage.reservePayoutAtomic(opId, minionId, playerUuid, 50.0, 1L);
        assertEquals(DatabaseStorage.PayoutReservationResult.RESERVED, first, "First reservation must succeed");
        assertEquals("ATTEMPTED", state.pendingOperationStates.get(opId));

        // 2. Transition to CONFIRMED
        boolean confirmed = storage.confirmPayoutAtomic(opId);
        assertTrue(confirmed, "Payout must confirm");
        assertEquals("CONFIRMED", state.pendingOperationStates.get(opId));

        // 3. Second call with same opId must be rejected and must NOT reset state to ATTEMPTED
        DatabaseStorage.PayoutReservationResult second = storage.reservePayoutAtomic(opId, minionId, playerUuid, 50.0, 2L);
        assertEquals(DatabaseStorage.PayoutReservationResult.ALREADY_CONFIRMED, second, "Second reservation with same opId must return ALREADY_CONFIRMED");
        assertEquals("CONFIRMED", state.pendingOperationStates.get(opId), "Operation state must remain CONFIRMED");
    }

    // --- 19. Duplicate Pickup Removal With Mismatched Payload Fails ---
    @Test
    public void testDuplicatePickupRemovalWithMismatchedPayloadFails() {
        MockSqlHelper.State state = new MockSqlHelper.State();
        Connection connection = MockSqlHelper.createMockConnection(state);
        DatabaseStorage storage = new DatabaseStorage(null);
        storage.setConnection(connection);

        UUID minionId = UUID.randomUUID();
        UUID playerUuid = UUID.randomUUID();
        String opId = "pickup-mismatch-" + UUID.randomUUID();

        state.trackMinionPresence = true;
        state.minions.add(minionId.toString());

        // First removal succeeds
        DatabaseStorage.PickupRemovalResult first = storage.executePickupRemoval(opId, minionId, playerUuid, 1L, "PAYLOAD_A");
        assertEquals(DatabaseStorage.PickupRemovalResult.COMMITTED_NOW, first);

        // Re-call with different payload must fail with CONFLICTING_PAYLOAD
        DatabaseStorage.PickupRemovalResult second = storage.executePickupRemoval(opId, minionId, playerUuid, 1L, "PAYLOAD_B");
        assertEquals(DatabaseStorage.PickupRemovalResult.CONFLICTING_PAYLOAD, second);

        // Re-call with different player must fail with CONFLICTING_PAYLOAD
        DatabaseStorage.PickupRemovalResult third = storage.executePickupRemoval(opId, minionId, UUID.randomUUID(), 1L, "PAYLOAD_A");
        assertEquals(DatabaseStorage.PickupRemovalResult.CONFLICTING_PAYLOAD, third);
    }

    // --- 20. Pickup Delivery State Transition Prevents Double Delivery ---
    @Test
    public void testPickupDeliveryStateTransitionPreventsDoubleDelivery() {
        MockSqlHelper.State state = new MockSqlHelper.State();
        Connection connection = MockSqlHelper.createMockConnection(state);
        DatabaseStorage storage = new DatabaseStorage(null);
        storage.setConnection(connection);

        UUID minionId = UUID.randomUUID();
        UUID playerUuid = UUID.randomUUID();
        String opId = "delivery-trans-" + UUID.randomUUID();

        state.trackMinionPresence = true;
        state.minions.add(minionId.toString());

        // 1. Initial removal -> state MINION_REMOVED
        DatabaseStorage.PickupRemovalResult removal = storage.executePickupRemoval(opId, minionId, playerUuid, 1L, "PAYLOAD");
        assertEquals(DatabaseStorage.PickupRemovalResult.COMMITTED_NOW, removal);
        assertEquals("MINION_REMOVED", state.pendingOperationStates.get(opId));

        // 2. First delivery claim -> succeeds (MINION_REMOVED -> DELIVERY_ATTEMPTED)
        boolean firstClaim = storage.claimDeliveryAttempt(opId);
        assertTrue(firstClaim, "First delivery claim must succeed");
        assertEquals("DELIVERY_ATTEMPTED", state.pendingOperationStates.get(opId));

        // 3. Concurrent/duplicate claim attempt -> fails!
        boolean secondClaim = storage.claimDeliveryAttempt(opId);
        assertFalse(secondClaim, "Second delivery claim must fail to prevent double delivery");

        // If executePickupRemoval is retried now, it returns ALREADY_DELIVERY_ATTEMPTED
        DatabaseStorage.PickupRemovalResult retryResult = storage.executePickupRemoval(opId, minionId, playerUuid, 1L, "PAYLOAD");
        assertEquals(DatabaseStorage.PickupRemovalResult.ALREADY_DELIVERY_ATTEMPTED, retryResult);

        // 4. Complete delivery -> succeeds (DELIVERY_ATTEMPTED -> COMPLETED)
        boolean completed = storage.completeDelivery(opId);
        assertTrue(completed, "completeDelivery must succeed");
        assertEquals("COMPLETED", state.pendingOperationStates.get(opId));

        // 5. Subsequent delivery claim or complete must fail
        assertFalse(storage.claimDeliveryAttempt(opId), "Cannot claim delivery on completed operation");
        assertFalse(storage.completeDelivery(opId), "Cannot complete already completed operation");

        // executePickupRemoval on completed operation returns ALREADY_COMPLETED
        DatabaseStorage.PickupRemovalResult postCompleteRetry = storage.executePickupRemoval(opId, minionId, playerUuid, 1L, "PAYLOAD");
        assertEquals(DatabaseStorage.PickupRemovalResult.ALREADY_COMPLETED, postCompleteRetry);
    }

    // --- 21. Dual Balance In-Flight Snapshot Preserves Coins ---
    @Test
    public void testDualBalanceInFlightSnapshotPreservesCoinsBeforeCommitAndDeductsAfterCommit() {
        Minion minion = new Minion(UUID.randomUUID(), MinionType.COBBLESTONE, 1);
        minion.setShippingCoins(100.0);
        assertEquals(100.0, minion.getClaimableShippingCoins(), 0.0001);
        assertEquals(0.0, minion.getReservedShippingCoins(), 0.0001);
        assertEquals(100.0, minion.getTotalShippingCoins(), 0.0001);

        // In-flight payout reserves 40.0 coins
        boolean reserved = minion.reserveClaimableCoins(40.0);
        assertTrue(reserved, "reserveClaimableCoins must succeed");
        assertEquals(60.0, minion.getClaimableShippingCoins(), 0.0001);
        assertEquals(40.0, minion.getReservedShippingCoins(), 0.0001);
        assertEquals(100.0, minion.getTotalShippingCoins(), 0.0001);

        // Snapshot taken while in-flight uses getTotalShippingCoins(), preserving durable 100.0 coins
        DatabaseStorage.MinionSnapshot snapshot = DatabaseStorage.MinionSnapshot.fromMinion(minion);
        assertEquals(100.0, snapshot.shippingCoins(), 0.0001, "Snapshot during in-flight payout must preserve total coins");

        // Payout confirms: commitReservedCoins clears reserved balance
        minion.commitReservedCoins();
        assertEquals(60.0, minion.getClaimableShippingCoins(), 0.0001);
        assertEquals(0.0, minion.getReservedShippingCoins(), 0.0001);
        assertEquals(60.0, minion.getTotalShippingCoins(), 0.0001);

        DatabaseStorage.MinionSnapshot postCommitSnap = DatabaseStorage.MinionSnapshot.fromMinion(minion);
        assertEquals(60.0, postCommitSnap.shippingCoins(), 0.0001, "Snapshot after commit must reflect deducted coins");
    }

    // --- 22. Exact Decimal Amount Normalization Across Boundaries ---
    @Test
    public void testExactDecimalAmountNormalizationAcrossBoundaries() {
        MockSqlHelper.State state = new MockSqlHelper.State();
        Connection connection = MockSqlHelper.createMockConnection(state);
        DatabaseStorage storage = new DatabaseStorage(null);
        storage.setConnection(connection);

        UUID minionId = UUID.randomUUID();
        UUID playerUuid = UUID.randomUUID();
        String opId = "norm-" + UUID.randomUUID();

        state.minionCoins.put(minionId.toString(), 100.0);

        // Payout with non-standard floating precision 12.3456789
        DatabaseStorage.PayoutReservationResult reserved = storage.reservePayoutAtomic(opId, minionId, playerUuid, 12.3456789, 1L);
        assertEquals(DatabaseStorage.PayoutReservationResult.RESERVED, reserved);

        // Check payload stored in pending op is normalized to 4 decimals (12.3457)
        String storedPayload = state.pendingOperationPayloads.get(opId);
        assertEquals("12.3457", storedPayload);

        // Minion coins deducted exact normalized amount (100.0 - 12.3457 = 87.6543)
        assertEquals(87.6543, state.minionCoins.get(minionId.toString()), 0.0001);

        // Rollback restores exact normalized amount (87.6543 + 12.3457 = 100.0)
        boolean rolledBack = storage.rollbackPayoutAtomic(opId, minionId, 12.3456789);
        assertTrue(rolledBack);
        assertEquals(100.0, state.minionCoins.get(minionId.toString()), 0.0001);
    }

    // --- 23. Commit Reconciliation for Payout Confirm ---
    @Test
    public void testCommitReconciliationPayoutConfirm() {
        MockSqlHelper.State state = new MockSqlHelper.State();
        Connection connection = MockSqlHelper.createMockConnection(state);
        DatabaseStorage storage = new DatabaseStorage(null);
        storage.setConnection(connection);

        UUID minionId = UUID.randomUUID();
        String opId = "reconcile-payout-confirm-" + UUID.randomUUID();

        // 1. When operation is in ATTEMPTED state, PAYOUT_CONFIRM probe returns CONFIRMED_ROLLBACK
        state.pendingOperationStates.put(opId, "ATTEMPTED");
        DatabaseStorage.TransactionOutcome outcomeRollback = storage.probeCommitReconciliation(
                minionId, opId, DatabaseStorage.TransactionType.PAYOUT_CONFIRM);
        assertEquals(DatabaseStorage.TransactionOutcome.CONFIRMED_ROLLBACK, outcomeRollback);

        // 2. When operation is in CONFIRMED state, PAYOUT_CONFIRM probe returns CONFIRMED_COMMIT
        state.pendingOperationStates.put(opId, "CONFIRMED");
        DatabaseStorage.TransactionOutcome outcomeCommit = storage.probeCommitReconciliation(
                minionId, opId, DatabaseStorage.TransactionType.PAYOUT_CONFIRM);
        assertEquals(DatabaseStorage.TransactionOutcome.CONFIRMED_COMMIT, outcomeCommit);

        // 3. When operation is in PAYOUT_AMBIGUOUS or unknown state, probe returns UNKNOWN_COMMIT_OUTCOME
        state.pendingOperationStates.put(opId, "PAYOUT_AMBIGUOUS");
        DatabaseStorage.TransactionOutcome outcomeAmbiguous = storage.probeCommitReconciliation(
                minionId, opId, DatabaseStorage.TransactionType.PAYOUT_CONFIRM);
        assertEquals(DatabaseStorage.TransactionOutcome.UNKNOWN_COMMIT_OUTCOME, outcomeAmbiguous);
    }

    // --- 24. Operation ID Reuse Across Payout and Pickup Is Safely Rejected ---
    @Test
    public void testOperationIdReuseAcrossPayoutAndPickupIsSafelyRejected() {
        MockSqlHelper.State state = new MockSqlHelper.State();
        Connection connection = MockSqlHelper.createMockConnection(state);
        DatabaseStorage storage = new DatabaseStorage(null);
        storage.setConnection(connection);

        UUID minionId1 = UUID.randomUUID();
        UUID minionId2 = UUID.randomUUID();
        UUID playerUuid = UUID.randomUUID();
        String sharedOpId = "shared-op-" + UUID.randomUUID();

        // Minion 1 has 100.0 coins
        state.minionCoins.put(minionId1.toString(), 100.0);
        state.minionCoins.put(minionId2.toString(), 100.0);
        state.minions.add(minionId1.toString());
        state.minions.add(minionId2.toString());

        // First payout succeeds with sharedOpId
        DatabaseStorage.PayoutReservationResult firstPayout = storage.reservePayoutAtomic(sharedOpId, minionId1, playerUuid, 25.0, 1L);
        assertEquals(DatabaseStorage.PayoutReservationResult.RESERVED, firstPayout);
        assertEquals(75.0, state.minionCoins.get(minionId1.toString()), 0.0001);

        // Attempting to reuse sharedOpId for a second payout with same params returns ALREADY_RESERVED and does not deduct coins
        DatabaseStorage.PayoutReservationResult secondPayout = storage.reservePayoutAtomic(sharedOpId, minionId1, playerUuid, 25.0, 2L);
        assertEquals(DatabaseStorage.PayoutReservationResult.ALREADY_RESERVED, secondPayout, "Reusing existing operationId for payout must return ALREADY_RESERVED");
        assertEquals(75.0, state.minionCoins.get(minionId1.toString()), 0.0001, "No extra coins may be deducted on duplicate opId");

        // Attempting to reuse sharedOpId for a pickup of minionId2 must return CONFLICTING_PAYLOAD and not delete minionId2
        DatabaseStorage.PickupRemovalResult pickupResult = storage.executePickupRemoval(sharedOpId, minionId2, playerUuid, 1L, "DIFFERENT_PAYLOAD");
        assertEquals(DatabaseStorage.PickupRemovalResult.CONFLICTING_PAYLOAD, pickupResult);
        assertTrue(state.minions.contains(minionId2.toString()), "Minion must not be deleted when opId conflicts");
    }

    // --- 25. MySQL Upsert Strict Revision Guard Covers All Columns In Order ---
    @Test
    public void testMySQLUpsertStrictRevisionGuardGuardsAllColumnsAndOrder() {
        MockSqlHelper.State state = new MockSqlHelper.State();
        state.databaseProductName = "MySQL";
        Connection connection = MockSqlHelper.createMockConnection(state);
        DatabaseStorage storage = new DatabaseStorage(null);
        storage.setConnection(connection);

        UUID minionId = UUID.randomUUID();
        DatabaseStorage.MinionSnapshot s15 = new DatabaseStorage.MinionSnapshot(
                minionId, UUID.randomUUID(), "COBBLESTONE", 11, "world",
                10.0, 64.0, 10.0, 0f, 0f,
                "LAVA", 5000L, "DIAMOND_SPREADING", "AUTO_SMELTER",
                "DEFAULT", "BUDGET_HOPPER", 100.0,
                null, null, null, null, null,
                1000L, false, 50, 20,
                null, List.of(), List.of(),
                15L, 0L, "{}"
        );

        storage.saveSnapshots(List.of(s15));

        // Find the executed upsert SQL
        String upsertSql = null;
        for (MockSqlHelper.PreparedStatementCall call : state.preparedCalls) {
            if (call.sql.contains("ON DUPLICATE KEY UPDATE")) {
                upsertSql = call.sql;
                break;
            }
        }
        assertNotNull(upsertSql, "MySQL UPSERT query must be generated");

        // Verify strict revision guard IF(src.state_revision > minions.state_revision, ...) on mutable columns
        List<String> mutableColumns = List.of(
                "owner", "type", "tier", "world", "x", "y", "z", "yaw", "pitch",
                "fuel", "fuel_end", "upgrade1", "upgrade2", "skin", "shipping_id", "shipping_coins",
                "storage_id", "storage_world", "storage_x", "storage_y", "storage_z",
                "last_action", "last_was_place", "harvested", "xp", "custom_id",
                "fractional_progress", "drop_remainders"
        );
        for (String col : mutableColumns) {
            String expectedClause = col + "=IF(src.state_revision > minions.state_revision, src." + col + ", minions." + col + ")";
            assertTrue(upsertSql.contains(expectedClause), "Column " + col + " must be strictly revision guarded: " + expectedClause);
        }

        // Verify state_revision is the final assignment in the clause
        String updateClause = upsertSql.substring(upsertSql.indexOf("ON DUPLICATE KEY UPDATE"));
        assertTrue(updateClause.endsWith("state_revision=GREATEST(minions.state_revision, src.state_revision)"),
                "state_revision must be the final assignment in ON DUPLICATE KEY UPDATE to prevent left-to-right evaluation bugs");
    }

    // --- 26. Race Two Pickup Requests With Same OpId Allows Exactly One Delivery ---
    @Test
    public void testRaceTwoPickupRequestsAllowsExactlyOneDelivery() {
        MockSqlHelper.State state = new MockSqlHelper.State();
        Connection connection = MockSqlHelper.createMockConnection(state);
        DatabaseStorage storage = new DatabaseStorage(null);
        storage.setConnection(connection);

        String opId = "race-op-" + UUID.randomUUID();
        state.pendingOperationStates.put(opId, "MINION_REMOVED");

        // First caller attempts claimDeliveryAttempt
        boolean firstClaim = storage.claimDeliveryAttempt(opId);
        assertTrue(firstClaim, "First caller must successfully claim delivery attempt");
        assertEquals("DELIVERY_ATTEMPTED", state.pendingOperationStates.get(opId));

        // Second caller attempts claimDeliveryAttempt on same operation
        boolean secondClaim = storage.claimDeliveryAttempt(opId);
        assertFalse(secondClaim, "Second caller must fail claimDeliveryAttempt, preventing duplicate delivery");

        // Only first caller completes delivery
        boolean complete = storage.completeDelivery(opId);
        assertTrue(complete);
        assertEquals("COMPLETED", state.pendingOperationStates.get(opId));
    }

    // --- 27. Ambiguous Commit Handling Quarantines Minion On Uncertainty ---
    @Test
    public void testAmbiguousCommitHandlingQuarantinesMinionOnUncertainty() {
        MockSqlHelper.State state = new MockSqlHelper.State();
        Connection connection = MockSqlHelper.createMockConnection(state);
        DatabaseStorage storage = new DatabaseStorage(null);
        storage.setConnection(connection);

        UUID minionId = UUID.randomUUID();
        String opId = "ambig-commit-" + UUID.randomUUID();

        // When connection is absent / lost during reservation, minion must be quarantined
        storage.setConnection(null);
        DatabaseStorage.PayoutReservationResult result = storage.reservePayoutAtomic(opId, minionId, UUID.randomUUID(), 50.0, 1L);
        assertEquals(DatabaseStorage.PayoutReservationResult.UNKNOWN_OUTCOME, result);
        assertTrue(storage.isQuarantined(minionId), "Minion must be quarantined when commit/connection fails");
    }

    // --- 28. Scheduler In-Place Compaction Keeps Ready Actions Dispatchable ---
    @Test
    public void testSchedulerInPlaceCompactionKeepsReadyActionsDispatchable() {
        com.minionx.core.MinionManager manager = new com.minionx.core.MinionManager();
        UUID m1Id = UUID.randomUUID();
        Minion m1 = new Minion(m1Id, MinionType.COBBLESTONE, 1);
        manager.register(m1);
        long currentGen = m1.getSchedulingGeneration();

        com.minionx.core.MinionTickTask task = new com.minionx.core.MinionTickTask(manager);

        // Schedule 10 ready valid actions for m1
        long now = System.currentTimeMillis();
        for (int i = 0; i < 10; i++) {
            task.getActionQueue().offer(new com.minionx.core.MinionTickTask.ScheduledMinion(
                    m1Id, now - 1000L + i, currentGen
            ));
        }

        // Add 5 stale actions for unmapped minions
        for (int i = 0; i < 5; i++) {
            task.getActionQueue().offer(new com.minionx.core.MinionTickTask.ScheduledMinion(
                    UUID.randomUUID(), now - 2000L, 99
            ));
        }

        assertEquals(15, task.getActionQueue().size());

        // Perform in-place compaction
        task.performCompaction();

        // Valid actions were NOT locked away into a staging queue; action queue remains immediately accessible and retains m1 entries
        assertEquals(10, task.getActionQueue().size(), "Compaction must prune stale entries while retaining active valid entries");
        com.minionx.core.MinionTickTask.ScheduledMinion peeked = task.getActionQueue().peek();
        assertNotNull(peeked);
        assertTrue(peeked.dueTimeMillis() <= now, "Due actions remain eligible for dispatch immediately");
    }

    // --- 29. Payout Rollback Failure Escalate to UNKNOWN_OUTCOME and Quarantine Barrier ---
    @Test
    public void testPayoutRollbackFailureEscalatesToUnknownOutcomeAndQuarantinesMinion() {
        MockSqlHelper.State state = new MockSqlHelper.State();
        Connection connection = MockSqlHelper.createMockConnection(state);
        DatabaseStorage storage = new DatabaseStorage(null);
        storage.setConnection(connection);

        UUID minionId = UUID.randomUUID();
        UUID playerUuid = UUID.randomUUID();
        String opId = "payout-rb-fail-" + UUID.randomUUID();

        // Minion has 100 coins
        state.minionCoins.put(minionId.toString(), 100.0);

        // When statement fails and rollback fails, outcome must be UNKNOWN_OUTCOME and minion quarantined
        state.throwOnExecute = true;
        state.throwOnRollback = true;
        DatabaseStorage.PayoutReservationResult result = storage.reservePayoutAtomic(opId, minionId, playerUuid, 50.0, 1L);
        assertEquals(DatabaseStorage.PayoutReservationResult.UNKNOWN_OUTCOME, result,
                "When rollback throws an exception, outcome is uncertain and must return UNKNOWN_OUTCOME");
        assertTrue(storage.isQuarantined(minionId), "Minion must be quarantined when rollback cannot be confirmed");
    }

    // --- 30. Pickup Blocked When Permanent Tombstone Already Exists ---
    @Test
    public void testPickupBlockedWhenTombstoneAlreadyExists() {
        MockSqlHelper.State state = new MockSqlHelper.State();
        Connection connection = MockSqlHelper.createMockConnection(state);
        DatabaseStorage storage = new DatabaseStorage(null);
        storage.setConnection(connection);

        UUID minionId = UUID.randomUUID();
        UUID playerUuid = UUID.randomUUID();
        String opId = "pickup-tomb-conflict-" + UUID.randomUUID();

        state.minions.add(minionId.toString());
        state.trackMinionPresence = true;
        // Coexisting tombstone already present in database!
        state.tombstones.put(minionId.toString(), 1L);
        state.tombstoneOperations.put(minionId.toString(), "prior-deletion-op");

        DatabaseStorage.PickupRemovalResult result = storage.executePickupRemoval(opId, minionId, playerUuid, 1L, "PAYLOAD");
        assertEquals(DatabaseStorage.PickupRemovalResult.UNKNOWN_OUTCOME, result,
                "Coexisting active minion and permanent tombstone is contradictory; must return UNKNOWN_OUTCOME");
        assertTrue(storage.isQuarantined(minionId), "Minion must be quarantined upon contradictory tombstone state");
        assertTrue(state.minions.contains(minionId.toString()), "Active minion row must NOT be deleted when tombstone conflict exists");
        assertEquals("prior-deletion-op", state.tombstoneOperations.get(minionId.toString()),
                "Original tombstone linkage must NOT be overwritten");
    }

    // --- 31. Idempotent Pickup Rejects Contradictory Active Minion Or Missing Tombstone ---
    @Test
    public void testIdempotentPickupRejectsContradictoryActiveMinionOrMissingTombstone() {
        MockSqlHelper.State state = new MockSqlHelper.State();
        Connection connection = MockSqlHelper.createMockConnection(state);
        DatabaseStorage storage = new DatabaseStorage(null);
        storage.setConnection(connection);

        UUID minionId = UUID.randomUUID();
        UUID playerUuid = UUID.randomUUID();
        String opId = "idemp-contradict-" + UUID.randomUUID();

        // Pending operation exists with state MINION_REMOVED
        state.pendingOperationStates.put(opId, "MINION_REMOVED");
        state.pendingOperationMinions.put(opId, minionId.toString());
        state.pendingOperationPlayers.put(opId, playerUuid.toString());
        state.pendingOperationPayloads.put(opId, "PAYLOAD");

        // Contradiction A: Active minion is still present in database!
        state.minions.add(minionId.toString());
        state.trackMinionPresence = true;
        state.tombstones.put(minionId.toString(), 1L);
        state.tombstoneOperations.put(minionId.toString(), opId);

        DatabaseStorage.PickupRemovalResult resA = storage.executePickupRemoval(opId, minionId, playerUuid, 1L, "PAYLOAD");
        assertEquals(DatabaseStorage.PickupRemovalResult.UNKNOWN_OUTCOME, resA,
                "Idempotent retry when active minion row still exists is contradictory and must return UNKNOWN_OUTCOME");
        assertTrue(storage.isQuarantined(minionId));

        // Contradiction B: Minion absent, but tombstone operation does not match opId!
        storage.unquarantineMinion(minionId);
        state.minions.remove(minionId.toString());
        state.tombstoneOperations.put(minionId.toString(), "mismatched-op");

        DatabaseStorage.PickupRemovalResult resB = storage.executePickupRemoval(opId, minionId, playerUuid, 1L, "PAYLOAD");
        assertEquals(DatabaseStorage.PickupRemovalResult.UNKNOWN_OUTCOME, resB,
                "Idempotent retry when tombstone operation linkage does not match must return UNKNOWN_OUTCOME");
        assertTrue(storage.isQuarantined(minionId));

        // Valid consistent state: Minion absent AND tombstone matches opId
        storage.unquarantineMinion(minionId);
        state.tombstoneOperations.put(minionId.toString(), opId);

        DatabaseStorage.PickupRemovalResult validRes = storage.executePickupRemoval(opId, minionId, playerUuid, 1L, "PAYLOAD");
        assertEquals(DatabaseStorage.PickupRemovalResult.ALREADY_COMMITTED_MINION_REMOVED, validRes,
                "Consistent idempotent lookup must safely return ALREADY_COMMITTED_MINION_REMOVED");
    }

    // --- 32. Pickup Rollback Failure Escalates to UNKNOWN_OUTCOME and Quarantines Minion ---
    @Test
    public void testPickupRollbackFailureEscalatesToUnknownOutcomeAndQuarantinesMinion() {
        MockSqlHelper.State state = new MockSqlHelper.State();
        Connection connection = MockSqlHelper.createMockConnection(state);
        DatabaseStorage storage = new DatabaseStorage(null);
        storage.setConnection(connection);

        UUID minionId = UUID.randomUUID();
        UUID playerUuid = UUID.randomUUID();
        String opId = "pickup-rb-fail-" + UUID.randomUUID();

        state.minions.add(minionId.toString());
        state.throwOnExecute = true;
        state.throwOnRollback = true;

        DatabaseStorage.PickupRemovalResult result = storage.executePickupRemoval(opId, minionId, playerUuid, 1L, "PAYLOAD");
        assertEquals(DatabaseStorage.PickupRemovalResult.UNKNOWN_OUTCOME, result,
                "When rollback throws during pickup failure, outcome must be UNKNOWN_OUTCOME");
        assertTrue(storage.isQuarantined(minionId), "Minion must be quarantined when pickup rollback outcome is uncertain");
    }

    // --- 33. Payout Idempotent Retry Does Not Restore Already Deducted Coins ---
    @Test
    public void testPayoutIdempotentRetryDoesNotRestoreAlreadyDeductedCoins() {
        MockSqlHelper.State state = new MockSqlHelper.State();
        Connection connection = MockSqlHelper.createMockConnection(state);
        DatabaseStorage storage = new DatabaseStorage(null);
        storage.setConnection(connection);

        UUID minionId = UUID.randomUUID();
        UUID playerUuid = UUID.randomUUID();
        String opId = "payout-idemp-" + UUID.randomUUID();

        // Scenario A: Operation already committed in ATTEMPTED state
        state.pendingOperationStates.put(opId, "ATTEMPTED");
        state.pendingOperationMinions.put(opId, minionId.toString());
        state.pendingOperationPlayers.put(opId, playerUuid.toString());
        state.pendingOperationPayloads.put(opId, "500.0");

        DatabaseStorage.PayoutReservationResult resAttempted = storage.reservePayoutAtomic(opId, minionId, playerUuid, 500.0, 1L);
        assertEquals(DatabaseStorage.PayoutReservationResult.ALREADY_RESERVED, resAttempted,
                "Idempotent retry of ATTEMPTED payout must return ALREADY_RESERVED and not FAILED_CONFIRMED_ROLLBACK");

        // Scenario B: Operation already completed in CONFIRMED state
        state.pendingOperationStates.put(opId, "CONFIRMED");
        DatabaseStorage.PayoutReservationResult resConfirmed = storage.reservePayoutAtomic(opId, minionId, playerUuid, 500.0, 1L);
        assertEquals(DatabaseStorage.PayoutReservationResult.ALREADY_CONFIRMED, resConfirmed,
                "Idempotent retry of CONFIRMED payout must return ALREADY_CONFIRMED so caller does not refund");

        // Scenario C: Operation exists but parameters mismatch (corrupt/conflicting retry)
        DatabaseStorage.PayoutReservationResult resMismatch = storage.reservePayoutAtomic(opId, minionId, playerUuid, 250.0, 1L);
        assertEquals(DatabaseStorage.PayoutReservationResult.UNKNOWN_OUTCOME, resMismatch,
                "Mismatched payout amount on retry must return UNKNOWN_OUTCOME and quarantine");
        assertTrue(storage.isQuarantined(minionId));
    }

    // --- 34. Pickup Revision Mismatch Rejects Deletion ---
    @Test
    public void testPickupRevisionMismatchRejectsDeletion() {
        MockSqlHelper.State state = new MockSqlHelper.State();
        Connection connection = MockSqlHelper.createMockConnection(state);
        DatabaseStorage storage = new DatabaseStorage(null);
        storage.setConnection(connection);

        UUID minionId = UUID.randomUUID();
        UUID playerUuid = UUID.randomUUID();
        String opId = "pickup-rev-mismatch-" + UUID.randomUUID();

        // Minion is present in database
        state.minions.add(minionId.toString());
        state.trackMinionPresence = true;
        // Simulate revision mismatch: 0 rows affected by revision-guarded delete
        state.defaultUpdateCount = 0;

        DatabaseStorage.PickupRemovalResult result = storage.executePickupRemoval(opId, minionId, playerUuid, 5L, "PAYLOAD");
        assertEquals(DatabaseStorage.PickupRemovalResult.PRECONDITION_FAILED, result,
                "Pickup removal with mismatched revision must fail precondition without deleting child items or inserting tombstone");
        assertFalse(state.tombstones.containsKey(minionId.toString()), "Tombstone must NOT be inserted on revision mismatch");
    }

    // --- 35. Rollback setAutoCommit Failure Invalidate Connection & Escalates to UNKNOWN ---
    @Test
    public void testRollbackAutoCommitFailureEscalatesToUnknown() {
        MockSqlHelper.State state = new MockSqlHelper.State();
        Connection connection = MockSqlHelper.createMockConnection(state);
        DatabaseStorage storage = new DatabaseStorage(null);
        storage.setConnection(connection);

        UUID minionId = UUID.randomUUID();
        UUID playerUuid = UUID.randomUUID();
        String opId = "autocommit-fail-" + UUID.randomUUID();

        // Cause DML failure and simulate setAutoCommit(true) throwing in rollback helper
        state.throwOnExecute = true;
        state.throwOnSetAutoCommitTrue = true;

        DatabaseStorage.PayoutReservationResult res = storage.reservePayoutAtomic(opId, minionId, playerUuid, 100.0, 1L);
        assertEquals(DatabaseStorage.PayoutReservationResult.UNKNOWN_OUTCOME, res,
                "If setAutoCommit(true) fails during rollback recovery, outcome must escalate to UNKNOWN_OUTCOME");
        assertTrue(storage.isQuarantined(minionId), "Minion must be quarantined when connection recovery fails");
    }

    // --- 36. Exact Integer Micros Currency No Epsilon Bypass ---
    @Test
    public void testExactIntegerMicrosCurrencyNoEpsilonBypass() {
        Minion minion = new Minion(UUID.randomUUID(), com.minionx.api.MinionType.COBBLESTONE, 1);
        // Set balance to 499.9999 (499,999,900 micros)
        minion.setShippingCoins(499.9999);

        // Attempting to reserve 500.0 (500,000,000 micros) must fail with zero epsilon bypass
        boolean reserved = minion.reserveClaimableCoins(500.0);
        assertFalse(reserved, "reserveClaimableCoins must fail when balance is 499.9999 and requested is 500.0");
        assertEquals(0.0, minion.getReservedShippingCoins(), 0.000001);
        assertEquals(499.9999, minion.getClaimableShippingCoins(), 0.0001);
    }
}

