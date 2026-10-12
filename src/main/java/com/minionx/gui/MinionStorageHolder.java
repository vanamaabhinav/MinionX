package com.minionx.gui;

import com.minionx.core.Minion;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * InventoryHolder identifying a Minion Storage GUI.
 */
public class MinionStorageHolder implements InventoryHolder {

    private final Minion minion;

    public MinionStorageHolder(Minion minion) {
        this.minion = minion;
    }

    public Minion getMinion() {
        return minion;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
