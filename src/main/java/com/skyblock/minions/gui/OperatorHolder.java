package com.skyblock.minions.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Custom InventoryHolder identifying the MinionX Operator GUI.
 */
public class OperatorHolder implements InventoryHolder {

    public enum ViewType {
        MAIN,
        MINIONS_CATALOG,
        MINION_TIERS,
        FUELS_CATALOG,
        UPGRADES_CATALOG,
        KITS_CATALOG,
        SKINS_CATALOG
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