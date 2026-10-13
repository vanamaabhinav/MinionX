package com.minionx.api;

import com.minionx.MinionX;
import com.minionx.core.CompressionManager;
import com.minionx.hook.CustomItemHook;
import com.minionx.item.CustomItemSpec;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;

/**
 * Registry managing base NPC item sell prices loaded from prices.yml.
 * Supports vanilla items, compressed enchanted items, and full custom items
 * (ItemsAdder, MMOItems, EcoItems, CustomModelData, Display Names, and PDC tags).
 * Used by Automated Shipping hoppers (Budget Hopper, Enchanted Hopper) to calculate earnings.
 */
@SuppressWarnings("deprecation")
public class PriceRegistry {

    /**
     * Definition for a custom item sell price rule.
     */
    public static class CustomPriceEntry {
        private final String id;
        private final CustomItemSpec spec;
        private final double price;

        public CustomPriceEntry(String id, CustomItemSpec spec, double price) {
            this.id = id != null ? id.toLowerCase() : "custom_price";
            this.spec = spec;
            this.price = Math.max(0.0, price);
        }

        public String getId() { return id; }
        public CustomItemSpec getSpec() { return spec; }
        public double getPrice() { return price; }

        public boolean matches(ItemStack item) {
            if (spec == null || item == null) return false;
            return spec.matches(item);
        }
    }

    private static final Map<String, Double> PRICES = new ConcurrentHashMap<>();
    private static final List<CustomPriceEntry> CUSTOM_PRICES = new CopyOnWriteArrayList<>();
    private static double defaultPrice = 1.0;

    static {
        registerDefaults();
    }

    public static void load() {
        if (MinionX.getInstance() == null) return;
        File file = new File(MinionX.getInstance().getDataFolder(), "prices.yml");
        if (!file.exists()) {
            try {
                if (!file.getParentFile().exists()) {
                    file.getParentFile().mkdirs();
                }
                try (InputStream in = MinionX.getInstance().getResource("prices.yml")) {
                    if (in != null) {
                        Files.copy(in, file.toPath());
                    }
                }
            } catch (IOException e) {
                MinionX.getInstance().getLogger().log(Level.WARNING, "Could not create default prices.yml", e);
            }
        }

        if (file.exists()) {
            load(YamlConfiguration.loadConfiguration(file));
        }
    }

    public static void load(ConfigurationSection config) {
        PRICES.clear();
        CUSTOM_PRICES.clear();
        registerDefaults();

        if (config == null) return;

        defaultPrice = config.getDouble("default-price", 1.0);

        // 1. Load standard prices map
        ConfigurationSection sec = config.getConfigurationSection("prices");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                double val = sec.getDouble(key, defaultPrice);
                PRICES.put(key.toUpperCase(), val);

                // If key is a hook identifier or material:cmd, register CustomPriceEntry
                if (key.contains(":")) {
                    if (key.toLowerCase().startsWith("itemsadder:") ||
                        key.toLowerCase().startsWith("mmoitems:") ||
                        key.toLowerCase().startsWith("ecoitems:")) {
                        ItemStack hookItem = CustomItemHook.getItem(key);
                        if (hookItem != null) {
                            CUSTOM_PRICES.add(new CustomPriceEntry(key, CustomItemSpec.fromItemStack(hookItem, 1.0), val));
                        }
                    } else {
                        // Could be MATERIAL:CMD
                        String[] parts = key.split(":", 2);
                        Material mat = Material.matchMaterial(parts[0]);
                        if (mat != null) {
                            try {
                                int cmd = Integer.parseInt(parts[1]);
                                CustomItemSpec spec = new CustomItemSpec(mat, null, 1, 1.0, null, null, cmd, false, null, null);
                                CUSTOM_PRICES.add(new CustomPriceEntry(key, spec, val));
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                }
            }
        }

        // 2. Load dedicated custom-prices section
        ConfigurationSection customSec = config.getConfigurationSection("custom-prices");
        if (customSec != null) {
            for (String id : customSec.getKeys(false)) {
                ConfigurationSection itemSec = customSec.getConfigurationSection(id);
                if (itemSec == null) continue;

                double price = itemSec.getDouble("price", defaultPrice);
                CustomItemSpec spec = CustomItemSpec.fromConfigSection(itemSec);
                CUSTOM_PRICES.add(new CustomPriceEntry(id, spec, price));
            }
        }
    }

    public static double getPrice(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return 0.0;

        // 1. Check custom prices entries (CustomItemSpec matches)
        for (CustomPriceEntry entry : CUSTOM_PRICES) {
            if (entry.matches(item)) {
                return entry.getPrice();
            }
        }

        // 2. Metadata checks (Display Name, CMD, PDC tags)
        if (org.bukkit.Bukkit.getServer() != null) {
            try {
                if (item.hasItemMeta()) {
                    ItemMeta meta = item.getItemMeta();
                    if (meta != null) {
                        // 2a. Match by exact Display Name or stripped display name
                        if (meta.hasDisplayName()) {
                            String rawName = meta.getDisplayName();
                            if (PRICES.containsKey(rawName.toUpperCase())) {
                                return PRICES.get(rawName.toUpperCase());
                            }
                            String stripped = ChatColor.stripColor(rawName).trim();
                            if (PRICES.containsKey(stripped.toUpperCase())) {
                                return PRICES.get(stripped.toUpperCase());
                            }
                            String under = stripped.replace(' ', '_').toUpperCase();
                            if (PRICES.containsKey(under)) {
                                return PRICES.get(under);
                            }
                        }

                        // 2b. Match by CustomModelData
                        if (meta.hasCustomModelData()) {
                            int cmd = meta.getCustomModelData();
                            String cmdKey = item.getType().name() + ":" + cmd;
                            if (PRICES.containsKey(cmdKey)) {
                                return PRICES.get(cmdKey);
                            }
                            String rawCmd = "CMD:" + cmd;
                            if (PRICES.containsKey(rawCmd)) {
                                return PRICES.get(rawCmd);
                            }
                        }

                        // 2c. Match by PDC tags
                        PersistentDataContainer pdc = meta.getPersistentDataContainer();
                        for (NamespacedKey key : pdc.getKeys()) {
                            String val = pdc.get(key, PersistentDataType.STRING);
                            if (val != null) {
                                if (PRICES.containsKey(val.toUpperCase())) {
                                    return PRICES.get(val.toUpperCase());
                                }
                                String namespaced = key.getNamespace() + ":" + val;
                                if (PRICES.containsKey(namespaced.toUpperCase())) {
                                    return PRICES.get(namespaced.toUpperCase());
                                }
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }

        // 3. Match against Compression Recipes (Super Compactor compressed enchanted items)
        try {
            for (CompressionManager.CompressionRecipe recipe : CompressionManager.getAllRecipes()) {
                if (recipe.getOutputSpec() != null && recipe.getOutputSpec().matches(item)) {
                    // Check if recipe ID has an explicit price (e.g. ENCHANTED_COBBLESTONE)
                    if (PRICES.containsKey(recipe.getId().toUpperCase())) {
                        return PRICES.get(recipe.getId().toUpperCase());
                    }
                    String strippedRecipe = recipe.getId().replace("recipe_", "").replace("compress_", "").toUpperCase();
                    if (PRICES.containsKey(strippedRecipe)) {
                        return PRICES.get(strippedRecipe);
                    }
                    // Dynamically calculate based on input amount * input unit price
                    double inPrice = getPrice(recipe.getInputMaterial());
                    return recipe.getInputAmount() * inPrice;
                }
            }
        } catch (Throwable ignored) {}

        // 4. Fallback to vanilla material price
        return getPrice(item.getType());
    }

    public static double getPrice(Material material) {
        if (material == null || material.isAir()) return 0.0;
        return PRICES.getOrDefault(material.name().toUpperCase(), defaultPrice);
    }

    public static void setPrice(Material material, double price) {
        if (material != null) {
            PRICES.put(material.name().toUpperCase(), Math.max(0.0, price));
        }
    }

    public static void setPrice(String key, double price) {
        if (key != null) {
            PRICES.put(key.toUpperCase(), Math.max(0.0, price));
        }
    }

    public static void setCustomPrice(String id, CustomItemSpec spec, double price) {
        CUSTOM_PRICES.removeIf(e -> e.getId().equalsIgnoreCase(id));
        CUSTOM_PRICES.add(new CustomPriceEntry(id, spec, price));
    }

    public static List<CustomPriceEntry> getCustomPrices() {
        return Collections.unmodifiableList(CUSTOM_PRICES);
    }

    public static Map<String, Double> getAllPrices() {
        return Collections.unmodifiableMap(PRICES);
    }

    public static double getDefaultPrice() {
        return defaultPrice;
    }

    public static void setDefaultPrice(double price) {
        defaultPrice = Math.max(0.0, price);
    }

    public static void save() {
        if (MinionX.getInstance() == null) return;
        File file = new File(MinionX.getInstance().getDataFolder(), "prices.yml");
        YamlConfiguration config = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();

        config.set("default-price", defaultPrice);
        for (Map.Entry<String, Double> entry : PRICES.entrySet()) {
            config.set("prices." + entry.getKey(), entry.getValue());
        }

        for (CustomPriceEntry entry : CUSTOM_PRICES) {
            String path = "custom-prices." + entry.getId();
            CustomItemSpec spec = entry.getSpec();
            if (spec != null) {
                config.set(path + ".price", entry.getPrice());
                if (spec.getMaterial() != null) config.set(path + ".material", spec.getMaterial().name());
                if (spec.getDisplayName() != null) config.set(path + ".display-name", spec.getDisplayName());
                if (spec.getCustomModelData() != null) config.set(path + ".custom-model-data", spec.getCustomModelData());
                if (spec.getPdcKey() != null) config.set(path + ".pdc-key", spec.getPdcKey());
                if (spec.getPdcValue() != null) config.set(path + ".pdc-value", spec.getPdcValue());
                if (spec.getHookIdentifier() != null) config.set(path + ".item", spec.getHookIdentifier());
            }
        }

        try {
            config.save(file);
        } catch (IOException e) {
            MinionX.getInstance().getLogger().log(Level.SEVERE, "Failed to save prices.yml", e);
        }
    }

    private static void registerDefaults() {
        PRICES.put("COBBLESTONE", 1.0);
        PRICES.put("STONE", 1.0);
        PRICES.put("COAL", 2.0);
        PRICES.put("CHARCOAL", 2.0);
        PRICES.put("RAW_IRON", 3.0);
        PRICES.put("IRON_INGOT", 3.0);
        PRICES.put("RAW_GOLD", 4.0);
        PRICES.put("GOLD_INGOT", 4.0);
        PRICES.put("RAW_COPPER", 2.0);
        PRICES.put("COPPER_INGOT", 2.0);
        PRICES.put("DIAMOND", 8.0);
        PRICES.put("EMERALD", 6.0);
        PRICES.put("LAPIS_LAZULI", 1.0);
        PRICES.put("REDSTONE", 1.0);
        PRICES.put("NETHER_QUARTZ", 4.0);
        PRICES.put("QUARTZ", 4.0);
        PRICES.put("OBSIDIAN", 12.0);
        PRICES.put("GLOWSTONE_DUST", 2.0);
        PRICES.put("GRAVEL", 1.0);
        PRICES.put("FLINT", 2.0);
        PRICES.put("ICE", 1.0);
        PRICES.put("PACKED_ICE", 9.0);
        PRICES.put("BLUE_ICE", 81.0);
        PRICES.put("SAND", 1.0);
        PRICES.put("RED_SAND", 1.0);
        PRICES.put("END_STONE", 2.0);
        PRICES.put("NETHERRACK", 1.0);
        PRICES.put("AMETHYST_SHARD", 4.0);
        PRICES.put("WHEAT", 1.0);
        PRICES.put("WHEAT_SEEDS", 0.5);
        PRICES.put("CARROT", 1.0);
        PRICES.put("POTATO", 1.0);
        PRICES.put("POISONOUS_POTATO", 1.0);
        PRICES.put("PUMPKIN", 4.0);
        PRICES.put("MELON", 1.0);
        PRICES.put("MELON_SLICE", 0.5);
        PRICES.put("SUGAR_CANE", 2.0);
        PRICES.put("CACTUS", 1.0);
        PRICES.put("COCOA_BEANS", 2.0);
        PRICES.put("BROWN_MUSHROOM", 4.0);
        PRICES.put("RED_MUSHROOM", 4.0);
        PRICES.put("NETHER_WART", 3.0);
        PRICES.put("OAK_LOG", 2.0);
        PRICES.put("BIRCH_LOG", 2.0);
        PRICES.put("SPRUCE_LOG", 2.0);
        PRICES.put("JUNGLE_LOG", 2.0);
        PRICES.put("ACACIA_LOG", 2.0);
        PRICES.put("DARK_OAK_LOG", 2.0);
        PRICES.put("MANGROVE_LOG", 2.0);
        PRICES.put("CHERRY_LOG", 2.0);
        PRICES.put("APPLE", 3.0);
        PRICES.put("OAK_SAPLING", 1.0);
        PRICES.put("ROTTEN_FLESH", 1.0);
        PRICES.put("BONE", 2.0);
        PRICES.put("STRING", 2.0);
        PRICES.put("SPIDER_EYE", 3.0);
        PRICES.put("GUNPOWDER", 4.0);
        PRICES.put("ENDER_PEARL", 10.0);
        PRICES.put("SLIME_BALL", 5.0);
        PRICES.put("MAGMA_CREAM", 8.0);
        PRICES.put("BLAZE_ROD", 10.0);
        PRICES.put("GHAST_TEAR", 20.0);
        PRICES.put("LEATHER", 3.0);
        PRICES.put("BEEF", 2.0);
        PRICES.put("PORKCHOP", 2.0);
        PRICES.put("CHICKEN", 2.0);
        PRICES.put("FEATHER", 1.0);
        PRICES.put("MUTTON", 2.0);
        PRICES.put("WHITE_WOOL", 2.0);
        PRICES.put("RABBIT", 3.0);
        PRICES.put("RABBIT_FOOT", 15.0);
        PRICES.put("RABBIT_HIDE", 2.0);

        // Pre-configured Compressed / Enchanted item prices (Super Compactor 3000)
        PRICES.put("ENCHANTED_COBBLESTONE", 160.0);
        PRICES.put("ENCHANTED_COAL", 320.0);
        PRICES.put("ENCHANTED_CHARCOAL", 320.0);
        PRICES.put("ENCHANTED_IRON", 480.0);
        PRICES.put("ENCHANTED_GOLD", 640.0);
        PRICES.put("ENCHANTED_DIAMOND", 1280.0);
        PRICES.put("ENCHANTED_EMERALD", 960.0);
        PRICES.put("ENCHANTED_LAPIS_LAZULI", 160.0);
        PRICES.put("ENCHANTED_REDSTONE", 160.0);
        PRICES.put("ENCHANTED_QUARTZ", 640.0);
        PRICES.put("ENCHANTED_OBSIDIAN", 1920.0);
        PRICES.put("ENCHANTED_GLOWSTONE_DUST", 320.0);
        PRICES.put("ENCHANTED_PUMPKIN", 640.0);
        PRICES.put("ENCHANTED_MELON", 160.0);
        PRICES.put("ENCHANTED_SUGAR_CANE", 320.0);
        PRICES.put("ENCHANTED_CACTUS", 160.0);
        PRICES.put("ENCHANTED_OAK_LOG", 320.0);
        PRICES.put("ENCHANTED_ROTTEN_FLESH", 160.0);
        PRICES.put("ENCHANTED_BONE", 320.0);
        PRICES.put("ENCHANTED_STRING", 320.0);
        PRICES.put("ENCHANTED_GUNPOWDER", 640.0);
        PRICES.put("ENCHANTED_ENDER_PEARL", 200.0);
    }
}
