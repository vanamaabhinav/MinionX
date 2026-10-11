package com.skyblock.minions.api;

import com.skyblock.minions.item.MinionItemFactory;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry managing all configured upgrades and accessories loaded dynamically from config.yml.
 */
public class UpgradeRegistry {

    private static final Map<String, CustomUpgrade> UPGRADES = new ConcurrentHashMap<>();

    static {
        registerDefaults();
    }

    public static void load(ConfigurationSection section) {
        UPGRADES.clear();

        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection upSec = section.getConfigurationSection(key);
                if (upSec == null) continue;

                String displayName = upSec.getString("display-name", key);
                String matName = upSec.getString("material", "IRON_BLOCK");
                Material mat = Material.matchMaterial(matName);
                if (mat == null) mat = Material.IRON_BLOCK;

                String typeStr = upSec.getString("type", "SPEED_BUFF");
                CustomUpgrade.UpgradeBehavior behavior;
                try {
                    behavior = CustomUpgrade.UpgradeBehavior.valueOf(typeStr.toUpperCase());
                } catch (IllegalArgumentException e) {
                    behavior = CustomUpgrade.UpgradeBehavior.SPEED_BUFF;
                }

                double speedBuff = upSec.getDouble("speed-buff", 0.0);
                double chance = upSec.getDouble("chance", 0.0);
                List<String> lore = upSec.getStringList("lore");
                boolean glow = upSec.getBoolean("glow", false);
                int customModelData = upSec.getInt("custom-model-data", 0);

                CustomUpgrade upgrade = new CustomUpgrade(key, displayName, mat, behavior, speedBuff, chance, lore, glow, customModelData);
                UPGRADES.put(key.toLowerCase(), upgrade);
            }
        }

        registerDefaults();
    }

    private static void registerDefaults() {
        registerDefaultIfMissing("auto_smelter", new CustomUpgrade("auto_smelter", "§6Auto-Smelter", Material.FURNACE,
                CustomUpgrade.UpgradeBehavior.AUTO_SMELTER, 0.0, 0.0,
                List.of("§7Automatically smelts harvested resources."), true, 0));

        registerDefaultIfMissing("compactor", new CustomUpgrade("compactor", "§6Compactor", Material.DROPPER,
                CustomUpgrade.UpgradeBehavior.COMPACTOR, 0.0, 0.0,
                List.of("§7Automatically compacts resources into blocks."), true, 0));

        registerDefaultIfMissing("diamond_spreading", new CustomUpgrade("diamond_spreading", "§bDiamond Spreading", Material.DIAMOND,
                CustomUpgrade.UpgradeBehavior.DIAMOND_SPREADING, 0.0, 0.10,
                List.of("§7Adds a 10% chance to generate diamonds when harvesting resources."), true, 0));

        registerDefaultIfMissing("minion_expander", new CustomUpgrade("minion_expander", "§aMinion Expander", Material.PISTON,
                CustomUpgrade.UpgradeBehavior.SPEED_BUFF, 0.05, 0.0,
                List.of("§7Increases production speed by +5%."), true, 0));

        registerDefaultIfMissing("flycatcher", new CustomUpgrade("flycatcher", "§dFlycatcher", Material.STRING,
                CustomUpgrade.UpgradeBehavior.SPEED_BUFF, 0.20, 0.0,
                List.of("§7Increases minion speed by §a+20%§7."), true, 0));
    }

    private static void registerDefaultIfMissing(String id, CustomUpgrade upgrade) {
        UPGRADES.putIfAbsent(id.toLowerCase(), upgrade);
    }

    public static CustomUpgrade get(String id) {
        if (id == null) return null;
        return UPGRADES.get(id.toLowerCase());
    }

    public static Collection<CustomUpgrade> getAll() {
        return Collections.unmodifiableCollection(UPGRADES.values());
    }

    public static Set<String> getKeys() {
        return Collections.unmodifiableSet(UPGRADES.keySet());
    }

    /**
     * Resolves a CustomUpgrade from an item stack via PDC tag or matching material fallback.
     */
    public static CustomUpgrade fromItemStack(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return null;

        String pdcId = MinionItemFactory.getUpgradeId(item);
        if (pdcId != null && UPGRADES.containsKey(pdcId.toLowerCase())) {
            return UPGRADES.get(pdcId.toLowerCase());
        }

        // Material fallback
        for (CustomUpgrade upgrade : UPGRADES.values()) {
            if (upgrade.getMaterial() == item.getType()) {
                return upgrade;
            }
        }

        return null;
    }
}
