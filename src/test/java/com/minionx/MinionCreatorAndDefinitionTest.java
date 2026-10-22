package com.minionx;

import com.minionx.api.*;
import com.minionx.gui.MinionCreatorSession;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class MinionCreatorAndDefinitionTest {

    @Test
    public void testMinionCreatorSessionDefaults() {
        MinionCreatorSession session = new MinionCreatorSession("ruby");
        assertEquals("ruby", session.getId());
        assertEquals("&aRuby Minion", session.getDisplayName());
        assertEquals(MinionArchetype.MINER, session.getArchetype());
        assertEquals(Material.COBBLESTONE, session.getTargetBlock());
        assertEquals("ZOMBIE", session.getTargetMob());
        assertEquals(14.0, session.getActionDelay(), 0.01);
        assertEquals(1, session.getStorageSlots());
        assertEquals(2, session.getXpPerHarvest());
        assertFalse(session.isPerTierMode());
    }

    @Test
    public void testArchetypeToggleAndBuildDefinition() {
        MinionCreatorSession session = new MinionCreatorSession("goblin");
        session.setArchetype(MinionArchetype.COMBAT);
        session.setTargetMob("SkeletalKnight");
        session.setXpPerHarvest(10);
        session.setActionDelay(8.5);
        session.setStorageSlots(5);

        MinionDefinition def = session.buildDefinition();
        assertNotNull(def);
        assertEquals("goblin", def.getId());
        assertEquals(MinionArchetype.COMBAT, def.getArchetype());
        assertEquals("SkeletalKnight", def.getTargetMob());
        assertEquals(10, def.getXpPerHarvest());
        assertEquals(8.5, def.getDefaultActionDelay(), 0.01);
        assertEquals(5, def.getDefaultStorageSlots());
    }

    @Test
    public void testPerTierStyleOverrides() {
        MinionDefinition def = new MinionDefinition("paladin", "&ePaladin Minion", MinionArchetype.COMBAT,
                "default_skull", Material.LEATHER_CHESTPLATE, Color.fromRGB(100, 100, 100), new ItemStack(Material.STONE_SWORD),
                null, "ZOMBIE", new ItemStack(Material.ROTTEN_FLESH), 5, 12.0, 2);

        // Base style for Tier 1
        assertEquals("default_skull", def.getSkullTexture(1));
        assertEquals(Material.LEATHER_CHESTPLATE, def.getArmorMaterial(1));

        // Add Tier 11 override
        def.addTierStyle(11, new MinionDefinition.TierStyle("maxed_skull", Material.NETHERITE_CHESTPLATE,
                Color.fromRGB(50, 50, 50), new ItemStack(Material.NETHERITE_SWORD)));

        // Tier 1 still gets base style
        assertEquals("default_skull", def.getSkullTexture(1));
        assertEquals(Material.LEATHER_CHESTPLATE, def.getArmorMaterial(1));

        // Tier 11 gets override
        assertEquals("maxed_skull", def.getSkullTexture(11));
        assertEquals(Material.NETHERITE_CHESTPLATE, def.getArmorMaterial(11));
    }

    @Test
    public void testMinionRegistryDefaults() {
        MinionDefinition cobble = MinionRegistry.get("cobblestone");
        assertNotNull(cobble);
        assertEquals(MinionArchetype.MINER, cobble.getArchetype());
        assertEquals(Material.COBBLESTONE, cobble.getTargetBlock());

        MinionDefinition zombie = MinionRegistry.get("zombie");
        assertNotNull(zombie);
        assertEquals(MinionArchetype.COMBAT, zombie.getArchetype());
        assertEquals("ZOMBIE", zombie.getTargetMob());
    }

    @Test
    public void testSessionTierCycleWithinMaxTier() {
        MinionCreatorSession session = new MinionCreatorSession("custom_test");
        session.setMaxTier(7);
        assertEquals(7, session.getMaxTier());

        // Cycle through 1 to 7
        session.setSelectedTier(7);
        int next = session.getSelectedTier() + 1;
        if (next > session.getMaxTier()) next = 1;
        session.setSelectedTier(next);
        assertEquals(1, session.getSelectedTier());

        // Previous from 1 wraps to maxTier (7)
        int prev = session.getSelectedTier() - 1;
        if (prev < 1) prev = session.getMaxTier();
        session.setSelectedTier(prev);
        assertEquals(7, session.getSelectedTier());
    }

    @Test
    public void testSessionTierClampingWhenMaxTierLowered() {
        MinionCreatorSession session = new MinionCreatorSession("custom_clamp");
        session.setMaxTier(11);
        session.setSelectedTier(9);

        // Lower max tier to 5
        int newMax = 5;
        session.setMaxTier(newMax);
        if (session.getSelectedTier() > newMax) {
            session.setSelectedTier(newMax);
        }

        assertEquals(5, session.getMaxTier());
        assertEquals(5, session.getSelectedTier());
    }

    @Test
    public void testEffectiveSkullTextureInPerTierMode() {
        MinionCreatorSession session = new MinionCreatorSession("textured_minion");
        session.setSkullTexture("base_texture");
        session.setPerTierMode(true);
        session.setSelectedTier(3);
        session.setTierSkullTexture(3, "tier_3_texture");

        assertEquals("tier_3_texture", session.getEffectiveSkullTexture(3));
        // Tier 1 has no override so falls back to base
        assertEquals("base_texture", session.getEffectiveSkullTexture(1));
    }
}
