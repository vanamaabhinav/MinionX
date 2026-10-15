package com.minionx.storage;

import com.minionx.MinionX;
import com.minionx.api.MinionType;
import com.minionx.core.Minion;
import com.minionx.core.MinionManager;
import com.minionx.visual.MinionHologram;
import com.minionx.visual.MinionVisual;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.logging.Level;

/**
 * Robust SQL database storage supporting SQLite and MySQL with versioned migrations,
 * multi-container item persistence, and crash-resilient pending operation journaling.
 */
public class DatabaseStorage {

    public static final int CURRENT_SCHEMA_VERSION = 2;

    private final MinionX plugin;
    private Connection connection;

    public DatabaseStorage(MinionX plugin) {
        this.plugin = plugin;
    }

    public synchronized Connection getConnection() {
        return connection;
    }

    public synchronized void setConnection(Connection connection) {
        this.connection = connection;
    }

    public synchronized void ensureConnection() throws SQLException {
        if (connection == null || connection.isClosed() || !connection.isValid(2)) {
            init();
        }
    }

    public boolean init() {
        FileConfiguration config = plugin.getConfig();
        String type = config.getString("storage.type", "SQLITE").toUpperCase();

        try {
            if ("MYSQL".equals(type)) {
                String host = config.getString("storage.mysql.host", "localhost");
                int port = config.getInt("storage.mysql.port", 3306);
                String database = config.getString("storage.mysql.database", "minions");
                String user = config.getString("storage.mysql.username", "root");
                String password = config.getString("storage.mysql.password", "");
                String url = "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&autoReconnect=true";
                connection = DriverManager.getConnection(url, user, password);
            } else {
                // SQLite
                File dbFile = new File(plugin.getDataFolder(), "data/minions.db");
                if (!dbFile.getParentFile().exists()) dbFile.getParentFile().mkdirs();
                connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            }

            createTablesOrMigrate();
            return true;
        } catch (SQLException e) {
            logSevere("Failed to connect to database: " + e.getMessage(), e);
            return false;
        }
    }

    public synchronized void createTablesOrMigrate() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            // Version tracking table
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS minion_schema_version (" +
                    "version INT PRIMARY KEY, " +
                    "applied_at BIGINT NOT NULL" +
                    ")");

            // Pending operations table for durable intent journaling
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS minion_pending_operations (" +
                    "operation_id VARCHAR(36) PRIMARY KEY, " +
                    "minion_id VARCHAR(36) NOT NULL, " +
                    "player_uuid VARCHAR(36) NOT NULL, " +
                    "state VARCHAR(32) NOT NULL, " +
                    "created_at BIGINT NOT NULL" +
                    ")");

            // Check current schema version
            int currentVersion = 0;
            try (ResultSet rs = stmt.executeQuery("SELECT MAX(version) AS v FROM minion_schema_version")) {
                if (rs.next()) {
                    currentVersion = rs.getInt("v");
                }
            }

            // Create initial tables if they don't exist
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS minions (" +
                    "id VARCHAR(36) PRIMARY KEY, " +
                    "owner VARCHAR(36), " +
                    "type VARCHAR(32) NOT NULL, " +
                    "tier INT NOT NULL, " +
                    "world VARCHAR(64) NOT NULL, " +
                    "x DOUBLE NOT NULL, " +
                    "y DOUBLE NOT NULL, " +
                    "z DOUBLE NOT NULL, " +
                    "yaw FLOAT NOT NULL, " +
                    "pitch FLOAT NOT NULL, " +
                    "fuel VARCHAR(64), " +
                    "fuel_end BIGINT, " +
                    "upgrade1 VARCHAR(64), " +
                    "upgrade2 VARCHAR(64), " +
                    "skin VARCHAR(64), " +
                    "shipping_id VARCHAR(64), " +
                    "shipping_coins DOUBLE DEFAULT 0.0, " +
                    "storage_id VARCHAR(64), " +
                    "storage_world VARCHAR(64), " +
                    "storage_x INT, " +
                    "storage_y INT, " +
                    "storage_z INT, " +
                    "last_action BIGINT, " +
                    "last_was_place INT, " +
                    "harvested INT, " +
                    "xp INT, " +
                    "custom_id VARCHAR(64)" +
                    ")");

            // Check if minion_items table exists
            boolean minionItemsExists = tableExists(connection, "minion_items");
            if (!minionItemsExists) {
                stmt.executeUpdate("CREATE TABLE minion_items (" +
                        "minion_id VARCHAR(36) NOT NULL, " +
                        "container_type VARCHAR(16) NOT NULL, " +
                        "slot_index INT NOT NULL, " +
                        "item_data TEXT NOT NULL, " +
                        "PRIMARY KEY (minion_id, container_type, slot_index)" +
                        ")");
            }

            // Perform versioned migration if needed
            if (currentVersion < CURRENT_SCHEMA_VERSION) {
                performMigration(currentVersion);
            }
        }
    }

    private void performMigration(int fromVersion) throws SQLException {
        logInfo("Migrating SQL schema from version " + fromVersion + " to " + CURRENT_SCHEMA_VERSION + "...");

        // 1. Ensure all missing columns exist in minions table
        addColumnIfNotExists(connection, "minions", "skin", "VARCHAR(64)");
        addColumnIfNotExists(connection, "minions", "shipping_id", "VARCHAR(64)");
        addColumnIfNotExists(connection, "minions", "shipping_coins", "DOUBLE DEFAULT 0.0");
        addColumnIfNotExists(connection, "minions", "storage_id", "VARCHAR(64)");
        addColumnIfNotExists(connection, "minions", "storage_world", "VARCHAR(64)");
        addColumnIfNotExists(connection, "minions", "storage_x", "INT");
        addColumnIfNotExists(connection, "minions", "storage_y", "INT");
        addColumnIfNotExists(connection, "minions", "storage_z", "INT");

        // 2. Ensure container_type exists in minion_items with composite primary key
        if (!columnExists(connection, "minion_items", "container_type")) {
            boolean isMySQL = isMySQL(connection);
            if (isMySQL) {
                try (Statement s = connection.createStatement()) {
                    s.executeUpdate("ALTER TABLE minion_items ADD COLUMN container_type VARCHAR(16) NOT NULL DEFAULT 'INVENTORY'");
                    s.executeUpdate("ALTER TABLE minion_items DROP PRIMARY KEY, ADD PRIMARY KEY (minion_id, container_type, slot_index)");
                }
            } else {
                // SQLite: Table recreation migration preserving existing inventory items
                connection.setAutoCommit(false);
                try (Statement s = connection.createStatement()) {
                    s.executeUpdate("CREATE TABLE minion_items_v2 (" +
                            "minion_id VARCHAR(36) NOT NULL, " +
                            "container_type VARCHAR(16) NOT NULL, " +
                            "slot_index INT NOT NULL, " +
                            "item_data TEXT NOT NULL, " +
                            "PRIMARY KEY (minion_id, container_type, slot_index)" +
                            ")");

                    s.executeUpdate("INSERT INTO minion_items_v2 (minion_id, container_type, slot_index, item_data) " +
                            "SELECT minion_id, 'INVENTORY', slot_index, item_data FROM minion_items");

                    s.executeUpdate("DROP TABLE minion_items");
                    s.executeUpdate("ALTER TABLE minion_items_v2 RENAME TO minion_items");
                    connection.commit();
                } catch (SQLException ex) {
                    connection.rollback();
                    throw ex;
                } finally {
                    connection.setAutoCommit(true);
                }
            }
        }

        // 3. Update version table
        try (PreparedStatement ps = connection.prepareStatement("INSERT INTO minion_schema_version (version, applied_at) VALUES (?,?) " +
                (isMySQL(connection) ? "ON DUPLICATE KEY UPDATE applied_at = VALUES(applied_at)" : "ON CONFLICT(version) DO UPDATE SET applied_at = excluded.applied_at"))) {
            ps.setInt(1, CURRENT_SCHEMA_VERSION);
            ps.setLong(2, System.currentTimeMillis());
            ps.executeUpdate();
        }

        logInfo("SQL schema successfully migrated to version " + CURRENT_SCHEMA_VERSION);
    }

    private boolean isMySQL(Connection conn) {
        try {
            DatabaseMetaData meta = conn.getMetaData();
            return meta != null && meta.getDatabaseProductName() != null && meta.getDatabaseProductName().toLowerCase().contains("mysql");
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean tableExists(Connection conn, String table) {
        try {
            DatabaseMetaData meta = conn.getMetaData();
            try (ResultSet rs = meta.getTables(null, null, table, null)) {
                if (rs.next()) return true;
            }
            try (ResultSet rs = meta.getTables(null, null, table.toUpperCase(), null)) {
                if (rs.next()) return true;
            }
        } catch (SQLException ignored) {}
        return false;
    }

    private boolean columnExists(Connection conn, String table, String column) {
        try {
            DatabaseMetaData meta = conn.getMetaData();
            try (ResultSet rs = meta.getColumns(null, null, table, null)) {
                while (rs.next()) {
                    String col = rs.getString("COLUMN_NAME");
                    if (col != null && col.equalsIgnoreCase(column)) return true;
                }
            }
            try (ResultSet rs = meta.getColumns(null, null, table.toUpperCase(), null)) {
                while (rs.next()) {
                    String col = rs.getString("COLUMN_NAME");
                    if (col != null && col.equalsIgnoreCase(column)) return true;
                }
            }
            // SQLite PRAGMA fallback
            try (Statement s = conn.createStatement();
                 ResultSet rs = s.executeQuery("PRAGMA table_info(" + table + ")")) {
                while (rs.next()) {
                    String name = rs.getString("name");
                    if (name != null && name.equalsIgnoreCase(column)) return true;
                }
            } catch (SQLException ignored) {}
        } catch (SQLException ignored) {}
        return false;
    }

    private void addColumnIfNotExists(Connection conn, String table, String column, String definition) {
        if (!columnExists(conn, table, column)) {
            try (Statement s = conn.createStatement()) {
                s.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
            } catch (SQLException e) {
                logFine("Column add notice for " + table + "." + column + ": " + e.getMessage());
            }
        }
    }

    public record MinionSnapshot(
            UUID id,
            UUID ownerId,
            String type,
            int tier,
            String world,
            double x, double y, double z,
            float yaw, float pitch,
            String fuelId,
            long fuelEndTime,
            String upgrade1Id,
            String upgrade2Id,
            String skinId,
            String shippingId,
            double shippingCoins,
            String storageId,
            String storageWorld,
            Integer storageX,
            Integer storageY,
            Integer storageZ,
            long lastActionTime,
            boolean lastActionWasPlace,
            int totalHarvested,
            int storedXp,
            String customDefinitionId,
            List<String> serializedInventoryItems,
            List<String> serializedStorageItems
    ) {
        public List<String> serializedItems() {
            return serializedInventoryItems;
        }

        public List<SerializedItemRecord> toSerializedItemRecords() {
            List<SerializedItemRecord> list = new ArrayList<>();
            int slot = 0;
            for (String data : serializedInventoryItems) {
                list.add(new SerializedItemRecord("INVENTORY", slot++, data));
            }
            int sSlot = 0;
            for (String data : serializedStorageItems) {
                list.add(new SerializedItemRecord("STORAGE", sSlot++, data));
            }
            return list;
        }

        public static MinionSnapshot fromMinion(Minion m) {
            Location loc = m.getLocation();
            String worldName = (loc != null && loc.getWorld() != null) ? loc.getWorld().getName() : "world";
            double x = loc != null ? loc.getX() : 0.0;
            double y = loc != null ? loc.getY() : 0.0;
            double z = loc != null ? loc.getZ() : 0.0;
            float yaw = loc != null ? loc.getYaw() : 0.0f;
            float pitch = loc != null ? loc.getPitch() : 0.0f;

            List<String> invItems = new ArrayList<>();
            for (ItemStack item : m.getInventory()) {
                if (item != null) {
                    try {
                        YamlConfiguration yc = new YamlConfiguration();
                        yc.set("item", item);
                        invItems.add(yc.saveToString());
                    } catch (Throwable t) {
                        invItems.add("type: " + item.getType().name() + "\namount: " + item.getAmount());
                    }
                }
            }

            List<String> storageItems = new ArrayList<>();
            for (ItemStack item : m.getStorageContents()) {
                if (item != null) {
                    try {
                        YamlConfiguration yc = new YamlConfiguration();
                        yc.set("item", item);
                        storageItems.add(yc.saveToString());
                    } catch (Throwable t) {
                        storageItems.add("type: " + item.getType().name() + "\namount: " + item.getAmount());
                    }
                }
            }

            String stId = m.getStorageId();
            String stWorld = null;
            Integer stX = null, stY = null, stZ = null;
            if (m.hasLinkedStorage() && m.getLinkedStorageLocation() != null) {
                Location sl = m.getLinkedStorageLocation();
                stWorld = sl.getWorld() != null ? sl.getWorld().getName() : null;
                stX = sl.getBlockX();
                stY = sl.getBlockY();
                stZ = sl.getBlockZ();
            }

            return new MinionSnapshot(
                    m.getMinionId(),
                    m.getOwnerId(),
                    m.getType() != null ? m.getType().name() : "COBBLESTONE",
                    m.getTier(),
                    worldName,
                    x, y, z,
                    yaw, pitch,
                    m.getFuelId(),
                    m.getFuelEndTime(),
                    m.getUpgrade1Id(),
                    m.getUpgrade2Id(),
                    m.getSkinId(),
                    m.getShippingId(),
                    m.getShippingCoins(),
                    stId,
                    stWorld,
                    stX, stY, stZ,
                    m.getLastActionTime(),
                    m.wasLastActionPlace(),
                    m.getTotalHarvested(),
                    m.getStoredXp(),
                    m.getCustomDefinitionId(),
                    invItems,
                    storageItems
            );
        }
    }

    public synchronized void saveAll(MinionManager manager) {
        if (manager == null) return;
        List<MinionSnapshot> snapshots = new ArrayList<>();
        for (Minion m : manager.getAllMinions()) {
            snapshots.add(MinionSnapshot.fromMinion(m));
        }
        saveSnapshots(snapshots);
    }

    public synchronized void saveSnapshots(List<MinionSnapshot> snapshots) {
        if (snapshots == null) return;
        try {
            ensureConnection();
            if (connection == null) return;
            connection.setAutoCommit(false);

            boolean isMySQL = isMySQL(connection);

            String upsertMinion;
            if (isMySQL) {
                upsertMinion = "INSERT INTO minions (id, owner, type, tier, world, x, y, z, yaw, pitch, fuel, fuel_end, upgrade1, upgrade2, skin, shipping_id, shipping_coins, storage_id, storage_world, storage_x, storage_y, storage_z, last_action, last_was_place, harvested, xp, custom_id) " +
                        "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) " +
                        "ON DUPLICATE KEY UPDATE " +
                        "owner=VALUES(owner), type=VALUES(type), tier=VALUES(tier), world=VALUES(world), " +
                        "x=VALUES(x), y=VALUES(y), z=VALUES(z), yaw=VALUES(yaw), pitch=VALUES(pitch), " +
                        "fuel=VALUES(fuel), fuel_end=VALUES(fuel_end), upgrade1=VALUES(upgrade1), upgrade2=VALUES(upgrade2), " +
                        "skin=VALUES(skin), shipping_id=VALUES(shipping_id), shipping_coins=VALUES(shipping_coins), " +
                        "storage_id=VALUES(storage_id), storage_world=VALUES(storage_world), storage_x=VALUES(storage_x), storage_y=VALUES(storage_y), storage_z=VALUES(storage_z), " +
                        "last_action=VALUES(last_action), last_was_place=VALUES(last_was_place), harvested=VALUES(harvested), " +
                        "xp=VALUES(xp), custom_id=VALUES(custom_id)";
            } else {
                upsertMinion = "INSERT INTO minions (id, owner, type, tier, world, x, y, z, yaw, pitch, fuel, fuel_end, upgrade1, upgrade2, skin, shipping_id, shipping_coins, storage_id, storage_world, storage_x, storage_y, storage_z, last_action, last_was_place, harvested, xp, custom_id) " +
                        "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) " +
                        "ON CONFLICT(id) DO UPDATE SET " +
                        "owner=excluded.owner, type=excluded.type, tier=excluded.tier, world=excluded.world, " +
                        "x=excluded.x, y=excluded.y, z=excluded.z, yaw=excluded.yaw, pitch=excluded.pitch, " +
                        "fuel=excluded.fuel, fuel_end=excluded.fuel_end, upgrade1=excluded.upgrade1, upgrade2=excluded.upgrade2, " +
                        "skin=excluded.skin, shipping_id=excluded.shipping_id, shipping_coins=excluded.shipping_coins, " +
                        "storage_id=excluded.storage_id, storage_world=excluded.storage_world, storage_x=excluded.storage_x, storage_y=excluded.storage_y, storage_z=excluded.storage_z, " +
                        "last_action=excluded.last_action, last_was_place=excluded.last_was_place, harvested=excluded.harvested, " +
                        "xp=excluded.xp, custom_id=excluded.custom_id";
            }

            // 1. Remove deleted minions that are no longer in active snapshots
            Set<String> activeIds = new HashSet<>();
            for (MinionSnapshot s : snapshots) {
                activeIds.add(s.id().toString());
            }

            try (Statement checkStmt = connection.createStatement();
                 ResultSet rs = checkStmt.executeQuery("SELECT id FROM minions")) {
                List<String> toDelete = new ArrayList<>();
                while (rs.next()) {
                    String existingId = rs.getString("id");
                    if (!activeIds.contains(existingId)) {
                        toDelete.add(existingId);
                    }
                }
                if (!toDelete.isEmpty()) {
                    try (PreparedStatement delMinion = connection.prepareStatement("DELETE FROM minions WHERE id = ?");
                         PreparedStatement delItems = connection.prepareStatement("DELETE FROM minion_items WHERE minion_id = ?")) {
                        for (String delId : toDelete) {
                            delMinion.setString(1, delId);
                            delMinion.addBatch();
                            delItems.setString(1, delId);
                            delItems.addBatch();
                        }
                        delMinion.executeBatch();
                        delItems.executeBatch();
                    }
                }
            }

            // 2. Batch UPSERT active minions and update multi-container items atomically
            String deleteItemsForMinion = "DELETE FROM minion_items WHERE minion_id = ?";
            String insertItem = "INSERT INTO minion_items (minion_id, container_type, slot_index, item_data) VALUES (?,?,?,?)";

            try (PreparedStatement psM = connection.prepareStatement(upsertMinion);
                 PreparedStatement psDelI = connection.prepareStatement(deleteItemsForMinion);
                 PreparedStatement psInsI = connection.prepareStatement(insertItem)) {

                for (MinionSnapshot s : snapshots) {
                    psM.setString(1, s.id().toString());
                    psM.setString(2, s.ownerId() != null ? s.ownerId().toString() : null);
                    psM.setString(3, s.type());
                    psM.setInt(4, s.tier());
                    psM.setString(5, s.world());
                    psM.setDouble(6, s.x());
                    psM.setDouble(7, s.y());
                    psM.setDouble(8, s.z());
                    psM.setFloat(9, s.yaw());
                    psM.setFloat(10, s.pitch());
                    psM.setString(11, s.fuelId());
                    psM.setLong(12, s.fuelEndTime());
                    psM.setString(13, s.upgrade1Id());
                    psM.setString(14, s.upgrade2Id());
                    psM.setString(15, s.skinId());
                    psM.setString(16, s.shippingId());
                    psM.setDouble(17, s.shippingCoins());
                    psM.setString(18, s.storageId());
                    psM.setString(19, s.storageWorld());
                    if (s.storageX() != null) psM.setInt(20, s.storageX()); else psM.setNull(20, Types.INTEGER);
                    if (s.storageY() != null) psM.setInt(21, s.storageY()); else psM.setNull(21, Types.INTEGER);
                    if (s.storageZ() != null) psM.setInt(22, s.storageZ()); else psM.setNull(22, Types.INTEGER);
                    psM.setLong(23, s.lastActionTime());
                    psM.setInt(24, s.lastActionWasPlace() ? 1 : 0);
                    psM.setInt(25, s.totalHarvested());
                    psM.setInt(26, s.storedXp());
                    psM.setString(27, s.customDefinitionId());
                    psM.addBatch();

                    psDelI.setString(1, s.id().toString());
                    psDelI.addBatch();

                    int slot = 0;
                    for (String itemData : s.serializedInventoryItems()) {
                        psInsI.setString(1, s.id().toString());
                        psInsI.setString(2, "INVENTORY");
                        psInsI.setInt(3, slot++);
                        psInsI.setString(4, itemData);
                        psInsI.addBatch();
                    }

                    int storageSlot = 0;
                    for (String itemData : s.serializedStorageItems()) {
                        psInsI.setString(1, s.id().toString());
                        psInsI.setString(2, "STORAGE");
                        psInsI.setInt(3, storageSlot++);
                        psInsI.setString(4, itemData);
                        psInsI.addBatch();
                    }
                }

                psM.executeBatch();
                psDelI.executeBatch();
                psInsI.executeBatch();
            }

            connection.commit();
            connection.setAutoCommit(true);
            logInfo("Saved " + snapshots.size() + " minions to SQL database.");
        } catch (SQLException e) {
            logSevere("Failed to save minions to database! Rolling back transaction.", e);
            try {
                if (connection != null && !connection.isClosed()) {
                    connection.rollback();
                    connection.setAutoCommit(true);
                }
            } catch (SQLException rollbackEx) {
                logSevere("Failed to rollback database transaction!", rollbackEx);
            }
        }
    }

    public synchronized void loadAll(MinionManager manager) {
        try {
            ensureConnection();
        } catch (SQLException ignored) {}
        if (connection == null) return;

        // 1. Identify quarantined minions with pending operations
        Set<UUID> quarantinedMinionIds = new HashSet<>();
        try {
            List<PendingOperation> pending = getPendingOperations();
            for (PendingOperation op : pending) {
                quarantinedMinionIds.add(op.minionId());
                logWarning("Minion " + op.minionId() + " has pending operation " + op.operationId() +
                        " (" + op.state() + ") by player " + op.playerUuid() + ". Quarantining from active ticking.");
            }
        } catch (SQLException e) {
            logSevere("Failed to query pending operations during startup!", e);
        }

        int loadedCount = 0;
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM minions")) {

            while (rs.next()) {
                UUID id = UUID.fromString(rs.getString("id"));
                if (quarantinedMinionIds.contains(id)) {
                    // Do not load quarantined minion into active runtime
                    continue;
                }

                String ownerStr = rs.getString("owner");
                UUID ownerId = ownerStr != null ? UUID.fromString(ownerStr) : null;
                MinionType type = MinionType.fromString(rs.getString("type"));
                if (type == null) continue;

                int tier = rs.getInt("tier");
                String worldName = rs.getString("world");
                World world = Bukkit.getWorld(worldName);
                if (world == null) continue;

                Location loc = new Location(world, rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"), rs.getFloat("yaw"), rs.getFloat("pitch"));
                Minion minion = new Minion(id, ownerId, type, tier, loc);

                String fuelStr = rs.getString("fuel");
                if (fuelStr != null && !fuelStr.isBlank()) {
                    minion.setFuel(fuelStr);
                    minion.setFuelEndTime(rs.getLong("fuel_end"));
                }

                String up1 = rs.getString("upgrade1");
                if (up1 != null) minion.setUpgrade1Id(up1);
                String up2 = rs.getString("upgrade2");
                if (up2 != null) minion.setUpgrade2Id(up2);

                String skin = rs.getString("skin");
                if (skin != null) minion.setSkinId(skin);

                String shipping = rs.getString("shipping_id");
                if (shipping != null) minion.setShippingId(shipping);
                minion.setShippingCoins(rs.getDouble("shipping_coins"));

                String stId = rs.getString("storage_id");
                String stWorld = rs.getString("storage_world");
                if (stId != null && stWorld != null) {
                    World sw = Bukkit.getWorld(stWorld);
                    if (sw != null) {
                        minion.linkStorage(new Location(sw, rs.getInt("storage_x"), rs.getInt("storage_y"), rs.getInt("storage_z")), stId);
                    }
                }

                minion.setLastActionTime(rs.getLong("last_action"));
                minion.setLastActionWasPlace(rs.getInt("last_was_place") == 1);
                minion.setTotalHarvested(rs.getInt("harvested"));
                minion.setStoredXp(rs.getInt("xp"));
                minion.setCustomDefinitionId(rs.getString("custom_id"));

                // Load items across both INVENTORY and STORAGE containers
                try (PreparedStatement psI = connection.prepareStatement("SELECT container_type, item_data FROM minion_items WHERE minion_id = ? ORDER BY slot_index ASC")) {
                    psI.setString(1, id.toString());
                    try (ResultSet rsI = psI.executeQuery()) {
                        while (rsI.next()) {
                            String cType = rsI.getString("container_type");
                            String yamlStr = rsI.getString("item_data");
                            YamlConfiguration yc = new YamlConfiguration();
                            yc.loadFromString(yamlStr);
                            ItemStack item = yc.getItemStack("item");
                            if (item != null) {
                                if ("STORAGE".equalsIgnoreCase(cType)) {
                                    minion.getStorageContents().add(item);
                                } else {
                                    minion.getInventory().add(item);
                                }
                            }
                        }
                    }
                }

                manager.register(minion);
                if (loc.isChunkLoaded()) {
                    MinionVisual.spawnOrUpdate(minion);
                    MinionHologram.updateHologram(minion);
                }
                loadedCount++;
            }

            plugin.getLogger().info("Successfully loaded " + loadedCount + " minions from SQL database.");
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to load minions from database: " + e.getMessage(), e);
        }
    }

    // --- Durable Operations Journal & Finalization ---

    public synchronized void recordPendingOperation(PendingOperation op) throws SQLException {
        ensureConnection();
        String sql = isMySQL(connection) ?
                "INSERT INTO minion_pending_operations (operation_id, minion_id, player_uuid, state, created_at) VALUES (?,?,?,?,?) ON DUPLICATE KEY UPDATE state=VALUES(state)" :
                "INSERT INTO minion_pending_operations (operation_id, minion_id, player_uuid, state, created_at) VALUES (?,?,?,?,?) ON CONFLICT(operation_id) DO UPDATE SET state=excluded.state";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, op.operationId());
            ps.setString(2, op.minionId().toString());
            ps.setString(3, op.playerUuid().toString());
            ps.setString(4, op.state());
            ps.setLong(5, op.createdAt());
            ps.executeUpdate();
        }
    }

    public synchronized void removePendingOperation(String operationId) throws SQLException {
        ensureConnection();
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM minion_pending_operations WHERE operation_id = ?")) {
            ps.setString(1, operationId);
            ps.executeUpdate();
        }
    }

    public synchronized List<PendingOperation> getPendingOperations() throws SQLException {
        ensureConnection();
        List<PendingOperation> list = new ArrayList<>();
        try (Statement s = connection.createStatement();
             ResultSet rs = s.executeQuery("SELECT operation_id, minion_id, player_uuid, state, created_at FROM minion_pending_operations")) {
            while (rs.next()) {
                list.add(new PendingOperation(
                        rs.getString("operation_id"),
                        UUID.fromString(rs.getString("minion_id")),
                        UUID.fromString(rs.getString("player_uuid")),
                        rs.getString("state"),
                        rs.getLong("created_at")
                ));
            }
        }
        return list;
    }

    /**
     * Atomically deletes the specific pending operation, minion record, and all its items
     * in a single ACID database transaction.
     */
    public synchronized boolean finalizePickup(String operationId, UUID minionId) {
        try {
            ensureConnection();
            connection.setAutoCommit(false);

            try (PreparedStatement psOp = connection.prepareStatement("DELETE FROM minion_pending_operations WHERE operation_id = ?");
                 PreparedStatement psItems = connection.prepareStatement("DELETE FROM minion_items WHERE minion_id = ?");
                 PreparedStatement psMinion = connection.prepareStatement("DELETE FROM minions WHERE id = ?")) {

                psOp.setString(1, operationId);
                psOp.executeUpdate();

                psItems.setString(1, minionId.toString());
                psItems.executeUpdate();

                psMinion.setString(1, minionId.toString());
                psMinion.executeUpdate();

                connection.commit();
                connection.setAutoCommit(true);
                return true;
            } catch (SQLException e) {
                connection.rollback();
                connection.setAutoCommit(true);
                logSevere("Failed to finalize pickup operation: " + operationId, e);
                return false;
            }
        } catch (SQLException e) {
            logSevere("Database error during finalizePickup: " + operationId, e);
            return false;
        }
    }

    private void logInfo(String msg) {
        if (plugin != null) plugin.getLogger().info(msg);
    }

    private void logFine(String msg) {
        if (plugin != null) plugin.getLogger().fine(msg);
    }

    private void logWarning(String msg) {
        if (plugin != null) plugin.getLogger().warning(msg);
    }

    private void logSevere(String msg, Throwable t) {
        if (plugin != null) plugin.getLogger().log(Level.SEVERE, msg, t);
    }

    public synchronized boolean deleteMinionData(UUID minionId) {
        if (minionId == null) return false;
        try {
            ensureConnection();
            if (connection == null) return false;
            connection.setAutoCommit(false);
            try (PreparedStatement psI = connection.prepareStatement("DELETE FROM minion_items WHERE minion_id = ?");
                 PreparedStatement psM = connection.prepareStatement("DELETE FROM minions WHERE id = ?")) {
                psI.setString(1, minionId.toString());
                psI.executeUpdate();
                psM.setString(1, minionId.toString());
                psM.executeUpdate();
                connection.commit();
                connection.setAutoCommit(true);
                return true;
            } catch (SQLException e) {
                connection.rollback();
                connection.setAutoCommit(true);
                logSevere("Failed to delete minion data for tombstone: " + minionId, e);
                return false;
            }
        } catch (SQLException e) {
            logSevere("Database error during deleteMinionData: " + minionId, e);
            return false;
        }
    }

    public synchronized boolean createBackup(File targetBackupFile) {
        if (targetBackupFile == null) return false;
        try {
            ensureConnection();
            if (connection == null) return false;

            if (targetBackupFile.exists()) {
                targetBackupFile.delete();
            }
            File parent = targetBackupFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            String path = targetBackupFile.getAbsolutePath().replace('\\', '/');
            try (Statement s = connection.createStatement()) {
                s.executeUpdate("VACUUM INTO '" + path + "'");
            }
            logInfo("Created online database backup: " + targetBackupFile.getName());
            return true;
        } catch (SQLException e) {
            logSevere("Failed to create online database backup: " + targetBackupFile.getName(), e);
            return false;
        }
    }

    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {}
        }
    }
}
