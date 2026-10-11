package com.skyblock.minions.core;

import com.skyblock.minions.api.*;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Deterministically simulates minion production while chunks are unloaded or player was offline.
 */
public class OfflineCalculator {

    /**
     * Processes elapsed offline time and populates the minion's storage accordingly.
     * @return Number of items added during catch-up.
     */
    public static int processOfflineCatchup(Minion minion) {
        if (minion == null) return 0;

        long now = System.currentTimeMillis();
        long elapsedMillis = now - minion.getLastActionTime();
        if (elapsedMillis <= 0) return 0;

        double delaySeconds = minion.getEffectiveActionDelay();
        if (delaySeconds <= 0) delaySeconds = 14.0;
        long delayMillis = (long) (delaySeconds * 1000L);

        // In Hypixel's 2-action cycle, 2 actions produce 1 resource
        long totalActions = elapsedMillis / delayMillis;
        long itemsToProduce = totalActions / 2L;

        // Reset the action timer to account for leftover progress
        long leftoverMillis = elapsedMillis % (delayMillis * 2L);
        minion.setLastActionTime(now - leftoverMillis);

        if (itemsToProduce <= 0) return 0;

        Material dropMaterial = determineDropMaterial(minion);
        int addedCount = 0;

        for (int i = 0; i < itemsToProduce; i++) {
            ItemStack drop = new ItemStack(dropMaterial, 1);

            // Apply Compactor if equipped
            if (minion.hasUpgrade(MinionUpgradeType.COMPACTOR)) {
                drop = tryCompact(dropMaterial, minion);
            }

            if (minion.addItem(drop)) {
                addedCount++;
                minion.incrementHarvested(1);

                // Diamond spreading: 10% chance
                if (minion.hasUpgrade(MinionUpgradeType.DIAMOND_SPREADING) && (i % 10 == 0)) {
                    minion.addItem(new ItemStack(Material.DIAMOND, 1));
                }
            } else {
                minion.setState(MinionState.FULL);
                break;
            }
        }

        return addedCount;
    }

    private static Material determineDropMaterial(Minion minion) {
        Material baseDrop = minion.getType().getDropMaterial();

        // Auto-smelter conversion
        if (minion.hasUpgrade(MinionUpgradeType.AUTO_SMELTER)) {
            if (baseDrop == Material.COBBLESTONE) {
                return Material.STONE;
            } else if (baseDrop == Material.RAW_IRON) {
                return Material.IRON_INGOT;
            }
        }

        return baseDrop;
    }

    private static ItemStack tryCompact(Material material, Minion minion) {
        if (material == Material.IRON_INGOT) {
            // If already has 9 iron ingots, could compact into block
            return new ItemStack(material, 1);
        }
        return new ItemStack(material, 1);
    }
}
