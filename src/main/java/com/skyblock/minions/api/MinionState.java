package com.skyblock.minions.api;

/**
 * Represents the current operational state of a minion.
 */
public enum MinionState {
    IDLE("§a● Working"),
    PLACING("§a● Spawning"),
    MINING("§a● Mining"),
    FULL("§c● Inventory Full!"),
    NO_SPACE("§c● Need 5x5 Area!");

    private final String displayStatus;

    MinionState(String displayStatus) {
        this.displayStatus = displayStatus;
    }

    public String getDisplayStatus() {
        return displayStatus;
    }
}
