package com.skyblock.minions.listener;

import com.skyblock.minions.SkyBlockMinions;
import com.skyblock.minions.core.Minion;
import com.skyblock.minions.core.OfflineCalculator;
import com.skyblock.minions.visual.MinionHologram;
import com.skyblock.minions.visual.MinionVisual;
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
        List<Minion> minions = SkyBlockMinions.getInstance().getMinionManager().getMinionsInChunk(chunk);

        for (Minion minion : minions) {
            // Process offline accumulation
            OfflineCalculator.processOfflineCatchup(minion);

            // Re-spawn visual entities
            MinionVisual.spawnOrUpdate(minion);
            MinionHologram.updateHologram(minion);
        }
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        Chunk chunk = event.getChunk();
        List<Minion> minions = SkyBlockMinions.getInstance().getMinionManager().getMinionsInChunk(chunk);

        for (Minion minion : minions) {
            MinionVisual.remove(minion);
            MinionHologram.remove(minion);
        }
    }
}
