package com.skyblock.minions.core;

import com.skyblock.minions.api.*;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Represents a placed minion instance in the world.
 */
public class Minion {

    private final UUID minionId;
    private final UUID ownerId;
    private final MinionType type;
    private int tier;
    private final Location location;

    private final List<ItemStack> inventory;
    private String fuelId;
    private long fuelEndTime; // -1 if permanent, or expiration millis
    private String upgrade1Id;
    private String upgrade2Id;

    private long lastActionTime;
    private boolean lastActionWasPlace;
    private int totalHarvested;
    private MinionState state;

    private UUID armorStandId;
    private UUID hologramId;

    public Minion(UUID minionId, UUID ownerId, MinionType type, int tier, Location location) {
        this.minionId = minionId != null ? minionId : UUID.randomUUID();
        this.ownerId = ownerId;
        this.type = type;
        this.tier = Math.clamp(tier, 1, MinionTier.getMaxTier());
        this.location = location;
        this.inventory = new ArrayList<>();
        this.lastActionTime = System.currentTimeMillis();
        this.lastActionWasPlace = false;
        this.totalHarvested = 0;
        this.state = MinionState.IDLE;
        this.fuelEndTime = 0;
    }

    public UUID getMinionId() {
        return minionId;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public MinionType getType() {
        return type;
    }

    public int getTier() {
        return tier;
    }

    public void setTier(int tier) {
        this.tier = Math.clamp(tier, 1, MinionTier.getMaxTier());
    }

    public Location getLocation() {
        return location;
    }

    public List<ItemStack> getInventory() {
        return inventory;
    }

    public String getFuelId() {
        return fuelId;
    }

    public CustomFuel getCustomFuel() {
        return FuelRegistry.get(fuelId);
    }

    public MinionFuelType getFuel() {
        if (fuelId == null) return null;
        try {
            return MinionFuelType.valueOf(fuelId.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public void setFuel(String fuelId) {
        this.fuelId = fuelId != null ? fuelId.toLowerCase() : null;
        CustomFuel cf = getCustomFuel();
        if (cf == null) {
            this.fuelEndTime = 0;
        } else if (cf.isPermanent()) {
            this.fuelEndTime = -1L;
        } else {
            this.fuelEndTime = System.currentTimeMillis() + cf.getDurationMillis();
        }
    }

    public void setFuel(CustomFuel fuel) {
        if (fuel == null) {
            setFuel((String) null);
        } else {
            setFuel(fuel.getId());
        }
    }

    public void setFuel(MinionFuelType fuel) {
        if (fuel == null) {
            setFuel((String) null);
        } else {
            setFuel(fuel.name().toLowerCase());
        }
    }

    public void setFuelType(MinionFuelType fuel) {
        setFuel(fuel);
    }

    public long getFuelEndTime() {
        return fuelEndTime;
    }

    public void setFuelEndTime(long fuelEndTime) {
        this.fuelEndTime = fuelEndTime;
    }

    public boolean isFuelActive() {
        CustomFuel fuel = getCustomFuel();
        if (fuel == null) return false;
        if (fuel.isPermanent()) return true;
        return System.currentTimeMillis() < fuelEndTime;
    }

    public String getUpgrade1Id() {
        return upgrade1Id;
    }

    public void setUpgrade1Id(String upgrade1Id) {
        this.upgrade1Id = upgrade1Id != null ? upgrade1Id.toLowerCase() : null;
    }

    public CustomUpgrade getCustomUpgrade1() {
        return UpgradeRegistry.get(upgrade1Id);
    }

    public MinionUpgradeType getUpgrade1() {
        if (upgrade1Id == null) return null;
        try {
            return MinionUpgradeType.valueOf(upgrade1Id.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public void setUpgrade1(CustomUpgrade upgrade) {
        this.upgrade1Id = upgrade != null ? upgrade.getId() : null;
    }

    public void setUpgrade1(MinionUpgradeType upgrade1) {
        this.upgrade1Id = upgrade1 != null ? upgrade1.name().toLowerCase() : null;
    }

    public void setUpgradeType1(MinionUpgradeType upgrade1) {
        setUpgrade1(upgrade1);
    }

    public String getUpgrade2Id() {
        return upgrade2Id;
    }

    public void setUpgrade2Id(String upgrade2Id) {
        this.upgrade2Id = upgrade2Id != null ? upgrade2Id.toLowerCase() : null;
    }

    public CustomUpgrade getCustomUpgrade2() {
        return UpgradeRegistry.get(upgrade2Id);
    }

    public MinionUpgradeType getUpgrade2() {
        if (upgrade2Id == null) return null;
        try {
            return MinionUpgradeType.valueOf(upgrade2Id.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public void setUpgrade2(CustomUpgrade upgrade) {
        this.upgrade2Id = upgrade != null ? upgrade.getId() : null;
    }

    public void setUpgrade2(MinionUpgradeType upgrade2) {
        this.upgrade2Id = upgrade2 != null ? upgrade2.name().toLowerCase() : null;
    }

    public void setUpgradeType2(MinionUpgradeType upgrade2) {
        setUpgrade2(upgrade2);
    }

    public boolean hasUpgradeBehavior(CustomUpgrade.UpgradeBehavior behavior) {
        if (behavior == null) return false;
        CustomUpgrade u1 = getCustomUpgrade1();
        if (u1 != null && u1.getBehavior() == behavior) return true;
        CustomUpgrade u2 = getCustomUpgrade2();
        return u2 != null && u2.getBehavior() == behavior;
    }

    public boolean hasUpgrade(MinionUpgradeType upgradeType) {
        if (upgradeType == null) return false;
        String target = upgradeType.name().toLowerCase();
        return target.equalsIgnoreCase(upgrade1Id) || target.equalsIgnoreCase(upgrade2Id);
    }

    public double getEffectiveActionDelay() {
        double baseTime = MinionTier.getActionDelay(this.tier);
        double totalBuff = 0.0;

        if (isFuelActive()) {
            CustomFuel f = getCustomFuel();
            if (f != null) {
                totalBuff += f.getSpeedBuff();
            }
        }

        CustomUpgrade u1 = getCustomUpgrade1();
        if (u1 != null && u1.getSpeedBuff() > 0) {
            totalBuff += u1.getSpeedBuff();
        }

        CustomUpgrade u2 = getCustomUpgrade2();
        if (u2 != null && u2.getSpeedBuff() > 0) {
            totalBuff += u2.getSpeedBuff();
        }

        return baseTime / (1.0 + totalBuff);
    }

    /**
     * Checks if the minion has room to accept another item stack.
     */
    public boolean canAccept(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return true;
        int maxSlots = MinionTier.getStorageSlots(this.tier);

        // Check if item can stack with existing slots
        for (ItemStack stored : inventory) {
            if (isSameItem(stored, item) && stored.getAmount() < stored.getMaxStackSize()) {
                return true;
            }
        }

        // Check if new empty slot is available
        return inventory.size() < maxSlots;
    }

    /**
     * Adds an item to the minion's inventory, respecting stack limits and tier slots.
     * Returns true if successfully added, false if inventory is full.
     */
    public boolean addItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return true;
        int maxSlots = MinionTier.getStorageSlots(this.tier);
        ItemStack toAdd = item.clone();

        // Try stacking into existing items
        for (ItemStack stored : inventory) {
            if (isSameItem(stored, toAdd)) {
                int space = stored.getMaxStackSize() - stored.getAmount();
                if (space > 0) {
                    int addAmount = Math.min(space, toAdd.getAmount());
                    stored.setAmount(stored.getAmount() + addAmount);
                    toAdd.setAmount(toAdd.getAmount() - addAmount);
                    if (toAdd.getAmount() <= 0) {
                        return true;
                    }
                }
            }
        }

        // Add to new slot if available
        if (inventory.size() < maxSlots && toAdd.getAmount() > 0) {
            inventory.add(toAdd);
            return true;
        }

        return false;
    }

    private boolean isSameItem(ItemStack a, ItemStack b) {
        if (a == null || b == null) return false;
        if (a.getType() != b.getType()) return false;
        try {
            if (org.bukkit.Bukkit.getServer() != null) {
                return a.isSimilar(b);
            }
        } catch (Throwable ignored) {}
        return true;
    }

    public int getTotalStoredCount() {
        int count = 0;
        for (ItemStack item : inventory) {
            if (item != null) {
                count += item.getAmount();
            }
        }
        return count;
    }

    /**
     * Returns the 24 perimeter blocks in the 5x5 working radius around the minion at its elevation.
     */
    public List<Block> getWorkingAreaBlocks() {
        List<Block> blocks = new ArrayList<>();
        if (location == null || location.getWorld() == null) return blocks;

        int centerX = location.getBlockX();
        int resourceY = location.getBlockY() - 1;
        int centerZ = location.getBlockZ();

        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                if (x == 0 && z == 0) continue; // Skip center where minion stands
                Block block = location.getWorld().getBlockAt(centerX + x, resourceY, centerZ + z);
                blocks.add(block);
            }
        }

        return blocks;
    }

    public long getLastActionTime() {
        return lastActionTime;
    }

    public void setLastActionTime(long lastActionTime) {
        this.lastActionTime = lastActionTime;
    }

    public boolean wasLastActionPlace() {
        return lastActionWasPlace;
    }

    public void setLastActionWasPlace(boolean lastActionWasPlace) {
        this.lastActionWasPlace = lastActionWasPlace;
    }

    public int getTotalHarvested() {
        return totalHarvested;
    }

    public void setTotalHarvested(int totalHarvested) {
        this.totalHarvested = totalHarvested;
    }

    public void incrementHarvested(int amount) {
        this.totalHarvested += amount;
    }

    public MinionState getState() {
        return state;
    }

    public void setState(MinionState state) {
        this.state = state;
    }

    public UUID getArmorStandId() {
        return armorStandId;
    }

    public void setArmorStandId(UUID armorStandId) {
        this.armorStandId = armorStandId;
    }

    public UUID getHologramId() {
        return hologramId;
    }

    public void setHologramId(UUID hologramId) {
        this.hologramId = hologramId;
    }
}
