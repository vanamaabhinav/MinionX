package com.skyblock.minions;

import com.skyblock.minions.api.MinionTier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class MinionTierTest {

    @Test
    @DisplayName("Should correctly return action delay for each tier")
    void testActionDelays() {
        assertEquals(14.0, MinionTier.getActionDelay(1));
        assertEquals(14.0, MinionTier.getActionDelay(2));
        assertEquals(12.5, MinionTier.getActionDelay(3));
        assertEquals(12.5, MinionTier.getActionDelay(4));
        assertEquals(11.0, MinionTier.getActionDelay(5));
        assertEquals(11.0, MinionTier.getActionDelay(6));
        assertEquals(9.5, MinionTier.getActionDelay(7));
        assertEquals(9.5, MinionTier.getActionDelay(8));
        assertEquals(8.0, MinionTier.getActionDelay(9));
        assertEquals(8.0, MinionTier.getActionDelay(10));
        assertEquals(7.0, MinionTier.getActionDelay(11));
    }

    @Test
    @DisplayName("Should correctly return storage slots progression")
    void testStorageSlots() {
        assertEquals(1, MinionTier.getStorageSlots(1));
        assertEquals(3, MinionTier.getStorageSlots(2));
        assertEquals(3, MinionTier.getStorageSlots(3));
        assertEquals(6, MinionTier.getStorageSlots(4));
        assertEquals(6, MinionTier.getStorageSlots(5));
        assertEquals(9, MinionTier.getStorageSlots(6));
        assertEquals(9, MinionTier.getStorageSlots(7));
        assertEquals(12, MinionTier.getStorageSlots(8));
        assertEquals(12, MinionTier.getStorageSlots(9));
        assertEquals(15, MinionTier.getStorageSlots(10));
        assertEquals(15, MinionTier.getStorageSlots(11));
    }

    @Test
    @DisplayName("Should calculate max item capacity correctly")
    void testMaxCapacity() {
        assertEquals(64, MinionTier.getMaxCapacity(1));
        assertEquals(192, MinionTier.getMaxCapacity(2));
        assertEquals(384, MinionTier.getMaxCapacity(4));
        assertEquals(576, MinionTier.getMaxCapacity(6));
        assertEquals(768, MinionTier.getMaxCapacity(8));
        assertEquals(960, MinionTier.getMaxCapacity(10));
        assertEquals(960, MinionTier.getMaxCapacity(11));
    }

    @Test
    @DisplayName("Should format Roman numerals accurately")
    void testRomanNumerals() {
        assertEquals("I", MinionTier.toRoman(1));
        assertEquals("V", MinionTier.toRoman(5));
        assertEquals("X", MinionTier.toRoman(10));
        assertEquals("XI", MinionTier.toRoman(11));
    }

    @Test
    @DisplayName("Should handle upgrade costs properly")
    void testUpgradeCosts() {
        assertEquals(80, MinionTier.getUpgradeCost(1));
        assertEquals(160, MinionTier.getUpgradeCost(2));
        assertEquals(320, MinionTier.getUpgradeCost(3));
        assertEquals(640, MinionTier.getUpgradeCost(4));
        assertEquals(1280, MinionTier.getUpgradeCost(5));
        assertEquals(0, MinionTier.getUpgradeCost(11)); // Max tier has no upgrade cost
    }
}
