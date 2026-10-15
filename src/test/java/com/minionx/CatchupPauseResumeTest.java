package com.minionx;

import com.minionx.api.MinionState;
import com.minionx.api.MinionType;
import com.minionx.core.Minion;
import com.minionx.core.OfflineCoordinator;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class CatchupPauseResumeTest {

    @Test
    public void testPausedIntervalDoesNotGenerateResourcesAndResumesAccurately() {
        Minion minion = new Minion(UUID.randomUUID(), UUID.randomUUID(), MinionType.COBBLESTONE, 1, null);
        minion.setState(MinionState.IDLE);

        double cycleDelay = minion.getEffectiveActionDelay() * 2.0; // Place + Mine cycle time in seconds
        long cycleDelayMillis = (long) (cycleDelay * 1000.0);
        assertTrue(cycleDelayMillis > 0, "Cycle delay must be positive");

        // 1. Simulate minion becoming FULL and left idle for 6 hours
        minion.setState(MinionState.FULL);
        long sixHoursAgo = System.currentTimeMillis() - (6 * 3600 * 1000L);
        minion.setLastActionTime(sixHoursAgo);
        minion.setLastCatchupTime(0L);

        // 2. Offline catch-up attempt while FULL
        int fullCatchupProduced = OfflineCoordinator.catchUpIfNeeded(minion);
        assertEquals(0, fullCatchupProduced, "Minion in FULL state must never produce resources");

        // The elapsed-time anchor MUST advance to current time so paused interval is not credited
        long anchorAfterPause = minion.getLastActionTime();
        assertTrue(anchorAfterPause > sixHoursAgo, "Elapsed-time anchor must advance forward past the paused interval");
        assertTrue(Math.abs(System.currentTimeMillis() - anchorAfterPause) < 2000L, "Anchor should be set to current time");

        // 3. Player frees space in the minion and state resumes to IDLE
        minion.getInventory().clear();
        minion.setState(MinionState.IDLE);

        // 4. Simulate exactly 4 cycles passing in productive offline time
        long productiveWindowMillis = cycleDelayMillis * 4;
        minion.setLastActionTime(System.currentTimeMillis() - productiveWindowMillis);
        minion.setLastCatchupTime(0L); // Reset debounce

        int resumedProduced = OfflineCoordinator.catchUpIfNeeded(minion);

        // Verify produced resources match only the productive window (4 cycles = 4 items for cobblestone)
        assertEquals(4, resumedProduced, "Resumed minion must produce exactly the items due during active period");
        assertEquals(4, minion.getTotalStoredCount());

        // 5. Verify Idempotence: Immediate subsequent catch-up generates 0 items and never double-counts
        minion.setLastCatchupTime(0L); // Bypass 1s debounce to test elapsed time calculation
        int immediateProduced = OfflineCoordinator.catchUpIfNeeded(minion);
        assertEquals(0, immediateProduced, "Immediate subsequent catch-up must produce 0 items and never double count");

        // Inventory should still have exactly 4 items
        assertEquals(4, minion.getTotalStoredCount());
    }
}
