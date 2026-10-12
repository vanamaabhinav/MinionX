package com.minionx.api;

import com.minionx.MinionX;
import com.minionx.core.Minion;
import com.minionx.item.MinionItemFactory;
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

    private MinionXAPI() {}

    /**
     * Retrieves the placed minion occupying a specific block location.
     */
    public static Minion getMinionAt(Location loc) {
        if (MinionX.getInstance() == null || MinionX.getInstance().getMinionManager() == null) return null;
        return MinionX.getInstance().getMinionManager().getMinionAt(loc);
    }

    /**
     * Retrieves all active minions owned by a player.
     */
    public static List<Minion> getMinionsByOwner(UUID ownerId) {
        if (MinionX.getInstance() == null || MinionX.getInstance().getMinionManager() == null) return Collections.emptyList();
        return MinionX.getInstance().getMinionManager().getMinionsByOwner(ownerId);
    }

    /**
     * Retrieves all active minions across all loaded worlds.
     */
    public static Collection<Minion> getAllMinions() {
        if (MinionX.getInstance() == null || MinionX.getInstance().getMinionManager() == null) return Collections.emptyList();
        return MinionX.getInstance().getMinionManager().getAllMinions();
    }

    /**
     * Returns the count of placed minions currently active for an owner.
     */
    public static int getPlacedCount(UUID ownerId) {
        if (MinionX.getInstance() == null || MinionX.getInstance().getPlayerLimitManager() == null) return 0;
        return MinionX.getInstance().getPlayerLimitManager().getPlacedCount(ownerId);
    }

    /**
     * Returns the effective minion placement limit for a player (-1 for unlimited).
     */
    public static int getEffectiveLimit(Player player) {
        if (MinionX.getInstance() == null || MinionX.getInstance().getPlayerLimitManager() == null) return -1;
        return MinionX.getInstance().getPlayerLimitManager().getEffectiveLimit(player);
    }

    /**
     * Returns the number of available placement slots remaining for a player (-1 for unlimited).
     */
    public static int getAvailableSlots(Player player) {
        if (MinionX.getInstance() == null || MinionX.getInstance().getPlayerLimitManager() == null) return -1;
        return MinionX.getInstance().getPlayerLimitManager().getAvailableSlots(player);
    }

    /**
     * Registers a custom minion definition dynamically.
     */
    public static void registerCustomMinion(MinionDefinition def) {
        MinionRegistry.saveCustomMinion(def);
    }

    /**
     * Registers a custom fuel dynamically.
     */
    public static void registerCustomFuel(CustomFuel fuel) {
        FuelRegistry.register(fuel);
    }

    /**
     * Registers a custom upgrade dynamically.
     */
    public static void registerCustomUpgrade(CustomUpgrade upgrade) {
        UpgradeRegistry.register(upgrade);
    }

    /**
     * Gives a minion item to a player.
     */
    public static boolean giveMinion(Player player, String minionTypeOrId, int tier) {
        if (player == null || minionTypeOrId == null) return false;
        int clampedTier = Math.clamp(tier, 1, MinionTier.getMaxTier());

        MinionDefinition customDef = MinionRegistry.get(minionTypeOrId);
        if (customDef != null && MinionRegistry.isCustom(minionTypeOrId)) {
            ItemStack stack = MinionItemFactory.createCustomMinionItem(customDef, clampedTier, 0);
            player.getInventory().addItem(stack);
            return true;
        }

        MinionType type = MinionType.fromId(minionTypeOrId);
        if (type != null) {
            ItemStack stack = MinionItemFactory.createMinionItem(type, clampedTier, 0);
            player.getInventory().addItem(stack);
            return true;
        }

        return false;
    }
}
