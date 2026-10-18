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

import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
public class MinionStorage implements AutoCloseable {

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
    private final Set<UUID> tombstoneSet = ConcurrentHashMap.newKeySet();
    private final Set<UUID> payoutLocks = ConcurrentHashMap.newKeySet();
    private final Set<UUID> quarantinedMinions = ConcurrentHashMap.newKeySet();

    public MinionStorage(MinionX plugin) {
        this(plugin, plugin != null ? plugin.getDataFolder() : new File("."));
    }

    public MinionStorage(MinionX plugin, File dataFolder) {
        this.plugin = plugin;
        this.dataFile = new File(dataFolder, "data/minions.yml");
        this.pendingOpsFile = new File(dataFolder, "data/pending_operations.yml");
    }

    public boolean init() {
        if (plugin != null) {
            String type = plugin.getConfig().getString("storage.type", "YAML").toUpperCase();
            if ("SQLITE".equals(type) || "MYSQL".equals(type)) {
                this.databaseStorage = new DatabaseStorage(plugin);
                if (!this.databaseStorage.init()) {
                    plugin.getLogger().severe("CRITICAL: Failed to initialize database storage! Aborting startup to prevent silent fallback.");
                    return false;
                }
                // Pre-load tombstones before allowing mutations or normal minion loading
                Set<UUID> dbTombstones = this.databaseStorage.loadAllTombstones();
                tombstoneSet.addAll(dbTombstones);
                logInfo("Loaded " + dbTombstones.size() + " tombstones from database into tombstone fence.");
                return true;
            }
        }
        // YAML mode
        verifyAndRecoverYamlFiles();
        loadTombstonesFromYaml();
        return true;
    }

    public ExecutorService getPersistenceExecutor() {
        return persistenceExecutor;
    }

    public boolean isPayoutActive(UUID minionId) {
        return minionId != null && payoutLocks.contains(minionId);
    }

    public boolean acquirePayoutLock(UUID minionId) {
        return minionId != null && payoutLocks.add(minionId);
    }

    public void releasePayoutLock(UUID minionId) {
        if (minionId != null) {
            payoutLocks.remove(minionId);
        }
    }

    public Set<UUID> getQuarantinedMinions() {
        return Collections.unmodifiableSet(quarantinedMinions);
    }

    public boolean isQuarantined(UUID minionId) {
        return minionId != null && quarantinedMinions.contains(minionId);
    }

    public void quarantineMinion(UUID minionId) {
        if (minionId != null) {
            quarantinedMinions.add(minionId);
            barrierMinions.add(minionId);
        }
    }

    public void unquarantineMinion(UUID minionId) {
        if (minionId != null) {
            quarantinedMinions.remove(minionId);
            barrierMinions.remove(minionId);
        }
    }

    public Set<UUID> getTombstoneSet() {
        return Collections.unmodifiableSet(tombstoneSet);
    }

    public boolean isTombstoned(UUID minionId) {
        return minionId != null && tombstoneSet.contains(minionId);
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
        if (tombstoneSet.contains(id)) {
            // Permanently reject snapshot write for tombstoned minion (preserves tombstone in pendingMutations)
            return;
        }
        if (barrierMinions.contains(id)) return;

        pendingMutations.compute(id, (k, existing) -> {
            if (existing != null && existing.isTombstone()) {
                return existing;
            }
            if (existing == null || revision >= existing.stateRevision()) {
                return MinionMutation.of(snapshot, revision);
            }
            return existing;
        });
    }

    public void recordTombstone(UUID minionId, long revision) {
        if (minionId == null) return;
        tombstoneSet.add(minionId);
        pendingMutations.compute(minionId, (k, existing) -> {
            return MinionMutation.tombstone(minionId, revision);
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
        boolean success = false;

        try {
            if (databaseStorage != null) {
                List<DatabaseStorage.MinionSnapshot> snapshotsToSave = new ArrayList<>();
                for (MinionMutation m : batch.values()) {
                    if (m.isTombstone()) {
                        databaseStorage.deleteMinion(m.minionId(), m.stateRevision(), System.currentTimeMillis());
                    } else if (m.snapshot() != null && !tombstoneSet.contains(m.minionId())) {
                        if (!payoutLocks.contains(m.minionId())) {
                            snapshotsToSave.add(m.snapshot());
                        }
                    }
                }
                if (!snapshotsToSave.isEmpty()) {
                    success = databaseStorage.saveSnapshots(snapshotsToSave);
                } else {
                    success = true;
                }
            } else {
                success = flushBatchToYaml(batch);
            }
        } catch (Exception e) {
            logSevere("Failed to flush pending mutations to persistence storage!", e);
            success = false;
        }

        if (success) {
            // Exact revision compare-and-remove eviction with tombstone protection
            for (Map.Entry<UUID, MinionMutation> entry : batch.entrySet()) {
                UUID id = entry.getKey();
                if (!entry.getValue().isTombstone() && payoutLocks.contains(id)) {
                    continue; // Defer eviction of in-flight payout snapshot
                }
                long savedRev = entry.getValue().stateRevision();
                boolean wasTombstone = entry.getValue().isTombstone();
                pendingMutations.computeIfPresent(id, (k, current) -> {
                    if (current.isTombstone()) {
                        // Only evict tombstone if the saved mutation was actually a tombstone
                        if (wasTombstone && current.stateRevision() <= savedRev) {
                            return null;
                        }
                        return current;
                    }
                    if (current.stateRevision() <= savedRev) {
                        return null; // Evict saved mutation
                    }
                    return current; // Retain newer mutation queued while save was in-flight
                });
            }
        } else {
            logWarning("Persistence write failed: retaining " + batch.size() + " mutations in queue for retry.");
        }
    }

    private synchronized boolean flushBatchToYaml(Map<UUID, MinionMutation> batch) {
        try {
            YamlConfiguration config;
            if (dataFile.exists() && dataFile.length() > 0) {
                config = YamlConfiguration.loadConfiguration(dataFile);
            } else {
                config = new YamlConfiguration();
                config.set("schema-version", 2);
            }

            for (MinionMutation m : batch.values()) {
                String path = "minions." + m.minionId().toString();
                if (m.isTombstone()) {
                    config.set(path, null);
                    config.set("tombstones." + m.minionId().toString() + ".deleted-revision", m.stateRevision());
                    config.set("tombstones." + m.minionId().toString() + ".deleted-at", m.timestamp());
                } else if (m.snapshot() != null && !tombstoneSet.contains(m.minionId())) {
                    if (!payoutLocks.contains(m.minionId())) {
                        populateConfigSectionFromSnapshot(config, m.snapshot());
                    }
                }
            }

            if (config.get("schema-version") == null) {
                config.set("schema-version", 2);
            }

            String yamlContent = config.saveToString();
            return writeYamlAtomically(yamlContent);
        } catch (Exception e) {
            logSevere("Failed to flush batch to YAML!", e);
            return false;
        }
    }

    public synchronized void verifyAndRecoverYamlFiles() {
        File parent = dataFile.getParentFile();
        if (parent == null || !parent.exists()) return;

        File bakFile = new File(parent, dataFile.getName() + ".bak");
        boolean targetValid = false;

        if (dataFile.exists() && dataFile.length() > 0) {
            try {
                YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
                if (!config.getKeys(false).isEmpty()) {
                    targetValid = true;
                }
            } catch (Exception ignored) {}
        }

        if (!targetValid) {
            if (bakFile.exists() && bakFile.length() > 0) {
                try {
                    YamlConfiguration bakConfig = YamlConfiguration.loadConfiguration(bakFile);
                    if (!bakConfig.getKeys(false).isEmpty()) {
                        Files.copy(bakFile.toPath(), dataFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        logSevere("CRITICAL ALERT: Corrupted or missing " + dataFile.getName() + " detected! Successfully restored from backup " + bakFile.getName(), null);
                        targetValid = true;
                    }
                } catch (Exception e) {
                    logSevere("Backup file also corrupted! Cannot restore.", e);
                }
            }
        }

        if (!targetValid && dataFile.exists() && dataFile.length() > 0) {
            logSevere("CRITICAL WARNING: " + dataFile.getName() + " is corrupted and no valid backup exists. Preserving file for admin recovery; not initializing blank state.", null);
        }
    }

    public synchronized void loadTombstonesFromYaml() {
        if (!dataFile.exists() || dataFile.length() == 0) return;
        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
            ConfigurationSection tsSec = config.getConfigurationSection("tombstones");
            if (tsSec != null) {
                for (String idStr : tsSec.getKeys(false)) {
                    try {
                        UUID id = UUID.fromString(idStr);
                        tombstoneSet.add(id);
                    } catch (IllegalArgumentException ignored) {}
                }
                logInfo("Loaded " + tombstoneSet.size() + " tombstones from YAML tombstone fence.");
            }
        } catch (Exception e) {
            logSevere("Failed to load tombstones from YAML!", e);
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

    private synchronized boolean writeYamlAtomically(String yamlContent) throws IOException {
        return writeYamlAtomically(yamlContent, dataFile);
    }

    private synchronized boolean writeYamlAtomically(String yamlContent, File targetFile) throws IOException {
        File parent = targetFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        File tempFile = new File(parent, targetFile.getName() + ".tmp." + UUID.randomUUID());
        File bakFile = new File(parent, targetFile.getName() + ".bak");

        // 1. Write to temp file and sync OS buffers
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            byte[] bytes = yamlContent.getBytes(StandardCharsets.UTF_8);
            fos.write(bytes);
            fos.getFD().sync();
        }

        // 2. Semantic validation of temp file
        if (tempFile.length() == 0) {
            tempFile.delete();
            throw new IOException("Temp YAML file is 0 bytes after write!");
        }
        try {
            YamlConfiguration check = YamlConfiguration.loadConfiguration(tempFile);
            if (check.getKeys(false).isEmpty()) {
                tempFile.delete();
                throw new IOException("Temp YAML file failed semantic check: root has no keys!");
            }
        } catch (Exception e) {
            tempFile.delete();
            throw new IOException("Temp YAML file failed validation parse!", e);
        }

        // 3. Backup existing target file if valid
        if (targetFile.exists() && targetFile.length() > 0) {
            try {
                YamlConfiguration targetCheck = YamlConfiguration.loadConfiguration(targetFile);
                if (!targetCheck.getKeys(false).isEmpty()) {
                    Files.copy(targetFile.toPath(), bakFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (Exception ignored) {}
        }

        // 4. Atomic move with fallback
        try {
            Files.move(tempFile.toPath(), targetFile.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tempFile.toPath(), targetFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
        }

        // 5. Post-move semantic validation of target file
        try {
            YamlConfiguration destCheck = YamlConfiguration.loadConfiguration(targetFile);
            if (destCheck.getKeys(false).isEmpty() && targetFile.length() == 0) {
                if (bakFile.exists() && bakFile.length() > 0) {
                    Files.copy(bakFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    logSevere("Target YAML file became empty after move! Restored from backup.", null);
                }
                return false;
            }
        } catch (Exception e) {
            if (bakFile.exists() && bakFile.length() > 0) {
                Files.copy(bakFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                logSevere("Target YAML file corrupted after move! Restored from backup.", e);
            }
            return false;
        }

        return true;
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
        config.set(path + ".state-revision", s.stateRevision());
        config.set(path + ".fractional-progress", s.fractionalProgressMicros());
        if (s.dropRemainders() != null && !s.dropRemainders().isBlank()) {
            config.set(path + ".drop-remainders", s.dropRemainders());
        }

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
            reconcilePendingOperations(manager);
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
                if (tombstoneSet.contains(id)) {
                    continue; // Skip loading tombstoned minion
                }
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

                long stateRev = section.getLong(idStr + ".state-revision", 0L);
                long fracProgress = section.getLong(idStr + ".fractional-progress", 0L);
                minion.setFractionalProgressMicros(fracProgress);
                if (section.isConfigurationSection(idStr + ".drop-remainders")) {
                    ConfigurationSection remSec = section.getConfigurationSection(idStr + ".drop-remainders");
                    if (remSec != null) {
                        for (String key : remSec.getKeys(false)) {
                            minion.setChannelRemainder(key, remSec.getLong(key));
                        }
                    }
                } else {
                    String remStr = section.getString(idStr + ".drop-remainders");
                    if (remStr != null && !remStr.isBlank()) {
                        Map<String, Long> remMap = DatabaseStorage.deserializeRemainders(remStr);
                        for (Map.Entry<String, Long> entry : remMap.entrySet()) {
                            minion.setChannelRemainder(entry.getKey(), entry.getValue());
                        }
                    }
                }
                minion.setStateRevision(stateRev);

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
        reconcilePendingOperations(manager);
    }

    public synchronized void reconcilePendingOperations(MinionManager manager) {
        List<PendingOperation> pending = getPendingOperations();
        if (!pending.isEmpty()) {
            for (PendingOperation op : pending) {
                String state = op.state();
                if ("PREPARED".equalsIgnoreCase(state)) {
                    boolean minionExists = (manager != null && manager.getMinionById(op.minionId()) != null) || (!tombstoneSet.contains(op.minionId()) && minionExistsInStorage(op.minionId()));
                    if (minionExists) {
                        try {
                            removePendingOperation(op.operationId());
                            logInfo("Startup reconciliation: Cancelled interrupted PREPARED pickup " + op.operationId() + " for active minion " + op.minionId());
                        } catch (Exception ignored) {}
                    } else {
                        try {
                            updatePendingOperationState(op.operationId(), "MINION_REMOVED", op.payload());
                            logWarning("Startup reconciliation: Transitioned PREPARED pickup " + op.operationId() + " to MINION_REMOVED (minion was deleted). Ready for claim.");
                        } catch (Exception ignored) {}
                    }
                } else if ("DELIVERY_ATTEMPTED".equalsIgnoreCase(state)) {
                    try {
                        updatePendingOperationState(op.operationId(), "UNKNOWN", op.payload());
                        quarantineMinion(op.minionId());
                        logWarning("Startup reconciliation: Operation " + op.operationId() + " was DELIVERY_ATTEMPTED before shutdown. Quarantined as UNKNOWN for admin audit. Automatic redelivery prohibited.");
                    } catch (Exception ignored) {}
                } else if ("AMBIGUOUS_PARTIAL_DELIVERY".equalsIgnoreCase(state)) {
                    quarantineMinion(op.minionId());
                    logSevere("Startup reconciliation: Operation " + op.operationId() + " has state AMBIGUOUS_PARTIAL_DELIVERY! Automatic redelivery prohibited to prevent item duplication. Audit ledger preserved: " + op.payload(), null);
                } else if ("DELIVERY_FAILED_OFFLINE".equalsIgnoreCase(state)) {
                    quarantineMinion(op.minionId());
                    logWarning("Startup reconciliation: Operation " + op.operationId() + " was DELIVERY_FAILED_OFFLINE. Retaining items and unconsumed XP for safe re-claim.");
                } else if ("MINION_REMOVED".equalsIgnoreCase(state) || "DELIVERY_CLAIM_FAILED".equalsIgnoreCase(state) || "QUARANTINED_LEFTOVERS".equalsIgnoreCase(state) || "UNKNOWN".equalsIgnoreCase(state)) {
                    quarantineMinion(op.minionId());
                    logWarning("Startup reconciliation: Preserved durable escrow operation " + op.operationId() + " (state: " + state + ") for minion " + op.minionId() + ". Quarantined for safe claim/audit.");
                } else if ("ATTEMPTED".equalsIgnoreCase(state)) {
                    // In-flight payout when shutdown/crash occurred:
                    boolean minionExists = (manager != null && manager.getMinionById(op.minionId()) != null) || (!tombstoneSet.contains(op.minionId()) && minionExistsInStorage(op.minionId()));
                    if (minionExists) {
                        double amt = 0.0;
                        try {
                            amt = Double.parseDouble(op.payload());
                        } catch (Exception ignored) {}
                        if (amt > 0.0) {
                            rollbackPayoutAtomic(op.operationId(), op.minionId(), amt);
                            if (manager != null) {
                                Minion m = manager.getMinionById(op.minionId());
                                if (m != null) {
                                    m.addShippingCoins(amt);
                                }
                            }
                            logWarning("Startup reconciliation: Rolled back interrupted ATTEMPTED payout " + op.operationId() + " (" + amt + " coins) for minion " + op.minionId());
                        }
                    } else {
                        ambiguousPayoutAtomic(op.operationId());
                        logWarning("Startup reconciliation: Minion " + op.minionId() + " absent for ATTEMPTED payout " + op.operationId() + ". Marked PAYOUT_AMBIGUOUS.");
                    }
                }
            }
        }

        // Detect inconsistent state: active minion in manager or storage that is also tombstoned
        for (UUID tombId : tombstoneSet) {
            if (manager != null && manager.getMinionById(tombId) != null) {
                quarantineMinion(tombId);
                manager.unregister(manager.getMinionById(tombId));
                logSevere("CRITICAL INCONSISTENCY: Minion " + tombId + " was active in manager but also tombstoned! Quarantined from runtime.", null);
            } else if (rawMinionRecordExists(tombId)) {
                quarantineMinion(tombId);
                logSevere("CRITICAL INCONSISTENCY: Minion " + tombId + " exists in storage but is also tombstoned! Quarantined from runtime.", null);
            }
        }
    }

    public boolean rawMinionRecordExists(UUID minionId) {
        if (minionId == null) return false;
        if (databaseStorage != null) {
            return databaseStorage.minionExists(minionId);
        }
        if (!dataFile.exists()) return false;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        return config.contains("minions." + minionId);
    }

    public boolean minionExistsInStorage(UUID minionId) {
        if (minionId == null || tombstoneSet.contains(minionId)) return false;
        return rawMinionRecordExists(minionId);
    }

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
        if (op.payload() != null) {
            config.set(path + ".payload", op.payload());
        }
        writeYamlAtomically(config.saveToString(), pendingOpsFile);
    }

    public synchronized void updatePendingOperationState(String operationId, String newState, String payload) throws Exception {
        if (databaseStorage != null) {
            databaseStorage.updatePendingOperationState(operationId, newState, payload);
            return;
        }

        if (!pendingOpsFile.exists()) return;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(pendingOpsFile);
        String path = "operations." + operationId;
        config.set(path + ".state", newState);
        if (payload != null) {
            config.set(path + ".payload", payload);
        }
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
                    String payload = sec.getString(opId + ".payload");
                    list.add(new PendingOperation(opId, minionId, playerUuid, state, createdAt, payload));
                } catch (Exception ignored) {}
            }
        }
        return list;
    }

    /**
     * Atomically deletes minion from persistence, records tombstone, and writes MINION_REMOVED with payload.
     * Enforces precondition check and idempotent retry handling.
     */
    public synchronized DatabaseStorage.PickupRemovalResult executePickupRemoval(String operationId, UUID minionId, UUID playerUuid, long revision, String payload) {
        if (databaseStorage != null) {
            DatabaseStorage.PickupRemovalResult result = databaseStorage.executePickupRemoval(operationId, minionId, playerUuid, revision, payload);
            if (result == DatabaseStorage.PickupRemovalResult.COMMITTED_NOW || result == DatabaseStorage.PickupRemovalResult.ALREADY_COMMITTED_MINION_REMOVED) {
                recordTombstone(minionId, revision);
            }
            return result;
        }

        try {
            if (!dataFile.exists()) return DatabaseStorage.PickupRemovalResult.PRECONDITION_FAILED;
            YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
            YamlConfiguration opConfig = pendingOpsFile.exists() ? YamlConfiguration.loadConfiguration(pendingOpsFile) : new YamlConfiguration();
            String opPath = "operations." + operationId;

            // 1. Check if operationId already exists
            if (opConfig.contains(opPath)) {
                String storedMinion = opConfig.getString(opPath + ".minion_id");
                String storedPlayer = opConfig.getString(opPath + ".player_uuid");
                String storedPayload = opConfig.getString(opPath + ".payload");
                String storedState = opConfig.getString(opPath + ".state");

                if (!minionId.toString().equalsIgnoreCase(storedMinion) ||
                    (playerUuid != null && !playerUuid.toString().equalsIgnoreCase(storedPlayer)) ||
                    !Objects.equals(payload, storedPayload)) {
                    return DatabaseStorage.PickupRemovalResult.CONFLICTING_PAYLOAD;
                }

                // Verify consistency: active minion absent and tombstone matches operationId
                String minionPath = "minions." + minionId.toString();
                String tOp = config.getString("tombstones." + minionId + ".operation-id");
                if (config.contains(minionPath) || !operationId.equalsIgnoreCase(tOp)) {
                    quarantineMinion(minionId);
                    return DatabaseStorage.PickupRemovalResult.UNKNOWN_OUTCOME;
                }

                return switch (storedState != null ? storedState.toUpperCase() : "") {
                    case "MINION_REMOVED" -> DatabaseStorage.PickupRemovalResult.ALREADY_COMMITTED_MINION_REMOVED;
                    case "DELIVERY_ATTEMPTED", "UNKNOWN" -> DatabaseStorage.PickupRemovalResult.ALREADY_DELIVERY_ATTEMPTED;
                    case "COMPLETED" -> DatabaseStorage.PickupRemovalResult.ALREADY_COMPLETED;
                    default -> DatabaseStorage.PickupRemovalResult.UNKNOWN_OUTCOME;
                };
            }

            // Reject if a tombstone already exists for this minionId before deleting active row!
            if (config.contains("tombstones." + minionId)) {
                quarantineMinion(minionId);
                return DatabaseStorage.PickupRemovalResult.UNKNOWN_OUTCOME;
            }

            // 2. Precondition check: Minion row exists and revision matches
            String minionPath = "minions." + minionId.toString();
            if (!config.contains(minionPath)) {
                return DatabaseStorage.PickupRemovalResult.PRECONDITION_FAILED;
            }
            long storedRev = config.getLong(minionPath + ".state-revision", 0L);
            if (storedRev != revision) {
                return DatabaseStorage.PickupRemovalResult.PRECONDITION_FAILED;
            }

            // 3. Remove minion and record tombstone
            config.set(minionPath, null);
            config.set("tombstones." + minionId + ".operation-id", operationId);
            config.set("tombstones." + minionId + ".deleted-revision", revision);
            config.set("tombstones." + minionId + ".deleted-at", System.currentTimeMillis());
            writeYamlAtomically(config.saveToString(), dataFile);

            // 4. Record pending op
            opConfig.set(opPath + ".minion_id", minionId.toString());
            opConfig.set(opPath + ".player_uuid", playerUuid != null ? playerUuid.toString() : "");
            opConfig.set(opPath + ".state", "MINION_REMOVED");
            opConfig.set(opPath + ".created_at", System.currentTimeMillis());
            if (payload != null) {
                opConfig.set(opPath + ".payload", payload);
            }
            writeYamlAtomically(opConfig.saveToString(), pendingOpsFile);
            recordTombstone(minionId, revision);
            return DatabaseStorage.PickupRemovalResult.COMMITTED_NOW;
        } catch (IOException e) {
            logSevere("Failed to execute atomic YAML pickup removal for " + minionId, e);
            quarantineMinion(minionId);
            return DatabaseStorage.PickupRemovalResult.UNKNOWN_OUTCOME;
        }
    }

    public synchronized boolean claimDeliveryAttempt(String operationId) {
        if (databaseStorage != null) {
            return databaseStorage.claimDeliveryAttempt(operationId);
        }
        try {
            if (!pendingOpsFile.exists()) return false;
            YamlConfiguration opConfig = YamlConfiguration.loadConfiguration(pendingOpsFile);
            String path = "operations." + operationId;
            if (!opConfig.contains(path)) return false;
            String state = opConfig.getString(path + ".state");
            if (!"MINION_REMOVED".equalsIgnoreCase(state)) return false;

            opConfig.set(path + ".state", "DELIVERY_ATTEMPTED");
            writeYamlAtomically(opConfig.saveToString(), pendingOpsFile);
            return true;
        } catch (IOException e) {
            logSevere("Failed to claim delivery attempt in YAML: " + operationId, e);
            return false;
        }
    }

    public synchronized boolean completeDelivery(String operationId) {
        if (databaseStorage != null) {
            return databaseStorage.completeDelivery(operationId);
        }
        try {
            if (!pendingOpsFile.exists()) return false;
            YamlConfiguration opConfig = YamlConfiguration.loadConfiguration(pendingOpsFile);
            String path = "operations." + operationId;
            if (!opConfig.contains(path)) return false;
            String state = opConfig.getString(path + ".state");
            if (!"DELIVERY_ATTEMPTED".equalsIgnoreCase(state)) return false;

            opConfig.set(path + ".state", "COMPLETED");
            writeYamlAtomically(opConfig.saveToString(), pendingOpsFile);
            return true;
        } catch (IOException e) {
            logSevere("Failed to complete delivery in YAML: " + operationId, e);
            return false;
        }
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

    public synchronized DatabaseStorage.PayoutReservationResult reservePayoutAtomic(String operationId, UUID minionId, UUID playerUuid, double amount, long revision) {
        if (operationId == null || minionId == null || playerUuid == null || !Double.isFinite(amount) || amount < 0.0001) {
            return DatabaseStorage.PayoutReservationResult.FAILED_CONFIRMED_ROLLBACK;
        }
        double normalizedAmount = BigDecimal.valueOf(amount).setScale(4, RoundingMode.HALF_UP).doubleValue();
        if (normalizedAmount < 0.0001) {
            return DatabaseStorage.PayoutReservationResult.FAILED_CONFIRMED_ROLLBACK;
        }

        if (databaseStorage != null) {
            // Delegate atomic dual-balance reservation to DatabaseStorage
            return databaseStorage.reservePayoutAtomic(operationId, minionId, playerUuid, normalizedAmount, revision);
        }

        try {
            if (!dataFile.exists()) return DatabaseStorage.PayoutReservationResult.FAILED_CONFIRMED_ROLLBACK;
            YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
            String path = "minions." + minionId.toString();
            if (!config.contains(path)) return DatabaseStorage.PayoutReservationResult.FAILED_CONFIRMED_ROLLBACK;

            YamlConfiguration opConfig = pendingOpsFile.exists() ?
                    YamlConfiguration.loadConfiguration(pendingOpsFile) : new YamlConfiguration();
            String opPath = "operations." + operationId;
            if (opConfig.contains(opPath)) {
                String storedMinion = opConfig.getString(opPath + ".minion_id");
                String storedPlayer = opConfig.getString(opPath + ".player_uuid");
                String storedPayload = opConfig.getString(opPath + ".payload");
                String storedState = opConfig.getString(opPath + ".state");

                boolean minionMatches = minionId.toString().equalsIgnoreCase(storedMinion);
                boolean playerMatches = storedPlayer != null && playerUuid.toString().equalsIgnoreCase(storedPlayer);
                boolean amountMatches = false;
                try {
                    if (storedPayload != null) {
                        double storedAmt = Double.parseDouble(storedPayload);
                        amountMatches = BigDecimal.valueOf(storedAmt).setScale(4, RoundingMode.HALF_UP)
                                .compareTo(BigDecimal.valueOf(normalizedAmount).setScale(4, RoundingMode.HALF_UP)) == 0;
                    }
                } catch (Exception ignored) {}

                if (!minionMatches || !playerMatches || !amountMatches) {
                    quarantineMinion(minionId);
                    return DatabaseStorage.PayoutReservationResult.UNKNOWN_OUTCOME;
                }

                return switch (storedState != null ? storedState.toUpperCase() : "") {
                    case "CONFIRMED" -> DatabaseStorage.PayoutReservationResult.ALREADY_CONFIRMED;
                    case "PAYOUT_FAILED" -> DatabaseStorage.PayoutReservationResult.FAILED_CONFIRMED_ROLLBACK;
                    case "ATTEMPTED" -> {
                        quarantineMinion(minionId);
                        yield DatabaseStorage.PayoutReservationResult.UNKNOWN_OUTCOME;
                    }
                    default -> {
                        quarantineMinion(minionId);
                        yield DatabaseStorage.PayoutReservationResult.UNKNOWN_OUTCOME;
                    }
                };
            }

            double currentCoins = config.getDouble(path + ".shipping-coins", 0.0);
            double normalizedCoins = BigDecimal.valueOf(currentCoins).setScale(4, RoundingMode.HALF_UP).doubleValue();
            long currentRev = config.getLong(path + ".state-revision", 0L);
            if (currentRev != revision) return DatabaseStorage.PayoutReservationResult.FAILED_CONFIRMED_ROLLBACK; // Optimistic revision guard
            if (normalizedCoins < normalizedAmount) return DatabaseStorage.PayoutReservationResult.FAILED_CONFIRMED_ROLLBACK;

            config.set(path + ".shipping-coins", BigDecimal.valueOf(normalizedCoins - normalizedAmount).setScale(4, RoundingMode.HALF_UP).doubleValue());
            config.set(path + ".state-revision", revision + 1);
            writeYamlAtomically(config.saveToString(), dataFile);

            opConfig.set(opPath + ".minion_id", minionId.toString());
            opConfig.set(opPath + ".player_uuid", playerUuid != null ? playerUuid.toString() : "");
            opConfig.set(opPath + ".state", "ATTEMPTED");
            opConfig.set(opPath + ".created_at", System.currentTimeMillis());
            opConfig.set(opPath + ".payload", String.valueOf(normalizedAmount));
            writeYamlAtomically(opConfig.saveToString(), pendingOpsFile);
            return DatabaseStorage.PayoutReservationResult.RESERVED;
        } catch (IOException e) {
            logSevere("Failed to reserve payout in YAML for " + minionId, e);
            quarantineMinion(minionId);
            return DatabaseStorage.PayoutReservationResult.UNKNOWN_OUTCOME;
        }
    }

    public synchronized boolean rollbackPayoutAtomic(String operationId, UUID minionId, double amount) {
        if (operationId == null || minionId == null || amount <= 0.0) return false;
        double normalizedAmount = BigDecimal.valueOf(amount).setScale(4, RoundingMode.HALF_UP).doubleValue();

        if (databaseStorage != null) {
            return databaseStorage.rollbackPayoutAtomic(operationId, minionId, normalizedAmount);
        }

        try {
            if (!pendingOpsFile.exists()) return false;
            YamlConfiguration opConfig = YamlConfiguration.loadConfiguration(pendingOpsFile);
            String opPath = "operations." + operationId;
            if (!opConfig.contains(opPath)) return false;

            String state = opConfig.getString(opPath + ".state");
            if (!"ATTEMPTED".equalsIgnoreCase(state)) {
                // Precondition failed: Either already PAYOUT_FAILED or not in ATTEMPTED state
                return false;
            }

            opConfig.set(opPath + ".state", "PAYOUT_FAILED");
            writeYamlAtomically(opConfig.saveToString(), pendingOpsFile);

            if (dataFile.exists()) {
                YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
                String path = "minions." + minionId.toString();
                if (config.contains(path)) {
                    double currentCoins = config.getDouble(path + ".shipping-coins", 0.0);
                    double restored = BigDecimal.valueOf(currentCoins).setScale(4, RoundingMode.HALF_UP).add(BigDecimal.valueOf(normalizedAmount)).doubleValue();
                    config.set(path + ".shipping-coins", restored);
                    long rev = config.getLong(path + ".state-revision", 0L);
                    config.set(path + ".state-revision", rev + 1);
                    writeYamlAtomically(config.saveToString(), dataFile);
                }
            }
            return true;
        } catch (IOException e) {
            logSevere("Failed to rollback payout in YAML for " + minionId, e);
            return false;
        }
    }

    public synchronized boolean confirmPayoutAtomic(String operationId) {
        return confirmPayoutAtomic(operationId, null);
    }

    public synchronized boolean confirmPayoutAtomic(String operationId, UUID minionId) {
        if (databaseStorage != null) {
            return databaseStorage.confirmPayoutAtomic(operationId, minionId);
        }

        try {
            if (!pendingOpsFile.exists()) return false;
            YamlConfiguration opConfig = YamlConfiguration.loadConfiguration(pendingOpsFile);
            String opPath = "operations." + operationId;
            if (!opConfig.contains(opPath)) return false;

            opConfig.set(opPath + ".state", "CONFIRMED");
            writeYamlAtomically(opConfig.saveToString(), pendingOpsFile);
            return true;
        } catch (IOException e) {
            logSevere("Failed to confirm payout in YAML: " + operationId, e);
            return false;
        }
    }

    public synchronized boolean ambiguousPayoutAtomic(String operationId) {
        if (databaseStorage != null) {
            return databaseStorage.ambiguousPayoutAtomic(operationId);
        }

        try {
            if (!pendingOpsFile.exists()) return false;
            YamlConfiguration opConfig = YamlConfiguration.loadConfiguration(pendingOpsFile);
            String opPath = "operations." + operationId;
            if (!opConfig.contains(opPath)) return false;

            opConfig.set(opPath + ".state", "PAYOUT_AMBIGUOUS");
            writeYamlAtomically(opConfig.saveToString(), pendingOpsFile);
            return true;
        } catch (IOException e) {
            logSevere("Failed to mark payout ambiguous in YAML: " + operationId, e);
            return false;
        }
    }

    public synchronized DatabaseStorage.TransactionOutcome probeCommitReconciliation(UUID minionId, String operationId) {
        return probeCommitReconciliation(minionId, operationId, DatabaseStorage.TransactionType.PICKUP_REMOVAL);
    }

    public synchronized DatabaseStorage.TransactionOutcome probeCommitReconciliation(UUID minionId, String operationId, DatabaseStorage.TransactionType type) {
        if (databaseStorage != null) {
            return databaseStorage.probeCommitReconciliation(minionId, operationId, type);
        }
        return DatabaseStorage.TransactionOutcome.CONFIRMED_ROLLBACK;
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
