package com.minionx;

import com.minionx.api.MinionArchetype;
import com.minionx.api.MinionDefinition;
import com.minionx.api.MinionRegistry;
import com.minionx.api.MinionTier;
import com.minionx.api.event.*;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class PhaseOneModularAndAPITest {

    @Test
    @DisplayName("Commercial LICENSE file should exist and designate AbhinavVanama for MinionX")
    void testCommercialLicenseFileExists() throws Exception {
        File licenseFile = new File("LICENSE");
        assertTrue(licenseFile.exists(), "LICENSE file must exist in project root");

        String content = Files.readString(licenseFile.toPath());
        assertTrue(content.contains("AbhinavVanama"), "License must reference author AbhinavVanama");
        assertTrue(content.contains("MinionX"), "License must reference plugin brand MinionX");
        assertTrue(content.contains("Commercial Proprietary") || content.contains("COMMERCIAL PROPRIETARY"), "License must be a commercial proprietary EULA");
    }

    @Test
    @DisplayName("Modular YAML resource files should exist and contain valid structures")
    void testModularConfigFilesExist() {
        ClassLoader cl = getClass().getClassLoader();

        // 1. tiers.yml
        InputStream tiersStream = cl.getResourceAsStream("tiers.yml");
        assertNotNull(tiersStream, "tiers.yml must exist in classpath resources");
        YamlConfiguration tiersConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(tiersStream, StandardCharsets.UTF_8));
        assertTrue(tiersConfig.contains("tiers.1"), "tiers.yml must contain tier 1");
        assertTrue(tiersConfig.contains("tiers.12"), "tiers.yml must contain tier 12");
        assertEquals(14.0, tiersConfig.getDouble("tiers.1.action-delay"));
        assertEquals(6.0, tiersConfig.getDouble("tiers.12.action-delay"));

        // 2. minions.yml
        InputStream minionsStream = cl.getResourceAsStream("minions.yml");
        assertNotNull(minionsStream, "minions.yml must exist in classpath resources");
        YamlConfiguration minionsConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(minionsStream, StandardCharsets.UTF_8));
        assertTrue(minionsConfig.contains("custom-minions"), "minions.yml must contain custom-minions section");

        // 3. fuels.yml
        InputStream fuelsStream = cl.getResourceAsStream("fuels.yml");
        assertNotNull(fuelsStream, "fuels.yml must exist in classpath resources");
        YamlConfiguration fuelsConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(fuelsStream, StandardCharsets.UTF_8));
        assertTrue(fuelsConfig.contains("fuels.coal"), "fuels.yml must contain coal fuel");
        assertTrue(fuelsConfig.contains("fuels.plasma_bucket"), "fuels.yml must contain plasma bucket fuel");

        // 4. upgrades.yml
        InputStream upgradesStream = cl.getResourceAsStream("upgrades.yml");
        assertNotNull(upgradesStream, "upgrades.yml must exist in classpath resources");
        YamlConfiguration upgradesConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(upgradesStream, StandardCharsets.UTF_8));
        assertTrue(upgradesConfig.contains("upgrades.auto_smelter"), "upgrades.yml must contain auto_smelter");
        assertTrue(upgradesConfig.contains("upgrades.super_compactor"), "upgrades.yml must contain super_compactor");

        // 5. recipes.yml
        InputStream recipesStream = cl.getResourceAsStream("recipes.yml");
        assertNotNull(recipesStream, "recipes.yml must exist in classpath resources");
        YamlConfiguration recipesConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(recipesStream, StandardCharsets.UTF_8));
        assertTrue(recipesConfig.contains("compression-recipes.cobblestone"), "recipes.yml must contain cobblestone recipe");
        assertTrue(recipesConfig.contains("compression-recipes.diamond"), "recipes.yml must contain diamond recipe");

        // 6. messages.yml
        InputStream messagesStream = cl.getResourceAsStream("messages.yml");
        assertNotNull(messagesStream, "messages.yml must exist in classpath resources");
        YamlConfiguration messagesConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(messagesStream, StandardCharsets.UTF_8));
        assertTrue(messagesConfig.contains("messages.prefix"), "messages.yml must contain chat prefix");
        assertTrue(messagesConfig.contains("messages.minion-already-exists"), "messages.yml must contain minion-already-exists");
    }

    @Test
    @DisplayName("Dynamic Tier System should support 12 default tiers, custom tier creation, and arbitrary Roman numerals")
    void testDynamicTierManagement() {
        assertTrue(MinionTier.getMaxTier() >= 12, "Default max tier should be at least 12");
        assertEquals("I", MinionTier.toRoman(1));
        assertEquals("XII", MinionTier.toRoman(12));
        assertEquals("XIII", MinionTier.toRoman(13));
        assertEquals("XVI", MinionTier.toRoman(16));
        assertEquals("XX", MinionTier.toRoman(20));
        assertEquals("XLII", MinionTier.toRoman(42));
        assertEquals("C", MinionTier.toRoman(100));

        // Create custom Tier 13
        MinionTier.saveTier(13, new MinionTier.TierData(5.5, 18, 150000));
        assertEquals(13, MinionTier.getMaxTier(), "Max tier should update to 13");
        assertEquals(5.5, MinionTier.getActionDelay(13), 0.01);
        assertEquals(18, MinionTier.getStorageSlots(13));
        assertEquals(150000, MinionTier.getTierData(13).getUpgradeCost());
        assertEquals(0, MinionTier.getUpgradeCost(13), "Upgrade cost for max tier should be 0");

        // Core 12 tiers cannot be deleted
        assertFalse(MinionTier.removeTier(12), "Default tier 12 should be protected against deletion");
        assertEquals(13, MinionTier.getMaxTier());

        // Custom tier 13 can be deleted
        assertTrue(MinionTier.removeTier(13), "Custom tier 13 should be removable");
        assertEquals(12, MinionTier.getMaxTier(), "Max tier should return to 12 after removing 13");
    }

    @Test
    @DisplayName("MinionDefinition DropEntry should support multiple drop items with individual drop chances")
    void testMultiDropAndChances() {
        MinionDefinition.DropEntry stringDrop = new MinionDefinition.DropEntry(new ItemStack(Material.STRING), 2, 1.0);
        MinionDefinition.DropEntry eyeDrop = new MinionDefinition.DropEntry(new ItemStack(Material.SPIDER_EYE), 1, 0.5);

        MinionDefinition spiderDef = new MinionDefinition("test_spider", "Test Spider", MinionArchetype.COMBAT,
                null, Material.LEATHER_CHESTPLATE, null, new ItemStack(Material.IRON_SWORD),
                null, "SPIDER", List.of(stringDrop, eyeDrop), 4, 13.0, 1);

        assertEquals(2, spiderDef.getDrops().size(), "Spider should have 2 configured drops");
        assertEquals(1.0, spiderDef.getDrops().get(0).getChance());
        assertEquals(0.5, spiderDef.getDrops().get(1).getChance());
        assertEquals(2, spiderDef.getDrops().get(0).getAmount());

        // Backwards compatibility verification
        assertEquals(Material.STRING, spiderDef.getExactDropItem().getType());
        assertEquals(Material.SPIDER_EYE, spiderDef.getSecondaryDropItem().getType());
        assertEquals(0.5, spiderDef.getSecondaryDropChance());

        // Test rollDrops: string is 100% guaranteed
        boolean sawEye = false;
        for (int i = 0; i < 50; i++) {
            List<ItemStack> rolled = spiderDef.rollDrops(1);
            assertFalse(rolled.isEmpty(), "Should roll at least the guaranteed drop");
            assertEquals(Material.STRING, rolled.get(0).getType());
            if (rolled.size() > 1 && rolled.get(1).getType() == Material.SPIDER_EYE) {
                sawEye = true;
            }
        }
        assertTrue(sawEye, "50% chance secondary drop should be rolled across 50 iterations");
    }

    @Test
    @DisplayName("YAML parsing should correctly parse custom minion with multi-drop chances schema")
    void testYamlMultiDropParsing() {
        String yaml = """
                custom-minions:
                  custom_spider:
                    display-name: "&cCustom Spider"
                    archetype: COMBAT
                    target-mob: SPIDER
                    held-tool: IRON_SWORD
                    action-delay: 12.0
                    storage-slots: 3
                    drops:
                      - item: STRING
                        amount: 3
                        chance: 1.0
                      - item: SPIDER_EYE
                        amount: 2
                        chance: 0.75
                """;

        YamlConfiguration config = new YamlConfiguration();
        try {
            config.loadFromString(yaml);
        } catch (Exception e) {
            fail("Failed to parse YAML: " + e.getMessage());
        }

        MinionRegistry.load(config);
        MinionDefinition def = MinionRegistry.get("custom_spider");
        assertNotNull(def, "custom_spider should be loaded in registry");
        assertEquals(2, def.getDrops().size());

        MinionDefinition.DropEntry d1 = def.getDrops().get(0);
        assertEquals(Material.STRING, d1.getItem().getType());
        assertEquals(3, d1.getAmount());
        assertEquals(1.0, d1.getChance());

        MinionDefinition.DropEntry d2 = def.getDrops().get(1);
        assertEquals(Material.SPIDER_EYE, d2.getItem().getType());
        assertEquals(2, d2.getAmount());
        assertEquals(0.75, d2.getChance());
    }

    @Test
    @DisplayName("Custom Bukkit events in com.minionx.api.event should be cancellable and hold event data")
    void testCustomBukkitEvents() {
        // MinionHarvestEvent
        List<ItemStack> drops = List.of(new ItemStack(Material.DIAMOND, 2));
        MinionHarvestEvent harvestEvent = new MinionHarvestEvent(null, drops, 15);
        assertEquals(1, harvestEvent.getDrops().size());
        assertEquals(15, harvestEvent.getXpGained());
        assertFalse(harvestEvent.isCancelled());
        harvestEvent.setCancelled(true);
        assertTrue(harvestEvent.isCancelled());

        // MinionPlaceEvent
        MinionPlaceEvent placeEvent = new MinionPlaceEvent(null, null, null, new ItemStack(Material.PLAYER_HEAD));
        assertFalse(placeEvent.isCancelled());
        placeEvent.setCancelled(true);
        assertTrue(placeEvent.isCancelled());

        // MinionPickupEvent
        MinionPickupEvent pickupEvent = new MinionPickupEvent(null, null);
        assertFalse(pickupEvent.isCancelled());
        pickupEvent.setCancelled(true);
        assertTrue(pickupEvent.isCancelled());

        // MinionUpgradeEvent
        MinionUpgradeEvent upgradeEvent = new MinionUpgradeEvent(null, null, 1, 2);
        assertEquals(1, upgradeEvent.getOldTier());
        assertEquals(2, upgradeEvent.getNewTier());
        assertFalse(upgradeEvent.isCancelled());
        upgradeEvent.setCancelled(true);
        assertTrue(upgradeEvent.isCancelled());

        // MinionCollectionEvent
        MinionCollectionEvent collectionEvent = new MinionCollectionEvent(null, null, drops, 25);
        assertEquals(25, collectionEvent.getXpCollected());
        assertFalse(collectionEvent.isCancelled());
        collectionEvent.setCancelled(true);
        assertTrue(collectionEvent.isCancelled());

        // MinionFuelConsumeEvent
        MinionFuelConsumeEvent fuelEvent = new MinionFuelConsumeEvent(null, null, true);
        assertTrue(fuelEvent.isBurnedOut());
        assertFalse(fuelEvent.isCancelled());

        // MinionStorageLinkEvent
        MinionStorageLinkEvent linkEvent = new MinionStorageLinkEvent(null, null, null, true);
        assertTrue(linkEvent.isLinking());
        assertFalse(linkEvent.isCancelled());

        // MinionQuarantineEvent
        MinionQuarantineEvent quarantineEvent = new MinionQuarantineEvent(null, true, "Admin toggle");
        assertTrue(quarantineEvent.isQuarantined());
        assertEquals("Admin toggle", quarantineEvent.getReason());
    }
}
