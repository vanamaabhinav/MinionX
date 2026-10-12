package com.minionx.api.event;

import com.minionx.core.Minion;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Fired whenever a Minion harvests resources (mining, chopping, farming, or killing a mob).
 * Plugins can inspect or modify drops, change awarded XP, or cancel the harvest entirely.
 */
public class MinionHarvestEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled = false;

    private final Minion minion;
    private List<ItemStack> drops;
    private int xpGained;

    public MinionHarvestEvent(Minion minion, List<ItemStack> drops, int xpGained) {
        this.minion = minion;
        this.drops = drops != null ? new ArrayList<>(drops) : new ArrayList<>();
        this.xpGained = xpGained;
    }

    public Minion getMinion() {
        return minion;
    }

    public List<ItemStack> getDrops() {
        return drops;
    }

    public void setDrops(List<ItemStack> drops) {
        this.drops = drops != null ? new ArrayList<>(drops) : new ArrayList<>();
    }

    public int getXpGained() {
        return xpGained;
    }

    public void setXpGained(int xpGained) {
        this.xpGained = Math.max(0, xpGained);
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
