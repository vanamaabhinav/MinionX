package com.minionx.util;

import com.minionx.api.MinionDefinition;
import com.minionx.core.CompressionManager;
import com.minionx.core.Minion;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Helper to compute and validate minion tier upgrade requirements.
 * Tiers 1-4 require regular drop items.
 * Big upgrades (Tier 5+ or costs >= 640) require Enchanted versions to fit
 * within a player's physical 36-slot inventory.
 */
public class UpgradeCostHelper {

    public static class Requirement {
        private final boolean enchanted;
        private final Material material;
        private final String displayName;
        private final int requiredAmount;
        private final CompressionManager.CompressionRecipe recipe;

        public Requirement(boolean enchanted, Material material, String displayName, int requiredAmount, CompressionManager.CompressionRecipe recipe) {
            this.enchanted = enchanted;
            this.material = material;
            this.displayName = displayName;
            this.requiredAmount = Math.max(1, requiredAmount);
            this.recipe = recipe;
        }

        public boolean isEnchanted() {
            return enchanted;
        }

        public Material getMaterial() {
            return material;
        }

        public String getDisplayName() {
            return displayName;
        }

        public int getRequiredAmount() {
            return requiredAmount;
        }

        public CompressionManager.CompressionRecipe getRecipe() {
            return recipe;
        }
    }

    public static Requirement getRequirement(Minion minion) {
        if (minion == null) {
            return new Requirement(false, Material.COBBLESTONE, "cobblestone", 80, null);
        }

        int currentTier = minion.getTier();
        int rawCost = minion.getUpgradeCost();
        Material mat = minion.getType() != null ? minion.getType().getDropMaterial() : Material.COBBLESTONE;
        MinionDefinition def = minion.getDefinition();
        if (def != null && def.getExactDropItem() != null && def.getExactDropItem().getType() != Material.AIR) {
            mat = def.getExactDropItem().getType();
        }

        CompressionManager.CompressionRecipe recipe = CompressionManager.getRecipe(mat);

        // Big upgrades (Tier 5+ or cost > 2000) require Enchanted versions
        boolean requireEnchanted = (currentTier >= 5 || rawCost > 2000);

        if (requireEnchanted) {
            int inputRatio = recipe != null ? recipe.getInputAmount() : 160;
            int count;
            if (rawCost <= 64 && currentTier >= 5) {
                count = Math.max(1, rawCost);
            } else if (rawCost >= 1024) {
                // Exponential tier progression (1024, 2048, 4096, 8192, 16384, 32768, 65536)
                // Maps to clean powers of 2 (Tier 5: 8, Tier 6: 16, Tier 7: 32, Tier 8: 64, Tier 9: 128, Tier 10: 256, Tier 11: 512)
                int standardCount = 8 * (1 << Math.min(10, currentTier - 5));
                if (Math.abs(rawCost - (standardCount * 128)) < (standardCount * 64)) {
                    count = standardCount;
                } else {
                    count = Math.max(1, (int) Math.round((double) rawCost / inputRatio));
                }
            } else {
                count = Math.max(1, (int) Math.round((double) rawCost / inputRatio));
            }
            String name = null;
            if (recipe != null) {
                try {
                    ItemStack out = recipe.createOutput();
                    if (out.hasItemMeta() && out.getItemMeta() != null && out.getItemMeta().hasDisplayName()) {
                        name = out.getItemMeta().getDisplayName();
                    }
                } catch (Throwable ignored) {}
            }
            if (name == null || name.isBlank()) {
                name = ColorUtil.color("&aEnchanted " + formatName(mat));
            }
            return new Requirement(true, mat, name, count, recipe);
        } else {
            String name = formatName(mat).toLowerCase();
            return new Requirement(false, mat, name, rawCost, recipe);
        }
    }

    public static String formatName(Material mat) {
        if (mat == null) return "Item";
        String s = mat.name().replace('_', ' ').toLowerCase();
        String[] parts = s.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (!p.isEmpty()) {
                sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(" ");
            }
        }
        return sb.toString().trim();
    }

    public static int countMatchingItems(Player player, Requirement req) {
        if (player == null || req == null) return 0;
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getAmount() <= 0) continue;
            if (matches(item, req)) {
                count += item.getAmount();
            }
        }
        return count;
    }

    public static boolean hasEnoughItems(Player player, Requirement req) {
        return countMatchingItems(player, req) >= req.getRequiredAmount();
    }

    public static boolean matches(ItemStack item, Requirement req) {
        if (item == null || item.getAmount() <= 0 || req == null) return false;

        if (req.isEnchanted()) {
            if (req.getRecipe() != null) {
                try {
                    ItemStack template = req.getRecipe().createOutput();
                    if (item.getType() != template.getType()) return false;
                    if (template.hasItemMeta() && template.getItemMeta() != null && template.getItemMeta().hasDisplayName()) {
                        String reqName = ChatColor.stripColor(template.getItemMeta().getDisplayName());
                        if (item.hasItemMeta() && item.getItemMeta() != null && item.getItemMeta().hasDisplayName()) {
                            String itemName = ChatColor.stripColor(item.getItemMeta().getDisplayName());
                            return itemName.equalsIgnoreCase(reqName);
                        }
                        return false;
                    }
                } catch (Throwable ignored) {}
            }
            // Fallback for enchanted items
            if (item.getType() != req.getMaterial()) return false;
            try {
                if (item.hasItemMeta() && item.getItemMeta() != null && item.getItemMeta().hasDisplayName()) {
                    return item.getItemMeta().getDisplayName().contains("Enchanted");
                }
            } catch (Throwable ignored) {}
            return false;
        } else {
            // Regular item: must match material and must NOT be an enchanted item
            if (item.getType() != req.getMaterial()) return false;
            try {
                if (item.hasItemMeta() && item.getItemMeta() != null && item.getItemMeta().hasDisplayName()) {
                    String name = item.getItemMeta().getDisplayName();
                    if (name.contains("Enchanted")) {
                        return false;
                    }
                }
            } catch (Throwable ignored) {}
            return true;
        }
    }

    public static void removeMatchingItems(Player player, Requirement req) {
        if (player == null || req == null) return;
        int remaining = req.getRequiredAmount();
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getAmount() <= 0) continue;
            if (matches(item, req)) {
                int take = Math.min(remaining, item.getAmount());
                item.setAmount(item.getAmount() - take);
                remaining -= take;
                if (remaining <= 0) break;
            }
        }
    }
}
