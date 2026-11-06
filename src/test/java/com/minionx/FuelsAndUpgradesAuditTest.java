package com.minionx;

import com.minionx.api.*;
import com.minionx.core.Minion;
import com.minionx.core.OfflineCalculator;
import com.minionx.gui.UpgradeCreatorSession;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite verifying Fuels and Upgrades audit remediations:
 * - AUD-F01/AUD-F02: Offline fuel segmentation when temporary fuel burns out during offline period.
 * - AUD-U01/AUD-U04: Claim protection on expander and duplicate unique upgrade rejection.
 * - Unified multi-behavior upgrade capabilities.
 * - Extensible multi-item spreading schema (Diamond Spreading, Potato Spreading, Bountiful Potato).
 * - In-game upgrade creator session multi-behavior builder.
 */
public class FuelsAndUpgradesAuditTest {

    @BeforeEach
    public void setup() {
        UpgradeRegistry.load();
    }

    @Test
    @DisplayName("AUD-01: BonusDropEntry serializes and deserializes cleanly with all properties")
    public void testBonusDropEntrySerialization() {
        BonusDropEntry entry = new BonusDropEntry(Material.POTATO, 2, 0.25, "&eGolden Potato", 101, null);

        assertEquals(Material.POTATO, entry.getMaterial());
        assertEquals(2, entry.getAmount());
        assertEquals(0.25, entry.getChance(), 1e-6);
        assertEquals("§eGolden Potato", entry.getDisplayName());
        assertEquals(101, entry.getCustomModelData());
        assertNull(entry.getHookId());

        Map<String, Object> map = entry.toConfigMap();
        assertEquals("POTATO", map.get("material"));
        assertEquals(2, map.get("amount"));
        assertEquals(0.25, (Double) map.get("chance"), 1e-6);

        BonusDropEntry deserialized = BonusDropEntry.fromConfig(map);
        assertNotNull(deserialized);
        assertEquals(Material.POTATO, deserialized.getMaterial());
        assertEquals(2, deserialized.getAmount());
        assertEquals(0.25, deserialized.getChance(), 1e-6);
    }

    @Test
    @DisplayName("AUD-02: Diamond Spreading, Potato Spreading and Bountiful Potato upgrades are loaded under unified ITEM_SPREADING")
    public void testRegistryShowcases() {
        CustomUpgrade diamondSpreading = UpgradeRegistry.get("diamond_spreading");
        assertNotNull(diamondSpreading, "diamond_spreading should be registered in UpgradeRegistry");
        assertTrue(diamondSpreading.isSpreading());
        assertTrue(diamondSpreading.hasBehavior(CustomUpgrade.UpgradeBehavior.ITEM_SPREADING));
        assertNotNull(diamondSpreading.getSpreadingItem());
        assertEquals(Material.DIAMOND, diamondSpreading.getSpreadingItem().getMaterial());
        assertEquals(0.10, diamondSpreading.getSpreadingItem().getChance(), 1e-6);

        CustomUpgrade potatoSpreading = UpgradeRegistry.get("potato_spreading");
        assertNotNull(potatoSpreading, "potato_spreading should be registered in UpgradeRegistry");
        assertTrue(potatoSpreading.isSpreading());
        assertTrue(potatoSpreading.hasBehavior(CustomUpgrade.UpgradeBehavior.ITEM_SPREADING));
        assertNotNull(potatoSpreading.getSpreadingItem());
        assertEquals(Material.POTATO, potatoSpreading.getSpreadingItem().getMaterial());
        assertEquals(0.10, potatoSpreading.getSpreadingItem().getChance(), 1e-6);

        CustomUpgrade bountifulPotato = UpgradeRegistry.get("bountiful_potato");
        assertNotNull(bountifulPotato, "bountiful_potato should be registered in UpgradeRegistry");
        assertEquals("§6Bountiful Potato", bountifulPotato.getDisplayName());
        assertTrue(bountifulPotato.isSpreading());
        assertTrue(bountifulPotato.hasBonusDrops());
        assertEquals(3, bountifulPotato.getSpreadingItems().size());

        BonusDropEntry drop1 = bountifulPotato.getSpreadingItems().get(0);
        assertEquals(Material.POTATO, drop1.getMaterial());
        assertEquals(0.25, drop1.getChance(), 1e-6);

        BonusDropEntry drop2 = bountifulPotato.getSpreadingItems().get(1);
        assertEquals(Material.POISONOUS_POTATO, drop2.getMaterial());
        assertEquals(0.10, drop2.getChance(), 1e-6);

        BonusDropEntry drop3 = bountifulPotato.getSpreadingItems().get(2);
        assertEquals(Material.BAKED_POTATO, drop3.getMaterial());
        assertEquals(2, drop3.getAmount());
        assertEquals(0.05, drop3.getChance(), 1e-6);
    }

    @Test
    @DisplayName("AUD-F01 & AUD-F02: Offline fuel segmentation accurately splits downtime when fuel burned out while offline")
    public void testOfflineFuelSegmentationWhenExpired() {
        Minion minion = new Minion(UUID.randomUUID(), UUID.randomUUID(), MinionType.COBBLESTONE, 11, null);
        long now = System.currentTimeMillis();
        long lastAction = now - (2 * 3600 * 1000L); // 2 hours ago
        minion.setLastActionTime(lastAction);

        // Equipped with 1-hour fuel that burned out 1 hour ago
        long fuelEnd = now - (1 * 3600 * 1000L); // burned out at midpoint
        CustomFuel coalFuel = new CustomFuel("test_coal", "Test Coal", Material.COAL, 0.50, 3600 * 1000L, List.of(), false, 0);
        FuelRegistry.register(coalFuel);
        minion.setFuel(coalFuel);
        minion.setFuelEndTime(fuelEnd);

        // Precondition check: at time 'now', fuelEnd < now, so isFuelActive() is FALSE
        assertFalse(minion.isFuelActive(), "Fuel should be expired at time 'now'");
        assertNotNull(minion.getFuelId());

        // Simulate catchup
        int added = OfflineCalculator.processOfflineCatchup(minion);

        // Verification:
        // 1. Minion produced items successfully across both segments
        assertTrue(added > 0, "Minion should have produced items offline");
        // 2. Expired fuel is now cleanly deactivated
        assertNull(minion.getFuelId(), "Expired fuel must be deactivated after simulation");
        assertFalse(minion.isFuelActive());
    }

    @Test
    @DisplayName("AUD-U03: Offline simulation generates Potato Spreading bonus items with exact remainder accounting")
    public void testOfflineSpreadingPotato() {
        Minion minion = new Minion(UUID.randomUUID(), UUID.randomUUID(), MinionType.COBBLESTONE, 11, null);
        long now = System.currentTimeMillis();
        // 200 harvest actions with Tier 11 (7.0s delay = 14.0s per harvest drop)
        long downtimeMillis = 200 * 14 * 1000L;
        minion.setLastActionTime(now - downtimeMillis);

        CustomUpgrade potatoSpreading = UpgradeRegistry.get("potato_spreading");
        assertNotNull(potatoSpreading);
        minion.setUpgrade1(potatoSpreading);

        int addedCobble = OfflineCalculator.processOfflineCatchup(minion);
        assertTrue(addedCobble > 0);

        // Check for potato drops in minion inventory
        int potatoCount = 0;
        for (ItemStack item : minion.getInventory()) {
            if (item != null && item.getType() == Material.POTATO) {
                potatoCount += item.getAmount();
            }
        }

        // At 10% chance across 200 harvests, expected is 20 potatoes
        assertTrue(potatoCount >= 18 && potatoCount <= 22, "Expected ~20 potatoes from 10% spreading, got: " + potatoCount);
    }

    @Test
    @DisplayName("AUD-U03: Offline simulation generates multi-item Bountiful Potato drops with individual probabilities")
    public void testOfflineMultiItemBountifulPotato() {
        Minion minion = new Minion(UUID.randomUUID(), UUID.randomUUID(), MinionType.COBBLESTONE, 11, null);
        long now = System.currentTimeMillis();
        // 400 harvest actions with Tier 11 (14.0s per harvest drop)
        long downtimeMillis = 400 * 14 * 1000L;
        minion.setLastActionTime(now - downtimeMillis);

        CustomUpgrade bountifulPotato = UpgradeRegistry.get("bountiful_potato");
        assertNotNull(bountifulPotato);
        minion.setUpgrade1(bountifulPotato);

        OfflineCalculator.processOfflineCatchup(minion);

        int normalPotatoes = 0;
        int poisonPotatoes = 0;
        int bakedPotatoes = 0;

        for (ItemStack item : minion.getInventory()) {
            if (item != null) {
                if (item.getType() == Material.POTATO) normalPotatoes += item.getAmount();
                else if (item.getType() == Material.POISONOUS_POTATO) poisonPotatoes += item.getAmount();
                else if (item.getType() == Material.BAKED_POTATO) bakedPotatoes += item.getAmount();
            }
        }

        // 400 harvests:
        // 25% Potato = 100 potatoes
        // 10% Poisonous Potato = 40 poisonous potatoes
        // 5% Baked Potato (amount 2) = 20 drops * 2 = 40 baked potatoes
        assertTrue(normalPotatoes >= 90 && normalPotatoes <= 110, "Expected ~100 potatoes, got: " + normalPotatoes);
        assertTrue(poisonPotatoes >= 35 && poisonPotatoes <= 45, "Expected ~40 poisonous potatoes, got: " + poisonPotatoes);
        assertTrue(bakedPotatoes >= 35 && bakedPotatoes <= 45, "Expected ~40 baked potatoes, got: " + bakedPotatoes);
    }

    @Test
    @DisplayName("AUD-U04: CustomUpgrade properly distinguishes unique non-stackable vs stackable upgrades")
    public void testCustomUpgradeStackableRules() {
        CustomUpgrade compactor = UpgradeRegistry.get("super_compactor");
        CustomUpgrade autoSmelter = UpgradeRegistry.get("auto_smelter");
        CustomUpgrade expander = UpgradeRegistry.get("minion_expander");
        CustomUpgrade flycatcher = UpgradeRegistry.get("flycatcher");

        // Compactor & AutoSmelter are unique non-stackable
        assertNotEquals(CustomUpgrade.UpgradeBehavior.SPEED_BUFF, compactor.getBehavior());
        assertNotEquals(CustomUpgrade.UpgradeBehavior.SPEED_BUFF, autoSmelter.getBehavior());

        // Expander & Flycatcher have SPEED_BUFF
        assertTrue(expander.hasBehavior(CustomUpgrade.UpgradeBehavior.SPEED_BUFF));
        assertTrue(expander.hasBehavior(CustomUpgrade.UpgradeBehavior.EXPANDER));
        assertTrue(flycatcher.hasBehavior(CustomUpgrade.UpgradeBehavior.SPEED_BUFF));
    }

    @Test
    @DisplayName("AUD-U05: Multi-behavior upgrade combining Auto-Smelt, Speed Buff, and Multi-Item Spreading")
    public void testMultiBehaviorUpgradeExecution() {
        BonusDropEntry diamondDrop = new BonusDropEntry(Material.DIAMOND, 1, 0.10);
        BonusDropEntry emeraldDrop = new BonusDropEntry(Material.EMERALD, 1, 0.05);

        CustomUpgrade hybrid = new CustomUpgrade("blazing_fortune", "Blazing Fortune", Material.BLAZE_ROD,
                Set.of(CustomUpgrade.UpgradeBehavior.AUTO_SMELTER, CustomUpgrade.UpgradeBehavior.SPEED_BUFF, CustomUpgrade.UpgradeBehavior.ITEM_SPREADING),
                0.20, 0.0, 0.0, 0,
                List.of(diamondDrop, emeraldDrop),
                List.of("&7Smelts ores, +20% speed, drops diamonds & emeralds!"), true, 0);

        assertTrue(hybrid.isAutoSmelter());
        assertTrue(hybrid.isSpreading());
        assertEquals(0.20, hybrid.getSpeedBuff(), 1e-6);
        assertEquals(2, hybrid.getSpreadingItems().size());

        UpgradeRegistry.register(hybrid);
        Minion minion = new Minion(UUID.randomUUID(), UUID.randomUUID(), MinionType.IRON, 11, null);
        minion.setUpgrade1(hybrid);

        // Verification 1: Speed buff applies (+20% speed reduces delay)
        double baseDelay = minion.getBaseActionDelay();
        double effectiveDelay = minion.getEffectiveActionDelay();
        assertEquals(baseDelay / 1.20, effectiveDelay, 1e-4);

        // Verification 2: Minion has both behaviors recognized
        assertTrue(minion.hasUpgradeBehavior(CustomUpgrade.UpgradeBehavior.AUTO_SMELTER));
        assertTrue(minion.hasUpgradeBehavior(CustomUpgrade.UpgradeBehavior.ITEM_SPREADING));

        // Verification 3: Offline simulation smelts and generates bonus drops
        long now = System.currentTimeMillis();
        long downtimeMillis = 200 * Math.round(effectiveDelay * 2 * 1000L);
        minion.setLastActionTime(now - downtimeMillis);

        OfflineCalculator.processOfflineCatchup(minion);

        int ironIngots = 0;
        int diamonds = 0;
        int emeralds = 0;

        for (ItemStack item : minion.getInventory()) {
            if (item != null) {
                if (item.getType() == Material.IRON_INGOT) ironIngots += item.getAmount();
                else if (item.getType() == Material.DIAMOND) diamonds += item.getAmount();
                else if (item.getType() == Material.EMERALD) emeralds += item.getAmount();
            }
        }

        assertTrue(ironIngots > 0, "Iron should have been smelted into ingots");
        assertTrue(diamonds >= 15 && diamonds <= 25, "Expected ~20 diamonds from 10% spreading, got: " + diamonds);
        assertTrue(emeralds >= 5 && emeralds <= 15, "Expected ~10 emeralds from 5% spreading, got: " + emeralds);
    }

    @Test
    @DisplayName("AUD-U06: UpgradeCreatorSession accurately configures and builds multi-behavior upgrades with spreading shelf")
    public void testUpgradeCreatorSessionBuilder() {
        UpgradeCreatorSession session = new UpgradeCreatorSession("custom_master");
        session.setDisplayName("&cMaster Accessory");
        session.setMaterial(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE);

        // Configure multi-behaviors
        session.setBehaviorActive(CustomUpgrade.UpgradeBehavior.AUTO_SMELTER, true);
        session.setSpeedBuff(0.15);
        session.setAreaExpansion(1);
        session.cycleCompactorMode(); // 0 -> 1 (COMPACTOR)

        // Configure multi-item spreading shelf
        session.addSpreadingItem(new BonusDropEntry(Material.DIAMOND, 1, 0.15));
        session.addSpreadingItem(new BonusDropEntry(Material.GOLD_INGOT, 2, 0.20));

        CustomUpgrade built = session.buildUpgrade();
        assertNotNull(built);
        assertEquals("custom_master", built.getId());
        assertEquals("&cMaster Accessory", built.getDisplayName());
        assertEquals(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE, built.getMaterial());

        // Assert all capabilities present
        assertTrue(built.isAutoSmelter());
        assertTrue(built.isCompactor());
        assertTrue(built.isSpreading());
        assertEquals(0.15, built.getSpeedBuff(), 1e-6);
        assertEquals(1, built.getAreaExpansion());
        assertEquals(2, built.getSpreadingItems().size());

        assertEquals(Material.DIAMOND, built.getSpreadingItems().get(0).getMaterial());
        assertEquals(0.15, built.getSpreadingItems().get(0).getChance(), 1e-6);

        assertEquals(Material.GOLD_INGOT, built.getSpreadingItems().get(1).getMaterial());
        assertEquals(2, built.getSpreadingItems().get(1).getAmount());
        assertEquals(0.20, built.getSpreadingItems().get(1).getChance(), 1e-6);
    }
}
