package com.skyblock.minions.core;

import com.skyblock.minions.api.MinionState;
import com.skyblock.minions.api.MinionUpgradeType;
import com.skyblock.minions.visual.MinionAnimation;
import com.skyblock.minions.visual.MinionHologram;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Global repeating task executing the autonomous action cycle for all active minions.
 * Fills the 5x5 working radius before beginning resource mining.
 */
public class MinionTickTask extends BukkitRunnable {

    private final MinionManager minionManager;

    public MinionTickTask(MinionManager minionManager) {
        this.minionManager = minionManager;
    }

    @Override
    public void run() {
        for (Minion minion : minionManager.getAllMinions()) {
            tickMinion(minion);
        }
    }

    private void tickMinion(Minion minion) {
        if (!minion.getLocation().isChunkLoaded()) {
            return;
        }

        // Check fuel expiration
        if (minion.getFuelId() != null && !minion.isFuelActive()) {
            minion.setFuel((com.skyblock.minions.api.CustomFuel) null);
            MinionHologram.updateHologram(minion);
        }

        // Check action cooldown
        long now = System.currentTimeMillis();
        long delayMillis = (long) (minion.getEffectiveActionDelay() * 1000L);
        if (now - minion.getLastActionTime() < delayMillis) {
            return;
        }

        // Check if inventory has room
        Material dropMaterial = determineDropMaterial(minion);
        ItemStack expectedDrop = new ItemStack(dropMaterial, 1);
        if (!minion.canAccept(expectedDrop)) {
            if (minion.getState() != MinionState.FULL) {
                minion.setState(MinionState.FULL);
                MinionHologram.updateHologram(minion);
            }
            return;
        }

        List<Block> workingBlocks = minion.getWorkingAreaBlocks();
        List<Block> resourceBlocks = new ArrayList<>();
        List<Block> emptySpots = new ArrayList<>();

        for (Block block : workingBlocks) {
            Material type = block.getType();
            if (type == minion.getType().getTargetMaterial() || type == minion.getType().getPlacedMaterial()) {
                resourceBlocks.add(block);
            } else if (type.isAir() || !type.isSolid()) {
                emptySpots.add(block);
            }
        }

        // Action Decision: Fill the entire 5x5 working area first before mining!
        if (!emptySpots.isEmpty()) {
            // Priority 1: Fill any available empty spots with resource blocks
            Collections.shuffle(emptySpots);
            Block target = emptySpots.getFirst();
            executePlace(minion, target);
        } else if (!resourceBlocks.isEmpty()) {
            // Priority 2: When 5x5 area is full (or in generator layout), harvest resources!
            Collections.shuffle(resourceBlocks);
            Block target = resourceBlocks.getFirst();
            executeMine(minion, target, dropMaterial);
        } else {
            setNoSpaceState(minion);
        }
    }

    private void executePlace(Minion minion, Block target) {
        target.setType(minion.getType().getPlacedMaterial());
        MinionAnimation.playPlaceAnimation(minion, target);
        minion.setLastActionWasPlace(true);
        minion.setLastActionTime(System.currentTimeMillis());
        minion.setState(MinionState.IDLE);
        MinionHologram.updateHologram(minion);
    }

    private void executeMine(Minion minion, Block target, Material dropMaterial) {
        MinionAnimation.playMineAnimation(minion, target);
        target.setType(Material.AIR);

        ItemStack drop = new ItemStack(dropMaterial, 1);
        minion.addItem(drop);
        minion.incrementHarvested(1);

        // Diamond Spreading perk
        if (minion.hasUpgrade(MinionUpgradeType.DIAMOND_SPREADING) && Math.random() < 0.10) {
            minion.addItem(new ItemStack(Material.DIAMOND, 1));
        }

        minion.setLastActionWasPlace(false);
        minion.setLastActionTime(System.currentTimeMillis());
        minion.setState(MinionState.IDLE);
        MinionHologram.updateHologram(minion);
    }

    private void setNoSpaceState(Minion minion) {
        if (minion.getState() != MinionState.NO_SPACE) {
            minion.setState(MinionState.NO_SPACE);
            MinionHologram.updateHologram(minion);
        }
    }

    private Material determineDropMaterial(Minion minion) {
        Material drop = minion.getType().getDropMaterial();
        if (minion.hasUpgrade(MinionUpgradeType.AUTO_SMELTER)) {
            if (drop == Material.COBBLESTONE) return Material.STONE;
            if (drop == Material.RAW_IRON) return Material.IRON_INGOT;
        }
        return drop;
    }
}