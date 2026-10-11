package com.skyblock.minions.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Custom InventoryHolder identifying the MinionX Operator GUI.
 */
public class OperatorHolder implements InventoryHolder {

    public enum ViewType {
        MAIN,
        MINION_TIERS
    }

    private final ViewType viewType;
    private final Object context;

    public OperatorHolder(ViewType viewType) {
        this(viewType, null);
    }

    public OperatorHolder(ViewType viewType, Object context) {
        this.viewType = viewType;
        this.context = context;
    }

    public ViewType getViewType() {
        return viewType;
    }

    public Object getContext() {
        return context;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
