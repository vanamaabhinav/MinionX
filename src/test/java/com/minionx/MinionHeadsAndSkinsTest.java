package com.minionx;

import com.minionx.api.*;
import com.minionx.gui.OperatorHolder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

public class MinionHeadsAndSkinsTest {

    @BeforeAll
    static void setUp() {
        MinionTier.load(null);
        MinionRegistry.load();
        MinionSkinRegistry.loadBuiltinSkins();
    }

    @Test
    @DisplayName("Should verify Tier XII is enabled with correct delay and storage")
    void testTierXIIProgression() {
        assertEquals(12, MinionTier.getMaxTier());
        assertEquals(6.0, MinionTier.getActionDelay(12));
        assertEquals(15, MinionTier.getStorageSlots(12));
        assertEquals(960, MinionTier.getMaxCapacity(12));
        assertEquals("XII", MinionTier.toRoman(12));
    }

    @Test
    @DisplayName("Should verify all 37 minions have custom heads across tiers")
    void testAuthenticMinionHeadsAllTiers() {
        Collection<MinionDefinition> definitions = MinionRegistry.getAll();
        assertTrue(definitions.size() >= 37, "Expected at least 37 builtin minions, found " + definitions.size());

        for (MinionDefinition def : definitions) {
            String defaultHead = def.getDefaultSkullTexture();
            assertNotNull(defaultHead, "Default head texture should not be null for " + def.getId());
            assertFalse(defaultHead.isEmpty(), "Default head texture should not be empty for " + def.getId());

            // Check tier 1 head
            String t1Head = def.getSkullTexture(1);
            assertNotNull(t1Head, "Tier 1 skull should exist for " + def.getId());
            assertFalse(t1Head.isEmpty(), "Tier 1 skull should not be empty for " + def.getId());

            // Check tier 11 head
            String t11Head = def.getSkullTexture(11);
            assertNotNull(t11Head, "Tier 11 skull should exist for " + def.getId());
            assertFalse(t11Head.isEmpty(), "Tier 11 skull should not be empty for " + def.getId());
        }

        // Specifically check Cobblestone minion tier heads
        MinionDefinition cobble = MinionRegistry.get("cobblestone");
        assertNotNull(cobble);
        String cobbleT1 = cobble.getSkullTexture(1);
        String cobbleT11 = cobble.getSkullTexture(11);
        String cobbleT12 = cobble.getSkullTexture(12);

        assertNotNull(cobbleT1);
        assertNotNull(cobbleT11);
        assertNotNull(cobbleT12);

        // Different tiers have different skull textures
        assertNotEquals(cobbleT1, cobbleT11, "Cobblestone Tier 1 and Tier 11 should have distinct heads");
    }

    @Test
    @DisplayName("Should verify builtin Minion Skins are loaded properly")
    void testMinionSkinRegistry() {
        Collection<MinionSkin> skins = MinionSkinRegistry.getAll();
        assertTrue(skins.size() >= 50, "Expected at least 50 builtin minion skins, found " + skins.size());

        MinionSkin santa = MinionSkinRegistry.get("santa");
        assertNotNull(santa, "Santa minion skin should exist");
        assertTrue(santa.getDisplayName().toLowerCase().contains("santa"));
        assertNotNull(santa.getSkullTexture(), "Santa skull texture should not be null");
        assertFalse(santa.getSkullTexture().isEmpty());

        MinionSkin snowman = MinionSkinRegistry.get("snowman");
        assertNotNull(snowman, "Snowman minion skin should exist");

        MinionSkin robot = MinionSkinRegistry.get("robot");
        assertNotNull(robot, "Robot minion skin should exist");
    }

    @Test
    @DisplayName("Should verify OperatorHolder supports SKINS_CATALOG")
    void testOperatorHolderSkinsView() {
        OperatorHolder holder = new OperatorHolder(OperatorHolder.ViewType.SKINS_CATALOG, 0);
        assertEquals(OperatorHolder.ViewType.SKINS_CATALOG, holder.getViewType());
        assertEquals(0, holder.getContext());
    }
}
