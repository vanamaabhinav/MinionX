package com.minionx.api.event;

import com.minionx.core.Minion;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired when a minion is quarantined or unquarantined by the emergency anti-dupe failsafe.
 */
public class MinionQuarantineEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Minion minion;
    private final boolean quarantined;
    private final String reason;

    public MinionQuarantineEvent(Minion minion, boolean quarantined, String reason) {
        this.minion = minion;
        this.quarantined = quarantined;
        this.reason = reason;
    }

    public Minion getMinion() {
        return minion;
    }

    public boolean isQuarantined() {
        return quarantined;
    }

    public String getReason() {
        return reason;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
