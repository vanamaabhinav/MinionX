package com.minionx.api.event;

import com.minionx.api.CustomFuel;
import com.minionx.core.Minion;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired when fuel is equipped or when temporary fuel burns out on a minion.
 */
public class MinionFuelConsumeEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled = false;

    private final Minion minion;
    private final CustomFuel fuel;
    private final boolean burnedOut;

    public MinionFuelConsumeEvent(Minion minion, CustomFuel fuel, boolean burnedOut) {
        this.minion = minion;
        this.fuel = fuel;
        this.burnedOut = burnedOut;
    }

    public Minion getMinion() {
        return minion;
    }

    public CustomFuel getFuel() {
        return fuel;
    }

    public boolean isBurnedOut() {
        return burnedOut;
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
