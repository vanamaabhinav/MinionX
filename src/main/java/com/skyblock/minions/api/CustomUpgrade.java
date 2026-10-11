package com.skyblock.minions.api;

import org.bukkit.Material;
import java.util.List;

/**
 * Represents a configurable Minion Upgrade / Accessory defined in config.yml.
 */
public class CustomUpgrade {

    public enum UpgradeBehavior {
        AUTO_SMELTER,
        COMPACTOR,
        DIAMOND_SPREADING,
        SPEED_BUFF
    }

    private final String id;
    private final String displayName;
    private final Material material;
    private final UpgradeBehavior behavior;
    private final double speedBuff; // e.g. 0.20 for +20%
    private final double chance;    // e.g. 0.10 for 10%
    private final List<String> lore;
    private final boolean glowing;
    private final int customModelData;

    public CustomUpgrade(String id, String displayName, Material material, UpgradeBehavior behavior,
                         double speedBuff, double chance, List<String> lore, boolean glowing, int customModelData) {
        this.id = id.toLowerCase();
        this.displayName = displayName;
        this.material = material;
        this.behavior = behavior != null ? behavior : UpgradeBehavior.SPEED_BUFF;
        this.speedBuff = speedBuff;
        this.chance = chance;
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

    public UpgradeBehavior getBehavior() {
        return behavior;
    }

    public double getSpeedBuff() {
        return speedBuff;
    }

    public double getChance() {
        return chance;
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
