package com.minionx;

import com.minionx.gui.MinionGUIConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class MinionGUIConfigTest {

    @Test
    @DisplayName("Should return standard default slots when config is null")
    void testDefaultSlots() {
        assertEquals(4, MinionGUIConfig.getHeadSlot());
        assertEquals(9, MinionGUIConfig.getFuelSlot());
        assertEquals(18, MinionGUIConfig.getAutomatedShippingSlot());
        assertEquals(27, MinionGUIConfig.getUpgrade1Slot());
        assertEquals(36, MinionGUIConfig.getUpgrade2Slot());
        assertEquals(37, MinionGUIConfig.getSkinSlot());
        assertEquals(46, MinionGUIConfig.getCollectAllSlot());
        assertEquals(49, MinionGUIConfig.getUpgradeTierSlot());
        assertEquals(53, MinionGUIConfig.getPickupSlot());

        int[] storage = MinionGUIConfig.getStorageSlots();
        assertEquals(15, storage.length);
        assertEquals(12, storage[0]);
        assertEquals(34, storage[14]);

        assertEquals(0, MinionGUIConfig.getStorageIndex(12));
        assertEquals(14, MinionGUIConfig.getStorageIndex(34));
        assertEquals(-1, MinionGUIConfig.getStorageIndex(4));
    }
}
