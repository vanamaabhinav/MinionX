package com.minionx.gui;

import com.minionx.core.Minion;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Custom InventoryHolder linking a chest GUI to its corresponding Minion instance.
 */
public class MinionHolder implements InventoryHolder {

    private final Minion minion;

    public MinionHolder(Minion minion) {
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
