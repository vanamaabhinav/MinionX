package com.minionx.api.event;

import com.minionx.core.Minion;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

/**
 * Fired when a player attempts to place down a minion in the world.
 */
public class MinionPlaceEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled = false;

    private final Player player;
    private final Minion minion;
    private final Location location;
    private final ItemStack minionItem;

    public MinionPlaceEvent(Player player, Minion minion, Location location, ItemStack minionItem) {
        this.player = player;
        this.minion = minion;
        this.location = location;
        this.minionItem = minionItem != null ? minionItem.clone() : null;
    }

    public Player getPlayer() {
        return player;
    }

    public Minion getMinion() {
        return minion;
    }

    public Location getLocation() {
        return location;
    }

    public ItemStack getMinionItem() {
        return minionItem != null ? minionItem.clone() : null;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
