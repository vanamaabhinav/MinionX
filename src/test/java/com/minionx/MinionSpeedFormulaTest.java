package com.minionx;

import com.minionx.api.*;
import com.minionx.core.Minion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class MinionSpeedFormulaTest {

    @Test
    @DisplayName("Should calculate base speed without buffs")
    void testBaseSpeed() {
        Minion minion = new Minion(UUID.randomUUID(), UUID.randomUUID(), MinionType.COBBLESTONE, 1, null);
        assertEquals(14.0, minion.getEffectiveActionDelay(), 0.001);

        minion.setTier(11);
        assertEquals(7.0, minion.getEffectiveActionDelay(), 0.001);
    }

    @Test
    @DisplayName("Should apply additive speed with Enchanted Lava Bucket (+25%)")
    void testEnchantedLavaBucketSpeed() {
        Minion minion = new Minion(UUID.randomUUID(), UUID.randomUUID(), MinionType.COBBLESTONE, 1, null);
        minion.setFuel(MinionFuelType.ENCHANTED_LAVA_BUCKET);

        // Formula: 14.0 / (1 + 0.25) = 14.0 / 1.25 = 11.2 seconds
        assertEquals(11.2, minion.getEffectiveActionDelay(), 0.001);
        assertTrue(minion.isFuelActive());
    }

    @Test
    @DisplayName("Should apply additive stacking buffs (Lava Bucket + Minion Expander)")
    void testStackedBuffs() {
        Minion minion = new Minion(UUID.randomUUID(), UUID.randomUUID(), MinionType.COBBLESTONE, 1, null);
        minion.setFuel(MinionFuelType.ENCHANTED_LAVA_BUCKET); // +25%
        minion.setUpgrade1(MinionUpgradeType.MINION_EXPANDER); // +5%

        // Formula: 14.0 / (1 + 0.25 + 0.05) = 14.0 / 1.30 = 10.7692...
        double expected = 14.0 / 1.30;
        assertEquals(expected, minion.getEffectiveActionDelay(), 0.001);
    }

    @Test
    @DisplayName("Should handle timed fuels properly")
    void testTimedFuel() {
        Minion minion = new Minion(UUID.randomUUID(), UUID.randomUUID(), MinionType.COBBLESTONE, 1, null);
        minion.setFuel(MinionFuelType.COAL); // +5%

        assertTrue(minion.isFuelActive());
        assertEquals(14.0 / 1.05, minion.getEffectiveActionDelay(), 0.001);

        // Simulate expired fuel
        minion.setFuelEndTime(System.currentTimeMillis() - 1000L);
        assertFalse(minion.isFuelActive());
        assertEquals(14.0, minion.getEffectiveActionDelay(), 0.001); // Reverts to base speed
    }
}
