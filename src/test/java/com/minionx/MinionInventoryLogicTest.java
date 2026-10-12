package com.minionx;

import com.minionx.api.MinionType;
import com.minionx.core.Minion;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class MinionInventoryLogicTest {

    @Test
    @DisplayName("Should accept items up to tier capacity (Tier 1 = 1 slot / 64 items)")
    void testTier1Capacity() {
        Minion minion = new Minion(UUID.randomUUID(), UUID.randomUUID(), MinionType.COBBLESTONE, 1, null);

        // Add 64 items in increments
        for (int i = 0; i < 64; i++) {
            assertTrue(minion.addItem(new ItemStack(Material.COBBLESTONE, 1)));
        }

        assertEquals(64, minion.getTotalStoredCount());
        assertEquals(1, minion.getInventory().size());

        // 65th item should be rejected as Tier 1 only has 1 slot
        assertFalse(minion.addItem(new ItemStack(Material.COBBLESTONE, 1)));
        assertEquals(64, minion.getTotalStoredCount());
    }

    @Test
    @DisplayName("Upgrading tier should unlock additional storage slots")
    void testUpgradeUnlocksSlots() {
        Minion minion = new Minion(UUID.randomUUID(), UUID.randomUUID(), MinionType.COBBLESTONE, 1, null);

        // Fill slot 1
        minion.addItem(new ItemStack(Material.COBBLESTONE, 64));
        assertFalse(minion.addItem(new ItemStack(Material.COBBLESTONE, 1)));

        // Upgrade to Tier 2 (unlocks 3 slots = 192 capacity)
        minion.setTier(2);
        assertTrue(minion.addItem(new ItemStack(Material.COBBLESTONE, 64)));
        assertEquals(128, minion.getTotalStoredCount());
        assertEquals(2, minion.getInventory().size());

        assertTrue(minion.addItem(new ItemStack(Material.COBBLESTONE, 64)));
        assertEquals(192, minion.getTotalStoredCount());
        assertEquals(3, minion.getInventory().size());

        // 4th slot should fail on Tier 2
        assertFalse(minion.addItem(new ItemStack(Material.COBBLESTONE, 1)));
    }
}
