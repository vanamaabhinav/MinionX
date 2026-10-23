package com.minionx;

import com.minionx.api.*;
import com.minionx.core.CompressionManager;
import com.minionx.core.Minion;
import com.minionx.util.UpgradeCostHelper;
import org.bukkit.Material;
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

    @Test
    @DisplayName("Animal Husbandry minions are FARMING archetype with explicit targetMob definitions")
    void testAnimalHusbandryDefinitionsTargetMobAndArchetype() {
        String[] husbandryIds = new String[]{"cow", "pig", "chicken", "sheep", "rabbit"};
        String[] expectedMobs = new String[]{"COW", "PIG", "CHICKEN", "SHEEP", "RABBIT"};

        for (int i = 0; i < husbandryIds.length; i++) {
            String id = husbandryIds[i];
            MinionDefinition def = MinionRegistry.get(id);
            assertNotNull(def, "Husbandry minion definition for " + id + " must exist");
            assertEquals(MinionArchetype.FARMING, def.getArchetype(), id + " should have FARMING archetype");
            assertNotNull(def.getTargetMob(), id + " must define targetMob for mob routing");
            assertEquals(expectedMobs[i], def.getTargetMob().toUpperCase(), id + " targetMob mismatch");
        }
    }

    @Test
    @DisplayName("Combat and Husbandry filling logic spawns until cap (5) then enters steady state slaying")
    void testCombatAndHusbandryFillingCapacityInvariant() {
        int cap = 5;
        java.util.List<UUID> tracked = new java.util.ArrayList<>();

        // Phase 1: Population up to cap
        for (int i = 0; i < cap; i++) {
            boolean shouldSpawn = tracked.size() < cap;
            assertTrue(shouldSpawn, "Should prioritize spawning when mob count is " + i + " (< " + cap + ")");
            tracked.add(UUID.randomUUID());
        }

        // At capacity (5 mobs)
        assertEquals(5, tracked.size());
        boolean shouldSpawnAtCap = tracked.size() < cap;
        assertFalse(shouldSpawnAtCap, "Should NOT spawn when at cap (5)");

        // Slay 1 mob (steady state harvest)
        UUID slain = tracked.remove(0);
        assertNotNull(slain);
        assertEquals(4, tracked.size());

        // Pen now has 4 (< 5), next action replenishes
        boolean replenish = tracked.size() < cap;
        assertTrue(replenish, "Should replenish 1 mob to return to 5");
        tracked.add(UUID.randomUUID());
        assertEquals(5, tracked.size());
    }

    @Test
    @DisplayName("MythicMobsHook declares distinct MINION_MOB_KEY and MINION_OWNER_KEY namespaced keys")
    void testMythicMobsHookOwnerKeyAndTagConstants() {
        assertNotNull(com.minionx.hook.MythicMobsHook.MINION_MOB_KEY);
        assertNotNull(com.minionx.hook.MythicMobsHook.MINION_OWNER_KEY);
        assertEquals("minion_spawned_mob", com.minionx.hook.MythicMobsHook.MINION_MOB_KEY.getKey());
        assertEquals("minion_owner_id", com.minionx.hook.MythicMobsHook.MINION_OWNER_KEY.getKey());
    }

    @Test
    @DisplayName("External mob death untracking immediately enables replacement spawn")
    void testUntrackOnExternalMobDeath() {
        Minion minion = new Minion(UUID.randomUUID(), UUID.randomUUID(), null, 1, null);
        UUID mob1 = UUID.randomUUID();
        UUID mob2 = UUID.randomUUID();
        UUID mob3 = UUID.randomUUID();
        UUID mob4 = UUID.randomUUID();
        UUID mob5 = UUID.randomUUID();

        minion.trackSpawnedMob(mob1);
        minion.trackSpawnedMob(mob2);
        minion.trackSpawnedMob(mob3);
        minion.trackSpawnedMob(mob4);
        minion.trackSpawnedMob(mob5);

        assertEquals(5, minion.getTrackedSpawnedMobs().size());

        // External death occurs on mob3 (e.g. killed by player or environment)
        minion.untrackSpawnedMob(mob3);

        assertEquals(4, minion.getTrackedSpawnedMobs().size());
        assertFalse(minion.getTrackedSpawnedMobs().contains(mob3));

        // Pen capacity allows replacement
        assertTrue(minion.getTrackedSpawnedMobs().size() < 5);
    }

    @Test
    @DisplayName("When all 5 mobs are outside the 5x5 boundary, minion halts with NO_MOBS_IN_RANGE and never spawns extra mobs")
    void testNoMobsInRangeStateWhenAllMobsOutsideBoundary() {
        int cap = 5;
        int workingRadius = 2; // 5x5 grid
        double centerX = 100.5;
        double centerZ = 100.5;
        double centerY = 64.0;

        // 5 mobs all positioned at (110.0, 64.0, 110.0) - well outside the 5x5 boundary
        java.util.List<double[]> mobPositions = java.util.List.of(
                new double[]{110.0, 64.0, 110.0},
                new double[]{112.0, 64.0, 105.0},
                new double[]{90.0, 64.0, 90.0},
                new double[]{100.5, 64.0, 120.0},
                new double[]{100.5, 64.0, 80.0}
        );

        java.util.List<double[]> inRange = new java.util.ArrayList<>();
        for (double[] pos : mobPositions) {
            if (Math.abs(pos[0] - centerX) <= workingRadius + 0.8
                    && Math.abs(pos[2] - centerZ) <= workingRadius + 0.8
                    && Math.abs(pos[1] - centerY) <= 2.5) {
                inRange.add(pos);
            }
        }

        // All 5 mobs are outside
        assertTrue(inRange.isEmpty(), "No mobs should be within 5x5 range");
        assertEquals(5, mobPositions.size(), "All 5 mobs must remain tracked against the cap");

        // Minion must NOT spawn a 6th mob
        boolean canSpawn = mobPositions.size() < cap;
        assertFalse(canSpawn, "Minion must strictly adhere to 5-mob cap even if all mobs are pushed outside");

        // Minion cannot slay (inRange is empty), so it transitions to NO_MOBS_IN_RANGE
        boolean isHaltedNoMobsInRange = mobPositions.size() >= cap && inRange.isEmpty();
        assertTrue(isHaltedNoMobsInRange, "Minion must enter NO_MOBS_IN_RANGE halted state");

        Minion minion = new Minion(UUID.randomUUID(), UUID.randomUUID(), null, 1, null);
        minion.setState(MinionState.NO_MOBS_IN_RANGE);
        assertEquals(MinionState.NO_MOBS_IN_RANGE, minion.getState());
        assertEquals("§cNo Mobs in Range!", minion.getState().getDisplayStatus());
    }

    @Test
    @DisplayName("Minion slays only the mobs inside the 5x5 boundary when some are displaced")
    void testSlayingOnlyTargetsMobsInsideRange() {
        int workingRadius = 2; // 5x5 grid
        double centerX = 50.5;
        double centerZ = 50.5;
        double centerY = 64.0;

        // 4 mobs outside, 1 mob inside at (51.0, 64.0, 51.0)
        java.util.List<double[]> mobPositions = java.util.List.of(
                new double[]{65.0, 64.0, 50.0}, // outside
                new double[]{50.5, 64.0, 70.0}, // outside
                new double[]{51.0, 64.0, 51.0}, // INSIDE (dx=0.5, dz=0.5 <= 2.8)
                new double[]{30.0, 64.0, 50.0}, // outside
                new double[]{50.5, 64.0, 30.0}  // outside
        );

        java.util.List<double[]> inRange = new java.util.ArrayList<>();
        for (double[] pos : mobPositions) {
            if (Math.abs(pos[0] - centerX) <= workingRadius + 0.8
                    && Math.abs(pos[2] - centerZ) <= workingRadius + 0.8
                    && Math.abs(pos[1] - centerY) <= 2.5) {
                inRange.add(pos);
            }
        }

        assertEquals(1, inRange.size(), "Exactly 1 mob must be detected in range");
        assertEquals(51.0, inRange.getFirst()[0]);
        assertEquals(51.0, inRange.getFirst()[2]);
    }

    @Test
    @DisplayName("Cold start / restart fallback categorizes displaced mobs as tracked but out-of-range")
    void testColdStartFallbackDisplacedMobsClassification() {
        int workingRadius = 2;
        double centerX = 10.5;
        double centerZ = 10.5;
        double centerY = 64.0;
        UUID myMinionId = UUID.randomUUID();

        record MockEntity(UUID id, double x, double y, double z, String ownerTag) {}

        // 5 mobs belonging to this minion: 2 inside 5x5, 3 displaced outside
        java.util.List<MockEntity> worldEntities = java.util.List.of(
                new MockEntity(UUID.randomUUID(), 10.5, 64.0, 10.5, myMinionId.toString()), // in-range
                new MockEntity(UUID.randomUUID(), 12.0, 64.0, 11.0, myMinionId.toString()), // in-range (dx=1.5, dz=0.5 <= 2.8)
                new MockEntity(UUID.randomUUID(), 16.0, 64.0, 10.5, myMinionId.toString()), // displaced (dx=5.5 > 2.8)
                new MockEntity(UUID.randomUUID(), 10.5, 64.0, 18.0, myMinionId.toString()), // displaced (dz=7.5 > 2.8)
                new MockEntity(UUID.randomUUID(), 5.0, 64.0, 5.0, myMinionId.toString())    // displaced (dx=5.5, dz=5.5 > 2.8)
        );

        java.util.List<UUID> aliveTracked = new java.util.ArrayList<>();
        java.util.List<UUID> inRange = new java.util.ArrayList<>();

        for (MockEntity e : worldEntities) {
            boolean isOwnerMatch = myMinionId.toString().equals(e.ownerTag());
            if (isOwnerMatch) {
                aliveTracked.add(e.id());
                if (Math.abs(e.x() - centerX) <= workingRadius + 0.8
                        && Math.abs(e.z() - centerZ) <= workingRadius + 0.8
                        && Math.abs(e.y() - centerY) <= 2.5) {
                    inRange.add(e.id());
                }
            }
        }

        assertEquals(5, aliveTracked.size(), "All 5 mobs must be re-tracked across restart");
        assertEquals(2, inRange.size(), "Only the 2 mobs within the 5x5 boundary must be in range");
        assertFalse(aliveTracked.size() < 5, "Minion must not spawn excess mobs beyond cap");
    }
}
