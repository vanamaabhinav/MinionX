package com.minionx.item;

import com.minionx.hook.CustomItemHook;
import com.minionx.util.ColorUtil;
import org.bukkit.Bukkit;
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
 * Unified custom item specification supporting identical configuration
 * schemas across recipes.yml, minions.yml, fuels.yml, and in-game studios.
 */
@SuppressWarnings("deprecation")
public class CustomItemSpec {

    private final Material material;
    private final String hookIdentifier;
    private final int amount;
    private final double chance; // 0.0 to 1.0 (used in drops)
    private final String displayName;
    private final List<String> lore;
    private final Integer customModelData;
    private final boolean glow;
    private final String pdcKey;
    private final String pdcValue;

    public CustomItemSpec(Material material, String hookIdentifier, int amount, double chance,
                          String displayName, List<String> lore, Integer customModelData,
                          boolean glow, String pdcKey, String pdcValue) {
        this.material = material != null ? material : Material.COBBLESTONE;
        this.hookIdentifier = hookIdentifier;
        this.amount = Math.max(1, amount);
        this.chance = Math.clamp(chance, 0.0, 1.0);
        this.displayName = displayName;
        this.lore = lore != null ? new ArrayList<>(lore) : Collections.emptyList();
        this.customModelData = customModelData;
        this.glow = glow;
        this.pdcKey = pdcKey;
        this.pdcValue = pdcValue;
    }

    public static CustomItemSpec fromConfigSection(ConfigurationSection sec) {
        if (sec == null) return new CustomItemSpec(Material.COBBLESTONE, null, 1, 1.0, null, null, null, false, null, null);

        String matName = sec.getString("material", sec.getString("item", "COBBLESTONE"));
        String hook = null;
        Material mat = Material.COBBLESTONE;

        if (matName != null && matName.contains(":")) {
            hook = matName;
            ItemStack resolved = CustomItemHook.getItem(matName);
            if (resolved != null) mat = resolved.getType();
        } else if (matName != null) {
            Material match = Material.matchMaterial(matName.toUpperCase());
            if (match != null) mat = match;
        }

        int amount = sec.getInt("amount", sec.getInt("input-amount", 1));
        double chance = sec.getDouble("chance", 1.0);
        String name = sec.getString("display-name", sec.getString("name"));
        List<String> lore = sec.getStringList("lore");
        Integer cmd = sec.contains("custom-model-data") ? sec.getInt("custom-model-data") : null;
        boolean glow = sec.getBoolean("glow", sec.getBoolean("enchanted", false));
        String pdcK = sec.getString("pdc-key", sec.getString("pdc.key"));
        String pdcV = sec.getString("pdc-value", sec.getString("pdc.value", ""));

        return new CustomItemSpec(mat, hook, amount, chance, name, lore, cmd, glow, pdcK, pdcV);
    }

    public static CustomItemSpec fromMap(Map<?, ?> map) {
        if (map == null) return new CustomItemSpec(Material.COBBLESTONE, null, 1, 1.0, null, null, null, false, null, null);

        Object matObj = map.containsKey("material") ? map.get("material") : map.get("item");
        String matName = matObj != null ? matObj.toString() : "COBBLESTONE";
        String hook = null;
        Material mat = Material.COBBLESTONE;

        if (matName.contains(":")) {
            hook = matName;
            ItemStack resolved = CustomItemHook.getItem(matName);
            if (resolved != null) mat = resolved.getType();
        } else {
            Material match = Material.matchMaterial(matName.toUpperCase());
            if (match != null) mat = match;
        }

        int amount = 1;
        if (map.containsKey("amount")) {
            try { amount = Integer.parseInt(map.get("amount").toString()); } catch (Exception ignored) {}
        }
        double chance = 1.0;
        if (map.containsKey("chance")) {
            try { chance = Double.parseDouble(map.get("chance").toString()); } catch (Exception ignored) {}
        }

        String name = map.containsKey("display-name") ? map.get("display-name").toString() :
                (map.containsKey("name") ? map.get("name").toString() : null);

        List<String> lore = new ArrayList<>();
        if (map.containsKey("lore") && map.get("lore") instanceof List<?> list) {
            for (Object o : list) {
                if (o != null) lore.add(o.toString());
            }
        }

        Integer cmd = null;
        if (map.containsKey("custom-model-data")) {
            try { cmd = Integer.parseInt(map.get("custom-model-data").toString()); } catch (Exception ignored) {}
        }

        boolean glow = false;
        if (map.containsKey("glow")) glow = Boolean.parseBoolean(map.get("glow").toString());
        else if (map.containsKey("enchanted")) glow = Boolean.parseBoolean(map.get("enchanted").toString());

        String pdcK = map.containsKey("pdc-key") ? map.get("pdc-key").toString() : null;
        String pdcV = map.containsKey("pdc-value") ? map.get("pdc-value").toString() : "";

        return new CustomItemSpec(mat, hook, amount, chance, name, lore, cmd, glow, pdcK, pdcV);
    }

    public static CustomItemSpec fromItemStack(ItemStack stack, double chance) {
        if (stack == null || stack.getType() == Material.AIR) {
            return new CustomItemSpec(Material.AIR, null, 1, chance, null, null, null, false, null, null);
        }

        Material mat = stack.getType();
        int amount = stack.getAmount();
        String name = null;
        List<String> lore = null;
        Integer cmd = null;
        boolean glow = false;

        if (Bukkit.getServer() != null) {
            try {
                if (stack.hasItemMeta()) {
                    ItemMeta meta = stack.getItemMeta();
                    if (meta != null) {
                        if (meta.hasDisplayName()) name = meta.getDisplayName();
                        if (meta.hasLore()) lore = meta.getLore();
                        if (meta.hasCustomModelData()) cmd = meta.getCustomModelData();
                        if (meta.hasEnchant(Enchantment.DURABILITY)) glow = true;
                    }
                }
            } catch (Exception ignored) {}
        }

        return new CustomItemSpec(mat, null, amount, chance, name, lore, cmd, glow, null, null);
    }

    public ItemStack build() {
        if (material == Material.AIR) return new ItemStack(Material.AIR);

        ItemStack item = null;
        if (hookIdentifier != null && !hookIdentifier.isEmpty()) {
            item = CustomItemHook.getItem(hookIdentifier);
        }
        if (item == null) {
            item = new ItemStack(material, amount);
        } else {
            item.setAmount(amount);
        }

        if (Bukkit.getServer() == null) {
            return item;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (displayName != null && !displayName.isEmpty()) {
                meta.setDisplayName(ColorUtil.color(displayName));
            }
            if (lore != null && !lore.isEmpty()) {
                meta.setLore(ColorUtil.color(lore));
            }
            if (customModelData != null && customModelData > 0) {
                meta.setCustomModelData(customModelData);
            }
            if (glow) {
                meta.addEnchant(Enchantment.DURABILITY, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            if (pdcKey != null && !pdcKey.isEmpty() && pdcValue != null) {
                try {
                    String[] parts = pdcKey.split(":", 2);
                    NamespacedKey key = parts.length == 2 ? new NamespacedKey(parts[0], parts[1]) : NamespacedKey.minecraft(parts[0]);
                    meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, pdcValue);
                } catch (Exception ignored) {}
            }
            item.setItemMeta(meta);
        }

        return item;
    }

    public boolean matches(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) return false;
        if (stack.getType() != material) return false;

        if (Bukkit.getServer() == null) {
            return true;
        }

        ItemMeta meta = stack.getItemMeta();
        boolean isCustom = isCustom();

        if (isCustom) {
            if (meta == null) return false;
            if (customModelData != null) {
                if (!meta.hasCustomModelData() || meta.getCustomModelData() != customModelData.intValue()) return false;
            }
            if (displayName != null && !displayName.isEmpty()) {
                if (!meta.hasDisplayName()) return false;
                String expected = ColorUtil.color(displayName);
                if (!expected.equals(meta.getDisplayName())) return false;
            }
            if (pdcKey != null && !pdcKey.isEmpty() && pdcValue != null) {
                try {
                    String[] parts = pdcKey.split(":", 2);
                    NamespacedKey key = parts.length == 2 ? new NamespacedKey(parts[0], parts[1]) : NamespacedKey.minecraft(parts[0]);
                    String val = meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
                    if (!pdcValue.equals(val)) return false;
                } catch (Exception ignored) {
                    return false;
                }
            }
            return true;
        } else {
            // Vanilla matching: strict isolation against items with custom display names or custom model data
            if (meta != null && (meta.hasDisplayName() || meta.hasCustomModelData())) {
                return false;
            }
            return true;
        }
    }

    public boolean isCustom() {
        return (customModelData != null && customModelData > 0)
                || (displayName != null && !displayName.isEmpty())
                || (pdcKey != null && !pdcKey.isEmpty())
                || (hookIdentifier != null && !hookIdentifier.isEmpty());
    }

    public Material getMaterial() { return material; }
    public String getHookIdentifier() { return hookIdentifier; }
    public int getAmount() { return amount; }
    public double getChance() { return chance; }
    public String getDisplayName() { return displayName; }
    public List<String> getLore() { return Collections.unmodifiableList(lore); }
    public Integer getCustomModelData() { return customModelData; }
    public boolean isGlow() { return glow; }
    public String getPdcKey() { return pdcKey; }
    public String getPdcValue() { return pdcValue; }
}
