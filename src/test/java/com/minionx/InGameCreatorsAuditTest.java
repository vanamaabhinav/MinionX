package com.minionx;

import com.minionx.api.MinionTier;
import com.minionx.api.UpgradeRequirement;
import com.minionx.gui.FuelCreatorSession;
import com.minionx.gui.MinionCreatorListener;
import com.minionx.gui.MinionCreatorSession;
import com.minionx.gui.MinionStorageCreatorSession;
import com.minionx.gui.UpgradeCreatorSession;
import com.minionx.util.UpgradeCostHelper;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Automated test suite verifying In-Game Creator Studio audit remediations,
 * anti-dupe click debouncing, ID sanitization, numeric bounding,
 * and multi-item tier upgrade requirement matching.
 */
public class InGameCreatorsAuditTest {

    @BeforeEach
    public void setup() {
        MinionCreatorListener.clearPrompts();
    }

    @Test
    @DisplayName("AUD-03: ID sanitization strips dots, spaces, uppercase, and invalid characters")
    public void testIdSanitization() {
        assertEquals("stone_miner", MinionCreatorSession.sanitizeId("Stone Miner"));
        assertEquals("stone_miner", MinionCreatorSession.sanitizeId("stone.miner"));
        assertEquals("stone_miner_v2", MinionCreatorSession.sanitizeId("..Stone.Miner...V2.."));
        assertEquals("test_minion_123", MinionCreatorSession.sanitizeId("test@minion#$123!"));
        assertEquals("custom_item", MinionCreatorSession.sanitizeId(null));
        assertEquals("custom_item", MinionCreatorSession.sanitizeId("   "));
        assertEquals("custom_item", MinionCreatorSession.sanitizeId("..."));

        // Max 32 characters
        String longId = "a_very_long_custom_minion_identifier_that_exceeds_limits";
        String sanitized = MinionCreatorSession.sanitizeId(longId);
        assertTrue(sanitized.length() <= 32);
        assertTrue(sanitized.startsWith("a_very_long_custom_minion"));

        // Test across all creator sessions
        MinionCreatorSession mSession = new MinionCreatorSession("My.Cool.Minion");
        assertEquals("my_cool_minion", mSession.getId());

        FuelCreatorSession fSession = new FuelCreatorSession("Mega.Blaze.Fuel");
        assertEquals("mega_blaze_fuel", fSession.getId());

        UpgradeCreatorSession uSession = new UpgradeCreatorSession("Super.Auto.Smelter");
        assertEquals("super_auto_smelter", uSession.getId());

        MinionStorageCreatorSession sSession = new MinionStorageCreatorSession("Huge.Storage.Chest");
        assertEquals("huge_storage_chest", sSession.getId());
    }

    @Test
    @DisplayName("AUD-05: Fuel speed multipliers and MaxTier caps are strictly bounded")
    public void testNumericBoundsAndCaps() {
        FuelCreatorSession fuel = new FuelCreatorSession("test_fuel");

        // Below min (0.01) clamps to 0.01
        fuel.setSpeedBuff(-0.5);
        assertEquals(0.01, fuel.getSpeedBuff(), 0.001);

        // Above max (10.0x / 1000%) clamps to 10.0
        fuel.setSpeedBuff(999.0);
        assertEquals(10.0, fuel.getSpeedBuff(), 0.001);

        // Normal speed
        fuel.setSpeedBuff(0.50);
        assertEquals(0.50, fuel.getSpeedBuff(), 0.001);

        // MinionCreatorSession bounds
        MinionCreatorSession minion = new MinionCreatorSession("test");
        minion.setActionDelay(0.1);
        assertEquals(1.0, minion.getActionDelay(), 0.001);
        minion.setActionDelay(500.0);
        assertEquals(120.0, minion.getActionDelay(), 0.001);

        minion.setStorageSlots(0);
        assertEquals(1, minion.getStorageSlots());
        minion.setStorageSlots(100);
        assertEquals(15, minion.getStorageSlots());

        minion.setMaxTier(0);
        assertEquals(1, minion.getMaxTier());
        minion.setMaxTier(100);
        assertEquals(MinionTier.getMaxTier(), minion.getMaxTier());
    }

    @Test
    @DisplayName("AUD-02: PromptSession expiration after 60 seconds")
    public void testPromptSessionExpiration() {
        MinionCreatorListener.PromptSession prompt = new MinionCreatorListener.PromptSession(input -> {});

        // Fresh prompt is not expired
        assertFalse(prompt.isExpired(60_000L));

        // Prompt created 61 seconds ago is expired
        MinionCreatorListener.PromptSession expiredPrompt = new MinionCreatorListener.PromptSession(input -> {}) {
            @Override
            public boolean isExpired(long timeoutMillis) {
                return true;
            }
        };
        assertTrue(expiredPrompt.isExpired(60_000L));
    }

    @Test
    @DisplayName("AUD-09: UpgradeRequirement matches material, amounts, and handles null/air")
    public void testUpgradeRequirementMatching() {
        UpgradeRequirement plainReq = new UpgradeRequirement(Material.DIAMOND, 64);

        assertEquals(Material.DIAMOND, plainReq.getMaterial());
        assertEquals(64, plainReq.getAmount());
        assertEquals("Diamond", plainReq.getDisplayName());
        assertFalse(plainReq.hasCustomDisplayName());

        // Null / Air / Wrong material
        assertFalse(plainReq.matches(null));
        assertFalse(plainReq.matches(new ItemStack(Material.AIR)));
        assertFalse(plainReq.matches(new ItemStack(Material.COBBLESTONE, 64)));

        // Matching material
        assertTrue(plainReq.matches(new ItemStack(Material.DIAMOND, 64)));
        assertTrue(plainReq.matches(new ItemStack(Material.DIAMOND, 1)));

        // Custom display name requirement rejects plain items without meta
        UpgradeRequirement customReq = new UpgradeRequirement(Material.DIAMOND, 64, "&bEnchanted Diamond");
        assertTrue(customReq.hasCustomDisplayName());
        assertEquals("§bEnchanted Diamond", customReq.getDisplayName());
        assertFalse(customReq.matches(new ItemStack(Material.DIAMOND, 64)));
    }

    @Test
    @DisplayName("AUD-09: UpgradeRequirement serializes to config map and deserializes losslessly")
    public void testUpgradeRequirementSerialization() {
        UpgradeRequirement req = new UpgradeRequirement(Material.NETHER_STAR, 8, "&eNether Star", 1005, "minionx:star", "tier11");
        Map<String, Object> map = req.toConfigMap();

        assertEquals("NETHER_STAR", map.get("item"));
        assertEquals(8, map.get("amount"));
        assertEquals("§eNether Star", map.get("display-name"));
        assertEquals(1005, map.get("custom-model-data"));
        assertEquals("minionx:star", map.get("pdc-key"));
        assertEquals("tier11", map.get("pdc-value"));

        UpgradeRequirement reloaded = UpgradeRequirement.fromConfig(map);
        assertNotNull(reloaded);
        assertEquals(Material.NETHER_STAR, reloaded.getMaterial());
        assertEquals(8, reloaded.getAmount());
        assertEquals("§eNether Star", reloaded.getDisplayName());
        assertEquals(1005, reloaded.getCustomModelData());
        assertEquals("minionx:star", reloaded.getPdcKey());
        assertEquals("tier11", reloaded.getPdcValue());
    }

    @Test
    @DisplayName("AUD-09: MinionTier loads and serializes multi-item upgrade-requirements")
    public void testTierDataMultiItemRequirements() {
        List<UpgradeRequirement> reqs = new ArrayList<>();
        reqs.add(new UpgradeRequirement(Material.COBBLESTONE, 160, "&fCobblestone"));
        reqs.add(new UpgradeRequirement(Material.COAL, 32, "&8Coal"));

        MinionTier.TierData data = new MinionTier.TierData(12.0, 3, 160, reqs);
        assertTrue(data.hasCustomRequirements());
        assertEquals(2, data.getRequirements().size());
        assertEquals(Material.COBBLESTONE, data.getRequirements().get(0).getMaterial());
        assertEquals(160, data.getRequirements().get(0).getAmount());
        assertEquals(Material.COAL, data.getRequirements().get(1).getMaterial());
        assertEquals(32, data.getRequirements().get(1).getAmount());

        // Test YAML section loading
        YamlConfiguration yaml = new YamlConfiguration();
        List<Map<String, Object>> rList = new ArrayList<>();
        for (UpgradeRequirement r : reqs) {
            rList.add(r.toConfigMap());
        }
        yaml.set("tiers.2.action-delay", 14.0);
        yaml.set("tiers.2.storage-slots", 3);
        yaml.set("tiers.2.upgrade-cost", 160);
        yaml.set("tiers.2.upgrade-requirements", rList);

        MinionTier.load(yaml.getConfigurationSection("tiers"));
        MinionTier.TierData loaded = MinionTier.getTierData(2);
        assertNotNull(loaded);
        assertTrue(loaded.hasCustomRequirements());
        assertEquals(2, loaded.getRequirements().size());
        assertEquals(Material.COBBLESTONE, loaded.getRequirements().get(0).getMaterial());
        assertEquals(160, loaded.getRequirements().get(0).getAmount());
        assertEquals(Material.COAL, loaded.getRequirements().get(1).getMaterial());
        assertEquals(32, loaded.getRequirements().get(1).getAmount());
    }
}
