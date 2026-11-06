package com.minionx.api;

import org.bukkit.Material;

import java.util.*;

/**
 * Represents a configurable Minion Upgrade / Accessory defined in upgrades.yml.
 * Supports composite multi-behavior capabilities and multi-item spreading drops.
 */
public class CustomUpgrade {

    public enum UpgradeBehavior {
        AUTO_SMELTER,
        COMPACTOR,
        SUPER_COMPACTOR,
        ITEM_SPREADING,
        SPEED_BUFF,
        AUTO_SELLER,
        EXPANDER
    }

    private final String id;
    private final String displayName;
    private final Material material;
    private final Set<UpgradeBehavior> behaviors;
    private final double speedBuff; // e.g. 0.20 for +20%
    private final double chance;    // e.g. 0.10 for 10%
    private final double sellRatio; // e.g. 0.50 for Budget Hopper, 0.90 for Enchanted Hopper
    private final int areaExpansion; // e.g. 1 for Minion Expander (+1 radius, 5x5 -> 7x7)
    private final List<BonusDropEntry> spreadingItems;
    private final List<String> lore;
    private final boolean glowing;
    private final int customModelData;

    public CustomUpgrade(String id, String displayName, Material material, UpgradeBehavior behavior,
                         double speedBuff, double chance, List<String> lore, boolean glowing, int customModelData) {
        this(id, displayName, material, behavior != null ? Set.of(behavior) : Set.of(UpgradeBehavior.SPEED_BUFF),
                speedBuff, chance, 0.0, 0, Collections.emptyList(), lore, glowing, customModelData);
    }

    public CustomUpgrade(String id, String displayName, Material material, UpgradeBehavior behavior,
                         double speedBuff, double chance, double sellRatio, List<String> lore, boolean glowing, int customModelData) {
        this(id, displayName, material, behavior != null ? Set.of(behavior) : Set.of(UpgradeBehavior.SPEED_BUFF),
                speedBuff, chance, sellRatio, 0, Collections.emptyList(), lore, glowing, customModelData);
    }

    public CustomUpgrade(String id, String displayName, Material material, UpgradeBehavior behavior,
                         double speedBuff, double chance, double sellRatio, int areaExpansion,
                         List<String> lore, boolean glowing, int customModelData) {
        this(id, displayName, material, behavior != null ? Set.of(behavior) : Set.of(UpgradeBehavior.SPEED_BUFF),
                speedBuff, chance, sellRatio, areaExpansion, Collections.emptyList(), lore, glowing, customModelData);
    }

    public CustomUpgrade(String id, String displayName, Material material, UpgradeBehavior behavior,
                         double speedBuff, double chance, double sellRatio, int areaExpansion,
                         BonusDropEntry spreadingItem, List<BonusDropEntry> bonusDrops,
                         List<String> lore, boolean glowing, int customModelData) {
        this(id, displayName, material,
                behavior != null ? Set.of(behavior) : Set.of(UpgradeBehavior.SPEED_BUFF),
                speedBuff, chance, sellRatio, areaExpansion,
                combineSpreading(spreadingItem, bonusDrops), lore, glowing, customModelData);
    }

    public CustomUpgrade(String id, String displayName, Material material, Set<UpgradeBehavior> behaviors,
                         double speedBuff, double chance, double sellRatio, int areaExpansion,
                         List<BonusDropEntry> spreadingItems,
                         List<String> lore, boolean glowing, int customModelData) {
        this.id = id.toLowerCase();
        this.displayName = displayName;
        this.material = material;
        if (behaviors == null || behaviors.isEmpty()) {
            this.behaviors = EnumSet.of(UpgradeBehavior.SPEED_BUFF);
        } else {
            this.behaviors = Collections.unmodifiableSet(EnumSet.copyOf(behaviors));
        }
        this.speedBuff = speedBuff;
        this.chance = chance;
        this.sellRatio = sellRatio;
        this.areaExpansion = Math.max(0, areaExpansion);
        this.spreadingItems = spreadingItems != null ? List.copyOf(spreadingItems) : Collections.emptyList();
        this.lore = lore;
        this.glowing = glowing;
        this.customModelData = customModelData;
    }

    private static List<BonusDropEntry> combineSpreading(BonusDropEntry single, List<BonusDropEntry> list) {
        List<BonusDropEntry> combined = new ArrayList<>();
        if (single != null) combined.add(single);
        if (list != null) combined.addAll(list);
        return combined;
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

    public Set<UpgradeBehavior> getBehaviors() {
        return behaviors;
    }

    public boolean hasBehavior(UpgradeBehavior behavior) {
        return behavior != null && behaviors.contains(behavior);
    }

    public UpgradeBehavior getBehavior() {
        return behaviors.isEmpty() ? UpgradeBehavior.SPEED_BUFF : behaviors.iterator().next();
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
        return hasBehavior(UpgradeBehavior.AUTO_SELLER) || sellRatio > 0;
    }

    public boolean isAutoSmelter() {
        return hasBehavior(UpgradeBehavior.AUTO_SMELTER);
    }

    public boolean isCompactor() {
        return hasBehavior(UpgradeBehavior.COMPACTOR);
    }

    public boolean isSuperCompactor() {
        return hasBehavior(UpgradeBehavior.SUPER_COMPACTOR);
    }

    public int getAreaExpansion() {
        return areaExpansion;
    }

    public boolean isSpreading() {
        return hasBehavior(UpgradeBehavior.ITEM_SPREADING) || !spreadingItems.isEmpty();
    }

    public List<BonusDropEntry> getSpreadingItems() {
        return spreadingItems;
    }

    public BonusDropEntry getSpreadingItem() {
        return spreadingItems.isEmpty() ? null : spreadingItems.get(0);
    }

    public boolean hasBonusDrops() {
        return !spreadingItems.isEmpty();
    }

    public List<BonusDropEntry> getBonusDrops() {
        return spreadingItems;
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
