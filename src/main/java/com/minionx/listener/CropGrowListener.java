package com.minionx.listener;

import com.minionx.MinionX;
import com.minionx.core.MinionManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockSpreadEvent;

/**
 * Prevents pumpkin or melon stems from naturally growing/spreading fruit blocks
 * onto a minion's standing location or linked storage blocks.
 */
public class CropGrowListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockGrow(BlockGrowEvent event) {
        if (isProtectedMinionBlock(event.getBlock(), event.getNewState().getType())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockSpread(BlockSpreadEvent event) {
        if (isProtectedMinionBlock(event.getBlock(), event.getNewState().getType())) {
            event.setCancelled(true);
        }
    }

    private boolean isProtectedMinionBlock(Block block, Material newType) {
        if (block == null || block.getWorld() == null) return false;

        // Check if the growing/spreading block is pumpkin or melon fruit
        if (newType == Material.PUMPKIN || newType == Material.MELON || newType == Material.CARVED_PUMPKIN
                || newType == Material.ATTACHED_PUMPKIN_STEM || newType == Material.ATTACHED_MELON_STEM) {
            MinionManager manager = MinionX.getInstance() != null ? MinionX.getInstance().getMinionManager() : null;
            if (manager == null) return false;

            Location loc = block.getLocation();

            // 1. Minion center standing feet block
            if (manager.getMinionAt(loc) != null) {
                return true;
            }

            // 2. Minion torso / head block (one block above standing point)
            Location below = loc.clone().subtract(0, 1, 0);
            if (manager.getMinionAt(below) != null) {
                return true;
            }

            // 3. Minion linked storage chest block
            if (manager.isStorageBlock(loc)) {
                return true;
            }
        }
        return false;
    }
}
