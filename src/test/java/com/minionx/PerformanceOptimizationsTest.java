package com.minionx;

import com.minionx.api.MinionType;
import com.minionx.api.MinionUpgradeType;
import com.minionx.core.CompressionManager;
import com.minionx.core.Minion;
import com.minionx.core.MinionManager;
import com.minionx.storage.DatabaseStorage;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class PerformanceOptimizationsTest {

    @Test
    public void testActiveLoadedMinionsRegistrationAndUnregistration() {
        MinionManager manager = new MinionManager();
        Minion minion = new Minion(UUID.randomUUID(), UUID.randomUUID(), MinionType.DIAMOND, 5, null);

        manager.register(minion);
        assertTrue(manager.getAllMinions().contains(minion));
        // Minions without world/chunk automatically register as active loaded minions
        assertTrue(manager.getActiveLoadedMinions().contains(minion));

        manager.unregister(minion);
        assertFalse(manager.getAllMinions().contains(minion));
        assertFalse(manager.getActiveLoadedMinions().contains(minion));
    }

    @Test
    public void testFastMaterialRecipeInvertedIndex() {
        // Test Compactor recipes inverted index
        List<CompressionManager.CompressionRecipe> redstoneCompactorRecipes =
                CompressionManager.getCompactorRecipesForMaterial(Material.REDSTONE);
        assertNotNull(redstoneCompactorRecipes);
        assertFalse(redstoneCompactorRecipes.isEmpty());
        assertEquals(Material.REDSTONE, redstoneCompactorRecipes.getFirst().getInputMaterial());

        // Test Compactor recipes inverted index for DIAMOND
        List<CompressionManager.CompressionRecipe> diamondRecipes =
                CompressionManager.getCompactorRecipesForMaterial(Material.DIAMOND);
        assertNotNull(diamondRecipes);
        assertFalse(diamondRecipes.isEmpty());
        assertEquals(Material.DIAMOND, diamondRecipes.getFirst().getInputMaterial());

        // Test non-compactible material yields empty list
        List<CompressionManager.CompressionRecipe> bedrockRecipes =
                CompressionManager.getCompactorRecipesForMaterial(Material.BEDROCK);
        assertTrue(bedrockRecipes == null || bedrockRecipes.isEmpty());
    }

    @Test
    public void testCombatEntityTrackingSet() {
        Minion minion = new Minion(UUID.randomUUID(), UUID.randomUUID(), MinionType.IRON, 1, null);
        assertNotNull(minion.getTrackedSpawnedMobs());
        assertTrue(minion.getTrackedSpawnedMobs().isEmpty());

        UUID mob1 = UUID.randomUUID();
        UUID mob2 = UUID.randomUUID();
        minion.getTrackedSpawnedMobs().add(mob1);
        minion.getTrackedSpawnedMobs().add(mob2);

        assertEquals(2, minion.getTrackedSpawnedMobs().size());
        assertTrue(minion.getTrackedSpawnedMobs().contains(mob1));
        assertTrue(minion.getTrackedSpawnedMobs().contains(mob2));

        minion.getTrackedSpawnedMobs().remove(mob1);
        assertEquals(1, minion.getTrackedSpawnedMobs().size());
        assertFalse(minion.getTrackedSpawnedMobs().contains(mob1));
    }

    @Test
    public void testDatabaseSnapshotCreation() {
        UUID minionId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Minion minion = new Minion(minionId, ownerId, MinionType.IRON, 7, null);
        minion.setFuel("solar_panel");
        minion.setFuelEndTime(99999999L);
        minion.setUpgrade1(MinionUpgradeType.AUTO_SMELTER);
        minion.setTotalHarvested(1500);
        minion.setStoredXp(42);

        DatabaseStorage.MinionSnapshot snapshot = DatabaseStorage.MinionSnapshot.fromMinion(minion);
        assertNotNull(snapshot);
        assertEquals(minionId, snapshot.id());
        assertEquals(ownerId, snapshot.ownerId());
        assertEquals("IRON", snapshot.type());
        assertEquals(7, snapshot.tier());
        assertEquals("solar_panel", snapshot.fuelId());
        assertEquals(99999999L, snapshot.fuelEndTime());
        assertEquals("auto_smelter", snapshot.upgrade1Id());
        assertEquals(1500, snapshot.totalHarvested());
        assertEquals(42, snapshot.storedXp());
        assertNotNull(snapshot.serializedItems());
        assertTrue(snapshot.serializedItems().isEmpty());
    }

    @Test
    public void testSchedulerTelemetryPercentileCalculation() {
        com.minionx.core.SchedulerTelemetry telemetry = new com.minionx.core.SchedulerTelemetry();

        // Feed 100 sample ticks between 0.5ms and 3.0ms
        for (int i = 1; i <= 100; i++) {
            telemetry.recordTick(i * 20_000L, 1, 0); // 0.02ms to 2.0ms
        }

        double p95 = telemetry.getP95DurationMillis();
        double p99 = telemetry.getP99DurationMillis();
        double avg = telemetry.getAverageDurationMillis();

        assertTrue(p95 > 1.8 && p95 <= 2.0, "p95 must be accurately bounded");
        assertTrue(p99 > 1.9 && p99 <= 2.0, "p99 must be accurately bounded");
        assertTrue(avg > 0.9 && avg < 1.1, "average must match mean");

        telemetry.recordActionDuration(450_000L); // 0.45 ms
        assertEquals(0.45, telemetry.getLongestActionMillis(), 0.001);
        assertEquals(100L, telemetry.getTotalActionsExecuted());
    }

    @Test
    public void testFullSleepingMinionShortCircuitAndWakeup() {
        Minion minion = new Minion(UUID.randomUUID(), UUID.randomUUID(), MinionType.COBBLESTONE, 1, null);
        assertFalse(minion.isFullSleeping());

        minion.setState(com.minionx.api.MinionState.FULL);
        minion.sleepFull();
        assertTrue(minion.isFullSleeping());

        // MinionTickTask fast-path: returns false immediately without performing actions
        MinionManager manager = new MinionManager();
        com.minionx.core.MinionTickTask tickTask = new com.minionx.core.MinionTickTask(manager);
        boolean actionExecuted = tickTask.tickMinion(minion);
        assertFalse(actionExecuted, "Sleeping full minion must short-circuit without execution");

        // Wake up minion
        minion.wakeUp();
        assertFalse(minion.isFullSleeping(), "Minion must be awake after wakeUp call");
    }

    @Test
    public void testOverloadLatencySheddingClampsCatchup() {
        Minion minion = new Minion(UUID.randomUUID(), UUID.randomUUID(), MinionType.IRON, 5, null);
        MinionManager manager = new MinionManager();
        com.minionx.core.MinionTickTask tickTask = new com.minionx.core.MinionTickTask(manager);

        long now = System.currentTimeMillis();
        long delayMillis = (long) (minion.getEffectiveActionDelay() * 1000L);

        // Simulate severe 30-second server freeze/lag
        minion.setLastActionTime(now - 30_000L);

        // Execute tick: should clamp overdue delay rather than attempting 15 consecutive actions
        tickTask.tickMinion(minion);

        long overdue = now - minion.getLastActionTime();
        assertTrue(overdue <= delayMillis + 100L, "Overdue time must be clamped to shed excessive lag");
        assertTrue(tickTask.getTelemetry().getTotalShedActions() >= 1, "Must record shed action telemetry");
    }

    @Test
    public void testGenerationTokenInvalidation() {
        Minion minion = new Minion(UUID.randomUUID(), UUID.randomUUID(), MinionType.DIAMOND, 7, null);
        long gen1 = minion.getSchedulingGeneration();

        minion.advanceSchedulingGeneration();
        long gen2 = minion.getSchedulingGeneration();

        assertNotEquals(gen1, gen2);
        assertEquals(gen1 + 1, gen2);
    }
}
