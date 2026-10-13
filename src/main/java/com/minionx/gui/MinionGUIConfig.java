package com.minionx.gui;

import com.minionx.MinionX;
import java.util.List;

/**
 * Configuration holder for the 54-slot Hypixel-style Minion chest menu.
 * Faithfully mirrors the authentic Hypixel layout:
 * - Top Row: Slot 3 = Ideal Layout | Slot 4 = Minion Status | Slot 5 = Next Tier
 * - Left Column: Slot 10 = Skin | Slot 19 = Fuel | Slot 28 = Automated Shipping | Slot 37 = Upgrade 1 | Slot 46 = Upgrade 2
 * - Middle-Right Storage Grid: Slots 21-25 (Row 3), 30-34 (Row 4), 39-43 (Row 5)
 * - Bottom Row: Slot 48 = Collect All | Slot 50 = Quick-Upgrade | Slot 53 = Pickup Minion
 */
public class MinionGUIConfig {

    public static int getIdealLayoutSlot() {
        return getInt("gui.minion-menu.slots.ideal-layout", 3);
    }

    public static int getHeadSlot() {
        return getInt("gui.minion-menu.slots.head", 4);
    }

    public static int getNextTierSlot() {
        return getInt("gui.minion-menu.slots.next-tier", 5);
    }

    public static int getSkinSlot() {
        return getInt("gui.minion-menu.slots.skin", 10);
    }

    public static int getFuelSlot() {
        return getInt("gui.minion-menu.slots.fuel", 19);
    }

    public static int getAutomatedShippingSlot() {
        return getInt("gui.minion-menu.slots.automated-shipping", 28);
    }

    public static int getUpgrade1Slot() {
        return getInt("gui.minion-menu.slots.upgrade-1", 37);
    }

    public static int getUpgrade2Slot() {
        return getInt("gui.minion-menu.slots.upgrade-2", 46);
    }

    public static int getCollectAllSlot() {
        return getInt("gui.minion-menu.slots.collect-all", 48);
    }

    public static int getUpgradeTierSlot() {
        return getInt("gui.minion-menu.slots.upgrade-tier", 50);
    }

    public static int getPickupSlot() {
        return getInt("gui.minion-menu.slots.pickup", 53);
    }

    public static int[] getStorageSlots() {
        if (MinionX.getInstance() == null || MinionX.getInstance().getConfig() == null) {
            return new int[]{
                    21, 22, 23, 24, 25,
                    30, 31, 32, 33, 34,
                    39, 40, 41, 42, 43
            };
        }
        List<Integer> list = MinionX.getInstance().getConfig().getIntegerList("gui.minion-menu.slots.storage");
        if (list == null || list.isEmpty()) {
            return new int[]{
                    21, 22, 23, 24, 25,
                    30, 31, 32, 33, 34,
                    39, 40, 41, 42, 43
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
