package com.minionx;

import com.minionx.api.MinionType;
import com.minionx.gui.OperatorHolder;
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

    @Test
    @DisplayName("Should verify OperatorCommand implements TabCompleter and returns empty completions")
    void testOperatorCommandTabCompletion() {
        com.minionx.command.OperatorCommand cmd = new com.minionx.command.OperatorCommand();
        assertNotNull(cmd.onTabComplete(null, null, "minionx", new String[]{}));
        assertTrue(cmd.onTabComplete(null, null, "minionx", new String[]{}).isEmpty());
    }

    @Test
    @DisplayName("Should initialize OperatorHolder with MinionDefinition context")
    void testOperatorHolderWithCustomDefinition() {
        com.minionx.gui.MinionCreatorSession session = new com.minionx.gui.MinionCreatorSession("ruby_minion");
        com.minionx.api.MinionDefinition def = session.buildDefinition();
        OperatorHolder defHolder = new OperatorHolder(OperatorHolder.ViewType.MINION_TIERS, def);
        assertEquals(OperatorHolder.ViewType.MINION_TIERS, defHolder.getViewType());
        assertEquals(def, defHolder.getContext());
    }
}
