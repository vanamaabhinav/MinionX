package com.skyblock.minions;

import com.skyblock.minions.api.*;
import com.skyblock.minions.core.CompressionManager;
import com.skyblock.minions.core.Minion;
import com.skyblock.minions.util.ColorUtil;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class SuperCompactorAndXpTest {

    @Test
    public void testCompressionRecipeStructure() {
        ItemStack output = new ItemStack(Material.COBBLESTONE, 1);
        CompressionManager.CompressionRecipe recipe = new CompressionManager.CompressionRecipe(Material.COBBLESTONE, 160, output);

        assertEquals(Material.COBBLESTONE, recipe.getInputMaterial());
        assertEquals(160, recipe.getInputAmount());
        assertNotNull(recipe.createOutput());
        assertEquals(Material.COBBLESTONE, recipe.createOutput().getType());
    }

    @Test
    public void testStoredXpClaiming() {
        Minion minion = new Minion(UUID.randomUUID(), UUID.randomUUID(), MinionType.DIAMOND, 1, null);
        assertEquals(0, minion.claimStoredXp());

        minion.addStoredXp(15);
        minion.addStoredXp(10);

        int claimed = minion.claimStoredXp();
        assertEquals(25, claimed);
        assertEquals(0, minion.claimStoredXp()); // Should be reset after claiming
    }

    @Test
    public void testCustomDefinitionDisplayName() {
        Minion minion = new Minion(UUID.randomUUID(), UUID.randomUUID(), MinionType.COBBLESTONE, 1, null);
        assertEquals("Cobblestone", minion.getDisplayName());

        minion.setCustomDefinitionId("diamond");
        assertEquals(ColorUtil.color("&bDiamond"), minion.getDisplayName());
    }
}
