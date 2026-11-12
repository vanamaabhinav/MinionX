package com.minionx.api;

import com.minionx.util.ColorUtil;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents a single configurable item requirement needed to upgrade a minion tier.
 * Supports vanilla materials, custom display names, custom model data, and PersistentDataContainer keys.
 */
@SuppressWarnings("deprecation")
public class UpgradeRequirement {

    private final Material material;
    private final int amount;
    private final String displayName;
    private final int customModelData;
    private final String pdcKey;
    private final String pdcValue;

    public UpgradeRequirement(Material material, int amount) {
        this(material, amount, null, 0, null, null);
    }

    public UpgradeRequirement(Material material, int amount, String displayName) {
        this(material, amount, displayName, 0, null, null);
    }

    public UpgradeRequirement(Material material, int amount, String displayName, int customModelData, String pdcKey, String pdcValue) {
        this.material = material != null ? material : Material.COBBLESTONE;
        this.amount = Math.max(1, amount);
        this.displayName = displayName != null ? ColorUtil.color(displayName) : null;
        this.customModelData = customModelData;
        this.pdcKey = pdcKey;
        this.pdcValue = pdcValue;
    }

    public Material getMaterial() {
        return material;
    }

    public int getAmount() {
        return amount;
    }

    public String getDisplayName() {
        return displayName != null ? displayName : formatName(material);
    }

    public boolean hasCustomDisplayName() {
        return displayName != null && !displayName.isEmpty();
    }

    public int getCustomModelData() {
        return customModelData;
    }

    public String getPdcKey() {
        return pdcKey;
    }

    public String getPdcValue() {
        return pdcValue;
    }

    /**
     * Checks if a given item matches this upgrade requirement.
     *
     * @param stack Item stack to test
     * @return true if matching
     */
    public boolean matches(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR || stack.getAmount() <= 0) {
            return false;
        }
        if (stack.getType() != this.material) {
            return false;
        }
        if (customModelData > 0) {
            try {
                if (org.bukkit.Bukkit.getServer() == null || !stack.hasItemMeta() || !stack.getItemMeta().hasCustomModelData() || stack.getItemMeta().getCustomModelData() != customModelData) {
                    return false;
                }
            } catch (Exception ignored) {
                return false;
            }
        }
        if (displayName != null && !displayName.isEmpty()) {
            try {
                if (org.bukkit.Bukkit.getServer() == null || !stack.hasItemMeta() || !stack.getItemMeta().hasDisplayName()) {
                    return false;
                }
                String stackName = org.bukkit.ChatColor.stripColor(stack.getItemMeta().getDisplayName());
                String expectedName = org.bukkit.ChatColor.stripColor(displayName);
                if (!stackName.equalsIgnoreCase(expectedName)) {
                    return false;
                }
            } catch (Exception ignored) {
                return false;
            }
        }
        if (pdcKey != null && !pdcKey.isEmpty()) {
            try {
                if (org.bukkit.Bukkit.getServer() == null || !stack.hasItemMeta()) return false;
                var pdc = stack.getItemMeta().getPersistentDataContainer();
                var namespaced = org.bukkit.NamespacedKey.fromString(pdcKey);
                if (namespaced == null || !pdc.has(namespaced, org.bukkit.persistence.PersistentDataType.STRING)) {
                    return false;
                }
                if (pdcValue != null && !pdcValue.isEmpty()) {
                    String val = pdc.get(namespaced, org.bukkit.persistence.PersistentDataType.STRING);
                    if (!pdcValue.equals(val)) return false;
                }
            } catch (Exception ignored) {
                return false;
            }
        } else if ((displayName == null || displayName.isEmpty()) && customModelData <= 0) {
            // Plain vanilla item requirement: strictly reject items with compressed PDC tag, custom model data, or custom display names
            try {
                if (stack.hasItemMeta()) {
                    org.bukkit.inventory.meta.ItemMeta meta = stack.getItemMeta();
                    if (meta != null) {
                        if (meta.hasDisplayName() || meta.hasCustomModelData()) {
                            return false;
                        }
                        var pdc = meta.getPersistentDataContainer();
                        if (pdc.has(com.minionx.core.CompressionManager.COMPRESSED_KEY, org.bukkit.persistence.PersistentDataType.STRING)) {
                            return false;
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return true;
    }

    public Map<String, Object> toConfigMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("item", material.name());
        map.put("amount", amount);
        if (displayName != null) {
            map.put("display-name", displayName);
        }
        if (customModelData > 0) {
            map.put("custom-model-data", customModelData);
        }
        if (pdcKey != null) {
            map.put("pdc-key", pdcKey);
            if (pdcValue != null) {
                map.put("pdc-value", pdcValue);
            }
        }
        return map;
    }

    public static UpgradeRequirement fromConfig(Map<?, ?> map) {
        if (map == null) return null;
        Object itemObj = map.get("item") != null ? map.get("item") : map.get("material");
        String matStr = itemObj != null ? itemObj.toString() : "COBBLESTONE";
        Material mat = Material.matchMaterial(matStr);
        if (mat == null) mat = Material.COBBLESTONE;

        int amount = 1;
        Object amtObj = map.get("amount") != null ? map.get("amount") : (map.get("quantity") != null ? map.get("quantity") : map.get("count"));
        if (amtObj instanceof Number num) {
            amount = num.intValue();
        } else if (amtObj != null) {
            try { amount = Integer.parseInt(amtObj.toString()); } catch (NumberFormatException ignored) {}
        }

        String displayName = map.get("display-name") != null ? map.get("display-name").toString() : null;
        int cmd = 0;
        if (map.get("custom-model-data") instanceof Number cNum) {
            cmd = cNum.intValue();
        }
        String pdcK = map.get("pdc-key") != null ? map.get("pdc-key").toString() : null;
        String pdcV = map.get("pdc-value") != null ? map.get("pdc-value").toString() : null;

        return new UpgradeRequirement(mat, amount, displayName, cmd, pdcK, pdcV);
    }

    private static String formatName(Material mat) {
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
}
