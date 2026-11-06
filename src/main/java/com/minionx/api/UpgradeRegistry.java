package com.minionx.api;

import com.minionx.item.MinionItemFactory;
import com.minionx.util.ColorUtil;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry managing all configured upgrades and accessories loaded dynamically from upgrades.yml.
 * Supports multi-behavior capabilities and unified multi-item spreading.
 */
@SuppressWarnings("deprecation")
public class UpgradeRegistry {

    private static final Map<String, CustomUpgrade> UPGRADES = new ConcurrentHashMap<>();

    static {
        registerDefaults();
    }

    public static void load() {
        load((ConfigurationSection) null);
    }

    public static void load(ConfigurationSection section) {
        UPGRADES.clear();
        if (!com.minionx.util.FeatureToggle.isUpgradesEnabled()) {
            return;
        }

        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection upSec = section.getConfigurationSection(key);
                if (upSec == null) continue;

                String displayName = ColorUtil.color(upSec.getString("display-name", key));
                String matName = upSec.getString("material", "IRON_BLOCK");
                Material mat = Material.matchMaterial(matName);
                if (mat == null) mat = Material.IRON_BLOCK;

                Set<CustomUpgrade.UpgradeBehavior> behaviors = EnumSet.noneOf(CustomUpgrade.UpgradeBehavior.class);
                if (upSec.isList("behaviors")) {
                    for (String bStr : upSec.getStringList("behaviors")) {
                        CustomUpgrade.UpgradeBehavior b = parseBehavior(bStr);
                        if (b != null) behaviors.add(b);
                    }
                } else {
                    String typeStr = upSec.getString("type", upSec.getString("behavior", "SPEED_BUFF"));
                    CustomUpgrade.UpgradeBehavior b = parseBehavior(typeStr);
                    if (b != null) behaviors.add(b);
                }

                double speedBuff = upSec.getDouble("speed-buff", 0.0);
                double chance = upSec.getDouble("chance", 0.0);
                double sellRatio = upSec.getDouble("sell-ratio", upSec.getDouble("sell_ratio", 0.0));
                if (sellRatio <= 0.0 && behaviors.contains(CustomUpgrade.UpgradeBehavior.AUTO_SELLER)) {
                    sellRatio = key.contains("enchanted") ? 0.90 : 0.50;
                }

                List<String> rawLore = upSec.getStringList("lore");
                List<String> lore = ColorUtil.color(rawLore);
                boolean glow = upSec.getBoolean("glow", false);
                int customModelData = upSec.getInt("custom-model-data", 0);
                int areaExpansion = upSec.getInt("area-expansion", upSec.getInt("area_expansion", 0));
                if (key.equalsIgnoreCase("minion_expander") && areaExpansion <= 0) {
                    areaExpansion = 1;
                }

                List<BonusDropEntry> spreadingItems = new ArrayList<>();
                if (upSec.isList("spreading-items")) {
                    for (Map<?, ?> bMap : upSec.getMapList("spreading-items")) {
                        BonusDropEntry bEntry = BonusDropEntry.fromConfig(bMap);
                        if (bEntry != null) spreadingItems.add(bEntry);
                    }
                }
                if (upSec.isList("bonus-drops")) {
                    for (Map<?, ?> bMap : upSec.getMapList("bonus-drops")) {
                        BonusDropEntry bEntry = BonusDropEntry.fromConfig(bMap);
                        if (bEntry != null) spreadingItems.add(bEntry);
                    }
                }
                if (upSec.isConfigurationSection("spreading-item")) {
                    BonusDropEntry bEntry = BonusDropEntry.fromConfig(upSec.getConfigurationSection("spreading-item").getValues(false));
                    if (bEntry != null) spreadingItems.add(bEntry);
                } else if (upSec.isString("spreading-item")) {
                    Material sMat = Material.matchMaterial(upSec.getString("spreading-item"));
                    if (sMat != null) {
                        spreadingItems.add(new BonusDropEntry(sMat, upSec.getInt("spreading-amount", 1), chance > 0 ? chance : 0.10));
                    }
                }

                if (!spreadingItems.isEmpty()) {
                    behaviors.add(CustomUpgrade.UpgradeBehavior.ITEM_SPREADING);
                }
                if (speedBuff > 0) {
                    behaviors.add(CustomUpgrade.UpgradeBehavior.SPEED_BUFF);
                }
                if (areaExpansion > 0) {
                    behaviors.add(CustomUpgrade.UpgradeBehavior.EXPANDER);
                }
                if (sellRatio > 0) {
                    behaviors.add(CustomUpgrade.UpgradeBehavior.AUTO_SELLER);
                }
                if (behaviors.isEmpty()) {
                    behaviors.add(CustomUpgrade.UpgradeBehavior.SPEED_BUFF);
                }

                CustomUpgrade upgrade = new CustomUpgrade(key, displayName, mat, behaviors, speedBuff, chance, sellRatio, areaExpansion, spreadingItems, lore, glow, customModelData);
                UPGRADES.put(key.toLowerCase(), upgrade);
            }
        }

        registerDefaults();
    }

    public static CustomUpgrade.UpgradeBehavior parseBehavior(String typeStr) {
        if (typeStr == null || typeStr.isBlank()) return CustomUpgrade.UpgradeBehavior.SPEED_BUFF;
        String s = typeStr.trim().toUpperCase();
        if ("BUDGET_HOPPER".equals(s) || "ENCHANTED_HOPPER".equals(s) || "AUTO_SELLER".equals(s) || "HOPPER".equals(s)) {
            return CustomUpgrade.UpgradeBehavior.AUTO_SELLER;
        }
        if ("POTATO_SPREADING".equals(s) || "DIAMOND_SPREADING".equals(s) || "ITEM_SPREADING".equals(s) ||
                "SPREADING".equals(s) || "BONUS_DROPS".equals(s)) {
            return CustomUpgrade.UpgradeBehavior.ITEM_SPREADING;
        }
        if ("EXPANDER".equals(s) || "MINION_EXPANDER".equals(s)) {
            return CustomUpgrade.UpgradeBehavior.EXPANDER;
        }
        try {
            return CustomUpgrade.UpgradeBehavior.valueOf(s);
        } catch (IllegalArgumentException e) {
            return CustomUpgrade.UpgradeBehavior.SPEED_BUFF;
        }
    }

    private static void registerDefaults() {
        if (!com.minionx.util.FeatureToggle.isUpgradesEnabled()) {
            return;
        }
        registerDefaultIfMissing("auto_smelter", new CustomUpgrade("auto_smelter", ColorUtil.color("&6Auto-Smelter"), Material.FURNACE,
                CustomUpgrade.UpgradeBehavior.AUTO_SMELTER, 0.0, 0.0,
                ColorUtil.color(List.of("&7Automatically smelts harvested resources.")), true, 0));

        registerDefaultIfMissing("compactor", new CustomUpgrade("compactor", ColorUtil.color("&6Compactor"), Material.DROPPER,
                CustomUpgrade.UpgradeBehavior.COMPACTOR, 0.0, 0.0,
                ColorUtil.color(List.of("&7Automatically compacts resources into blocks.")), true, 0));

        registerDefaultIfMissing("super_compactor", new CustomUpgrade("super_compactor", ColorUtil.color("&dSuper Compactor 3000"), Material.DISPENSER,
                CustomUpgrade.UpgradeBehavior.SUPER_COMPACTOR, 0.0, 0.0,
                ColorUtil.color(List.of("&7Automatically compresses items into their", "&7enchanted compressed forms.")), true, 0));

        registerDefaultIfMissing("super_compactor_3000", new CustomUpgrade("super_compactor_3000", ColorUtil.color("&dSuper Compactor 3000"), Material.DISPENSER,
                CustomUpgrade.UpgradeBehavior.SUPER_COMPACTOR, 0.0, 0.0,
                ColorUtil.color(List.of("&7Automatically compresses items into their", "&7enchanted compressed forms.")), true, 0));

        registerDefaultIfMissing("diamond_spreading", new CustomUpgrade("diamond_spreading", ColorUtil.color("&bDiamond Spreading"), Material.DIAMOND,
                Set.of(CustomUpgrade.UpgradeBehavior.ITEM_SPREADING), 0.0, 0.10, 0.0, 0,
                List.of(new BonusDropEntry(Material.DIAMOND, 1, 0.10)),
                ColorUtil.color(List.of("&7Adds a 10% chance to generate diamonds", "&7when harvesting resources.")), true, 0));

        registerDefaultIfMissing("potato_spreading", new CustomUpgrade("potato_spreading", ColorUtil.color("&ePotato Spreading"), Material.POTATO,
                Set.of(CustomUpgrade.UpgradeBehavior.ITEM_SPREADING), 0.0, 0.10, 0.0, 0,
                List.of(new BonusDropEntry(Material.POTATO, 1, 0.10)),
                ColorUtil.color(List.of("&7Adds a 10% chance to generate potatoes", "&7when harvesting resources.")), true, 0));

        List<BonusDropEntry> potatoBonusList = List.of(
                new BonusDropEntry(Material.POTATO, 1, 0.25),
                new BonusDropEntry(Material.POISONOUS_POTATO, 1, 0.10),
                new BonusDropEntry(Material.BAKED_POTATO, 2, 0.05)
        );
        registerDefaultIfMissing("bountiful_potato", new CustomUpgrade("bountiful_potato", ColorUtil.color("&6Bountiful Potato"), Material.GOLDEN_CARROT,
                Set.of(CustomUpgrade.UpgradeBehavior.ITEM_SPREADING), 0.0, 0.0, 0.0, 0,
                potatoBonusList,
                ColorUtil.color(List.of("&7Grants bonus potato drops on harvest:",
                        "&7- &e25% &7chance for &fPotato",
                        "&7- &e10% &7chance for &fPoisonous Potato",
                        "&7- &e5% &7chance for &f2x Baked Potato")), true, 0));

        registerDefaultIfMissing("minion_expander", new CustomUpgrade("minion_expander", ColorUtil.color("&aMinion Expander"), Material.PISTON,
                Set.of(CustomUpgrade.UpgradeBehavior.SPEED_BUFF, CustomUpgrade.UpgradeBehavior.EXPANDER), 0.05, 0.0, 0.0, 1,
                Collections.emptyList(),
                ColorUtil.color(List.of("&7Increases production speed by &a+5%&7.", "&7Expands working area by &a+1 block&7.", "&7(5x5 -> 7x7 -> 9x9 when stacked!)")), true, 0));

        registerDefaultIfMissing("flycatcher", new CustomUpgrade("flycatcher", ColorUtil.color("&dFlycatcher"), Material.STRING,
                CustomUpgrade.UpgradeBehavior.SPEED_BUFF, 0.20, 0.0,
                ColorUtil.color(List.of("&7Increases minion speed by &a+20%&7.")), true, 0));

        registerDefaultIfMissing("hyper_catalyst", new CustomUpgrade("hyper_catalyst", ColorUtil.color("&5Hyper Catalyst"), Material.NETHER_STAR,
                CustomUpgrade.UpgradeBehavior.SPEED_BUFF, 0.40, 0.0,
                ColorUtil.color(List.of("&7Increases production speed by &a+40%&7.")), true, 0));

        registerDefaultIfMissing("budget_hopper", new CustomUpgrade("budget_hopper", ColorUtil.color("&eBudget Hopper"), Material.HOPPER,
                CustomUpgrade.UpgradeBehavior.AUTO_SELLER, 0.0, 0.0, 0.50,
                ColorUtil.color(List.of("&7Automatically sells overflow items at &e50%&7", "&7value when storage is completely full.")), true, 0));

        registerDefaultIfMissing("enchanted_hopper", new CustomUpgrade("enchanted_hopper", ColorUtil.color("&6Enchanted Hopper"), Material.HOPPER,
                CustomUpgrade.UpgradeBehavior.AUTO_SELLER, 0.0, 0.0, 0.90,
                ColorUtil.color(List.of("&7Automatically sells overflow items at &690%&7", "&7value when storage is completely full.")), true, 0));
    }

    public static void saveCustomUpgrade(CustomUpgrade upgrade) {
        if (upgrade == null) return;
        UPGRADES.put(upgrade.getId().toLowerCase(), upgrade);

        if (com.minionx.MinionX.getInstance() == null) return;
        java.io.File file = new java.io.File(com.minionx.MinionX.getInstance().getDataFolder(), "upgrades.yml");
        if (file.getParentFile() != null && !file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }
        org.bukkit.configuration.file.YamlConfiguration config = file.exists()
                ? org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file)
                : new org.bukkit.configuration.file.YamlConfiguration();

        String path = "upgrades." + upgrade.getId().toLowerCase();
        config.set(path + ".display-name", upgrade.getDisplayName());
        config.set(path + ".material", upgrade.getMaterial().name());

        if (upgrade.getBehaviors().size() > 1) {
            List<String> bNames = new ArrayList<>();
            for (CustomUpgrade.UpgradeBehavior b : upgrade.getBehaviors()) {
                bNames.add(b.name());
            }
            config.set(path + ".behaviors", bNames);
            config.set(path + ".type", upgrade.getBehavior().name());
        } else {
            config.set(path + ".type", upgrade.getBehavior().name());
        }

        if (upgrade.getSpeedBuff() > 0) {
            config.set(path + ".speed-buff", upgrade.getSpeedBuff());
        }
        if (upgrade.getChance() > 0) {
            config.set(path + ".chance", upgrade.getChance());
        }
        if (upgrade.getSellRatio() > 0) {
            config.set(path + ".sell-ratio", upgrade.getSellRatio());
        }
        if (upgrade.getAreaExpansion() > 0) {
            config.set(path + ".area-expansion", upgrade.getAreaExpansion());
        }
        config.set(path + ".glow", upgrade.isGlowing());
        if (upgrade.getCustomModelData() > 0) {
            config.set(path + ".custom-model-data", upgrade.getCustomModelData());
        }
        if (upgrade.getLore() != null && !upgrade.getLore().isEmpty()) {
            config.set(path + ".lore", upgrade.getLore());
        }
        if (upgrade.isSpreading() && !upgrade.getSpreadingItems().isEmpty()) {
            List<Map<String, Object>> sList = new ArrayList<>();
            for (BonusDropEntry b : upgrade.getSpreadingItems()) {
                sList.add(b.toConfigMap());
            }
            config.set(path + ".spreading-items", sList);
        }

        try {
            config.save(file);
        } catch (java.io.IOException e) {
            com.minionx.MinionX.getInstance().getLogger().log(java.util.logging.Level.SEVERE, "Failed to save upgrade to upgrades.yml", e);
        }
    }

    private static void registerDefaultIfMissing(String id, CustomUpgrade upgrade) {
        UPGRADES.putIfAbsent(id.toLowerCase(), upgrade);
    }

    public static void register(CustomUpgrade upgrade) {
        if (upgrade != null) {
            UPGRADES.put(upgrade.getId().toLowerCase(), upgrade);
        }
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
     * Resolves a CustomUpgrade from an item stack.
     * Matches PDC key first, then meta display name, then material-based fallback.
     */
    public static CustomUpgrade fromItemStack(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return null;

        String pdcId = MinionItemFactory.getUpgradeId(item);
        if (pdcId != null && UPGRADES.containsKey(pdcId.toLowerCase())) {
            return UPGRADES.get(pdcId.toLowerCase());
        }

        if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.hasDisplayName()) {
                String name = meta.getDisplayName();
                for (CustomUpgrade upgrade : UPGRADES.values()) {
                    if (upgrade.getDisplayName().equalsIgnoreCase(name) || name.contains(upgrade.getId().replace('_', ' '))) {
                        return upgrade;
                    }
                }
            }
        }

        // Fallback: Check MinionUpgradeType by icon
        for (MinionUpgradeType type : MinionUpgradeType.values()) {
            if (item.getType() == type.getIcon()) {
                return UPGRADES.get(type.name().toLowerCase());
            }
        }

        return null;
    }
}
