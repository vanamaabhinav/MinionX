package com.minionx;

import com.minionx.api.MinionType;
import com.minionx.core.Minion;
import com.minionx.storage.DatabaseStorage;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.sql.Connection;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class LegacyMigrationTest {

    private File tempDir;

    @BeforeEach
    public void setUp() throws Exception {
        tempDir = File.createTempFile("legacy_test_dir_", "");
        tempDir.delete();
        tempDir.mkdirs();
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (tempDir != null && tempDir.exists()) {
            deleteDirectory(tempDir);
        }
    }

    private void deleteDirectory(File f) {
        if (f.isDirectory()) {
            File[] files = f.listFiles();
            if (files != null) {
                for (File child : files) {
                    deleteDirectory(child);
                }
            }
        }
        f.delete();
    }

    // --- YAML Legacy Migration Acceptance Test ---

    @Test
    public void testLegacyYamlMigrationAndBackwardCompatibility() throws Exception {
        File dataFile = new File(tempDir, "data/minions.yml");
        dataFile.getParentFile().mkdirs();

        UUID legacyMinionId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        // 1. Write legacy v1 YAML format without new v2 fields (no skin, no shipping, no storage chest)
        YamlConfiguration legacyConfig = new YamlConfiguration();
        String path = "minions." + legacyMinionId;
        legacyConfig.set(path + ".owner", ownerId.toString());
        legacyConfig.set(path + ".type", "DIAMOND");
        legacyConfig.set(path + ".tier", 7);
        legacyConfig.set(path + ".world", "world");
        legacyConfig.set(path + ".x", 10.5);
        legacyConfig.set(path + ".y", 65.0);
        legacyConfig.set(path + ".z", 20.5);
        legacyConfig.set(path + ".fuel", "solar_panel");
        legacyConfig.set(path + ".fuel-end", -1L);
        legacyConfig.set(path + ".upgrade1", "auto_smelter");
        legacyConfig.set(path + ".upgrade2", "diamond_spreading");
        legacyConfig.set(path + ".last-action", 1700000000000L);
        legacyConfig.set(path + ".last-was-place", false);
        legacyConfig.set(path + ".harvested", 350);
        legacyConfig.set(path + ".xp", 45);
        legacyConfig.save(dataFile);

        // 2. Read legacy config and verify backwards compatibility defaults
        YamlConfiguration loaded = YamlConfiguration.loadConfiguration(dataFile);
        assertEquals("DIAMOND", loaded.getString(path + ".type"));
        assertEquals(7, loaded.getInt(path + ".tier"));
        assertEquals(350, loaded.getInt(path + ".harvested"));
        assertNull(loaded.getString(path + ".skin"), "Missing legacy skin should default to null");
        assertNull(loaded.getString(path + ".shipping"), "Missing legacy shipping should default to null");
        assertEquals(0.0, loaded.getDouble(path + ".shipping-coins", 0.0), 0.001);
        assertNull(loaded.getString(path + ".storage.id"), "Missing legacy storage should default to null");

        // 3. Upgrade record with v2 features and save
        loaded.set(path + ".skin", "spaceman_skin");
        loaded.set(path + ".shipping", "budget_hopper");
        loaded.set(path + ".shipping-coins", 150.25);
        loaded.set(path + ".storage.id", "medium_storage");
        loaded.set(path + ".storage.world", "world");
        loaded.set(path + ".storage.x", 11);
        loaded.set(path + ".storage.y", 65);
        loaded.set(path + ".storage.z", 20);
        loaded.save(dataFile);

        // 4. Reload and verify all fields preserved without data loss
        YamlConfiguration reloaded = YamlConfiguration.loadConfiguration(dataFile);
        assertEquals("DIAMOND", reloaded.getString(path + ".type"));
        assertEquals(7, reloaded.getInt(path + ".tier"));
        assertEquals(350, reloaded.getInt(path + ".harvested"));
        assertEquals("spaceman_skin", reloaded.getString(path + ".skin"));
        assertEquals("budget_hopper", reloaded.getString(path + ".shipping"));
        assertEquals(150.25, reloaded.getDouble(path + ".shipping-coins"), 0.001);
        assertEquals("medium_storage", reloaded.getString(path + ".storage.id"));
        assertEquals(11, reloaded.getInt(path + ".storage.x"));
    }

    // --- SQL Versioned Migration Acceptance Test ---

    @Test
    public void testSqlSchemaMigrationToV2WithCompositePrimaryKey() throws Exception {
        MockSqlHelper.State state = new MockSqlHelper.State();
        state.currentSchemaVersion = 0; // Legacy unversioned / v1 schema
        state.minionItemsTableExists = true;
        state.containerTypeColumnExists = false; // Missing container_type column

        Connection connection = MockSqlHelper.createMockConnection(state);
        DatabaseStorage storage = new DatabaseStorage(null);
        storage.setConnection(connection);

        // Trigger schema verification and migration
        storage.createTablesOrMigrate();

        // 1. Verify version table and pending operations table created
        boolean createdVersionTable = state.executedStatements.stream()
                .anyMatch(s -> s.contains("CREATE TABLE IF NOT EXISTS minion_schema_version"));
        assertTrue(createdVersionTable, "Must create minion_schema_version table");

        boolean createdPendingOpsTable = state.executedStatements.stream()
                .anyMatch(s -> s.contains("CREATE TABLE IF NOT EXISTS minion_pending_operations"));
        assertTrue(createdPendingOpsTable, "Must create minion_pending_operations table");

        // 2. Verify migration step recreated minion_items with container_type composite primary key
        boolean createdV2Items = state.executedStatements.stream()
                .anyMatch(s -> s.contains("CREATE TABLE minion_items_v2") &&
                        s.contains("PRIMARY KEY (minion_id, container_type, slot_index)"));
        assertTrue(createdV2Items, "Must create minion_items_v2 with composite primary key");

        // 3. Verify existing legacy items were copied over with container_type = 'INVENTORY'
        boolean copiedLegacyItems = state.executedStatements.stream()
                .anyMatch(s -> s.contains("INSERT INTO minion_items_v2") &&
                        s.contains("SELECT minion_id, 'INVENTORY', slot_index, item_data FROM minion_items"));
        assertTrue(copiedLegacyItems, "Must copy existing inventory items into v2 table preserving data");

        // 4. Verify table swap: drop old minion_items and rename minion_items_v2
        boolean droppedOld = state.executedStatements.stream().anyMatch(s -> s.contains("DROP TABLE minion_items"));
        boolean renamedV2 = state.executedStatements.stream().anyMatch(s -> s.contains("ALTER TABLE minion_items_v2 RENAME TO minion_items"));
        assertTrue(droppedOld, "Must drop old minion_items table");
        assertTrue(renamedV2, "Must rename minion_items_v2 to minion_items");

        // 5. Verify transaction boundaries during migration
        assertTrue(state.transactionEvents.contains("setAutoCommit:false"), "Migration must execute inside transaction");
        assertTrue(state.transactionEvents.contains("commit"), "Migration must commit transaction");

        // 6. Verify missing columns added to minions table
        List<String> expectedColumns = List.of("skin", "shipping_id", "shipping_coins", "storage_id", "storage_world", "storage_x", "storage_y", "storage_z");
        for (String col : expectedColumns) {
            boolean colAdded = state.executedStatements.stream()
                    .anyMatch(s -> s.contains("ALTER TABLE minions ADD COLUMN " + col));
            assertTrue(colAdded, "Must add missing column: " + col);
        }

        // 7. Verify version table updated to CURRENT_SCHEMA_VERSION (2)
        boolean versionUpdated = false;
        for (MockSqlHelper.PreparedStatementCall call : state.preparedCalls) {
            if (call.sql.contains("INSERT INTO minion_schema_version")) {
                versionUpdated = true;
                assertEquals(DatabaseStorage.CURRENT_SCHEMA_VERSION, call.parameters.get(1));
            }
        }
        assertTrue(versionUpdated, "Must record schema version update to version " + DatabaseStorage.CURRENT_SCHEMA_VERSION);
    }

    @Test
    public void testMultiContainerSnapshotCreation() {
        UUID minionId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Minion minion = new Minion(minionId, ownerId, MinionType.DIAMOND, 9, null);

        minion.setSkinId("spaceman_skin");
        minion.setShippingId("budget_hopper");
        minion.setShippingCoins(75.50);

        // Add internal inventory item
        minion.addItem(new ItemStack(Material.DIAMOND, 64));

        // Link external chest and add storage chest item
        minion.linkStorage(new Location(null, 20, 65, 20), "large_storage");
        minion.getStorageContents().add(new ItemStack(Material.DIAMOND, 32));

        DatabaseStorage.MinionSnapshot snapshot = DatabaseStorage.MinionSnapshot.fromMinion(minion);
        assertNotNull(snapshot);
        assertEquals(minionId, snapshot.id());
        assertEquals("DIAMOND", snapshot.type());
        assertEquals(9, snapshot.tier());
        assertEquals("spaceman_skin", snapshot.skinId());
        assertEquals("budget_hopper", snapshot.shippingId());
        assertEquals(75.50, snapshot.shippingCoins(), 0.001);
        assertEquals("large_storage", snapshot.storageId());

        // Internal inventory items snapshot
        assertEquals(1, snapshot.serializedInventoryItems().size());
        assertTrue(snapshot.serializedInventoryItems().getFirst().contains("DIAMOND"));

        // Linked chest items snapshot
        assertEquals(1, snapshot.serializedStorageItems().size());
        assertTrue(snapshot.serializedStorageItems().getFirst().contains("DIAMOND"));
    }
}
