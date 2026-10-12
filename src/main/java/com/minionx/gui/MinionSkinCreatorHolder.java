package com.minionx.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class MinionSkinCreatorHolder implements InventoryHolder {

    private final MinionSkinCreatorSession session;

    public MinionSkinCreatorHolder(MinionSkinCreatorSession session) {
        this.session = session;
    }

    public MinionSkinCreatorSession getSession() {
        return session;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
