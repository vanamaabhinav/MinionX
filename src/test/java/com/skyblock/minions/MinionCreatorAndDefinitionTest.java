package com.skyblock.minions;

import com.skyblock.minions.api.*;
import com.skyblock.minions.gui.MinionCreatorSession;
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
}
