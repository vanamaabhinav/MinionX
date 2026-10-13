package com.minionx;

import com.minionx.api.*;
import com.minionx.core.CompressionManager;
import com.minionx.core.Minion;
import com.minionx.util.UpgradeCostHelper;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class CombatScalingAndEnchantedUpgradesTest {

    @BeforeEach
    void setup() {
        MinionRegistry.load();
        UpgradeRegistry.load();
        java.io.File recipesFile = new java.io.File("src/main/resources/recipes.yml");
        if (recipesFile.exists()) {
            org.bukkit.configuration.file.YamlConfiguration yaml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(recipesFile);
            org.bukkit.configuration.ConfigurationSection sec = yaml.getConfigurationSection("compression-recipes");
            CompressionManager.loadRecipes(sec);
        }
    }

    @Test
    @DisplayName("Monster minions action delays must decrease or stay equal across tiers and never reverse or increase")
    void testMonsterMinionActionDelayScaling() {
        String[] monsterMinions = new String[]{"zombie", "spider", "skeleton", "creeper", "enderman", "blaze"};

        for (String id : monsterMinions) {
            MinionDefinition def = MinionRegistry.get(id);
            assertNotNull(def, "Monster minion definition for " + id + " should exist");

            double prevDelay = def.getActionDelay(1);
            assertTrue(prevDelay > 0, "Tier 1 delay for " + id + " must be positive");

            for (int tier = 2; tier <= 12; tier++) {
                double tierDelay = def.getActionDelay(tier);
                assertTrue(tierDelay <= prevDelay + 0.0001,
                        "Action delay for " + id + " Tier " + tier + " (" + tierDelay + "s) must not exceed Tier "
                                + (tier - 1) + " (" + prevDelay + "s)!");
                prevDelay = tierDelay;
            }

            // Verify max tier is significantly faster than tier 1
            assertTrue(def.getActionDelay(12) < def.getActionDelay(1),
                    "Max tier delay for " + id + " must be strictly faster than Tier 1!");
        }
    }

    @Test
    @DisplayName("UpgradeCostHelper requires regular items for Tiers 1-4 and Enchanted items for Tier 5+")
    void testUpgradeCostHelperRequirements() {
        Minion minion = new Minion(UUID.randomUUID(), UUID.randomUUID(), MinionType.COBBLESTONE, 1, null);

        // Tier 1 -> Tier 2
        UpgradeCostHelper.Requirement req1 = UpgradeCostHelper.getRequirement(minion);
        assertFalse(req1.isEnchanted(), "Tier 1 upgrade should not require enchanted items");
        assertEquals(80, req1.getRequiredAmount());
        assertEquals(Material.COBBLESTONE, req1.getMaterial());

        // Tier 4 -> Tier 5
        minion.setTier(4);
        UpgradeCostHelper.Requirement req4 = UpgradeCostHelper.getRequirement(minion);
        assertFalse(req4.isEnchanted(), "Tier 4 upgrade should not require enchanted items");
        assertEquals(minion.getUpgradeCost(), req4.getRequiredAmount());

        // Tier 5 -> Tier 6: requires Enchanted items
        minion.setTier(5);
        UpgradeCostHelper.Requirement req5 = UpgradeCostHelper.getRequirement(minion);
        assertTrue(req5.isEnchanted(), "Tier 5 upgrade must require Enchanted items");
        assertEquals(8, req5.getRequiredAmount(), "Tier 5 should require 8 Enchanted items (1024 raw equivalent)");
        assertTrue(req5.getDisplayName().contains("Enchanted"), "Display name should indicate Enchanted item");

        // Tier 6 -> Tier 7
        minion.setTier(6);
        UpgradeCostHelper.Requirement req6 = UpgradeCostHelper.getRequirement(minion);
        assertTrue(req6.isEnchanted());
        assertEquals(16, req6.getRequiredAmount());

        // Tier 7 -> Tier 8
        minion.setTier(7);
        UpgradeCostHelper.Requirement req7 = UpgradeCostHelper.getRequirement(minion);
        assertTrue(req7.isEnchanted());
        assertEquals(32, req7.getRequiredAmount());

        // Tier 8 -> Tier 9
        minion.setTier(8);
        UpgradeCostHelper.Requirement req8 = UpgradeCostHelper.getRequirement(minion);
        assertTrue(req8.isEnchanted());
        assertEquals(64, req8.getRequiredAmount());

        // Tier 11 -> Tier 12
        minion.setTier(11);
        UpgradeCostHelper.Requirement req11 = UpgradeCostHelper.getRequirement(minion);
        assertTrue(req11.isEnchanted());
        assertEquals(512, req11.getRequiredAmount());
        // 512 enchanted items = 8 stacks, comfortably fits in 36-slot player inventory
        assertTrue(req11.getRequiredAmount() <= 512);
    }

    @Test
    @DisplayName("Hoppers are identified as auto-sellers and non-sellers are distinguished")
    void testAutoSellerIdentification() {
        CustomUpgrade budgetHopper = UpgradeRegistry.get("budget_hopper");
        assertNotNull(budgetHopper);
        assertTrue(budgetHopper.isAutoSeller());

        CustomUpgrade enchantedHopper = UpgradeRegistry.get("enchanted_hopper");
        assertNotNull(enchantedHopper);
        assertTrue(enchantedHopper.isAutoSeller());

        CustomUpgrade autoSmelter = UpgradeRegistry.get("auto_smelter");
        assertNotNull(autoSmelter);
        assertFalse(autoSmelter.isAutoSeller());

        CustomUpgrade diamondSpreading = UpgradeRegistry.get("diamond_spreading");
        assertNotNull(diamondSpreading);
        assertFalse(diamondSpreading.isAutoSeller());
    }

    @Test
    @DisplayName("Zombie minion upgrade requirements use Rotten Flesh and Enchanted Rotten Flesh")
    void testZombieUpgradeRequirements() {
        Minion zombieMinion = new Minion(UUID.randomUUID(), UUID.randomUUID(), null, 1, null);
        zombieMinion.setCustomDefinitionId("zombie");

        UpgradeCostHelper.Requirement req1 = UpgradeCostHelper.getRequirement(zombieMinion);
        assertFalse(req1.isEnchanted());
        assertEquals(Material.ROTTEN_FLESH, req1.getMaterial());

        zombieMinion.setTier(5);
        UpgradeCostHelper.Requirement req5 = UpgradeCostHelper.getRequirement(zombieMinion);
        assertTrue(req5.isEnchanted());
        assertEquals(Material.ROTTEN_FLESH, req5.getMaterial());
        assertEquals(8, req5.getRequiredAmount());
    }
}
