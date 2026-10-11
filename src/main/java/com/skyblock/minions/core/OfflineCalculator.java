package com.skyblock.minions.core;

import com.skyblock.minions.SkyBlockMinions;
import com.skyblock.minions.api.*;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Ultra-optimized O(1) constant-time offline production engine.
 * Computes exact item yields, Super Compaction batches, and XP in single-step
 * integer arithmetic without tick/simulation loops, eliminating server lag.
 */
public class OfflineCalculator {

    public static int processOfflineCatchup(Minion minion) {
        if (minion == null) return 0;

        long now = System.currentTimeMillis();
        long elapsedMillis = now - minion.getLastActionTime();
        if (elapsedMillis <= 0) return 0;

        // Cap offline calculation to configured max hours (default 720 hours = 30 days)
        long maxOfflineMillis = 720L * 3600L * 1000L;
        if (SkyBlockMinions.getInstance() != null && SkyBlockMinions.getInstance().getConfig() != null) {
            int cfgHours = SkyBlockMinions.getInstance().getConfig().getInt("settings.max-offline-hours", 720);
            if (cfgHours > 0) {
                maxOfflineMillis = (long) cfgHours * 3600L * 1000L;
            }
        }

        if (elapsedMillis > maxOfflineMillis) {
            elapsedMillis = maxOfflineMillis;
        }

        double delaySeconds = minion.getEffectiveActionDelay();
        if (delaySeconds <= 0) delaySeconds = 14.0;
        long cycleMillis = (long) (delaySeconds * 2000L); // Two-action cycle (Place + Mine)

        long totalCycles = elapsedMillis / cycleMillis;
        long leftoverMillis = elapsedMillis % cycleMillis;
        minion.setLastActionTime(now - leftoverMillis);

        if (totalCycles <= 0) return 0;

        // Determine drop item and XP
        MinionDefinition def = minion.getCustomDefinitionId() != null
                ? MinionRegistry.get(minion.getCustomDefinitionId())
                : (minion.getType() != null ? MinionRegistry.get(minion.getType().name().toLowerCase()) : null);

        ItemStack baseDrop = determineDropItem(minion, def);
        int xpPerHarvest = def != null && def.getXpPerHarvest() > 0 ? def.getXpPerHarvest() : 1;

        // Calculate available capacity in unlocked storage slots
        int unlockedSlots = MinionTier.getStorageSlots(minion.getTier());
        int maxCapacity = unlockedSlots * 64;
        int currentStored = minion.getTotalStoredCount();
        int spaceRemaining = Math.max(0, maxCapacity - currentStored);

        if (spaceRemaining <= 0) {
            minion.setState(MinionState.FULL);
            return 0;
        }

        // Check Super Compactor
        boolean hasSuperCompactor = minion.hasUpgrade(MinionUpgradeType.SUPER_COMPACTOR)
                || minion.hasUpgradeBehavior(CustomUpgrade.UpgradeBehavior.COMPACTOR);

        int itemsToProduce = (int) Math.min((long) totalCycles, (long) spaceRemaining);

        if (hasSuperCompactor) {
            CompressionManager.CompressionRecipe recipe = CompressionManager.getRecipe(baseDrop.getType());
            if (recipe != null && recipe.getInputAmount() > 0) {
                int req = recipe.getInputAmount();
                // O(1) compression arithmetic
                int totalRaw = currentStored + (int) totalCycles;
                int compressedYield = totalRaw / req;
                int rawRemainder = totalRaw % req;

                // Re-batch minion inventory in O(1)
                minion.clearInventory();
                int itemsAdded = 0;

                // Add compressed stacks directly
                if (compressedYield > 0) {
                    ItemStack compItem = recipe.createOutput();
                    int fullStacks = compressedYield / 64;
                    int compRem = compressedYield % 64;
                    for (int s = 0; s < fullStacks && minion.getInventory().size() < unlockedSlots; s++) {
                        ItemStack stack = compItem.clone();
                        stack.setAmount(64);
                        minion.addItemDirect(stack);
                        itemsAdded += 64;
                    }
                    if (compRem > 0 && minion.getInventory().size() < unlockedSlots) {
                        ItemStack stack = compItem.clone();
                        stack.setAmount(compRem);
                        minion.addItemDirect(stack);
                        itemsAdded += compRem;
                    }
                }

                // Add remaining raw stacks
                if (rawRemainder > 0 && minion.getInventory().size() < unlockedSlots) {
                    ItemStack rawStack = baseDrop.clone();
                    rawStack.setAmount(Math.min(64, rawRemainder));
                    minion.addItemDirect(rawStack);
                    itemsAdded += rawStack.getAmount();
                }

                minion.incrementHarvested((int) totalCycles);
                minion.addStoredXp((int) totalCycles * xpPerHarvest);
                return itemsAdded;
            }
        }

        // Standard batch allocation in O(1)
        int actualAdded = batchAddItems(minion, baseDrop, itemsToProduce, unlockedSlots);

        minion.incrementHarvested(actualAdded);
        minion.addStoredXp(actualAdded * xpPerHarvest);

        // Secondary drop in O(1) (Apples, Saplings, Seeds, Leather, etc.)
        if (def != null && def.hasSecondaryDrop() && def.getSecondaryDropChance() > 0 && def.getSecondaryDropItem() != null) {
            int secCount = (int) (actualAdded * def.getSecondaryDropChance());
            if (secCount > 0) {
                batchAddItems(minion, def.getSecondaryDropItem(), secCount, unlockedSlots);
            }
        }

        // Diamond Spreading 10% perk in O(1)
        boolean hasSpreading = minion.hasUpgrade(MinionUpgradeType.DIAMOND_SPREADING)
                || minion.hasUpgradeBehavior(CustomUpgrade.UpgradeBehavior.DIAMOND_SPREADING);
        if (hasSpreading && actualAdded >= 10) {
            int diamonds = actualAdded / 10;
            batchAddItems(minion, new ItemStack(Material.DIAMOND), diamonds, unlockedSlots);
        }

        if (minion.getTotalStoredCount() >= maxCapacity) {
            minion.setState(MinionState.FULL);
        }

        return actualAdded;
    }

    private static int batchAddItems(Minion minion, ItemStack baseItem, int count, int maxSlots) {
        if (count <= 0) return 0;
        int remaining = count;
        List<ItemStack> inventory = minion.getInventory();

        // 1. Fill existing partial stacks of the same material
        for (ItemStack slotItem : inventory) {
            if (slotItem.isSimilar(baseItem) && slotItem.getAmount() < 64) {
                int canAdd = Math.min(64 - slotItem.getAmount(), remaining);
                slotItem.setAmount(slotItem.getAmount() + canAdd);
                remaining -= canAdd;
                if (remaining <= 0) break;
            }
        }

        // 2. Fill empty slots with remaining items in full 64-stacks
        while (remaining > 0 && inventory.size() < maxSlots) {
            int stackAmount = Math.min(64, remaining);
            ItemStack newStack = baseItem.clone();
            newStack.setAmount(stackAmount);
            minion.addItemDirect(newStack);
            remaining -= stackAmount;
        }

        return count - remaining;
    }

    private static ItemStack determineDropItem(Minion minion, MinionDefinition def) {
        ItemStack drop = def != null ? def.getExactDropItem() : null;
        if (drop == null) {
            Material mat = minion.getType() != null ? minion.getType().getDropMaterial() : Material.COBBLESTONE;
            drop = new ItemStack(mat, 1);
        }

        boolean hasSmelter = minion.hasUpgrade(MinionUpgradeType.AUTO_SMELTER)
                || minion.hasUpgradeBehavior(CustomUpgrade.UpgradeBehavior.AUTO_SMELTER);
        if (hasSmelter) {
            if (drop.getType() == Material.COBBLESTONE) return new ItemStack(Material.STONE, drop.getAmount());
            if (drop.getType() == Material.RAW_IRON) return new ItemStack(Material.IRON_INGOT, drop.getAmount());
        }
        return drop;
    }
}
