package com.skyblock.minions.api;

import com.skyblock.minions.util.ColorUtil;

/**
 * Represents the current operational state of a minion.
 */
public enum MinionState {
    IDLE("&aWorking"),
    PLACING("&aSpawning"),
    MINING("&aMining"),
    PARTIALLY_OBSTRUCTED("&ePartially Obstructed"),
    FULL("&cInventory Full!"),
    NO_SPACE("&cNeed 5x5 Area!");

    private final String defaultStatus;

    MinionState(String defaultStatus) {
        this.defaultStatus = defaultStatus;
    }

    public String getDisplayStatus() {
        return ColorUtil.color(defaultStatus);
    }
}