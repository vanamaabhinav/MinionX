package com.minionx.storage;

/**
 * Deeply immutable DTO representing a serialized item inside a minion container (internal inventory or linked storage).
 */
public record SerializedItemRecord(
        String containerType,
        int slotIndex,
        String itemData
) {
    public SerializedItemRecord {
        if (containerType == null) {
            containerType = "INVENTORY";
        }
    }
}
