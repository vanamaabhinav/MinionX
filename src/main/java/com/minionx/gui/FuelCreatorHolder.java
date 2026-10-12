package com.minionx.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class FuelCreatorHolder implements InventoryHolder {

    private final FuelCreatorSession session;

    public FuelCreatorHolder(FuelCreatorSession session) {
        this.session = session;
    }

    public FuelCreatorSession getSession() {
        return session;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
