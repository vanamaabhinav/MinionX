package com.minionx.api;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Data model for dynamically created or configured minions.
 * Supports exact copy drop items, secondary drops (seeds/apples/leather),
 * per-tier appearance overrides, and MythicMobs.
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

    public static class DropEntry {
        private final ItemStack item;
        private final int amount;
        private final double chance; // 0.0 to 1.0 (1.0 = 100%)

        public DropEntry(ItemStack item, int amount, double chance) {
            this.item = item != null ? item.clone() : new ItemStack(Material.COBBLESTONE);
            this.amount = Math.max(1, amount);
            this.chance = Math.clamp(chance, 0.0, 1.0);
        }

        public ItemStack getItem() {
            return item.clone();
        }

        public int getAmount() {
            return amount;
        }

        public double getChance() {
            return chance;
        }

        public ItemStack createDrop(int multiplier) {
            ItemStack stack = item.clone();
            stack.setAmount(Math.max(1, amount * multiplier));
            return stack;
        }
    }

    private final String id;
    private final String displayName;
    private final MinionArchetype archetype;

    private String defaultSkullTexture;
    private final Material defaultArmorMaterial;
    private final Color defaultArmorColor;
    private final ItemStack defaultHandItem;

    private final Material targetBlock;
    private final String targetMob; // Vanilla EntityType or MythicMob ID
    private final ItemStack exactDropItem; // Exact copy of custom drop item
    private final ItemStack secondaryDropItem; // Secondary drop (Seeds, Leather, Apples)
    private final double secondaryDropChance; // 0.0 to 1.0
    private final List<DropEntry> drops;
    private final int xpPerHarvest;

    private final double defaultActionDelay;
    private final int defaultStorageSlots;

    private final Map<Integer, TierStyle> tierStyles;

    public MinionDefinition(String id, String displayName, MinionArchetype archetype,
                            String defaultSkullTexture, Material defaultArmorMaterial, Color defaultArmorColor, ItemStack defaultHandItem,
                            Material targetBlock, String targetMob, ItemStack exactDropItem, int xpPerHarvest,
                            double defaultActionDelay, int defaultStorageSlots) {
        this(id, displayName, archetype, defaultSkullTexture, defaultArmorMaterial, defaultArmorColor, defaultHandItem,
                targetBlock, targetMob, exactDropItem, null, 0.0, xpPerHarvest, defaultActionDelay, defaultStorageSlots);
    }

    public MinionDefinition(String id, String displayName, MinionArchetype archetype,
                            String defaultSkullTexture, Material defaultArmorMaterial, Color defaultArmorColor, ItemStack defaultHandItem,
                            Material targetBlock, String targetMob, List<DropEntry> drops,
                            int xpPerHarvest, double defaultActionDelay, int defaultStorageSlots) {
        this.id = id.toLowerCase();
        this.displayName = displayName;
        this.archetype = archetype != null ? archetype : MinionArchetype.MINER;
        this.defaultSkullTexture = defaultSkullTexture;
        this.defaultArmorMaterial = defaultArmorMaterial != null ? defaultArmorMaterial : Material.LEATHER_CHESTPLATE;
        this.defaultArmorColor = defaultArmorColor != null ? defaultArmorColor : Color.fromRGB(160, 160, 160);
        this.defaultHandItem = defaultHandItem != null ? defaultHandItem.clone() : new ItemStack(Material.STONE_PICKAXE);
        this.targetBlock = targetBlock != null ? targetBlock : Material.COBBLESTONE;
        this.targetMob = targetMob;
        this.drops = drops != null ? new ArrayList<>(drops) : new ArrayList<>();
        this.exactDropItem = !this.drops.isEmpty() ? this.drops.get(0).getItem() : new ItemStack(Material.COBBLESTONE);
        if (this.drops.size() > 1) {
            this.secondaryDropItem = this.drops.get(1).getItem();
            this.secondaryDropChance = this.drops.get(1).getChance();
        } else {
            this.secondaryDropItem = null;
            this.secondaryDropChance = 0.0;
        }
        this.xpPerHarvest = xpPerHarvest;
        this.defaultActionDelay = defaultActionDelay > 0 ? defaultActionDelay : 14.0;
        this.defaultStorageSlots = defaultStorageSlots > 0 ? defaultStorageSlots : 1;
        this.tierStyles = new HashMap<>();
    }

    public MinionDefinition(String id, String displayName, MinionArchetype archetype,
                            String defaultSkullTexture, Material defaultArmorMaterial, Color defaultArmorColor, ItemStack defaultHandItem,
                            Material targetBlock, String targetMob, ItemStack exactDropItem,
                            ItemStack secondaryDropItem, double secondaryDropChance,
                            int xpPerHarvest, double defaultActionDelay, int defaultStorageSlots) {
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
        this.secondaryDropItem = secondaryDropItem != null ? secondaryDropItem.clone() : null;
        this.secondaryDropChance = secondaryDropChance;
        this.drops = new ArrayList<>();
        if (this.exactDropItem != null && this.exactDropItem.getType() != Material.AIR) {
            this.drops.add(new DropEntry(this.exactDropItem, this.exactDropItem.getAmount(), 1.0));
        }
        if (this.secondaryDropItem != null && this.secondaryDropChance > 0) {
            this.drops.add(new DropEntry(this.secondaryDropItem, this.secondaryDropItem.getAmount(), this.secondaryDropChance));
        }
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

    public List<DropEntry> getDrops() {
        return java.util.Collections.unmodifiableList(drops);
    }

    public void addDrop(DropEntry drop) {
        if (drop != null) {
            this.drops.add(drop);
        }
    }

    public List<ItemStack> rollDrops(int harvestMultiplier) {
        List<ItemStack> rolled = new ArrayList<>();
        if (drops != null && !drops.isEmpty()) {
            for (DropEntry d : drops) {
                if (java.util.concurrent.ThreadLocalRandom.current().nextDouble() < d.getChance()) {
                    rolled.add(d.createDrop(harvestMultiplier));
                }
            }
        }
        if (rolled.isEmpty() && exactDropItem != null && exactDropItem.getType() != Material.AIR) {
            ItemStack fallback = exactDropItem.clone();
            fallback.setAmount(Math.max(1, fallback.getAmount() * harvestMultiplier));
            rolled.add(fallback);
        }
        return rolled;
    }

    public ItemStack getExactDropItem() {
        return exactDropItem != null ? exactDropItem.clone() : new ItemStack(Material.AIR);
    }

    public ItemStack getSecondaryDropItem() {
        return secondaryDropItem != null ? secondaryDropItem.clone() : null;
    }

    public double getSecondaryDropChance() {
        return secondaryDropChance;
    }

    public boolean hasSecondaryDrop() {
        return secondaryDropItem != null && secondaryDropChance > 0;
    }

    public int getXpPerHarvest() {
        return xpPerHarvest;
    }

    public String getDefaultSkullTexture() {
        return defaultSkullTexture;
    }

    public void setDefaultSkullTexture(String defaultSkullTexture) {
        this.defaultSkullTexture = defaultSkullTexture;
    }

    public Material getDefaultArmorMaterial() {
        return defaultArmorMaterial;
    }

    public Color getDefaultArmorColor() {
        return defaultArmorColor;
    }

    public ItemStack getDefaultHandItem() {
        return defaultHandItem != null ? defaultHandItem.clone() : new ItemStack(Material.STONE_PICKAXE);
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

    public Material getSaplingMaterial() {
        if (targetBlock == null) return Material.OAK_SAPLING;
        return switch (targetBlock) {
            case SPRUCE_LOG, SPRUCE_WOOD -> Material.SPRUCE_SAPLING;
            case BIRCH_LOG, BIRCH_WOOD -> Material.BIRCH_SAPLING;
            case JUNGLE_LOG, JUNGLE_WOOD -> Material.JUNGLE_SAPLING;
            case ACACIA_LOG, ACACIA_WOOD -> Material.ACACIA_SAPLING;
            case DARK_OAK_LOG, DARK_OAK_WOOD -> Material.DARK_OAK_SAPLING;
            case MANGROVE_LOG, MANGROVE_WOOD -> Material.MANGROVE_PROPAGULE;
            case CHERRY_LOG, CHERRY_WOOD -> Material.CHERRY_SAPLING;
            case CRIMSON_STEM -> Material.CRIMSON_FUNGUS;
            case WARPED_STEM -> Material.WARPED_FUNGUS;
            default -> Material.OAK_SAPLING;
        };
    }

    public Material getLeafMaterial() {
        if (targetBlock == null) return Material.OAK_LEAVES;
        return switch (targetBlock) {
            case SPRUCE_LOG, SPRUCE_WOOD -> Material.SPRUCE_LEAVES;
            case BIRCH_LOG, BIRCH_WOOD -> Material.BIRCH_LEAVES;
            case JUNGLE_LOG, JUNGLE_WOOD -> Material.JUNGLE_LEAVES;
            case ACACIA_LOG, ACACIA_WOOD -> Material.ACACIA_LEAVES;
            case DARK_OAK_LOG, DARK_OAK_WOOD -> Material.DARK_OAK_LEAVES;
            case MANGROVE_LOG, MANGROVE_WOOD -> Material.MANGROVE_LEAVES;
            case CHERRY_LOG, CHERRY_WOOD -> Material.CHERRY_LEAVES;
            case CRIMSON_STEM -> Material.NETHER_WART_BLOCK;
            case WARPED_STEM -> Material.WARPED_WART_BLOCK;
            default -> Material.OAK_LEAVES;
        };
    }
}
