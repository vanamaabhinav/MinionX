package com.minionx.gui;

import com.minionx.core.Minion;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * InventoryHolder identifying a Minion Storage GUI.
 */
public class MinionStorageHolder implements InventoryHolder {

    private final Minion minion;
    private final Runnable backAction;

    public MinionStorageHolder(Minion minion) {
        this(minion, null);
    }

    public MinionStorageHolder(Minion minion, Runnable backAction) {
        this.minion = minion;
        this.backAction = backAction;
    }

    public Minion getMinion() {
        return minion;
    }

    public Runnable getBackAction() {
        return backAction;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
