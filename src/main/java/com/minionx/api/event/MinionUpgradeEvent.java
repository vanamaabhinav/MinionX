package com.minionx.api.event;

import com.minionx.core.Minion;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired when a player upgrades a minion to a higher tier.
 */
public class MinionUpgradeEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled = false;

    private final Player player;
    private final Minion minion;
    private final int oldTier;
    private final int newTier;

    public MinionUpgradeEvent(Player player, Minion minion, int oldTier, int newTier) {
        this.player = player;
        this.minion = minion;
        this.oldTier = oldTier;
        this.newTier = newTier;
    }

    public Player getPlayer() {
        return player;
    }

    public Minion getMinion() {
        return minion;
    }

    public int getOldTier() {
        return oldTier;
    }

    public int getNewTier() {
        return newTier;
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
