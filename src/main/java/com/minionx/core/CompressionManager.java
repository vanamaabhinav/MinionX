package com.minionx.core;

import com.minionx.MinionX;
import com.minionx.api.CustomUpgrade;
import com.minionx.api.MinionUpgradeType;
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
        private final Material inputMaterial;
        private final int inputAmount;
        private final ItemStack outputItem;

        // Custom matching parameters
        private final Integer inputCustomModelData;
        private final String inputDisplayName;
        private final String inputPdcKey;
        private final String inputPdcValue;
        private final boolean isCustomInput;

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
        }

        public String getId() { return id; }
        public Material getInputMaterial() { return inputMaterial; }
        public int getInputAmount() { return inputAmount; }
        public ItemStack createOutput() { return outputItem.clone(); }
        public Integer getInputCustomModelData() { return inputCustomModelData; }
        public String getInputDisplayName() { return inputDisplayName; }
        public String getInputPdcKey() { return inputPdcKey; }
        public String getInputPdcValue() { return inputPdcValue; }
        public boolean isCustomInput() { return isCustomInput; }

        public boolean matches(ItemStack item) {
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

            Material inMat;
            Integer inCmd = null;
            String inName = null;
            String inPdcKey = null;
            String inPdcVal = null;
            boolean isCustom = false;

            if (recSec.isConfigurationSection("input")) {
                ConfigurationSection inSec = recSec.getConfigurationSection("input");
                String inMatName = inSec != null ? inSec.getString("material", "COBBLESTONE") : "COBBLESTONE";
                inMat = Material.matchMaterial(inMatName != null ? inMatName : "COBBLESTONE");
                if (inSec != null && inSec.contains("custom-model-data")) {
                    inCmd = inSec.getInt("custom-model-data");
                    isCustom = true;
                }
                if (inSec != null && inSec.contains("display-name")) {
                    inName = inSec.getString("display-name");
                    isCustom = true;
                }
                if (inSec != null && inSec.contains("pdc-key")) {
                    inPdcKey = inSec.getString("pdc-key");
                    inPdcVal = inSec.getString("pdc-value", "");
                    isCustom = true;
                }
            } else {
                String inMatName = recSec.getString("input", "COBBLESTONE");
                inMat = Material.matchMaterial(inMatName != null ? inMatName : "COBBLESTONE");
            }

            if (inMat == null) continue;
            int amount = recSec.getInt("input-amount", 160);

            ConfigurationSection outSec = recSec.getConfigurationSection("output");
            if (outSec == null) continue;

            String outMatName = outSec.getString("material", inMat.name());
            Material outMat = Material.matchMaterial(outMatName != null ? outMatName : inMat.name());
            if (outMat == null) outMat = inMat;

            String dispName = outSec.getString("display-name", "&aEnchanted " + inMat.name());
            boolean glow = outSec.getBoolean("glow", true);
            List<String> lore = outSec.getStringList("lore");
            Integer outCmd = outSec.contains("custom-model-data") ? outSec.getInt("custom-model-data") : null;
            String outPdcKey = outSec.getString("pdc-key");
            String outPdcVal = outSec.getString("pdc-value");

            ItemStack output = new ItemStack(outMat, 1);
            if (org.bukkit.Bukkit.getServer() != null) {
                ItemMeta meta = output.getItemMeta();
                if (meta != null) {
                    if (dispName != null && !dispName.isEmpty()) {
                        meta.setDisplayName(ColorUtil.color(dispName));
                    }
                    if (lore != null && !lore.isEmpty()) {
                        List<String> coloredLore = new ArrayList<>();
                        for (String l : lore) {
                            coloredLore.add(ColorUtil.color(l));
                        }
                        meta.setLore(coloredLore);
                    }
                    if (glow) {
                        meta.addEnchant(Enchantment.DURABILITY, 1, true);
                        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                    }
                    if (outCmd != null) {
                        meta.setCustomModelData(outCmd);
                    }
                    if (outPdcKey != null && !outPdcKey.isEmpty() && outPdcVal != null) {
                        try {
                            String[] parts = outPdcKey.split(":", 2);
                            NamespacedKey nKey = parts.length == 2 ? new NamespacedKey(parts[0], parts[1]) : NamespacedKey.minecraft(parts[0]);
                            meta.getPersistentDataContainer().set(nKey, PersistentDataType.STRING, outPdcVal);
                        } catch (Exception ignored) {}
                    }
                    output.setItemMeta(meta);
                }
            }

            CompressionRecipe recipe = new CompressionRecipe(key, inMat, amount, output, inCmd, inName, inPdcKey, inPdcVal, isCustom);
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