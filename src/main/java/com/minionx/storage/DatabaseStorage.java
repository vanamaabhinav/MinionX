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
import java.util.UUID;
import java.util.logging.Level;

/**
 * Robust SQL database storage supporting SQLite and MySQL with auto-table generation.
 */
public class DatabaseStorage {

    private final MinionX plugin;
    private Connection connection;

    public DatabaseStorage(MinionX plugin) {
        this.plugin = plugin;
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
                // SQLite fallback
                File dbFile = new File(plugin.getDataFolder(), "data/minions.db");
                if (!dbFile.getParentFile().exists()) dbFile.getParentFile().mkdirs();
                connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            }

            createTables();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to connect to database: " + e.getMessage(), e);
            return false;
        }
    }

    private void createTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
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
                    "last_action BIGINT, " +
                    "last_was_place INT, " +
                    "harvested INT, " +
                    "xp INT, " +
                    "custom_id VARCHAR(64)" +
                    ")");

            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS minion_items (" +
                    "minion_id VARCHAR(36) NOT NULL, " +
                    "slot_index INT NOT NULL, " +
                    "item_data TEXT NOT NULL, " +
                    "PRIMARY KEY (minion_id, slot_index)" +
                    ")");
        }
    }

    public synchronized void saveAll(MinionManager manager) {
        try {
            ensureConnection();
            if (connection == null) return;
            connection.setAutoCommit(false);

            try (Statement clear = connection.createStatement()) {
                clear.executeUpdate("DELETE FROM minion_items");
                clear.executeUpdate("DELETE FROM minions");
            }

            String insertMinion = "INSERT INTO minions (id, owner, type, tier, world, x, y, z, yaw, pitch, fuel, fuel_end, upgrade1, upgrade2, last_action, last_was_place, harvested, xp, custom_id) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
            String insertItem = "INSERT INTO minion_items (minion_id, slot_index, item_data) VALUES (?,?,?)";

            try (PreparedStatement psM = connection.prepareStatement(insertMinion);
                 PreparedStatement psI = connection.prepareStatement(insertItem)) {

                for (Minion m : manager.getAllMinions()) {
                    psM.setString(1, m.getMinionId().toString());
                    psM.setString(2, m.getOwnerId() != null ? m.getOwnerId().toString() : null);
                    psM.setString(3, m.getType().name());
                    psM.setInt(4, m.getTier());

                    Location loc = m.getLocation();
                    psM.setString(5, loc.getWorld() != null ? loc.getWorld().getName() : "world");
                    psM.setDouble(6, loc.getX());
                    psM.setDouble(7, loc.getY());
                    psM.setDouble(8, loc.getZ());
                    psM.setFloat(9, loc.getYaw());
                    psM.setFloat(10, loc.getPitch());

                    psM.setString(11, m.getFuelId());
                    psM.setLong(12, m.getFuelEndTime());
                    psM.setString(13, m.getUpgrade1Id());
                    psM.setString(14, m.getUpgrade2Id());
                    psM.setLong(15, m.getLastActionTime());
                    psM.setInt(16, m.wasLastActionPlace() ? 1 : 0);
                    psM.setInt(17, m.getTotalHarvested());
                    psM.setInt(18, m.getStoredXp());
                    psM.setString(19, m.getCustomDefinitionId());
                    psM.addBatch();

                    int slot = 0;
                    for (ItemStack item : m.getInventory()) {
                        if (item != null) {
                            YamlConfiguration yc = new YamlConfiguration();
                            yc.set("item", item);
                            psI.setString(1, m.getMinionId().toString());
                            psI.setInt(2, slot++);
                            psI.setString(3, yc.saveToString());
                            psI.addBatch();
                        }
                    }
                }

                psM.executeBatch();
                psI.executeBatch();
            }

            connection.commit();
            connection.setAutoCommit(true);
            plugin.getLogger().info("Saved " + manager.getAllMinions().size() + " minions to SQL database.");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save minions to database!", e);
        }
    }

    public synchronized void loadAll(MinionManager manager) {
        try {
            ensureConnection();
        } catch (SQLException ignored) {}
        if (connection == null) return;

        int loadedCount = 0;
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM minions")) {

            while (rs.next()) {
                UUID id = UUID.fromString(rs.getString("id"));
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

                minion.setLastActionTime(rs.getLong("last_action"));
                minion.setLastActionWasPlace(rs.getInt("last_was_place") == 1);
                minion.setTotalHarvested(rs.getInt("harvested"));
                minion.setStoredXp(rs.getInt("xp"));
                minion.setCustomDefinitionId(rs.getString("custom_id"));

                // Load items
                try (PreparedStatement psI = connection.prepareStatement("SELECT item_data FROM minion_items WHERE minion_id = ? ORDER BY slot_index ASC")) {
                    psI.setString(1, id.toString());
                    try (ResultSet rsI = psI.executeQuery()) {
                        while (rsI.next()) {
                            String yamlStr = rsI.getString("item_data");
                            YamlConfiguration yc = new YamlConfiguration();
                            yc.loadFromString(yamlStr);
                            ItemStack item = yc.getItemStack("item");
                            if (item != null) {
                                minion.getInventory().add(item);
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

    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {}
        }
    }
}
