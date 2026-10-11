package com.skyblock.minions;

import com.skyblock.minions.api.MinionType;
import com.skyblock.minions.gui.OperatorHolder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class OperatorGUITest {

    @Test
    @DisplayName("Should initialize OperatorHolder with correct view types and context")
    void testOperatorHolder() {
        OperatorHolder mainHolder = new OperatorHolder(OperatorHolder.ViewType.MAIN);
        assertEquals(OperatorHolder.ViewType.MAIN, mainHolder.getViewType());
        assertNull(mainHolder.getContext());

        OperatorHolder tierHolder = new OperatorHolder(OperatorHolder.ViewType.MINION_TIERS, MinionType.DIAMOND);
        assertEquals(OperatorHolder.ViewType.MINION_TIERS, tierHolder.getViewType());
        assertEquals(MinionType.DIAMOND, tierHolder.getContext());
    }
}
