package com.minionx;

import com.minionx.api.*;
import com.minionx.api.MinionSkin;
import com.minionx.api.MinionSkinRegistry;
import com.minionx.core.Minion;
import com.minionx.core.PlaceholderUtil;
import com.minionx.gui.SymmetricalSlotCalculator;
import com.minionx.quarantine.QuarantineManager;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class MinionStoragesAndQuarantineTest {

    @BeforeEach
    public void setup() {
        MinionStorageRegistry.load();
        MinionSkinRegistry.load();
        QuarantineManager.setQuarantined(false);
    }

    @Test
    public void testSymmetricalSlotCalculatorCenter13() {
        // 0-indexed center is 13
        List<Integer> slots1 = SymmetricalSlotCalculator.getSlots(1);
        assertEquals(1, slots1.size());
        assertEquals(13, slots1.getFirst());

        // Capacity 3: centered symmetrically [12, 13, 14]
        List<Integer> slots3 = SymmetricalSlotCalculator.getSlots(3);
        assertEquals(3, slots3.size());
        assertTrue(slots3.contains(12));
        assertTrue(slots3.contains(13));
        assertTrue(slots3.contains(14));

        // Capacity 9: 3x3 square centered at 13
        List<Integer> slots9 = SymmetricalSlotCalculator.getSlots(9);
        assertEquals(9, slots9.size());
        assertTrue(slots9.contains(13)); // center
        assertTrue(slots9.contains(3));  // top-left of 3x3
        assertTrue(slots9.contains(23)); // bottom-right of 3x3

        // Capacity 15: 3x5 rectangle centered at 13
        List<Integer> slots15 = SymmetricalSlotCalculator.getSlots(15);
        assertEquals(15, slots15.size());
        assertTrue(slots15.contains(13));
        assertTrue(slots15.contains(2));
        assertTrue(slots15.contains(24));

        // Capacity 27: full chest
        List<Integer> slots27 = SymmetricalSlotCalculator.getSlots(27);
        assertEquals(27, slots27.size());
        for (int i = 0; i < 27; i++) {
            assertTrue(SymmetricalSlotCalculator.isSlotUnlocked(i, 27));
        }
    }

    @Test
    public void testMinionStorageRegistryDefaults() {
        CustomMinionStorage small = MinionStorageRegistry.get("small_storage");
        assertNotNull(small);
        assertEquals(3, small.getCapacity());

        CustomMinionStorage med = MinionStorageRegistry.get("medium_storage");
        assertNotNull(med);
        assertEquals(9, med.getCapacity());

        CustomMinionStorage large = MinionStorageRegistry.get("large_storage");
        assertNotNull(large);
        assertEquals(15, large.getCapacity());

        CustomMinionStorage max = MinionStorageRegistry.get("max_storage");
        assertNotNull(max);
        assertEquals(27, max.getCapacity());
    }

    @Test
    public void testMinionStorageLinkingAndItemOverflow() {
        Minion minion = new Minion(UUID.randomUUID(), UUID.randomUUID(), MinionType.COBBLESTONE, 1, null);
        assertFalse(minion.hasLinkedStorage());

        // Link medium storage (9 slots)
        minion.linkStorage(new Location(null, 100, 64, 100), "medium_storage");
        assertTrue(minion.hasLinkedStorage());
        assertEquals("medium_storage", minion.getStorageId());

        // Minion Tier 1 has 1 internal slot (max 64 items)
        // Add 64 cobblestone -> internal fills completely
        boolean added1 = minion.addItem(new ItemStack(Material.COBBLESTONE, 64));
        assertTrue(added1);
        assertEquals(1, minion.getInventory().size());
        assertEquals(64, minion.getInventory().getFirst().getAmount());
        assertEquals(0, minion.getStorageContents().size());

        // Add 32 more cobblestone -> internal full, overflows into linked storage chest!
        boolean added2 = minion.addItem(new ItemStack(Material.COBBLESTONE, 32));
        assertTrue(added2);
        assertEquals(1, minion.getStorageContents().size());
        assertEquals(32, minion.getStorageContents().getFirst().getAmount());

        // Unlink storage
        minion.unlinkStorage();
        assertFalse(minion.hasLinkedStorage());
        assertEquals(0, minion.getStorageContents().size());
    }

    @Test
    public void testQuarantineManagerDefaultOffAndToggling() {
        // Quarantine MUST default to false (off)
        assertFalse(QuarantineManager.isQuarantined());

        QuarantineManager.setQuarantined(true);
        assertTrue(QuarantineManager.isQuarantined());

        QuarantineManager.setQuarantined(false);
        assertFalse(QuarantineManager.isQuarantined());
    }

    @Test
    public void testQuarantineAndStoragePlaceholders() {
        QuarantineManager.setQuarantined(false);
        String normalStatus = PlaceholderUtil.parse("Status: %minions_quarantine_status% (%minion_is_quarantined%)", null, null, null, 0);
        assertEquals("Status: &aNORMAL (false)", normalStatus);

        QuarantineManager.setQuarantined(true);
        String quarStatus = PlaceholderUtil.parse("Status: %minions_quarantine_status% (%minion_is_quarantined%)", null, null, null, 0);
        assertEquals("Status: &cQUARANTINED (true)", quarStatus);

        // Minion storage placeholders
        Minion minion = new Minion(UUID.randomUUID(), UUID.randomUUID(), MinionType.DIAMOND, 5, null);
        String noStorage = PlaceholderUtil.parse("Storage: %minion_storage_name% (%minion_has_storage%)", null, minion, null, 0);
        assertEquals("Storage: None (false)", noStorage);

        minion.linkStorage(new Location(null, 10, 60, 10), "large_storage");
        String hasStorage = PlaceholderUtil.parse("Storage: %minion_storage_name% Cap: %minion_storage_capacity% Used: %minion_storage_used%", null, minion, null, 0);
        assertEquals("Storage: &5Large Minion Storage Cap: 15 Used: 0", hasStorage);
    }

    @Test
    public void testCustomSkinWithArmorMaterialAndHandItem() {
        MinionSkin skin = new MinionSkin(
                "diamond_knight",
                "&bDiamond Knight",
                "dummy_base64_texture",
                Color.fromRGB(0, 200, 255),
                Material.DIAMOND_CHESTPLATE,
                new ItemStack(Material.DIAMOND_SWORD),
                List.of("&7Noble diamond guardian.")
        );

        MinionSkinRegistry.register(skin);
        MinionSkin fetched = MinionSkinRegistry.get("diamond_knight");
        assertNotNull(fetched);
        assertEquals(Material.DIAMOND_CHESTPLATE, fetched.getArmorMaterial());
        assertTrue(fetched.hasHandItem());
        assertEquals(Material.DIAMOND_SWORD, fetched.getHandItem().getType());
        assertEquals(Color.fromRGB(0, 200, 255), fetched.getArmorColor());
    }

    @Test
    public void testMinionDialogueSystem() {
        Minion minion = new Minion(UUID.randomUUID(), UUID.randomUUID(), MinionType.IRON, 3, null);
        assertFalse(minion.hasActiveDialogue());
        assertNull(minion.getActiveDialogue());

        // Minion speaks
        minion.speak("Hard at work!", 5000L);
        assertTrue(minion.hasActiveDialogue());
        assertEquals("Hard at work!", minion.getActiveDialogue());

        // Minion clears dialogue
        minion.clearDialogue();
        assertFalse(minion.hasActiveDialogue());
        assertNull(minion.getActiveDialogue());
    }
}
