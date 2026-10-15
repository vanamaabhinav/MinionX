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
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;

/**
 * Handles persistence for active minions.
 * Supports YAML by default, and robust SQLite/MySQL database storage.
 * Enforces atomic file writes, main-thread snapshotting, non-overlapping saves,
 * and durable operations journaling across both SQL and YAML backends.
 */
public class MinionStorage {

    private final MinionX plugin;
    private final File dataFile;
    private final File pendingOpsFile;
    private DatabaseStorage databaseStorage;

    private final ExecutorService persistenceExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "MinionX-PersistenceWriter");
        t.setDaemon(true);
        return t;
    });
    private final ConcurrentMap<UUID, MinionMutation> pendingMutations = new ConcurrentHashMap<>();
    private final Set<UUID> barrierMinions = ConcurrentHashMap.newKeySet();

    public MinionStorage(MinionX plugin) {
        this(plugin, plugin != null ? plugin.getDataFolder() : new File("."));
    }

    public MinionStorage(MinionX plugin, File dataFolder) {
        this.plugin = plugin;
        this.dataFile = new File(dataFolder, "data/minions.yml");
        this.pendingOpsFile = new File(dataFolder, "data/pending_operations.yml");
    }

    public boolean init() {
        if (plugin == null) return true;
        String type = plugin.getConfig().getString("storage.type", "YAML").toUpperCase();
        if ("SQLITE".equals(type) || "MYSQL".equals(type)) {
            this.databaseStorage = new DatabaseStorage(plugin);
            if (!this.databaseStorage.init()) {
                plugin.getLogger().severe("CRITICAL: Failed to initialize database storage! Aborting startup to prevent silent fallback.");
                return false;
            }
        }
        return true;
    }

    public DatabaseStorage getDatabaseStorage() {
        return databaseStorage;
    }

    public void setDatabaseStorage(DatabaseStorage databaseStorage) {
        this.databaseStorage = databaseStorage;
    }

    public ConcurrentMap<UUID, MinionMutation> getPendingMutations() {
        return pendingMutations;
    }

    public Set<UUID> getBarrierMinions() {
        return barrierMinions;
    }

    public boolean isBarrierActive(UUID minionId) {
        return minionId != null && barrierMinions.contains(minionId);
    }

    public void submitMutation(DatabaseStorage.MinionSnapshot snapshot, long revision) {
        if (snapshot == null) return;
        UUID id = snapshot.id();
        if (barrierMinions.contains(id)) return;

        pendingMutations.compute(id, (k, existing) -> {
            if (existing != null && existing.isTombstone()) {
                if (revision <= existing.stateRevision()) return existing;
            }
            if (existing == null || revision >= existing.stateRevision()) {
                return MinionMutation.of(snapshot, revision);
            }
            return existing;
        });
    }

    public void recordTombstone(UUID minionId, long revision) {
        if (minionId == null) return;
        pendingMutations.compute(minionId, (k, existing) -> {
            if (existing == null || revision >= existing.stateRevision()) {
                return MinionMutation.tombstone(minionId, revision);
            }
            return existing;
        });
    }

    public <T> CompletableFuture<T> executeBarrier(UUID minionId, Callable<T> task) {
        if (minionId != null) {
            barrierMinions.add(minionId);
        }
        CompletableFuture<T> future = new CompletableFuture<>();
        persistenceExecutor.submit(() -> {
            try {
                T result = task.call();
                future.complete(result);
            } catch (Throwable t) {
                future.completeExceptionally(t);
            } finally {
                if (minionId != null) {
                    barrierMinions.remove(minionId);
                }
            }
        });
        return future;
    }

    public CompletableFuture<Void> executeBarrier(UUID minionId, Runnable task) {
        return executeBarrier(minionId, () -> {
            task.run();
            return null;
        });
    }

    public CompletableFuture<Void> flushPendingMutationsAsync() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        persistenceExecutor.submit(() -> {
            try {
                flushPendingMutationsSync();
                future.complete(null);
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        return future;
    }

    public synchronized void flushPendingMutationsSync() {
        if (pendingMutations.isEmpty()) return;

        Map<UUID, MinionMutation> batch = new HashMap<>(pendingMutations);
        for (UUID id : batch.keySet()) {
            pendingMutations.remove(id, batch.get(id));
        }

        if (databaseStorage != null) {
            List<DatabaseStorage.MinionSnapshot> snapshotsToSave = new ArrayList<>();
            for (MinionMutation m : batch.values()) {
                if (m.isTombstone()) {
                    databaseStorage.deleteMinionData(m.minionId());
                } else if (m.snapshot() != null) {
                    snapshotsToSave.add(m.snapshot());
                }
            }
            if (!snapshotsToSave.isEmpty()) {
                databaseStorage.saveSnapshots(snapshotsToSave);
            }
            return;
        }

        // YAML Document-Safe Merge
        try {
            YamlConfiguration config = dataFile.exists() ?
                    YamlConfiguration.loadConfiguration(dataFile) : new YamlConfiguration();

            for (MinionMutation m : batch.values()) {
                String path = "minions." + m.minionId().toString();
                if (m.isTombstone()) {
                    config.set(path, null);
                } else if (m.snapshot() != null) {
                    populateConfigSectionFromSnapshot(config, m.snapshot());
                }
            }

            writeYamlAtomically(config.saveToString());
        } catch (Exception e) {
            logSevere("Failed to flush pending mutations to YAML file!", e);
        }
    }

    public synchronized void saveAll(MinionManager manager) {
        if (manager == null) return;
        for (Minion minion : manager.getAllMinions()) {
            DatabaseStorage.MinionSnapshot snapshot = DatabaseStorage.MinionSnapshot.fromMinion(minion);
            submitMutation(snapshot, minion.getStateRevision());
        }
        flushPendingMutationsSync();
        logInfo("Saved " + manager.getAllMinions().size() + " minions to persistence storage.");
    }

    /**
     * Snapshots minion state on the main thread and queues sequential persistence flush
     * with atomic file replacement to eliminate tick drops and prevent crash corruptions.
     */
    public void saveAllAsync(MinionManager manager) {
        if (manager == null) return;
        int count = 0;
        for (Minion minion : manager.getAllMinions()) {
            if (!isBarrierActive(minion.getMinionId())) {
                DatabaseStorage.MinionSnapshot snapshot = DatabaseStorage.MinionSnapshot.fromMinion(minion);
                submitMutation(snapshot, minion.getStateRevision());
                count++;
            }
        }
        final int snapped = count;
        flushPendingMutationsAsync().thenRun(() -> {
            logInfo("Async auto-saved " + snapped + " minions to persistence storage.");
        });
    }

    private void writeYamlAtomically(String yamlContent) throws IOException {
        writeYamlAtomically(yamlContent, dataFile);
    }

    private void writeYamlAtomically(String yamlContent, File targetFile) throws IOException {
        File parent = targetFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        File tempFile = new File(parent, targetFile.getName() + ".tmp");
        try (BufferedWriter writer = Files.newBufferedWriter(tempFile.toPath(), StandardCharsets.UTF_8)) {
            writer.write(yamlContent);
        }
        try {
            Files.move(tempFile.toPath(), targetFile.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tempFile.toPath(), targetFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void populateConfigSectionFromSnapshot(YamlConfiguration config, DatabaseStorage.MinionSnapshot s) {
        String path = "minions." + s.id().toString();

        config.set(path + ".owner", s.ownerId() != null ? s.ownerId().toString() : null);
        config.set(path + ".type", s.type());
        config.set(path + ".tier", s.tier());

        config.set(path + ".world", s.world());
        config.set(path + ".x", s.x());
        config.set(path + ".y", s.y());
        config.set(path + ".z", s.z());
        config.set(path + ".yaw", s.yaw());
        config.set(path + ".pitch", s.pitch());

        config.set(path + ".fuel", s.fuelId());
        config.set(path + ".fuel-end", s.fuelEndTime());

        config.set(path + ".upgrade1", s.upgrade1Id());
        config.set(path + ".upgrade2", s.upgrade2Id());
        config.set(path + ".skin", s.skinId());
        config.set(path + ".shipping", s.shippingId());
        config.set(path + ".shipping-coins", s.shippingCoins());

        config.set(path + ".last-action", s.lastActionTime());
        config.set(path + ".last-was-place", s.lastActionWasPlace());
        config.set(path + ".harvested", s.totalHarvested());

        config.set(path + ".xp", s.storedXp());
        config.set(path + ".custom_id", s.customDefinitionId());

        List<ItemStack> invCopy = new ArrayList<>();
        for (String raw : s.serializedInventoryItems()) {
            try {
                YamlConfiguration yc = new YamlConfiguration();
                yc.loadFromString(raw);
                ItemStack item = yc.getItemStack("item");
                if (item != null) invCopy.add(item);
            } catch (Exception ignored) {}
        }
        config.set(path + ".items", invCopy);

        if (s.storageId() != null && !s.storageId().isBlank()) {
            config.set(path + ".storage.id", s.storageId());
            config.set(path + ".storage.world", s.storageWorld() != null ? s.storageWorld() : s.world());
            if (s.storageX() != null) config.set(path + ".storage.x", s.storageX());
            if (s.storageY() != null) config.set(path + ".storage.y", s.storageY());
            if (s.storageZ() != null) config.set(path + ".storage.z", s.storageZ());

            List<ItemStack> storageCopy = new ArrayList<>();
            for (String raw : s.serializedStorageItems()) {
                try {
                    YamlConfiguration yc = new YamlConfiguration();
                    yc.loadFromString(raw);
                    ItemStack item = yc.getItemStack("item");
                    if (item != null) storageCopy.add(item);
                } catch (Exception ignored) {}
            }
            config.set(path + ".storage.items", storageCopy);
        }
    }

    public void loadAll(MinionManager manager) {
        if (databaseStorage != null) {
            databaseStorage.loadAll(manager);
            return;
        }

        // Identify quarantined minions with pending operations
        Set<UUID> quarantinedMinionIds = new HashSet<>();
        List<PendingOperation> pending = getPendingOperations();
        for (PendingOperation op : pending) {
            quarantinedMinionIds.add(op.minionId());
            logWarning("Minion " + op.minionId() + " has pending operation " + op.operationId() +
                    " (" + op.state() + ") by player " + op.playerUuid() + ". Quarantining from active ticking.");
        }

        if (!dataFile.exists()) return;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        List<String> pendingPurges = config.getStringList("pending_storage_purges");
        if (pendingPurges != null && !pendingPurges.isEmpty()) {
            manager.loadPendingStoragePurges(pendingPurges);
        }

        ConfigurationSection section = config.getConfigurationSection("minions");
        if (section == null) return;

        int loadedCount = 0;
        for (String idStr : section.getKeys(false)) {
            try {
                UUID id = UUID.fromString(idStr);
                if (quarantinedMinionIds.contains(id)) {
                    continue; // Skip loading quarantined minion
                }

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
                logWarning("Failed to load minion " + idStr, e);
            }
        }

        logInfo("Successfully loaded " + loadedCount + " minions.");
    }

    // --- Durable Operations Journal & Finalization ---

    public synchronized void recordPendingOperation(PendingOperation op) throws Exception {
        if (databaseStorage != null) {
            databaseStorage.recordPendingOperation(op);
            return;
        }

        YamlConfiguration config = pendingOpsFile.exists() ?
                YamlConfiguration.loadConfiguration(pendingOpsFile) : new YamlConfiguration();
        String path = "operations." + op.operationId();
        config.set(path + ".minion_id", op.minionId().toString());
        config.set(path + ".player_uuid", op.playerUuid().toString());
        config.set(path + ".state", op.state());
        config.set(path + ".created_at", op.createdAt());
        writeYamlAtomically(config.saveToString(), pendingOpsFile);
    }

    public synchronized void removePendingOperation(String operationId) throws Exception {
        if (databaseStorage != null) {
            databaseStorage.removePendingOperation(operationId);
            return;
        }

        if (!pendingOpsFile.exists()) return;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(pendingOpsFile);
        config.set("operations." + operationId, null);
        writeYamlAtomically(config.saveToString(), pendingOpsFile);
    }

    public synchronized List<PendingOperation> getPendingOperations() {
        if (databaseStorage != null) {
            try {
                return databaseStorage.getPendingOperations();
            } catch (SQLException e) {
                logSevere("Failed to get pending operations from database!", e);
                return Collections.emptyList();
            }
        }

        if (!pendingOpsFile.exists()) return Collections.emptyList();
        List<PendingOperation> list = new ArrayList<>();
        YamlConfiguration config = YamlConfiguration.loadConfiguration(pendingOpsFile);
        ConfigurationSection sec = config.getConfigurationSection("operations");
        if (sec != null) {
            for (String opId : sec.getKeys(false)) {
                try {
                    UUID minionId = UUID.fromString(sec.getString(opId + ".minion_id"));
                    UUID playerUuid = UUID.fromString(sec.getString(opId + ".player_uuid"));
                    String state = sec.getString(opId + ".state", "PICKUP_PENDING");
                    long createdAt = sec.getLong(opId + ".created_at", System.currentTimeMillis());
                    list.add(new PendingOperation(opId, minionId, playerUuid, state, createdAt));
                } catch (Exception ignored) {}
            }
        }
        return list;
    }

    public synchronized boolean finalizePickup(String operationId, UUID minionId) {
        recordTombstone(minionId, Long.MAX_VALUE);
        if (databaseStorage != null) {
            return databaseStorage.finalizePickup(operationId, minionId);
        }

        try {
            if (dataFile.exists()) {
                YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
                config.set("minions." + minionId.toString(), null);
                writeYamlAtomically(config.saveToString(), dataFile);
            }
            if (pendingOpsFile.exists()) {
                YamlConfiguration opConfig = YamlConfiguration.loadConfiguration(pendingOpsFile);
                opConfig.set("operations." + operationId, null);
                writeYamlAtomically(opConfig.saveToString(), pendingOpsFile);
            }
            return true;
        } catch (IOException e) {
            logSevere("Failed to finalize YAML pickup for minion " + minionId, e);
            return false;
        }
    }

    private void logInfo(String msg) {
        if (plugin != null) plugin.getLogger().info(msg);
    }

    private void logWarning(String msg) {
        if (plugin != null) plugin.getLogger().warning(msg);
    }

    private void logWarning(String msg, Throwable t) {
        if (plugin != null) plugin.getLogger().log(Level.WARNING, msg, t);
    }

    private void logSevere(String msg, Throwable t) {
        if (plugin != null) plugin.getLogger().log(Level.SEVERE, msg, t);
    }

    public void close() {
        flushPendingMutationsSync();
        persistenceExecutor.shutdown();
        if (databaseStorage != null) {
            databaseStorage.close();
        }
    }
}
