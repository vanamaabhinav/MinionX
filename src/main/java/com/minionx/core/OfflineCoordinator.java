package com.minionx.core;

import com.minionx.api.MinionState;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Unified, thread-safe, idempotent coordinator for offline catch-up calculations.
 * Guarantees exactly-once processing across server startup, chunk-load, and player interaction.
 */
public class OfflineCoordinator {

    private static final Set<UUID> activeCatchupMinions = ConcurrentHashMap.newKeySet();

    /**
     * Executes offline catch-up if needed, protected by an in-progress guard,
     * paused-state anchor advancement, and debounce tracking.
     *
     * @param minion The minion instance to evaluate
     * @return Number of items produced during catch-up, or 0 if skipped/full
     */
    public static int catchUpIfNeeded(Minion minion) {
        if (minion == null) return 0;

        // 1. Advance paused-time anchor when full or blocked so paused intervals are never credited
        if (minion.getState() == MinionState.FULL || minion.getState() == MinionState.NO_SPACE) {
            minion.setLastActionTime(System.currentTimeMillis());
            return 0;
        }

        // 2. Debounce guard: prevent redundant immediate invocations within 1000ms
        long now = System.currentTimeMillis();
        if (now - minion.getLastCatchupTime() < 1000L) {
            return 0;
        }

        // 3. In-progress atomic guard: prevent concurrent execution across event listeners
        if (!activeCatchupMinions.add(minion.getMinionId())) {
            return 0;
        }

        try {
            minion.setLastCatchupTime(now);
            return OfflineCalculator.processOfflineCatchup(minion);
        } finally {
            activeCatchupMinions.remove(minion.getMinionId());
        }
    }
}
