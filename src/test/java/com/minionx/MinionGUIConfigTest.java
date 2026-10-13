package com.minionx;

import com.minionx.gui.MinionGUIConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class MinionGUIConfigTest {

    @Test
    @DisplayName("Should return standard default slots when config is null")
    void testDefaultSlots() {
        assertEquals(3, MinionGUIConfig.getIdealLayoutSlot());
        assertEquals(4, MinionGUIConfig.getHeadSlot());
        assertEquals(5, MinionGUIConfig.getNextTierSlot());
        assertEquals(10, MinionGUIConfig.getSkinSlot());
        assertEquals(19, MinionGUIConfig.getFuelSlot());
        assertEquals(28, MinionGUIConfig.getAutomatedShippingSlot());
        assertEquals(37, MinionGUIConfig.getUpgrade1Slot());
        assertEquals(46, MinionGUIConfig.getUpgrade2Slot());
        assertEquals(48, MinionGUIConfig.getCollectAllSlot());
        assertEquals(50, MinionGUIConfig.getUpgradeTierSlot());
        assertEquals(53, MinionGUIConfig.getPickupSlot());

        int[] storage = MinionGUIConfig.getStorageSlots();
        assertEquals(15, storage.length);
        assertEquals(21, storage[0]);
        assertEquals(43, storage[14]);

        assertEquals(0, MinionGUIConfig.getStorageIndex(21));
        assertEquals(14, MinionGUIConfig.getStorageIndex(43));
        assertEquals(-1, MinionGUIConfig.getStorageIndex(4));
    }
}
