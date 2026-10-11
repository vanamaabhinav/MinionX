package com.skyblock.minions.core;

import com.skyblock.minions.visual.MinionHologram;
import com.skyblock.minions.visual.MinionVisual;
import org.bukkit.Chunk;
import org.bukkit.Location;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory manager and registry for all active minions on the server.
 */
public class MinionManager {

    private final Map<UUID, Minion> minionsById = new ConcurrentHashMap<>();
    private final Map<UUID, Minion> minionsByEntityId = new ConcurrentHashMap<>();
    private final Map<Long, List<Minion>> minionsByChunk = new ConcurrentHashMap<>();

    public void register(Minion minion) {
        if (minion == null) return;
        minionsById.put(minion.getMinionId(), minion);

        if (minion.getArmorStandId() != null) {
            minionsByEntityId.put(minion.getArmorStandId(), minion);
        }

        if (minion.getLocation() != null && minion.getLocation().getWorld() != null) {
            long chunkKey = getChunkKey(minion.getLocation().getBlockX() >> 4, minion.getLocation().getBlockZ() >> 4);
            minionsByChunk.computeIfAbsent(chunkKey, k -> new ArrayList<>()).add(minion);
        }
    }

    public void unregister(Minion minion) {
        if (minion == null) return;
        minionsById.remove(minion.getMinionId());

        if (minion.getArmorStandId() != null) {
            minionsByEntityId.remove(minion.getArmorStandId());
        }

        if (minion.getLocation() != null && minion.getLocation().getWorld() != null) {
            long chunkKey = getChunkKey(minion.getLocation().getBlockX() >> 4, minion.getLocation().getBlockZ() >> 4);
            List<Minion> inChunk = minionsByChunk.get(chunkKey);
            if (inChunk != null) {
                inChunk.remove(minion);
                if (inChunk.isEmpty()) {
                    minionsByChunk.remove(chunkKey);
                }
            }
        }

        // Clean up entities
        MinionVisual.remove(minion);
        MinionHologram.remove(minion);
    }

    public Minion getMinionById(UUID id) {
        if (id == null) return null;
        return minionsById.get(id);
    }

    public Minion getMinionByEntityId(UUID entityId) {
        if (entityId == null) return null;
        return minionsByEntityId.get(entityId);
    }

    public Minion getMinionAt(Location loc) {
        if (loc == null) return null;
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
        long chunkKey = getChunkKey(chunk.getX(), chunk.getZ());
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

    private long getChunkKey(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }
}
