package com.skyblock.minions.core;

import com.skyblock.minions.SkyBlockMinions;
import com.skyblock.minions.api.*;
import com.skyblock.minions.hook.MythicMobsHook;
import com.skyblock.minions.visual.MinionAnimation;
import com.skyblock.minions.visual.MinionHologram;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Global repeating task executing the autonomous action cycle for all active minions.
 * Supports MINER, COMBAT, FORAGING (trees & chopping), and FARMING (crops, stems, canes, cacti, mushrooms).
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

        switch (def.getArchetype()) {
            case COMBAT -> tickCombatMinion(minion, def, dropItem);
            case FORAGING -> tickForagingMinion(minion, def, dropItem);
            case FARMING -> tickFarmingMinion(minion, def, dropItem);
            default -> tickMinerMinion(minion, def, dropItem);
        }

        // Automatic Super Compactor processing
        CompressionManager.processSuperCompaction(minion);
    }

    // =========================================================================
    // 1. MINER MINION
    // =========================================================================
    private void tickMinerMinion(Minion minion, MinionDefinition def, ItemStack dropItem) {
        List<Block> workingBlocks = minion.getWorkingGroundBlocks();
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
                foreignObstructions++;
            }
        }

        boolean isPartiallyObstructed = foreignObstructions > 0 && (!emptySpots.isEmpty() || !resourceBlocks.isEmpty());

        if (minion.wasLastActionPlace()) {
            if (!resourceBlocks.isEmpty()) {
                updateObstructionState(minion, isPartiallyObstructed);
                Collections.shuffle(resourceBlocks);
                Block target = resourceBlocks.getFirst();
                executeMine(minion, target, dropItem, def.getXpPerHarvest());
            } else if (!emptySpots.isEmpty()) {
                updateObstructionState(minion, isPartiallyObstructed);
                Collections.shuffle(emptySpots);
                Block target = emptySpots.getFirst();
                executePlace(minion, target, targetBlock);
            } else {
                setNoSpaceState(minion);
            }
        } else {
            if (!emptySpots.isEmpty()) {
                updateObstructionState(minion, isPartiallyObstructed);
                Collections.shuffle(emptySpots);
                Block target = emptySpots.getFirst();
                executePlace(minion, target, targetBlock);
            } else if (!resourceBlocks.isEmpty()) {
                updateObstructionState(minion, isPartiallyObstructed);
                Collections.shuffle(resourceBlocks);
                Block target = resourceBlocks.getFirst();
                executeMine(minion, target, dropItem, def.getXpPerHarvest());
            } else {
                setNoSpaceState(minion);
            }
        }
    }

    // =========================================================================
    // 2. FORAGING MINION (Saplings -> Basic Trees -> Chopping)
    // =========================================================================
    private void tickForagingMinion(Minion minion, MinionDefinition def, ItemStack dropItem) {
        List<Block> cropSpots = minion.getWorkingCropBlocks(); // at Y
        Material targetLog = def.getTargetBlock() != null ? def.getTargetBlock() : Material.OAK_LOG;
        Material saplingMat = def.getSaplingMaterial();
        Material leafMat = def.getLeafMaterial();

        List<Block> saplingBlocks = new ArrayList<>();
        List<Block> logBlocks = new ArrayList<>();
        List<Block> plantableSpots = new ArrayList<>();

        for (Block block : cropSpots) {
            Material mat = block.getType();
            Block ground = block.getRelative(BlockFace.DOWN); // Y - 1

            if (mat == saplingMat) {
                saplingBlocks.add(block);
            } else if (isWoodLog(mat, targetLog)) {
                logBlocks.add(block);
                for (int yOffset = 1; yOffset <= 2; yOffset++) {
                    Block higher = block.getRelative(0, yOffset, 0);
                    if (isWoodLog(higher.getType(), targetLog)) {
                        logBlocks.add(higher);
                    }
                }
            } else if (mat.isAir() || !mat.isSolid()) {
                if (isSoil(ground.getType())) {
                    plantableSpots.add(block);
                }
            }
        }

        if (minion.wasLastActionPlace()) {
            if (!logBlocks.isEmpty()) {
                Collections.shuffle(logBlocks);
                Block target = logBlocks.getFirst();
                executeChop(minion, target, dropItem, def, leafMat);
            } else if (!saplingBlocks.isEmpty()) {
                Collections.shuffle(saplingBlocks);
                Block target = saplingBlocks.getFirst();
                growTree(minion, target, targetLog, leafMat);
            } else if (!plantableSpots.isEmpty()) {
                Collections.shuffle(plantableSpots);
                Block target = plantableSpots.getFirst();
                executePlantSapling(minion, target, saplingMat);
            } else {
                setNoSpaceState(minion);
            }
        } else {
            if (!plantableSpots.isEmpty() && (saplingBlocks.size() + logBlocks.size()) < 5) {
                Collections.shuffle(plantableSpots);
                Block target = plantableSpots.getFirst();
                executePlantSapling(minion, target, saplingMat);
            } else if (!saplingBlocks.isEmpty()) {
                Collections.shuffle(saplingBlocks);
                Block target = saplingBlocks.getFirst();
                growTree(minion, target, targetLog, leafMat);
            } else if (!logBlocks.isEmpty()) {
                Collections.shuffle(logBlocks);
                Block target = logBlocks.getFirst();
                executeChop(minion, target, dropItem, def, leafMat);
            } else {
                setNoSpaceState(minion);
            }
        }
    }

    private void executePlantSapling(Minion minion, Block target, Material saplingMat) {
        target.setType(saplingMat);
        MinionAnimation.playPlaceAnimation(minion, target);
        minion.setLastActionWasPlace(true);
        minion.setLastActionTime(System.currentTimeMillis());
        minion.setState(MinionState.IDLE);
        MinionHologram.updateHologram(minion);
    }

    private void growTree(Minion minion, Block base, Material logMat, Material leafMat) {
        base.setType(logMat);
        Block trunk2 = base.getRelative(0, 1, 0);
        Block trunk3 = base.getRelative(0, 2, 0);

        if (trunk2.getType().isAir()) trunk2.setType(logMat);
        if (trunk3.getType().isAir()) trunk3.setType(logMat);

        Block top = base.getRelative(0, 3, 0);
        if (top.getType().isAir()) top.setType(leafMat);

        for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            Block leaf = trunk3.getRelative(face);
            if (leaf.getType().isAir()) leaf.setType(leafMat);
        }

        base.getWorld().spawnParticle(Particle.VILLAGER_HAPPY, base.getLocation().add(0.5, 1.5, 0.5), 10, 0.4, 0.6, 0.4, 0.1);
        base.getWorld().playSound(base.getLocation(), Sound.BLOCK_GRASS_PLACE, 1.0f, 1.2f);

        minion.setLastActionWasPlace(false);
        minion.setLastActionTime(System.currentTimeMillis());
        minion.setState(MinionState.IDLE);
        MinionHologram.updateHologram(minion);
    }

    private void executeChop(Minion minion, Block target, ItemStack dropItem, MinionDefinition def, Material leafMat) {
        MinionAnimation.playMineAnimation(minion, target);
        target.setType(Material.AIR);

        for (BlockFace face : new BlockFace[]{BlockFace.UP, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            Block relative = target.getRelative(face);
            if (relative.getType() == leafMat) {
                relative.setType(Material.AIR);
            }
        }

        minion.addItem(dropItem.clone());
        minion.incrementHarvested(1);
        minion.addStoredXp(def.getXpPerHarvest() > 0 ? def.getXpPerHarvest() : 1);

        if (def.hasSecondaryDrop() && Math.random() < def.getSecondaryDropChance()) {
            minion.addItem(def.getSecondaryDropItem().clone());
        }

        minion.setLastActionWasPlace(false);
        minion.setLastActionTime(System.currentTimeMillis());
        minion.setState(MinionState.IDLE);
        MinionHologram.updateHologram(minion);
    }

    // =========================================================================
    // 3. FARMING MINION (Crops, Stems, Sugar Cane, Cactus, Mushrooms)
    // =========================================================================
    private void tickFarmingMinion(Minion minion, MinionDefinition def, ItemStack dropItem) {
        Material targetMat = def.getTargetBlock();
        if (targetMat == null) targetMat = Material.WHEAT;

        if (isStemCrop(targetMat)) {
            tickStemCropMinion(minion, def, dropItem, targetMat);
        } else if (targetMat == Material.SUGAR_CANE) {
            tickSugarCaneMinion(minion, def, dropItem);
        } else if (targetMat == Material.CACTUS) {
            tickCactusMinion(minion, def, dropItem);
        } else if (isMushroom(targetMat)) {
            tickMushroomMinion(minion, def, dropItem, targetMat);
        } else {
            tickSoilCropMinion(minion, def, dropItem, targetMat);
        }
    }

    private void tickSoilCropMinion(Minion minion, MinionDefinition def, ItemStack dropItem, Material cropMat) {
        List<Block> cropSpots = minion.getWorkingCropBlocks(); // at Y
        List<Block> matureCrops = new ArrayList<>();
        List<Block> growingCrops = new ArrayList<>();
        List<Block> plantableSpots = new ArrayList<>();

        boolean isNetherWart = cropMat == Material.NETHER_WART;

        for (Block cropBlock : cropSpots) {
            Block ground = cropBlock.getRelative(BlockFace.DOWN); // Y - 1
            Material groundType = ground.getType();

            if (!isNetherWart && (groundType == Material.DIRT || groundType == Material.GRASS_BLOCK)) {
                ground.setType(Material.FARMLAND);
                groundType = Material.FARMLAND;
            }

            boolean validSoil = isNetherWart ? groundType == Material.SOUL_SAND : groundType == Material.FARMLAND;
            if (!validSoil) continue;

            Material mat = cropBlock.getType();
            if (mat == cropMat) {
                if (cropBlock.getBlockData() instanceof Ageable ageable) {
                    if (ageable.getAge() >= ageable.getMaximumAge()) {
                        matureCrops.add(cropBlock);
                    } else {
                        growingCrops.add(cropBlock);
                    }
                } else {
                    matureCrops.add(cropBlock);
                }
            } else if (mat.isAir()) {
                plantableSpots.add(cropBlock);
            }
        }

        if (minion.wasLastActionPlace()) {
            if (!matureCrops.isEmpty()) {
                Collections.shuffle(matureCrops);
                harvestSoilCrop(minion, matureCrops.getFirst(), dropItem, def);
            } else if (!growingCrops.isEmpty()) {
                advanceCropGrowth(minion, growingCrops.getFirst());
            } else if (!plantableSpots.isEmpty()) {
                Collections.shuffle(plantableSpots);
                plantSoilCrop(minion, plantableSpots.getFirst(), cropMat);
            } else {
                setNoSpaceState(minion);
            }
        } else {
            if (!plantableSpots.isEmpty()) {
                Collections.shuffle(plantableSpots);
                plantSoilCrop(minion, plantableSpots.getFirst(), cropMat);
            } else if (!matureCrops.isEmpty()) {
                Collections.shuffle(matureCrops);
                harvestSoilCrop(minion, matureCrops.getFirst(), dropItem, def);
            } else if (!growingCrops.isEmpty()) {
                advanceCropGrowth(minion, growingCrops.getFirst());
            } else {
                setNoSpaceState(minion);
            }
        }
    }

    private void plantSoilCrop(Minion minion, Block target, Material cropMat) {
        target.setType(cropMat);
        MinionAnimation.playPlaceAnimation(minion, target);
        minion.setLastActionWasPlace(true);
        minion.setLastActionTime(System.currentTimeMillis());
        minion.setState(MinionState.IDLE);
        MinionHologram.updateHologram(minion);
    }

    private void advanceCropGrowth(Minion minion, Block target) {
        if (target.getBlockData() instanceof Ageable ageable) {
            ageable.setAge(Math.min(ageable.getMaximumAge(), ageable.getAge() + 2));
            target.setBlockData(ageable);
            target.getWorld().spawnParticle(Particle.VILLAGER_HAPPY, target.getLocation().add(0.5, 0.3, 0.5), 5, 0.2, 0.2, 0.2, 0.05);
        }
        minion.setLastActionWasPlace(false);
        minion.setLastActionTime(System.currentTimeMillis());
        minion.setState(MinionState.IDLE);
        MinionHologram.updateHologram(minion);
    }

    private void harvestSoilCrop(Minion minion, Block target, ItemStack dropItem, MinionDefinition def) {
        MinionAnimation.playMineAnimation(minion, target);
        Material cropMat = target.getType();

        if (target.getBlockData() instanceof Ageable ageable) {
            ageable.setAge(0);
            target.setBlockData(ageable);
        } else {
            target.setType(cropMat);
        }

        minion.addItem(dropItem.clone());
        minion.incrementHarvested(1);
        minion.addStoredXp(def.getXpPerHarvest() > 0 ? def.getXpPerHarvest() : 1);

        if (def.hasSecondaryDrop() && Math.random() < def.getSecondaryDropChance()) {
            minion.addItem(def.getSecondaryDropItem().clone());
        }

        minion.setLastActionWasPlace(false);
        minion.setLastActionTime(System.currentTimeMillis());
        minion.setState(MinionState.IDLE);
        MinionHologram.updateHologram(minion);
    }

    private void tickStemCropMinion(Minion minion, MinionDefinition def, ItemStack dropItem, Material fruitMat) {
        List<Block> cropSpots = minion.getWorkingCropBlocks(); // at Y
        Material stemMat = fruitMat == Material.PUMPKIN ? Material.PUMPKIN_STEM : Material.MELON_STEM;

        List<Block> fruitBlocks = new ArrayList<>();
        List<Block> matureStems = new ArrayList<>();
        List<Block> growingStems = new ArrayList<>();
        List<Block> openFarmland = new ArrayList<>();

        for (Block b : cropSpots) {
            Block ground = b.getRelative(BlockFace.DOWN);
            if (ground.getType() == Material.DIRT || ground.getType() == Material.GRASS_BLOCK) {
                ground.setType(Material.FARMLAND);
            }
            if (ground.getType() != Material.FARMLAND) continue;

            Material mat = b.getType();
            if (mat == fruitMat) {
                fruitBlocks.add(b);
            } else if (mat == stemMat) {
                if (b.getBlockData() instanceof Ageable ageable && ageable.getAge() >= 7) {
                    matureStems.add(b);
                } else {
                    growingStems.add(b);
                }
            } else if (mat.isAir()) {
                openFarmland.add(b);
            }
        }

        if (!fruitBlocks.isEmpty()) {
            Collections.shuffle(fruitBlocks);
            Block target = fruitBlocks.getFirst();
            MinionAnimation.playMineAnimation(minion, target);
            target.setType(Material.AIR);

            minion.addItem(dropItem.clone());
            minion.incrementHarvested(1);
            minion.addStoredXp(def.getXpPerHarvest() > 0 ? def.getXpPerHarvest() : 1);

            minion.setLastActionWasPlace(false);
            minion.setLastActionTime(System.currentTimeMillis());
            minion.setState(MinionState.IDLE);
            MinionHologram.updateHologram(minion);
            return;
        }

        if (!matureStems.isEmpty() && !openFarmland.isEmpty()) {
            Collections.shuffle(openFarmland);
            Block target = openFarmland.getFirst();
            target.setType(fruitMat);
            MinionAnimation.playPlaceAnimation(minion, target);
            minion.setLastActionWasPlace(true);
            minion.setLastActionTime(System.currentTimeMillis());
            minion.setState(MinionState.IDLE);
            MinionHologram.updateHologram(minion);
            return;
        }

        if (!growingStems.isEmpty()) {
            Block target = growingStems.getFirst();
            if (target.getBlockData() instanceof Ageable ageable) {
                ageable.setAge(Math.min(7, ageable.getAge() + 2));
                target.setBlockData(ageable);
                target.getWorld().spawnParticle(Particle.VILLAGER_HAPPY, target.getLocation().add(0.5, 0.3, 0.5), 5, 0.2, 0.2, 0.2, 0.05);
            }
            minion.setLastActionWasPlace(false);
            minion.setLastActionTime(System.currentTimeMillis());
            minion.setState(MinionState.IDLE);
            MinionHologram.updateHologram(minion);
            return;
        }

        if (!openFarmland.isEmpty() && (matureStems.size() + growingStems.size()) < 6) {
            Collections.shuffle(openFarmland);
            Block target = openFarmland.getFirst();
            target.setType(stemMat);
            MinionAnimation.playPlaceAnimation(minion, target);
            minion.setLastActionWasPlace(true);
            minion.setLastActionTime(System.currentTimeMillis());
            minion.setState(MinionState.IDLE);
            MinionHologram.updateHologram(minion);
            return;
        }

        setNoSpaceState(minion);
    }

    private void tickSugarCaneMinion(Minion minion, MinionDefinition def, ItemStack dropItem) {
        List<Block> cropSpots = minion.getWorkingCropBlocks(); // at Y
        List<Block> harvestableTops = new ArrayList<>();
        List<Block> growableBases = new ArrayList<>();
        List<Block> plantableSpots = new ArrayList<>();

        for (Block b : cropSpots) {
            Block ground = b.getRelative(BlockFace.DOWN);
            Material groundMat = ground.getType();
            boolean validSoil = (groundMat == Material.DIRT || groundMat == Material.GRASS_BLOCK || groundMat == Material.SAND || groundMat == Material.RED_SAND)
                    && hasAdjacentWater(ground);

            if (!validSoil) continue;

            Material mat = b.getType();
            if (mat == Material.SUGAR_CANE) {
                Block top1 = b.getRelative(0, 1, 0);
                Block top2 = b.getRelative(0, 2, 0);

                if (top2.getType() == Material.SUGAR_CANE) {
                    harvestableTops.add(top2);
                } else if (top1.getType() == Material.SUGAR_CANE) {
                    harvestableTops.add(top1);
                } else if (top1.getType().isAir()) {
                    growableBases.add(b);
                }
            } else if (mat.isAir()) {
                plantableSpots.add(b);
            }
        }

        if (!harvestableTops.isEmpty()) {
            Block target = harvestableTops.getFirst();
            MinionAnimation.playMineAnimation(minion, target);
            target.setType(Material.AIR);

            minion.addItem(dropItem.clone());
            minion.incrementHarvested(1);
            minion.addStoredXp(def.getXpPerHarvest() > 0 ? def.getXpPerHarvest() : 1);

            minion.setLastActionWasPlace(false);
            minion.setLastActionTime(System.currentTimeMillis());
            minion.setState(MinionState.IDLE);
            MinionHologram.updateHologram(minion);
        } else if (!growableBases.isEmpty()) {
            Block base = growableBases.getFirst();
            Block top1 = base.getRelative(0, 1, 0);
            top1.setType(Material.SUGAR_CANE);
            MinionAnimation.playPlaceAnimation(minion, top1);

            minion.setLastActionWasPlace(true);
            minion.setLastActionTime(System.currentTimeMillis());
            minion.setState(MinionState.IDLE);
            MinionHologram.updateHologram(minion);
        } else if (!plantableSpots.isEmpty()) {
            Block target = plantableSpots.getFirst();
            target.setType(Material.SUGAR_CANE);
            MinionAnimation.playPlaceAnimation(minion, target);

            minion.setLastActionWasPlace(true);
            minion.setLastActionTime(System.currentTimeMillis());
            minion.setState(MinionState.IDLE);
            MinionHologram.updateHologram(minion);
        } else {
            setNoSpaceState(minion);
        }
    }

    private void tickCactusMinion(Minion minion, MinionDefinition def, ItemStack dropItem) {
        List<Block> cropSpots = minion.getWorkingCropBlocks(); // at Y
        List<Block> harvestableTops = new ArrayList<>();
        List<Block> growableBases = new ArrayList<>();
        List<Block> plantableSpots = new ArrayList<>();

        for (Block b : cropSpots) {
            Block ground = b.getRelative(BlockFace.DOWN);
            Material groundMat = ground.getType();
            if (groundMat != Material.SAND && groundMat != Material.RED_SAND) continue;

            int x = b.getX();
            int z = b.getZ();
            if ((x + z) % 2 != 0) continue;

            Material mat = b.getType();
            if (mat == Material.CACTUS) {
                Block top1 = b.getRelative(0, 1, 0);
                Block top2 = b.getRelative(0, 2, 0);

                if (top2.getType() == Material.CACTUS) {
                    harvestableTops.add(top2);
                } else if (top1.getType() == Material.CACTUS) {
                    harvestableTops.add(top1);
                } else if (top1.getType().isAir()) {
                    growableBases.add(b);
                }
            } else if (mat.isAir()) {
                plantableSpots.add(b);
            }
        }

        if (!harvestableTops.isEmpty()) {
            Block target = harvestableTops.getFirst();
            MinionAnimation.playMineAnimation(minion, target);
            target.setType(Material.AIR);

            minion.addItem(dropItem.clone());
            minion.incrementHarvested(1);
            minion.addStoredXp(def.getXpPerHarvest() > 0 ? def.getXpPerHarvest() : 1);

            minion.setLastActionWasPlace(false);
            minion.setLastActionTime(System.currentTimeMillis());
            minion.setState(MinionState.IDLE);
            MinionHologram.updateHologram(minion);
        } else if (!growableBases.isEmpty()) {
            Block base = growableBases.getFirst();
            Block top1 = base.getRelative(0, 1, 0);
            top1.setType(Material.CACTUS);
            MinionAnimation.playPlaceAnimation(minion, top1);

            minion.setLastActionWasPlace(true);
            minion.setLastActionTime(System.currentTimeMillis());
            minion.setState(MinionState.IDLE);
            MinionHologram.updateHologram(minion);
        } else if (!plantableSpots.isEmpty()) {
            Block target = plantableSpots.getFirst();
            target.setType(Material.CACTUS);
            MinionAnimation.playPlaceAnimation(minion, target);

            minion.setLastActionWasPlace(true);
            minion.setLastActionTime(System.currentTimeMillis());
            minion.setState(MinionState.IDLE);
            MinionHologram.updateHologram(minion);
        } else {
            setNoSpaceState(minion);
        }
    }

    private void tickMushroomMinion(Minion minion, MinionDefinition def, ItemStack dropItem, Material mushroomMat) {
        List<Block> cropSpots = minion.getWorkingCropBlocks(); // at Y
        List<Block> harvestableMushrooms = new ArrayList<>();
        List<Block> plantableSpots = new ArrayList<>();

        for (Block b : cropSpots) {
            Block ground = b.getRelative(BlockFace.DOWN);
            Material groundMat = ground.getType();
            if (groundMat != Material.PODZOL && groundMat != Material.MYCELIUM) continue;

            Material mat = b.getType();
            if (mat == mushroomMat) {
                harvestableMushrooms.add(b);
            } else if (mat.isAir()) {
                plantableSpots.add(b);
            }
        }

        if (minion.wasLastActionPlace() && !harvestableMushrooms.isEmpty()) {
            Block target = harvestableMushrooms.getFirst();
            MinionAnimation.playMineAnimation(minion, target);
            target.setType(Material.AIR);

            minion.addItem(dropItem.clone());
            minion.incrementHarvested(1);
            minion.addStoredXp(def.getXpPerHarvest() > 0 ? def.getXpPerHarvest() : 1);

            minion.setLastActionWasPlace(false);
            minion.setLastActionTime(System.currentTimeMillis());
            minion.setState(MinionState.IDLE);
            MinionHologram.updateHologram(minion);
        } else if (!plantableSpots.isEmpty()) {
            Block target = plantableSpots.getFirst();
            target.setType(mushroomMat);
            MinionAnimation.playPlaceAnimation(minion, target);

            minion.setLastActionWasPlace(true);
            minion.setLastActionTime(System.currentTimeMillis());
            minion.setState(MinionState.IDLE);
            MinionHologram.updateHologram(minion);
        } else if (!harvestableMushrooms.isEmpty()) {
            Block target = harvestableMushrooms.getFirst();
            MinionAnimation.playMineAnimation(minion, target);
            target.setType(Material.AIR);

            minion.addItem(dropItem.clone());
            minion.incrementHarvested(1);
            minion.addStoredXp(def.getXpPerHarvest() > 0 ? def.getXpPerHarvest() : 1);

            minion.setLastActionWasPlace(false);
            minion.setLastActionTime(System.currentTimeMillis());
            minion.setState(MinionState.IDLE);
            MinionHologram.updateHologram(minion);
        } else {
            setNoSpaceState(minion);
        }
    }

    // =========================================================================
    // 4. COMBAT & HUSBANDRY MINION (Mobs spawn at Y elevation)
    // =========================================================================
    private void tickCombatMinion(Minion minion, MinionDefinition def, ItemStack dropItem) {
        Location loc = minion.getLocation();
        if (loc.getWorld() == null) return;

        int cap = SkyBlockMinions.getInstance().getConfig().getInt("settings.combat.max-mobs-per-minion", 5);
        boolean disableAi = SkyBlockMinions.getInstance().getConfig().getBoolean("settings.combat.disable-mob-wandering", true);

        List<Entity> taggedMobs = new ArrayList<>();
        for (Entity e : loc.getWorld().getNearbyEntities(loc, 3.5, 2.0, 3.5)) {
            if (MythicMobsHook.isMinionSpawnedMob(e) && e.isValid() && !e.isDead()) {
                taggedMobs.add(e);
            }
        }

        if (taggedMobs.size() < cap && !minion.wasLastActionPlace()) {
            List<Block> working = minion.getWorkingCropBlocks(); // at Y!
            List<Location> validLocs = new ArrayList<>();
            for (Block b : working) {
                if (b.getType().isAir() || !b.getType().isSolid()) {
                    validLocs.add(b.getLocation().add(0.5, 0.0, 0.5));
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
            Entity target = taggedMobs.getFirst();
            MinionAnimation.playMineAnimation(minion, target.getLocation().getBlock());
            target.remove();

            minion.addItem(dropItem.clone());
            minion.incrementHarvested(1);
            int xp = def.getXpPerHarvest() > 0 ? def.getXpPerHarvest() : 3;
            minion.addStoredXp(xp);

            if (def.hasSecondaryDrop() && Math.random() < def.getSecondaryDropChance()) {
                minion.addItem(def.getSecondaryDropItem().clone());
            }

            minion.setLastActionWasPlace(false);
            minion.setLastActionTime(System.currentTimeMillis());
            minion.setState(MinionState.IDLE);
            MinionHologram.updateHologram(minion);
        } else {
            setNoSpaceState(minion);
        }
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================
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

        boolean hasSpreading = minion.hasUpgrade(MinionUpgradeType.DIAMOND_SPREADING)
                || minion.hasUpgradeBehavior(CustomUpgrade.UpgradeBehavior.DIAMOND_SPREADING);
        if (hasSpreading && Math.random() < 0.10) {
            minion.addItem(new ItemStack(Material.DIAMOND, 1));
        }

        minion.setLastActionWasPlace(false);
        minion.setLastActionTime(System.currentTimeMillis());
        MinionHologram.updateHologram(minion);
    }

    private boolean isMatchingTargetBlock(Material type, Material targetBlock, Minion minion) {
        if (type == targetBlock) return true;
        if (minion.getType() != null) {
            if (type == minion.getType().getTargetMaterial() || type == minion.getType().getPlacedMaterial()) return true;
            if (minion.getType() == MinionType.COBBLESTONE && (type == Material.COBBLESTONE || type == Material.STONE)) return true;
            if (targetBlock == Material.DIAMOND_ORE && type == Material.DEEPSLATE_DIAMOND_ORE) return true;
            if (targetBlock == Material.IRON_ORE && type == Material.DEEPSLATE_IRON_ORE) return true;
        }
        return false;
    }

    private boolean isWoodLog(Material mat, Material targetLog) {
        if (mat == targetLog) return true;
        return mat.name().endsWith("_LOG") || mat.name().endsWith("_WOOD") || mat.name().endsWith("_STEM");
    }

    private boolean isSoil(Material mat) {
        return mat == Material.DIRT || mat == Material.GRASS_BLOCK || mat == Material.COARSE_DIRT || mat == Material.PODZOL
                || mat == Material.ROOTED_DIRT || mat == Material.MOSS_BLOCK || mat == Material.WARPED_NYLIUM || mat == Material.CRIMSON_NYLIUM;
    }

    private boolean isStemCrop(Material mat) {
        return mat == Material.PUMPKIN || mat == Material.MELON;
    }

    private boolean isMushroom(Material mat) {
        return mat == Material.RED_MUSHROOM || mat == Material.BROWN_MUSHROOM;
    }

    private boolean hasAdjacentWater(Block ground) {
        for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            Material relative = ground.getRelative(face).getType();
            if (relative == Material.WATER) return true;
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
            if (drop.getType().name().endsWith("_LOG") || drop.getType().name().endsWith("_WOOD")) {
                return new ItemStack(Material.CHARCOAL, drop.getAmount());
            }
        }
        return drop;
    }
}
