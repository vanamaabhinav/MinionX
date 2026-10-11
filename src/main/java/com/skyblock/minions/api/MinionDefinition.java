package com.skyblock.minions.api;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * Data model for dynamically created or configured minions.
 * Supports exact copy drop items, per-tier appearance overrides, and MythicMobs.
 */
public class MinionDefinition {

    public static class TierStyle {
        private final String skullTexture;
        private final Material armorMaterial;
        private final Color armorColor;
        private final ItemStack handItem;

        public TierStyle(String skullTexture, Material armorMaterial, Color armorColor, ItemStack handItem) {
            this.skullTexture = skullTexture;
            this.armorMaterial = armorMaterial;
            this.armorColor = armorColor;
            this.handItem = handItem;
        }

        public String getSkullTexture() {
            return skullTexture;
        }

        public Material getArmorMaterial() {
            return armorMaterial;
        }

        public Color getArmorColor() {
            return armorColor;
        }

        public ItemStack getHandItem() {
            return handItem;
        }
    }

    private final String id;
    private final String displayName;
    private final MinionArchetype archetype;

    private final String defaultSkullTexture;
    private final Material defaultArmorMaterial;
    private final Color defaultArmorColor;
    private final ItemStack defaultHandItem;

    private final Material targetBlock;
    private final String targetMob; // Vanilla EntityType or MythicMob ID
    private final ItemStack exactDropItem; // Exact copy of custom drop item
    private final int xpPerHarvest;

    private final double defaultActionDelay;
    private final int defaultStorageSlots;

    private final Map<Integer, TierStyle> tierStyles;

    public MinionDefinition(String id, String displayName, MinionArchetype archetype,
                            String defaultSkullTexture, Material defaultArmorMaterial, Color defaultArmorColor, ItemStack defaultHandItem,
                            Material targetBlock, String targetMob, ItemStack exactDropItem, int xpPerHarvest,
                            double defaultActionDelay, int defaultStorageSlots) {
        this.id = id.toLowerCase();
        this.displayName = displayName;
        this.archetype = archetype != null ? archetype : MinionArchetype.MINER;
        this.defaultSkullTexture = defaultSkullTexture;
        this.defaultArmorMaterial = defaultArmorMaterial != null ? defaultArmorMaterial : Material.LEATHER_CHESTPLATE;
        this.defaultArmorColor = defaultArmorColor != null ? defaultArmorColor : Color.fromRGB(160, 160, 160);
        this.defaultHandItem = defaultHandItem != null ? defaultHandItem.clone() : new ItemStack(Material.STONE_PICKAXE);
        this.targetBlock = targetBlock != null ? targetBlock : Material.COBBLESTONE;
        this.targetMob = targetMob;
        this.exactDropItem = exactDropItem != null ? exactDropItem.clone() : new ItemStack(Material.COBBLESTONE);
        this.xpPerHarvest = xpPerHarvest;
        this.defaultActionDelay = defaultActionDelay > 0 ? defaultActionDelay : 14.0;
        this.defaultStorageSlots = defaultStorageSlots > 0 ? defaultStorageSlots : 1;
        this.tierStyles = new HashMap<>();
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public MinionArchetype getArchetype() {
        return archetype;
    }

    public String getSkullTexture(int tier) {
        TierStyle style = tierStyles.get(tier);
        if (style != null && style.getSkullTexture() != null) return style.getSkullTexture();
        return defaultSkullTexture;
    }

    public Material getArmorMaterial(int tier) {
        TierStyle style = tierStyles.get(tier);
        if (style != null && style.getArmorMaterial() != null) return style.getArmorMaterial();
        return defaultArmorMaterial;
    }

    public Color getArmorColor(int tier) {
        TierStyle style = tierStyles.get(tier);
        if (style != null && style.getArmorColor() != null) return style.getArmorColor();
        return defaultArmorColor;
    }

    public ItemStack getHandItem(int tier) {
        TierStyle style = tierStyles.get(tier);
        if (style != null && style.getHandItem() != null) return style.getHandItem().clone();
        return defaultHandItem.clone();
    }

    public Material getTargetBlock() {
        return targetBlock;
    }

    public String getTargetMob() {
        return targetMob;
    }

    public ItemStack getExactDropItem() {
        return exactDropItem != null ? exactDropItem.clone() : new ItemStack(Material.AIR);
    }

    public int getXpPerHarvest() {
        return xpPerHarvest;
    }

    public double getDefaultActionDelay() {
        return defaultActionDelay;
    }

    public int getDefaultStorageSlots() {
        return defaultStorageSlots;
    }

    public Map<Integer, TierStyle> getTierStyles() {
        return tierStyles;
    }

    public void addTierStyle(int tier, TierStyle style) {
        tierStyles.put(tier, style);
    }
}