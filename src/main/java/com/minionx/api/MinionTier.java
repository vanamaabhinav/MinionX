package com.minionx.api;

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

    public static TierData getTierData(int tier) {
        return TIERS.get(tier);
    }

    public static Map<Integer, TierData> getAllTiers() {
        return java.util.Collections.unmodifiableMap(TIERS);
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

    public static void saveTier(int tier, TierData data) {
        TIERS.put(tier, data);
        if (tier > maxTier) {
            maxTier = tier;
        }

        if (com.minionx.MinionX.getInstance() == null) return;
        java.io.File file = new java.io.File(com.minionx.MinionX.getInstance().getDataFolder(), "tiers.yml");
        org.bukkit.configuration.file.YamlConfiguration config = file.exists()
                ? org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file)
                : new org.bukkit.configuration.file.YamlConfiguration();

        String path = "tiers." + tier;
        config.set(path + ".action-delay", data.getActionDelay());
        config.set(path + ".storage-slots", data.getStorageSlots());
        config.set(path + ".upgrade-cost", data.getUpgradeCost());
        try {
            config.save(file);
        } catch (java.io.IOException e) {
            com.minionx.MinionX.getInstance().getLogger().log(java.util.logging.Level.SEVERE, "Could not save tier " + tier + " to tiers.yml", e);
        }
    }

    public static boolean removeTier(int tier) {
        if (tier <= 12) return false; // Preserve default 12 tiers
        TIERS.remove(tier);
        int highest = 1;
        for (int t : TIERS.keySet()) {
            if (t > highest) highest = t;
        }
        maxTier = highest;

        if (com.minionx.MinionX.getInstance() == null) return true;
        java.io.File file = new java.io.File(com.minionx.MinionX.getInstance().getDataFolder(), "tiers.yml");
        if (file.exists()) {
            org.bukkit.configuration.file.YamlConfiguration config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
            config.set("tiers." + tier, null);
            try {
                config.save(file);
            } catch (java.io.IOException ignored) {}
        }
        return true;
    }

    public static String toRoman(int tier) {
        if (tier <= 0) return String.valueOf(tier);
        int[] values = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] symbols = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        StringBuilder sb = new StringBuilder();
        int remaining = tier;
        for (int i = 0; i < values.length; i++) {
            while (remaining >= values[i]) {
                sb.append(symbols[i]);
                remaining -= values[i];
            }
        }
        return sb.toString();
    }

    public static int getMaxTier() {
        return maxTier;
    }
}