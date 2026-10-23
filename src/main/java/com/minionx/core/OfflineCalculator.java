package com.minionx.core;

import com.minionx.MinionX;
import com.minionx.api.*;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Offline production engine.
 * Computes item yields, Super Compaction batches, and XP in checked
 * 64-bit integer arithmetic with cycle-by-cycle precision and paused-time advancement.
 * Invariants:
 * - Downtime is segmented across fuel expiration boundaries.
 * - Each cycle executes as an all-or-nothing accounting unit.
 * - Inventory/storage saturation stops simulation without dropping or deleting unpriced items.
 */
public class OfflineCalculator {

    public static int processOfflineCatchup(Minion minion) {
        if (minion == null) return 0;

        // 1. Paused-state anchor advance: paused duration is never credited as work
        if (minion.getState() == MinionState.FULL || minion.getState() == MinionState.NO_SPACE || minion.getState() == MinionState.NO_MOBS_IN_RANGE) {
            minion.setLastActionTime(System.currentTimeMillis());
            return 0;
        }

        long now = System.currentTimeMillis();
        long lastAction = minion.getLastActionTime();
        long elapsedMillis = now - lastAction;
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
            lastAction = now - maxOfflineMillis;
            minion.setLastActionTime(lastAction);
            elapsedMillis = maxOfflineMillis;
        }

        // 3. Segment downtime across fuel expiration boundaries to apply appropriate speed multipliers
        long fuelEnd = minion.getFuelEndTime();
        if (fuelEnd > 0 && fuelEnd > lastAction && fuelEnd < now && minion.isFuelActive()) {
            // Segment 1: from lastAction to fuelEnd with fuel speed boost
            int added1 = simulateSegment(minion, lastAction, fuelEnd);
            if (minion.getState() == MinionState.FULL) {
                minion.setLastActionTime(now);
                return added1;
            }
            // Deactivate fuel at boundary
            minion.setFuel((CustomFuel) null);
            // Segment 2: from fuelEnd to now without fuel
            int added2 = simulateSegment(minion, fuelEnd, now);
            return added1 + added2;
        } else {
            if (fuelEnd > 0 && fuelEnd <= lastAction && minion.isFuelActive()) {
                minion.setFuel((CustomFuel) null);
            }
            return simulateSegment(minion, lastAction, now);
        }
    }

    private static int simulateSegment(Minion minion, long segStart, long segEnd) {
        if (segEnd <= segStart) return 0;
        long elapsedMillis = segEnd - segStart;

        double delaySeconds = minion.getEffectiveActionDelay();
        if (delaySeconds <= 0) delaySeconds = 14.0;
        long actionMillis = (long) (delaySeconds * 1000L);
        if (actionMillis <= 0) actionMillis = 1000L;

        long totalActions = elapsedMillis / actionMillis;
        long leftoverMillis = elapsedMillis % actionMillis;
        minion.setLastActionTime(segEnd - leftoverMillis);

        if (totalActions <= 0) return 0;

        // Alternating phase resolution (Action 0 = Place, Action 1 = Mine/Yield)
        long harvestActions;
        if (!minion.wasLastActionPlace()) {
            harvestActions = totalActions / 2L;
            minion.setLastActionWasPlace(totalActions % 2L == 1L);
        } else {
            harvestActions = (totalActions + 1L) / 2L;
            minion.setLastActionWasPlace(totalActions % 2L == 0L);
        }

        if (harvestActions <= 0) return 0;

        MinionDefinition def = minion.getCustomDefinitionId() != null
                ? MinionRegistry.get(minion.getCustomDefinitionId())
                : (minion.getType() != null ? MinionRegistry.get(minion.getType().name().toLowerCase()) : null);

        ItemStack baseDrop = determineDropItem(minion, def);
        int xpPerHarvest = def != null && def.getXpPerHarvest() > 0 ? def.getXpPerHarvest() : 1;

        boolean hasSecondary = def != null && def.hasSecondaryDrop() && def.getSecondaryDropChance() > 0 && def.getSecondaryDropItem() != null;
        long secChanceMicros = (def != null && hasSecondary) ? (long) (def.getSecondaryDropChance() * 1_000_000.0) : 0L;

        boolean hasSpreading = minion.hasUpgrade(MinionUpgradeType.DIAMOND_SPREADING)
                || minion.hasUpgradeBehavior(CustomUpgrade.UpgradeBehavior.DIAMOND_SPREADING);

        long curSecRem = minion.getChannelRemainder("secondary");
        long curDiaRem = minion.getChannelRemainder("diamond_spreading");

        long successfulHarvests = 0;
        int actualAdded = 0;

        for (long cycle = 0; cycle < harvestActions; cycle++) {
            // Snapshot working lists for this individual cycle (all-or-nothing accounting unit)
            List<ItemStack> invCycleSnap = cloneItemList(minion.getInventory());
            List<ItemStack> storeCycleSnap = cloneItemList(minion.getStorageContents());
            double coinsCycleSnap = minion.getShippingCoins();

            List<ItemStack> cycleDrops = new ArrayList<>();
            ItemStack baseStack = baseDrop.clone();
            cycleDrops.add(baseStack);

            long nextSecRem = curSecRem + secChanceMicros;
            long secDropCount = nextSecRem / 1_000_000L;
            nextSecRem = nextSecRem % 1_000_000L;
            if (secDropCount > 0 && def != null && def.getSecondaryDropItem() != null) {
                ItemStack secStack = def.getSecondaryDropItem().clone();
                secStack.setAmount((int) secDropCount);
                cycleDrops.add(secStack);
            }

            long nextDiaRem = curDiaRem + (hasSpreading ? 100_000L : 0L);
            long diaDropCount = nextDiaRem / 1_000_000L;
            nextDiaRem = nextDiaRem % 1_000_000L;
            if (diaDropCount > 0) {
                cycleDrops.add(new ItemStack(Material.DIAMOND, (int) diaDropCount));
            }

            // Test adding all drops for this cycle
            boolean cycleFit = true;
            for (ItemStack drop : cycleDrops) {
                if (!minion.addItem(drop)) {
                    cycleFit = false;
                    break;
                }
            }

            if (!cycleFit) {
                // Roll back this cycle's partial additions
                restoreItemList(minion.getInventory(), invCycleSnap);
                restoreItemList(minion.getStorageContents(), storeCycleSnap);
                minion.setShippingCoins(coinsCycleSnap);

                // Saturation State: Stop simulation immediately, mark FULL
                minion.setState(MinionState.FULL);
                minion.setLastActionTime(segEnd); // Advance paused-time anchor
                break;
            }

            // Cycle succeeded! Commit working remainders
            curSecRem = nextSecRem;
            curDiaRem = nextDiaRem;
            successfulHarvests++;
            actualAdded += baseStack.getAmount();

            CompressionManager.processCompaction(minion);
        }

        if (hasSecondary) minion.setChannelRemainder("secondary", curSecRem);
        if (hasSpreading) minion.setChannelRemainder("diamond_spreading", curDiaRem);

        minion.incrementHarvested((int) Math.min(Integer.MAX_VALUE, successfulHarvests));
        minion.addStoredXp((int) Math.min(Integer.MAX_VALUE, successfulHarvests * (long) xpPerHarvest));
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
