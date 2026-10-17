package com.minionx.storage;

import java.util.UUID;

/**
 * Represents a durable pending minion operation (e.g. pickup intent).
 * Persisted to disk/database before performing mutations to guarantee zero item loss or duplication.
 */
public record PendingOperation(
        String operationId,
        UUID minionId,
        UUID playerUuid,
        String state,
        long createdAt,
        String payload
) {
    public PendingOperation(String operationId, UUID minionId, UUID playerUuid, String state, long createdAt) {
        this(operationId, minionId, playerUuid, state, createdAt, null);
    }
}
