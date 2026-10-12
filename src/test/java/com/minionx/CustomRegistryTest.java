package com.minionx;

import com.minionx.api.*;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class CustomRegistryTest {

    @Test
    @DisplayName("Should parse fuel durations correctly")
    void testDurationParsing() {
        assertEquals(30 * 60 * 1000L, FuelRegistry.parseDuration("30m"));
        assertEquals(2 * 3600 * 1000L, FuelRegistry.parseDuration("2h"));
        assertEquals(86400 * 1000L, FuelRegistry.parseDuration("1d"));
        assertEquals(45 * 1000L, FuelRegistry.parseDuration("45s"));
        assertEquals(-1L, FuelRegistry.parseDuration("-1"));
        assertEquals(-1L, FuelRegistry.parseDuration("permanent"));
        assertEquals(-1L, FuelRegistry.parseDuration("infinite"));
    }

    @Test
    @DisplayName("Should load custom fuels from YAML configuration")
    void testLoadCustomFuels() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("fuels.super_fuel.display-name", "§dSuper Fuel");
        config.set("fuels.super_fuel.material", "BLAZE_POWDER");
        config.set("fuels.super_fuel.speed-buff", 0.50);
        config.set("fuels.super_fuel.duration", "2h");
        config.set("fuels.super_fuel.lore", List.of("§7Incredible +50% speed boost."));
        config.set("fuels.super_fuel.glow", true);

        FuelRegistry.load(config.getConfigurationSection("fuels"));

        CustomFuel fuel = FuelRegistry.get("super_fuel");
        assertNotNull(fuel);
        assertEquals("super_fuel", fuel.getId());
        assertEquals("§dSuper Fuel", fuel.getDisplayName());
        assertEquals(Material.BLAZE_POWDER, fuel.getMaterial());
        assertEquals(0.50, fuel.getSpeedBuff(), 0.001);
        assertEquals(2 * 3600 * 1000L, fuel.getDurationMillis());
        assertTrue(fuel.isGlowing());

        // Baseline defaults still present
        assertNotNull(FuelRegistry.get("enchanted_lava_bucket"));
    }

    @Test
    @DisplayName("Should load custom upgrades from YAML configuration")
    void testLoadCustomUpgrades() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("upgrades.hyper_catalyst.display-name", "§5Hyper Catalyst");
        config.set("upgrades.hyper_catalyst.material", "NETHER_STAR");
        config.set("upgrades.hyper_catalyst.type", "SPEED_BUFF");
        config.set("upgrades.hyper_catalyst.speed-buff", 0.40);
        config.set("upgrades.hyper_catalyst.lore", List.of("§7Multiplies minion efficiency."));
        config.set("upgrades.hyper_catalyst.glow", true);

        UpgradeRegistry.load(config.getConfigurationSection("upgrades"));

        CustomUpgrade upgrade = UpgradeRegistry.get("hyper_catalyst");
        assertNotNull(upgrade);
        assertEquals("hyper_catalyst", upgrade.getId());
        assertEquals("§5Hyper Catalyst", upgrade.getDisplayName());
        assertEquals(Material.NETHER_STAR, upgrade.getMaterial());
        assertEquals(CustomUpgrade.UpgradeBehavior.SPEED_BUFF, upgrade.getBehavior());
        assertEquals(0.40, upgrade.getSpeedBuff(), 0.001);
        assertTrue(upgrade.isGlowing());

        // Baseline defaults still present
        assertNotNull(UpgradeRegistry.get("auto_smelter"));
        assertNotNull(UpgradeRegistry.get("compactor"));
    }
}
