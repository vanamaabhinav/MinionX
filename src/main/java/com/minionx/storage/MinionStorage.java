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
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * Handles persistence for active minions.
 * Supports YAML by default, and optional SQLite/MySQL database storage.
 * Enforces atomic file writes, main-thread snapshotting, and non-overlapping saves
 * to eliminate data corruption and item duplication exploits.
 */
public class MinionStorage {

    private final MinionX plugin;
    private final File dataFile;
    private DatabaseStorage databaseStorage;
    private final AtomicBoolean isSaving = new AtomicBoolean(false);

    public MinionStorage(MinionX plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "data/minions.yml");

        String type = plugin.getConfig().getString("storage.type", "YAML").toUpperCase();
        if ("SQLITE".equals(type) || "MYSQL".equals(type)) {
            this.databaseStorage = new DatabaseStorage(plugin);
            if (!this.databaseStorage.init()) {
                plugin.getLogger().warning("Failed to initialize database storage, falling back to YAML.");
                this.databaseStorage = null;
            }
        }
    }

    public synchronized void saveAll(MinionManager manager) {
        if (databaseStorage != null) {
            databaseStorage.saveAll(manager);
            return;
        }

        YamlConfiguration config = new YamlConfiguration();
        for (Minion minion : manager.getAllMinions()) {
            populateConfigSection(config, minion);
        }

        String yamlDump = config.saveToString();
        try {
            writeYamlAtomically(yamlDump);
            plugin.getLogger().info("Saved " + manager.getAllMinions().size() + " minions to minions.yml.");
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save minions to file atomically!", e);
        }
    }

    /**
     * Snapshots minion state on the main thread and flushes to disk/SQL asynchronously
     * with atomic file replacement to eliminate tick drops and prevent crash corruptions.
     */
    public void saveAllAsync(MinionManager manager) {
        if (!isSaving.compareAndSet(false, true)) {
            plugin.getLogger().fine("Skipping async auto-save: previous save still in progress.");
            return;
        }

        if (databaseStorage != null) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    databaseStorage.saveAll(manager);
                } finally {
                    isSaving.set(false);
                }
            });
            return;
        }

        // 1. Snapshot and serialize on MAIN THREAD (thread-safe, zero race conditions)
        YamlConfiguration config = new YamlConfiguration();
        int count = manager.getAllMinions().size();
        for (Minion minion : manager.getAllMinions()) {
            populateConfigSection(config, minion);
        }
        String yamlDump = config.saveToString();

        // 2. Write immutable String dump to disk on ASYNC THREAD
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                writeYamlAtomically(yamlDump);
                plugin.getLogger().info("Async auto-saved " + count + " minions to minions.yml.");
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save minions asynchronously!", e);
            } finally {
                isSaving.set(false);
            }
        });
    }

    private void writeYamlAtomically(String yamlContent) throws IOException {
        File parent = dataFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        File tempFile = new File(parent, "minions.yml.tmp");
        try (BufferedWriter writer = Files.newBufferedWriter(tempFile.toPath(), StandardCharsets.UTF_8)) {
            writer.write(yamlContent);
        }
        try {
            Files.move(tempFile.toPath(), dataFile.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tempFile.toPath(), dataFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void populateConfigSection(YamlConfiguration config, Minion minion) {
        String path = "minions." + minion.getMinionId().toString();

        config.set(path + ".owner", minion.getOwnerId() != null ? minion.getOwnerId().toString() : null);
        config.set(path + ".type", minion.getType() != null ? minion.getType().name() : "COBBLESTONE");
        config.set(path + ".tier", minion.getTier());

        Location loc = minion.getLocation();
        config.set(path + ".world", loc.getWorld() != null ? loc.getWorld().getName() : "world");
        config.set(path + ".x", loc.getX());
        config.set(path + ".y", loc.getY());
        config.set(path + ".z", loc.getZ());
        config.set(path + ".yaw", loc.getYaw());
        config.set(path + ".pitch", loc.getPitch());

        config.set(path + ".fuel", minion.getFuelId());
        config.set(path + ".fuel-end", minion.getFuelEndTime());

        config.set(path + ".upgrade1", minion.getUpgrade1Id());
        config.set(path + ".upgrade2", minion.getUpgrade2Id());
        config.set(path + ".skin", minion.getSkinId());
        config.set(path + ".shipping", minion.getShippingId());
        config.set(path + ".shipping-coins", minion.getShippingCoins());

        config.set(path + ".last-action", minion.getLastActionTime());
        config.set(path + ".last-was-place", minion.wasLastActionPlace());
        config.set(path + ".harvested", minion.getTotalHarvested());

        config.set(path + ".xp", minion.getStoredXp());
        config.set(path + ".custom_id", minion.getCustomDefinitionId());

        // Clone inventory items to prevent concurrent modifications
        List<ItemStack> invCopy = new ArrayList<>();
        for (ItemStack item : minion.getInventory()) {
            if (item != null) {
                invCopy.add(item.clone());
            }
        }
        config.set(path + ".items", invCopy);

        // Linked storage persistence with cloned items
        if (minion.hasLinkedStorage() && minion.getLinkedStorageLocation() != null) {
            config.set(path + ".storage.id", minion.getStorageId());
            Location sl = minion.getLinkedStorageLocation();
            config.set(path + ".storage.world", sl.getWorld() != null ? sl.getWorld().getName() : "world");
            config.set(path + ".storage.x", sl.getBlockX());
            config.set(path + ".storage.y", sl.getBlockY());
            config.set(path + ".storage.z", sl.getBlockZ());

            List<ItemStack> storageCopy = new ArrayList<>();
            for (ItemStack item : minion.getStorageContents()) {
                if (item != null) {
                    storageCopy.add(item.clone());
                }
            }
            config.set(path + ".storage.items", storageCopy);
        }
    }

    public void loadAll(MinionManager manager) {
        if (databaseStorage != null) {
            databaseStorage.loadAll(manager);
            return;
        }

        if (!dataFile.exists()) return;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection section = config.getConfigurationSection("minions");
        if (section == null) return;

        int loadedCount = 0;
        for (String idStr : section.getKeys(false)) {
            try {
                UUID id = UUID.fromString(idStr);
                String ownerStr = section.getString(idStr + ".owner");
                UUID ownerId = ownerStr != null ? UUID.fromString(ownerStr) : null;

                String typeStr = section.getString(idStr + ".type");
                MinionType type = MinionType.fromString(typeStr);
                String customId = section.getString(idStr + ".custom_id");
                if (type == null && (customId == null || customId.isBlank())) continue;
                if (type == null) {
                    type = MinionType.COBBLESTONE;
                }

                int tier = section.getInt(idStr + ".tier", 1);

                String worldName = section.getString(idStr + ".world", "world");
                World world = Bukkit.getWorld(worldName);
                if (world == null) {
                    plugin.getLogger().warning("World '" + worldName + "' not found for minion " + id + ", skipping.");
                    continue;
                }

                double x = section.getDouble(idStr + ".x");
                double y = section.getDouble(idStr + ".y");
                double z = section.getDouble(idStr + ".z");
                float yaw = (float) section.getDouble(idStr + ".yaw", 0.0);
                float pitch = (float) section.getDouble(idStr + ".pitch", 0.0);
                Location loc = new Location(world, x, y, z, yaw, pitch);

                Minion minion = new Minion(id, ownerId, type, tier, loc);

                String fuelStr = section.getString(idStr + ".fuel");
                if (fuelStr != null && !fuelStr.isBlank()) {
                    minion.setFuel(fuelStr);
                    minion.setFuelEndTime(section.getLong(idStr + ".fuel-end", 0L));
                }

                String up1Str = section.getString(idStr + ".upgrade1");
                if (up1Str != null && !up1Str.isBlank()) {
                    minion.setUpgrade1Id(up1Str);
                }

                String up2Str = section.getString(idStr + ".upgrade2");
                if (up2Str != null && !up2Str.isBlank()) {
                    minion.setUpgrade2Id(up2Str);
                }

                String skinStr = section.getString(idStr + ".skin");
                if (skinStr != null && !skinStr.isBlank()) {
                    minion.setSkinId(skinStr);
                }

                String shipStr = section.getString(idStr + ".shipping");
                if (shipStr != null && !shipStr.isBlank()) {
                    minion.setShippingId(shipStr);
                }
                minion.setShippingCoins(section.getDouble(idStr + ".shipping-coins", 0.0));

                minion.setLastActionTime(section.getLong(idStr + ".last-action", System.currentTimeMillis()));
                minion.setLastActionWasPlace(section.getBoolean(idStr + ".last-was-place", false));
                minion.setTotalHarvested(section.getInt(idStr + ".harvested", 0));
                minion.setStoredXp(section.getInt(idStr + ".xp", 0));

                if (customId != null && !customId.isBlank()) {
                    minion.setCustomDefinitionId(customId);
                }

                List<?> rawItems = section.getList(idStr + ".items");
                if (rawItems != null) {
                    for (Object obj : rawItems) {
                        if (obj instanceof ItemStack stack) {
                            minion.getInventory().add(stack);
                        }
                    }
                }

                // Restore linked storage chest and items if saved
                String storageId = section.getString(idStr + ".storage.id");
                if (storageId != null && !storageId.isBlank()) {
                    String sWorldName = section.getString(idStr + ".storage.world", worldName);
                    World sWorld = Bukkit.getWorld(sWorldName);
                    if (sWorld != null) {
                        int sx = section.getInt(idStr + ".storage.x");
                        int sy = section.getInt(idStr + ".storage.y");
                        int sz = section.getInt(idStr + ".storage.z");
                        Location sLoc = new Location(sWorld, sx, sy, sz);
                        minion.linkStorage(sLoc, storageId);

                        List<?> sRawItems = section.getList(idStr + ".storage.items");
                        if (sRawItems != null) {
                            for (Object obj : sRawItems) {
                                if (obj instanceof ItemStack stack) {
                                    minion.getStorageContents().add(stack);
                                }
                            }
                        }
                    }
                }

                manager.register(minion);

                // Spawn visual entities if chunk is currently loaded
                if (loc.isChunkLoaded()) {
                    MinionVisual.spawnOrUpdate(minion);
                    MinionHologram.updateHologram(minion);
                }

                loadedCount++;
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load minion " + idStr, e);
            }
        }

        plugin.getLogger().info("Successfully loaded " + loadedCount + " minions.");
    }

    public void close() {
        if (databaseStorage != null) {
            databaseStorage.close();
        }
    }
}
