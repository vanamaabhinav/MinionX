package com.minionx.api;

import org.bukkit.Material;

import java.util.Collections;
import java.util.List;

/**
 * Represents a configurable Minion Upgrade / Accessory defined in upgrades.yml.
 */
public class CustomUpgrade {

    public enum UpgradeBehavior {
        AUTO_SMELTER,
        COMPACTOR,
        SUPER_COMPACTOR,
        DIAMOND_SPREADING,
        ITEM_SPREADING,
        BONUS_DROPS,
        SPEED_BUFF,
        AUTO_SELLER
    }

    private final String id;
    private final String displayName;
    private final Material material;
    private final UpgradeBehavior behavior;
    private final double speedBuff; // e.g. 0.20 for +20%
    private final double chance;    // e.g. 0.10 for 10%
    private final double sellRatio; // e.g. 0.50 for Budget Hopper, 0.90 for Enchanted Hopper
    private final int areaExpansion; // e.g. 1 for Minion Expander (+1 radius, 5x5 -> 7x7)
    private final BonusDropEntry spreadingItem;
    private final List<BonusDropEntry> bonusDrops;
    private final List<String> lore;
    private final boolean glowing;
    private final int customModelData;

    public CustomUpgrade(String id, String displayName, Material material, UpgradeBehavior behavior,
                         double speedBuff, double chance, List<String> lore, boolean glowing, int customModelData) {
        this(id, displayName, material, behavior, speedBuff, chance, 0.0, 0, null, Collections.emptyList(), lore, glowing, customModelData);
    }

    public CustomUpgrade(String id, String displayName, Material material, UpgradeBehavior behavior,
                         double speedBuff, double chance, double sellRatio, List<String> lore, boolean glowing, int customModelData) {
        this(id, displayName, material, behavior, speedBuff, chance, sellRatio, 0, null, Collections.emptyList(), lore, glowing, customModelData);
    }

    public CustomUpgrade(String id, String displayName, Material material, UpgradeBehavior behavior,
                         double speedBuff, double chance, double sellRatio, int areaExpansion,
                         List<String> lore, boolean glowing, int customModelData) {
        this(id, displayName, material, behavior, speedBuff, chance, sellRatio, areaExpansion, null, Collections.emptyList(), lore, glowing, customModelData);
    }

    public CustomUpgrade(String id, String displayName, Material material, UpgradeBehavior behavior,
                         double speedBuff, double chance, double sellRatio, int areaExpansion,
                         BonusDropEntry spreadingItem, List<BonusDropEntry> bonusDrops,
                         List<String> lore, boolean glowing, int customModelData) {
        this.id = id.toLowerCase();
        this.displayName = displayName;
        this.material = material;
        this.behavior = behavior != null ? behavior : UpgradeBehavior.SPEED_BUFF;
        this.speedBuff = speedBuff;
        this.chance = chance;
        this.sellRatio = sellRatio;
        this.areaExpansion = Math.max(0, areaExpansion);
        this.spreadingItem = spreadingItem;
        this.bonusDrops = bonusDrops != null ? List.copyOf(bonusDrops) : Collections.emptyList();
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

    public double getSellRatio() {
        return sellRatio;
    }

    public boolean isAutoSeller() {
        return behavior == UpgradeBehavior.AUTO_SELLER;
    }

    public int getAreaExpansion() {
        return areaExpansion;
    }

    public boolean isSpreading() {
        return behavior == UpgradeBehavior.ITEM_SPREADING || behavior == UpgradeBehavior.DIAMOND_SPREADING || spreadingItem != null;
    }

    public BonusDropEntry getSpreadingItem() {
        if (spreadingItem != null) {
            return spreadingItem;
        }
        if (behavior == UpgradeBehavior.DIAMOND_SPREADING) {
            return new BonusDropEntry(Material.DIAMOND, 1, chance > 0 ? chance : 0.10);
        }
        return null;
    }

    public boolean hasBonusDrops() {
        return !bonusDrops.isEmpty();
    }

    public List<BonusDropEntry> getBonusDrops() {
        return bonusDrops;
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
