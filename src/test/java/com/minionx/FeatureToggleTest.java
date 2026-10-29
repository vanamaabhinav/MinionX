package com.minionx;

import com.minionx.api.FuelRegistry;
import com.minionx.api.MinionSkinRegistry;
import com.minionx.api.MinionStorageRegistry;
import com.minionx.api.UpgradeRegistry;
import com.minionx.util.FeatureToggle;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class FeatureToggleTest {

    @BeforeEach
    @AfterEach
    void resetToggles() {
        FeatureToggle.reload(null);
        FuelRegistry.load(null);
        UpgradeRegistry.load(null);
        MinionSkinRegistry.load(null);
        MinionStorageRegistry.load();
    }

    @AfterAll
    static void cleanup() {
        FeatureToggle.reload(null);
        FuelRegistry.load(null);
        UpgradeRegistry.load(null);
        MinionSkinRegistry.load(null);
        MinionStorageRegistry.load();
    }

    @Test
    @DisplayName("Default configuration should enable all features and set nametags to ALWAYS")
    void testDefaults() {
        assertTrue(FeatureToggle.isAnimationsEnabled());
        assertTrue(FeatureToggle.isSoundsEnabled());
        assertTrue(FeatureToggle.isParticlesEnabled());
        assertTrue(FeatureToggle.isGeyserTouchFriendly());
        assertTrue(FeatureToggle.isDialoguesEnabled());
        assertTrue(FeatureToggle.isOfflineCatchupEnabled());
        assertTrue(FeatureToggle.isFuelsEnabled());
        assertTrue(FeatureToggle.isUpgradesEnabled());
        assertTrue(FeatureToggle.isShippingEnabled());
        assertTrue(FeatureToggle.isSkinsEnabled());
        assertTrue(FeatureToggle.isStorageChestsEnabled());
        assertFalse(FeatureToggle.isCustomStorageLayoutsEnabled());
        assertEquals("ALWAYS", FeatureToggle.getNametagMode());
    }

    @Test
    @DisplayName("Feature toggles should respect disabled settings from YamlConfiguration")
    void testDisabledSubsystems() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("features.fuels", false);
        config.set("features.upgrades", false);
        config.set("features.automated-shipping", false);
        config.set("features.skins", false);
        config.set("features.storage-chests", false);
        config.set("features.offline-catchup", false);
        config.set("settings.enable-animations", false);
        config.set("settings.enable-sounds", false);
        config.set("settings.enable-particles", false);
        config.set("settings.geyser-touch-friendly", false);
        config.set("dialogues.enabled", false);
        config.set("storage-layouts.use-custom-layouts", true);
        config.set("nametags.mode", "TARGETED");

        FeatureToggle.reload(config);

        assertFalse(FeatureToggle.isFuelsEnabled());
        assertFalse(FeatureToggle.isUpgradesEnabled());
        assertFalse(FeatureToggle.isShippingEnabled());
        assertFalse(FeatureToggle.isSkinsEnabled());
        assertFalse(FeatureToggle.isStorageChestsEnabled());
        assertFalse(FeatureToggle.isOfflineCatchupEnabled());
        assertFalse(FeatureToggle.isAnimationsEnabled());
        assertFalse(FeatureToggle.isSoundsEnabled());
        assertFalse(FeatureToggle.isParticlesEnabled());
        assertFalse(FeatureToggle.isGeyserTouchFriendly());
        assertFalse(FeatureToggle.isDialoguesEnabled());
        assertTrue(FeatureToggle.isCustomStorageLayoutsEnabled());
        assertEquals("TARGETED", FeatureToggle.getNametagMode());
    }

    @Test
    @DisplayName("max-offline-hours: 0 should disable offline catchup even if feature is true")
    void testMaxOfflineHoursZero() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("features.offline-catchup", true);
        config.set("settings.max-offline-hours", 0);

        FeatureToggle.reload(config);

        assertFalse(FeatureToggle.isOfflineCatchupEnabled(), "Offline catchup should be disabled when max-offline-hours is 0");
    }

    @Test
    @DisplayName("Nametag modes: TARGETED, DISABLED, and fallback to ALWAYS")
    void testNametagModes() {
        YamlConfiguration config = new YamlConfiguration();

        config.set("nametags.mode", "TARGETED");
        FeatureToggle.reload(config);
        assertEquals("TARGETED", FeatureToggle.getNametagMode());

        config.set("nametags.mode", "DISABLED");
        FeatureToggle.reload(config);
        assertEquals("DISABLED", FeatureToggle.getNametagMode());

        config.set("nametags.mode", "INVALID_UNKNOWN_MODE");
        FeatureToggle.reload(config);
        assertEquals("ALWAYS", FeatureToggle.getNametagMode());
    }

    @Test
    @DisplayName("When subsystems are disabled, their registries should remain completely empty (Feature Never Existed)")
    void testSubsystemRegistriesEmptyWhenDisabled() {
        YamlConfiguration disabledConfig = new YamlConfiguration();
        disabledConfig.set("features.fuels", false);
        disabledConfig.set("features.upgrades", false);
        disabledConfig.set("features.skins", false);
        disabledConfig.set("features.storage-chests", false);
        FeatureToggle.reload(disabledConfig);

        FuelRegistry.load(null);
        assertTrue(FuelRegistry.getAll().isEmpty(), "FuelRegistry should be completely empty when fuels are disabled");

        UpgradeRegistry.load(null);
        assertTrue(UpgradeRegistry.getAll().isEmpty(), "UpgradeRegistry should be completely empty when upgrades are disabled");

        MinionSkinRegistry.load(null);
        assertTrue(MinionSkinRegistry.getAll().isEmpty(), "MinionSkinRegistry should be completely empty when skins are disabled");

        MinionStorageRegistry.load();
        assertTrue(MinionStorageRegistry.getAll().isEmpty(), "MinionStorageRegistry should be completely empty when storage chests are disabled");
    }
}
