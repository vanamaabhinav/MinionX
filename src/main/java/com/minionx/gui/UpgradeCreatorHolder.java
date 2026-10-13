package com.minionx.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * InventoryHolder implementation for the Upgrade Creator Studio GUI.
 */
public class UpgradeCreatorHolder implements InventoryHolder {

    private final UpgradeCreatorSession session;

    public UpgradeCreatorHolder(UpgradeCreatorSession session) {
        this.session = session;
    }

    public UpgradeCreatorSession getSession() {
        return session;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
