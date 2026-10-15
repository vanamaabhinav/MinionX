package com.minionx.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class FuelPresetSelectorHolder implements InventoryHolder {

    private final FuelCreatorSession session;

    public FuelPresetSelectorHolder(FuelCreatorSession session) {
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
