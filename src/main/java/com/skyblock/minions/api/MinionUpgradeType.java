package com.skyblock.minions.api;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Supported minion upgrade items and their gameplay effects.
 */
public enum MinionUpgradeType {
    AUTO_SMELTER("Auto-Smelter", Material.FURNACE, "Automatically smelts harvested resources."),
    COMPACTOR("Compactor", Material.DROPPER, "Automatically compacts resources into blocks."),
    SUPER_COMPACTOR("Super Compactor 3000", Material.DISPENSER, "Automatically compacts resources into enchanted items."),
    DIAMOND_SPREADING("Diamond Spreading", Material.DIAMOND, "Adds a 10% chance to generate diamonds when harvesting resources."),
    MINION_EXPANDER("Minion Expander", Material.PISTON, "Increases production speed by +5%.");

    private final String displayName;
    private final Material icon;
    private final String description;

    MinionUpgradeType(String displayName, Material icon, String description) {
        this.displayName = displayName;
        this.icon = icon;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Material getIcon() {
        return icon;
    }

    public String getDescription() {
        return description;
    }

    public static MinionUpgradeType fromItemStack(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return null;
        for (MinionUpgradeType upgrade : values()) {
            if (item.getType() == upgrade.getIcon()) {
                return upgrade;
            }
        }
        return null;
    }
}
