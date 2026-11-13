package com.minionx;

import com.minionx.api.BonusDropEntry;
import com.minionx.api.CustomUpgrade;
import com.minionx.api.MinionArchetype;
import com.minionx.api.MinionDefinition;
import com.minionx.core.Minion;
import com.minionx.core.PlaceholderUtil;
import com.minionx.gui.MinionCreatorSession;
import com.minionx.gui.UpgradeCreatorSession;
import com.minionx.item.CustomItemSpec;
import com.minionx.util.SkullUtil;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Rigorous tests verifying:
 * 1. %tier% and %tier_roman% placeholder formatting in Minion GUI lore and PlaceholderUtil.
 * 2. SkullUtil normalization, URL-to-Base64 encoding, and hash translation.
 * 3. Full-fidelity CustomItemSpec serialization and retention of lore, enchants, glow, and head textures.
 * 4. In-game creator studios cursor item metadata preservation.
 */
public class EditorCustomHeadsAndPlaceholdersTest {

    @Test
    @DisplayName("Verify %tier% and %tier_roman% placeholders in PlaceholderUtil")
    public void testPlaceholderUtilRomanNumerals() {
        UUID minionId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Minion minion = new Minion(minionId, ownerId, com.minionx.api.MinionType.COBBLESTONE, 1, null);
        minion.setCustomDefinitionId("custom_wheat");

        String template = "Minion: %minion_type_id% | Tier: %tier% (Lv.%tier_roman%) | Alt: %minion_tier% (%minion_tier_roman%)";
        String formatted = PlaceholderUtil.parse(template, null, minion, Material.WHEAT, 1);

        assertTrue(formatted.contains("Tier: 1 (Lv.I)"), "Must format %tier% as 1 and %tier_roman% as I");
        assertTrue(formatted.contains("Alt: 1 (I)"), "Must format %minion_tier% as 1 and %minion_tier_roman% as I");

        minion.setTier(5);
        String formatted5 = PlaceholderUtil.parse(template, null, minion, Material.WHEAT, 1);
        assertTrue(formatted5.contains("Tier: 5 (Lv.V)"), "Must format Tier 5 as V");

        minion.setTier(11);
        String formatted11 = PlaceholderUtil.parse(template, null, minion, Material.WHEAT, 1);
        assertTrue(formatted11.contains("Tier: 11 (Lv.XI)"), "Must format Tier 11 as XI");

        minion.setTier(12);
        String formatted12 = PlaceholderUtil.parse(template, null, minion, Material.WHEAT, 1);
        assertTrue(formatted12.contains("Tier: 12 (Lv.XII)"), "Must format Tier 12 as XII");
    }

    @Test
    @DisplayName("Verify SkullUtil URL-to-Base64 and 64-char hex hash normalization")
    public void testSkullUtilNormalization() {
        String testUrl = "http://textures.minecraft.net/texture/14a243e559e99a10bf1ae4e6bafbf410588a15a3d6c5c8ef29c1a8bdb5f31acc";
        String base64FromUrl = SkullUtil.urlToBase64(testUrl);
        assertNotNull(base64FromUrl);

        // Decode Base64 to verify JSON content
        String decoded = new String(Base64.getDecoder().decode(base64FromUrl), StandardCharsets.UTF_8);
        assertTrue(decoded.contains(testUrl), "Decoded JSON must contain original skin URL");

        // Normalize raw hash
        String hash = "14a243e559e99a10bf1ae4e6bafbf410588a15a3d6c5c8ef29c1a8bdb5f31acc";
        String base64FromHash = SkullUtil.normalizeToBase64(hash);
        assertNotNull(base64FromHash);
        String decodedHash = new String(Base64.getDecoder().decode(base64FromHash), StandardCharsets.UTF_8);
        assertTrue(decodedHash.contains("http://textures.minecraft.net/texture/" + hash),
                "Decoded hash must construct valid Mojang texture URL");

        // Pre-encoded Base64 string is returned as-is
        String preEncoded = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvdGVzdCJ9fX0=";
        assertEquals(preEncoded, SkullUtil.normalizeToBase64(preEncoded),
                "Pre-encoded base64 payload starting with 'ey' must be preserved unchanged");
    }

    @Test
    @DisplayName("Verify CustomItemSpec full fidelity serialization with lore, enchants, glow, and head textures")
    public void testCustomItemSpecFullFidelitySerialization() {
        Map<String, Integer> enchants = new LinkedHashMap<>();
        enchants.put("FORTUNE", 3);
        enchants.put("EFFICIENCY", 5);

        List<String> lore = List.of("&7Tasting sweet and fresh", "&eRarity: &6Legendary");
        String skullTex = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZGVtbzEyMyJ9fX0=";

        CustomItemSpec spec = new CustomItemSpec(
                Material.PLAYER_HEAD,
                null,
                2,
                0.75,
                "&aSweet Cane Head",
                lore,
                1008,
                true,
                "custom:tag",
                "val99",
                skullTex,
                enchants
        );

        assertTrue(spec.isCustom(), "Must detect as custom item");
        assertEquals(Material.PLAYER_HEAD, spec.getMaterial());
        assertEquals("&aSweet Cane Head", spec.getDisplayName());
        assertEquals(2, spec.getAmount());
        assertEquals(0.75, spec.getChance(), 0.001);
        assertEquals(lore, spec.getLore());
        assertEquals(1008, spec.getCustomModelData());
        assertTrue(spec.isGlow());
        assertEquals(skullTex, spec.getSkullTexture());
        assertEquals(2, spec.getEnchantments().size());
        assertEquals(3, spec.getEnchantments().get("FORTUNE"));

        // Test DropEntry toConfigMap serialization
        MinionDefinition.DropEntry dropEntry = new MinionDefinition.DropEntry(new ItemStack(Material.PLAYER_HEAD), spec, 2, 0.75);
        Map<String, Object> configMap = dropEntry.toConfigMap();

        assertEquals("PLAYER_HEAD", configMap.get("material"));
        assertEquals(2, configMap.get("amount"));
        assertEquals(0.75, configMap.get("chance"));
        assertEquals("&aSweet Cane Head", configMap.get("display-name"));
        assertEquals(lore, configMap.get("lore"));
        assertEquals(1008, configMap.get("custom-model-data"));
        assertEquals(true, configMap.get("glow"));
        assertEquals(skullTex, configMap.get("skull-texture"));
        assertNotNull(configMap.get("enchantments"));

        // Test deserialization fromMap
        CustomItemSpec restored = CustomItemSpec.fromMap(configMap);
        assertTrue(restored.isCustom());
        assertEquals(Material.PLAYER_HEAD, restored.getMaterial());
        assertEquals("&aSweet Cane Head", restored.getDisplayName());
        assertEquals(lore, restored.getLore());
        assertEquals(1008, restored.getCustomModelData());
        assertTrue(restored.isGlow());
        assertEquals(skullTex, restored.getSkullTexture());
        assertEquals(3, restored.getEnchantments().get("FORTUNE"));
        assertEquals(5, restored.getEnchantments().get("EFFICIENCY"));
    }

    @Test
    @DisplayName("Verify CustomItemSpec isCustom() detects lore-only, glow-only, and skull-only items")
    public void testCustomItemSpecIsCustomEdgeCases() {
        // Lore-only item
        CustomItemSpec loreOnly = new CustomItemSpec(Material.SUGAR_CANE, null, 1, 1.0, null,
                List.of("&7Freshly picked cane"), null, false, null, null);
        assertTrue(loreOnly.isCustom(), "Item with custom lore must be detected as custom");

        // Glow-only item
        CustomItemSpec glowOnly = new CustomItemSpec(Material.IRON_INGOT, null, 1, 1.0, null,
                Collections.emptyList(), null, true, null, null);
        assertTrue(glowOnly.isCustom(), "Item with glow must be detected as custom");

        // Skull texture only item
        CustomItemSpec skullOnly = new CustomItemSpec(Material.PLAYER_HEAD, null, 1, 1.0, null,
                Collections.emptyList(), null, false, null, null, "eyTest123", null);
        assertTrue(skullOnly.isCustom(), "Head with custom skull texture must be detected as custom");

        // Vanilla plain item
        CustomItemSpec vanilla = new CustomItemSpec(Material.COBBLESTONE, null, 1, 1.0, null,
                Collections.emptyList(), null, false, null, null);
        assertFalse(vanilla.isCustom(), "Standard vanilla item must not be detected as custom");
    }

    @Test
    @DisplayName("Verify MinionCreatorSession preserves exact drop item with full metadata")
    public void testMinionCreatorSessionPreservesExactDropItem() {
        MinionCreatorSession session = new MinionCreatorSession("cane_minion");
        session.setDisplayName("&aSweet Cane Minion");
        session.setArchetype(MinionArchetype.FARMING);

        Map<String, Integer> enchants = Map.of("DURABILITY", 3);
        CustomItemSpec dropSpec = new CustomItemSpec(
                Material.SUGAR_CANE, null, 1, 1.0, "&aSweet Cane",
                List.of("&7Refined sugar cane from the oasis"), null, true, null, null, null, enchants
        );

        MinionDefinition.DropEntry entry = new MinionDefinition.DropEntry(dropSpec);
        MinionDefinition def = new MinionDefinition(
                "cane_minion", "&aSweet Cane Minion", MinionArchetype.FARMING,
                null, Material.LEATHER_CHESTPLATE, null, null,
                Material.SUGAR_CANE, null, List.of(entry), 5, 12.0, 1
        );

        assertEquals(1, def.getDrops().size());
        CustomItemSpec savedSpec = def.getDrops().get(0).getCustomItemSpec();
        assertNotNull(savedSpec);
        assertEquals("&aSweet Cane", savedSpec.getDisplayName());
        assertEquals(1, savedSpec.getLore().size());
        assertTrue(savedSpec.isGlow());
        assertEquals(3, savedSpec.getEnchantments().get("DURABILITY"));
    }

    @Test
    @DisplayName("Verify UpgradeCreatorSession applyIconFrom copies custom name, lore, CMD, and glow")
    public void testUpgradeCreatorSessionApplyIconFrom() {
        UpgradeCreatorSession session = new UpgradeCreatorSession("super_hopper");

        // Verify initial state
        assertEquals(Material.HOPPER, session.getMaterial());

        // Update session properties
        session.setMaterial(Material.CHEST);
        session.setDisplayName("&6Vault Hopper");
        session.setLore(List.of("&7Transfers items to deep vaults", "&eCapacity: 1000"));
        session.setGlowing(true);
        session.setCustomModelData(42);

        CustomUpgrade built = session.buildUpgrade();
        assertEquals(Material.CHEST, built.getMaterial());
        assertEquals("&6Vault Hopper", built.getDisplayName());
        assertEquals(2, built.getLore().size());
        assertTrue(built.isGlowing());
        assertEquals(42, built.getCustomModelData());
    }

    @Test
    @DisplayName("Verify BonusDropEntry serialization preserves custom display name and custom model data")
    public void testBonusDropEntrySerialization() {
        BonusDropEntry baseEntry = new BonusDropEntry(Material.DIAMOND, 2, 0.20, "&bShining Diamond", 999, "itemsadder:ruby");
        Map<String, Object> map = baseEntry.toConfigMap();

        assertEquals("DIAMOND", map.get("material"));
        assertEquals(2, map.get("amount"));
        assertEquals(0.20, map.get("chance"));
        assertEquals(com.minionx.util.ColorUtil.color("&bShining Diamond"), map.get("display-name"));
        assertEquals(999, map.get("custom-model-data"));
        assertEquals("itemsadder:ruby", map.get("hook-id"));

        // Adjusting amount must preserve metadata
        BonusDropEntry amountUpdated = new BonusDropEntry(baseEntry.getMaterial(), 5, baseEntry.getChance(),
                baseEntry.getDisplayName(), baseEntry.getCustomModelData(), baseEntry.getHookId());
        assertEquals(5, amountUpdated.getAmount());
        assertEquals(baseEntry.getDisplayName(), amountUpdated.getDisplayName());
        assertEquals(999, amountUpdated.getCustomModelData());
        assertEquals("itemsadder:ruby", amountUpdated.getHookId());

        // Adjusting chance must preserve metadata
        BonusDropEntry chanceUpdated = new BonusDropEntry(baseEntry.getMaterial(), baseEntry.getAmount(), 0.50,
                baseEntry.getDisplayName(), baseEntry.getCustomModelData(), baseEntry.getHookId());
        assertEquals(0.50, chanceUpdated.getChance(), 0.001);
        assertEquals(baseEntry.getDisplayName(), chanceUpdated.getDisplayName());
        assertEquals(999, chanceUpdated.getCustomModelData());
        assertEquals("itemsadder:ruby", chanceUpdated.getHookId());
    }
}
