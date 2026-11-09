package com.minionx;

import com.minionx.api.*;
import com.minionx.core.CompressionManager;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class ConfigAuditAndRemediationTest {

    private static final String RESOURCES_DIR = "src/main/resources/";

    @Test
    @DisplayName("All YAML resource files should be syntactically valid and non-empty")
    void testAllYamlResourceFilesSyntacticallyValid() {
        String[] configFiles = {
                "config.yml",
                "fuels.yml",
                "messages.yml",
                "minions.yml",
                "plugin.yml",
                "prices.yml",
                "recipes.yml",
                "skins.yml",
                "storages.yml",
                "tiers.yml",
                "upgrades.yml"
        };

        for (String fileName : configFiles) {
            File file = new File(RESOURCES_DIR, fileName);
            assertTrue(file.exists(), "Resource file must exist: " + fileName);
            assertTrue(file.length() > 0, "Resource file must not be empty: " + fileName);

            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            assertFalse(config.getKeys(false).isEmpty(), "Resource YAML must contain root keys: " + fileName);
        }
    }

    @Test
    @DisplayName("Compactor recipes defined in recipes.yml should be properly loaded and queryable")
    void testCompactorRecipesLoadedFromConfig() {
        File recipesFile = new File(RESOURCES_DIR, "recipes.yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(recipesFile);

        ConfigurationSection compSec = config.getConfigurationSection("compactor-recipes");
        assertNotNull(compSec, "recipes.yml must contain compactor-recipes section");

        CompressionManager.loadCompactorRecipes(compSec);

        CompressionManager.CompressionRecipe redstoneRecipe = CompressionManager.getCompactorRecipe("redstone_block");
        assertNotNull(redstoneRecipe, "compactor-recipes must include redstone_block");
        assertEquals(Material.REDSTONE, redstoneRecipe.getInputMaterial());
        assertEquals(9, redstoneRecipe.getInputAmount());
        assertEquals(Material.REDSTONE_BLOCK, redstoneRecipe.createOutput().getType());

        CompressionManager.CompressionRecipe ironRecipe = CompressionManager.getCompactorRecipe("iron_block");
        assertNotNull(ironRecipe, "compactor-recipes must include iron_block");
        assertEquals(Material.IRON_INGOT, ironRecipe.getInputMaterial());
        assertEquals(9, ironRecipe.getInputAmount());
        assertEquals(Material.IRON_BLOCK, ironRecipe.createOutput().getType());

        CompressionManager.CompressionRecipe diamondRecipe = CompressionManager.getCompactorRecipe("diamond_block");
        assertNotNull(diamondRecipe, "compactor-recipes must include diamond_block");
        assertEquals(Material.DIAMOND, diamondRecipe.getInputMaterial());
        assertEquals(9, diamondRecipe.getInputAmount());
        assertEquals(Material.DIAMOND_BLOCK, diamondRecipe.createOutput().getType());
    }

    @Test
    @DisplayName("MinionRegistry should resolve target-crop, target-log, and target-sapling aliases")
    void testTargetBlockAliasesInMinionRegistry() {
        YamlConfiguration config = new YamlConfiguration();

        // Minion using target-crop
        config.set("custom-minions.crop_minion.archetype", "FARMING");
        config.set("custom-minions.crop_minion.target-crop", "WHEAT");
        config.set("custom-minions.crop_minion.display-name", "&aCrop Minion");

        // Minion using target-log
        config.set("custom-minions.log_minion.archetype", "FORAGING");
        config.set("custom-minions.log_minion.target-log", "OAK_LOG");
        config.set("custom-minions.log_minion.display-name", "&6Log Minion");

        // Minion using target-sapling
        config.set("custom-minions.sapling_minion.archetype", "FORAGING");
        config.set("custom-minions.sapling_minion.target-sapling", "BIRCH_SAPLING");
        config.set("custom-minions.sapling_minion.display-name", "&eSapling Minion");

        // Standard target-block still works
        config.set("custom-minions.ore_minion.archetype", "MINER");
        config.set("custom-minions.ore_minion.target-block", "AMETHYST_CLUSTER");
        config.set("custom-minions.ore_minion.display-name", "&dOre Minion");

        MinionRegistry.load(config);

        MinionDefinition cropDef = MinionRegistry.get("crop_minion");
        assertNotNull(cropDef, "crop_minion should be registered");
        assertEquals(Material.WHEAT, cropDef.getTargetBlock(), "target-crop alias should resolve to WHEAT");

        MinionDefinition logDef = MinionRegistry.get("log_minion");
        assertNotNull(logDef, "log_minion should be registered");
        assertEquals(Material.OAK_LOG, logDef.getTargetBlock(), "target-log alias should resolve to OAK_LOG");

        MinionDefinition saplingDef = MinionRegistry.get("sapling_minion");
        assertNotNull(saplingDef, "sapling_minion should be registered");
        assertEquals(Material.BIRCH_SAPLING, saplingDef.getTargetBlock(), "target-sapling alias should resolve to BIRCH_SAPLING");

        MinionDefinition oreDef = MinionRegistry.get("ore_minion");
        assertNotNull(oreDef, "ore_minion should be registered");
        assertEquals(Material.AMETHYST_CLUSTER, oreDef.getTargetBlock(), "target-block should resolve to AMETHYST_CLUSTER");
    }

    @Test
    @DisplayName("storages.yml should load into MinionStorageRegistry with all default storage tiers")
    void testStoragesYmlLoadsIntoMinionStorageRegistry() {
        File storagesFile = new File(RESOURCES_DIR, "storages.yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(storagesFile);

        ConfigurationSection section = config.getConfigurationSection("storages");
        assertNotNull(section, "storages.yml must contain a 'storages' section");

        MinionStorageRegistry.load(section);

        CustomMinionStorage small = MinionStorageRegistry.get("small_storage");
        assertNotNull(small, "small_storage should be registered");
        assertEquals(3, small.getCapacity());
        assertEquals(Material.CHEST, small.getMaterial());

        CustomMinionStorage medium = MinionStorageRegistry.get("medium_storage");
        assertNotNull(medium, "medium_storage should be registered");
        assertEquals(9, medium.getCapacity());

        CustomMinionStorage large = MinionStorageRegistry.get("large_storage");
        assertNotNull(large, "large_storage should be registered");
        assertEquals(15, large.getCapacity());

        CustomMinionStorage max = MinionStorageRegistry.get("max_storage");
        assertNotNull(max, "max_storage should be registered");
        assertEquals(27, max.getCapacity());
    }

    @Test
    @DisplayName("config.yml messages section should have full parity with messages.yml as fallbacks")
    void testMessagesParityBetweenConfigAndMessages() {
        File msgFile = new File(RESOURCES_DIR, "messages.yml");
        YamlConfiguration msgConfig = YamlConfiguration.loadConfiguration(msgFile);

        File mainFile = new File(RESOURCES_DIR, "config.yml");
        YamlConfiguration mainConfig = YamlConfiguration.loadConfiguration(mainFile);

        ConfigurationSection msgSec = msgConfig.getConfigurationSection("messages");
        assertNotNull(msgSec, "messages.yml must contain messages section");

        ConfigurationSection mainSec = mainConfig.getConfigurationSection("messages");
        assertNotNull(mainSec, "config.yml must contain fallback messages section");

        for (String key : msgSec.getKeys(false)) {
            // dialogues and enabled/format/duration-seconds belong to the dialogue sub-block
            if (Set.of("dialogues", "enabled", "format", "duration-seconds").contains(key)) continue;
            assertTrue(mainSec.contains(key), "config.yml messages fallback is missing key: " + key);
        }

        // Verify the 3 fuel messages are explicitly present in config.yml
        assertTrue(mainSec.contains("fuel-burned-out"), "config.yml missing fuel-burned-out");
        assertTrue(mainSec.contains("fuel-extended"), "config.yml missing fuel-extended");
        assertTrue(mainSec.contains("cannot-stack-permanent-fuel"), "config.yml missing cannot-stack-permanent-fuel");
    }

    @Test
    @DisplayName("upgrades.yml should load both single type and multi behaviors into UpgradeRegistry")
    void testUpgradesYmlTypesAndBehaviors() {
        File file = new File(RESOURCES_DIR, "upgrades.yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        ConfigurationSection section = config.getConfigurationSection("upgrades");
        assertNotNull(section, "upgrades.yml must contain upgrades section");

        UpgradeRegistry.load(section);

        // Single type upgrade (Auto-Smelter)
        CustomUpgrade smelter = UpgradeRegistry.get("auto_smelter");
        assertNotNull(smelter);
        assertTrue(smelter.hasBehavior(CustomUpgrade.UpgradeBehavior.AUTO_SMELTER));

        // Multi-behavior upgrade (Minion Expander: SPEED_BUFF + EXPANDER)
        CustomUpgrade expander = UpgradeRegistry.get("minion_expander");
        assertNotNull(expander);
        assertTrue(expander.hasBehavior(CustomUpgrade.UpgradeBehavior.SPEED_BUFF));
        assertTrue(expander.hasBehavior(CustomUpgrade.UpgradeBehavior.EXPANDER));
        assertEquals(0.05, expander.getSpeedBuff(), 0.001);
        assertEquals(1, expander.getAreaExpansion());

        // Multi-item spreading upgrade (Bountiful Potato)
        CustomUpgrade bountiful = UpgradeRegistry.get("bountiful_potato");
        assertNotNull(bountiful);
        assertTrue(bountiful.hasBehavior(CustomUpgrade.UpgradeBehavior.ITEM_SPREADING));
        List<BonusDropEntry> items = bountiful.getSpreadingItems();
        assertNotNull(items);
        assertEquals(3, items.size());
    }

    @Test
    @DisplayName("fuels.yml should load all default fuels with valid speed buffs and durations")
    void testFuelsYmlLoadsCleanly() {
        File file = new File(RESOURCES_DIR, "fuels.yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        ConfigurationSection section = config.getConfigurationSection("fuels");
        assertNotNull(section, "fuels.yml must contain fuels section");

        FuelRegistry.load(section);

        CustomFuel coal = FuelRegistry.get("coal");
        assertNotNull(coal);
        assertEquals(0.05, coal.getSpeedBuff(), 0.001);
        assertFalse(coal.isPermanent());
        assertEquals(1800 * 1000L, coal.getDurationMillis());

        CustomFuel lava = FuelRegistry.get("enchanted_lava_bucket");
        assertNotNull(lava);
        assertEquals(0.25, lava.getSpeedBuff(), 0.001);
        assertTrue(lava.isPermanent());
    }

    @Test
    @DisplayName("tiers.yml should load all tiers 1-12 with valid delays and storage slots")
    void testTiersYmlLoadsCleanly() {
        File file = new File(RESOURCES_DIR, "tiers.yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        ConfigurationSection section = config.getConfigurationSection("tiers");
        assertNotNull(section, "tiers.yml must contain tiers section");

        MinionTier.load(section);

        assertEquals(12, MinionTier.getMaxTier());
        assertEquals(14.0, MinionTier.getActionDelay(1), 0.001);
        assertEquals(1, MinionTier.getStorageSlots(1));

        assertEquals(6.0, MinionTier.getActionDelay(12), 0.001);
        assertEquals(15, MinionTier.getStorageSlots(12));
    }
}
