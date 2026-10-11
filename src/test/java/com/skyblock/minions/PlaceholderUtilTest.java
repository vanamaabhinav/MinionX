package com.skyblock.minions;

import com.skyblock.minions.api.MinionType;
import com.skyblock.minions.core.Minion;
import com.skyblock.minions.core.PlaceholderUtil;
import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class PlaceholderUtilTest {

    @Test
    @DisplayName("Should evaluate basic placeholders and inline math formulas")
    void testBasicPlaceholdersAndMath() {
        Minion minion = new Minion(UUID.randomUUID(), UUID.randomUUID(), MinionType.COBBLESTONE, 5, null);

        // Command template: eco give %player% {amount * 2}
        String cmdTemplate = "skills addexp %player% mining {amount * 3.5}";
        String parsed = PlaceholderUtil.parse(cmdTemplate, null, minion, Material.COBBLESTONE, 10);

        // Amount is 10, {10 * 3.5} = 35
        assertEquals("skills addexp %player% mining 35", parsed);
    }

    @Test
    @DisplayName("Should evaluate minion placeholders")
    void testMinionPlaceholders() {
        Minion minion = new Minion(UUID.randomUUID(), UUID.randomUUID(), MinionType.DIAMOND, 11, null);
        minion.setTotalHarvested(500);

        String template = "collection add %player% %minion_type_id% %amount% (Tier: %minion_tier_roman%, Total: %total_harvested%)";
        String parsed = PlaceholderUtil.parse(template, null, minion, Material.DIAMOND, 64);

        assertEquals("collection add %player% diamond 64 (Tier: XI, Total: 500)", parsed);
    }

    @Test
    @DisplayName("Should evaluate mathematical expressions correctly")
    void testMathExpressions() {
        assertEquals(25.0, PlaceholderUtil.simpleEval("10 + 15"), 0.001);
        assertEquals(40.0, PlaceholderUtil.simpleEval("20 * 2"), 0.001);
        assertEquals(7.5, PlaceholderUtil.simpleEval("15 / 2"), 0.001);
        assertEquals(12.0, PlaceholderUtil.simpleEval("20 - 8"), 0.001);
    }
}
