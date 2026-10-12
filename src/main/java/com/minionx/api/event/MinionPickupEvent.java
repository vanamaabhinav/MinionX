package com.minionx.api.event;

import com.minionx.core.Minion;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired when a player attempts to pick up / retrieve a placed minion.
 */
public class MinionPickupEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled = false;

    private final Player player;
    private final Minion minion;

    public MinionPickupEvent(Player player, Minion minion) {
        this.player = player;
        this.minion = minion;
    }

    public Player getPlayer() {
        return player;
    }

    public Minion getMinion() {
        return minion;
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
