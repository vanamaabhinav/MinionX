package com.skyblock.minions.core;

import com.skyblock.minions.SkyBlockMinions;
import com.skyblock.minions.api.*;
import com.skyblock.minions.hook.MythicMobsHook;
import com.skyblock.minions.hook.SkillHook;
import com.skyblock.minions.visual.MinionAnimation;
import com.skyblock.minions.visual.MinionHologram;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Global repeating task executing the autonomous action cycle for all active minions.
 * Supports MINER (flexible 5x5 natural block, generator & placed harvesting)
 * and COMBAT (vanilla & MythicMobs spawning and harvesting with Super Compaction).
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
            minion.setFuel((CustomFuel) null);
            MinionHologram.updateHologram(minion);
        }

        // Check action cooldown
        long now = System.currentTimeMillis();
        long delayMillis = (long) (minion.getEffectiveActionDelay() * 1000L);
        if (now - minion.getLastActionTime() < delayMillis) {
            return;
        }

        MinionDefinition def = minion.getCustomDefinitionId() != null
                ? MinionRegistry.get(minion.getCustomDefinitionId())
                : (minion.getType() != null ? MinionRegistry.get(minion.getType().name().toLowerCase()) : null);

        // Fallback default miner definition if null
        if (def == null) {
            def = MinionRegistry.get("cobblestone");
        }

        ItemStack dropItem = determineDropItem(minion, def);
        if (!minion.canAccept(dropItem)) {
            if (minion.getState() != MinionState.FULL) {
                minion.setState(MinionState.FULL);
                MinionHologram.updateHologram(minion);
            }
            return;
        }

        if (def.getArchetype() == MinionArchetype.COMBAT) {
            tickCombatMinion(minion, def, dropItem);
        } else {
            tickMinerMinion(minion, def, dropItem);
        }

        // Automatic Super Compactor processing
        CompressionManager.processSuperCompaction(minion);
    }

    private void tickMinerMinion(Minion minion, MinionDefinition def, ItemStack dropItem) {
        List<Block> workingBlocks = minion.getWorkingAreaBlocks();
        List<Block> resourceBlocks = new ArrayList<>();
        List<Block> emptySpots = new ArrayList<>();
        int foreignObstructions = 0;

        Material targetBlock = def.getTargetBlock();

        for (Block block : workingBlocks) {
            Material type = block.getType();
            if (isMatchingTargetBlock(type, targetBlock, minion)) {
                resourceBlocks.add(block);
            } else if (type.isAir() || !type.isSolid()) {
                emptySpots.add(block);
            } else {
                // Foreign block placed by player (chest, torch, fence, generator frame)
                foreignObstructions++;
            }
        }

        // Determine if partially obstructed
        boolean isPartiallyObstructed = foreignObstructions > 0 && (!emptySpots.isEmpty() || !resourceBlocks.isEmpty());

        // Hypixel Two-Action Cycle with generator & natural block harvesting:
        // If last action was Place -> prioritize Mining any existing designated target block!
        if (minion.wasLastActionPlace()) {
            if (!resourceBlocks.isEmpty()) {
                updateObstructionState(minion, isPartiallyObstructed);
                Collections.shuffle(resourceBlocks);
                Block target = resourceBlocks.getFirst();
                executeMine(minion, target, dropItem, def.getXpPerHarvest());
            } else if (!emptySpots.isEmpty()) {
                // Target block wasn't there (maybe broken by player), so place if space exists
                updateObstructionState(minion, isPartiallyObstructed);
                Collections.shuffle(emptySpots);
                Block target = emptySpots.getFirst();
                executePlace(minion, target, targetBlock);
            } else {
                setNoSpaceState(minion);
            }
        } else {
            // Last action was Mine (or initial cycle)
            if (!emptySpots.isEmpty()) {
                // Standard generation: place designated block on an open spot
                updateObstructionState(minion, isPartiallyObstructed);
                Collections.shuffle(emptySpots);
                Block target = emptySpots.getFirst();
                executePlace(minion, target, targetBlock);
            } else if (!resourceBlocks.isEmpty()) {
                // No open spots (all spots occupied by generator or player-placed ores) -> mine directly!
                updateObstructionState(minion, isPartiallyObstructed);
                Collections.shuffle(resourceBlocks);
                Block target = resourceBlocks.getFirst();
                executeMine(minion, target, dropItem, def.getXpPerHarvest());
            } else {
                // No open spots and no resource blocks
                setNoSpaceState(minion);
            }
        }
    }

    private boolean isMatchingTargetBlock(Material type, Material targetBlock, Minion minion) {
        if (type == targetBlock) return true;
        if (minion.getType() != null) {
            if (type == minion.getType().getTargetMaterial() || type == minion.getType().getPlacedMaterial()) {
                return true;
            }
            // Cobblestone generator tolerance (Stone or Cobblestone)
            if (minion.getType() == MinionType.COBBLESTONE && (type == Material.COBBLESTONE || type == Material.STONE)) {
                return true;
            }
            // Deepslate ore variants
            if (targetBlock == Material.DIAMOND_ORE && type == Material.DEEPSLATE_DIAMOND_ORE) return true;
            if (targetBlock == Material.IRON_ORE && type == Material.DEEPSLATE_IRON_ORE) return true;
            if (targetBlock == Material.GOLD_ORE && type == Material.DEEPSLATE_GOLD_ORE) return true;
            if (targetBlock == Material.COAL_ORE && type == Material.DEEPSLATE_COAL_ORE) return true;
            if (targetBlock == Material.EMERALD_ORE && type == Material.DEEPSLATE_EMERALD_ORE) return true;
            if (targetBlock == Material.REDSTONE_ORE && type == Material.DEEPSLATE_REDSTONE_ORE) return true;
            if (targetBlock == Material.LAPIS_ORE && type == Material.DEEPSLATE_LAPIS_ORE) return true;
            if (targetBlock == Material.COPPER_ORE && type == Material.DEEPSLATE_COPPER_ORE) return true;
        }
        return false;
    }

    private void updateObstructionState(Minion minion, boolean partiallyObstructed) {
        if (partiallyObstructed) {
            minion.setState(MinionState.PARTIALLY_OBSTRUCTED);
        } else {
            minion.setState(MinionState.IDLE);
        }
    }

    private void tickCombatMinion(Minion minion, MinionDefinition def, ItemStack dropItem) {
        Location loc = minion.getLocation();
        if (loc.getWorld() == null) return;

        int cap = SkyBlockMinions.getInstance().getConfig().getInt("settings.combat.max-mobs-per-minion", 5);
        boolean disableAi = SkyBlockMinions.getInstance().getConfig().getBoolean("settings.combat.disable-mob-wandering", true);

        // Find existing nearby spawned mobs
        List<Entity> taggedMobs = new ArrayList<>();
        for (Entity e : loc.getWorld().getNearbyEntities(loc, 3.5, 2.0, 3.5)) {
            if (MythicMobsHook.isMinionSpawnedMob(e) && e.isValid() && !e.isDead()) {
                taggedMobs.add(e);
            }
        }

        if (taggedMobs.size() < cap && !minion.wasLastActionPlace()) {
            // Spawn mob in 5x5 working radius
            List<Block> working = minion.getWorkingAreaBlocks();
            List<Location> validLocs = new ArrayList<>();
            for (Block b : working) {
                if (b.getType().isAir() || !b.getType().isSolid()) {
                    validLocs.add(b.getLocation().add(0.5, 0, 0.5));
                }
            }
            if (!validLocs.isEmpty()) {
                Collections.shuffle(validLocs);
                Location spawnLoc = validLocs.getFirst();
                MythicMobsHook.spawnMob(def.getTargetMob(), spawnLoc, disableAi);
                MinionAnimation.playPlaceAnimation(minion, spawnLoc.getBlock());
                minion.setLastActionWasPlace(true);
                minion.setLastActionTime(System.currentTimeMillis());
                minion.setState(MinionState.IDLE);
                MinionHologram.updateHologram(minion);
                return;
            }
        }

        if (!taggedMobs.isEmpty()) {
            // Slay / Harvest 1 mob
            Entity target = taggedMobs.getFirst();
            MinionAnimation.playMineAnimation(minion, target.getLocation().getBlock());
            target.remove();

            minion.addItem(dropItem.clone());
            minion.incrementHarvested(1);
            int xp = def.getXpPerHarvest() > 0 ? def.getXpPerHarvest() : 5;
            minion.addStoredXp(xp);

            minion.setLastActionWasPlace(false);
            minion.setLastActionTime(System.currentTimeMillis());
            minion.setState(MinionState.IDLE);
            MinionHologram.updateHologram(minion);
        } else {
            setNoSpaceState(minion);
        }
    }

    private void executePlace(Minion minion, Block target, Material placeMat) {
        target.setType(placeMat);
        MinionAnimation.playPlaceAnimation(minion, target);
        minion.setLastActionWasPlace(true);
        minion.setLastActionTime(System.currentTimeMillis());
        MinionHologram.updateHologram(minion);
    }

    private void executeMine(Minion minion, Block target, ItemStack dropItem, int xp) {
        MinionAnimation.playMineAnimation(minion, target);
        target.setType(Material.AIR);

        minion.addItem(dropItem.clone());
        minion.incrementHarvested(1);
        minion.addStoredXp(xp);

        // Diamond Spreading perk
        boolean hasSpreading = minion.hasUpgrade(MinionUpgradeType.DIAMOND_SPREADING)
                || minion.hasUpgradeBehavior(CustomUpgrade.UpgradeBehavior.DIAMOND_SPREADING);
        if (hasSpreading && Math.random() < 0.10) {
            minion.addItem(new ItemStack(Material.DIAMOND, 1));
        }

        minion.setLastActionWasPlace(false);
        minion.setLastActionTime(System.currentTimeMillis());
        MinionHologram.updateHologram(minion);
    }

    private void setNoSpaceState(Minion minion) {
        if (minion.getState() != MinionState.NO_SPACE) {
            minion.setState(MinionState.NO_SPACE);
            MinionHologram.updateHologram(minion);
        }
    }

    private ItemStack determineDropItem(Minion minion, MinionDefinition def) {
        ItemStack drop = def.getExactDropItem();
        boolean hasSmelter = minion.hasUpgrade(MinionUpgradeType.AUTO_SMELTER)
                || minion.hasUpgradeBehavior(CustomUpgrade.UpgradeBehavior.AUTO_SMELTER);
        if (hasSmelter) {
            if (drop.getType() == Material.COBBLESTONE) return new ItemStack(Material.STONE, drop.getAmount());
            if (drop.getType() == Material.RAW_IRON) return new ItemStack(Material.IRON_INGOT, drop.getAmount());
        }
        return drop;
    }
}
