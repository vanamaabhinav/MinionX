package com.minionx.api.event;

import com.minionx.core.Minion;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired when a player links or unlinks an external chest/storage container to a minion.
 */
public class MinionStorageLinkEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled = false;

    private final Player player;
    private final Minion minion;
    private final Location storageLocation;
    private final boolean linking; // true = link, false = unlink

    public MinionStorageLinkEvent(Player player, Minion minion, Location storageLocation, boolean linking) {
        this.player = player;
        this.minion = minion;
        this.storageLocation = storageLocation;
        this.linking = linking;
    }

    public Player getPlayer() {
        return player;
    }

    public Minion getMinion() {
        return minion;
    }

    public Location getStorageLocation() {
        return storageLocation;
    }

    public boolean isLinking() {
        return linking;
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
