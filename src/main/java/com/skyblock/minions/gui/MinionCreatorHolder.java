package com.skyblock.minions.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class MinionCreatorHolder implements InventoryHolder {

    private final MinionCreatorSession session;

    public MinionCreatorHolder(MinionCreatorSession session) {
        this.session = session;
    }

    public MinionCreatorSession getSession() {
        return session;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
