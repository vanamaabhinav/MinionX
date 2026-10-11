package com.skyblock.minions.core;

import com.skyblock.minions.SkyBlockMinions;
import com.skyblock.minions.api.CustomUpgrade;
import com.skyblock.minions.api.MinionUpgradeType;
import com.skyblock.minions.util.ColorUtil;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Handles Super Compactor recipe compression in Minion inventories.
 */
public class CompressionManager {

    public static class CompressionRecipe {
        private final Material inputMaterial;
        private final int inputAmount;
        private final ItemStack outputItem;

        public CompressionRecipe(Material inputMaterial, int inputAmount, ItemStack outputItem) {
            this.inputMaterial = inputMaterial;
            this.inputAmount = inputAmount;
            this.outputItem = outputItem;
        }

        public Material getInputMaterial() {
            return inputMaterial;
        }

        public int getInputAmount() {
            return inputAmount;
        }

        public ItemStack createOutput() {
            return outputItem.clone();
        }
    }

    private static final Map<Material, CompressionRecipe> RECIPES = new HashMap<>();

    static {
        loadRecipes();
    }

    public static void loadRecipes() {
        RECIPES.clear();
        if (SkyBlockMinions.getInstance() == null || SkyBlockMinions.getInstance().getConfig() == null) {
            return;
        }

        ConfigurationSection section = SkyBlockMinions.getInstance().getConfig().getConfigurationSection("compression-recipes");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            ConfigurationSection recSec = section.getConfigurationSection(key);
            if (recSec == null) continue;

            String inMatName = recSec.getString("input", "COBBLESTONE");
            Material inMat = Material.matchMaterial(inMatName);
            if (inMat == null) continue;

            int amount = recSec.getInt("input-amount", 160);

            ConfigurationSection outSec = recSec.getConfigurationSection("output");
            if (outSec == null) continue;

            String outMatName = outSec.getString("material", inMatName);
            Material outMat = Material.matchMaterial(outMatName);
            if (outMat == null) outMat = inMat;

            String dispName = outSec.getString("display-name", "&aEnchanted " + inMat.name());
            boolean glow = outSec.getBoolean("glow", true);

            ItemStack output = new ItemStack(outMat, 1);
            ItemMeta meta = output.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ColorUtil.color(dispName));
                if (glow) {
                    meta.addEnchant(Enchantment.DURABILITY, 1, true);
                    meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                }
                output.setItemMeta(meta);
            }

            RECIPES.put(inMat, new CompressionRecipe(inMat, amount, output));
        }
    }

    /**
     * Executes automatic super compaction if the minion has a Super Compactor upgrade equipped.
     */
    public static void processSuperCompaction(Minion minion) {
        if (!hasSuperCompactor(minion)) return;

        for (CompressionRecipe recipe : RECIPES.values()) {
            Material inMat = recipe.getInputMaterial();
            int req = recipe.getInputAmount();

            int count = 0;
            for (ItemStack item : minion.getInventory()) {
                if (item != null && item.getType() == inMat && (!item.hasItemMeta() || (item.getItemMeta() != null && !item.getItemMeta().hasDisplayName()))) {
                    count += item.getAmount();
                }
            }

            while (count >= req) {
                // Deduct req raw items
                int remainingToDeduct = req;
                Iterator<ItemStack> it = minion.getInventory().iterator();
                while (it.hasNext()) {
                    ItemStack item = it.next();
                    if (item != null && item.getType() == inMat && (!item.hasItemMeta() || (item.getItemMeta() != null && !item.getItemMeta().hasDisplayName()))) {
                        int take = Math.min(remainingToDeduct, item.getAmount());
                        item.setAmount(item.getAmount() - take);
                        remainingToDeduct -= take;
                        if (item.getAmount() <= 0) {
                            it.remove();
                        }
                        if (remainingToDeduct <= 0) break;
                    }
                }

                // Add compressed item
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