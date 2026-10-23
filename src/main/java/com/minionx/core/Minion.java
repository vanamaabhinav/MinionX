package com.minionx.core;

import com.minionx.MinionX;
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
    private String shippingId;
    private double shippingCoins;
    private long shippingCoinsMicros;

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

    // Transaction Guard & Revisions
    private final java.util.concurrent.atomic.AtomicBoolean isTransacting = new java.util.concurrent.atomic.AtomicBoolean(false);
    private final java.util.concurrent.atomic.AtomicBoolean isFullSleeping = new java.util.concurrent.atomic.AtomicBoolean(false);
    private final java.util.concurrent.atomic.AtomicLong stateRevision = new java.util.concurrent.atomic.AtomicLong(0L);
    private long schedulingGeneration = 0L;
    private long lastCatchupTime = 0L;
    private boolean initialCatchupDone = false;
    private final Map<String, Long> dropRemaindersMicros = new java.util.concurrent.ConcurrentHashMap<>();
    private long fractionalProgressMicros = 0L;

    private transient String lastHologramText;
    private transient float lastRenderedYaw = Float.NaN;
    private transient org.bukkit.entity.Entity cachedArmorStand;
    private transient org.bukkit.entity.TextDisplay cachedHologram;
    private final Set<UUID> trackedSpawnedMobs = java.util.concurrent.ConcurrentHashMap.newKeySet();

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

    public Minion(UUID minionId, MinionType type, int tier) {
        this(minionId, null, type, tier, null);
    }

    public UUID getMinionId() {
        return minionId;
    }

    public UUID getId() {
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
        advanceStateRevision();
        advanceSchedulingGeneration();
        if (!isFullSleeping() && MinionX.getInstance() != null && MinionX.getInstance().getMinionTickTask() != null) {
            MinionX.getInstance().getMinionTickTask().scheduleMinion(this);
        }
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
        advanceStateRevision();
        advanceSchedulingGeneration();
        if (!isFullSleeping() && MinionX.getInstance() != null && MinionX.getInstance().getMinionTickTask() != null) {
            MinionX.getInstance().getMinionTickTask().scheduleMinion(this);
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
            advanceStateRevision();
            advanceSchedulingGeneration();
            if (!isFullSleeping() && MinionX.getInstance() != null && MinionX.getInstance().getMinionTickTask() != null) {
                MinionX.getInstance().getMinionTickTask().scheduleMinion(this);
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
        advanceStateRevision();
        advanceSchedulingGeneration();
        if (!isFullSleeping() && MinionX.getInstance() != null && MinionX.getInstance().getMinionTickTask() != null) {
            MinionX.getInstance().getMinionTickTask().scheduleMinion(this);
        }
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
        setUpgrade1Id(upgrade != null ? upgrade.getId() : null);
    }

    public void setUpgrade1(MinionUpgradeType upgrade1) {
        setUpgrade1Id(upgrade1 != null ? upgrade1.name().toLowerCase() : null);
    }

    public void setUpgradeType1(MinionUpgradeType upgrade1) {
        setUpgrade1(upgrade1);
    }

    public String getUpgrade2Id() {
        return upgrade2Id;
    }

    public void setUpgrade2Id(String upgrade2Id) {
        this.upgrade2Id = upgrade2Id != null ? upgrade2Id.toLowerCase() : null;
        advanceStateRevision();
        advanceSchedulingGeneration();
        if (!isFullSleeping() && MinionX.getInstance() != null && MinionX.getInstance().getMinionTickTask() != null) {
            MinionX.getInstance().getMinionTickTask().scheduleMinion(this);
        }
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
        setUpgrade2Id(upgrade != null ? upgrade.getId() : null);
    }

    public void setUpgrade2(MinionUpgradeType upgrade2) {
        setUpgrade2Id(upgrade2 != null ? upgrade2.name().toLowerCase() : null);
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

    public String getShippingId() {
        return shippingId;
    }

    public void setShippingId(String shippingId) {
        this.shippingId = shippingId != null && !shippingId.isBlank() ? shippingId.toLowerCase() : null;
    }

    public CustomUpgrade getShippingUpgrade() {
        return shippingId != null ? UpgradeRegistry.get(shippingId) : null;
    }

    public boolean hasAutoSeller() {
        CustomUpgrade u = getShippingUpgrade();
        return u != null && u.isAutoSeller();
    }

    private double reservedShippingCoins = 0.0;

    public double getShippingCoins() {
        return shippingCoinsMicros > 0 ? shippingCoinsMicros / 1_000_000.0 : shippingCoins;
    }

    public double getClaimableShippingCoins() {
        return getShippingCoins();
    }

    public double getReservedShippingCoins() {
        return reservedShippingCoins;
    }

    public void setReservedShippingCoins(double reserved) {
        this.reservedShippingCoins = Math.max(0.0, reserved);
    }

    public double getTotalShippingCoins() {
        return getShippingCoins() + reservedShippingCoins;
    }

    public synchronized boolean reserveClaimableCoins(double amount) {
        if (!Double.isFinite(amount) || amount < 0.0001) {
            return false;
        }
        double normalized = java.math.BigDecimal.valueOf(amount).setScale(4, java.math.RoundingMode.HALF_UP).doubleValue();
        if (normalized < 0.0001) {
            return false;
        }
        long amountMicros = Math.round(normalized * 1_000_000.0);
        if (this.shippingCoinsMicros < amountMicros) {
            return false;
        }
        this.shippingCoinsMicros -= amountMicros;
        this.shippingCoins = this.shippingCoinsMicros / 1_000_000.0;
        this.reservedShippingCoins += normalized;
        return true;
    }

    public synchronized void cancelReservedCoins() {
        if (this.reservedShippingCoins > 0.0) {
            double restore = this.reservedShippingCoins;
            this.reservedShippingCoins = 0.0;
            addShippingCoins(restore);
        }
    }

    public synchronized void commitReservedCoins() {
        this.reservedShippingCoins = 0.0;
        advanceStateRevision();
    }

    public void setShippingCoins(double coins) {
        double clamped = Math.max(0.0, coins);
        double normalized = java.math.BigDecimal.valueOf(clamped).setScale(4, java.math.RoundingMode.HALF_UP).doubleValue();
        this.shippingCoins = normalized;
        this.shippingCoinsMicros = Math.round(normalized * 1_000_000.0);
        advanceStateRevision();
    }

    public long getShippingCoinsMicros() {
        return shippingCoinsMicros;
    }

    public void setShippingCoinsMicros(long micros) {
        // Enforce 4-decimal alignment (multiples of 100 micros)
        long clamped = Math.max(0L, micros);
        long normalizedMicros = (clamped / 100L) * 100L;
        this.shippingCoinsMicros = normalizedMicros;
        this.shippingCoins = this.shippingCoinsMicros / 1_000_000.0;
        advanceStateRevision();
    }

    public void addShippingCoins(double coins) {
        if (!Double.isFinite(coins) || coins < 0.0001) {
            return;
        }
        double normalized = java.math.BigDecimal.valueOf(coins).setScale(4, java.math.RoundingMode.HALF_UP).doubleValue();
        if (normalized < 0.0001) {
            return;
        }
        long addMicros = Math.round(normalized * 1_000_000.0);
        try {
            this.shippingCoinsMicros = Math.addExact(this.shippingCoinsMicros, addMicros);
            this.shippingCoins = this.shippingCoinsMicros / 1_000_000.0;
            advanceStateRevision();
        } catch (ArithmeticException e) {
            // Reject credit on arithmetic overflow; never manufacture Long.MAX_VALUE balance!
            if (org.bukkit.Bukkit.getServer() != null) {
                org.bukkit.Bukkit.getLogger().warning("[MinionX] Overflow detected in addShippingCoins for minion " + minionId + "! Rejected credit: " + coins);
            }
        }
    }

    public double claimShippingCoins() {
        double c = getShippingCoins();
        this.shippingCoins = 0.0;
        this.shippingCoinsMicros = 0L;
        advanceStateRevision();
        return c;
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
     * Evaluates internal inventory slots, linked storage chest capacity,
     * and Automated Shipping hoppers (which auto-sell overflow items for coins).
     */
    public boolean canAccept(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return true;
        int maxSlots = getStorageSlots();

        // 1. Check if item can stack with existing internal inventory slots
        for (ItemStack stored : inventory) {
            if (isSameItem(stored, item) && stored.getAmount() < stored.getMaxStackSize()) {
                return true;
            }
        }

        // 2. Check if new empty internal slot is available
        if (inventory.size() < maxSlots) {
            return true;
        }

        // 3. Check if linked storage chest can accept
        if (hasLinkedStorage()) {
            com.minionx.api.CustomMinionStorage st = getLinkedStorage();
            int maxStorageSlots = st != null ? st.getCapacity() : 9;

            for (ItemStack stored : storageContents) {
                if (isSameItem(stored, item) && stored.getAmount() < stored.getMaxStackSize()) {
                    return true;
                }
            }

            if (storageContents.size() < maxStorageSlots) {
                return true;
            }
        }

        // 4. Check if Automated Shipping hopper is equipped to auto-sell overflow
        if (hasAutoSeller()) {
            return true;
        }

        return false;
    }

    /**
     * Adds an item to the minion's inventory, respecting stack limits and tier slots.
     * If internal inventory is full, overflows into linked storage chest.
     * If linked storage is also full, auto-sells via equipped Automated Shipping hopper.
     * Returns true if successfully handled, false if completely full.
     */
    public boolean addItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return true;
        int maxSlots = getStorageSlots();
        ItemStack toAdd = item.clone();

        // 1. Try stacking into internal inventory
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

        // 2. Add to new internal slot if available
        if (inventory.size() < maxSlots && toAdd.getAmount() > 0) {
            inventory.add(toAdd);
            return true;
        }

        // 3. Overflow into linked storage chest if present
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

            // Add to new slot in storage if capacity allows
            if (storageContents.size() < maxStorageSlots && toAdd.getAmount() > 0) {
                storageContents.add(toAdd);
                return true;
            }
        }

        // 4. Automated Shipping: auto-sell remaining items when full
        if (hasAutoSeller() && toAdd.getAmount() > 0) {
            double unitPrice = com.minionx.api.PriceRegistry.getPrice(toAdd);
            if (unitPrice <= 0.0) {
                // Items without a configured price in prices.yml cannot be sold or destroyed for free; overflow handling stops when unpriced items remain.
                return false;
            }
            CustomUpgrade hopper = getShippingUpgrade();
            double ratio = (hopper != null && hopper.getSellRatio() > 0) ? hopper.getSellRatio() : 0.50;
            double earned = unitPrice * toAdd.getAmount() * ratio;
            addShippingCoins(earned);
            toAdd.setAmount(0);
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

    public record BlockOffset(int dx, int dz) {}

    private static final List<BlockOffset> OFFSETS_5X5;
    private static final List<BlockOffset> OFFSETS_7X7;
    private static final List<BlockOffset> OFFSETS_9X9;

    static {
        OFFSETS_5X5 = computeOffsets(2); // 24 offsets (5x5)
        OFFSETS_7X7 = computeOffsets(3); // 48 offsets (7x7)
        OFFSETS_9X9 = computeOffsets(4); // 80 offsets (9x9)
    }

    private static List<BlockOffset> computeOffsets(int radius) {
        List<BlockOffset> list = new ArrayList<>();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (x == 0 && z == 0) continue; // Skip center where minion stands
                list.add(new BlockOffset(x, z));
            }
        }
        return Collections.unmodifiableList(list);
    }

    /**
     * Total area expansion radius bonus (+1 per equipped Minion Expander, stacks up to +2).
     */
    public int getAreaExpansion() {
        int expansion = 0;
        CustomUpgrade u1 = getCustomUpgrade1();
        if (u1 != null) {
            expansion += u1.getAreaExpansion();
        } else if ("minion_expander".equalsIgnoreCase(upgrade1Id)) {
            expansion += 1;
        }

        CustomUpgrade u2 = getCustomUpgrade2();
        if (u2 != null) {
            expansion += u2.getAreaExpansion();
        } else if ("minion_expander".equalsIgnoreCase(upgrade2Id)) {
            expansion += 1;
        }

        return Math.clamp(expansion, 0, 2);
    }

    /**
     * Working radius around the minion: 2 (5x5, 24 blocks), 3 (7x7, 48 blocks), or 4 (9x9, 80 blocks).
     */
    public int getWorkingRadius() {
        return 2 + getAreaExpansion();
    }

    public List<BlockOffset> getWorkingOffsets() {
        int r = getWorkingRadius();
        if (r >= 4) return OFFSETS_9X9;
        if (r == 3) return OFFSETS_7X7;
        return OFFSETS_5X5;
    }

    /**
     * Returns the ground blocks (Y - 1) in the working area around the minion.
     */
    public List<Block> getWorkingGroundBlocks() {
        return getWorkingAreaBlocks();
    }

    public List<Block> getWorkingCropBlocks() {
        List<Block> blocks = new ArrayList<>();
        if (location == null || location.getWorld() == null) return blocks;

        org.bukkit.World world = location.getWorld();
        int centerX = location.getBlockX();
        int cropY = location.getBlockY();
        int centerZ = location.getBlockZ();

        for (BlockOffset offset : getWorkingOffsets()) {
            blocks.add(world.getBlockAt(centerX + offset.dx(), cropY, centerZ + offset.dz()));
        }
        return blocks;
    }

    public List<Block> getWorkingAreaBlocks() {
        List<Block> blocks = new ArrayList<>();
        if (location == null || location.getWorld() == null) return blocks;

        org.bukkit.World world = location.getWorld();
        int centerX = location.getBlockX();
        int resourceY = location.getBlockY() - 1;
        int centerZ = location.getBlockZ();

        for (BlockOffset offset : getWorkingOffsets()) {
            blocks.add(world.getBlockAt(centerX + offset.dx(), resourceY, centerZ + offset.dz()));
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

    public org.bukkit.entity.Entity getCachedArmorStand() {
        if (cachedArmorStand != null && cachedArmorStand.isValid() && !cachedArmorStand.isDead()) {
            return cachedArmorStand;
        }
        return null;
    }

    public void setCachedArmorStand(org.bukkit.entity.Entity cachedArmorStand) {
        this.cachedArmorStand = cachedArmorStand;
    }

    public org.bukkit.entity.TextDisplay getCachedHologram() {
        if (cachedHologram != null && cachedHologram.isValid() && !cachedHologram.isDead()) {
            return cachedHologram;
        }
        return null;
    }

    public void setCachedHologram(org.bukkit.entity.TextDisplay cachedHologram) {
        this.cachedHologram = cachedHologram;
    }

    public Set<UUID> getTrackedSpawnedMobs() {
        return trackedSpawnedMobs;
    }

    public void trackSpawnedMob(UUID uuid) {
        if (uuid != null) trackedSpawnedMobs.add(uuid);
    }

    public void untrackSpawnedMob(UUID uuid) {
        if (uuid != null) trackedSpawnedMobs.remove(uuid);
    }

    public boolean tryAcquireTransaction() {
        return isTransacting.compareAndSet(false, true);
    }

    public void releaseTransaction() {
        isTransacting.set(false);
    }

    public boolean isTransacting() {
        return isTransacting.get();
    }

    public long getStateRevision() {
        return stateRevision.get();
    }

    public void setStateRevision(long revision) {
        this.stateRevision.set(revision);
    }

    public long advanceStateRevision() {
        return stateRevision.incrementAndGet();
    }

    public long getSchedulingGeneration() {
        return schedulingGeneration;
    }

    public void advanceSchedulingGeneration() {
        this.schedulingGeneration++;
    }

    public boolean isFullSleeping() {
        return isFullSleeping.get();
    }

    public void sleepFull() {
        this.isFullSleeping.set(true);
        advanceSchedulingGeneration();
    }

    public void wakeUp() {
        if (this.isFullSleeping.compareAndSet(true, false)) {
            advanceStateRevision();
            advanceSchedulingGeneration();
        }
    }

    public long getLastCatchupTime() {
        return lastCatchupTime;
    }

    public void setLastCatchupTime(long lastCatchupTime) {
        this.lastCatchupTime = lastCatchupTime;
    }

    public boolean isInitialCatchupDone() {
        return initialCatchupDone;
    }

    public void setInitialCatchupDone(boolean initialCatchupDone) {
        this.initialCatchupDone = initialCatchupDone;
    }

    public Map<String, Long> getDropRemaindersMicros() {
        return dropRemaindersMicros;
    }

    public long getChannelRemainder(String channel) {
        return dropRemaindersMicros.getOrDefault(channel != null ? channel.toLowerCase() : "", 0L);
    }

    public void setChannelRemainder(String channel, long remainder) {
        if (channel != null) {
            dropRemaindersMicros.put(channel.toLowerCase(), remainder);
            advanceStateRevision();
        }
    }

    public long getFractionalProgressMicros() {
        return fractionalProgressMicros;
    }

    public void setFractionalProgressMicros(long fractionalProgressMicros) {
        this.fractionalProgressMicros = fractionalProgressMicros;
        advanceStateRevision();
    }
}
