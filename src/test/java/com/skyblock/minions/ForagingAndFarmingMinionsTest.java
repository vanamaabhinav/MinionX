package com.skyblock.minions;

import com.skyblock.minions.api.*;
import com.skyblock.minions.gui.MinionCreatorSession;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ForagingAndFarmingMinionsTest {

    @Test
    public void testForagingMinionDefinitions() {
        MinionDefinition oak = MinionRegistry.get("oak");
        assertNotNull(oak, "Oak minion must be registered");
        assertEquals(MinionArchetype.FORAGING, oak.getArchetype());
        assertEquals(Material.OAK_LOG, oak.getTargetBlock());
        assertEquals(Material.OAK_SAPLING, oak.getSaplingMaterial());
        assertEquals(Material.OAK_LEAVES, oak.getLeafMaterial());
        assertTrue(oak.hasSecondaryDrop());
        assertEquals(Material.APPLE, oak.getSecondaryDropItem().getType());
        assertEquals(0.05, oak.getSecondaryDropChance(), 0.001);

        MinionDefinition birch = MinionRegistry.get("birch");
        assertNotNull(birch, "Birch minion must be registered");
        assertEquals(MinionArchetype.FORAGING, birch.getArchetype());
        assertEquals(Material.BIRCH_LOG, birch.getTargetBlock());
        assertEquals(Material.BIRCH_SAPLING, birch.getSaplingMaterial());
        assertEquals(Material.BIRCH_LEAVES, birch.getLeafMaterial());

        MinionDefinition darkOak = MinionRegistry.get("dark_oak");
        assertNotNull(darkOak, "Dark oak minion must be registered");
        assertEquals(MinionArchetype.FORAGING, darkOak.getArchetype());
        assertEquals(Material.DARK_OAK_LOG, darkOak.getTargetBlock());
        assertEquals(Material.DARK_OAK_SAPLING, darkOak.getSaplingMaterial());
        assertEquals(Material.DARK_OAK_LEAVES, darkOak.getLeafMaterial());
    }

    @Test
    public void testFarmingSoilCropDefinitions() {
        MinionDefinition wheat = MinionRegistry.get("wheat");
        assertNotNull(wheat, "Wheat minion must be registered");
        assertEquals(MinionArchetype.FARMING, wheat.getArchetype());
        assertEquals(Material.WHEAT, wheat.getTargetBlock());
        assertTrue(wheat.hasSecondaryDrop());
        assertEquals(Material.WHEAT_SEEDS, wheat.getSecondaryDropItem().getType());
        assertEquals(1.0, wheat.getSecondaryDropChance(), 0.001);

        MinionDefinition carrot = MinionRegistry.get("carrot");
        assertNotNull(carrot, "Carrot minion must be registered");
        assertEquals(MinionArchetype.FARMING, carrot.getArchetype());
        assertEquals(Material.CARROTS, carrot.getTargetBlock());

        MinionDefinition potato = MinionRegistry.get("potato");
        assertNotNull(potato, "Potato minion must be registered");
        assertEquals(MinionArchetype.FARMING, potato.getArchetype());
        assertEquals(Material.POTATOES, potato.getTargetBlock());
        assertTrue(potato.hasSecondaryDrop());
        assertEquals(Material.POISONOUS_POTATO, potato.getSecondaryDropItem().getType());
        assertEquals(0.02, potato.getSecondaryDropChance(), 0.001);

        MinionDefinition netherWart = MinionRegistry.get("nether_wart");
        assertNotNull(netherWart, "Nether wart minion must be registered");
        assertEquals(MinionArchetype.FARMING, netherWart.getArchetype());
        assertEquals(Material.NETHER_WART, netherWart.getTargetBlock());
    }

    @Test
    public void testFarmingStemAndColumnAndMushroomDefinitions() {
        MinionDefinition pumpkin = MinionRegistry.get("pumpkin");
        assertNotNull(pumpkin, "Pumpkin minion must be registered");
        assertEquals(MinionArchetype.FARMING, pumpkin.getArchetype());
        assertEquals(Material.PUMPKIN, pumpkin.getTargetBlock());

        MinionDefinition melon = MinionRegistry.get("melon");
        assertNotNull(melon, "Melon minion must be registered");
        assertEquals(MinionArchetype.FARMING, melon.getArchetype());
        assertEquals(Material.MELON, melon.getTargetBlock());
        assertEquals(Material.MELON_SLICE, melon.getExactDropItem().getType());

        MinionDefinition sugarCane = MinionRegistry.get("sugar_cane");
        assertNotNull(sugarCane, "Sugar cane minion must be registered");
        assertEquals(MinionArchetype.FARMING, sugarCane.getArchetype());
        assertEquals(Material.SUGAR_CANE, sugarCane.getTargetBlock());

        MinionDefinition cactus = MinionRegistry.get("cactus");
        assertNotNull(cactus, "Cactus minion must be registered");
        assertEquals(MinionArchetype.FARMING, cactus.getArchetype());
        assertEquals(Material.CACTUS, cactus.getTargetBlock());

        MinionDefinition redMushroom = MinionRegistry.get("red_mushroom");
        assertNotNull(redMushroom, "Red mushroom minion must be registered");
        assertEquals(MinionArchetype.FARMING, redMushroom.getArchetype());
        assertEquals(Material.RED_MUSHROOM, redMushroom.getTargetBlock());

        MinionDefinition brownMushroom = MinionRegistry.get("brown_mushroom");
        assertNotNull(brownMushroom, "Brown mushroom minion must be registered");
        assertEquals(MinionArchetype.FARMING, brownMushroom.getArchetype());
        assertEquals(Material.BROWN_MUSHROOM, brownMushroom.getTargetBlock());
    }

    @Test
    public void testHusbandryAnimalDefinitions() {
        MinionDefinition cow = MinionRegistry.get("cow");
        assertNotNull(cow, "Cow minion must be registered");
        assertEquals(MinionArchetype.COMBAT, cow.getArchetype());
        assertEquals("COW", cow.getTargetMob());
        assertEquals(Material.BEEF, cow.getExactDropItem().getType());
        assertTrue(cow.hasSecondaryDrop());
        assertEquals(Material.LEATHER, cow.getSecondaryDropItem().getType());
        assertEquals(0.5, cow.getSecondaryDropChance(), 0.001);

        MinionDefinition pig = MinionRegistry.get("pig");
        assertNotNull(pig, "Pig minion must be registered");
        assertEquals(MinionArchetype.COMBAT, pig.getArchetype());
        assertEquals("PIG", pig.getTargetMob());
        assertEquals(Material.PORKCHOP, pig.getExactDropItem().getType());

        MinionDefinition chicken = MinionRegistry.get("chicken");
        assertNotNull(chicken, "Chicken minion must be registered");
        assertEquals(MinionArchetype.COMBAT, chicken.getArchetype());
        assertEquals("CHICKEN", chicken.getTargetMob());
        assertEquals(Material.CHICKEN, chicken.getExactDropItem().getType());
        assertTrue(chicken.hasSecondaryDrop());
        assertEquals(Material.FEATHER, chicken.getSecondaryDropItem().getType());

        MinionDefinition sheep = MinionRegistry.get("sheep");
        assertNotNull(sheep, "Sheep minion must be registered");
        assertEquals(MinionArchetype.COMBAT, sheep.getArchetype());
        assertEquals("SHEEP", sheep.getTargetMob());
        assertEquals(Material.MUTTON, sheep.getExactDropItem().getType());
        assertTrue(sheep.hasSecondaryDrop());
        assertEquals(Material.WHITE_WOOL, sheep.getSecondaryDropItem().getType());

        MinionDefinition rabbit = MinionRegistry.get("rabbit");
        assertNotNull(rabbit, "Rabbit minion must be registered");
        assertEquals(MinionArchetype.COMBAT, rabbit.getArchetype());
        assertEquals("RABBIT", rabbit.getTargetMob());
        assertEquals(Material.RABBIT, rabbit.getExactDropItem().getType());
        assertTrue(rabbit.hasSecondaryDrop());
        assertEquals(Material.RABBIT_HIDE, rabbit.getSecondaryDropItem().getType());
    }

    @Test
    public void testCreatorSessionSecondaryDrops() {
        MinionCreatorSession session = new MinionCreatorSession("cherry_blossom");
        session.setArchetype(MinionArchetype.FORAGING);
        session.setTargetBlock(Material.CHERRY_LOG);
        session.setExactDropItem(new ItemStack(Material.CHERRY_LOG));
        session.setSecondaryDropItem(new ItemStack(Material.CHERRY_SAPLING));
        session.setSecondaryDropChance(0.15);

        MinionDefinition def = session.buildDefinition();
        assertNotNull(def);
        assertEquals("cherry_blossom", def.getId());
        assertEquals(MinionArchetype.FORAGING, def.getArchetype());
        assertEquals(Material.CHERRY_LOG, def.getTargetBlock());
        assertTrue(def.hasSecondaryDrop());
        assertEquals(Material.CHERRY_SAPLING, def.getSecondaryDropItem().getType());
        assertEquals(0.15, def.getSecondaryDropChance(), 0.001);
    }

    @Test
    public void testMinionRegistryIsCustom() {
        assertFalse(MinionRegistry.isCustom("oak"));
        assertFalse(MinionRegistry.isCustom("wheat"));
        assertFalse(MinionRegistry.isCustom("cow"));
        assertFalse(MinionRegistry.isCustom("cobblestone"));
        assertTrue(MinionRegistry.isCustom("mystic_cherry_tree"));
    }
}
