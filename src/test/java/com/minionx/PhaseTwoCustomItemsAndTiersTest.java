package com.minionx;

import com.minionx.api.MinionArchetype;
import com.minionx.api.MinionDefinition;
import com.minionx.api.MinionRegistry;
import com.minionx.api.MinionTier;
import com.minionx.core.CompressionManager;
import com.minionx.core.Minion;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class PhaseTwoCustomItemsAndTiersTest {

    @Test
    @DisplayName("MinionDefinition and Minion should support minion-specific maximum tier caps")
    void testMinionSpecificMaxTier() {
        // Create custom minion capped at Tier 5
        MinionDefinition defTier5 = new MinionDefinition("capped_spider", "Capped Spider", MinionArchetype.COMBAT,
                null, Material.LEATHER_CHESTPLATE, null, new ItemStack(Material.IRON_SWORD),
                null, "SPIDER", List.of(new MinionDefinition.DropEntry(new ItemStack(Material.STRING), 1, 1.0)),
                2, 12.0, 1)
                .withMaxTier(5);

        assertEquals(5, defTier5.getMaxTier(), "Minion definition max tier should be 5");

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
    @DisplayName("Pre-release versioning should strictly adhere to v0.x.x until final release")
    void testPreReleaseVersioning() throws Exception {
        // Check plugin.yml
        File pluginYml = new File("src/main/resources/plugin.yml");
        assertTrue(pluginYml.exists());
        String pluginContent = Files.readString(pluginYml.toPath());
        assertTrue(pluginContent.contains("version: 0.2.0"), "plugin.yml version should be 0.2.0");
        assertFalse(pluginContent.contains("version: 1.0.0"), "plugin.yml must not be 1.0.0 before official release");

        // Check pom.xml
        File pomXml = new File("pom.xml");
        assertTrue(pomXml.exists());
        String pomContent = Files.readString(pomXml.toPath());
        assertTrue(pomContent.contains("<version>0.2.0-SNAPSHOT</version>"), "pom.xml version should be 0.2.0-SNAPSHOT");
        assertFalse(pomContent.contains("<version>1.0.0</version>"), "pom.xml must not be 1.0.0 before official release");
    }
}
