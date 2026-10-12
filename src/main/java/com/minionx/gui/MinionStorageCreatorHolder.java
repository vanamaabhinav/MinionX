package com.minionx.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class MinionStorageCreatorHolder implements InventoryHolder {

    private final MinionStorageCreatorSession session;

    public MinionStorageCreatorHolder(MinionStorageCreatorSession session) {
        this.session = session;
    }

    public MinionStorageCreatorSession getSession() {
        return session;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
