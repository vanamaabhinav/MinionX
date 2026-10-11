package com.skyblock.minions;

import com.skyblock.minions.api.CustomFuel;
import com.skyblock.minions.api.FuelRegistry;
import com.skyblock.minions.api.MinionType;
import com.skyblock.minions.core.Minion;
import com.skyblock.minions.hook.GeyserHook;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class FuelStackingAndGeyserTest {

    @Test
    @DisplayName("Should multiply fuel duration by quantity for stackable fuels")
    void testStackableFuelDuration() {
        Minion minion = new Minion(UUID.randomUUID(), UUID.randomUUID(), MinionType.COBBLESTONE, 1, null);
        CustomFuel coalFuel = FuelRegistry.get("coal");
        assertNotNull(coalFuel);

        long before = System.currentTimeMillis();
        // Depositing 64 coal -> 64 * 30 min (1,800,000 ms) = 115,200,000 ms = 32 hours
        minion.setFuel(coalFuel, 64);
        long after = System.currentTimeMillis();

        assertTrue(minion.isFuelActive());
        assertFalse(minion.isFuelPermanent());

        long expectedMin = before + (64L * 1800000L);
        long expectedMax = after + (64L * 1800000L);

        assertTrue(minion.getFuelEndTime() >= expectedMin);
        assertTrue(minion.getFuelEndTime() <= expectedMax);

        // Check formatted string contains "1d" (since 32 hours = 1 day 8 hours)
        String formatted = minion.getFormattedFuelTimeRemaining();
        assertTrue(formatted.contains("1d") || formatted.contains("32h"));
    }

    @Test
    @DisplayName("Should extend remaining duration when depositing more stackable fuel")
    void testExtendFuel() {
        Minion minion = new Minion(UUID.randomUUID(), UUID.randomUUID(), MinionType.COBBLESTONE, 1, null);
        CustomFuel charcoal = FuelRegistry.get("charcoal");
        assertNotNull(charcoal);

        // Initial deposit of 10 items
        minion.setFuel(charcoal, 10);
        long initialEnd = minion.getFuelEndTime();

        // Extend with 5 more items -> +5 * 1800000 ms = +9,000,000 ms
        minion.extendFuel(charcoal, 5);
        long extendedEnd = minion.getFuelEndTime();

        assertEquals(initialEnd + (5L * 1800000L), extendedEnd);
    }

    @Test
    @DisplayName("Should handle permanent fuels correctly without timing out")
    void testPermanentFuel() {
        Minion minion = new Minion(UUID.randomUUID(), UUID.randomUUID(), MinionType.COBBLESTONE, 1, null);
        CustomFuel lavaBucket = FuelRegistry.get("enchanted_lava_bucket");
        assertNotNull(lavaBucket);

        minion.setFuel(lavaBucket, 1);
        assertTrue(minion.isFuelActive());
        assertTrue(minion.isFuelPermanent());
        assertEquals("Permanent", minion.getFormattedFuelTimeRemaining());
    }

    @Test
    @DisplayName("GeyserHook should safely detect missing Floodgate and return false without throwing")
    void testGeyserHookFallback() {
        assertDoesNotThrow(() -> {
            boolean available = GeyserHook.isFloodgateAvailable();
            assertFalse(available, "Floodgate should not be available in standard test environment");

            boolean isBedrock = GeyserHook.isBedrockPlayer((Player) null);
            assertFalse(isBedrock, "Null player should not be detected as Bedrock player");
        });
    }
}
