package com.minionx;

import com.minionx.core.MinionManager;
import com.minionx.storage.DatabaseStorage;
import com.minionx.storage.MinionStorage;
import com.minionx.storage.PendingOperation;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.sql.Connection;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class PickupInterruptionTest {

    private File tempDir;

    @BeforeEach
    public void setUp() throws Exception {
        tempDir = File.createTempFile("minionx_test_dir_", "");
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

    // --- YAML Engine Acceptance Tests ---

    @Test
    public void testYamlPickupInterruptionQuarantinesOnRestart() throws Exception {
        MinionStorage storage = new MinionStorage(null, tempDir);
        UUID minionId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        // 1. Create and populate minion in minions.yml
        File dataFile = new File(tempDir, "data/minions.yml");
        dataFile.getParentFile().mkdirs();
        YamlConfiguration config = new YamlConfiguration();
        config.set("minions." + minionId + ".owner", ownerId.toString());
        config.set("minions." + minionId + ".type", "DIAMOND");
        config.set("minions." + minionId + ".tier", 5);
        config.set("minions." + minionId + ".world", "world");
        config.set("minions." + minionId + ".x", 10.0);
        config.set("minions." + minionId + ".y", 64.0);
        config.set("minions." + minionId + ".z", 10.0);
        config.save(dataFile);

        // 2. Interruption: durable intent commits, then abrupt crash/kill occurs
        String operationId = UUID.randomUUID().toString();
        PendingOperation intent = new PendingOperation(
                operationId,
                minionId,
                ownerId,
                "PICKUP_PENDING",
                System.currentTimeMillis()
        );
        storage.recordPendingOperation(intent);

        // Verify pending operation written to file
        File opsFile = new File(tempDir, "data/pending_operations.yml");
        assertTrue(opsFile.exists());

        // 3. Simulate Server Restart: re-instantiate storage with clean runtime
        MinionStorage restartStorage = new MinionStorage(null, tempDir);
        List<PendingOperation> pending = restartStorage.getPendingOperations();
        assertEquals(1, pending.size());
        assertEquals(operationId, pending.getFirst().operationId());
        assertEquals(minionId, pending.getFirst().minionId());
        assertEquals("PICKUP_PENDING", pending.getFirst().state());

        // 4. Verify quarantined minion is not registered into active manager
        MinionManager restartManager = new MinionManager();
        restartStorage.loadAll(restartManager);
        assertNull(restartManager.getMinionById(minionId), "Interrupted minion must be quarantined from runtime");
        assertTrue(restartManager.getAllMinions().isEmpty());
    }

    @Test
    public void testYamlAtomicFinalizationSafelyDeletesTargetOperationAndMinion() throws Exception {
        MinionStorage storage = new MinionStorage(null, tempDir);
        UUID minionA = UUID.randomUUID();
        UUID minionB = UUID.randomUUID();
        UUID owner = UUID.randomUUID();

        // 1. Populate minions.yml with Minion A and Minion B
        File dataFile = new File(tempDir, "data/minions.yml");
        dataFile.getParentFile().mkdirs();
        YamlConfiguration config = new YamlConfiguration();
        config.set("minions." + minionA + ".type", "DIAMOND");
        config.set("minions." + minionA + ".tier", 11);
        config.set("minions." + minionB + ".type", "IRON");
        config.set("minions." + minionB + ".tier", 9);
        config.save(dataFile);

        // 2. Two concurrent pending pickup operations recorded
        String opA = "op-A-" + UUID.randomUUID();
        String opB = "op-B-" + UUID.randomUUID();
        storage.recordPendingOperation(new PendingOperation(opA, minionA, owner, "PICKUP_PENDING", System.currentTimeMillis()));
        storage.recordPendingOperation(new PendingOperation(opB, minionB, owner, "PICKUP_PENDING", System.currentTimeMillis()));
        assertEquals(2, storage.getPendingOperations().size());

        // 3. Finalize pickup of Minion A
        boolean finalized = storage.finalizePickup(opA, minionA);
        assertTrue(finalized);

        // 4. Verify Minion A and opA deleted, while Minion B and opB remain completely intact!
        YamlConfiguration updatedConfig = YamlConfiguration.loadConfiguration(dataFile);
        assertFalse(updatedConfig.contains("minions." + minionA), "Minion A must be deleted");
        assertTrue(updatedConfig.contains("minions." + minionB), "Minion B must still exist");

        List<PendingOperation> remaining = storage.getPendingOperations();
        assertEquals(1, remaining.size());
        assertEquals(opB, remaining.getFirst().operationId());
        assertEquals(minionB, remaining.getFirst().minionId());
    }

    @Test
    public void testYamlCompensatingRollbackRemovesPendingOperation() throws Exception {
        MinionStorage storage = new MinionStorage(null, tempDir);
        UUID minionId = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        String opId = UUID.randomUUID().toString();

        storage.recordPendingOperation(new PendingOperation(opId, minionId, owner, "PICKUP_PENDING", System.currentTimeMillis()));
        assertEquals(1, storage.getPendingOperations().size());

        // Compensating rollback when item transfer fails
        storage.removePendingOperation(opId);
        assertTrue(storage.getPendingOperations().isEmpty(), "Pending operation must be removed after compensating rollback");
    }

    // --- SQL Engine Acceptance Tests with Transaction Atomicity ---

    @Test
    public void testSqlAtomicFinalizationWithTransaction() throws Exception {
        MockSqlHelper.State state = new MockSqlHelper.State();
        Connection connection = MockSqlHelper.createMockConnection(state);
        DatabaseStorage dbStorage = new DatabaseStorage(null);
        dbStorage.setConnection(connection);

        String opId = "op-" + UUID.randomUUID();
        UUID minionId = UUID.randomUUID();

        boolean success = dbStorage.finalizePickup(opId, minionId);
        assertTrue(success);

        // Verify transaction boundaries
        assertTrue(state.transactionEvents.contains("setAutoCommit:false"), "Transaction must be started with setAutoCommit(false)");
        assertTrue(state.transactionEvents.contains("commit"), "Transaction must commit on success");
        assertTrue(state.transactionEvents.contains("setAutoCommit:true"), "AutoCommit must be restored");

        // Verify parameterized DELETE queries were prepared and executed with exact IDs
        boolean foundOpDelete = false;
        boolean foundItemsDelete = false;
        boolean foundMinionDelete = false;

        for (MockSqlHelper.PreparedStatementCall call : state.preparedCalls) {
            if (call.sql.contains("DELETE FROM minion_pending_operations WHERE operation_id = ?")) {
                foundOpDelete = true;
                assertEquals(opId, call.parameters.get(1));
            }
            if (call.sql.contains("DELETE FROM minion_items WHERE minion_id = ?")) {
                foundItemsDelete = true;
                assertEquals(minionId.toString(), call.parameters.get(1));
            }
            if (call.sql.contains("DELETE FROM minions WHERE id = ?")) {
                foundMinionDelete = true;
                assertEquals(minionId.toString(), call.parameters.get(1));
            }
        }

        assertTrue(foundOpDelete, "Must execute parameterized DELETE for operation_id");
        assertTrue(foundItemsDelete, "Must execute parameterized DELETE for minion_items");
        assertTrue(foundMinionDelete, "Must execute parameterized DELETE for minions");
    }

    @Test
    public void testSqlFinalizePickupRollsBackOnSqlException() throws Exception {
        MockSqlHelper.State state = new MockSqlHelper.State();
        state.throwOnExecute = true; // Trigger failure during delete execution
        Connection connection = MockSqlHelper.createMockConnection(state);
        DatabaseStorage dbStorage = new DatabaseStorage(null);
        dbStorage.setConnection(connection);

        String opId = "op-fail-" + UUID.randomUUID();
        UUID minionId = UUID.randomUUID();

        boolean success = dbStorage.finalizePickup(opId, minionId);
        assertFalse(success, "Must return false on database error");

        // Verify rollback was triggered
        assertTrue(state.transactionEvents.contains("rollback"), "Transaction must rollback on SQL error");
        assertTrue(state.transactionEvents.contains("setAutoCommit:true"), "AutoCommit must be restored after error");
    }

    @Test
    public void testSqlCompensatingRollbackRemovesPendingOperation() throws Exception {
        MockSqlHelper.State state = new MockSqlHelper.State();
        Connection connection = MockSqlHelper.createMockConnection(state);
        DatabaseStorage dbStorage = new DatabaseStorage(null);
        dbStorage.setConnection(connection);

        String opId = "op-compensate-" + UUID.randomUUID();
        dbStorage.removePendingOperation(opId);

        boolean foundDelete = false;
        for (MockSqlHelper.PreparedStatementCall call : state.preparedCalls) {
            if (call.sql.contains("DELETE FROM minion_pending_operations WHERE operation_id = ?")) {
                foundDelete = true;
                assertEquals(opId, call.parameters.get(1));
            }
        }
        assertTrue(foundDelete, "Must execute parameterized delete for compensating rollback");
    }

    @Test
    public void testSqlQuarantinesPendingOperationsOnLoad() throws Exception {
        MockSqlHelper.State state = new MockSqlHelper.State();
        String opId = UUID.randomUUID().toString();
        UUID minionId = UUID.randomUUID();
        UUID playerUuid = UUID.randomUUID();

        state.pendingOperations.add(new PendingOperation(opId, minionId, playerUuid, "PICKUP_PENDING", 1700000000000L));

        Connection connection = MockSqlHelper.createMockConnection(state);
        DatabaseStorage dbStorage = new DatabaseStorage(null);
        dbStorage.setConnection(connection);

        List<PendingOperation> pending = dbStorage.getPendingOperations();
        assertEquals(1, pending.size());
        assertEquals(opId, pending.getFirst().operationId());
        assertEquals(minionId, pending.getFirst().minionId());
        assertEquals(playerUuid, pending.getFirst().playerUuid());
        assertEquals("PICKUP_PENDING", pending.getFirst().state());
    }
}
