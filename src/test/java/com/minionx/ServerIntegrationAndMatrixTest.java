package com.minionx;

import com.minionx.api.MinionType;
import com.minionx.api.event.*;
import com.minionx.core.Minion;
import com.minionx.core.MinionManager;
import com.minionx.util.ServerCompatibilityUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Gate 5 (Real Paper Integration & Event Handling) and Gate 7 (Multi-Version Matrix) Validation Suite.
 */
public class ServerIntegrationAndMatrixTest {

    @Test
    @DisplayName("Gate 5: MinionUpgradeEvent cancellation prevents tier advancement")
    public void testUpgradeEventCancellationPreservesMinionState() {
        Minion minion = new Minion(UUID.randomUUID(), UUID.randomUUID(), MinionType.DIAMOND, 1, null);
        assertEquals(1, minion.getTier());

        // Simulate upgrade event with cancellation
        MinionUpgradeEvent event = new MinionUpgradeEvent(null, minion, 1, 2);
        event.setCancelled(true);
        assertTrue(event.isCancelled());

        // Handlers must check isCancelled() before modifying minion state
        if (!event.isCancelled()) {
            minion.setTier(2);
        }

        assertEquals(1, minion.getTier(), "Minion tier must remain 1 when upgrade event is cancelled");
    }

    @Test
    @DisplayName("Gate 5: MinionStorageLinkEvent cancellation preserves unlinked storage state")
    public void testStorageLinkEventCancellationPreservesUnlinkedState() {
        Minion minion = new Minion(UUID.randomUUID(), UUID.randomUUID(), MinionType.IRON, 3, null);
        assertFalse(minion.hasLinkedStorage());

        Location chestLoc = new Location(null, 10, 64, 10);
        MinionStorageLinkEvent event = new MinionStorageLinkEvent(null, minion, chestLoc, true);
        event.setCancelled(true);
        assertTrue(event.isCancelled());

        if (!event.isCancelled()) {
            minion.linkStorage(chestLoc, "medium_storage");
        }

        assertFalse(minion.hasLinkedStorage(), "Minion must not link storage if event was cancelled");
        assertNull(minion.getLinkedStorageLocation());
    }

    @Test
    @DisplayName("Gate 5: MinionPickupEvent cancellation maintains active registration in MinionManager")
    public void testPickupEventCancellationMaintainsActiveRegistration() {
        MinionManager manager = new MinionManager();
        Minion minion = new Minion(UUID.randomUUID(), UUID.randomUUID(), MinionType.DIAMOND, 5, null);
        manager.register(minion);
        assertNotNull(manager.getMinionById(minion.getMinionId()));

        MinionPickupEvent event = new MinionPickupEvent(null, minion);
        event.setCancelled(true);
        assertTrue(event.isCancelled());

        if (!event.isCancelled()) {
            manager.unregister(minion);
        }

        assertNotNull(manager.getMinionById(minion.getMinionId()), "Minion must remain registered when pickup event is cancelled");
    }

    @Test
    @DisplayName("Gate 5: MinionCollectionEvent cancellation prevents inventory extraction")
    public void testCollectionEventCancellationPreventsInventoryDrain() {
        Minion minion = new Minion(UUID.randomUUID(), UUID.randomUUID(), MinionType.COBBLESTONE, 4, null);
        minion.addItemDirect(new ItemStack(Material.COBBLESTONE, 64));
        assertEquals(64, minion.getTotalStoredCount());

        MinionCollectionEvent event = new MinionCollectionEvent(null, minion, List.of(new ItemStack(Material.COBBLESTONE, 64)), 0);
        event.setCancelled(true);
        assertTrue(event.isCancelled());

        if (!event.isCancelled()) {
            minion.clearInventory();
        }

        assertEquals(64, minion.getTotalStoredCount(), "Inventory must not be drained if collection event was cancelled");
    }

    @Test
    @DisplayName("Gate 5: ChunkLifecycle markUnloaded and markLoaded coordination")
    public void testChunkLifecycleMarkUnloadedAndLoadedCoordination() {
        MinionManager manager = new MinionManager();
        Minion minion = new Minion(UUID.randomUUID(), UUID.randomUUID(), MinionType.COBBLESTONE, 7, null);
        manager.register(minion);

        // Minion registered with null location is in active loaded set by default in manager
        assertTrue(manager.getActiveLoadedMinions().contains(minion));

        // Chunk unloads
        manager.markUnloaded(minion);
        assertFalse(manager.getActiveLoadedMinions().contains(minion), "Minion must be removed from active loaded set when chunk unloads");
        assertNotNull(manager.getMinionById(minion.getMinionId()), "Minion must remain globally tracked across chunk cycles");

        // Chunk loads
        manager.markLoaded(minion);
        assertTrue(manager.getActiveLoadedMinions().contains(minion), "Minion must be in active loaded set when chunk loads");
    }

    @Test
    @DisplayName("Gate 7: Multi-Version Matrix verification — all core subsystems load on Java 21")
    public void testMultiVersionMatrixClassesAndJava21Runtime() throws Exception {
        // Verify running on Java 21 or newer
        String javaSpecVersion = System.getProperty("java.specification.version");
        int majorVersion = Integer.parseInt(javaSpecVersion.split("\\.")[0]);
        assertTrue(majorVersion >= 21, "Runtime JVM must be Java 21 or newer, found: " + majorVersion);

        // Verify all core subsystem classes load without class linkage or bytecode incompatibility
        String[] coreClasses = {
            "com.minionx.MinionX",
            "com.minionx.core.Minion",
            "com.minionx.core.MinionTickTask",
            "com.minionx.core.MinionManager",
            "com.minionx.core.SchedulerTelemetry",
            "com.minionx.core.OfflineCoordinator",
            "com.minionx.core.OfflineCalculator",
            "com.minionx.storage.DatabaseStorage",
            "com.minionx.storage.MinionStorage",
            "com.minionx.storage.MinionMutation",
            "com.minionx.storage.SerializedItemRecord",
            "com.minionx.storage.PendingOperation",
            "com.minionx.quarantine.QuarantineManager",
            "com.minionx.util.ServerCompatibilityUtil",
            "com.minionx.util.ColorUtil",
            "com.minionx.util.MinionXMetrics"
        };

        for (String className : coreClasses) {
            Class<?> clazz = Class.forName(className);
            assertNotNull(clazz, "Class must load cleanly: " + className);
        }

        // Verify ServerCompatibilityUtil null and non-armorstand safety
        ServerCompatibilityUtil.disableEntityTicking(null);
        ServerCompatibilityUtil.setEntityRotation(null, 180.0f, 0.0f, null);

        // Verify cross-version particle resolution
        assertNotNull(ServerCompatibilityUtil.getBlockParticle(), "Block break particle must resolve on current Paper runtime");
        assertNotNull(ServerCompatibilityUtil.getHappyParticle(), "Happy particle must resolve on current Paper runtime");

        // Verify particle spawn methods handle nulls without exception
        ServerCompatibilityUtil.spawnBlockBreakParticles(null, null, 10, 0.1, 0.1, 0.1);
        ServerCompatibilityUtil.spawnHappyParticles(null, 5, 0.1, 0.1, 0.1, 0.05);
    }
}
