package com.minionx.gui;

import com.minionx.api.MinionArchetype;
import com.minionx.api.MinionDefinition;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * Stores working state while an admin configures a minion in the Minion Creator Studio.
 */
public class MinionCreatorSession {

    private String id;
    private String displayName;
    private MinionArchetype archetype;

    private String skullTexture;
    private Material armorMaterial;
    private Color armorColor;
    private ItemStack handItem;

    private Material targetBlock;
    private String targetMob;
    private ItemStack exactDropItem;
    private ItemStack secondaryDropItem;
    private double secondaryDropChance;
    private int xpPerHarvest;

    private double actionDelay;
    private int storageSlots;
    private int maxTier = 12;

    private boolean perTierMode;
    private int selectedTier;
    private final Map<Integer, MinionDefinition.TierStyle> tierStyles;

    public MinionCreatorSession(String id) {
        this.id = id != null ? id.toLowerCase() : "custom_minion";
        this.displayName = "&a" + capitalize(this.id) + " Minion";
        this.archetype = MinionArchetype.MINER;

        this.skullTexture = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMTRhMjQzZTU1OWU5OWExMGJmMWFlNGU2YmFmYmY0MTA1ODhhMTVhM2Q2YzVjOGVmMjljMWE4YmRiNWYzMWFjYyJ9fX0=";
        this.armorMaterial = Material.LEATHER_CHESTPLATE;
        this.armorColor = Color.fromRGB(120, 120, 120);
        this.handItem = new ItemStack(Material.STONE_PICKAXE);

        this.targetBlock = Material.COBBLESTONE;
        this.targetMob = "ZOMBIE";
        this.exactDropItem = new ItemStack(Material.COBBLESTONE);
        this.secondaryDropItem = null;
        this.secondaryDropChance = 0.0;
        this.xpPerHarvest = 2;

        this.actionDelay = 14.0;
        this.storageSlots = 1;
        this.maxTier = 12;

        this.perTierMode = false;
        this.selectedTier = 1;
        this.tierStyles = new HashMap<>();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id != null ? id.toLowerCase() : "custom_minion"; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public MinionArchetype getArchetype() { return archetype; }
    public void setArchetype(MinionArchetype archetype) { this.archetype = archetype; }

    public String getSkullTexture() { return skullTexture; }
    public void setSkullTexture(String skullTexture) { this.skullTexture = skullTexture; }

    public Material getArmorMaterial() { return armorMaterial; }
    public void setArmorMaterial(Material armorMaterial) { this.armorMaterial = armorMaterial; }

    public Color getArmorColor() { return armorColor; }
    public void setArmorColor(Color armorColor) { this.armorColor = armorColor; }

    public ItemStack getHandItem() { return handItem; }
    public void setHandItem(ItemStack handItem) { this.handItem = handItem != null ? handItem.clone() : new ItemStack(Material.STONE_PICKAXE); }

    public Material getTargetBlock() { return targetBlock; }
    public void setTargetBlock(Material targetBlock) { this.targetBlock = targetBlock; }

    public String getTargetMob() { return targetMob; }
    public void setTargetMob(String targetMob) { this.targetMob = targetMob; }

    public ItemStack getExactDropItem() { return exactDropItem; }
    public void setExactDropItem(ItemStack exactDropItem) { this.exactDropItem = exactDropItem != null ? exactDropItem.clone() : new ItemStack(Material.COBBLESTONE); }

    public ItemStack getSecondaryDropItem() { return secondaryDropItem; }
    public void setSecondaryDropItem(ItemStack secondaryDropItem) { this.secondaryDropItem = secondaryDropItem != null ? secondaryDropItem.clone() : null; }

    public double getSecondaryDropChance() { return secondaryDropChance; }
    public void setSecondaryDropChance(double secondaryDropChance) { this.secondaryDropChance = Math.max(0.0, Math.min(1.0, secondaryDropChance)); }

    public int getXpPerHarvest() { return xpPerHarvest; }
    public void setXpPerHarvest(int xpPerHarvest) { this.xpPerHarvest = Math.max(0, xpPerHarvest); }

    public double getActionDelay() { return actionDelay; }
    public void setActionDelay(double actionDelay) { this.actionDelay = Math.clamp(actionDelay, 1.0, 120.0); }

    public int getStorageSlots() { return storageSlots; }
    public void setStorageSlots(int storageSlots) { this.storageSlots = Math.clamp(storageSlots, 1, 15); }

    public int getMaxTier() { return maxTier; }
    public void setMaxTier(int maxTier) { this.maxTier = Math.clamp(maxTier, 1, com.minionx.api.MinionTier.getMaxTier()); }

    public boolean isPerTierMode() { return perTierMode; }
    public void setPerTierMode(boolean perTierMode) { this.perTierMode = perTierMode; }

    public int getSelectedTier() { return selectedTier; }
    public void setSelectedTier(int selectedTier) { this.selectedTier = Math.clamp(selectedTier, 1, 11); }

    public Map<Integer, MinionDefinition.TierStyle> getTierStyles() { return tierStyles; }

    public String getEffectiveSkullTexture(int tier) {
        if (perTierMode) {
            MinionDefinition.TierStyle style = tierStyles.get(tier);
            if (style != null && style.getSkullTexture() != null) {
                return style.getSkullTexture();
            }
        }
        return skullTexture;
    }

    public Material getEffectiveArmorMaterial(int tier) {
        if (perTierMode) {
            MinionDefinition.TierStyle style = tierStyles.get(tier);
            if (style != null && style.getArmorMaterial() != null) {
                return style.getArmorMaterial();
            }
        }
        return armorMaterial;
    }

    public Color getEffectiveArmorColor(int tier) {
        if (perTierMode) {
            MinionDefinition.TierStyle style = tierStyles.get(tier);
            if (style != null && style.getArmorColor() != null) {
                return style.getArmorColor();
            }
        }
        return armorColor;
    }

    public ItemStack getEffectiveHandItem(int tier) {
        if (perTierMode) {
            MinionDefinition.TierStyle style = tierStyles.get(tier);
            if (style != null && style.getHandItem() != null) {
                return style.getHandItem().clone();
            }
        }
        return handItem != null ? handItem.clone() : new ItemStack(Material.STONE_PICKAXE);
    }

    public void setTierSkullTexture(int tier, String texture) {
        MinionDefinition.TierStyle existing = tierStyles.get(tier);
        Material mat = existing != null ? existing.getArmorMaterial() : armorMaterial;
        Color col = existing != null ? existing.getArmorColor() : armorColor;
        ItemStack hand = existing != null ? existing.getHandItem() : handItem;
        tierStyles.put(tier, new MinionDefinition.TierStyle(texture, mat, col, hand));
    }

    public void setTierArmorMaterial(int tier, Material mat) {
        MinionDefinition.TierStyle existing = tierStyles.get(tier);
        String skull = existing != null ? existing.getSkullTexture() : skullTexture;
        Color col = existing != null ? existing.getArmorColor() : armorColor;
        ItemStack hand = existing != null ? existing.getHandItem() : handItem;
        tierStyles.put(tier, new MinionDefinition.TierStyle(skull, mat, col, hand));
    }

    public void setTierArmorColor(int tier, Color col) {
        MinionDefinition.TierStyle existing = tierStyles.get(tier);
        String skull = existing != null ? existing.getSkullTexture() : skullTexture;
        Material mat = existing != null ? existing.getArmorMaterial() : armorMaterial;
        ItemStack hand = existing != null ? existing.getHandItem() : handItem;
        tierStyles.put(tier, new MinionDefinition.TierStyle(skull, mat, col, hand));
    }

    public void setTierHandItem(int tier, ItemStack item) {
        MinionDefinition.TierStyle existing = tierStyles.get(tier);
        String skull = existing != null ? existing.getSkullTexture() : skullTexture;
        Material mat = existing != null ? existing.getArmorMaterial() : armorMaterial;
        Color col = existing != null ? existing.getArmorColor() : armorColor;
        tierStyles.put(tier, new MinionDefinition.TierStyle(skull, mat, col, item != null ? item.clone() : null));
    }

    public static MinionCreatorSession fromDefinition(MinionDefinition def) {
        if (def == null) return new MinionCreatorSession("custom_minion");
        MinionCreatorSession session = new MinionCreatorSession(def.getId());
        session.setDisplayName(def.getDisplayName());
        session.setArchetype(def.getArchetype());
        session.setSkullTexture(def.getDefaultSkullTexture());
        session.setArmorMaterial(def.getDefaultArmorMaterial());
        session.setArmorColor(def.getDefaultArmorColor());
        session.setHandItem(def.getDefaultHandItem());
        session.setTargetBlock(def.getTargetBlock());
        session.setTargetMob(def.getTargetMob());
        session.setExactDropItem(def.getExactDropItem());
        session.setSecondaryDropItem(def.getSecondaryDropItem());
        session.setSecondaryDropChance(def.getSecondaryDropChance());
        session.setXpPerHarvest(def.getXpPerHarvest());
        session.setActionDelay(def.getDefaultActionDelay());
        session.setStorageSlots(def.getDefaultStorageSlots());
        session.setMaxTier(def.getMaxTier());
        if (def.getTierStyles() != null && !def.getTierStyles().isEmpty()) {
            session.setPerTierMode(true);
            session.getTierStyles().putAll(def.getTierStyles());
        }
        return session;
    }

    public MinionDefinition buildDefinition() {
        MinionDefinition def = new MinionDefinition(id, displayName, archetype,
                skullTexture, armorMaterial, armorColor, handItem,
                targetBlock, targetMob, exactDropItem,
                secondaryDropItem, secondaryDropChance,
                xpPerHarvest, actionDelay, storageSlots);

        if (perTierMode) {
            for (Map.Entry<Integer, MinionDefinition.TierStyle> entry : tierStyles.entrySet()) {
                def.addTierStyle(entry.getKey(), entry.getValue());
            }
        }
        def.setMaxTier(maxTier);
        return def;
    }

    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) return "";
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}
