package com.minionx.listener;

import com.minionx.MinionX;
import com.minionx.core.Minion;
import com.minionx.core.OfflineCoordinator;
import com.minionx.visual.MinionHologram;
import com.minionx.visual.MinionVisual;
import org.bukkit.Chunk;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

import java.util.List;

/**
 * Ensures clean visual entity restoration on chunk load and cleanup on chunk unload.
 */
public class ChunkLifecycleListener implements Listener {

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();

        // Process any pending storage purges for this newly loaded chunk
        MinionX.getInstance().getMinionManager().processPendingStoragePurges(chunk);

        // Mark minions in chunk as actively loaded for high-performance tick loop
        MinionX.getInstance().getMinionManager().markChunkLoaded(chunk);

        List<Minion> minions = MinionX.getInstance().getMinionManager().getMinionsInChunk(chunk);

        for (Minion minion : minions) {
            // Process offline accumulation
            OfflineCoordinator.catchUpIfNeeded(minion);

            // Re-spawn visual entities
            MinionVisual.spawnOrUpdate(minion);
            MinionHologram.updateHologram(minion);
        }
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        Chunk chunk = event.getChunk();

        // Mark minions in chunk as unloaded
        MinionX.getInstance().getMinionManager().markChunkUnloaded(chunk);

        List<Minion> minions = MinionX.getInstance().getMinionManager().getMinionsInChunk(chunk);

        for (Minion minion : minions) {
            MinionVisual.remove(minion);
            MinionHologram.remove(minion);
        }
    }
}
