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
            if (MinionRegistry.isCustom(def.getId())) continue;
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

    @Test
    @DisplayName("Should verify builtin skins have authentic thematic armor colors")
    void testBuiltinSkinArmorColors() {
        MinionSkin snowman = MinionSkinRegistry.get("snowman");
        assertNotNull(snowman);
        assertTrue(snowman.hasArmorColor());
        assertEquals(org.bukkit.Color.fromRGB(240, 248, 255), snowman.getArmorColor());

        MinionSkin santa = MinionSkinRegistry.get("santa");
        assertNotNull(santa);
        assertTrue(santa.hasArmorColor());
        assertEquals(org.bukkit.Color.fromRGB(183, 28, 28), santa.getArmorColor());

        MinionSkin penguin = MinionSkinRegistry.get("penguin");
        assertNotNull(penguin);
        assertTrue(penguin.hasArmorColor());
        assertEquals(org.bukkit.Color.fromRGB(33, 33, 33), penguin.getArmorColor());
    }

    @Test
    @DisplayName("Should load custom skins from YAML configuration section")
    void testCustomSkinYamlLoading() {
        org.bukkit.configuration.file.YamlConfiguration yaml = new org.bukkit.configuration.file.YamlConfiguration();
        yaml.set("skins.ruby_warrior.display-name", "&cRuby Warrior");
        yaml.set("skins.ruby_warrior.skull-texture", "fake_ruby_base64_texture");
        yaml.set("skins.ruby_warrior.armor-color", "#E53935");
        yaml.set("skins.ruby_warrior.armor-material", "LEATHER");
        yaml.set("skins.ruby_warrior.hand-item", "NETHERITE_SWORD");
        yaml.set("skins.ruby_warrior.description", java.util.List.of("&7Forged from pure nether ruby."));

        yaml.set("skins.gold_titan.display-name", "&6Gold Titan");
        yaml.set("skins.gold_titan.skull-texture", "fake_gold_texture");
        yaml.set("skins.gold_titan.armor-material", "GOLD");
        yaml.set("skins.gold_titan.hand-item", "GOLDEN_AXE");

        MinionSkinRegistry.loadCustomSkins(yaml.getConfigurationSection("skins"));

        MinionSkin ruby = MinionSkinRegistry.get("ruby_warrior");
        assertNotNull(ruby);
        assertEquals("&cRuby Warrior", ruby.getDisplayName());
        assertEquals("fake_ruby_base64_texture", ruby.getSkullTexture());
        assertTrue(ruby.hasArmorColor());
        assertEquals(org.bukkit.Color.fromRGB(0xE53935), ruby.getArmorColor());
        assertEquals(org.bukkit.Material.LEATHER_CHESTPLATE, ruby.getArmorMaterial());
        assertTrue(ruby.hasHandItem());
        assertEquals(org.bukkit.Material.NETHERITE_SWORD, ruby.getHandItem().getType());

        MinionSkin gold = MinionSkinRegistry.get("gold_titan");
        assertNotNull(gold);
        assertEquals(org.bukkit.Material.GOLDEN_CHESTPLATE, gold.getArmorMaterial());
        assertFalse(gold.hasArmorColor());
        assertEquals(org.bukkit.Material.GOLDEN_AXE, gold.getHandItem().getType());
    }

    @Test
    @DisplayName("Should parse various color formats correctly")
    void testColorParsing() {
        assertEquals(org.bukkit.Color.fromRGB(0xFF0000), com.minionx.util.ColorUtil.parseColor("#FF0000"));
        assertEquals(org.bukkit.Color.fromRGB(0x00FF00), com.minionx.util.ColorUtil.parseColor("0x00FF00"));
        assertEquals(org.bukkit.Color.fromRGB(255, 128, 0), com.minionx.util.ColorUtil.parseColor("255, 128, 0"));
        assertEquals(org.bukkit.Color.RED, com.minionx.util.ColorUtil.parseColor("RED"));
        assertEquals(org.bukkit.Color.BLUE, com.minionx.util.ColorUtil.parseColor("BLUE"));
        assertEquals("#FF5555", com.minionx.util.ColorUtil.toHexString(org.bukkit.Color.fromRGB(255, 85, 85)));
    }
}
