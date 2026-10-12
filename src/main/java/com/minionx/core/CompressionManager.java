package com.minionx.core;

import com.minionx.MinionX;
import com.minionx.api.CustomUpgrade;
import com.minionx.api.MinionUpgradeType;
import com.minionx.item.CustomItemSpec;
import com.minionx.util.ColorUtil;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
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

    public static CompressionRecipe getRecipe(String id) {
        return RECIPES.get(id);
    }

    public static CompressionRecipe getRecipe(Material material) {
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

    static {
        loadRecipes();
    }

    public static void loadRecipes() {
        ConfigurationSection section = null;
        if (MinionX.getInstance() != null && MinionX.getInstance().getRecipesConfig() != null) {
            section = MinionX.getInstance().getRecipesConfig().getConfigurationSection("compression-recipes");
        }
        if (section == null && MinionX.getInstance() != null && MinionX.getInstance().getConfig() != null) {
            section = MinionX.getInstance().getConfig().getConfigurationSection("compression-recipes");
        }
        loadRecipes(section);
    }

    public static void loadRecipes(ConfigurationSection section) {
        RECIPES.clear();
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            ConfigurationSection recSec = section.getConfigurationSection(key);
            if (recSec == null) continue;

            CustomItemSpec inSpec;
            int amount = recSec.getInt("input-amount", 160);

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
            RECIPES.put(key, recipe);
        }
    }

    /**
     * Executes automatic super compaction if the minion has a Super Compactor upgrade equipped.
     */
    public static void processSuperCompaction(Minion minion) {
        if (!hasSuperCompactor(minion)) return;

        for (CompressionRecipe recipe : RECIPES.values()) {
            int req = recipe.getInputAmount();

            int count = 0;
            for (ItemStack item : minion.getInventory()) {
                if (recipe.matches(item)) {
                    count += item.getAmount();
                }
            }

            while (count >= req) {
                int remainingToDeduct = req;
                Iterator<ItemStack> it = minion.getInventory().iterator();
                while (it.hasNext()) {
                    ItemStack item = it.next();
                    if (recipe.matches(item)) {
                        int take = Math.min(remainingToDeduct, item.getAmount());
                        item.setAmount(item.getAmount() - take);
                        remainingToDeduct -= take;
                        if (item.getAmount() <= 0) {
                            it.remove();
                        }
                        if (remainingToDeduct <= 0) break;
                    }
                }

                minion.addItem(recipe.createOutput());
                count -= req;
            }
        }
    }

    private static boolean hasSuperCompactor(Minion minion) {
        if (minion.hasUpgrade(MinionUpgradeType.SUPER_COMPACTOR)) return true;
        CustomUpgrade u1 = minion.getCustomUpgrade1();
        if (u1 != null && u1.getBehavior() == CustomUpgrade.UpgradeBehavior.SUPER_COMPACTOR) return true;
        CustomUpgrade u2 = minion.getCustomUpgrade2();
        return u2 != null && u2.getBehavior() == CustomUpgrade.UpgradeBehavior.SUPER_COMPACTOR;
    }
}