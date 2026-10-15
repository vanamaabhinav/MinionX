package com.minionx.storage;

import java.util.UUID;

/**
 * Represents an atomic minion mutation inside the coalescing write map.
 * Enforces monotonic state revision ordering and explicit tombstoning to prevent
 * stale writes or delayed autosaves from resurrecting deleted minions.
 */
public record MinionMutation(
        UUID minionId,
        long stateRevision,
        DatabaseStorage.MinionSnapshot snapshot,
        boolean isTombstone,
        long timestamp
) {
    public static MinionMutation of(DatabaseStorage.MinionSnapshot snapshot, long revision) {
        return new MinionMutation(snapshot.id(), revision, snapshot, false, System.currentTimeMillis());
    }

    public static MinionMutation tombstone(UUID minionId, long revision) {
        return new MinionMutation(minionId, revision, null, true, System.currentTimeMillis());
    }
}
