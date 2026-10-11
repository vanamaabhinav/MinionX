package com.skyblock.minions;

import com.skyblock.minions.api.MinionType;
import com.skyblock.minions.core.Minion;
import com.skyblock.minions.core.MinionManager;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class GridFillingAndPlacementTest {

    @Test
    @DisplayName("MinionManager should accurately detect existing minion at target block location")
    void testMinionAtLocationOccupancy() {
        MinionManager manager = new MinionManager();

        UUID owner = UUID.randomUUID();
        Location loc1 = new Location(null, 100.5, 65.0, 200.5);
        Minion minion1 = new Minion(UUID.randomUUID(), owner, MinionType.COBBLESTONE, 1, loc1);

        assertNull(manager.getMinionAt(loc1));

        manager.register(minion1);

        // Same block coordinates should be identified as occupied
        Location sameBlock = new Location(null, 100.0, 65.0, 200.0);
        assertNotNull(manager.getMinionAt(sameBlock), "Should find minion at same block coordinates");
        assertEquals(minion1.getMinionId(), manager.getMinionAt(sameBlock).getMinionId());

        // Different block should not be occupied
        Location diffBlock = new Location(null, 101.0, 65.0, 200.0);
        assertNull(manager.getMinionAt(diffBlock), "Different block should be free");

        // After unregistering, the block should be free
        manager.unregister(minion1);
        assertNull(manager.getMinionAt(sameBlock), "Block should be free after unregister");
    }

    @Test
    @DisplayName("Default config should enable mob AI (disable-mob-wandering: false) and contain minion-already-exists message")
    void testDefaultCombatAndPlacementConfig() {
        InputStream stream = getClass().getClassLoader().getResourceAsStream("config.yml");
        assertNotNull(stream, "config.yml should be accessible in classpath");

        YamlConfiguration config = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        assertFalse(config.getBoolean("settings.combat.disable-mob-wandering"),
                "Mob wandering/AI should be enabled (disable-mob-wandering=false) by default");

        assertTrue(config.contains("messages.minion-already-exists"),
                "messages.minion-already-exists should be defined in config");
        assertNotNull(config.getString("messages.minion-already-exists"));
    }
}
