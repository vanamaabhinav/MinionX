package com.skyblock.minions;

import com.skyblock.minions.visual.MinionVisual;
import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class MinionArmorVisualTest {

    @Test
    @DisplayName("Should resolve full matching armor set for Leather armor")
    void testLeatherArmorResolution() {
        assertEquals(Material.LEATHER_CHESTPLATE, MinionVisual.getMatchingChestplate(Material.LEATHER_CHESTPLATE));
        assertEquals(Material.LEATHER_LEGGINGS, MinionVisual.getMatchingLeggings(Material.LEATHER_CHESTPLATE));
        assertEquals(Material.LEATHER_BOOTS, MinionVisual.getMatchingBoots(Material.LEATHER_CHESTPLATE));
    }

    @Test
    @DisplayName("Should resolve full matching armor set (Chestplate, Leggings, Boots) for Iron armor")
    void testIronArmorResolution() {
        assertEquals(Material.IRON_CHESTPLATE, MinionVisual.getMatchingChestplate(Material.IRON_CHESTPLATE));
        assertEquals(Material.IRON_LEGGINGS, MinionVisual.getMatchingLeggings(Material.IRON_CHESTPLATE));
        assertEquals(Material.IRON_BOOTS, MinionVisual.getMatchingBoots(Material.IRON_CHESTPLATE));
    }

    @Test
    @DisplayName("Should resolve full matching armor set (Chestplate, Leggings, Boots) for Diamond armor")
    void testDiamondArmorResolution() {
        assertEquals(Material.DIAMOND_CHESTPLATE, MinionVisual.getMatchingChestplate(Material.DIAMOND_CHESTPLATE));
        assertEquals(Material.DIAMOND_LEGGINGS, MinionVisual.getMatchingLeggings(Material.DIAMOND_CHESTPLATE));
        assertEquals(Material.DIAMOND_BOOTS, MinionVisual.getMatchingBoots(Material.DIAMOND_CHESTPLATE));
    }

    @Test
    @DisplayName("Should resolve full matching armor set (Chestplate, Leggings, Boots) for Netherite armor")
    void testNetheriteArmorResolution() {
        assertEquals(Material.NETHERITE_CHESTPLATE, MinionVisual.getMatchingChestplate(Material.NETHERITE_CHESTPLATE));
        assertEquals(Material.NETHERITE_LEGGINGS, MinionVisual.getMatchingLeggings(Material.NETHERITE_CHESTPLATE));
        assertEquals(Material.NETHERITE_BOOTS, MinionVisual.getMatchingBoots(Material.NETHERITE_CHESTPLATE));
    }

    @Test
    @DisplayName("Should resolve full matching armor set (Chestplate, Leggings, Boots) for Golden armor")
    void testGoldenArmorResolution() {
        assertEquals(Material.GOLDEN_CHESTPLATE, MinionVisual.getMatchingChestplate(Material.GOLDEN_CHESTPLATE));
        assertEquals(Material.GOLDEN_LEGGINGS, MinionVisual.getMatchingLeggings(Material.GOLDEN_CHESTPLATE));
        assertEquals(Material.GOLDEN_BOOTS, MinionVisual.getMatchingBoots(Material.GOLDEN_CHESTPLATE));
    }

    @Test
    @DisplayName("Should resolve full matching armor set (Chestplate, Leggings, Boots) for Chainmail armor")
    void testChainmailArmorResolution() {
        assertEquals(Material.CHAINMAIL_CHESTPLATE, MinionVisual.getMatchingChestplate(Material.CHAINMAIL_CHESTPLATE));
        assertEquals(Material.CHAINMAIL_LEGGINGS, MinionVisual.getMatchingLeggings(Material.CHAINMAIL_CHESTPLATE));
        assertEquals(Material.CHAINMAIL_BOOTS, MinionVisual.getMatchingBoots(Material.CHAINMAIL_CHESTPLATE));
    }

    @Test
    @DisplayName("Should deduce full set even if input armor material was Leggings or Boots")
    void testArmorResolutionFromLeggingsOrBootsInput() {
        assertEquals(Material.IRON_CHESTPLATE, MinionVisual.getMatchingChestplate(Material.IRON_LEGGINGS));
        assertEquals(Material.IRON_LEGGINGS, MinionVisual.getMatchingLeggings(Material.IRON_LEGGINGS));
        assertEquals(Material.IRON_BOOTS, MinionVisual.getMatchingBoots(Material.IRON_LEGGINGS));

        assertEquals(Material.DIAMOND_CHESTPLATE, MinionVisual.getMatchingChestplate(Material.DIAMOND_BOOTS));
        assertEquals(Material.DIAMOND_LEGGINGS, MinionVisual.getMatchingLeggings(Material.DIAMOND_BOOTS));
        assertEquals(Material.DIAMOND_BOOTS, MinionVisual.getMatchingBoots(Material.DIAMOND_BOOTS));
    }

    @Test
    @DisplayName("Should safely handle null or AIR materials")
    void testAirOrNullInput() {
        assertNull(MinionVisual.getMatchingChestplate(null));
        assertNull(MinionVisual.getMatchingLeggings(null));
        assertNull(MinionVisual.getMatchingBoots(null));

        assertNull(MinionVisual.getMatchingChestplate(Material.AIR));
        assertNull(MinionVisual.getMatchingLeggings(Material.AIR));
        assertNull(MinionVisual.getMatchingBoots(Material.AIR));
    }
}
