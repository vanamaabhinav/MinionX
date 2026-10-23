package com.minionx.api;

import com.minionx.util.ColorUtil;

/**
 * Represents the current operational state of a minion.
 */
public enum MinionState {
    IDLE("&aWorking"),
    PLACING("&aSpawning"),
    MINING("&aMining"),
    PARTIALLY_OBSTRUCTED("&ePartially Obstructed"),
    FULL("&cInventory Full!"),
    NO_SPACE("&cNeed 5x5 Area!"),
    NO_MOBS_IN_RANGE("&cNo Mobs in Range!");

    private final String defaultStatus;

    MinionState(String defaultStatus) {
        this.defaultStatus = defaultStatus;
    }

    public String getDisplayStatus() {
        return ColorUtil.color(defaultStatus);
    }
}