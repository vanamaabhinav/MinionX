package com.minionx.api;

import com.minionx.hook.CustomItemHook;
import com.minionx.util.ColorUtil;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents a single bonus drop entry that can be produced by a minion upgrade.
 * Supports configurable drop chance, quantity, custom display name, custom model data,
 * and external custom item hooks (ItemsAdder, MMOItems, Nexo, Oraxen, EcoItems).
 */
@SuppressWarnings("deprecation")
public class BonusDropEntry {

    private final Material material;
    private final int amount;
    private final double chance; // 0.0 to 1.0 (e.g. 0.20 = 20%)
    private final String displayName;
    private final int customModelData;
    private final String hookId;

    public BonusDropEntry(Material material, int amount, double chance) {
        this(material, amount, chance, null, 0, null);
    }

    public BonusDropEntry(Material material, int amount, double chance, String displayName, int customModelData, String hookId) {
        this.material = material != null ? material : Material.POTATO;
        this.amount = Math.max(1, Math.min(64, amount));
        this.chance = Math.max(0.0, Math.min(1.0, chance));
        this.displayName = displayName != null ? ColorUtil.color(displayName) : null;
        this.customModelData = customModelData;
        this.hookId = hookId;
    }

    public static BonusDropEntry fromItemStack(ItemStack item, double chance) {
        if (item == null || item.getType() == Material.AIR) {
            return new BonusDropEntry(Material.POTATO, 1, chance);
        }
        Material mat = item.getType();
        int amount = Math.max(1, item.getAmount());
        String name = null;
        int cmd = 0;
        String hook = CustomItemHook.identifyCustomItem(item);
        if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                if (meta.hasDisplayName()) {
                    name = meta.getDisplayName();
                }
                if (meta.hasCustomModelData()) {
                    cmd = meta.getCustomModelData();
                }
            }
        }
        return new BonusDropEntry(mat, amount, chance, name, cmd, hook);
    }

    public Material getMaterial() {
        return material;
    }

    public int getAmount() {
        return amount;
    }

    public double getChance() {
        return chance;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getCustomModelData() {
        return customModelData;
    }

    public String getHookId() {
        return hookId;
    }

    /**
     * Creates an ItemStack representing this bonus drop.
     * Respects external custom item hooks when available.
     *
     * @return Fresh ItemStack instance
     */
    public ItemStack createItemStack() {
        if (hookId != null && !hookId.isEmpty()) {
            ItemStack customItem = CustomItemHook.getItem(hookId);
            if (customItem != null && customItem.getType() != Material.AIR) {
                ItemStack clone = customItem.clone();
                clone.setAmount(amount);
                return clone;
            }
        }

        ItemStack item = new ItemStack(material, amount);
        if ((displayName != null && !displayName.isEmpty()) || customModelData > 0) {
            try {
                if (org.bukkit.Bukkit.getServer() != null) {
                    ItemMeta meta = item.getItemMeta();
                    if (meta != null) {
                        if (displayName != null && !displayName.isEmpty()) {
                            meta.setDisplayName(displayName);
                        }
                        if (customModelData > 0) {
                            meta.setCustomModelData(customModelData);
                        }
                        item.setItemMeta(meta);
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return item;
    }

    /**
     * Serializes this bonus drop entry into a YAML-compatible configuration map.
     *
     * @return Map representation
     */
    public Map<String, Object> toConfigMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("material", material.name());
        map.put("amount", amount);
        map.put("chance", chance);
        if (displayName != null) {
            map.put("display-name", displayName);
        }
        if (customModelData > 0) {
            map.put("custom-model-data", customModelData);
        }
        if (hookId != null && !hookId.isEmpty()) {
            map.put("hook-id", hookId);
        }
        return map;
    }

    /**
     * Deserializes a bonus drop entry from a configuration map.
     *
     * @param map Raw map from YAML
     * @return Parsed BonusDropEntry or null if invalid
     */
    public static BonusDropEntry fromConfig(Map<?, ?> map) {
        if (map == null) return null;

        String matName = map.get("material") != null ? map.get("material").toString() : null;
        if (matName == null && map.get("item") != null) {
            matName = map.get("item").toString();
        }
        Material mat = matName != null ? Material.matchMaterial(matName) : null;
        if (mat == null) mat = Material.POTATO;

        int amount = 1;
        if (map.get("amount") instanceof Number num) {
            amount = num.intValue();
        } else if (map.get("amount") != null) {
            try {
                amount = Integer.parseInt(map.get("amount").toString().trim());
            } catch (NumberFormatException ignored) {
            }
        }

        double chance = 0.10;
        if (map.get("chance") instanceof Number num) {
            chance = num.doubleValue();
        } else if (map.get("chance") != null) {
            try {
                chance = Double.parseDouble(map.get("chance").toString().trim());
            } catch (NumberFormatException ignored) {
            }
        }

        String displayName = map.get("display-name") != null ? map.get("display-name").toString() : null;
        int customModelData = 0;
        if (map.get("custom-model-data") instanceof Number num) {
            customModelData = num.intValue();
        }

        String hookId = map.get("hook-id") != null ? map.get("hook-id").toString() : null;

        return new BonusDropEntry(mat, amount, chance, displayName, customModelData, hookId);
    }
}

