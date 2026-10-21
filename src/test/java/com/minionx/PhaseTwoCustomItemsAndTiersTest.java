package com.minionx;

import com.minionx.api.MinionArchetype;
import com.minionx.api.MinionDefinition;
import com.minionx.api.MinionRegistry;
import com.minionx.core.CompressionManager;
import com.minionx.core.Minion;
import com.minionx.item.CustomItemSpec;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class PhaseTwoCustomItemsAndTiersTest {

    @Test
    @DisplayName("MinionDefinition and Minion should support minion-specific maximum tier caps")
    void testMinionSpecificMaxTier() {
        // Create custom minion capped at Tier 5
        MinionDefinition defTier5 = new MinionDefinition("capped_spider", "Capped Spider", MinionArchetype.COMBAT,
                "eyJtest", Material.LEATHER_CHESTPLATE, null, new ItemStack(Material.IRON_SWORD),
                null, "SPIDER", List.of(new MinionDefinition.DropEntry(new ItemStack(Material.STRING), 1, 1.0)),
                2, 12.0, 1)
                .withMaxTier(5);

        assertEquals(5, defTier5.getMaxTier(), "Minion definition max tier should be 5");

        try {
            MinionRegistry.register(defTier5);

            // Instantiate minion with definition ID
            Minion minion = new Minion(UUID.randomUUID(), UUID.randomUUID(), null, 1, null);
            minion.setCustomDefinitionId("capped_spider");

            assertEquals(5, minion.getMaxTier(), "Minion instance should return definition's max tier of 5");

            // Attempt to upgrade to Tier 10
            minion.setTier(10);
            assertEquals(5, minion.getTier(), "Minion tier should be clamped to its individual max tier of 5");

            // Set to lower tier
            minion.setTier(3);
            assertEquals(3, minion.getTier());
        } finally {
            MinionRegistry.unregister("capped_spider");
        }

        // Default minion without explicit cap defaults to 12
        MinionDefinition defDefault = new MinionDefinition("default_miner", "Default Miner", MinionArchetype.MINER,
                null, Material.LEATHER_CHESTPLATE, null, new ItemStack(Material.DIAMOND_PICKAXE),
                Material.COBBLESTONE, null, List.of(new MinionDefinition.DropEntry(new ItemStack(Material.COBBLESTONE), 1, 1.0)),
                1, 14.0, 1);
        assertEquals(12, defDefault.getMaxTier(), "Default minion should have max tier 12");
    }

    @Test
    @DisplayName("Super Compactor should support custom items with CustomModelData, display names, lore, and PDC tags")
    void testSuperCompactorCustomItemSupport() {
        ItemStack customOutput = new ItemStack(Material.EMERALD_BLOCK);

        CompressionManager.CompressionRecipe customRecipe = new CompressionManager.CompressionRecipe(
                "custom_jade",
                Material.EMERALD,
                160,
                customOutput,
                1001,               // Expected input CustomModelData
                "§aRaw Jade Gem",   // Expected input display name
                "minionx:item_id",  // Expected input PDC key
                "raw_jade",         // Expected input PDC value
                true                // Custom item input flag
        );

        assertEquals("custom_jade", customRecipe.getId());
        assertEquals(Material.EMERALD, customRecipe.getInputMaterial());
        assertEquals(160, customRecipe.getInputAmount());
        assertTrue(customRecipe.isCustomInput());
        assertEquals(1001, customRecipe.getInputCustomModelData());
        assertEquals("§aRaw Jade Gem", customRecipe.getInputDisplayName());
        assertEquals("minionx:item_id", customRecipe.getInputPdcKey());
        assertEquals("raw_jade", customRecipe.getInputPdcValue());
        assertEquals(Material.EMERALD_BLOCK, customRecipe.createOutput().getType());

        // Test YAML ConfigurationSection parsing with custom items
        org.bukkit.configuration.file.YamlConfiguration yaml = new org.bukkit.configuration.file.YamlConfiguration();
        yaml.set("custom_ruby.input.material", "REDSTONE");
        yaml.set("custom_ruby.input.custom-model-data", 501);
        yaml.set("custom_ruby.input.display-name", "&cRaw Ruby");
        yaml.set("custom_ruby.input.pdc-key", "minionx:item_id");
        yaml.set("custom_ruby.input.pdc-value", "raw_ruby");
        yaml.set("custom_ruby.input-amount", 160);
        yaml.set("custom_ruby.output.material", "REDSTONE_BLOCK");
        yaml.set("custom_ruby.output.display-name", "&cEnchanted Ruby Block");
        yaml.set("custom_ruby.output.custom-model-data", 502);
        yaml.set("custom_ruby.output.pdc-key", "minionx:item_id");
        yaml.set("custom_ruby.output.pdc-value", "enchanted_ruby");

        CompressionManager.loadRecipes(yaml);
        CompressionManager.CompressionRecipe loaded = CompressionManager.getRecipe("custom_ruby");
        assertNotNull(loaded, "Custom ruby recipe should be loaded into registry");
        assertTrue(loaded.isCustomInput(), "Loaded recipe should be recognized as custom input");
        assertEquals(Material.REDSTONE, loaded.getInputMaterial());
        assertEquals(501, loaded.getInputCustomModelData());
        assertEquals("&cRaw Ruby", loaded.getInputDisplayName());
        assertEquals("minionx:item_id", loaded.getInputPdcKey());
        assertEquals("raw_ruby", loaded.getInputPdcValue());
        assertEquals(160, loaded.getInputAmount());
        assertEquals(Material.REDSTONE_BLOCK, loaded.createOutput().getType());
    }

    @Test
    @DisplayName("Release versioning should strictly adhere to v1.0.0 for deployment")
    void testReleaseVersioning() throws Exception {
        // Check plugin.yml
        File pluginYml = new File("src/main/resources/plugin.yml");
        assertTrue(pluginYml.exists());
        String pluginContent = Files.readString(pluginYml.toPath());
        assertTrue(pluginContent.contains("version: 1.0.0"), "plugin.yml version should be 1.0.0");

        // Check pom.xml
        File pomXml = new File("pom.xml");
        assertTrue(pomXml.exists());
        String pomContent = Files.readString(pomXml.toPath());
        assertTrue(pomContent.contains("<version>1.0.0</version>"), "pom.xml version should be 1.0.0");
    }

    @Test
    @DisplayName("Per-Minion Tier Data overrides action-delay, storage-slots, max-capacity, and upgrade-cost with fallback")
    void testPerMinionTierDataOverrides() {
        MinionDefinition customDef = new MinionDefinition("speedy_farmer", "Speedy Farmer", MinionArchetype.FARMING,
                "eyJtest", Material.LEATHER_CHESTPLATE, null, new ItemStack(Material.IRON_HOE),
                Material.WHEAT, null, List.of(new MinionDefinition.DropEntry(new ItemStack(Material.WHEAT), 1, 1.0)),
                1, 20.0, 1)
                .withMaxTier(6);

        // Custom override for Tier 1: custom delay 5.5s, 4 slots (256 capacity), upgrade cost 32
        customDef.setTierData(1, new MinionDefinition.TierData(5.5, 4, 32));
        // Custom override for Tier 2: custom delay 4.0s, 6 slots (384 capacity), upgrade cost 128
        customDef.setTierData(2, new MinionDefinition.TierData(4.0, 6, 128));

        try {
            MinionRegistry.register(customDef);

            // Verify definition returns custom tier 1 data
            assertEquals(5.5, customDef.getActionDelay(1), 0.001);
            assertEquals(4, customDef.getStorageSlots(1));
            assertEquals(256, customDef.getMaxCapacity(1));
            assertEquals(32, customDef.getUpgradeCost(1));

            // Verify definition returns custom tier 2 data
            assertEquals(4.0, customDef.getActionDelay(2), 0.001);
            assertEquals(6, customDef.getStorageSlots(2));
            assertEquals(384, customDef.getMaxCapacity(2));
            assertEquals(128, customDef.getUpgradeCost(2));

            // Verify fallback to global MinionTier for tier 3 (which was not explicitly overridden)
            assertEquals(com.minionx.api.MinionTier.getActionDelay(3), customDef.getActionDelay(3), 0.001);
            assertEquals(com.minionx.api.MinionTier.getStorageSlots(3), customDef.getStorageSlots(3));
            assertEquals(com.minionx.api.MinionTier.getUpgradeCost(3), customDef.getUpgradeCost(3));

            // Verify minion instance reflections
            Minion minion = new Minion(UUID.randomUUID(), UUID.randomUUID(), null, 1, null);
            minion.setCustomDefinitionId("speedy_farmer");

            assertEquals(5.5, minion.getBaseActionDelay(), 0.001);
            assertEquals(4, minion.getStorageSlots());
            assertEquals(256, minion.getMaxCapacity());
            assertEquals(32, minion.getUpgradeCost());

            // Level up minion to tier 2
            minion.setTier(2);
            assertEquals(4.0, minion.getBaseActionDelay(), 0.001);
            assertEquals(6, minion.getStorageSlots());
            assertEquals(384, minion.getMaxCapacity());
            assertEquals(128, minion.getUpgradeCost());

            // Level up minion to tier 3 (fallback)
            minion.setTier(3);
            assertEquals(com.minionx.api.MinionTier.getActionDelay(3), minion.getBaseActionDelay(), 0.001);
            assertEquals(com.minionx.api.MinionTier.getStorageSlots(3), minion.getStorageSlots());
            assertEquals(com.minionx.api.MinionTier.getUpgradeCost(3), minion.getUpgradeCost());
        } finally {
            MinionRegistry.unregister("speedy_farmer");
        }
    }

    @Test
    @DisplayName("Unified CustomItemSpec parses identically from Map and ConfigurationSection for drops and recipes")
    void testUnifiedCustomItemSpecDropsAndRecipesParsing() {
        Map<String, Object> itemMap = Map.of(
                "material", "DIAMOND",
                "amount", 2,
                "chance", 0.75,
                "display-name", "&bShiny Diamond",
                "custom-model-data", 2002,
                "glow", true,
                "pdc-key", "minionx:item_id",
                "pdc-value", "shiny_diamond"
        );

        CustomItemSpec fromMap = CustomItemSpec.fromMap(itemMap);
        assertEquals(Material.DIAMOND, fromMap.getMaterial());
        assertEquals(2, fromMap.getAmount());
        assertEquals(0.75, fromMap.getChance(), 0.001);
        assertEquals("&bShiny Diamond", fromMap.getDisplayName());
        assertEquals(2002, fromMap.getCustomModelData());
        assertTrue(fromMap.isGlow());
        assertEquals("minionx:item_id", fromMap.getPdcKey());
        assertEquals("shiny_diamond", fromMap.getPdcValue());

        // Test DropEntry serialization toConfigMap
        MinionDefinition.DropEntry dropEntry = new MinionDefinition.DropEntry(fromMap);
        Map<String, Object> serialized = dropEntry.toConfigMap();
        assertEquals("DIAMOND", serialized.get("material"));
        assertEquals(2, serialized.get("amount"));
        assertEquals(0.75, ((Number) serialized.get("chance")).doubleValue(), 0.001);
        assertEquals("&bShiny Diamond", serialized.get("display-name"));
        assertEquals(2002, serialized.get("custom-model-data"));
        assertEquals(true, serialized.get("glow"));
        assertEquals("minionx:item_id", serialized.get("pdc-key"));
        assertEquals("shiny_diamond", serialized.get("pdc-value"));

        // Test ConfigurationSection equivalent for recipes.yml input/output
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("recipe_test.input.material", "DIAMOND");
        yaml.set("recipe_test.input.amount", 2);
        yaml.set("recipe_test.input.display-name", "&bShiny Diamond");
        yaml.set("recipe_test.input.custom-model-data", 2002);
        yaml.set("recipe_test.input.glow", true);
        yaml.set("recipe_test.input.pdc-key", "minionx:item_id");
        yaml.set("recipe_test.input.pdc-value", "shiny_diamond");

        CustomItemSpec fromSection = CustomItemSpec.fromConfigSection(yaml.getConfigurationSection("recipe_test.input"));
        assertNotNull(fromSection);
        assertEquals(fromMap.getMaterial(), fromSection.getMaterial());
        assertEquals(fromMap.getAmount(), fromSection.getAmount());
        assertEquals(fromMap.getDisplayName(), fromSection.getDisplayName());
        assertEquals(fromMap.getCustomModelData(), fromSection.getCustomModelData());
        assertEquals(fromMap.isGlow(), fromSection.isGlow());
        assertEquals(fromMap.getPdcKey(), fromSection.getPdcKey());
        assertEquals(fromMap.getPdcValue(), fromSection.getPdcValue());
    }

    @Test
    @DisplayName("MinionRegistry saveMinionTier dynamically modifies in-memory definition")
    void testMinionRegistrySaveTierData() {
        MinionDefinition testDef = new MinionDefinition("test_tier_minion", "Test Tier Minion", MinionArchetype.COMBAT,
                "eyJtest", Material.LEATHER_CHESTPLATE, null, new ItemStack(Material.BOW),
                null, "SKELETON", List.of(new MinionDefinition.DropEntry(new ItemStack(Material.BONE), 1, 1.0)),
                1, 22.0, 1)
                .withMaxTier(8);

        try {
            MinionRegistry.register(testDef);

            MinionDefinition.TierData newTier3 = new MinionDefinition.TierData(15.0, 5, 250);
            // Note: saveMinionTier will update memory even if minions.yml doesn't have existing custom entry on disk
            MinionRegistry.saveMinionTier("test_tier_minion", 3, newTier3);

            MinionDefinition updatedDef = MinionRegistry.get("test_tier_minion");
            assertNotNull(updatedDef);
            assertEquals(15.0, updatedDef.getActionDelay(3), 0.001);
            assertEquals(5, updatedDef.getStorageSlots(3));
            assertEquals(250, updatedDef.getUpgradeCost(3));
        } finally {
            MinionRegistry.unregister("test_tier_minion");
        }
    }
}

