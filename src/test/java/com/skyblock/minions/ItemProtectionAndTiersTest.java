package com.skyblock.minions;

import com.skyblock.minions.api.MinionTier;
import com.skyblock.minions.gui.OperatorHolder;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ItemProtectionAndTiersTest {

    @Test
    @DisplayName("Should dynamically load tier speeds and storage capacities from YAML config")
    void testDynamicTiers() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("tiers.1.action-delay", 15.0);
        config.set("tiers.1.storage-slots", 2);
        config.set("tiers.1.upgrade-cost", 100);

        config.set("tiers.2.action-delay", 12.0);
        config.set("tiers.2.storage-slots", 5);
        config.set("tiers.2.upgrade-cost", 250);

        config.set("tiers.3.action-delay", 9.0);
        config.set("tiers.3.storage-slots", 8);
        config.set("tiers.3.upgrade-cost", 500);

        MinionTier.load(config.getConfigurationSection("tiers"));

        assertEquals(3, MinionTier.getMaxTier());
        assertEquals(15.0, MinionTier.getActionDelay(1));
        assertEquals(2, MinionTier.getStorageSlots(1));
        assertEquals(100, MinionTier.getUpgradeCost(1));

        assertEquals(12.0, MinionTier.getActionDelay(2));
        assertEquals(5, MinionTier.getStorageSlots(2));

        assertEquals(9.0, MinionTier.getActionDelay(3));
        assertEquals(8, MinionTier.getStorageSlots(3));
        assertEquals(8 * 64, MinionTier.getMaxCapacity(3));

        // Reset to default
        MinionTier.load(null);
        assertEquals(11, MinionTier.getMaxTier());
    }

    @Test
    @DisplayName("Should verify all OperatorHolder view types")
    void testOperatorHolderCategories() {
        for (OperatorHolder.ViewType viewType : OperatorHolder.ViewType.values()) {
            OperatorHolder holder = new OperatorHolder(viewType);
            assertEquals(viewType, holder.getViewType());
        }
    }
}