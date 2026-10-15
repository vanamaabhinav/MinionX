package com.minionx.core;

import com.minionx.MinionX;
import com.minionx.api.CustomUpgrade;
import com.minionx.api.MinionUpgradeType;
import com.minionx.item.CustomItemSpec;
import com.minionx.util.ColorUtil;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/**
 * Handles Super Compactor recipe compression in Minion inventories.
 * Supports vanilla items and custom items with PDC, CustomModelData, display names, and lore.
 */
@SuppressWarnings("deprecation")
public class CompressionManager {

    public static class CompressionRecipe {
        private final String id;
        private final CustomItemSpec inputSpec;
        private final CustomItemSpec outputSpec;
        private final Material inputMaterial;
        private final int inputAmount;
        private final ItemStack outputItem;

        // Custom matching parameters
        private final Integer inputCustomModelData;
        private final String inputDisplayName;
        private final String inputPdcKey;
        private final String inputPdcValue;
        private final boolean isCustomInput;

        public CompressionRecipe(String id, CustomItemSpec inputSpec, CustomItemSpec outputSpec) {
            this.id = id;
            this.inputSpec = inputSpec;
            this.outputSpec = outputSpec;
            this.inputMaterial = inputSpec != null ? inputSpec.getMaterial() : Material.COBBLESTONE;
            this.inputAmount = inputSpec != null ? inputSpec.getAmount() : 160;
            this.outputItem = outputSpec != null ? outputSpec.build() : new ItemStack(Material.COBBLESTONE);
            this.inputCustomModelData = inputSpec != null ? inputSpec.getCustomModelData() : null;
            this.inputDisplayName = inputSpec != null ? inputSpec.getDisplayName() : null;
            this.inputPdcKey = inputSpec != null ? inputSpec.getPdcKey() : null;
            this.inputPdcValue = inputSpec != null ? inputSpec.getPdcValue() : null;
            this.isCustomInput = inputSpec != null && inputSpec.isCustom();
        }

        public CompressionRecipe(Material inputMaterial, int inputAmount, ItemStack outputItem) {
            this("vanilla_" + (inputMaterial != null ? inputMaterial.name().toLowerCase() : "unknown"),
                    inputMaterial, inputAmount, outputItem, null, null, null, null, false);
        }

        public CompressionRecipe(String id, Material inputMaterial, int inputAmount, ItemStack outputItem,
                                 Integer inputCustomModelData, String inputDisplayName,
                                 String inputPdcKey, String inputPdcValue, boolean isCustomInput) {
            this.id = id;
            this.inputMaterial = inputMaterial;
            this.inputAmount = Math.max(1, inputAmount);
            this.outputItem = outputItem != null ? outputItem.clone() : new ItemStack(Material.COBBLESTONE);
            this.inputCustomModelData = inputCustomModelData;
            this.inputDisplayName = inputDisplayName;
            this.inputPdcKey = inputPdcKey;
            this.inputPdcValue = inputPdcValue;
            this.isCustomInput = isCustomInput;
            this.inputSpec = new CustomItemSpec(inputMaterial, null, inputAmount, 1.0, inputDisplayName, null, inputCustomModelData, false, inputPdcKey, inputPdcValue);
            this.outputSpec = CustomItemSpec.fromItemStack(this.outputItem, 1.0);
        }

        public String getId() { return id; }
        public CustomItemSpec getInputSpec() { return inputSpec; }
        public CustomItemSpec getOutputSpec() { return outputSpec; }
        public Material getInputMaterial() { return inputMaterial; }
        public int getInputAmount() { return inputAmount; }
        public ItemStack createOutput() { return outputSpec != null ? outputSpec.build() : outputItem.clone(); }
        public Integer getInputCustomModelData() { return inputCustomModelData; }
        public String getInputDisplayName() { return inputDisplayName; }
        public String getInputPdcKey() { return inputPdcKey; }
        public String getInputPdcValue() { return inputPdcValue; }
        public boolean isCustomInput() { return isCustomInput; }

        public boolean matches(ItemStack item) {
            if (inputSpec != null) {
                return inputSpec.matches(item);
            }
            if (item == null || item.getAmount() <= 0) return false;
            if (item.getType() != inputMaterial) return false;

            if (org.bukkit.Bukkit.getServer() == null) {
                return true;
            }

            ItemMeta meta = item.getItemMeta();
            if (isCustomInput) {
                if (meta == null) return false;
                if (inputCustomModelData != null) {
                    if (!meta.hasCustomModelData() || meta.getCustomModelData() != inputCustomModelData.intValue()) return false;
                }
                if (inputDisplayName != null && !inputDisplayName.isEmpty()) {
                    if (!meta.hasDisplayName()) return false;
                    String expected = ColorUtil.color(inputDisplayName);
                    if (!expected.equals(meta.getDisplayName())) return false;
                }
                if (inputPdcKey != null && !inputPdcKey.isEmpty() && inputPdcValue != null) {
                    try {
                        String[] parts = inputPdcKey.split(":", 2);
                        NamespacedKey key = parts.length == 2 ? new NamespacedKey(parts[0], parts[1]) : NamespacedKey.minecraft(parts[0]);
                        String val = meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
                        if (!inputPdcValue.equals(val)) return false;
                    } catch (Exception ignored) {
                        return false;
                    }
                }
                return true;
            } else {
                // Vanilla matching: avoid consuming items with custom display names or custom model data
                if (meta != null && (meta.hasDisplayName() || meta.hasCustomModelData())) {
                    return false;
                }
                return true;
            }
        }
    }

    private static final Map<String, CompressionRecipe> RECIPES = new LinkedHashMap<>();
    private static final Map<String, CompressionRecipe> COMPACTOR_RECIPES = new LinkedHashMap<>();
    private static final Map<Material, List<CompressionRecipe>> RECIPES_BY_MATERIAL = new EnumMap<>(Material.class);
    private static final Map<Material, List<CompressionRecipe>> COMPACTOR_RECIPES_BY_MATERIAL = new EnumMap<>(Material.class);

    public static CompressionRecipe getRecipe(String id) {
        return RECIPES.get(id);
    }

    public static CompressionRecipe getRecipe(Material material) {
        if (material == null) return null;
        List<CompressionRecipe> list = RECIPES_BY_MATERIAL.get(material);
        if (list != null) {
            for (CompressionRecipe r : list) {
                if (!r.isCustomInput()) return r;
            }
        }
        for (CompressionRecipe r : RECIPES.values()) {
            if (r.getInputMaterial() == material && !r.isCustomInput()) {
                return r;
            }
        }
        return null;
    }

    public static Collection<CompressionRecipe> getAllRecipes() {
        return Collections.unmodifiableCollection(RECIPES.values());
    }

    public static CompressionRecipe getCompactorRecipe(String id) {
        return COMPACTOR_RECIPES.get(id);
    }

    public static CompressionRecipe getCompactorRecipe(Material material) {
        if (material == null) return null;
        List<CompressionRecipe> list = COMPACTOR_RECIPES_BY_MATERIAL.get(material);
        if (list != null) {
            for (CompressionRecipe r : list) {
                if (!r.isCustomInput()) return r;
            }
        }
        for (CompressionRecipe r : COMPACTOR_RECIPES.values()) {
            if (r.getInputMaterial() == material && !r.isCustomInput()) {
                return r;
            }
        }
        return null;
    }

    public static Collection<CompressionRecipe> getAllCompactorRecipes() {
        return Collections.unmodifiableCollection(COMPACTOR_RECIPES.values());
    }

    public static List<CompressionRecipe> getRecipesForMaterial(Material material) {
        if (material == null) return Collections.emptyList();
        List<CompressionRecipe> list = RECIPES_BY_MATERIAL.get(material);
        return list != null ? Collections.unmodifiableList(list) : Collections.emptyList();
    }

    public static List<CompressionRecipe> getCompactorRecipesForMaterial(Material material) {
        if (material == null) return Collections.emptyList();
        List<CompressionRecipe> list = COMPACTOR_RECIPES_BY_MATERIAL.get(material);
        return list != null ? Collections.unmodifiableList(list) : Collections.emptyList();
    }

    static {
        registerDefaultCompactorRecipes();
        loadRecipes();
    }

    private static void registerDefaultCompactorRecipes() {
        addDefaultCompactor("redstone_block", Material.REDSTONE, 9, Material.REDSTONE_BLOCK);
        addDefaultCompactor("coal_block", Material.COAL, 9, Material.COAL_BLOCK);
        addDefaultCompactor("iron_block", Material.IRON_INGOT, 9, Material.IRON_BLOCK);
        addDefaultCompactor("gold_block", Material.GOLD_INGOT, 9, Material.GOLD_BLOCK);
        addDefaultCompactor("diamond_block", Material.DIAMOND, 9, Material.DIAMOND_BLOCK);
        addDefaultCompactor("emerald_block", Material.EMERALD, 9, Material.EMERALD_BLOCK);
        addDefaultCompactor("lapis_block", Material.LAPIS_LAZULI, 9, Material.LAPIS_BLOCK);
        addDefaultCompactor("copper_block", Material.COPPER_INGOT, 9, Material.COPPER_BLOCK);
        addDefaultCompactor("hay_block", Material.WHEAT, 9, Material.HAY_BLOCK);
        addDefaultCompactor("slime_block", Material.SLIME_BALL, 9, Material.SLIME_BLOCK);
        addDefaultCompactor("quartz_block", Material.QUARTZ, 4, Material.QUARTZ_BLOCK);
        addDefaultCompactor("glowstone", Material.GLOWSTONE_DUST, 4, Material.GLOWSTONE);
        addDefaultCompactor("snow_block", Material.SNOWBALL, 4, Material.SNOW_BLOCK);
        addDefaultCompactor("clay_block", Material.CLAY_BALL, 4, Material.CLAY);
        addDefaultCompactor("magma_block", Material.MAGMA_CREAM, 4, Material.MAGMA_BLOCK);
        addDefaultCompactor("melon_block", Material.MELON_SLICE, 9, Material.MELON);
        addDefaultCompactor("bone_block", Material.BONE, 3, Material.BONE_BLOCK);
        addDefaultCompactor("sandstone", Material.SAND, 4, Material.SANDSTONE);
        addDefaultCompactor("red_sandstone", Material.RED_SAND, 4, Material.RED_SANDSTONE);
        addDefaultCompactor("packed_ice", Material.ICE, 9, Material.PACKED_ICE);
        addDefaultCompactor("blue_ice", Material.PACKED_ICE, 9, Material.BLUE_ICE);
        addDefaultCompactor("honeycomb_block", Material.HONEYCOMB, 4, Material.HONEYCOMB_BLOCK);
        addDefaultCompactor("amethyst_block", Material.AMETHYST_SHARD, 4, Material.AMETHYST_BLOCK);
        addDefaultCompactor("netherite_block", Material.NETHERITE_INGOT, 9, Material.NETHERITE_BLOCK);
        addDefaultCompactor("raw_copper_block", Material.RAW_COPPER, 9, Material.RAW_COPPER_BLOCK);
        addDefaultCompactor("raw_iron_block", Material.RAW_IRON, 9, Material.RAW_IRON_BLOCK);
        addDefaultCompactor("raw_gold_block", Material.RAW_GOLD, 9, Material.RAW_GOLD_BLOCK);
        addDefaultCompactor("prismarine", Material.PRISMARINE_SHARD, 4, Material.PRISMARINE);
        addDefaultCompactor("dried_kelp_block", Material.DRIED_KELP, 9, Material.DRIED_KELP_BLOCK);
    }

    private static void addDefaultCompactor(String id, Material input, int amount, Material output) {
        CustomItemSpec inSpec = new CustomItemSpec(input, null, amount, 1.0, null, null, null, false, null, null);
        CustomItemSpec outSpec = new CustomItemSpec(output, null, 1, 1.0, null, null, null, false, null, null);
        CompressionRecipe recipe = new CompressionRecipe(id, inSpec, outSpec);
        COMPACTOR_RECIPES.put(id, recipe);
        COMPACTOR_RECIPES_BY_MATERIAL.computeIfAbsent(input, k -> new ArrayList<>()).add(recipe);
    }

    public static void loadRecipes() {
        ConfigurationSection superSec = null;
        ConfigurationSection compSec = null;

        if (MinionX.getInstance() != null && MinionX.getInstance().getRecipesConfig() != null) {
            superSec = MinionX.getInstance().getRecipesConfig().getConfigurationSection("compression-recipes");
            compSec = MinionX.getInstance().getRecipesConfig().getConfigurationSection("compactor-recipes");
        }
        if (superSec == null && MinionX.getInstance() != null && MinionX.getInstance().getConfig() != null) {
            superSec = MinionX.getInstance().getConfig().getConfigurationSection("compression-recipes");
        }
        if (compSec == null && MinionX.getInstance() != null && MinionX.getInstance().getConfig() != null) {
            compSec = MinionX.getInstance().getConfig().getConfigurationSection("compactor-recipes");
        }

        loadRecipes(superSec);
        loadCompactorRecipes(compSec);
    }

    public static void loadRecipes(ConfigurationSection section) {
        RECIPES.clear();
        RECIPES_BY_MATERIAL.clear();
        if (section == null) return;
        parseRecipeSection(section, RECIPES, RECIPES_BY_MATERIAL, 160);
    }

    public static void loadCompactorRecipes(ConfigurationSection section) {
        COMPACTOR_RECIPES.clear();
        COMPACTOR_RECIPES_BY_MATERIAL.clear();
        registerDefaultCompactorRecipes();
        if (section == null) return;
        parseRecipeSection(section, COMPACTOR_RECIPES, COMPACTOR_RECIPES_BY_MATERIAL, 9);
    }

    private static void parseRecipeSection(ConfigurationSection section, Map<String, CompressionRecipe> targetMap,
                                           Map<Material, List<CompressionRecipe>> indexMap, int defaultAmount) {
        for (String key : section.getKeys(false)) {
            ConfigurationSection recSec = section.getConfigurationSection(key);
            if (recSec == null) continue;

            CustomItemSpec inSpec;
            int amount = recSec.getInt("input-amount", defaultAmount);

            if (recSec.isConfigurationSection("input")) {
                ConfigurationSection inSec = recSec.getConfigurationSection("input");
                inSpec = CustomItemSpec.fromConfigSection(inSec);
                if (inSpec.getAmount() <= 1 && recSec.contains("input-amount")) {
                    inSpec = new CustomItemSpec(inSpec.getMaterial(), inSpec.getHookIdentifier(), amount, inSpec.getChance(),
                            inSpec.getDisplayName(), inSpec.getLore(), inSpec.getCustomModelData(), inSpec.isGlow(), inSpec.getPdcKey(), inSpec.getPdcValue());
                }
            } else {
                String inMatName = recSec.getString("input", "COBBLESTONE");
                Material inMat = Material.matchMaterial(inMatName != null ? inMatName : "COBBLESTONE");
                if (inMat == null) inMat = Material.COBBLESTONE;
                inSpec = new CustomItemSpec(inMat, null, amount, 1.0, null, null, null, false, null, null);
            }

            ConfigurationSection outSec = recSec.getConfigurationSection("output");
            if (outSec == null) continue;

            CustomItemSpec outSpec = CustomItemSpec.fromConfigSection(outSec);

            CompressionRecipe recipe = new CompressionRecipe(key, inSpec, outSpec);
            targetMap.put(key, recipe);
            if (recipe.getInputMaterial() != null) {
                indexMap.computeIfAbsent(recipe.getInputMaterial(), k -> new ArrayList<>()).add(recipe);
            }
        }
    }

    /**
     * Executes automatic compaction for equipped Compactor and/or Super Compactor upgrades.
     * Evaluates condition-based dual compaction: whichever condition is met (160x for enchanted,
     * 9x/4x for blocks) executes without reservation deadlocks.
     * Checks both minion internal inventory and linked storage chest.
     * Uses O(1) material index to avoid iterating unrelated recipes.
     */
    public static void processCompaction(Minion minion) {
        if (minion == null) return;
        boolean superComp = hasSuperCompactor(minion);
        boolean standardComp = hasStandardCompactor(minion);
        if (!superComp && !standardComp) return;

        Set<Material> materials = new HashSet<>();
        for (ItemStack item : minion.getInventory()) {
            if (item != null && item.getType() != Material.AIR) {
                materials.add(item.getType());
            }
        }
        if (minion.hasLinkedStorage() && minion.getStorageContents() != null) {
            for (ItemStack item : minion.getStorageContents()) {
                if (item != null && item.getType() != Material.AIR) {
                    materials.add(item.getType());
                }
            }
        }
        if (materials.isEmpty()) return;

        // 1. If Super Compactor is equipped, process high-tier 160x enchanted recipes for these materials
        if (superComp) {
            for (Material mat : materials) {
                List<CompressionRecipe> list = RECIPES_BY_MATERIAL.get(mat);
                if (list != null) {
                    for (CompressionRecipe recipe : list) {
                        compactRecipeAcrossContainers(minion, recipe);
                    }
                }
            }
        }

        // 2. If Standard Compactor is equipped, process 9x/4x block recipes for these materials
        if (standardComp) {
            for (Material mat : materials) {
                List<CompressionRecipe> list = COMPACTOR_RECIPES_BY_MATERIAL.get(mat);
                if (list != null) {
                    for (CompressionRecipe recipe : list) {
                        compactRecipeAcrossContainers(minion, recipe);
                    }
                }
            }
        }
    }

    /**
     * Backwards-compatible alias for processCompaction.
     */
    public static void processSuperCompaction(Minion minion) {
        processCompaction(minion);
    }

    /**
     * Backwards-compatible alias for standard compactor processing.
     */
    public static void processStandardCompaction(Minion minion) {
        processCompaction(minion);
    }

    private static void compactRecipeAcrossContainers(Minion minion, CompressionRecipe recipe) {
        int req = recipe.getInputAmount();
        int total = 0;
        for (ItemStack item : minion.getInventory()) {
            if (item != null && recipe.matches(item)) {
                total += item.getAmount();
            }
        }
        if (minion.hasLinkedStorage() && minion.getStorageContents() != null) {
            for (ItemStack item : minion.getStorageContents()) {
                if (item != null && recipe.matches(item)) {
                    total += item.getAmount();
                }
            }
        }

        if (total < req) return;

        int batches = total / req;
        int remainingToDeduct = batches * req;

        // 1. Deduct from minion internal inventory first to free up slots
        Iterator<ItemStack> invIt = minion.getInventory().iterator();
        while (invIt.hasNext() && remainingToDeduct > 0) {
            ItemStack item = invIt.next();
            if (item != null && recipe.matches(item)) {
                int take = Math.min(remainingToDeduct, item.getAmount());
                item.setAmount(item.getAmount() - take);
                remainingToDeduct -= take;
                if (item.getAmount() <= 0) {
                    invIt.remove();
                }
            }
        }

        // 2. Deduct remainder from linked storage chest if needed
        if (remainingToDeduct > 0 && minion.hasLinkedStorage() && minion.getStorageContents() != null) {
            Iterator<ItemStack> stIt = minion.getStorageContents().iterator();
            while (stIt.hasNext() && remainingToDeduct > 0) {
                ItemStack item = stIt.next();
                if (item != null && recipe.matches(item)) {
                    int take = Math.min(remainingToDeduct, item.getAmount());
                    item.setAmount(item.getAmount() - take);
                    remainingToDeduct -= take;
                    if (item.getAmount() <= 0) {
                        stIt.remove();
                    }
                }
            }
        }

        // 3. Add output stacks to inventory, storage, or auto-sell
        int outAmountPerBatch = recipe.getOutputSpec() != null ? Math.max(1, recipe.getOutputSpec().getAmount()) : 1;
        int totalOutputItems = batches * outAmountPerBatch;
        while (totalOutputItems > 0) {
            ItemStack outStack = recipe.createOutput();
            int stackSize = Math.min(totalOutputItems, outStack.getMaxStackSize());
            outStack.setAmount(stackSize);
            minion.addItem(outStack);
            totalOutputItems -= stackSize;
        }
    }

    public static boolean hasSuperCompactor(Minion minion) {
        if (minion == null) return false;
        if (minion.hasUpgrade(MinionUpgradeType.SUPER_COMPACTOR)) return true;
        CustomUpgrade u1 = minion.getCustomUpgrade1();
        if (u1 != null && u1.getBehavior() == CustomUpgrade.UpgradeBehavior.SUPER_COMPACTOR) return true;
        CustomUpgrade u2 = minion.getCustomUpgrade2();
        return u2 != null && u2.getBehavior() == CustomUpgrade.UpgradeBehavior.SUPER_COMPACTOR;
    }

    public static boolean hasStandardCompactor(Minion minion) {
        if (minion == null) return false;
        if (minion.hasUpgrade(MinionUpgradeType.COMPACTOR)) return true;
        CustomUpgrade u1 = minion.getCustomUpgrade1();
        if (u1 != null && u1.getBehavior() == CustomUpgrade.UpgradeBehavior.COMPACTOR) return true;
        CustomUpgrade u2 = minion.getCustomUpgrade2();
        return u2 != null && u2.getBehavior() == CustomUpgrade.UpgradeBehavior.COMPACTOR;
    }
}