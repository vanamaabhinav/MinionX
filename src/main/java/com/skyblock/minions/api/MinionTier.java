package com.skyblock.minions.api;

import org.bukkit.configuration.ConfigurationSection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Defines minion tiers, their action delays, storage capacities, and upgrade costs.
 * Fully configurable from config.yml with fallback defaults.
 */
public class MinionTier {

    public static class TierData {
        private final double actionDelay;
        private final int storageSlots;
        private final int upgradeCost;

        public TierData(double actionDelay, int storageSlots, int upgradeCost) {
            this.actionDelay = actionDelay;
            this.storageSlots = storageSlots;
            this.upgradeCost = upgradeCost;
        }

        public double getActionDelay() {
            return actionDelay;
        }

        public int getStorageSlots() {
            return storageSlots;
        }

        public int getUpgradeCost() {
            return upgradeCost;
        }
    }

    private static final Map<Integer, TierData> TIERS = new ConcurrentHashMap<>();
    private static int maxTier = 12;

    static {
        registerDefaults();
    }

    public static void load(ConfigurationSection section) {
        TIERS.clear();
        if (section != null) {
            int highest = 1;
            for (String key : section.getKeys(false)) {
                try {
                    int tier = Integer.parseInt(key);
                    ConfigurationSection tSec = section.getConfigurationSection(key);
                    if (tSec == null) continue;

                    double delay = tSec.getDouble("action-delay", 14.0);
                    int slots = tSec.getInt("storage-slots", 1);
                    int cost = tSec.getInt("upgrade-cost", 80);

                    TIERS.put(tier, new TierData(delay, slots, cost));
                    if (tier > highest) highest = tier;
                } catch (NumberFormatException ignored) {}
            }
            if (!TIERS.isEmpty()) {
                maxTier = highest;
                return;
            }
        }
        registerDefaults();
    }

    private static void registerDefaults() {
        double[] defaultDelays = {14.0, 14.0, 12.5, 12.5, 11.0, 11.0, 9.5, 9.5, 8.0, 8.0, 7.0, 6.0};
        int[] defaultSlots = {1, 3, 3, 6, 6, 9, 9, 12, 12, 15, 15, 15};
        int[] defaultCosts = {80, 160, 320, 640, 1280, 2560, 5120, 10240, 20480, 40960, 81920, 0};

        for (int i = 0; i < defaultDelays.length; i++) {
            TIERS.put(i + 1, new TierData(defaultDelays[i], defaultSlots[i], defaultCosts[i]));
        }
        maxTier = defaultDelays.length;
    }

    public static double getActionDelay(int tier) {
        TierData data = TIERS.get(Math.clamp(tier, 1, maxTier));
        return data != null ? data.getActionDelay() : 14.0;
    }

    public static int getStorageSlots(int tier) {
        TierData data = TIERS.get(Math.clamp(tier, 1, maxTier));
        return data != null ? data.getStorageSlots() : 1;
    }

    public static int getMaxCapacity(int tier) {
        return getStorageSlots(tier) * 64;
    }

    public static int getUpgradeCost(int currentTier) {
        if (currentTier >= maxTier) return 0;
        TierData data = TIERS.get(currentTier);
        return data != null ? data.getUpgradeCost() : 0;
    }

    public static String toRoman(int tier) {
        String[] romans = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII", "XIII", "XIV", "XV"};
        if (tier >= 1 && tier <= romans.length) {
            return romans[tier - 1];
        }
        return String.valueOf(tier);
    }

    public static int getMaxTier() {
        return maxTier;
    }
}