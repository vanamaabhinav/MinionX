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
import java.sql.Connection;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class PersistenceOrderingAndBarrierTest {

    private File tempDir;
    private MinionStorage minionStorage;

    @BeforeEach
    public void setUp() throws Exception {
        tempDir = File.createTempFile("persistence_test_dir_", "");
        tempDir.delete();
        tempDir.mkdirs();
        minionStorage = new MinionStorage(null, tempDir);
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (minionStorage != null) {
            minionStorage.close();
        }
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

    private DatabaseStorage.MinionSnapshot createSnapshot(UUID id, int tier, double coins) {
        return new DatabaseStorage.MinionSnapshot(
                id,
                UUID.randomUUID(),
                "DIAMOND",
                tier,
                "world",
                10.0, 64.0, 10.0,
                0f, 0f,
                null, 0L,
                null, null, null,
                "budget_hopper", coins,
                null, null, null, null, null,
                System.currentTimeMillis(), false,
                100, 25, null,
                Collections.emptyList(),
                Collections.emptyList()
        );
    }

    @Test
    public void testStaleMutationRejectedByStateRevision() {
        UUID minionId = UUID.randomUUID();

        // 1. Submit newer mutation at revision 5
        DatabaseStorage.MinionSnapshot newerSnapshot = createSnapshot(minionId, 5, 50.0);
        minionStorage.submitMutation(newerSnapshot, 5L);

        MinionMutation current = minionStorage.getPendingMutations().get(minionId);
        assertNotNull(current);
        assertEquals(5L, current.stateRevision());
        assertEquals(5, current.snapshot().tier());

        // 2. Submit older mutation at revision 3 (e.g. from delayed thread or out-of-order event)
        DatabaseStorage.MinionSnapshot staleSnapshot = createSnapshot(minionId, 3, 30.0);
        minionStorage.submitMutation(staleSnapshot, 3L);

        // 3. Verify stale mutation was rejected and revision 5 was preserved
        MinionMutation preserved = minionStorage.getPendingMutations().get(minionId);
        assertNotNull(preserved);
        assertEquals(5L, preserved.stateRevision(), "Stale revision 3 must not overwrite newer revision 5");
        assertEquals(5, preserved.snapshot().tier());
        assertEquals(50.0, preserved.snapshot().shippingCoins());
    }

    @Test
    public void testTombstonePreventsStaleReanimation() {
        UUID minionId = UUID.randomUUID();

        // 1. Minion exists at revision 2
        DatabaseStorage.MinionSnapshot s2 = createSnapshot(minionId, 2, 20.0);
        minionStorage.submitMutation(s2, 2L);

        // 2. Minion is picked up or dismantled -> tombstoned at revision 3
        minionStorage.recordTombstone(minionId, 3L);
        MinionMutation tombstone = minionStorage.getPendingMutations().get(minionId);
        assertNotNull(tombstone);
        assertTrue(tombstone.isTombstone());

        // 3. A delayed in-flight autosave tries to submit an old snapshot at revision 2
        minionStorage.submitMutation(s2, 2L);

        // Verify tombstone is still active and minion was NOT reanimated
        MinionMutation afterStale = minionStorage.getPendingMutations().get(minionId);
        assertTrue(afterStale.isTombstone(), "Tombstone must not be overwritten by stale snapshot");
    }

    @Test
    public void testBarrierTaskSuppressesRoutineAutosave() throws Exception {
        UUID minionId = UUID.randomUUID();
        CountDownLatch barrierRunningLatch = new CountDownLatch(1);
        CountDownLatch releaseBarrierLatch = new CountDownLatch(1);

        // 1. Launch barrier task (e.g. pickup transaction) on persistence executor
        CompletableFuture<Void> barrierFuture = minionStorage.executeBarrier(minionId, () -> {
            barrierRunningLatch.countDown();
            try {
                releaseBarrierLatch.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {}
        });

        // Wait until barrier task is actively running
        assertTrue(barrierRunningLatch.await(2, TimeUnit.SECONDS));
        assertTrue(minionStorage.isBarrierActive(minionId), "Barrier must be reported as active");

        // 2. Routine autosave tries to submit mutation while barrier is in flight
        DatabaseStorage.MinionSnapshot snapshot = createSnapshot(minionId, 8, 100.0);
        minionStorage.submitMutation(snapshot, 10L);

        // 3. Verify routine mutation was suppressed and dropped
        assertFalse(minionStorage.getPendingMutations().containsKey(minionId),
                "Routine mutation must be suppressed while minion is locked under barrier task");

        // 4. Release barrier
        releaseBarrierLatch.countDown();
        barrierFuture.get(2, TimeUnit.SECONDS);
        assertFalse(minionStorage.isBarrierActive(minionId));
    }

    @Test
    public void testDocumentSafeYamlMerging() throws Exception {
        File dataFile = new File(tempDir, "data/minions.yml");
        dataFile.getParentFile().mkdirs();

        UUID minionA = UUID.randomUUID();
        UUID minionB = UUID.randomUUID();

        // 1. Initial file on disk contains Minion A and Minion B
        YamlConfiguration initialConfig = new YamlConfiguration();
        initialConfig.set("minions." + minionA + ".type", "DIAMOND");
        initialConfig.set("minions." + minionA + ".tier", 1);
        initialConfig.set("minions." + minionB + ".type", "IRON");
        initialConfig.set("minions." + minionB + ".tier", 1);
        initialConfig.save(dataFile);

        // 2. Submit mutation updating Minion A to tier 10, and tombstone for Minion B
        DatabaseStorage.MinionSnapshot snapA = createSnapshot(minionA, 10, 250.0);
        minionStorage.submitMutation(snapA, 5L);
        minionStorage.recordTombstone(minionB, 5L);

        // 3. Flush mutations synchronously to YAML
        minionStorage.flushPendingMutationsSync();

        // 4. Reload and verify document-safe merging:
        YamlConfiguration reloaded = YamlConfiguration.loadConfiguration(dataFile);
        assertEquals(10, reloaded.getInt("minions." + minionA + ".tier"), "Minion A must be upgraded to tier 10");
        assertFalse(reloaded.contains("minions." + minionB), "Tombstoned Minion B must be removed from document");
    }

    @Test
    public void testOnlineSQLiteBackupAndIntegrityCheck() {
        MockSqlHelper.State state = new MockSqlHelper.State();
        Connection connection = MockSqlHelper.createMockConnection(state);
        DatabaseStorage dbStorage = new DatabaseStorage(null);
        dbStorage.setConnection(connection);

        File backupFile = new File(tempDir, "backup/minions_backup.db");
        boolean success = dbStorage.createBackup(backupFile);
        assertTrue(success);

        // Verify that VACUUM INTO was issued to SQLite connection
        boolean foundVacuum = state.executedStatements.stream()
                .anyMatch(s -> s.contains("VACUUM INTO"));
        assertTrue(foundVacuum, "Must execute VACUUM INTO statement for online hot backup");
    }

    @Test
    public void testCurrencyMicroMathParity() {
        Minion minion = new Minion(UUID.randomUUID(), UUID.randomUUID(), MinionType.DIAMOND, 5, null);

        // Perform multiple fractional currency additions
        minion.addShippingCoins(0.10);
        minion.addShippingCoins(0.20);
        minion.addShippingCoins(0.05);

        // Standard floating point math would produce 0.35000000000000003
        // Canonical fixed-point micro-math guarantees exact 0.35 and 350,000 micros
        assertEquals(350_000L, minion.getShippingCoinsMicros());
        assertEquals(0.35, minion.getShippingCoins(), 0.0000001);

        // Claiming coins cleanly resets micros to zero
        double claimed = minion.claimShippingCoins();
        assertEquals(0.35, claimed, 0.0000001);
        assertEquals(0L, minion.getShippingCoinsMicros());
        assertEquals(0.0, minion.getShippingCoins(), 0.0000001);
    }
}
