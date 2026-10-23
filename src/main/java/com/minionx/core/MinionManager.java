package com.minionx.core;

import com.minionx.visual.MinionHologram;
import com.minionx.visual.MinionVisual;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory manager and registry for all active minions on the server.
 * Uses high-performance ConcurrentHashMaps with O(1) spatial indexing for chunk lookups,
 * minion locations, and linked storage chests to prevent O(N^2) tick bottlenecks.
 */
public class MinionManager {

    /**
     * Unique multi-world chunk coordinate identifier preventing cross-world key collisions.
     */
    public record ChunkKey(UUID worldId, int x, int z) {}

    private final Map<UUID, Minion> minionsById = new ConcurrentHashMap<>();
    private final Map<UUID, Minion> minionsByEntityId = new ConcurrentHashMap<>();
    private final Map<ChunkKey, List<Minion>> minionsByChunk = new ConcurrentHashMap<>();
    private final Map<String, UUID> minionLocationIndex = new ConcurrentHashMap<>();
    private final Map<String, UUID> storageBlockIndex = new ConcurrentHashMap<>();
    private final Set<Minion> activeLoadedMinions = ConcurrentHashMap.newKeySet();

    public void register(Minion minion) {
        if (minion == null) return;
        minionsById.put(minion.getMinionId(), minion);

        if (minion.getArmorStandId() != null) {
            minionsByEntityId.put(minion.getArmorStandId(), minion);
        }

        if (minion.getLocation() != null && minion.getLocation().getWorld() != null) {
            minionLocationIndex.put(toBlockKey(minion.getLocation()), minion.getMinionId());
            ChunkKey chunkKey = getChunkKey(minion.getLocation());
            minionsByChunk.computeIfAbsent(chunkKey, k -> new CopyOnWriteArrayList<>()).add(minion);
            if (minion.getLocation().isChunkLoaded()) {
                activeLoadedMinions.add(minion);
            }
        } else {
            activeLoadedMinions.add(minion);
        }

        if (minion.hasLinkedStorage() && minion.getLinkedStorageLocation() != null) {
            registerStorageBlock(minion.getLinkedStorageLocation(), minion.getMinionId());
        }
    }

    public void unregister(Minion minion) {
        if (minion == null) return;
        minion.advanceSchedulingGeneration();
        minionsById.remove(minion.getMinionId());
        activeLoadedMinions.remove(minion);

        if (minion.getArmorStandId() != null) {
            minionsByEntityId.remove(minion.getArmorStandId());
        }

        if (minion.getLocation() != null && minion.getLocation().getWorld() != null) {
            minionLocationIndex.remove(toBlockKey(minion.getLocation()));
            ChunkKey chunkKey = getChunkKey(minion.getLocation());
            List<Minion> inChunk = minionsByChunk.get(chunkKey);
            if (inChunk != null) {
                inChunk.remove(minion);
                if (inChunk.isEmpty()) {
                    minionsByChunk.remove(chunkKey);
                }
            }
        }

        if (minion.hasLinkedStorage() && minion.getLinkedStorageLocation() != null) {
            unregisterStorageBlock(minion.getLinkedStorageLocation());
        }

        // Clean up entities
        MinionVisual.remove(minion);
        MinionHologram.remove(minion);

        // Release tracked spawned combat / animal husbandry mobs without killing them
        minion.getTrackedSpawnedMobs().clear();
    }

    public Minion getMinionById(UUID id) {
        if (id == null) return null;
        return minionsById.get(id);
    }

    public Minion getMinionByEntityId(UUID entityId) {
        if (entityId == null) return null;
        return minionsByEntityId.get(entityId);
    }

    /**
     * O(1) spatial lookup of a minion at a given location.
     */
    public Minion getMinionAt(Location loc) {
        if (loc == null) return null;
        UUID id = minionLocationIndex.get(toBlockKey(loc));
        if (id != null) {
            return minionsById.get(id);
        }
        // Fallback scan in case location index was not populated
        for (Minion minion : minionsById.values()) {
            Location mLoc = minion.getLocation();
            if (mLoc == null) continue;
            if (mLoc.getWorld() != null && loc.getWorld() != null) {
                if (!mLoc.getWorld().equals(loc.getWorld())) continue;
            } else if (mLoc.getWorld() != loc.getWorld()) {
                continue;
            }
            if (mLoc.getBlockX() == loc.getBlockX() &&
                    mLoc.getBlockY() == loc.getBlockY() &&
                    mLoc.getBlockZ() == loc.getBlockZ()) {
                return minion;
            }
        }
        return null;
    }

    public List<Minion> getMinionsInChunk(Chunk chunk) {
        if (chunk == null) return Collections.emptyList();
        ChunkKey chunkKey = getChunkKey(chunk);
        return minionsByChunk.getOrDefault(chunkKey, Collections.emptyList());
    }

    public List<Minion> getMinionsByOwner(UUID ownerId) {
        List<Minion> list = new ArrayList<>();
        if (ownerId == null) return list;
        for (Minion minion : minionsById.values()) {
            if (ownerId.equals(minion.getOwnerId())) {
                list.add(minion);
            }
        }
        return list;
    }

    public Collection<Minion> getAllMinions() {
        return minionsById.values();
    }

    public Collection<Minion> getActiveLoadedMinions() {
        return Collections.unmodifiableSet(activeLoadedMinions);
    }

    public void markChunkLoaded(Chunk chunk) {
        if (chunk == null || chunk.getWorld() == null) return;
        ChunkKey key = new ChunkKey(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ());
        List<Minion> inChunk = minionsByChunk.get(key);
        if (inChunk != null) {
            activeLoadedMinions.addAll(inChunk);
        }
    }

    public void markChunkUnloaded(Chunk chunk) {
        if (chunk == null || chunk.getWorld() == null) return;
        ChunkKey key = new ChunkKey(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ());
        List<Minion> inChunk = minionsByChunk.get(key);
        if (inChunk != null) {
            for (Minion m : inChunk) {
                activeLoadedMinions.remove(m);
                m.advanceSchedulingGeneration();
            }
        }
    }

    public void markLoaded(Minion minion) {
        if (minion != null) activeLoadedMinions.add(minion);
    }

    public void markUnloaded(Minion minion) {
        if (minion != null) {
            activeLoadedMinions.remove(minion);
            minion.advanceSchedulingGeneration();
        }
    }

    public void linkEntity(UUID entityId, Minion minion) {
        if (entityId != null && minion != null) {
            minionsByEntityId.put(entityId, minion);
        }
    }

    public void removeAllVisuals() {
        for (Minion minion : minionsById.values()) {
            MinionVisual.remove(minion);
            MinionHologram.remove(minion);
        }
    }

    private final Map<ChunkKey, Set<String>> pendingStoragePurgesByChunk = new ConcurrentHashMap<>();

    // --- Spatial Storage Indexing (O(1) Lookups) ---

    public void registerStorageBlock(Location loc, UUID minionId) {
        if (loc == null || minionId == null) return;
        storageBlockIndex.put(toBlockKey(loc), minionId);
    }

    public void unregisterStorageBlock(Location loc) {
        if (loc == null) return;
        storageBlockIndex.remove(toBlockKey(loc));
    }

    public UUID getStorageMinionAt(Location loc) {
        if (loc == null) return null;
        return storageBlockIndex.get(toBlockKey(loc));
    }

    public boolean isStorageBlock(Location loc) {
        if (loc == null) return false;
        return storageBlockIndex.containsKey(toBlockKey(loc));
    }

    public void markStorageForPendingPurge(Location loc) {
        if (loc == null || loc.getWorld() == null) return;
        ChunkKey key = getChunkKey(loc);
        pendingStoragePurgesByChunk.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet())
                .add(toLocationString(loc));
    }

    public boolean hasPendingStoragePurges(Chunk chunk) {
        if (chunk == null) return false;
        Set<String> set = pendingStoragePurgesByChunk.get(getChunkKey(chunk));
        return set != null && !set.isEmpty();
    }

    public void processPendingStoragePurges(Chunk chunk) {
        if (chunk == null) return;
        ChunkKey key = getChunkKey(chunk);
        Set<String> locations = pendingStoragePurgesByChunk.remove(key);
        if (locations == null || locations.isEmpty()) return;

        org.bukkit.World world = chunk.getWorld();
        for (String locStr : locations) {
            try {
                String[] parts = locStr.split(":");
                if (parts.length >= 4) {
                    int x = Integer.parseInt(parts[1]);
                    int y = Integer.parseInt(parts[2]);
                    int z = Integer.parseInt(parts[3]);
                    Block b = world.getBlockAt(x, y, z);
                    if (b.getType() != Material.AIR) {
                        b.setType(Material.AIR);
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    public List<String> getPendingStoragePurgeLocations() {
        List<String> list = new ArrayList<>();
        for (Set<String> set : pendingStoragePurgesByChunk.values()) {
            list.addAll(set);
        }
        return list;
    }

    public void loadPendingStoragePurges(List<String> serializedCoords) {
        pendingStoragePurgesByChunk.clear();
        if (serializedCoords == null) return;
        for (String s : serializedCoords) {
            try {
                String[] parts = s.split(":");
                if (parts.length >= 4) {
                    UUID wid = UUID.fromString(parts[0]);
                    int x = Integer.parseInt(parts[1]);
                    int z = Integer.parseInt(parts[3]);
                    ChunkKey key = new ChunkKey(wid, x >> 4, z >> 4);
                    pendingStoragePurgesByChunk.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(s);
                }
            } catch (Exception ignored) {}
        }
    }

    private String toLocationString(Location loc) {
        if (loc == null || loc.getWorld() == null) return "";
        return loc.getWorld().getUID().toString() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }

    private String toBlockKey(Location loc) {
        if (loc == null) return "";
        UUID wid = loc.getWorld() != null ? loc.getWorld().getUID() : new UUID(0, 0);
        return wid + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }

    public ChunkKey getChunkKey(Location loc) {
        UUID wid = (loc != null && loc.getWorld() != null) ? loc.getWorld().getUID() : new UUID(0, 0);
        int x = loc != null ? (loc.getBlockX() >> 4) : 0;
        int z = loc != null ? (loc.getBlockZ() >> 4) : 0;
        return new ChunkKey(wid, x, z);
    }

    public ChunkKey getChunkKey(Chunk chunk) {
        UUID wid = (chunk != null && chunk.getWorld() != null) ? chunk.getWorld().getUID() : new UUID(0, 0);
        int x = chunk != null ? chunk.getX() : 0;
        int z = chunk != null ? chunk.getZ() : 0;
        return new ChunkKey(wid, x, z);
    }

    /**
     * Completely despawns, unregisters, and purges all active minions, holograms,
     * and ticking tasks across all loaded worlds and saves state to storage.
     *
     * @return count of minions purged
     */
    public int killAndPurgeAll() {
        int count = minionsById.size();

        // 1. Cleanly handle all linked minion storage chests before unregistering
        for (Minion minion : minionsById.values()) {
            if (minion.hasLinkedStorage() && minion.getLinkedStorageLocation() != null) {
                Location sLoc = minion.getLinkedStorageLocation();
                if (sLoc.getWorld() != null) {
                    if (sLoc.isChunkLoaded()) {
                        // Loaded chunk: instant O(1) removal without dropping items (prevents entity lag)
                        Block b = sLoc.getBlock();
                        if (b.getType() != Material.AIR) {
                            b.setType(Material.AIR);
                        }
                    } else {
                        // Unloaded chunk: queue for lazy cleanup on ChunkLoadEvent (prevents disk IO freeze)
                        markStorageForPendingPurge(sLoc);
                    }
                }
                minion.getStorageContents().clear();
                minion.unlinkStorage();
            }
        }

        // 2. Unregister all minions from memory and remove tracked visuals
        List<Minion> all = new ArrayList<>(minionsById.values());
        for (Minion minion : all) {
            unregister(minion);
        }
        minionsById.clear();
        minionsByEntityId.clear();
        minionsByChunk.clear();
        minionLocationIndex.clear();
        storageBlockIndex.clear();

        // 3. Persist empty state to storage so they don't reload on restart
        if (com.minionx.MinionX.getInstance() != null &&
                com.minionx.MinionX.getInstance().getMinionStorage() != null) {
            com.minionx.MinionX.getInstance().getMinionStorage().saveAll(this);
        }

        // 4. World sweep: aggressively search every loaded world for any entity with MINION_ENTITY_KEY, HOLOGRAM_KEY,
        // or metadata and remove them so ZERO orphan armor stands or holograms remain.
        try {
            for (org.bukkit.World world : org.bukkit.Bukkit.getWorlds()) {
                if (world == null) continue;
                for (org.bukkit.entity.Entity entity : world.getEntities()) {
                    if (entity == null) continue;
                    org.bukkit.persistence.PersistentDataContainer pdc = entity.getPersistentDataContainer();
                    if (pdc.has(MinionVisual.MINION_ENTITY_KEY, org.bukkit.persistence.PersistentDataType.STRING) ||
                            pdc.has(MinionHologram.HOLOGRAM_KEY, org.bukkit.persistence.PersistentDataType.STRING) ||
                            entity.hasMetadata("minion_hologram") ||
                            entity.hasMetadata("minionx_minion")) {
                        entity.remove();
                    }
                }
            }
        } catch (Exception ignored) {
        }

        return count;
    }
}
