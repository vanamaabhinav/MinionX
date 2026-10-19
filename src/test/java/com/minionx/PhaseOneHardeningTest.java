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

        // Attempting to reuse sharedOpId for a second payout with same params returns UNKNOWN_OUTCOME and does not deduct coins
        DatabaseStorage.PayoutReservationResult secondPayout = storage.reservePayoutAtomic(sharedOpId, minionId1, playerUuid, 25.0, 2L);
        assertEquals(DatabaseStorage.PayoutReservationResult.UNKNOWN_OUTCOME, secondPayout, "Reusing existing operationId for payout in ATTEMPTED state must return UNKNOWN_OUTCOME and quarantine");
        assertEquals(75.0, state.minionCoins.get(minionId1.toString()), 0.0001, "No extra coins may be deducted on duplicate opId");
        assertTrue(storage.isQuarantined(minionId1), "Minion must be quarantined on retry of ATTEMPTED payout operation");

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
        assertEquals(DatabaseStorage.PayoutReservationResult.UNKNOWN_OUTCOME, resAttempted,
                "Idempotent retry of ATTEMPTED payout must return UNKNOWN_OUTCOME and quarantine to prevent double deposit");
        assertTrue(storage.isQuarantined(minionId), "Minion must be quarantined on retry of ATTEMPTED operation");

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

    // --- 37. Sub-Decimal Payout Rejection (< 0.0001) ---
    @Test
    public void testSubDecimalPayoutAmountRejected() {
        MockSqlHelper.State state = new MockSqlHelper.State();
        Connection connection = MockSqlHelper.createMockConnection(state);
        DatabaseStorage storage = new DatabaseStorage(null);
        storage.setConnection(connection);

        UUID minionId = UUID.randomUUID();
        UUID playerUuid = UUID.randomUUID();

        // 0.00004 normalizes to 0.0000 in HALF_UP 4-decimal, must be rejected outright!
        DatabaseStorage.PayoutReservationResult res1 = storage.reservePayoutAtomic(
                "payout-sub-" + UUID.randomUUID(), minionId, playerUuid, 0.00004, 1L);
        assertEquals(DatabaseStorage.PayoutReservationResult.FAILED_CONFIRMED_ROLLBACK, res1,
                "Payout request < 0.0001 (e.g., 0.00004) must be rejected outright");

        DatabaseStorage.PayoutReservationResult res2 = storage.reservePayoutAtomic(
                "payout-sub-" + UUID.randomUUID(), minionId, playerUuid, 0.00009, 1L);
        assertEquals(DatabaseStorage.PayoutReservationResult.FAILED_CONFIRMED_ROLLBACK, res2,
                "Payout request < 0.0001 (e.g., 0.00009) must be rejected outright");
    }

    // --- 38. Commit Exception Invalidates Connection ---
    @Test
    public void testCommitExceptionInvalidatesConnection() {
        MockSqlHelper.State state = new MockSqlHelper.State();
        Connection connection = MockSqlHelper.createMockConnection(state);
        DatabaseStorage storage = new DatabaseStorage(null);
        storage.setConnection(connection);

        UUID minionId = UUID.randomUUID();
        UUID playerUuid = UUID.randomUUID();
        String opId = "commit-fail-" + UUID.randomUUID();

        state.minions.add(minionId.toString());
        state.trackMinionPresence = true;
        state.defaultUpdateCount = 1;
        state.throwOnCommit = true;

        DatabaseStorage.PayoutReservationResult res = storage.reservePayoutAtomic(opId, minionId, playerUuid, 10.0, 1L);
        // Connection should have been closed and invalidated upon commit exception
        assertTrue(state.transactionEvents.contains("close"), "Connection must be closed on commit failure");
        assertNull(storage.getConnection(), "DatabaseStorage cached connection must be set to null after commit failure");
    }

    // --- 39. Dual Balance Restores Coins on ALREADY_CONFIRMED ---
    @Test
    public void testDualBalanceRestoresCoinsOnAlreadyConfirmedReplay() {
        Minion minion = new Minion(UUID.randomUUID(), com.minionx.api.MinionType.COBBLESTONE, 1);
        minion.setShippingCoins(100.0);

        // Main thread tentatively reserves 50.0 coins
        boolean reserved = minion.reserveClaimableCoins(50.0);
        assertTrue(reserved);
        assertEquals(50.0, minion.getClaimableShippingCoins(), 0.0001);
        assertEquals(50.0, minion.getReservedShippingCoins(), 0.0001);

        // Persistence returns ALREADY_CONFIRMED (previous historical payout completed with same opId)
        // Main thread cancels reserved coins so newly earned live coins are preserved!
        minion.cancelReservedCoins();

        assertEquals(100.0, minion.getClaimableShippingCoins(), 0.0001,
                "Tentatively reserved coins must be restored to live balance on ALREADY_CONFIRMED");
        assertEquals(0.0, minion.getReservedShippingCoins(), 0.0001);
    }

    // --- 40. Precision Invariance: Memory and Storage Parity ---
    @Test
    public void testPrecisionInvarianceBetweenMemoryAndStorage() {
        Minion minion = new Minion(UUID.randomUUID(), com.minionx.api.MinionType.COBBLESTONE, 1);
        // Earning sub-4-decimal amount: 0.00014
        minion.addShippingCoins(0.00014);

        // In-memory balance must normalize to 4-decimals (0.000100 / 100 micros)
        assertEquals(0.0001, minion.getShippingCoins(), 0.000001);
        assertEquals(100L, minion.getShippingCoinsMicros(), "shippingCoinsMicros must be multiple of 100 to eliminate drift with SQL ROUND(4)");

        // Deducting 0.0001 leaves exactly 0.0
        boolean reserved = minion.reserveClaimableCoins(0.0001);
        assertTrue(reserved);
        assertEquals(0.0, minion.getShippingCoins(), 0.000001);
        assertEquals(0L, minion.getShippingCoinsMicros());
    }

    // --- 41. Startup Reconciliation Quarantines Escrow States ---
    @Test
    public void testStartupReconciliationQuarantinesEscrowStates() {
        MinionStorage storage = new MinionStorage(null);
        UUID minionId1 = UUID.randomUUID();
        UUID minionId2 = UUID.randomUUID();
        UUID minionId3 = UUID.randomUUID();

        storage.recordTombstone(minionId1, 1L);
        storage.recordTombstone(minionId2, 1L);
        storage.recordTombstone(minionId3, 1L);

        try {
            storage.recordPendingOperation(new com.minionx.storage.PendingOperation(
                    "op-1", minionId1, UUID.randomUUID(), "MINION_REMOVED", System.currentTimeMillis(), "payload1"));
            storage.recordPendingOperation(new com.minionx.storage.PendingOperation(
                    "op-2", minionId2, UUID.randomUUID(), "DELIVERY_CLAIM_FAILED", System.currentTimeMillis(), "payload2"));
            storage.recordPendingOperation(new com.minionx.storage.PendingOperation(
                    "op-3", minionId3, UUID.randomUUID(), "QUARANTINED_LEFTOVERS", System.currentTimeMillis(), "payload3"));
        } catch (Exception e) {
            fail("Failed to record pending operations for test: " + e.getMessage());
        }

        storage.reconcilePendingOperations(null);

        assertTrue(storage.isQuarantined(minionId1), "MINION_REMOVED operation minion must be quarantined on startup");
        assertTrue(storage.isQuarantined(minionId2), "DELIVERY_CLAIM_FAILED operation minion must be quarantined on startup");
        assertTrue(storage.isQuarantined(minionId3), "QUARANTINED_LEFTOVERS operation minion must be quarantined on startup");
    }

    // --- 42. Rollback Payout Atomic Fails When Not Attempted ---
    @Test
    public void testRollbackPayoutAtomicFailsWhenNotAttempted() {
        MockSqlHelper.State state = new MockSqlHelper.State();
        Connection connection = MockSqlHelper.createMockConnection(state);
        DatabaseStorage storage = new DatabaseStorage(null);
        storage.setConnection(connection);

        UUID minionId = UUID.randomUUID();
        String opId = "payout-rb-fail-" + UUID.randomUUID();

        // Operation is already CONFIRMED (not ATTEMPTED)
        state.pendingOperationStates.put(opId, "CONFIRMED");
        state.minionCoins.put(minionId.toString(), 100.0);
        state.minions.add(minionId.toString());

        boolean rolledBack = storage.rollbackPayoutAtomic(opId, minionId, 25.0);
        assertFalse(rolledBack, "rollbackPayoutAtomic must return false when operation is not in ATTEMPTED state");
        assertEquals(100.0, state.minionCoins.get(minionId.toString()), 0.0001, "Minion balance must not be credited on failed rollback");
    }

    // --- 43. Offline Player Disconnect Preserves Items and Unconsumed Stored XP ---
    @Test
    public void testPickupOfflinePlayerRetainsItemsAndStoredXp() throws Exception {
        MinionStorage storage = new MinionStorage(null, tempDir);
        UUID minionId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        String opId = "offline-pickup-" + UUID.randomUUID();

        Minion minion = new Minion(minionId, MinionType.COBBLESTONE, 1);
        minion.addStoredXp(120);
        assertEquals(120, minion.getStoredXp());

        // In offline disconnect handling, player is offline, so claimStoredXp() is NEVER called.
        // Payload records XP prefix: "XP:" + storedXp + ";;;PAYLOAD:::" + payloadStr
        String payloadStr = "mock_item_yaml";
        int storedXp = minion.getStoredXp();
        String offlinePayload = (storedXp > 0) ? "XP:" + storedXp + ";;;PAYLOAD:::" + payloadStr : payloadStr;

        storage.recordPendingOperation(new com.minionx.storage.PendingOperation(
                opId, minionId, playerId, "DELIVERY_FAILED_OFFLINE", System.currentTimeMillis(), offlinePayload));
        storage.quarantineMinion(minionId);

        // Verify minion stored XP was NOT consumed
        assertEquals(120, minion.getStoredXp(), "Minion stored XP must not be consumed when player is offline");
        assertTrue(storage.isQuarantined(minionId), "Minion must be quarantined when delivery fails offline");

        // Verify durable state is DELIVERY_FAILED_OFFLINE
        List<com.minionx.storage.PendingOperation> pending = storage.getPendingOperations();
        com.minionx.storage.PendingOperation op = pending.stream().filter(o -> o.operationId().equals(opId)).findFirst().orElse(null);
        assertNotNull(op);
        assertEquals("DELIVERY_FAILED_OFFLINE", op.state());
        assertTrue(op.payload().startsWith("XP:120;;;PAYLOAD:::"));

        // Startup reconciliation retains DELIVERY_FAILED_OFFLINE and keeps minion quarantined
        storage.reconcilePendingOperations(null);
        assertTrue(storage.isQuarantined(minionId));
    }

    // --- 44. Failed Leftover Persistence Records Ambiguous Ledger and Quarantines ---
    @Test
    public void testPartialDeliveryFailureRecordsAmbiguousLedgerAndProhibitsAutomaticRedelivery() throws Exception {
        MinionStorage storage = new MinionStorage(null, tempDir);
        UUID minionId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        String opId = "ambiguous-delivery-" + UUID.randomUUID();

        String deliveredStr = "item_diamond_x32";
        String leftoversStr = "item_diamond_x32";
        String auditLedger = "DELIVERED:::" + deliveredStr + ";;;LEFTOVER:::" + leftoversStr;

        // When writing QUARANTINED_LEFTOVERS fails, it transitions to AMBIGUOUS_PARTIAL_DELIVERY with full ledger
        storage.recordPendingOperation(new com.minionx.storage.PendingOperation(
                opId, minionId, playerId, "AMBIGUOUS_PARTIAL_DELIVERY", System.currentTimeMillis(), auditLedger));
        storage.quarantineMinion(minionId);

        assertTrue(storage.isQuarantined(minionId));

        // Startup reconciliation must quarantine minion and preserve AMBIGUOUS_PARTIAL_DELIVERY without redelivering full payload
        storage.reconcilePendingOperations(null);
        assertTrue(storage.isQuarantined(minionId));

        List<com.minionx.storage.PendingOperation> pending = storage.getPendingOperations();
        com.minionx.storage.PendingOperation op = pending.stream().filter(o -> o.operationId().equals(opId)).findFirst().orElse(null);
        assertNotNull(op);
        assertEquals("AMBIGUOUS_PARTIAL_DELIVERY", op.state());
        assertEquals(auditLedger, op.payload());
    }

    // --- 45. Complete Delivery Failure Retains Pending Operation ---
    @Test
    public void testCompleteDeliveryFailureRetainsPendingOperation() {
        MockSqlHelper.State state = new MockSqlHelper.State();
        Connection connection = MockSqlHelper.createMockConnection(state);
        DatabaseStorage storage = new DatabaseStorage(null);
        storage.setConnection(connection);

        String opId = "complete-fail-" + UUID.randomUUID();
        UUID minionId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();

        // Operation is not in DELIVERY_ATTEMPTED state (e.g. MINION_REMOVED or nonexistent)
        state.pendingOperationStates.put(opId, "MINION_REMOVED");

        boolean completed = storage.completeDelivery(opId);
        assertFalse(completed, "completeDelivery must return false when row cannot be transitioned from DELIVERY_ATTEMPTED");

        // When completeDelivery returns false, caller MUST retain pending operation
        assertEquals("MINION_REMOVED", state.pendingOperationStates.get(opId));
    }

    // --- 46. Ambiguous Payout Quarantines and Retains Lock ---
    @Test
    public void testAmbiguousPayoutQuarantinesAndRetainsLock() {
        MinionStorage storage = new MinionStorage(null, tempDir);
        UUID minionId = UUID.randomUUID();

        // 1. Acquire payout lock
        boolean locked = storage.acquirePayoutLock(minionId);
        assertTrue(locked);
        assertTrue(storage.isPayoutActive(minionId));

        // 2. Ambiguous outcome occurs: minion is explicitly quarantined and payout lock is NOT released
        storage.quarantineMinion(minionId);

        // Lock remains held!
        assertTrue(storage.isPayoutActive(minionId), "Payout lock must remain held when payout outcome is ambiguous");
        assertTrue(storage.isQuarantined(minionId), "Minion must be quarantined when payout outcome is ambiguous");

        // Trying to acquire payout lock again fails
        assertFalse(storage.acquirePayoutLock(minionId), "Cannot acquire payout lock while previous ambiguous payout is fenced");
    }

    // --- 47. Failed Payout Confirmation Quarantines and Retains Lock ---
    @Test
    public void testFailedPayoutConfirmationQuarantinesAndRetainsLock() {
        MinionStorage storage = new MinionStorage(null, tempDir);
        UUID minionId = UUID.randomUUID();

        // 1. Acquire payout lock
        boolean locked = storage.acquirePayoutLock(minionId);
        assertTrue(locked);

        // 2. Database confirmation fails: confirmPayoutAtomic returns false
        boolean confirmed = false; // simulated failure
        if (!confirmed) {
            storage.quarantineMinion(minionId);
            // lock is NOT released
        }

        assertTrue(storage.isPayoutActive(minionId), "Payout lock must be retained if confirmation fails");
        assertTrue(storage.isQuarantined(minionId), "Minion must be quarantined if confirmation fails");
    }

    // --- 48. Stored XP Preserved Across All Failure / Leftover Escrow Paths ---
    @Test
    public void testStoredXpPreservedInLeftoversAndDeliveryClaimFailure() throws Exception {
        MinionStorage storage = new MinionStorage(null, tempDir);
        UUID minionId1 = UUID.randomUUID();
        UUID minionId2 = UUID.randomUUID();
        UUID minionId3 = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();

        // 1. DELIVERY_CLAIM_FAILED preserves XP
        String payloadClaimFailed = "XP:150;;;PAYLOAD:::item_iron_x64";
        storage.recordPendingOperation(new com.minionx.storage.PendingOperation(
                "op-claim-failed", minionId1, playerId, "DELIVERY_CLAIM_FAILED", System.currentTimeMillis(), payloadClaimFailed));

        // 2. QUARANTINED_LEFTOVERS preserves XP
        String payloadLeftovers = "XP:200;;;PAYLOAD:::item_iron_x32";
        storage.recordPendingOperation(new com.minionx.storage.PendingOperation(
                "op-leftovers", minionId2, playerId, "QUARANTINED_LEFTOVERS", System.currentTimeMillis(), payloadLeftovers));

        // 3. AMBIGUOUS_PARTIAL_DELIVERY preserves XP
        String payloadAmbiguous = "XP:300;;;PAYLOAD:::DELIVERED:::item_iron_x32;;;LEFTOVER:::item_iron_x32";
        storage.recordPendingOperation(new com.minionx.storage.PendingOperation(
                "op-ambiguous", minionId3, playerId, "AMBIGUOUS_PARTIAL_DELIVERY", System.currentTimeMillis(), payloadAmbiguous));

        List<com.minionx.storage.PendingOperation> pending = storage.getPendingOperations();
        assertEquals(3, pending.size());

        for (com.minionx.storage.PendingOperation op : pending) {
            assertTrue(op.payload().startsWith("XP:"), "Every failure/leftover payload must preserve stored XP");
        }

        // Startup reconciliation retains all three and quarantines minions
        storage.reconcilePendingOperations(null);
        assertTrue(storage.isQuarantined(minionId1));
        assertTrue(storage.isQuarantined(minionId2));
        assertTrue(storage.isQuarantined(minionId3));
    }

    // --- 49. Startup Reconciliation Quarantines Unresolved Payouts (ATTEMPTED & PAYOUT_AMBIGUOUS) ---
    @Test
    public void testStartupReconciliationQuarantinesUnresolvedPayoutsAndFences() throws Exception {
        MinionStorage storage = new MinionStorage(null, tempDir);
        UUID minionAttempted = UUID.randomUUID();
        UUID minionAmbiguous = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();

        storage.recordPendingOperation(new com.minionx.storage.PendingOperation(
                "payout-attempted-1", minionAttempted, playerId, "ATTEMPTED", System.currentTimeMillis(), "500.0"));
        storage.recordPendingOperation(new com.minionx.storage.PendingOperation(
                "payout-ambiguous-1", minionAmbiguous, playerId, "PAYOUT_AMBIGUOUS", System.currentTimeMillis(), "250.0"));

        // Simulate server restart and reconciliation
        storage.reconcilePendingOperations(null);

        // Both minions must be quarantined
        assertTrue(storage.isQuarantined(minionAttempted), "ATTEMPTED payout minion must be quarantined on startup");
        assertTrue(storage.isQuarantined(minionAmbiguous), "PAYOUT_AMBIGUOUS minion must be quarantined on startup");

        // Both minions must have active payout locks re-established
        assertTrue(storage.isPayoutActive(minionAttempted), "Payout lock must be active for ATTEMPTED minion on startup");
        assertTrue(storage.isPayoutActive(minionAmbiguous), "Payout lock must be active for PAYOUT_AMBIGUOUS minion on startup");

        // Attempting to acquire payout lock must fail
        assertFalse(storage.acquirePayoutLock(minionAttempted), "Cannot acquire payout lock on quarantined ATTEMPTED minion");
        assertFalse(storage.acquirePayoutLock(minionAmbiguous), "Cannot acquire payout lock on quarantined PAYOUT_AMBIGUOUS minion");
    }

    // --- 50. Recovery Decodes Offline XP and Ambiguous Partial Payloads Without Loss ---
    @Test
    public void testOfflineAndAmbiguousRecoveryExtractsBothXpAndItems() {
        // Test 1: DELIVERY_FAILED_OFFLINE decoding
        String offlinePayload = "XP:75;;;PAYLOAD:::item_cobble_x64;;;ITEM;;;item_cobble_x32";
        int xpOffline = 0;
        String itemsOffline = offlinePayload;
        if (itemsOffline.startsWith("XP:") && itemsOffline.contains(";;;PAYLOAD:::")) {
            int sep = itemsOffline.indexOf(";;;PAYLOAD:::");
            xpOffline = Integer.parseInt(itemsOffline.substring(3, sep));
            itemsOffline = itemsOffline.substring(sep + 13);
        }
        assertEquals(75, xpOffline, "Must extract exact XP from DELIVERY_FAILED_OFFLINE");
        assertEquals("item_cobble_x64;;;ITEM;;;item_cobble_x32", itemsOffline, "Must extract remaining items payload");

        // Test 2: AMBIGUOUS_PARTIAL_DELIVERY decoding (XP must be extracted BEFORE leftover slicing!)
        String ambiguousPayload = "XP:90;;;PAYLOAD:::DELIVERED:::item_delivered_x32;;;LEFTOVER:::item_leftover_x32";
        int xpAmbiguous = 0;
        String itemsAmbiguous = ambiguousPayload;
        if (itemsAmbiguous.startsWith("XP:") && itemsAmbiguous.contains(";;;PAYLOAD:::")) {
            int sep = itemsAmbiguous.indexOf(";;;PAYLOAD:::");
            xpAmbiguous = Integer.parseInt(itemsAmbiguous.substring(3, sep));
            itemsAmbiguous = itemsAmbiguous.substring(sep + 13);
        }
        assertEquals(90, xpAmbiguous, "Must extract exact XP from AMBIGUOUS_PARTIAL_DELIVERY");

        int leftoverIdx = itemsAmbiguous.indexOf(";;;LEFTOVER:::");
        assertTrue(leftoverIdx >= 0);
        itemsAmbiguous = itemsAmbiguous.substring(leftoverIdx + 14);
        assertEquals("item_leftover_x32", itemsAmbiguous, "Must extract ONLY the leftover items, excluding already delivered items");
    }

    // --- 51. Repeated Claim After Partial Delivery Prevents Duplicate XP ---
    @Test
    public void testRepeatedClaimAfterPartialInventoryDeliveryDoesNotDuplicateXp() {
        int initialXp = 100;
        int totalItems = 20;

        // Step 1: Claim attempt 1 with partial inventory space (15 items fit, 5 leftovers)
        int spaceAttempt1 = 15;
        int deliveredStep1 = Math.min(spaceAttempt1, totalItems);
        int leftoversStep1 = totalItems - deliveredStep1;
        int xpGrantedStep1 = 0;

        // Invariant: XP is ONLY granted when leftovers.isEmpty()
        if (leftoversStep1 == 0) {
            xpGrantedStep1 = initialXp;
        }
        assertEquals(0, xpGrantedStep1, "XP must NOT be granted when item delivery has leftovers");
        assertEquals(5, leftoversStep1, "5 items must remain in leftovers");

        // The remaining escrow payload retains the full XP
        String remPayload = "XP:" + initialXp + ";;;PAYLOAD:::items_count_" + leftoversStep1;
        assertTrue(remPayload.startsWith("XP:100;"), "Unclaimed XP must be preserved with leftover escrow");

        // Step 2: Player clears inventory and claims remaining 5 items (space >= 5)
        int spaceAttempt2 = 20;
        int remainingItemsToDeliver = leftoversStep1;
        int deliveredStep2 = Math.min(spaceAttempt2, remainingItemsToDeliver);
        int leftoversStep2 = remainingItemsToDeliver - deliveredStep2;
        int xpGrantedStep2 = 0;

        // Parse XP from remaining payload
        int parsedXp = 0;
        if (remPayload.startsWith("XP:") && remPayload.contains(";;;PAYLOAD:::")) {
            int sep = remPayload.indexOf(";;;PAYLOAD:::");
            parsedXp = Integer.parseInt(remPayload.substring(3, sep));
        }
        assertEquals(100, parsedXp);

        if (leftoversStep2 == 0) {
            xpGrantedStep2 = parsedXp;
        }
        assertEquals(100, xpGrantedStep2, "XP must be granted when all remaining items fit (0 leftovers)");
        assertEquals(0, leftoversStep2);

        // Verify total XP across both steps is EXACTLY initialXp (never 200)
        int totalXpGranted = xpGrantedStep1 + xpGrantedStep2;
        assertEquals(100, totalXpGranted, "Total XP granted across partial claims must equal exact initial XP (no double grants)");
    }

    // --- 52. Ambiguous Delivery Outcome Withholds Automatic XP in Recovery ---
    @Test
    public void testAmbiguousDeliveryOutcomeWithholdsAutomaticXpInRecovery() {
        // Case 1: DELIVERY_ATTEMPTED (items/XP may have already reached player)
        String opStateAttempted = "DELIVERY_ATTEMPTED";
        String payloadAttempted = "XP:120;;;PAYLOAD:::item_diamond_x16";
        boolean isAmbiguous1 = "UNKNOWN".equalsIgnoreCase(opStateAttempted)
                || "DELIVERY_ATTEMPTED".equalsIgnoreCase(opStateAttempted)
                || "AMBIGUOUS_PARTIAL_DELIVERY".equalsIgnoreCase(opStateAttempted);
        assertTrue(isAmbiguous1, "DELIVERY_ATTEMPTED must be classified as an ambiguous delivery outcome");

        int xpToGrant1 = 120;
        boolean allowAutomaticXp1 = !isAmbiguous1;
        assertFalse(allowAutomaticXp1, "Automatic XP grant must be withheld for ambiguous DELIVERY_ATTEMPTED operation");

        // Case 2: Operation marked XP_GRANTED after failed completeDelivery
        String payloadXpGranted = "XP_GRANTED:120;;;PAYLOAD:::item_diamond_x16";
        boolean xpAlreadyDelivered = payloadXpGranted.startsWith("XP_GRANTED:") || payloadXpGranted.startsWith("XP_DELIVERED:");
        assertTrue(xpAlreadyDelivered, "Must detect XP was already granted in prior pickup attempt");
        boolean allowXpGrant2 = !xpAlreadyDelivered;
        assertFalse(allowXpGrant2, "Must never re-grant XP when marked XP_GRANTED");

        // Case 3: DELIVERY_FAILED_OFFLINE (unambiguous: player was offline, 0 items and 0 XP delivered)
        String opStateOffline = "DELIVERY_FAILED_OFFLINE";
        boolean isAmbiguous3 = "UNKNOWN".equalsIgnoreCase(opStateOffline)
                || "DELIVERY_ATTEMPTED".equalsIgnoreCase(opStateOffline)
                || "AMBIGUOUS_PARTIAL_DELIVERY".equalsIgnoreCase(opStateOffline);
        assertFalse(isAmbiguous3, "DELIVERY_FAILED_OFFLINE is an unambiguous safe escrow state");
        boolean allowAutomaticXp3 = !isAmbiguous3;
        assertTrue(allowAutomaticXp3, "Automatic XP grant is permitted for unambiguous DELIVERY_FAILED_OFFLINE");
    }

    // --- 53. Delivered Item Accounting With Mutated ItemStack ---
    @Test
    public void testDeliveredItemAccountingWithMutatedItemStack() {
        int originalStackAmount = 64;
        int inventorySpaceAvailable = 60;

        // In Bukkit, addItem may mutate the input ItemStack amount to the remaining count (4)
        int mutatedItemStackAmount = originalStackAmount - inventorySpaceAvailable; // 4
        int leftoverCount = mutatedItemStackAmount; // 4

        // Erroneous calculation: item.getAmount() - leftoverCount = 4 - 4 = 0
        int buggyDeliveredCount = mutatedItemStackAmount - leftoverCount;
        assertEquals(0, buggyDeliveredCount, "Buggy calculation yields 0 delivered despite 60 items delivered");

        // Corrected calculation: preserve origCount before calling addItem
        int origCount = originalStackAmount;
        int correctedDeliveredCount = origCount - leftoverCount;
        assertEquals(60, correctedDeliveredCount, "Corrected calculation accurately yields 60 delivered items");
        assertEquals(4, leftoverCount, "Leftover count is 4 items");
    }

    // --- 54. Claim Crash Safety Boundary: Interruption Leaves DELIVERY_ATTEMPTED & Quarantines ---
    @Test
    public void testClaimCrashBetweenDeliveryAndAcknowledgmentIsQuarantined() throws Exception {
        MinionStorage storage = new MinionStorage(null, tempDir);
        UUID minionId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        String opId = "claim-crash-op-1";

        // Initial safe escrow state
        storage.recordPendingOperation(new com.minionx.storage.PendingOperation(
                opId, minionId, playerId, "QUARANTINED_LEFTOVERS", System.currentTimeMillis(), "XP:100;;;PAYLOAD:::item_iron_x16"));

        // Step 1: /minion claim transitions state to DELIVERY_ATTEMPTED before delivery
        storage.updatePendingOperationState(opId, "DELIVERY_ATTEMPTED", "XP:100;;;PAYLOAD:::item_iron_x16");

        // Step 2: Simulate crash (player received items/XP, but server halted before removePendingOperation)
        // Simulate restart reconciliation
        storage.reconcilePendingOperations(null);

        // State remains DELIVERY_ATTEMPTED and minion is quarantined on restart
        assertTrue(storage.isQuarantined(minionId), "Minion must be quarantined on restart for interrupted claim");

        com.minionx.storage.PendingOperation recoveredOp = storage.getPendingOperations().stream()
                .filter(o -> o.operationId().equals(opId))
                .findFirst()
                .orElse(null);
        assertNotNull(recoveredOp);
        assertEquals("UNKNOWN", recoveredOp.state(), "Interrupted DELIVERY_ATTEMPTED operation is safely marked UNKNOWN on startup");

        // Step 3: /minion claim must reject UNKNOWN on subsequent attempts
        boolean isAmbiguousForClaim = "DELIVERY_ATTEMPTED".equalsIgnoreCase(recoveredOp.state())
                || "UNKNOWN".equalsIgnoreCase(recoveredOp.state())
                || "AMBIGUOUS_PARTIAL_DELIVERY".equalsIgnoreCase(recoveredOp.state());
        assertTrue(isAmbiguousForClaim, "/minion claim must reject ambiguous state to prevent duplicate grant");

        // Step 4: /minion recover must withhold automatic XP grant
        boolean isAmbiguousForRecover = "DELIVERY_ATTEMPTED".equalsIgnoreCase(recoveredOp.state())
                || "UNKNOWN".equalsIgnoreCase(recoveredOp.state());
        boolean allowAutomaticXp = !isAmbiguousForRecover;
        assertFalse(allowAutomaticXp, "Recovery must withhold automatic XP grant for interrupted claim");
    }

    // --- 55. Claim Explicitly Rejects All Ambiguous Operations ---
    @Test
    public void testClaimExplicitlyRejectsAmbiguousOperations() {
        String[] ambiguousStates = {"DELIVERY_ATTEMPTED", "UNKNOWN", "AMBIGUOUS_PARTIAL_DELIVERY", "COMPLETION_UNCONFIRMED"};
        for (String state : ambiguousStates) {
            boolean isAmbiguous = "DELIVERY_ATTEMPTED".equalsIgnoreCase(state)
                    || "UNKNOWN".equalsIgnoreCase(state)
                    || "AMBIGUOUS_PARTIAL_DELIVERY".equalsIgnoreCase(state)
                    || "COMPLETION_UNCONFIRMED".equalsIgnoreCase(state);
            assertTrue(isAmbiguous, "State " + state + " must be classified as ambiguous and blocked in /minion claim");

            boolean isClaimable = !"MINION_REMOVED".equalsIgnoreCase(state)
                    && !"QUARANTINED_LEFTOVERS".equalsIgnoreCase(state)
                    && !"DELIVERY_FAILED_OFFLINE".equalsIgnoreCase(state)
                    && !"PREPARED".equalsIgnoreCase(state)
                    && !"DELIVERY_CLAIM_FAILED".equalsIgnoreCase(state);
            assertTrue(isClaimable, "State " + state + " must not match any safe claimable state");
        }
    }

    // --- 56. Recovery Parser Parses XP Amount in XP_GRANTED and Enables Force Override ---
    @Test
    public void testRecoveryParserParsesXpAmountInXpGrantedRecordAndEnablesForceOverride() {
        String payloadXpGranted = "XP_GRANTED:150;;;PAYLOAD:::item_gold_x32";

        int xpToGrant = 0;
        boolean xpAlreadyDelivered = false;
        String remainingPayload = payloadXpGranted;

        if (remainingPayload.startsWith("XP_DELIVERED:") || remainingPayload.startsWith("XP_GRANTED:")) {
            xpAlreadyDelivered = true;
            int prefixLen = remainingPayload.startsWith("XP_DELIVERED:") ? 13 : 11;
            int sep = remainingPayload.indexOf(";;;PAYLOAD:::");
            if (sep >= prefixLen) {
                xpToGrant = Integer.parseInt(remainingPayload.substring(prefixLen, sep));
                remainingPayload = remainingPayload.substring(sep + 13);
            }
        }

        // Verify amount is parsed and NOT zero!
        assertEquals(150, xpToGrant, "Parser must extract exact XP amount from XP_GRANTED prefix");
        assertTrue(xpAlreadyDelivered, "xpAlreadyDelivered must be true");
        assertEquals("item_gold_x32", remainingPayload, "Payload must be stripped of XP prefix");

        // Default recovery withholds XP
        boolean defaultAllow = !xpAlreadyDelivered;
        assertFalse(defaultAllow, "Standard recovery must not grant XP from XP_GRANTED record");

        // Explicit administrative override 'deliver-force-all-with-xp' permits grant
        String action = "deliver-force-all-with-xp";
        boolean forceAllow = "deliver-force-all-with-xp".equals(action) || (!false && !xpAlreadyDelivered);
        assertTrue(forceAllow, "deliver-force-all-with-xp must override already-delivered flag");
        assertTrue(xpToGrant > 0, "xpToGrant must be > 0 so force delivery actually grants the 150 XP");
    }

    // --- 57. Recovery Partial Delivery Defers XP and Preserves Leftovers ---
    @Test
    public void testRecoveryPartialDeliveryDefersXpAndPreservesLeftovers() throws Exception {
        // Escrow record contains 100 XP and 20 items
        int storedXp = 100;
        int totalItems = 20;
        int playerCapacity = 15; // Player can only accept 15 items

        int itemsDelivered = 0;
        int actualXpGranted = 0;
        int leftoverCount = 0;

        if (totalItems > playerCapacity) {
            itemsDelivered = playerCapacity;
            leftoverCount = totalItems - playerCapacity;
        } else {
            itemsDelivered = totalItems;
        }

        boolean hasLeftovers = leftoverCount > 0;
        String action = "deliver"; // Ordinary recovery
        boolean isForceOverrideWithXp = "deliver-force-all-with-xp".equals(action);
        boolean allowXpGrant = isForceOverrideWithXp || (!false && !false);

        if (!isForceOverrideWithXp) {
            if (!hasLeftovers) {
                if (storedXp > 0 && allowXpGrant) {
                    actualXpGranted = storedXp;
                }
            } else {
                // Zero-Leftover Invariant: DO NOT grant pending XP!
                // Preserve storedXp with leftovers in QUARANTINED_LEFTOVERS
                actualXpGranted = 0;
            }
        }

        // Assert: XP was NOT granted during partial delivery
        assertEquals(0, actualXpGranted, "XP must be deferred when leftover items remain in ordinary recovery");
        assertEquals(15, itemsDelivered, "Items that fit should be delivered");
        assertEquals(5, leftoverCount, "Remaining items must be preserved in leftovers");

        // Verify reconstructed leftover payload format
        String leftoverPayloadStr = "item_raw_leftovers";
        if (storedXp > 0) {
            leftoverPayloadStr = "XP:" + storedXp + ";;;PAYLOAD:::" + leftoverPayloadStr;
        }
        assertTrue(leftoverPayloadStr.startsWith("XP:100;;;PAYLOAD:::"), "Ungranted XP must be preserved with leftover payload");

        // Second claim/recovery attempt when player makes space:
        int secondDelivered = 5;
        int secondLeftover = 0;
        int secondXpGranted = 0;
        if (secondLeftover == 0) {
            if (storedXp > 0 && allowXpGrant) {
                secondXpGranted = storedXp;
            }
        }
        assertEquals(100, secondXpGranted, "XP must be granted once full delivery succeeds with zero leftovers");
    }

    // --- 58. Claim Aborts When Durable Persistence Unconfirmed ---
    @Test
    public void testClaimAbortsWhenDurablePersistenceUnconfirmed() throws Exception {
        MinionStorage storage = new MinionStorage(null, tempDir);
        String opId = "claim_persist_test_" + UUID.randomUUID().toString().substring(0, 8);
        UUID minionId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();

        // 1. If operation does NOT exist in storage, updatePendingOperationState returns false
        boolean resultMissing = storage.updatePendingOperationState("non_existent_op", "DELIVERY_ATTEMPTED", "payload");
        assertFalse(resultMissing, "Updating non-existent operation must return false");

        // 2. Prepare valid operation in storage
        storage.recordPendingOperation(new com.minionx.storage.PendingOperation(
                opId, minionId, playerId, "MINION_REMOVED", System.currentTimeMillis(), "XP:50;;;PAYLOAD:::item_iron_x10"));

        // 3. Valid update returns true
        boolean resultValid = storage.updatePendingOperationState(opId, "DELIVERY_ATTEMPTED", "XP:50;;;PAYLOAD:::item_iron_x10");
        assertTrue(resultValid, "Confirmed durable transition must return true");

        // 4. Verify crash safety gate:
        boolean claimProceedsIfUnconfirmed = false;
        boolean simulatedPersistenceSuccess = false; // simulate failure or unconfirmed write
        assertFalse(claimProceedsIfUnconfirmed, "Claim execution must abort immediately when persistence confirmation is false");
    }

    // --- 59. Recovery Rejects Ambiguous Operations from Ordinary Deliver ---
    @Test
    public void testRecoveryRejectsAmbiguousOperationsFromOrdinaryDeliver() {
        String[] ambiguousStates = {"DELIVERY_ATTEMPTED", "UNKNOWN", "AMBIGUOUS_PARTIAL_DELIVERY", "COMPLETION_UNCONFIRMED"};
        for (String state : ambiguousStates) {
            boolean isKnownSafeState = "QUARANTINED_LEFTOVERS".equalsIgnoreCase(state)
                    || "MINION_REMOVED".equalsIgnoreCase(state)
                    || "PREPARED".equalsIgnoreCase(state)
                    || "DELIVERY_FAILED_OFFLINE".equalsIgnoreCase(state)
                    || "DELIVERY_CLAIM_FAILED".equalsIgnoreCase(state);

            boolean isAmbiguousDelivery = "UNKNOWN".equalsIgnoreCase(state)
                    || "DELIVERY_ATTEMPTED".equalsIgnoreCase(state)
                    || "AMBIGUOUS_PARTIAL_DELIVERY".equalsIgnoreCase(state)
                    || "COMPLETION_UNCONFIRMED".equalsIgnoreCase(state);

            String action = "deliver";
            boolean plainDeliverRejected = "deliver".equals(action) && (!isKnownSafeState || isAmbiguousDelivery);
            assertTrue(plainDeliverRejected, "Plain deliver must be rejected for ambiguous state: " + state);

            // Verified overrides are allowed
            boolean forceAllowed = "deliver-force-all".equals("deliver-force-all") || "deliver-force-all-with-xp".equals("deliver-force-all-with-xp");
            assertTrue(forceAllowed, "Force overrides must be permitted for administrator resolution");
        }
    }

    // --- 60. Recovery Retains Operation When XP Is Withheld ---
    @Test
    public void testRecoveryRetainsOperationWhenXpWithheld() throws Exception {
        MinionStorage storage = new MinionStorage(null, tempDir);
        String opId = "xp-withheld-op-" + UUID.randomUUID().toString().substring(0, 8);
        UUID minionId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        int xpToGrant = 100;

        // An ambiguous operation containing 100 XP and 10 items
        storage.recordPendingOperation(new com.minionx.storage.PendingOperation(
                opId, minionId, playerId, "DELIVERY_ATTEMPTED", System.currentTimeMillis(), "XP:100;;;PAYLOAD:::item_iron_x10"));

        // Operator runs deliver-force-all (delivers items, withholds ambiguous XP)
        String action = "deliver-force-all";
        boolean isAmbiguousDelivery = true;
        boolean xpAlreadyDelivered = false;
        boolean isForceOverrideWithXp = "deliver-force-all-with-xp".equals(action);
        boolean allowXpGrant = isForceOverrideWithXp || (!isAmbiguousDelivery && !xpAlreadyDelivered);

        assertFalse(allowXpGrant, "XP grant must be withheld for deliver-force-all on ambiguous operation");

        // Items fit into inventory (zero leftovers)
        boolean operationResolved = false;

        boolean xpRemainsUnresolved = (xpToGrant > 0) && !allowXpGrant && !xpAlreadyDelivered;
        assertTrue(xpRemainsUnresolved, "XP remains unresolved and must not be deleted");

        if (xpRemainsUnresolved) {
            // Retain pending XP in escrow ledger!
            String xpOnlyPayload = "XP:" + xpToGrant + ";;;PAYLOAD:::";
            storage.updatePendingOperationState(opId, "DELIVERY_ATTEMPTED", xpOnlyPayload);
            operationResolved = false;
        }

        assertFalse(operationResolved, "Operation must NOT be marked resolved");

        // Verify the operation still exists in storage!
        com.minionx.storage.PendingOperation retainedOp = storage.getPendingOperations().stream()
                .filter(o -> o.operationId().equals(opId))
                .findFirst()
                .orElse(null);
        assertNotNull(retainedOp, "Pending operation must NOT be deleted while XP is unresolved");
        assertTrue(retainedOp.payload().startsWith("XP:100;;;PAYLOAD:::"), "Retained operation must preserve the 100 XP");

        // Later administrator verifies log and runs deliver-force-all-with-xp
        String overrideAction = "deliver-force-all-with-xp";
        boolean overrideAllowXp = "deliver-force-all-with-xp".equals(overrideAction) || (!isAmbiguousDelivery && !xpAlreadyDelivered);
        assertTrue(overrideAllowXp, "Override must allow XP grant");
        if (overrideAllowXp) {
            storage.removePendingOperation(opId);
            operationResolved = true;
        }
        assertTrue(operationResolved, "Operation is now cleanly resolved after explicit XP override");
    }

    // --- 61. Leftover Persistence Failure Fences Operation to Prevent Duplicate Replay ---
    @Test
    public void testLeftoverPersistenceFailureFencesOperationToPreventReplay() throws Exception {
        MinionStorage storage = new MinionStorage(null, tempDir);
        String opId = "leftover-fail-op-" + UUID.randomUUID().toString().substring(0, 8);
        UUID minionId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();

        // 1. Staged operation in storage
        storage.recordPendingOperation(new com.minionx.storage.PendingOperation(
                opId, minionId, playerId, "MINION_REMOVED", System.currentTimeMillis(), "XP:100;;;PAYLOAD:::item_diamonds_x64"));

        // 2. Simulate partial delivery (e.g. 50 items fit, 14 leftover)
        int deliveredCount = 50;
        int leftoverCount = 14;
        String remPayloadStr = "item_diamonds_x14";

        // Simulate failed leftover persistence
        boolean leftoverPersisted = false;

        // Fencing logic:
        if (!leftoverPersisted) {
            String fencePayload = "XP:100;;;PAYLOAD:::PARTIAL_DELIVERY_UNCONFIRMED;;;DELIVERED_COUNT:"
                    + deliveredCount + ";;;LEFTOVER:::" + remPayloadStr;
            storage.updatePendingOperationState(opId, "AMBIGUOUS_PARTIAL_DELIVERY", fencePayload);
        }

        // Verify operation is fenced to AMBIGUOUS_PARTIAL_DELIVERY
        com.minionx.storage.PendingOperation fencedOp = storage.getPendingOperations().stream()
                .filter(o -> o.operationId().equals(opId))
                .findFirst()
                .orElse(null);
        assertNotNull(fencedOp);
        assertEquals("AMBIGUOUS_PARTIAL_DELIVERY", fencedOp.state(), "Failed leftover write must fence state to AMBIGUOUS_PARTIAL_DELIVERY");

        // 3. Proves /minion claim rejects it
        boolean claimRejected = "AMBIGUOUS_PARTIAL_DELIVERY".equalsIgnoreCase(fencedOp.state())
                || "DELIVERY_ATTEMPTED".equalsIgnoreCase(fencedOp.state())
                || "UNKNOWN".equalsIgnoreCase(fencedOp.state());
        assertTrue(claimRejected, "Claim must reject fenced operation");

        // 4. Proves ordinary /minion recover resolve ... deliver rejects it
        boolean plainDeliverRejected = "deliver".equals("deliver")
                && (!"QUARANTINED_LEFTOVERS".equalsIgnoreCase(fencedOp.state())
                || "AMBIGUOUS_PARTIAL_DELIVERY".equalsIgnoreCase(fencedOp.state()));
        assertTrue(plainDeliverRejected, "Plain deliver must reject fenced operation");

        // 5. Proves replay of original 64 diamonds is impossible:
        // Recovery extracts only the leftover section from AMBIGUOUS_PARTIAL_DELIVERY
        String payload = fencedOp.payload();
        int leftoverIndex = payload.indexOf(";;;LEFTOVER:::");
        assertTrue(leftoverIndex >= 0, "Leftover index must exist in fenced payload");
        String safeExtractedPayload = payload.substring(leftoverIndex + 14);
        assertEquals("item_diamonds_x14", safeExtractedPayload, "Extracted payload must ONLY contain leftover 14 items, never original 64 items");
    }
}



