package com.skyblock.minions.api;

import com.skyblock.minions.item.MinionItemFactory;
import com.skyblock.minions.util.ColorUtil;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry managing all configured fuels loaded dynamically from config.yml.
 */
public class FuelRegistry {

    private static final Map<String, CustomFuel> FUELS = new ConcurrentHashMap<>();

    static {
        registerDefaults();
    }

    public static void load(ConfigurationSection section) {
        FUELS.clear();

        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection fuelSec = section.getConfigurationSection(key);
                if (fuelSec == null) continue;

                String displayName = ColorUtil.color(fuelSec.getString("display-name", key));
                String matName = fuelSec.getString("material", "COAL");
                Material mat = Material.matchMaterial(matName);
                if (mat == null) mat = Material.COAL;

                double speedBuff = fuelSec.getDouble("speed-buff", 0.05);
                String durationStr = fuelSec.getString("duration", "30m");
                long durationMillis = parseDuration(durationStr);

                List<String> rawLore = fuelSec.getStringList("lore");
                List<String> lore = ColorUtil.color(rawLore);
                boolean glow = fuelSec.getBoolean("glow", false);
                int customModelData = fuelSec.getInt("custom-model-data", 0);

                CustomFuel fuel = new CustomFuel(key, displayName, mat, speedBuff, durationMillis, lore, glow, customModelData);
                FUELS.put(key.toLowerCase(), fuel);
            }
        }

        registerDefaults();
    }

    private static void registerDefaults() {
        registerDefaultIfMissing("coal", new CustomFuel("coal", ColorUtil.color("&fCoal"), Material.COAL, 0.05, 30 * 60 * 1000L,
                ColorUtil.color(List.of("&7Increases minion speed by &a+5%&7.", "&7Duration: &e30 minutes")), false, 0));
        registerDefaultIfMissing("charcoal", new CustomFuel("charcoal", ColorUtil.color("&fCharcoal"), Material.CHARCOAL, 0.05, 30 * 60 * 1000L,
                ColorUtil.color(List.of("&7Increases minion speed by &a+5%&7.", "&7Duration: &e30 minutes")), false, 0));
        registerDefaultIfMissing("enchanted_charcoal", new CustomFuel("enchanted_charcoal", ColorUtil.color("&aEnchanted Charcoal"), Material.CHARCOAL, 0.20, 36 * 3600 * 1000L,
                ColorUtil.color(List.of("&7Increases minion speed by &a+20%&7.", "&7Duration: &e36 hours")), true, 0));
        registerDefaultIfMissing("enchanted_lava_bucket", new CustomFuel("enchanted_lava_bucket", ColorUtil.color("&6Enchanted Lava Bucket"), Material.LAVA_BUCKET, 0.25, -1L,
                ColorUtil.color(List.of("&7Increases minion speed by &a+25%&7.", "&7Duration: &bPermanent")), true, 0));
        registerDefaultIfMissing("plasma_bucket", new CustomFuel("plasma_bucket", ColorUtil.color("&dPlasma Bucket"), Material.LAVA_BUCKET, 0.35, -1L,
                ColorUtil.color(List.of("&7Increases minion speed by &a+35%&7.", "&7Duration: &bPermanent")), true, 0));
        registerDefaultIfMissing("solar_panel", new CustomFuel("solar_panel", ColorUtil.color("&eSolar Panel"), Material.DAYLIGHT_DETECTOR, 0.25, -1L,
                ColorUtil.color(List.of("&7Increases minion speed by &a+25%&7.", "&7Duration: &bPermanent")), true, 0));
    }

    private static void registerDefaultIfMissing(String id, CustomFuel fuel) {
        FUELS.putIfAbsent(id.toLowerCase(), fuel);
    }

    public static CustomFuel get(String id) {
        if (id == null) return null;
        return FUELS.get(id.toLowerCase());
    }

    public static Collection<CustomFuel> getAll() {
        return Collections.unmodifiableCollection(FUELS.values());
    }

    public static Set<String> getKeys() {
        return Collections.unmodifiableSet(FUELS.keySet());
    }

    /**
     * Resolves a CustomFuel from an item stack.
     * Matches PDC key first, then meta display name. Plain vanilla items only match for coal/charcoal.
     */
    public static CustomFuel fromItemStack(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return null;

        String pdcId = MinionItemFactory.getFuelId(item);
        if (pdcId != null && FUELS.containsKey(pdcId.toLowerCase())) {
            return FUELS.get(pdcId.toLowerCase());
        }

        if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.hasDisplayName()) {
                String name = meta.getDisplayName();
                for (CustomFuel fuel : FUELS.values()) {
                    if (fuel.getDisplayName().equalsIgnoreCase(name) || name.contains(fuel.getId().replace('_', ' '))) {
                        return fuel;
                    }
                }
            }
        }

        // Vanilla basic fuel compatibility (plain coal and charcoal)
        if (!item.hasItemMeta() || (item.getItemMeta() != null && !item.getItemMeta().hasDisplayName())) {
            if (item.getType() == Material.COAL) {
                return FUELS.get("coal");
            } else if (item.getType() == Material.CHARCOAL) {
                return FUELS.get("charcoal");
            }
        }

        return null;
    }

    public static long parseDuration(String str) {
        if (str == null || str.isBlank()) return -1L;
        str = str.trim().toLowerCase();
        if (str.equals("-1") || str.contains("perm") || str.contains("inf")) {
            return -1L;
        }

        try {
            if (str.endsWith("s")) {
                return Long.parseLong(str.substring(0, str.length() - 1)) * 1000L;
            } else if (str.endsWith("m")) {
                return Long.parseLong(str.substring(0, str.length() - 1)) * 60 * 1000L;
            } else if (str.endsWith("h")) {
                return Long.parseLong(str.substring(0, str.length() - 1)) * 3600 * 1000L;
            } else if (str.endsWith("d")) {
                return Long.parseLong(str.substring(0, str.length() - 1)) * 86400 * 1000L;
            }
            return Long.parseLong(str);
        } catch (NumberFormatException e) {
            return -1L;
        }
    }
}