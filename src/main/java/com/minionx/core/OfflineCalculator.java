package com.minionx.core;

import com.minionx.MinionX;
import com.minionx.api.*;
import com.minionx.api.BonusDropEntry;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    private OfflineCalculator() {}

    public static int processOfflineCatchup(Minion minion) {
        if (minion == null) return 0;
        if (!com.minionx.util.FeatureToggle.isOfflineCatchupEnabled()) {
            minion.setLastActionTime(System.currentTimeMillis());
            return 0;
        }

        // 1. Paused-state anchor advance: paused duration is never credited as work
        if (minion.getState() == MinionState.FULL || minion.getState() == MinionState.NO_SPACE || minion.getState() == MinionState.NO_MOBS_IN_RANGE) {
            minion.setLastActionTime(System.currentTimeMillis());
            return 0;
        }

        long now = System.currentTimeMillis();
        long lastAction = minion.getLastActionTime();
        long elapsedMillis = now - lastAction;
        if (elapsedMillis <= 0) return 0;

        // 2. Cap offline calculation to configured max hours (default 168 hours = 7 days, -1 = unlimited, 0 = disabled)
        long maxOfflineMillis = 168L * 3600L * 1000L;
        boolean unlimited = false;
        if (minion.getOwnerId() != null && org.bukkit.Bukkit.getServer() != null) {
            try {
                org.bukkit.entity.Player owner = org.bukkit.Bukkit.getPlayer(minion.getOwnerId());
                if (owner != null && owner.hasPermission("minionx.offline.unlimited")) {
                    unlimited = true;
                }
            } catch (Throwable ignored) {}
        }
        if (!unlimited && MinionX.getInstance() != null && MinionX.getInstance().getConfig() != null) {
            int cfgHours = MinionX.getInstance().getConfig().getInt("settings.max-offline-hours", 168);
            if (cfgHours == -1) {
                unlimited = true;
            } else if (cfgHours == 0) {
                return 0; // Disabled
            } else if (cfgHours > 0) {
                maxOfflineMillis = (long) cfgHours * 3600L * 1000L;
            }
        }

        if (!unlimited && elapsedMillis > maxOfflineMillis) {
            lastAction = now - maxOfflineMillis;
            minion.setLastActionTime(lastAction);
            elapsedMillis = maxOfflineMillis;
        }

        // 3. Segment downtime across fuel expiration boundaries to apply appropriate speed multipliers
        long fuelEnd = minion.getFuelEndTime();
        if (fuelEnd > 0 && fuelEnd > lastAction && fuelEnd < now && minion.getFuelId() != null) {
            // Segment 1: from lastAction to fuelEnd with fuel speed boost
            int added1 = simulateSegment(minion, lastAction, fuelEnd, true);
            if (minion.getState() == MinionState.FULL) {
                minion.setLastActionTime(now);
                return added1;
            }
            // Deactivate fuel at boundary
            minion.setFuel((CustomFuel) null);
            // Segment 2: from fuelEnd to now without fuel
            int added2 = simulateSegment(minion, fuelEnd, now, false);
            return added1 + added2;
        } else {
            if (fuelEnd > 0 && fuelEnd <= lastAction && minion.getFuelId() != null) {
                minion.setFuel((CustomFuel) null);
            }
            return simulateSegment(minion, lastAction, now, minion.isFuelActive());
        }
    }

    private static int simulateSegment(Minion minion, long segStart, long segEnd, boolean fuelActive) {
        if (segEnd <= segStart) return 0;
        long elapsedMillis = segEnd - segStart;

        double delaySeconds = getSegmentDelay(minion, fuelActive);
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

        List<BonusDropEntry> activeBonusDrops = new ArrayList<>();
        if (com.minionx.util.FeatureToggle.isUpgradesEnabled()) {
            CustomUpgrade u1 = minion.getCustomUpgrade1();
            if (u1 != null && u1.isSpreading()) {
                activeBonusDrops.addAll(u1.getSpreadingItems());
            }
            CustomUpgrade u2 = minion.getCustomUpgrade2();
            if (u2 != null && u2.isSpreading()) {
                activeBonusDrops.addAll(u2.getSpreadingItems());
            }
        }

        long curSecRem = minion.getChannelRemainder("secondary");
        Map<BonusDropEntry, Long> bonusRemainders = new LinkedHashMap<>();
        for (BonusDropEntry b : activeBonusDrops) {
            String chKey = getBonusChannelKey(b);
            bonusRemainders.put(b, minion.getChannelRemainder(chKey));
        }

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

            Map<BonusDropEntry, Long> nextBonusRemainders = new LinkedHashMap<>();
            for (BonusDropEntry b : activeBonusDrops) {
                long curRem = bonusRemainders.getOrDefault(b, 0L);
                long nextRem = curRem + (long) (b.getChance() * 1_000_000.0);
                long bCount = nextRem / 1_000_000L;
                nextRem = nextRem % 1_000_000L;
                if (bCount > 0) {
                    ItemStack bStack = b.createItemStack();
                    bStack.setAmount((int) Math.min(64, bCount * b.getAmount()));
                    cycleDrops.add(bStack);
                }
                nextBonusRemainders.put(b, nextRem);
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
            bonusRemainders.putAll(nextBonusRemainders);
            successfulHarvests++;
            actualAdded += baseStack.getAmount();

            if (com.minionx.util.FeatureToggle.isUpgradesEnabled()) {
                CompressionManager.processCompaction(minion);
            }
        }

        if (hasSecondary) minion.setChannelRemainder("secondary", curSecRem);
        for (Map.Entry<BonusDropEntry, Long> entry : bonusRemainders.entrySet()) {
            minion.setChannelRemainder(getBonusChannelKey(entry.getKey()), entry.getValue());
        }

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

    private static double getSegmentDelay(Minion minion, boolean fuelActive) {
        double base = minion.getBaseActionDelay();
        double totalBuff = 0.0;
        if (com.minionx.util.FeatureToggle.isFuelsEnabled() && fuelActive) {
            CustomFuel f = minion.getCustomFuel();
            if (f != null && f.getSpeedBuff() > 0) {
                totalBuff += f.getSpeedBuff();
            }
        }
        if (com.minionx.util.FeatureToggle.isUpgradesEnabled()) {
            CustomUpgrade u1 = minion.getCustomUpgrade1();
            if (u1 != null && u1.getSpeedBuff() > 0) {
                totalBuff += u1.getSpeedBuff();
            }
            CustomUpgrade u2 = minion.getCustomUpgrade2();
            if (u2 != null && u2.getSpeedBuff() > 0) {
                totalBuff += u2.getSpeedBuff();
            }
        }
        return base / (1.0 + totalBuff);
    }

    private static String getBonusChannelKey(BonusDropEntry b) {
        if (b.getMaterial() == Material.DIAMOND && Math.abs(b.getChance() - 0.10) < 1e-4) {
            return "diamond_spreading";
        }
        return "bonus_" + b.getMaterial().name().toLowerCase() + "_" + (int) Math.round(b.getChance() * 1000);
    }
}
