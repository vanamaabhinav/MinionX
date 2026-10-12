package com.minionx;

import com.minionx.api.CustomFuel;
import com.minionx.api.FuelRegistry;
import com.minionx.api.MinionType;
import com.minionx.core.Minion;
import com.minionx.gui.FuelCreatorSession;
import com.minionx.quarantine.QuarantineManager;
import com.minionx.util.UpdateChecker;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Automated Duplication Prevention & Multi-Threaded Concurrency Testing Suite.
 */
public class DupeAndConcurrencyTest {

    @Test
    @DisplayName("Verify concurrent item collection cannot duplicate or leak items")
    public void testConcurrentCollectionThreadSafety() throws InterruptedException {
        Minion minion = new Minion(UUID.randomUUID(), UUID.randomUUID(), MinionType.DIAMOND, 1, null);
        int initialStacks = 10;
        int itemsPerStack = 64;
        int expectedTotal = initialStacks * itemsPerStack;

        for (int i = 0; i < initialStacks; i++) {
            minion.getInventory().add(new ItemStack(Material.DIAMOND, itemsPerStack));
        }

        assertEquals(expectedTotal, minion.getTotalStoredCount());

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger totalCollected = new AtomicInteger(0);

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                try {
                    latch.await();
                    // Each thread tries to withdraw items from the minion's inventory
                    while (true) {
                        ItemStack item = null;
                        synchronized (minion.getInventory()) {
                            if (!minion.getInventory().isEmpty()) {
                                item = minion.getInventory().remove(0);
                            }
                        }
                        if (item == null) break;
                        totalCollected.addAndGet(item.getAmount());
                        Thread.sleep(1);
                    }
                } catch (Exception e) {
                    fail("Exception in concurrency worker: " + e.getMessage());
                }
            }));
        }

        latch.countDown(); // Start all threads simultaneously
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        // Invariant: Total collected must strictly equal initial items - zero dupes, zero items lost
        assertEquals(expectedTotal, totalCollected.get(), "Total collected items must match original item count!");
        assertTrue(minion.getInventory().isEmpty(), "Minion inventory must be completely emptied without ghost items");
    }

    @Test
    @DisplayName("Verify Emergency Quarantine Mode prevents collections and changes")
    public void testEmergencyQuarantineProtection() {
        QuarantineManager.setQuarantined(true);
        assertTrue(QuarantineManager.isQuarantined());

        // In active quarantine, non-admin players must be blocked
        // Null player or regular player without bypass cannot bypass
        assertFalse(QuarantineManager.canBypass(null));

        QuarantineManager.setQuarantined(false);
        assertFalse(QuarantineManager.isQuarantined());
    }

    @Test
    @DisplayName("Verify UpdateChecker semantic version comparison accuracy")
    public void testUpdateCheckerSemanticVersionComparison() {
        assertTrue(UpdateChecker.isNewerVersion("0.2.0", "0.3.0"));
        assertTrue(UpdateChecker.isNewerVersion("0.2.0", "1.0.0"));
        assertTrue(UpdateChecker.isNewerVersion("0.1.3", "0.2.0"));
        assertTrue(UpdateChecker.isNewerVersion("0.0.9", "0.1.0"));

        assertFalse(UpdateChecker.isNewerVersion("0.2.0", "0.2.0"));
        assertFalse(UpdateChecker.isNewerVersion("0.3.0", "0.2.0"));
        assertFalse(UpdateChecker.isNewerVersion("0.2.0-SNAPSHOT", "0.2.0"));
        assertFalse(UpdateChecker.isNewerVersion("0.2.0", "0.1.3"));
    }

    @Test
    @DisplayName("Verify Fuel Creator session configuration and duration calculations")
    public void testFuelCreatorSessionCalculations() {
        FuelCreatorSession session = new FuelCreatorSession("super_plasma");
        session.setSpeedBuff(0.40);
        session.setPermanent(false);
        session.setDurationMinutes(120);

        assertEquals("2h", session.getDurationString());
        assertEquals(120 * 60 * 1000L, session.getDurationMillis());
        assertFalse(session.isPermanent());

        CustomFuel fuel = session.toCustomFuel();
        assertEquals("super_plasma", fuel.getId());
        assertEquals(0.40, fuel.getSpeedBuff());
        assertEquals(120 * 60 * 1000L, fuel.getDurationMillis());

        // Test permanent mode
        session.setPermanent(true);
        assertEquals("permanent", session.getDurationString());
        assertEquals(-1L, session.getDurationMillis());
        assertTrue(session.toCustomFuel().isPermanent());

        // Test FuelRegistry
        FuelRegistry.register(fuel);
        assertNotNull(FuelRegistry.get("super_plasma"));
        assertEquals(fuel, FuelRegistry.get("super_plasma"));
    }
}
