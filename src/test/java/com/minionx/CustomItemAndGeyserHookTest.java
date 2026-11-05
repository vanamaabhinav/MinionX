package com.minionx;

import com.minionx.hook.CustomItemHook;
import com.minionx.hook.GeyserHook;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class CustomItemAndGeyserHookTest {

    @Test
    @DisplayName("CustomItemHook: Handles absent plugins gracefully and resolves vanilla fallback items")
    void testCustomItemHookFallback() {
        assertDoesNotThrow(CustomItemHook::checkHooks);
        assertFalse(CustomItemHook.isItemsAdderAvailable());
        assertFalse(CustomItemHook.isMmoItemsAvailable());
        assertFalse(CustomItemHook.isEcoItemsAvailable());
        assertFalse(CustomItemHook.isOraxenAvailable());
        assertFalse(CustomItemHook.isNexoAvailable());

        // Resolves vanilla material
        ItemStack diamond = CustomItemHook.getItem("DIAMOND");
        assertNotNull(diamond);
        assertEquals(Material.DIAMOND, diamond.getType());

        // Custom prefixes return null without exceptions when providers are absent
        assertNull(CustomItemHook.getItem("itemsadder:ruby_gem"));
        assertNull(CustomItemHook.getItem("mmoitems:SWORD:CUTLASS"));
        assertNull(CustomItemHook.getItem("ecoitems:titanium_ingot"));
        assertNull(CustomItemHook.getItem("oraxen:mythic_sword"));
        assertNull(CustomItemHook.getItem("nexo:ruby_crystal"));

        // Null and blank safety
        assertNull(CustomItemHook.getItem(null));
        assertNull(CustomItemHook.getItem(""));
        assertNull(CustomItemHook.getItem("   "));
        assertNull(CustomItemHook.getItem("non_existent_invalid_mat_xyz"));

        // identifyCustomItem safety
        assertNull(CustomItemHook.identifyCustomItem(null));
        assertNull(CustomItemHook.identifyCustomItem(new ItemStack(Material.AIR)));
        assertNull(CustomItemHook.identifyCustomItem(new ItemStack(Material.DIRT)));
        assertFalse(CustomItemHook.isCustomItem(new ItemStack(Material.COBBLESTONE)));
    }

    @Test
    @DisplayName("GeyserHook: Handles absent Geyser/Floodgate environments safely")
    void testGeyserHookFallback() {
        assertDoesNotThrow(GeyserHook::checkHooks);
        assertFalse(GeyserHook.isFloodgateAvailable());
        assertFalse(GeyserHook.isGeyserAvailable());
        assertFalse(GeyserHook.isCrossPlatformSupported());

        // Bedrock check defaults to false safely
        assertFalse(GeyserHook.isBedrockPlayer((UUID) null));
        assertFalse(GeyserHook.isBedrockPlayer(UUID.randomUUID()));
    }
}

