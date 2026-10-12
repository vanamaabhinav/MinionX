package com.minionx;

import com.minionx.api.*;
import com.minionx.core.Minion;
import com.minionx.core.OfflineCalculator;
import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class OfflineCalculatorTest {

    @Test
    @DisplayName("Should simulate correct offline production (1 drop per 2 action delays)")
    void testOfflineProduction() {
        Minion minion = new Minion(UUID.randomUUID(), UUID.randomUUID(), MinionType.COBBLESTONE, 11, null);
        // Tier 11 base delay is 7.0 seconds. 2 actions = 14.0 seconds per drop.

        // Simulate being offline for 140 seconds (should produce exactly 140 / 14 = 10 items)
        minion.setLastActionTime(System.currentTimeMillis() - 140_000L);

        int produced = OfflineCalculator.processOfflineCatchup(minion);
        assertEquals(10, produced);
        assertEquals(10, minion.getTotalStoredCount());
        assertEquals(10, minion.getTotalHarvested());
    }

    @Test
    @DisplayName("Auto-Smelter upgrade should convert Cobblestone to Stone during offline simulation")
    void testAutoSmelterOffline() {
        Minion minion = new Minion(UUID.randomUUID(), UUID.randomUUID(), MinionType.COBBLESTONE, 11, null);
        minion.setUpgrade1(MinionUpgradeType.AUTO_SMELTER);

        // Simulate 70 seconds offline (70 / 14 = 5 items)
        minion.setLastActionTime(System.currentTimeMillis() - 70_000L);

        int produced = OfflineCalculator.processOfflineCatchup(minion);
        assertEquals(5, produced);
        assertEquals(Material.STONE, minion.getInventory().getFirst().getType());
    }
}
