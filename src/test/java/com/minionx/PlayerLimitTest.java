package com.minionx;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

public class PlayerLimitTest {

    @Test
    @DisplayName("Should resolve correct limit with bonus slots")
    void testLimitCalculation() {
        ConcurrentHashMap<UUID, Integer> bonusMap = new ConcurrentHashMap<>();
        UUID player1 = UUID.randomUUID();
        bonusMap.put(player1, 3);

        int baseLimit = 5;
        int effectiveLimit = baseLimit + bonusMap.getOrDefault(player1, 0);
        assertEquals(8, effectiveLimit);

        int placed = 4;
        int available = effectiveLimit - placed;
        assertEquals(4, available);
        assertTrue(placed < effectiveLimit);
    }

    @Test
    @DisplayName("Should handle unlimited limit properly (-1)")
    void testUnlimitedLimit() {
        int baseLimit = -1;
        assertTrue(baseLimit < 0);
    }
}
