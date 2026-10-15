package com.minionx;

import com.minionx.api.CustomFuel;
import com.minionx.api.CustomUpgrade;
import com.minionx.api.MinionType;
import com.minionx.api.MinionUpgradeType;
import com.minionx.core.CompressionManager;
import com.minionx.core.Minion;
import com.minionx.core.MinionManager;
import com.minionx.gui.FuelCreatorSession;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class NewMechanicsAndCompactorTest {

    @Test
    public void testCompactorPresetRecipesLoaded() {
        assertNotNull(CompressionManager.getCompactorRecipe("redstone_block"));
        assertEquals(Material.REDSTONE, CompressionManager.getCompactorRecipe("redstone_block").getInputMaterial());
        assertEquals(9, CompressionManager.getCompactorRecipe("redstone_block").getInputAmount());

        assertNotNull(CompressionManager.getCompactorRecipe("quartz_block"));
        assertEquals(Material.QUARTZ, CompressionManager.getCompactorRecipe("quartz_block").getInputMaterial());
        assertEquals(4, CompressionManager.getCompactorRecipe("quartz_block").getInputAmount());

        assertNotNull(CompressionManager.getCompactorRecipe(Material.COAL));
        assertEquals(9, CompressionManager.getCompactorRecipe(Material.COAL).getInputAmount());
    }

    @Test
    public void testStandardCompactorCompaction() {
        Minion minion = new Minion(UUID.randomUUID(), UUID.randomUUID(), MinionType.IRON, 1, null);
        minion.setUpgrade1(MinionUpgradeType.COMPACTOR);

        // Add 18 Redstone dust
        minion.addItem(new ItemStack(Material.REDSTONE, 18));
        assertEquals(18, minion.getInventory().getFirst().getAmount());

        // Process compaction
        CompressionManager.processCompaction(minion);

        // Should have compacted into 2 Redstone Blocks
        boolean hasBlock = false;
        int blockCount = 0;
        for (ItemStack item : minion.getInventory()) {
            if (item.getType() == Material.REDSTONE_BLOCK) {
                hasBlock = true;
                blockCount += item.getAmount();
            }
        }
        assertTrue(hasBlock);
        assertEquals(2, blockCount);
    }

    @Test
    public void testDualCompactionConditionBased() {
        Minion minion = new Minion(UUID.randomUUID(), UUID.randomUUID(), MinionType.IRON, 1, null);
        // Equip BOTH Super Compactor and Standard Compactor
        minion.setUpgrade1(MinionUpgradeType.SUPER_COMPACTOR);
        minion.setUpgrade2(MinionUpgradeType.COMPACTOR);

        // Case 1: Less than 160 (e.g. 18 redstone) -> Compactor executes, Super Compactor doesn't block!
        minion.addItem(new ItemStack(Material.REDSTONE, 18));
        CompressionManager.processCompaction(minion);

        int blocks = 0;
        for (ItemStack item : minion.getInventory()) {
            if (item.getType() == Material.REDSTONE_BLOCK) {
                blocks += item.getAmount();
            }
        }
        assertEquals(2, blocks, "Compactor must compact 18 redstone into 2 blocks even when Super Compactor is also equipped");
    }

    @Test
    public void testMinionExpanderStacking() {
        Minion minion = new Minion(UUID.randomUUID(), UUID.randomUUID(), MinionType.COBBLESTONE, 1, null);

        // 0 expanders
        assertEquals(0, minion.getAreaExpansion());
        assertEquals(2, minion.getWorkingRadius());
        assertEquals(24, minion.getWorkingOffsets().size());

        // 1 expander equipped
        minion.setUpgrade1(MinionUpgradeType.MINION_EXPANDER);
        assertEquals(1, minion.getAreaExpansion());
        assertEquals(3, minion.getWorkingRadius());
        assertEquals(48, minion.getWorkingOffsets().size());

        // 2 expanders equipped (stacking)
        minion.setUpgrade2(new CustomUpgrade("minion_expander", "Minion Expander", Material.PISTON,
                CustomUpgrade.UpgradeBehavior.SPEED_BUFF, 0.05, 0.0, 0.0, 1, List.of(), true, 0));
        assertEquals(2, minion.getAreaExpansion());
        assertEquals(4, minion.getWorkingRadius());
        assertEquals(80, minion.getWorkingOffsets().size());
    }

    @Test
    public void testStoragePurgeChunkTracking() {
        MinionManager manager = new MinionManager();
        UUID worldId = UUID.randomUUID();

        // Queue a location in chunk (10, 20)
        String locStr = worldId + ":165:64:325";
        manager.loadPendingStoragePurges(List.of(locStr));

        List<String> saved = manager.getPendingStoragePurgeLocations();
        assertEquals(1, saved.size());
        assertEquals(locStr, saved.getFirst());
    }

    @Test
    public void testFuelCreatorSession() {
        CustomFuel existing = new CustomFuel("plasma", "Plasma", Material.NETHER_STAR, 0.35, -1L, List.of("Fast"), true, 555);
        FuelCreatorSession session = new FuelCreatorSession(existing);

        assertEquals("plasma", session.getId());
        assertEquals("Plasma", session.getDisplayName());
        assertEquals(Material.NETHER_STAR, session.getMaterial());
        assertEquals(0.35, session.getSpeedBuff());
        assertTrue(session.isPermanent());
        assertEquals(555, session.getCustomModelData());
        assertTrue(session.isGlow());

        // Toggle to temporary
        session.setPermanent(false);
        assertFalse(session.isPermanent());
        session.setDurationMinutes(120);
        assertEquals(120 * 60 * 1000L, session.getDurationMillis());
    }
}
