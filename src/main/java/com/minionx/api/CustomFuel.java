package com.minionx.api;

import org.bukkit.Material;
import java.util.List;

/**
 * Represents a configurable Minion Fuel defined in config.yml.
 */
public class CustomFuel {

    private final String id;
    private final String displayName;
    private final Material material;
    private final double speedBuff; // e.g. 0.25 = +25% speed
    private final long durationMillis; // -1 for permanent
    private final List<String> lore;
    private final boolean glowing;
    private final int customModelData;

    public CustomFuel(String id, String displayName, Material material, double speedBuff,
                      long durationMillis, List<String> lore, boolean glowing, int customModelData) {
        this.id = id.toLowerCase();
        this.displayName = displayName;
        this.material = material;
        this.speedBuff = speedBuff;
        this.durationMillis = durationMillis;
        this.lore = lore;
        this.glowing = glowing;
        this.customModelData = customModelData;
    }

    public String getId() {
        return id;
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
        return durationMillis <= -1L;
    }

    public List<String> getLore() {
        return lore;
    }

    public boolean isGlowing() {
        return glowing;
    }

    public int getCustomModelData() {
        return customModelData;
    }
}
