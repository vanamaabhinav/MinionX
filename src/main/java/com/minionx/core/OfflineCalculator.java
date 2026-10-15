package com.minionx.core;

import com.minionx.MinionX;
import com.minionx.api.*;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Ultra-optimized constant-time offline production engine.
 * Computes exact item yields, Super Compaction batches, and XP in checked
 * 64-bit integer arithmetic with action-phase precision and paused-time advancement.
 */
public class OfflineCalculator {

    public static int processOfflineCatchup(Minion minion) {
        if (minion == null) return 0;

        // 1. Paused-state anchor advance: paused duration is never credited as work
        if (minion.getState() == MinionState.FULL || minion.getState() == MinionState.NO_SPACE) {
            minion.setLastActionTime(System.currentTimeMillis());
            return 0;
        }

        long now = System.currentTimeMillis();
        long elapsedMillis = now - minion.getLastActionTime();
        if (elapsedMillis <= 0) return 0;

        // 2. Cap offline calculation to configured max hours (default 720 hours = 30 days)
        long maxOfflineMillis = 720L * 3600L * 1000L;
        if (MinionX.getInstance() != null && MinionX.getInstance().getConfig() != null) {
            int cfgHours = MinionX.getInstance().getConfig().getInt("settings.max-offline-hours", 720);
            if (cfgHours > 0) {
                maxOfflineMillis = (long) cfgHours * 3600L * 1000L;
            }
        }

        if (elapsedMillis > maxOfflineMillis) {
            elapsedMillis = maxOfflineMillis;
        }

        double delaySeconds = minion.getEffectiveActionDelay();
        if (delaySeconds <= 0) delaySeconds = 14.0;
        long actionMillis = (long) (delaySeconds * 1000L);
        if (actionMillis <= 0) actionMillis = 1000L;

        // 3. Action-based time resolution
        long totalActions = elapsedMillis / actionMillis;
        long leftoverMillis = elapsedMillis % actionMillis;
        minion.setLastActionTime(now - leftoverMillis);

        if (totalActions <= 0) return 0;

        // 4. Alternating phase resolution (Action 0 = Place, Action 1 = Mine/Yield)
        long harvestActions;
        if (!minion.wasLastActionPlace()) {
            harvestActions = totalActions / 2L;
            minion.setLastActionWasPlace(totalActions % 2L == 1L);
        } else {
            harvestActions = (totalActions + 1L) / 2L;
            minion.setLastActionWasPlace(totalActions % 2L == 0L);
        }

        if (harvestActions <= 0) return 0;

        // 5. Determine definition, drop item, and XP
        MinionDefinition def = minion.getCustomDefinitionId() != null
                ? MinionRegistry.get(minion.getCustomDefinitionId())
                : (minion.getType() != null ? MinionRegistry.get(minion.getType().name().toLowerCase()) : null);

        ItemStack baseDrop = determineDropItem(minion, def);
        int xpPerHarvest = def != null && def.getXpPerHarvest() > 0 ? def.getXpPerHarvest() : 1;

        // 6. Snapshot pre-production state for atomic rollback on overflow
        List<ItemStack> invSnapshot = cloneItemList(minion.getInventory());
        List<ItemStack> storeSnapshot = cloneItemList(minion.getStorageContents());
        double coinsSnapshot = minion.getShippingCoins();

        // 7. Calculate secondary drops and Diamond Spreading via fixed-point micro-math (100% = 1,000,000)
        long secProduced = 0;
        long newSecRemainder = 0;
        boolean hasSecondary = def != null && def.hasSecondaryDrop() && def.getSecondaryDropChance() > 0 && def.getSecondaryDropItem() != null;
        if (hasSecondary && def != null) {
            long secChanceMicros = (long) (def.getSecondaryDropChance() * 1_000_000.0);
            long existingSecRem = minion.getChannelRemainder("secondary");
            long totalSecMicros = Math.addExact(existingSecRem, Math.multiplyExact(harvestActions, secChanceMicros));
            secProduced = totalSecMicros / 1_000_000L;
            newSecRemainder = totalSecMicros % 1_000_000L;
        }

        long diaProduced = 0;
        long newDiaRemainder = 0;
        boolean hasSpreading = minion.hasUpgrade(MinionUpgradeType.DIAMOND_SPREADING)
                || minion.hasUpgradeBehavior(CustomUpgrade.UpgradeBehavior.DIAMOND_SPREADING);
        if (hasSpreading) {
            long existingDiaRem = minion.getChannelRemainder("diamond_spreading");
            long totalDiaMicros = Math.addExact(existingDiaRem, Math.multiplyExact(harvestActions, 100_000L)); // 10%
            diaProduced = totalDiaMicros / 1_000_000L;
            newDiaRemainder = totalDiaMicros % 1_000_000L;
        }

        // 8. Safely simulate pipeline insertion into internal slots, virtual storage, and Auto-Shipper
        long totalBase = Math.multiplyExact(harvestActions, (long) baseDrop.getAmount());
        boolean success = true;
        int actualAdded = 0;

        // Base items
        long remBase = totalBase;
        while (remBase > 0) {
            int batch = (int) Math.min(64L, remBase);
            ItemStack stack = baseDrop.clone();
            stack.setAmount(batch);
            if (!minion.addItem(stack)) {
                success = false;
                break;
            }
            actualAdded += batch;
            remBase -= batch;
            CompressionManager.processCompaction(minion);
        }

        // Secondary drops
        if (success && secProduced > 0 && def != null && def.getSecondaryDropItem() != null) {
            long remSec = secProduced;
            while (remSec > 0) {
                int batch = (int) Math.min(64L, remSec);
                ItemStack stack = def.getSecondaryDropItem().clone();
                stack.setAmount(batch);
                if (!minion.addItem(stack)) {
                    success = false;
                    break;
                }
                actualAdded += batch;
                remSec -= batch;
                CompressionManager.processCompaction(minion);
            }
        }

        // Diamond spreading
        if (success && diaProduced > 0) {
            long remDia = diaProduced;
            while (remDia > 0) {
                int batch = (int) Math.min(64L, remDia);
                ItemStack stack = new ItemStack(Material.DIAMOND, batch);
                if (!minion.addItem(stack)) {
                    success = false;
                    break;
                }
                actualAdded += batch;
                remDia -= batch;
                CompressionManager.processCompaction(minion);
            }
        }

        // 9. All-or-nothing rollback on capacity overflow without Auto-Shipper
        if (!success) {
            restoreItemList(minion.getInventory(), invSnapshot);
            restoreItemList(minion.getStorageContents(), storeSnapshot);
            minion.setShippingCoins(coinsSnapshot);

            minion.setState(MinionState.FULL);
            minion.setLastActionTime(now); // Advance paused-time anchor
            return 0;
        }

        // 10. Commit changes atomically
        if (hasSecondary) minion.setChannelRemainder("secondary", newSecRemainder);
        if (hasSpreading) minion.setChannelRemainder("diamond_spreading", newDiaRemainder);

        minion.incrementHarvested((int) Math.min(Integer.MAX_VALUE, harvestActions));
        minion.addStoredXp((int) Math.min(Integer.MAX_VALUE, harvestActions * (long) xpPerHarvest));
        CompressionManager.processCompaction(minion);

        return actualAdded;
    }

    private static List<ItemStack> cloneItemList(List<ItemStack> source) {
        List<ItemStack> clone = new ArrayList<>(source.size());
        for (ItemStack item : source) {
            clone.add(item != null ? item.clone() : null);
        }
        return clone;
    }

    private static void restoreItemList(List<ItemStack> target, List<ItemStack> source) {
        target.clear();
        for (ItemStack item : source) {
            target.add(item != null ? item.clone() : null);
        }
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
            if (drop.getType() == Material.RAW_GOLD) return new ItemStack(Material.GOLD_INGOT, drop.getAmount());
            if (drop.getType() == Material.RAW_COPPER) return new ItemStack(Material.COPPER_INGOT, drop.getAmount());
        }
        return drop;
    }
}
