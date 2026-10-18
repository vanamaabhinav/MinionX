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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.util.*;
import java.util.logging.Level;

/**
 * Robust SQL database storage supporting SQLite and MySQL with versioned migrations,
 * multi-container item persistence, and crash-resilient pending operation journaling.
 */
public class DatabaseStorage {

    public static final int CURRENT_SCHEMA_VERSION = 3;

    private final MinionX plugin;
    private Connection connection;
    private final Set<UUID> quarantinedMinions = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public DatabaseStorage(MinionX plugin) {
        this.plugin = plugin;
    }

    public void quarantineMinion(UUID minionId) {
        if (minionId != null) {
            quarantinedMinions.add(minionId);
            if (plugin != null && plugin.getMinionStorage() != null) {
                plugin.getMinionStorage().quarantineMinion(minionId);
            }
        }
    }

    public boolean isQuarantined(UUID minionId) {
        return minionId != null && (quarantinedMinions.contains(minionId) ||
                (plugin != null && plugin.getMinionStorage() != null && plugin.getMinionStorage().isQuarantined(minionId)));
    }

    public void unquarantineMinion(UUID minionId) {
        if (minionId != null) {
            quarantinedMinions.remove(minionId);
            if (plugin != null && plugin.getMinionStorage() != null) {
                plugin.getMinionStorage().unquarantineMinion(minionId);
            }
        }
    }

    public synchronized Connection getConnection() {
        return connection;
    }

    public synchronized void setConnection(Connection connection) {
        this.connection = connection;
    }

    public synchronized void ensureConnection() throws SQLException {
        if (connection == null || connection.isClosed() || !connection.isValid(2)) {
            if (plugin != null) {
                init();
            }
        }
    }

    public boolean init() {
        if (plugin == null) return false;
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
                    "operation_id VARCHAR(64) PRIMARY KEY, " +
                    "minion_id VARCHAR(36) NOT NULL, " +
                    "player_uuid VARCHAR(36) NOT NULL, " +
                    "state VARCHAR(32) NOT NULL, " +
                    "created_at BIGINT NOT NULL, " +
                    "payload TEXT" +
                    ")");

            // Permanent tombstone resurrection fence table
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS minion_tombstones (" +
                    "minion_id VARCHAR(36) PRIMARY KEY, " +
                    "operation_id VARCHAR(64), " +
                    "deleted_revision BIGINT NOT NULL, " +
                    "deleted_at BIGINT NOT NULL" +
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
                    "custom_id VARCHAR(64), " +
                    "state_revision BIGINT DEFAULT 0, " +
                    "fractional_progress BIGINT DEFAULT 0, " +
                    "drop_remainders TEXT" +
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
        addColumnIfNotExists(connection, "minions", "state_revision", "BIGINT DEFAULT 0");
        addColumnIfNotExists(connection, "minions", "fractional_progress", "BIGINT DEFAULT 0");
        addColumnIfNotExists(connection, "minions", "drop_remainders", "TEXT");

        // Ensure tombstone table exists
        try (Statement s = connection.createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS minion_tombstones (" +
                    "minion_id VARCHAR(36) PRIMARY KEY, " +
                    "operation_id VARCHAR(64), " +
                    "deleted_revision BIGINT NOT NULL, " +
                    "deleted_at BIGINT NOT NULL" +
                    ")");
        }
        addColumnIfNotExists(connection, "minion_tombstones", "operation_id", "VARCHAR(64)");

        // Ensure payload column exists in minion_pending_operations
        if (!columnExists(connection, "minion_pending_operations", "payload")) {
            try (Statement s = connection.createStatement()) {
                s.executeUpdate("ALTER TABLE minion_pending_operations ADD COLUMN payload TEXT");
            }
        }

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

        // 3. Update version table using backend-specific modern UPSERT
        String upsertVersion = isMySQL(connection) ?
                "INSERT INTO minion_schema_version (version, applied_at) VALUES (?,?) AS new_ver ON DUPLICATE KEY UPDATE applied_at = new_ver.applied_at" :
                "INSERT INTO minion_schema_version (version, applied_at) VALUES (?,?) ON CONFLICT(version) DO UPDATE SET applied_at = excluded.applied_at";
        try (PreparedStatement ps = connection.prepareStatement(upsertVersion)) {
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

    public static String serializeRemainders(Map<String, Long> remainders) {
        if (remainders == null || remainders.isEmpty()) return "{}";
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Long> e : remainders.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(e.getKey()).append("\":").append(e.getValue());
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    public static Map<String, Long> deserializeRemainders(String str) {
        Map<String, Long> map = new HashMap<>();
        if (str == null || str.isBlank() || "{}".equals(str.trim())) return map;
        String clean = str.trim().replace("{", "").replace("}", "").replace("\"", "");
        for (String pair : clean.split(",")) {
            String[] kv = pair.split(":");
            if (kv.length == 2) {
                try {
                    map.put(kv[0].trim(), Long.parseLong(kv[1].trim()));
                } catch (NumberFormatException ignored) {}
            }
        }
        return map;
    }

    public record TombstoneRecord(UUID minionId, String operationId, long deletedRevision, long deletedAt) {}

    public enum TransactionOutcome {
        CONFIRMED_COMMIT,
        CONFIRMED_ROLLBACK,
        UNKNOWN_COMMIT_OUTCOME
    }

    public enum TransactionType {
        PICKUP_REMOVAL,
        PAYOUT_RESERVATION,
        PAYOUT_ROLLBACK,
        PAYOUT_CONFIRM,
        ORDINARY_DELETION
    }

    public enum PickupRemovalResult {
        COMMITTED_NOW,
        ALREADY_COMMITTED_MINION_REMOVED,
        ALREADY_DELIVERY_ATTEMPTED,
        ALREADY_COMPLETED,
        PRECONDITION_FAILED,
        CONFLICTING_PAYLOAD,
        CONFIRMED_ROLLBACK,
        UNKNOWN_OUTCOME
    }

    public enum PayoutReservationResult {
        RESERVED,
        ALREADY_RESERVED,
        ALREADY_CONFIRMED,
        FAILED_CONFIRMED_ROLLBACK,
        UNKNOWN_OUTCOME
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
            List<String> serializedStorageItems,
            long stateRevision,
            long fractionalProgressMicros,
            String dropRemainders
    ) {
        public MinionSnapshot(
                UUID id, UUID ownerId, String type, int tier, String world,
                double x, double y, double z, float yaw, float pitch,
                String fuelId, long fuelEndTime, String upgrade1Id, String upgrade2Id,
                String skinId, String shippingId, double shippingCoins,
                String storageId, String storageWorld, Integer storageX, Integer storageY, Integer storageZ,
                long lastActionTime, boolean lastActionWasPlace, int totalHarvested, int storedXp,
                String customDefinitionId, List<String> serializedInventoryItems, List<String> serializedStorageItems
        ) {
            this(id, ownerId, type, tier, world, x, y, z, yaw, pitch,
                    fuelId, fuelEndTime, upgrade1Id, upgrade2Id, skinId, shippingId, shippingCoins,
                    storageId, storageWorld, storageX, storageY, storageZ,
                    lastActionTime, lastActionWasPlace, totalHarvested, storedXp,
                    customDefinitionId, serializedInventoryItems, serializedStorageItems,
                    0L, 0L, "{}");
        }

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

            String dropRemaindersStr = serializeRemainders(m.getDropRemaindersMicros());

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
                    m.getTotalShippingCoins(),
                    stId,
                    stWorld,
                    stX, stY, stZ,
                    m.getLastActionTime(),
                    m.wasLastActionPlace(),
                    m.getTotalHarvested(),
                    m.getStoredXp(),
                    m.getCustomDefinitionId(),
                    invItems,
                    storageItems,
                    m.getStateRevision(),
                    m.getFractionalProgressMicros(),
                    dropRemaindersStr
            );
        }
    }

    public synchronized Set<String> queryTombstones(Collection<String> ids) {
        Set<String> result = new HashSet<>();
        if (ids == null || ids.isEmpty()) return result;
        try {
            ensureConnection();
            if (connection == null) return result;
            StringBuilder sb = new StringBuilder("SELECT minion_id FROM minion_tombstones WHERE minion_id IN (");
            for (int i = 0; i < ids.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append("?");
            }
            sb.append(")");
            try (PreparedStatement ps = connection.prepareStatement(sb.toString())) {
                int idx = 1;
                for (String id : ids) {
                    ps.setString(idx++, id);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        result.add(rs.getString("minion_id"));
                    }
                }
            }
        } catch (SQLException e) {
            logSevere("Failed to query tombstones!", e);
        }
        return result;
    }

    public synchronized Map<UUID, TombstoneRecord> loadAllTombstoneRecords() {
        Map<UUID, TombstoneRecord> tombstones = new HashMap<>();
        try {
            ensureConnection();
            if (connection == null) return tombstones;
            try (Statement s = connection.createStatement();
                 ResultSet rs = s.executeQuery("SELECT minion_id, operation_id, deleted_revision, deleted_at FROM minion_tombstones")) {
                while (rs.next()) {
                    String idStr = rs.getString("minion_id");
                    String opId = rs.getString("operation_id");
                    long rev = rs.getLong("deleted_revision");
                    long at = rs.getLong("deleted_at");
                    if (idStr != null) {
                        try {
                            UUID id = UUID.fromString(idStr);
                            tombstones.put(id, new TombstoneRecord(id, opId, rev, at));
                        } catch (IllegalArgumentException ignored) {}
                    }
                }
            }
        } catch (SQLException e) {
            logSevere("Failed to load tombstone records from database!", e);
        }
        return tombstones;
    }

    public synchronized TombstoneRecord getTombstone(UUID minionId) {
        if (minionId == null) return null;
        try {
            ensureConnection();
            if (connection == null) return null;
            try (PreparedStatement ps = connection.prepareStatement("SELECT operation_id, deleted_revision, deleted_at FROM minion_tombstones WHERE minion_id = ?")) {
                ps.setString(1, minionId.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return new TombstoneRecord(minionId, rs.getString("operation_id"), rs.getLong("deleted_revision"), rs.getLong("deleted_at"));
                    }
                }
            }
        } catch (SQLException e) {
            logSevere("Failed to query tombstone for " + minionId, e);
        }
        return null;
    }

    public synchronized Set<UUID> loadAllTombstones() {
        return loadAllTombstoneRecords().keySet();
    }

    public synchronized boolean deleteMinion(UUID minionId, long revision, long deletedAt, String operationId) {
        if (minionId == null) return false;
        try {
            ensureConnection();
            if (connection == null) return false;
            connection.setAutoCommit(false);
            try {
                // If already tombstoned, return true (idempotent deletion) or reject overwrite
                if (getTombstone(minionId) != null) {
                    connection.rollback();
                    connection.setAutoCommit(true);
                    return true;
                }

                String insertTombstone = "INSERT INTO minion_tombstones (minion_id, operation_id, deleted_revision, deleted_at) VALUES (?,?,?,?)";
                try (PreparedStatement psT = connection.prepareStatement(insertTombstone);
                     PreparedStatement psI = connection.prepareStatement("DELETE FROM minion_items WHERE minion_id = ?");
                     PreparedStatement psM = connection.prepareStatement("DELETE FROM minions WHERE id = ?")) {

                    psT.setString(1, minionId.toString());
                    psT.setString(2, operationId != null ? operationId : "MANUAL_DELETION");
                    psT.setLong(3, revision);
                    psT.setLong(4, deletedAt);
                    psT.executeUpdate();

                    psI.setString(1, minionId.toString());
                    psI.executeUpdate();

                    psM.setString(1, minionId.toString());
                    psM.executeUpdate();

                    connection.commit();
                    connection.setAutoCommit(true);
                    return true;
                }
            } catch (SQLException e) {
                executeRollbackSafely();
                logSevere("Failed to execute deleteMinion transaction for " + minionId, e);
                return false;
            }
        } catch (SQLException e) {
            logSevere("Database error during deleteMinion: " + minionId, e);
            return false;
        }
    }

    public synchronized boolean deleteMinion(UUID minionId, long revision, long deletedAt) {
        return deleteMinion(minionId, revision, deletedAt, "MANUAL_DELETION");
    }

    public synchronized void saveAll(MinionManager manager) {
        if (manager == null) return;
        List<MinionSnapshot> snapshots = new ArrayList<>();
        for (Minion m : manager.getAllMinions()) {
            snapshots.add(MinionSnapshot.fromMinion(m));
        }
        saveSnapshots(snapshots);
    }

    public synchronized boolean saveSnapshots(List<MinionSnapshot> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) return true;
        try {
            ensureConnection();
            if (connection == null) return false;

            // 1. Pre-filter snapshots against minion_tombstones table
            List<String> ids = new ArrayList<>();
            for (MinionSnapshot s : snapshots) {
                ids.add(s.id().toString());
            }
            Set<String> tombstoned = queryTombstones(ids);

            List<MinionSnapshot> toSave = new ArrayList<>();
            for (MinionSnapshot s : snapshots) {
                if (!tombstoned.contains(s.id().toString())) {
                    toSave.add(s);
                }
            }
            if (toSave.isEmpty()) return true;

            connection.setAutoCommit(false);
            boolean isMySQL = isMySQL(connection);

            String upsertMinion;
            if (isMySQL) {
                upsertMinion = "INSERT INTO minions (id, owner, type, tier, world, x, y, z, yaw, pitch, " +
                        "fuel, fuel_end, upgrade1, upgrade2, skin, shipping_id, shipping_coins, " +
                        "storage_id, storage_world, storage_x, storage_y, storage_z, " +
                        "last_action, last_was_place, harvested, xp, custom_id, " +
                        "state_revision, fractional_progress, drop_remainders) " +
                        "SELECT * FROM (SELECT ? AS id, ? AS owner, ? AS type, ? AS tier, ? AS world, ? AS x, ? AS y, ? AS z, ? AS yaw, ? AS pitch, " +
                        "? AS fuel, ? AS fuel_end, ? AS upgrade1, ? AS upgrade2, ? AS skin, ? AS shipping_id, ? AS shipping_coins, " +
                        "? AS storage_id, ? AS storage_world, ? AS storage_x, ? AS storage_y, ? AS storage_z, " +
                        "? AS last_action, ? AS last_was_place, ? AS harvested, ? AS xp, ? AS custom_id, " +
                        "? AS state_revision, ? AS fractional_progress, ? AS drop_remainders) AS src " +
                        "WHERE NOT EXISTS (SELECT 1 FROM minion_tombstones WHERE minion_id = src.id) " +
                        "ON DUPLICATE KEY UPDATE " +
                        "owner=IF(src.state_revision > minions.state_revision, src.owner, minions.owner), " +
                        "type=IF(src.state_revision > minions.state_revision, src.type, minions.type), " +
                        "tier=IF(src.state_revision > minions.state_revision, src.tier, minions.tier), " +
                        "world=IF(src.state_revision > minions.state_revision, src.world, minions.world), " +
                        "x=IF(src.state_revision > minions.state_revision, src.x, minions.x), " +
                        "y=IF(src.state_revision > minions.state_revision, src.y, minions.y), " +
                        "z=IF(src.state_revision > minions.state_revision, src.z, minions.z), " +
                        "yaw=IF(src.state_revision > minions.state_revision, src.yaw, minions.yaw), " +
                        "pitch=IF(src.state_revision > minions.state_revision, src.pitch, minions.pitch), " +
                        "fuel=IF(src.state_revision > minions.state_revision, src.fuel, minions.fuel), " +
                        "fuel_end=IF(src.state_revision > minions.state_revision, src.fuel_end, minions.fuel_end), " +
                        "upgrade1=IF(src.state_revision > minions.state_revision, src.upgrade1, minions.upgrade1), " +
                        "upgrade2=IF(src.state_revision > minions.state_revision, src.upgrade2, minions.upgrade2), " +
                        "skin=IF(src.state_revision > minions.state_revision, src.skin, minions.skin), " +
                        "shipping_id=IF(src.state_revision > minions.state_revision, src.shipping_id, minions.shipping_id), " +
                        "shipping_coins=IF(src.state_revision > minions.state_revision, src.shipping_coins, minions.shipping_coins), " +
                        "storage_id=IF(src.state_revision > minions.state_revision, src.storage_id, minions.storage_id), " +
                        "storage_world=IF(src.state_revision > minions.state_revision, src.storage_world, minions.storage_world), " +
                        "storage_x=IF(src.state_revision > minions.state_revision, src.storage_x, minions.storage_x), " +
                        "storage_y=IF(src.state_revision > minions.state_revision, src.storage_y, minions.storage_y), " +
                        "storage_z=IF(src.state_revision > minions.state_revision, src.storage_z, minions.storage_z), " +
                        "last_action=IF(src.state_revision > minions.state_revision, src.last_action, minions.last_action), " +
                        "last_was_place=IF(src.state_revision > minions.state_revision, src.last_was_place, minions.last_was_place), " +
                        "harvested=IF(src.state_revision > minions.state_revision, src.harvested, minions.harvested), " +
                        "xp=IF(src.state_revision > minions.state_revision, src.xp, minions.xp), " +
                        "custom_id=IF(src.state_revision > minions.state_revision, src.custom_id, minions.custom_id), " +
                        "fractional_progress=IF(src.state_revision > minions.state_revision, src.fractional_progress, minions.fractional_progress), " +
                        "drop_remainders=IF(src.state_revision > minions.state_revision, src.drop_remainders, minions.drop_remainders), " +
                        "state_revision=GREATEST(minions.state_revision, src.state_revision)";
            } else {
                upsertMinion = "INSERT INTO minions (id, owner, type, tier, world, x, y, z, yaw, pitch, " +
                        "fuel, fuel_end, upgrade1, upgrade2, skin, shipping_id, shipping_coins, " +
                        "storage_id, storage_world, storage_x, storage_y, storage_z, " +
                        "last_action, last_was_place, harvested, xp, custom_id, " +
                        "state_revision, fractional_progress, drop_remainders) " +
                        "SELECT ?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,? " +
                        "WHERE NOT EXISTS (SELECT 1 FROM minion_tombstones WHERE minion_id = ?) " +
                        "ON CONFLICT(id) DO UPDATE SET " +
                        "owner=excluded.owner, type=excluded.type, tier=excluded.tier, world=excluded.world, " +
                        "x=excluded.x, y=excluded.y, z=excluded.z, yaw=excluded.yaw, pitch=excluded.pitch, " +
                        "fuel=excluded.fuel, fuel_end=excluded.fuel_end, upgrade1=excluded.upgrade1, upgrade2=excluded.upgrade2, " +
                        "skin=excluded.skin, shipping_id=excluded.shipping_id, shipping_coins=excluded.shipping_coins, " +
                        "storage_id=excluded.storage_id, storage_world=excluded.storage_world, storage_x=excluded.storage_x, storage_y=excluded.storage_y, storage_z=excluded.storage_z, " +
                        "last_action=excluded.last_action, last_was_place=excluded.last_was_place, harvested=excluded.harvested, " +
                        "xp=excluded.xp, custom_id=excluded.custom_id, " +
                        "state_revision=excluded.state_revision, " +
                        "fractional_progress=excluded.fractional_progress, " +
                        "drop_remainders=excluded.drop_remainders " +
                        "WHERE excluded.state_revision > minions.state_revision";
            }

            String deleteItemsForMinion = "DELETE FROM minion_items WHERE minion_id = ?";
            String insertItem = "INSERT INTO minion_items (minion_id, container_type, slot_index, item_data) VALUES (?,?,?,?)";

            try (PreparedStatement psM = connection.prepareStatement(upsertMinion);
                 PreparedStatement psDelI = connection.prepareStatement(deleteItemsForMinion);
                 PreparedStatement psInsI = connection.prepareStatement(insertItem)) {

                for (MinionSnapshot s : toSave) {
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
                    psM.setLong(28, s.stateRevision());
                    psM.setLong(29, s.fractionalProgressMicros());
                    psM.setString(30, s.dropRemainders() != null ? s.dropRemainders() : "{}");
                    if (!isMySQL) {
                        psM.setString(31, s.id().toString());
                    }
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
            logInfo("Saved " + toSave.size() + " minions to SQL database.");
            return true;
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
            return false;
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

        // 2. Pre-load tombstones to fence deleted minions permanently
        Set<UUID> tombstones = loadAllTombstones();

        int loadedCount = 0;
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM minions")) {

            while (rs.next()) {
                UUID id = UUID.fromString(rs.getString("id"));
                if (tombstones.contains(id)) {
                    // Tombstone fence: never load deleted minion
                    continue;
                }
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

                long rev = rs.getLong("state_revision");
                minion.setStateRevision(rev);
                minion.setFractionalProgressMicros(rs.getLong("fractional_progress"));
                String remStr = rs.getString("drop_remainders");
                if (remStr != null && !remStr.isBlank()) {
                    Map<String, Long> remMap = deserializeRemainders(remStr);
                    for (Map.Entry<String, Long> entry : remMap.entrySet()) {
                        minion.setChannelRemainder(entry.getKey(), entry.getValue());
                    }
                }
                minion.setStateRevision(rev);

                // Load items across both INVENTORY and STORAGE containers
                try (PreparedStatement psI = connection.prepareStatement("SELECT container_type, item_data FROM minion_items WHERE minion_id = ? ORDER BY slot_index ASC")) {
                    psI.setString(1, id.toString());
                    try (ResultSet rsI = psI.executeQuery()) {
                        while (rsI.next()) {
                            String cType = rsI.getString("container_type");
                            String yamlStr = rsI.getString("item_data");
                            try {
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
                            } catch (Exception ignored) {}
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
        } catch (SQLException e) {
            logSevere("Failed to load minions from database!", e);
        }
    }

    public synchronized boolean minionExists(UUID minionId) {
        if (minionId == null) return false;
        try {
            ensureConnection();
            if (connection == null) return false;
            try (PreparedStatement ps = connection.prepareStatement("SELECT 1 FROM minions WHERE id = ?")) {
                ps.setString(1, minionId.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        } catch (SQLException e) {
            return false;
        }
    }

    // --- Durable Operations Journal & Finalization ---

    public synchronized void recordPendingOperation(PendingOperation op) throws SQLException {
        ensureConnection();
        String sql = isMySQL(connection) ?
                "INSERT INTO minion_pending_operations (operation_id, minion_id, player_uuid, state, created_at, payload) VALUES (?,?,?,?,?,?) AS new_op ON DUPLICATE KEY UPDATE state = new_op.state, payload = new_op.payload" :
                "INSERT INTO minion_pending_operations (operation_id, minion_id, player_uuid, state, created_at, payload) VALUES (?,?,?,?,?,?) ON CONFLICT(operation_id) DO UPDATE SET state = excluded.state, payload = excluded.payload";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, op.operationId());
            ps.setString(2, op.minionId().toString());
            ps.setString(3, op.playerUuid().toString());
            ps.setString(4, op.state());
            ps.setLong(5, op.createdAt());
            ps.setString(6, op.payload());
            ps.executeUpdate();
        }
    }

    public synchronized void updatePendingOperationState(String operationId, String newState, String payload) throws SQLException {
        ensureConnection();
        String sql = "UPDATE minion_pending_operations SET state = ?, payload = ? WHERE operation_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, newState);
            ps.setString(2, payload);
            ps.setString(3, operationId);
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
             ResultSet rs = s.executeQuery("SELECT operation_id, minion_id, player_uuid, state, created_at, payload FROM minion_pending_operations")) {
            while (rs.next()) {
                list.add(new PendingOperation(
                        rs.getString("operation_id"),
                        UUID.fromString(rs.getString("minion_id")),
                        UUID.fromString(rs.getString("player_uuid")),
                        rs.getString("state"),
                        rs.getLong("created_at"),
                        rs.getString("payload")
                ));
            }
        }
        return list;
    }

    /**
     * Atomically records MINION_REMOVED with escrow payload, inserts tombstone,
     * and deletes minion from active tables in a single ACID transaction.
     */
    /**
     * Atomically records MINION_REMOVED with escrow payload, inserts tombstone,
     * and deletes minion from active tables in a single ACID transaction.
     */
    private boolean executeRollbackSafely() {
        if (connection == null) return false;
        try {
            connection.rollback();
            connection.setAutoCommit(true);
            return true;
        } catch (SQLException e) {
            logSevere("CRITICAL: Rollback execution or autoCommit reset threw an exception! Connection compromised.", e);
            try {
                connection.close();
            } catch (SQLException ignored) {}
            connection = null;
            return false;
        }
    }

    public synchronized PickupRemovalResult executePickupRemoval(String operationId, UUID minionId, UUID playerUuid, long revision, String payload) {
        if (operationId == null || minionId == null) return PickupRemovalResult.PRECONDITION_FAILED;
        try {
            ensureConnection();
            if (connection == null) {
                quarantineMinion(minionId);
                return PickupRemovalResult.UNKNOWN_OUTCOME;
            }
            connection.setAutoCommit(false);
            boolean commitAttempted = false;
            try {
                // 1. Check if operationId already exists in minion_pending_operations BEFORE deleting anything!
                try (PreparedStatement psCheck = connection.prepareStatement(
                        "SELECT minion_id, player_uuid, state, payload FROM minion_pending_operations WHERE operation_id = ?")) {
                    psCheck.setString(1, operationId);
                    try (ResultSet rs = psCheck.executeQuery()) {
                        if (rs.next()) {
                            String storedMinion = rs.getString("minion_id");
                            String storedPlayer = rs.getString("player_uuid");
                            String storedState = rs.getString("state");
                            String storedPayload = rs.getString("payload");

                            if (!minionId.toString().equalsIgnoreCase(storedMinion) ||
                                (playerUuid != null && !playerUuid.toString().equalsIgnoreCase(storedPlayer)) ||
                                !Objects.equals(payload, storedPayload)) {
                                executeRollbackSafely();
                                return PickupRemovalResult.CONFLICTING_PAYLOAD;
                            }

                            // Verify consistency: active minion must be absent AND matching tombstone must exist
                            boolean activeMinionExists = false;
                            try (PreparedStatement psM = connection.prepareStatement("SELECT 1 FROM minions WHERE id = ?")) {
                                psM.setString(1, minionId.toString());
                                try (ResultSet rsM = psM.executeQuery()) {
                                    activeMinionExists = rsM.next();
                                }
                            }

                            boolean tombstoneMatches = false;
                            try (PreparedStatement psT = connection.prepareStatement("SELECT operation_id FROM minion_tombstones WHERE minion_id = ?")) {
                                psT.setString(1, minionId.toString());
                                try (ResultSet rsT = psT.executeQuery()) {
                                    if (rsT.next()) {
                                        String tOp = rsT.getString("operation_id");
                                        tombstoneMatches = operationId.equalsIgnoreCase(tOp);
                                    }
                                }
                            }

                            if (activeMinionExists || !tombstoneMatches) {
                                executeRollbackSafely();
                                quarantineMinion(minionId);
                                return PickupRemovalResult.UNKNOWN_OUTCOME;
                            }

                            executeRollbackSafely();
                            return switch (storedState != null ? storedState.toUpperCase() : "") {
                                case "MINION_REMOVED" -> PickupRemovalResult.ALREADY_COMMITTED_MINION_REMOVED;
                                case "DELIVERY_ATTEMPTED", "UNKNOWN" -> PickupRemovalResult.ALREADY_DELIVERY_ATTEMPTED;
                                case "COMPLETED" -> PickupRemovalResult.ALREADY_COMPLETED;
                                default -> PickupRemovalResult.UNKNOWN_OUTCOME;
                            };
                        }
                    }
                }

                // 2. Reject if a tombstone already exists for this minionId before deleting active row!
                try (PreparedStatement psTCheck = connection.prepareStatement("SELECT 1 FROM minion_tombstones WHERE minion_id = ?")) {
                    psTCheck.setString(1, minionId.toString());
                    try (ResultSet rsT = psTCheck.executeQuery()) {
                        if (rsT.next()) {
                            executeRollbackSafely();
                            quarantineMinion(minionId);
                            return PickupRemovalResult.UNKNOWN_OUTCOME;
                        }
                    }
                }

                // 3. Precondition check: Delete active minion row with strict revision check
                int deletedMinions;
                try (PreparedStatement psM = connection.prepareStatement("DELETE FROM minions WHERE id = ? AND state_revision = ?")) {
                    psM.setString(1, minionId.toString());
                    psM.setLong(2, revision);
                    deletedMinions = psM.executeUpdate();
                }

                if (deletedMinions == 0) {
                    boolean minionStillExists = false;
                    try (PreparedStatement psCheck = connection.prepareStatement("SELECT 1 FROM minions WHERE id = ?")) {
                        psCheck.setString(1, minionId.toString());
                        try (ResultSet rsCheck = psCheck.executeQuery()) {
                            minionStillExists = rsCheck.next();
                        }
                    } catch (SQLException ignored) {}

                    boolean rbOk = executeRollbackSafely();
                    if (!rbOk) {
                        quarantineMinion(minionId);
                        return PickupRemovalResult.UNKNOWN_OUTCOME;
                    }
                    if (minionStillExists) {
                        logWarning("Pickup removal rejected: revision mismatch for minion " + minionId + " (supplied rev: " + revision + ")");
                    }
                    return PickupRemovalResult.PRECONDITION_FAILED;
                }

                // 4. Delete child items
                try (PreparedStatement psI = connection.prepareStatement("DELETE FROM minion_items WHERE minion_id = ?")) {
                    psI.setString(1, minionId.toString());
                    psI.executeUpdate();
                }

                // 5. Insert permanent immutable tombstone (NO update on conflict!)
                try (PreparedStatement psT = connection.prepareStatement(
                        "INSERT INTO minion_tombstones (minion_id, operation_id, deleted_revision, deleted_at) VALUES (?,?,?,?)")) {
                    psT.setString(1, minionId.toString());
                    psT.setString(2, operationId);
                    psT.setLong(3, revision);
                    psT.setLong(4, System.currentTimeMillis());
                    psT.executeUpdate();
                }

                // 6. Plain INSERT into minion_pending_operations with state MINION_REMOVED
                try (PreparedStatement psOp = connection.prepareStatement(
                        "INSERT INTO minion_pending_operations (operation_id, minion_id, player_uuid, state, created_at, payload) VALUES (?,?,?,?,?,?)")) {
                    psOp.setString(1, operationId);
                    psOp.setString(2, minionId.toString());
                    psOp.setString(3, playerUuid != null ? playerUuid.toString() : "");
                    psOp.setString(4, "MINION_REMOVED");
                    psOp.setLong(5, System.currentTimeMillis());
                    psOp.setString(6, payload);
                    psOp.executeUpdate();
                }

                commitAttempted = true;
                connection.commit();
                connection.setAutoCommit(true);
                return PickupRemovalResult.COMMITTED_NOW;
            } catch (SQLException e) {
                if (commitAttempted) {
                    logSevere("Commit exception in executePickupRemoval for " + minionId + ", probing reconciliation...", e);
                    TransactionOutcome outcome = probeCommitReconciliation(minionId, operationId, TransactionType.PICKUP_REMOVAL);
                    if (outcome == TransactionOutcome.CONFIRMED_COMMIT) {
                        return PickupRemovalResult.COMMITTED_NOW;
                    } else if (outcome == TransactionOutcome.CONFIRMED_ROLLBACK) {
                        return PickupRemovalResult.CONFIRMED_ROLLBACK;
                    } else {
                        quarantineMinion(minionId);
                        return PickupRemovalResult.UNKNOWN_OUTCOME;
                    }
                } else {
                    boolean rbOk = executeRollbackSafely();
                    if (!rbOk) {
                        logSevere("Rollback failed after statement error in executePickupRemoval for " + minionId, e);
                        quarantineMinion(minionId);
                        return PickupRemovalResult.UNKNOWN_OUTCOME;
                    }
                    logSevere("Failed to execute atomic pickup removal for " + minionId, e);
                    return PickupRemovalResult.CONFIRMED_ROLLBACK;
                }
            }
        } catch (SQLException e) {
            logSevere("Database error during executePickupRemoval: " + operationId, e);
            quarantineMinion(minionId);
            return PickupRemovalResult.UNKNOWN_OUTCOME;
        }
    }

    public synchronized boolean claimDeliveryAttempt(String operationId) {
        if (operationId == null) return false;
        try {
            ensureConnection();
            if (connection == null) return false;
            connection.setAutoCommit(false);
            try {
                int updated;
                try (PreparedStatement ps = connection.prepareStatement(
                        "UPDATE minion_pending_operations SET state = 'DELIVERY_ATTEMPTED' WHERE operation_id = ? AND state = 'MINION_REMOVED'")) {
                    ps.setString(1, operationId);
                    updated = ps.executeUpdate();
                }
                if (updated == 1) {
                    connection.commit();
                    connection.setAutoCommit(true);
                    return true;
                } else {
                    executeRollbackSafely();
                    return false;
                }
            } catch (SQLException e) {
                executeRollbackSafely();
                logSevere("Failed to claim delivery attempt: " + operationId, e);
                return false;
            }
        } catch (SQLException e) {
            logSevere("Database error during claimDeliveryAttempt: " + operationId, e);
            return false;
        }
    }

    public synchronized boolean completeDelivery(String operationId) {
        if (operationId == null) return false;
        try {
            ensureConnection();
            if (connection == null) return false;
            connection.setAutoCommit(false);
            try {
                int updated;
                try (PreparedStatement ps = connection.prepareStatement(
                        "UPDATE minion_pending_operations SET state = 'COMPLETED' WHERE operation_id = ? AND state = 'DELIVERY_ATTEMPTED'")) {
                    ps.setString(1, operationId);
                    updated = ps.executeUpdate();
                }
                if (updated == 1) {
                    connection.commit();
                    connection.setAutoCommit(true);
                    return true;
                } else {
                    executeRollbackSafely();
                    return false;
                }
            } catch (SQLException e) {
                executeRollbackSafely();
                logSevere("Failed to complete delivery: " + operationId, e);
                return false;
            }
        } catch (SQLException e) {
            logSevere("Database error during completeDelivery: " + operationId, e);
            return false;
        }
    }

    public synchronized PayoutReservationResult reservePayoutAtomic(String operationId, UUID minionId, UUID playerUuid, double amount, long revision) {
        if (operationId == null || minionId == null || !Double.isFinite(amount) || amount <= 0.0) {
            return PayoutReservationResult.FAILED_CONFIRMED_ROLLBACK;
        }
        double normalizedAmount = BigDecimal.valueOf(amount).setScale(4, RoundingMode.HALF_UP).doubleValue();

        try {
            ensureConnection();
            if (connection == null) {
                quarantineMinion(minionId);
                return PayoutReservationResult.UNKNOWN_OUTCOME;
            }
            connection.setAutoCommit(false);
            boolean commitAttempted = false;
            try {
                // Check if operationId already exists (idempotency reconciliation)
                try (PreparedStatement psCheck = connection.prepareStatement(
                        "SELECT minion_id, player_uuid, state, payload FROM minion_pending_operations WHERE operation_id = ?")) {
                    psCheck.setString(1, operationId);
                    try (ResultSet rs = psCheck.executeQuery()) {
                        if (rs.next()) {
                            String storedMinion = rs.getString("minion_id");
                            String storedPlayer = rs.getString("player_uuid");
                            String storedState = rs.getString("state");
                            String storedPayload = rs.getString("payload");

                            boolean minionMatches = minionId.toString().equalsIgnoreCase(storedMinion);
                            boolean playerMatches = (playerUuid == null || storedPlayer == null || playerUuid.toString().equalsIgnoreCase(storedPlayer));
                            boolean amountMatches = false;
                            try {
                                if (storedPayload != null) {
                                    double storedAmt = Double.parseDouble(storedPayload);
                                    amountMatches = Math.abs(storedAmt - normalizedAmount) < 0.0001;
                                }
                            } catch (Exception ignored) {}

                            if (!minionMatches || !playerMatches || !amountMatches) {
                                executeRollbackSafely();
                                quarantineMinion(minionId);
                                return PayoutReservationResult.UNKNOWN_OUTCOME;
                            }

                            boolean rbOk = executeRollbackSafely();
                            if (!rbOk) {
                                quarantineMinion(minionId);
                                return PayoutReservationResult.UNKNOWN_OUTCOME;
                            }

                            return switch (storedState != null ? storedState.toUpperCase() : "") {
                                case "ATTEMPTED" -> PayoutReservationResult.ALREADY_RESERVED;
                                case "CONFIRMED" -> PayoutReservationResult.ALREADY_CONFIRMED;
                                case "PAYOUT_FAILED" -> PayoutReservationResult.FAILED_CONFIRMED_ROLLBACK;
                                default -> {
                                    quarantineMinion(minionId);
                                    yield PayoutReservationResult.UNKNOWN_OUTCOME;
                                }
                            };
                        }
                    }
                }

                // Deduct exact shipping coins with optimistic revision check
                int updated;
                try (PreparedStatement psU = connection.prepareStatement(
                        "UPDATE minions SET shipping_coins = ROUND(shipping_coins - ?, 4), state_revision = state_revision + 1 WHERE id = ? AND shipping_coins >= ? AND state_revision = ?")) {
                    psU.setDouble(1, normalizedAmount);
                    psU.setString(2, minionId.toString());
                    psU.setDouble(3, normalizedAmount);
                    psU.setLong(4, revision);
                    updated = psU.executeUpdate();
                }

                if (updated != 1) {
                    boolean rbOk = executeRollbackSafely();
                    if (!rbOk) {
                        quarantineMinion(minionId);
                        return PayoutReservationResult.UNKNOWN_OUTCOME;
                    }
                    return PayoutReservationResult.FAILED_CONFIRMED_ROLLBACK;
                }

                // Plain INSERT into minion_pending_operations with state ATTEMPTED
                try (PreparedStatement psOp = connection.prepareStatement(
                        "INSERT INTO minion_pending_operations (operation_id, minion_id, player_uuid, state, created_at, payload) VALUES (?, ?, ?, 'ATTEMPTED', ?, ?)")) {
                    psOp.setString(1, operationId);
                    psOp.setString(2, minionId.toString());
                    psOp.setString(3, playerUuid != null ? playerUuid.toString() : "");
                    psOp.setLong(4, System.currentTimeMillis());
                    psOp.setString(5, String.valueOf(normalizedAmount));
                    psOp.executeUpdate();
                }

                commitAttempted = true;
                connection.commit();
                connection.setAutoCommit(true);
                return PayoutReservationResult.RESERVED;
            } catch (SQLException e) {
                if (commitAttempted) {
                    logSevere("Commit exception during reservePayoutAtomic for " + minionId + ", probing reconciliation...", e);
                    TransactionOutcome outcome = probeCommitReconciliation(minionId, operationId, TransactionType.PAYOUT_RESERVATION);
                    if (outcome == TransactionOutcome.CONFIRMED_COMMIT) {
                        return PayoutReservationResult.RESERVED;
                    } else if (outcome == TransactionOutcome.CONFIRMED_ROLLBACK) {
                        return PayoutReservationResult.FAILED_CONFIRMED_ROLLBACK;
                    } else {
                        quarantineMinion(minionId);
                        return PayoutReservationResult.UNKNOWN_OUTCOME;
                    }
                } else {
                    boolean rbOk = executeRollbackSafely();
                    if (!rbOk) {
                        logSevere("Rollback failed after statement exception in reservePayoutAtomic for " + minionId, e);
                        quarantineMinion(minionId);
                        return PayoutReservationResult.UNKNOWN_OUTCOME;
                    }
                    logSevere("Failed to reserve payout in database for " + minionId, e);
                    return PayoutReservationResult.FAILED_CONFIRMED_ROLLBACK;
                }
            }
        } catch (SQLException e) {
            logSevere("Database error during reservePayoutAtomic: " + operationId, e);
            quarantineMinion(minionId);
            return PayoutReservationResult.UNKNOWN_OUTCOME;
        }
    }

    public synchronized boolean rollbackPayoutAtomic(String operationId, UUID minionId, double amount) {
        if (operationId == null || minionId == null || amount <= 0.0) return false;
        double normalizedAmount = BigDecimal.valueOf(amount).setScale(4, RoundingMode.HALF_UP).doubleValue();
        try {
            ensureConnection();
            if (connection == null) return false;
            connection.setAutoCommit(false);
            try {
                // Guard state transition: only if currently ATTEMPTED
                int opUpdated;
                try (PreparedStatement psOp = connection.prepareStatement(
                        "UPDATE minion_pending_operations SET state = 'PAYOUT_FAILED' WHERE operation_id = ? AND state = 'ATTEMPTED'")) {
                    psOp.setString(1, operationId);
                    opUpdated = psOp.executeUpdate();
                }

                if (opUpdated != 1) {
                    // Precondition failed: Either operation was already PAYOUT_FAILED or not in ATTEMPTED state.
                    executeRollbackSafely();
                    return false;
                }

                // Additive restoration of shipping coins to minion row
                try (PreparedStatement psM = connection.prepareStatement(
                        "UPDATE minions SET shipping_coins = ROUND(shipping_coins + ?, 4), state_revision = state_revision + 1 WHERE id = ?")) {
                    psM.setDouble(1, normalizedAmount);
                    psM.setString(2, minionId.toString());
                    psM.executeUpdate();
                }

                connection.commit();
                connection.setAutoCommit(true);
                return true;
            } catch (SQLException e) {
                executeRollbackSafely();
                logSevere("Failed to rollback payout in database for " + minionId, e);
                return false;
            }
        } catch (SQLException e) {
            logSevere("Database error during rollbackPayoutAtomic: " + operationId, e);
            return false;
        }
    }

    public synchronized boolean confirmPayoutAtomic(String operationId) {
        return confirmPayoutAtomic(operationId, null);
    }

    public synchronized boolean confirmPayoutAtomic(String operationId, UUID minionId) {
        if (operationId == null) return false;
        try {
            ensureConnection();
            if (connection == null) {
                if (minionId != null) quarantineMinion(minionId);
                return false;
            }
            connection.setAutoCommit(false);
            boolean commitAttempted = false;
            try {
                int updated;
                try (PreparedStatement ps = connection.prepareStatement(
                        "UPDATE minion_pending_operations SET state = 'CONFIRMED' WHERE operation_id = ? AND state = 'ATTEMPTED'")) {
                    ps.setString(1, operationId);
                    updated = ps.executeUpdate();
                }
                if (updated == 1) {
                    commitAttempted = true;
                    connection.commit();
                    connection.setAutoCommit(true);
                    return true;
                } else {
                    executeRollbackSafely();
                    return false;
                }
            } catch (SQLException e) {
                if (commitAttempted) {
                    logSevere("Commit exception during confirmPayoutAtomic for " + operationId + ", probing reconciliation...", e);
                    TransactionOutcome outcome = probeCommitReconciliation(minionId, operationId, TransactionType.PAYOUT_CONFIRM);
                    if (outcome == TransactionOutcome.CONFIRMED_COMMIT) {
                        return true;
                    } else if (outcome == TransactionOutcome.CONFIRMED_ROLLBACK) {
                        return false;
                    } else {
                        if (minionId != null) quarantineMinion(minionId);
                        return false;
                    }
                } else {
                    boolean rbOk = executeRollbackSafely();
                    if (!rbOk && minionId != null) {
                        quarantineMinion(minionId);
                    }
                    logSevere("Database error during confirmPayoutAtomic: " + operationId, e);
                    return false;
                }
            }
        } catch (SQLException e) {
            logSevere("Database error during confirmPayoutAtomic: " + operationId, e);
            if (minionId != null) quarantineMinion(minionId);
            return false;
        }
    }

    public synchronized boolean ambiguousPayoutAtomic(String operationId) {
        if (operationId == null) return false;
        try {
            ensureConnection();
            if (connection == null) return false;
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE minion_pending_operations SET state = 'PAYOUT_AMBIGUOUS' WHERE operation_id = ? AND state = 'ATTEMPTED'")) {
                ps.setString(1, operationId);
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            logSevere("Database error during ambiguousPayoutAtomic: " + operationId, e);
            return false;
        }
    }

    public synchronized TransactionOutcome probeCommitReconciliation(UUID minionId, String operationId) {
        return probeCommitReconciliation(minionId, operationId, TransactionType.PICKUP_REMOVAL);
    }

    public synchronized TransactionOutcome probeCommitReconciliation(UUID minionId, String operationId, TransactionType type) {
        if (type == null) type = TransactionType.PICKUP_REMOVAL;
        if (minionId == null && operationId == null) return TransactionOutcome.CONFIRMED_ROLLBACK;
        try {
            ensureConnection();
            if (connection == null) return TransactionOutcome.UNKNOWN_COMMIT_OUTCOME;

            switch (type) {
                case PICKUP_REMOVAL -> {
                    boolean hasTombstone = false;
                    if (operationId != null && minionId != null) {
                        try (PreparedStatement psT = connection.prepareStatement(
                                "SELECT 1 FROM minion_tombstones WHERE minion_id = ? AND operation_id = ?")) {
                            psT.setString(1, minionId.toString());
                            psT.setString(2, operationId);
                            try (ResultSet rs = psT.executeQuery()) {
                                hasTombstone = rs.next();
                            }
                        }
                    }
                    boolean hasMinion = minionId != null && minionExists(minionId);
                    String opState = null;
                    if (operationId != null) {
                        try (PreparedStatement psOp = connection.prepareStatement("SELECT state FROM minion_pending_operations WHERE operation_id = ?")) {
                            psOp.setString(1, operationId);
                            try (ResultSet rs = psOp.executeQuery()) {
                                if (rs.next()) opState = rs.getString("state");
                            }
                        }
                    }
                    if (hasTombstone && !hasMinion && ("MINION_REMOVED".equalsIgnoreCase(opState) || "DELIVERY_ATTEMPTED".equalsIgnoreCase(opState) || "COMPLETED".equalsIgnoreCase(opState))) {
                        return TransactionOutcome.CONFIRMED_COMMIT;
                    } else if (!hasTombstone && hasMinion && opState == null) {
                        return TransactionOutcome.CONFIRMED_ROLLBACK;
                    } else {
                        return TransactionOutcome.UNKNOWN_COMMIT_OUTCOME;
                    }
                }
                case PAYOUT_RESERVATION -> {
                    if (operationId == null) return TransactionOutcome.UNKNOWN_COMMIT_OUTCOME;
                    String opState = null;
                    try (PreparedStatement psOp = connection.prepareStatement("SELECT state FROM minion_pending_operations WHERE operation_id = ?")) {
                        psOp.setString(1, operationId);
                        try (ResultSet rs = psOp.executeQuery()) {
                            if (rs.next()) opState = rs.getString("state");
                        }
                    }
                    if ("ATTEMPTED".equalsIgnoreCase(opState)) {
                        return TransactionOutcome.CONFIRMED_COMMIT;
                    } else if (opState == null) {
                        return TransactionOutcome.CONFIRMED_ROLLBACK;
                    } else {
                        return TransactionOutcome.UNKNOWN_COMMIT_OUTCOME;
                    }
                }
                case PAYOUT_ROLLBACK -> {
                    if (operationId == null) return TransactionOutcome.UNKNOWN_COMMIT_OUTCOME;
                    String opState = null;
                    try (PreparedStatement psOp = connection.prepareStatement("SELECT state FROM minion_pending_operations WHERE operation_id = ?")) {
                        psOp.setString(1, operationId);
                        try (ResultSet rs = psOp.executeQuery()) {
                            if (rs.next()) opState = rs.getString("state");
                        }
                    }
                    if ("PAYOUT_FAILED".equalsIgnoreCase(opState)) {
                        return TransactionOutcome.CONFIRMED_COMMIT;
                    } else if ("ATTEMPTED".equalsIgnoreCase(opState)) {
                        return TransactionOutcome.CONFIRMED_ROLLBACK;
                    } else {
                        return TransactionOutcome.UNKNOWN_COMMIT_OUTCOME;
                    }
                }
                case PAYOUT_CONFIRM -> {
                    if (operationId == null) return TransactionOutcome.UNKNOWN_COMMIT_OUTCOME;
                    String opState = null;
                    try (PreparedStatement psOp = connection.prepareStatement("SELECT state FROM minion_pending_operations WHERE operation_id = ?")) {
                        psOp.setString(1, operationId);
                        try (ResultSet rs = psOp.executeQuery()) {
                            if (rs.next()) opState = rs.getString("state");
                        }
                    }
                    if ("CONFIRMED".equalsIgnoreCase(opState)) {
                        return TransactionOutcome.CONFIRMED_COMMIT;
                    } else if ("ATTEMPTED".equalsIgnoreCase(opState)) {
                        return TransactionOutcome.CONFIRMED_ROLLBACK;
                    } else {
                        return TransactionOutcome.UNKNOWN_COMMIT_OUTCOME;
                    }
                }
                case ORDINARY_DELETION -> {
                    boolean hasMinion = minionId != null && minionExists(minionId);
                    boolean hasTombstone = false;
                    if (minionId != null) {
                        try (PreparedStatement psT = connection.prepareStatement("SELECT 1 FROM minion_tombstones WHERE minion_id = ?")) {
                            psT.setString(1, minionId.toString());
                            try (ResultSet rs = psT.executeQuery()) {
                                hasTombstone = rs.next();
                            }
                        }
                    }
                    if (!hasMinion && hasTombstone) {
                        return TransactionOutcome.CONFIRMED_COMMIT;
                    } else if (hasMinion && !hasTombstone) {
                        return TransactionOutcome.CONFIRMED_ROLLBACK;
                    } else {
                        return TransactionOutcome.UNKNOWN_COMMIT_OUTCOME;
                    }
                }
                default -> {
                    return TransactionOutcome.UNKNOWN_COMMIT_OUTCOME;
                }
            }
        } catch (SQLException e) {
            logSevere("Failed to execute reconciliation probe for minion " + minionId, e);
            return TransactionOutcome.UNKNOWN_COMMIT_OUTCOME;
        }
    }

    /**
     * Atomically completes pickup by removing the pending operation and inserting tombstone if missing.
     */
    public synchronized boolean finalizePickup(String operationId, UUID minionId) {
        try {
            ensureConnection();
            if (connection == null) return false;
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
