package com.minionx.api;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Supported minion fuels and their speed boost multipliers.
 */
public enum MinionFuelType {
    COAL("Coal", Material.COAL, 0.05, 30 * 60 * 1000L),
    CHARCOAL("Charcoal", Material.CHARCOAL, 0.05, 30 * 60 * 1000L),
    ENCHANTED_CHARCOAL("Enchanted Charcoal", Material.CHARCOAL, 0.20, 36 * 3600 * 1000L),
    ENCHANTED_LAVA_BUCKET("Enchanted Lava Bucket", Material.LAVA_BUCKET, 0.25, -1L),
    PLASMA_BUCKET("Plasma Bucket", Material.LAVA_BUCKET, 0.35, -1L);

    private final String displayName;
    private final Material material;
    private final double speedBuff; // e.g. 0.25 = +25% speed
    private final long durationMillis; // -1 for unlimited

    MinionFuelType(String displayName, Material material, double speedBuff, long durationMillis) {
        this.displayName = displayName;
        this.material = material;
        this.speedBuff = speedBuff;
        this.durationMillis = durationMillis;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Material getMaterial() {
        return material;
    }

    public double getSpeedBuff() {
        return speedBuff;
    }

    public long getDurationMillis() {
        return durationMillis;
    }

    public boolean isPermanent() {
        return durationMillis == -1L;
    }

    public static MinionFuelType fromItemStack(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return null;
        if (item.getType() == Material.LAVA_BUCKET) {
            return ENCHANTED_LAVA_BUCKET;
        } else if (item.getType() == Material.CHARCOAL) {
            return ENCHANTED_CHARCOAL;
        } else if (item.getType() == Material.COAL) {
            return COAL;
        }
        return null;
    }
}
