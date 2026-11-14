package com.minionx;

import com.minionx.api.*;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verification test suite for MinionX public Developer API (MinionXAPI).
 */
public class DeveloperApiTest {

    @Test
    @DisplayName("MinionXAPI: Standalone queries return safe defaults without Bukkit server")
    void testQueryMethodsWithoutServer() {
        UUID testId = UUID.randomUUID();

        assertNull(MinionXAPI.getMinion(null));
        assertNull(MinionXAPI.getMinion(testId));
        assertNull(MinionXAPI.getMinionAt(null));

        assertNotNull(MinionXAPI.getMinionsByOwner(testId));
        assertTrue(MinionXAPI.getMinionsByOwner(testId).isEmpty());

        assertNotNull(MinionXAPI.getAllMinions());
        assertTrue(MinionXAPI.getAllMinions().isEmpty());

        assertEquals(0, MinionXAPI.getPlacedCount(testId));
        assertEquals(-1, MinionXAPI.getEffectiveLimit(null));
        assertEquals(-1, MinionXAPI.getAvailableSlots(null));

        assertFalse(MinionXAPI.isQuarantined());
    }

    @Test
    @DisplayName("MinionXAPI: Item creation and identification")
    void testItemCreationAndIdentification() {
        assertNull(MinionXAPI.createMinionItem((String) null, 1));
        assertNull(MinionXAPI.createMinionItem((MinionType) null, 1));
        assertNull(MinionXAPI.createMinionItem("non_existent_id_xyz", 1));

        // Without Bukkit server initialized, createMinionItem gracefully returns null
        assertNull(MinionXAPI.createMinionItem(MinionType.COBBLESTONE, 1));
        assertNull(MinionXAPI.createMinionItem("diamond", 5));

        ItemStack vanillaItem = new ItemStack(Material.DIAMOND, 1);
        assertFalse(MinionXAPI.isMinionItem(vanillaItem));
        assertFalse(MinionXAPI.isMinionItem(null));

        // giveMinion with null player
        assertFalse(MinionXAPI.giveMinion(null, "cobblestone", 1));
        assertFalse(MinionXAPI.giveMinion(null, null, 1));
    }

    @Test
    @DisplayName("MinionXAPI: Dynamic registration of fuels, upgrades, storages, and skins")
    void testDynamicRegistrations() {
        assertDoesNotThrow(() -> {
            CustomFuel fuel = new CustomFuel("api_test_fuel", "API Test Fuel", Material.COAL, 1.25, 3600000L, java.util.Collections.emptyList(), false, 0);
            MinionXAPI.registerCustomFuel(fuel);
            assertNotNull(FuelRegistry.get("api_test_fuel"));

            CustomUpgrade upgrade = new CustomUpgrade("api_test_upgrade", "API Test Upgrade", Material.IRON_BLOCK, CustomUpgrade.UpgradeBehavior.SPEED_BUFF, 0.2, 0.0, java.util.Collections.emptyList(), false, 0);
            MinionXAPI.registerCustomUpgrade(upgrade);
            assertNotNull(UpgradeRegistry.get("api_test_upgrade"));

            CustomMinionStorage storage = new CustomMinionStorage("api_test_storage", "API Test Storage", 9, Material.CHEST, java.util.Collections.emptyList());
            MinionXAPI.registerCustomStorage(storage);
            assertNotNull(MinionStorageRegistry.get("api_test_storage"));

            MinionSkin skin = new MinionSkin("api_test_skin", "API Test Skin", "eyJ0ZXh0dXJlcyI6e319");
            MinionXAPI.registerCustomSkin(skin);
            assertNotNull(MinionSkinRegistry.get("api_test_skin"));
        });
    }

    @Test
    @DisplayName("Plugin Manifest: Verify all internal permission nodes are explicitly declared")
    void testPluginYmlPermissionsDeclared() {
        java.io.InputStream in = getClass().getClassLoader().getResourceAsStream("plugin.yml");
        assertNotNull(in, "plugin.yml resource should exist on classpath");

        org.yaml.snakeyaml.Yaml yaml = new org.yaml.snakeyaml.Yaml();
        java.util.Map<String, Object> data = yaml.load(in);
        assertNotNull(data);

        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> perms = (java.util.Map<String, Object>) data.get("permissions");
        assertNotNull(perms, "permissions section must be present in plugin.yml");

        String[] requiredPerms = {
            "minionx.admin",
            "minionx.use",
            "minionx.place",
            "minionx.storage",
            "minionx.bypass.limit",
            "minionx.bypass.protection",
            "minionx.offline.unlimited",
            "minionx.admin.bypass"
        };

        for (String perm : requiredPerms) {
            assertTrue(perms.containsKey(perm), "plugin.yml missing required permission node: " + perm);
        }
    }
}
