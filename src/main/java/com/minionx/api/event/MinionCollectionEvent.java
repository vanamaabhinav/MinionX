package com.minionx.api.event;

import com.minionx.core.Minion;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Fired when a player collects stored items or XP from a minion.
 */
public class MinionCollectionEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled = false;

    private final Player player;
    private final Minion minion;
    private final List<ItemStack> collectedItems;
    private int xpCollected;

    public MinionCollectionEvent(Player player, Minion minion, List<ItemStack> collectedItems, int xpCollected) {
        this.player = player;
        this.minion = minion;
        this.collectedItems = collectedItems != null ? new ArrayList<>(collectedItems) : new ArrayList<>();
        this.xpCollected = xpCollected;
    }

    public Player getPlayer() {
        return player;
    }

    public Minion getMinion() {
        return minion;
    }

    public List<ItemStack> getCollectedItems() {
        return collectedItems;
    }

    public int getXpCollected() {
        return xpCollected;
    }

    public void setXpCollected(int xpCollected) {
        this.xpCollected = Math.max(0, xpCollected);
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
