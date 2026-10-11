package com.skyblock.minions;

import com.skyblock.minions.gui.MinionGUIConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class MinionGUIConfigTest {

    @Test
    @DisplayName("Should return authentic Hypixel default slots when config is null")
    void testHypixelDefaultSlots() {
        assertEquals(4, MinionGUIConfig.getHeadSlot());
        assertEquals(19, MinionGUIConfig.getFuelSlot());
        assertEquals(20, MinionGUIConfig.getAutomatedShippingSlot());
        assertEquals(28, MinionGUIConfig.getUpgrade1Slot());
        assertEquals(29, MinionGUIConfig.getUpgrade2Slot());
        assertEquals(37, MinionGUIConfig.getSkinSlot());
        assertEquals(48, MinionGUIConfig.getUpgradeTierSlot());
        assertEquals(49, MinionGUIConfig.getCollectAllSlot());
        assertEquals(51, MinionGUIConfig.getPickupSlot());

        int[] storage = MinionGUIConfig.getStorageSlots();
        assertEquals(15, storage.length);
        assertEquals(21, storage[0]);
        assertEquals(43, storage[14]);

        assertEquals(0, MinionGUIConfig.getStorageIndex(21));
        assertEquals(14, MinionGUIConfig.getStorageIndex(43));
        assertEquals(-1, MinionGUIConfig.getStorageIndex(4));
    }
}
