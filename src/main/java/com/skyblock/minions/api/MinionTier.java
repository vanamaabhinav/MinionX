package com.skyblock.minions.api;

/**
 * Defines the 11 tiers of a minion, their action delays, storage capacities, and upgrade costs.
 */
public class MinionTier {

    // Delays in seconds for tiers 1 to 11 (Cobblestone / Standard mining baseline)
    private static final double[] DELAYS = {
            14.0, 14.0, 12.5, 12.5, 11.0, 11.0, 9.5, 9.5, 8.0, 8.0, 7.0
    };

    // Storage capacity in slots (each slot holds up to 64 items)
    private static final int[] STORAGE_SLOTS = {
            1, 3, 3, 6, 6, 9, 9, 12, 12, 15, 15
    };

    // Upgrade cost from tier X to X+1 in base resources (e.g. 80 Cobblestone for Tier 1 -> 2)
    private static final int[] UPGRADE_COSTS = {
            80, 160, 320, 640, 1280, 2560, 5120, 10240, 20480, 40960, 0
    };

    private static final String[] ROMAN_NUMERALS = {
            "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI"
    };

    public static double getActionDelay(int tier) {
        int index = Math.clamp(tier - 1, 0, DELAYS.length - 1);
        return DELAYS[index];
    }

    public static int getStorageSlots(int tier) {
        int index = Math.clamp(tier - 1, 0, STORAGE_SLOTS.length - 1);
        return STORAGE_SLOTS[index];
    }

    public static int getMaxCapacity(int tier) {
        return getStorageSlots(tier) * 64;
    }

    public static int getUpgradeCost(int currentTier) {
        if (currentTier >= 11) return 0;
        int index = Math.clamp(currentTier - 1, 0, UPGRADE_COSTS.length - 1);
        return UPGRADE_COSTS[index];
    }

    public static String toRoman(int tier) {
        int index = Math.clamp(tier - 1, 0, ROMAN_NUMERALS.length - 1);
        return ROMAN_NUMERALS[index];
    }

    public static int getMaxTier() {
        return 11;
    }
}
