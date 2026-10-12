package com.minionx.core;

import com.minionx.api.*;
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
    private String skinId;

    private long lastActionTime;
    private boolean lastActionWasPlace;
    private int totalHarvested;
    private MinionState state;

    private UUID armorStandId;
    private UUID hologramId;
    private UUID dialogueHologramId;
    private int storedXp;
    private String customDefinitionId;

    private Location storageLocation;
    private String storageId;
    private final List<ItemStack> storageContents = new ArrayList<>();

    private String activeDialogue;
    private long dialogueExpiresAt;

    private transient String lastHologramText;
    private transient float lastRenderedYaw = Float.NaN;

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

    public com.minionx.api.MinionDefinition getDefinition() {
        if (customDefinitionId != null) {
            return com.minionx.api.MinionRegistry.get(customDefinitionId);
        }
        if (type != null) {
            return com.minionx.api.MinionRegistry.get(type.name().toLowerCase());
        }
        return null;
    }

    public int getMaxTier() {
        com.minionx.api.MinionDefinition def = getDefinition();
        if (def != null && def.getMaxTier() > 0) {
            return def.getMaxTier();
        }
        if (type != null) {
            return type.getMaxTier();
        }
        return com.minionx.api.MinionTier.getMaxTier();
    }

    public double getBaseActionDelay() {
        com.minionx.api.MinionDefinition def = getDefinition();
        if (def != null) {
            return def.getActionDelay(this.tier);
        }
        return MinionTier.getActionDelay(this.tier);
    }

    public int getStorageSlots() {
        com.minionx.api.MinionDefinition def = getDefinition();
        if (def != null) {
            return def.getStorageSlots(this.tier);
        }
        return MinionTier.getStorageSlots(this.tier);
    }

    public int getMaxCapacity() {
        return getStorageSlots() * 64;
    }

    public int getUpgradeCost() {
        if (this.tier >= getMaxTier()) return 0;
        com.minionx.api.MinionDefinition def = getDefinition();
        if (def != null) {
            return def.getUpgradeCost(this.tier);
        }
        return MinionTier.getUpgradeCost(this.tier);
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
        this.tier = Math.clamp(tier, 1, getMaxTier());
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
        setFuel(fuelId, 1);
    }

    public void setFuel(String fuelId, int amount) {
        this.fuelId = fuelId != null ? fuelId.toLowerCase() : null;
        CustomFuel cf = getCustomFuel();
        if (cf == null) {
            this.fuelEndTime = 0;
        } else if (cf.isPermanent()) {
            this.fuelEndTime = -1L;
        } else {
            amount = Math.max(1, amount);
            this.fuelEndTime = System.currentTimeMillis() + (cf.getDurationMillis() * (long) amount);
        }
    }

    public void setFuel(CustomFuel fuel) {
        setFuel(fuel, 1);
    }

    public void setFuel(CustomFuel fuel, int amount) {
        if (fuel == null) {
            setFuel((String) null, 1);
        } else {
            this.fuelId = fuel.getId().toLowerCase();
            if (fuel.isPermanent()) {
                this.fuelEndTime = -1L;
            } else {
                amount = Math.max(1, amount);
                this.fuelEndTime = System.currentTimeMillis() + (fuel.getDurationMillis() * (long) amount);
            }
        }
    }

    public void extendFuel(CustomFuel fuel, int amount) {
        if (fuel == null || fuel.isPermanent()) return;
        amount = Math.max(1, amount);
        long added = fuel.getDurationMillis() * (long) amount;
        long now = System.currentTimeMillis();
        if (this.fuelEndTime > now) {
            this.fuelEndTime += added;
        } else {
            this.fuelEndTime = now + added;
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

    public boolean isFuelPermanent() {
        CustomFuel cf = getCustomFuel();
        return cf != null && cf.isPermanent();
    }

    public String getFormattedFuelTimeRemaining() {
        if (isFuelPermanent()) return "Permanent";
        if (!isFuelActive()) return "Expired";
        long remaining = Math.max(0, fuelEndTime - System.currentTimeMillis());
        long days = remaining / (86400 * 1000L);
        long hours = (remaining % (86400 * 1000L)) / (3600 * 1000L);
        long minutes = (remaining % (3600 * 1000L)) / (60 * 1000L);
        long seconds = (remaining % (60 * 1000L)) / 1000L;
        if (days > 0) return String.format("%dd %dh", days, hours);
        if (hours > 0) return String.format("%dh %dm", hours, minutes);
        return String.format("%dm %ds", minutes, seconds);
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

    public String getSkinId() {
        return skinId;
    }

    public void setSkinId(String skinId) {
        this.skinId = skinId != null && !skinId.isBlank() ? skinId.toLowerCase() : null;
    }

    public MinionSkin getSkin() {
        return skinId != null ? MinionSkinRegistry.get(skinId) : null;
    }

    public boolean hasSkin() {
        return skinId != null && getSkin() != null;
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
        double baseTime = getBaseActionDelay();
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
        int maxSlots = getStorageSlots();

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
        int maxSlots = getStorageSlots();
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

        // If internal inventory cannot fit all, try overflowing to linked storage chest!
        if (hasLinkedStorage() && toAdd.getAmount() > 0) {
            com.minionx.api.CustomMinionStorage st = getLinkedStorage();
            int maxStorageSlots = st != null ? st.getCapacity() : 9;

            // Try stacking into existing storage items
            for (ItemStack stored : storageContents) {
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

            // Add to new slot in storage if space
            if (storageContents.size() < maxStorageSlots && toAdd.getAmount() > 0) {
                storageContents.add(toAdd);
                return true;
            }
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

    public void addItemDirect(ItemStack item) {
        if (item != null && item.getType() != Material.AIR) {
            this.inventory.add(item);
        }
    }

    public void clearInventory() {
        this.inventory.clear();
    }

    public int getTotalStoredCount() {
        int count = 0;
        for (ItemStack item : inventory) {
            if (item != null) {
                count += item.getAmount();
            }
        }
        for (ItemStack item : storageContents) {
            if (item != null) {
                count += item.getAmount();
            }
        }
        return count;
    }

    /**
     * Returns the 24 perimeter blocks in the 5x5 working radius around the minion at its elevation.
     */
    public List<Block> getWorkingGroundBlocks() {
        return getWorkingAreaBlocks();
    }

    public List<Block> getWorkingCropBlocks() {
        List<Block> blocks = new ArrayList<>();
        if (location == null || location.getWorld() == null) return blocks;

        int centerX = location.getBlockX();
        int cropY = location.getBlockY();
        int centerZ = location.getBlockZ();

        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                if (x == 0 && z == 0) continue;
                Block block = location.getWorld().getBlockAt(centerX + x, cropY, centerZ + z);
                blocks.add(block);
            }
        }
        return blocks;
    }

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

    public int getStoredXp() {
        return storedXp;
    }

    public void setStoredXp(int storedXp) {
        this.storedXp = storedXp;
    }

    public void addStoredXp(int xp) {
        this.storedXp += xp;
    }

    public int claimStoredXp() {
        int x = this.storedXp;
        this.storedXp = 0;
        return x;
    }

    public String getCustomDefinitionId() {
        return customDefinitionId;
    }

    public String getDisplayName() {
        if (customDefinitionId != null) {
            MinionDefinition def = MinionRegistry.get(customDefinitionId);
            if (def != null) {
                return def.getDisplayName();
            }
        }
        return type != null ? type.getDisplayName() : "Unknown";
    }

    public void setCustomDefinitionId(String customDefinitionId) {
        this.customDefinitionId = customDefinitionId;
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

    public UUID getDialogueHologramId() {
        return dialogueHologramId;
    }

    public void setDialogueHologramId(UUID dialogueHologramId) {
        this.dialogueHologramId = dialogueHologramId;
    }


    // --- Storage System ---
    public boolean hasLinkedStorage() {
        return storageLocation != null && storageId != null;
    }

    public Location getLinkedStorageLocation() {
        return storageLocation;
    }

    public String getStorageId() {
        return storageId;
    }

    public CustomMinionStorage getLinkedStorage() {
        if (storageId == null) return null;
        return MinionStorageRegistry.get(storageId);
    }

    public List<ItemStack> getStorageContents() {
        return storageContents;
    }

    public void linkStorage(Location location, String storageId) {
        this.storageLocation = location;
        this.storageId = storageId;
    }

    public void unlinkStorage() {
        this.storageLocation = null;
        this.storageId = null;
        this.storageContents.clear();
    }

    // --- Dialogue System ---
    public boolean hasActiveDialogue() {
        return activeDialogue != null && System.currentTimeMillis() < dialogueExpiresAt;
    }

    public String getActiveDialogue() {
        return hasActiveDialogue() ? activeDialogue : null;
    }

    public void speak(String message, long durationMillis) {
        this.activeDialogue = message;
        this.dialogueExpiresAt = System.currentTimeMillis() + durationMillis;
    }

    public void clearDialogue() {
        this.activeDialogue = null;
        this.dialogueExpiresAt = 0;
    }

    public boolean checkAndClearExpiredDialogue() {
        if (activeDialogue != null && System.currentTimeMillis() >= dialogueExpiresAt) {
            clearDialogue();
            return true;
        }
        return false;
    }



    public String getLastHologramText() {
        return lastHologramText;
    }

    public void setLastHologramText(String lastHologramText) {
        this.lastHologramText = lastHologramText;
    }

    public float getLastRenderedYaw() {
        return lastRenderedYaw;
    }

    public void setLastRenderedYaw(float lastRenderedYaw) {
        this.lastRenderedYaw = lastRenderedYaw;
    }
}
