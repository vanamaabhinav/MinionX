package com.minionx.util;

import com.minionx.api.MinionDefinition;
import com.minionx.api.MinionTier;
import com.minionx.api.UpgradeRequirement;
import com.minionx.core.CompressionManager;
import com.minionx.core.Minion;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Helper to compute and validate minion tier upgrade requirements.
 * Supports configurable multi-item upgrade requirement lists, custom item hooks,
 * and automatic fallback to regular drop items or Enchanted Super Compactor items.
 */
@SuppressWarnings("deprecation")
public class UpgradeCostHelper {

    public static class Requirement {
        private final boolean enchanted;
        private final Material material;
        private final String displayName;
        private final int requiredAmount;
        private final CompressionManager.CompressionRecipe recipe;
        private final UpgradeRequirement spec;

        public Requirement(boolean enchanted, Material material, String displayName, int requiredAmount, CompressionManager.CompressionRecipe recipe) {
            this(enchanted, material, displayName, requiredAmount, recipe, null);
        }

        public Requirement(boolean enchanted, Material material, String displayName, int requiredAmount, CompressionManager.CompressionRecipe recipe, UpgradeRequirement spec) {
            this.enchanted = enchanted;
            this.material = material;
            this.displayName = displayName;
            this.requiredAmount = Math.max(1, requiredAmount);
            this.recipe = recipe;
            this.spec = spec;
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

        public UpgradeRequirement getSpec() {
            return spec;
        }
    }

    /**
     * Resolves the primary requirement for backward compatibility.
     *
     * @param minion Minion to evaluate
     * @return Primary Requirement
     */
    public static Requirement getRequirement(Minion minion) {
        List<Requirement> reqs = getRequirements(minion);
        return !reqs.isEmpty() ? reqs.get(0) : new Requirement(false, Material.COBBLESTONE, "cobblestone", 80, null);
    }

    /**
     * Resolves all required items needed to upgrade the given minion to the next tier.
     * Checks per-minion tier overrides first, then global tier definitions, and falls
     * back to the Super Compactor / drop item calculation.
     *
     * @param minion Minion to evaluate
     * @return List of Requirement items
     */
    public static List<Requirement> getRequirements(Minion minion) {
        if (minion == null) {
            return Collections.singletonList(new Requirement(false, Material.COBBLESTONE, "cobblestone", 80, null));
        }

        int currentTier = minion.getTier();
        MinionDefinition def = minion.getDefinition();

        // 1. Check per-minion tier override for multi-item requirements
        if (def != null) {
            MinionTier.TierData customTierData = def.getTierData(currentTier);
            if (customTierData != null && customTierData.hasCustomRequirements()) {
                List<Requirement> list = new ArrayList<>();
                for (UpgradeRequirement ur : customTierData.getRequirements()) {
                    list.add(new Requirement(false, ur.getMaterial(), ur.getDisplayName(), ur.getAmount(), null, ur));
                }
                return list;
            }
        }

        // 2. Check global tiers.yml for multi-item requirements
        MinionTier.TierData globalTierData = MinionTier.getTierData(currentTier);
        if (globalTierData != null && globalTierData.hasCustomRequirements()) {
            List<Requirement> list = new ArrayList<>();
            for (UpgradeRequirement ur : globalTierData.getRequirements()) {
                list.add(new Requirement(false, ur.getMaterial(), ur.getDisplayName(), ur.getAmount(), null, ur));
            }
            return list;
        }

        // 3. Fallback: Standard drop / enchanted calculation
        int rawCost = minion.getUpgradeCost();
        Material mat = minion.getType() != null ? minion.getType().getDropMaterial() : Material.COBBLESTONE;
        if (def != null && def.getExactDropItem() != null && def.getExactDropItem().getType() != Material.AIR) {
            mat = def.getExactDropItem().getType();
        }

        CompressionManager.CompressionRecipe recipe = CompressionManager.getRecipe(mat);
        boolean requireEnchanted = (currentTier >= 5 || rawCost > 2000);

        if (requireEnchanted) {
            int inputRatio = recipe != null ? recipe.getInputAmount() : 160;
            int count;
            if (rawCost <= 64 && currentTier >= 5) {
                count = Math.max(1, rawCost);
            } else if (rawCost >= 1024) {
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
            return Collections.singletonList(new Requirement(true, mat, name, count, recipe));
        } else {
            String name = formatName(mat).toLowerCase();
            return Collections.singletonList(new Requirement(false, mat, name, rawCost, recipe));
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

    /**
     * Checks whether a player possesses all required items for an upgrade.
     *
     * @param player Player to inspect
     * @param requirements List of item requirements
     * @return true if all items are present in sufficient quantities
     */
    public static boolean hasAllRequirements(Player player, List<Requirement> requirements) {
        if (player == null || requirements == null || requirements.isEmpty()) return true;
        Map<Requirement, Integer> needed = new LinkedHashMap<>();
        for (Requirement r : requirements) {
            needed.put(r, needed.getOrDefault(r, 0) + r.getRequiredAmount());
        }
        for (Map.Entry<Requirement, Integer> entry : needed.entrySet()) {
            if (countMatchingItems(player, entry.getKey()) < entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    public static boolean matches(ItemStack item, Requirement req) {
        if (item == null || item.getAmount() <= 0 || req == null) return false;

        // Custom upgrade requirement spec match
        if (req.getSpec() != null) {
            return req.getSpec().matches(item);
        }

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

    /**
     * Deducts all required items from the player's personal inventory.
     *
     * @param player Player from whom to remove items
     * @param requirements Requirements to consume
     */
    public static void removeAllRequirements(Player player, List<Requirement> requirements) {
        if (player == null || requirements == null) return;
        for (Requirement r : requirements) {
            removeMatchingItems(player, r);
        }
    }
}
