package com.minionx.api;

import com.minionx.MinionX;
import com.minionx.core.Minion;
import com.minionx.item.MinionItemFactory;
import com.minionx.quarantine.QuarantineManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Public developer API for MinionX.
 * Allows other plugins to query placed minions, check limits, give minions,
 * and register custom minion definitions, fuels, and upgrades.
 */
public final class MinionXAPI {

    private MinionXAPI() {
    }

    /**
     * Retrieves a minion by its unique identifier.
     *
     * @param minionId the UUID of the minion
     * @return the Minion instance, or null if not found
     */
    public static Minion getMinion(UUID minionId) {
        if (minionId == null || MinionX.getInstance() == null || MinionX.getInstance().getMinionManager() == null)
            return null;
        return MinionX.getInstance().getMinionManager().getMinionById(minionId);
    }

    /**
     * Retrieves the placed minion occupying a specific block location.
     *
     * @param loc the location to inspect
     * @return the Minion instance, or null if none is placed here
     */
    public static Minion getMinionAt(Location loc) {
        if (MinionX.getInstance() == null || MinionX.getInstance().getMinionManager() == null)
            return null;
        return MinionX.getInstance().getMinionManager().getMinionAt(loc);
    }

    /**
     * Retrieves all active minions owned by a player.
     *
     * @param ownerId UUID of the owning player
     * @return unmodifiable list of minions owned by the player
     */
    public static List<Minion> getMinionsByOwner(UUID ownerId) {
        if (MinionX.getInstance() == null || MinionX.getInstance().getMinionManager() == null)
            return Collections.emptyList();
        return MinionX.getInstance().getMinionManager().getMinionsByOwner(ownerId);
    }

    /**
     * Retrieves all active minions across all loaded worlds.
     *
     * @return collection of all active minions
     */
    public static Collection<Minion> getAllMinions() {
        if (MinionX.getInstance() == null || MinionX.getInstance().getMinionManager() == null)
            return Collections.emptyList();
        return MinionX.getInstance().getMinionManager().getAllMinions();
    }

    /**
     * Returns the count of placed minions currently active for an owner.
     *
     * @param ownerId UUID of the player
     * @return count of active placed minions
     */
    public static int getPlacedCount(UUID ownerId) {
        if (MinionX.getInstance() == null || MinionX.getInstance().getPlayerLimitManager() == null)
            return 0;
        return MinionX.getInstance().getPlayerLimitManager().getPlacedCount(ownerId);
    }

    /**
     * Returns the effective minion placement limit for a player (-1 for unlimited).
     *
     * @param player the player to check
     * @return maximum minion limit, or -1 if unlimited
     */
    public static int getEffectiveLimit(Player player) {
        if (MinionX.getInstance() == null || MinionX.getInstance().getPlayerLimitManager() == null)
            return -1;
        return MinionX.getInstance().getPlayerLimitManager().getEffectiveLimit(player);
    }

    /**
     * Returns the number of available placement slots remaining for a player (-1
     * for unlimited).
     *
     * @param player the player to check
     * @return number of remaining slots, or -1 if unlimited
     */
    public static int getAvailableSlots(Player player) {
        if (MinionX.getInstance() == null || MinionX.getInstance().getPlayerLimitManager() == null)
            return -1;
        return MinionX.getInstance().getPlayerLimitManager().getAvailableSlots(player);
    }

    /**
     * Registers a custom minion definition dynamically.
     *
     * @param def the minion definition to register
     */
    public static void registerCustomMinion(MinionDefinition def) {
        MinionRegistry.saveCustomMinion(def);
    }

    /**
     * Registers a custom fuel dynamically.
     *
     * @param fuel the custom fuel to register
     */
    public static void registerCustomFuel(CustomFuel fuel) {
        FuelRegistry.register(fuel);
    }

    /**
     * Registers a custom upgrade dynamically.
     *
     * @param upgrade the custom upgrade to register
     */
    public static void registerCustomUpgrade(CustomUpgrade upgrade) {
        UpgradeRegistry.register(upgrade);
    }

    /**
     * Registers a custom minion storage definition dynamically.
     *
     * @param storage the custom minion storage specification
     */
    public static void registerCustomStorage(CustomMinionStorage storage) {
        MinionStorageRegistry.register(storage);
    }

    /**
     * Registers a custom cosmetic minion skin dynamically.
     *
     * @param skin the custom minion skin
     */
    public static void registerCustomSkin(MinionSkin skin) {
        MinionSkinRegistry.register(skin);
    }

    /**
     * Checks whether MinionX is currently running in emergency quarantine mode.
     *
     * @return true if the system is quarantined, false under normal operation
     */
    public static boolean isQuarantined() {
        return QuarantineManager.isQuarantined();
    }

    /**
     * Checks whether an ItemStack is a valid MinionX minion item.
     *
     * @param item the item stack to check
     * @return true if the item represents a minion, false otherwise
     */
    public static boolean isMinionItem(ItemStack item) {
        return MinionItemFactory.isMinionItem(item);
    }

    /**
     * Creates an ItemStack representation of a minion without adding it to an
     * inventory.
     *
     * @param minionTypeOrId internal minion type or custom definition id
     * @param tier           minion tier (1 to maximum configured tier)
     * @return the configured minion ItemStack, or null if the minion definition was
     *         not found
     */
    public static ItemStack createMinionItem(String minionTypeOrId, int tier) {
        if (minionTypeOrId == null || org.bukkit.Bukkit.getServer() == null)
            return null;
        int clampedTier = Math.max(1, Math.min(MinionTier.getMaxTier(), tier));

        MinionDefinition customDef = MinionRegistry.get(minionTypeOrId);
        if (customDef != null && MinionRegistry.isCustom(minionTypeOrId)) {
            return MinionItemFactory.createCustomMinionItem(customDef, clampedTier, 0);
        }

        MinionType type = MinionType.fromId(minionTypeOrId);
        if (type != null) {
            return MinionItemFactory.createMinionItem(type, clampedTier, 0);
        }

        return null;
    }

    /**
     * Creates an ItemStack representation of a minion for a standard MinionType.
     *
     * @param type the standard MinionType
     * @param tier minion tier (1 to maximum configured tier)
     * @return the configured minion ItemStack, or null if type is null
     */
    public static ItemStack createMinionItem(MinionType type, int tier) {
        if (type == null || org.bukkit.Bukkit.getServer() == null)
            return null;
        int clampedTier = Math.max(1, Math.min(MinionTier.getMaxTier(), tier));
        return MinionItemFactory.createMinionItem(type, clampedTier, 0);
    }

    /**
     * Gives a minion item to a player.
     *
     * @param player         target player
     * @param minionTypeOrId minion type or custom definition id
     * @param tier           tier number
     * @return true if item was successfully created and delivered, false otherwise
     */
    public static boolean giveMinion(Player player, String minionTypeOrId, int tier) {
        if (player == null || minionTypeOrId == null)
            return false;
        ItemStack stack = createMinionItem(minionTypeOrId, tier);
        if (stack != null) {
            player.getInventory().addItem(stack);
            return true;
        }
        return false;
    }
}
