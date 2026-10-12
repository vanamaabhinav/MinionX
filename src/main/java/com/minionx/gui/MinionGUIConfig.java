package com.minionx.gui;

import com.minionx.MinionX;
import java.util.List;

/**
 * Configuration holder for the 54-slot Minion chest menu.
 * Faithfully mirrors the standard MinionX layout by default:
 * - Row 0: Slot 4 = Minion Head
 * - Row 1: Slot 9 = Fuel | Slots 12-16 = Storage (Row 1)
 * - Row 2: Slot 18 = Automated Shipping | Slots 21-25 = Storage (Row 2)
 * - Row 3: Slot 27 = Upgrade Slot 1 | Slots 30-34 = Storage (Row 3)
 * - Row 4: Slot 36 = Upgrade Slot 2
 * - Row 5: Slot 46 = Quick Collect | Slot 49 = Upgrade Tier | Slot 53 = Pickup Minion
 */
public class MinionGUIConfig {

    public static int getHeadSlot() {
        return getInt("gui.minion-menu.slots.head", 4);
    }

    public static int getFuelSlot() {
        return getInt("gui.minion-menu.slots.fuel", 9);
    }

    public static int getAutomatedShippingSlot() {
        return getInt("gui.minion-menu.slots.automated-shipping", 18);
    }

    public static int getUpgrade1Slot() {
        return getInt("gui.minion-menu.slots.upgrade-1", 27);
    }

    public static int getUpgrade2Slot() {
        return getInt("gui.minion-menu.slots.upgrade-2", 36);
    }

    public static int getSkinSlot() {
        return getInt("gui.minion-menu.slots.skin", 37);
    }

    public static int getCollectAllSlot() {
        return getInt("gui.minion-menu.slots.collect-all", 46);
    }

    public static int getUpgradeTierSlot() {
        return getInt("gui.minion-menu.slots.upgrade-tier", 49);
    }

    public static int getPickupSlot() {
        return getInt("gui.minion-menu.slots.pickup", 53);
    }

    public static int[] getStorageSlots() {
        if (MinionX.getInstance() == null || MinionX.getInstance().getConfig() == null) {
            return new int[]{
                    12, 13, 14, 15, 16,
                    21, 22, 23, 24, 25,
                    30, 31, 32, 33, 34
            };
        }
        List<Integer> list = MinionX.getInstance().getConfig().getIntegerList("gui.minion-menu.slots.storage");
        if (list == null || list.isEmpty()) {
            return new int[]{
                    12, 13, 14, 15, 16,
                    21, 22, 23, 24, 25,
                    30, 31, 32, 33, 34
            };
        }
        int[] arr = new int[list.size()];
        for (int i = 0; i < list.size(); i++) {
            arr[i] = list.get(i);
        }
        return arr;
    }

    public static int getStorageIndex(int slot) {
        int[] storage = getStorageSlots();
        for (int i = 0; i < storage.length; i++) {
            if (storage[i] == slot) return i;
        }
        return -1;
    }

    private static int getInt(String path, int def) {
        if (MinionX.getInstance() == null || MinionX.getInstance().getConfig() == null) {
            return def;
        }
        return MinionX.getInstance().getConfig().getInt(path, def);
    }
}
