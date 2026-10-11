package com.skyblock.minions.listener;

import com.skyblock.minions.SkyBlockMinions;
import com.skyblock.minions.api.MinionType;
import com.skyblock.minions.core.Minion;
import com.skyblock.minions.item.MinionItemFactory;
import com.skyblock.minions.visual.MinionHologram;
import com.skyblock.minions.visual.MinionVisual;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Handles placing a Minion item onto the ground in the world.
 */
public class MinionPlaceListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        ItemStack item = event.getItem();
        if (!MinionItemFactory.isMinionItem(item)) return;

        // Cancel vanilla block placement of player head
        event.setCancelled(true);

        Player player = event.getPlayer();
        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) return;

        // Placement target is the block directly above clicked block
        Block placeBlock = clickedBlock.getRelative(BlockFace.UP);
        if (!placeBlock.getType().isAir()) {
            player.sendMessage("§cCannot place minion here! The space above must be clear.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // Validate 5x5 footprint clearance
        if (!isAreaSuitable(placeBlock)) {
            player.sendMessage("§e[Minions] §cWarning: Working area around minion is obstructed or has missing floor!");
        }

        MinionType type = MinionItemFactory.getMinionType(item);
        int tier = MinionItemFactory.getMinionTier(item);
        int harvested = MinionItemFactory.getMinionHarvested(item);

        if (type == null) {
            player.sendMessage("§cInvalid minion item!");
            return;
        }

        Location minionLoc = placeBlock.getLocation();
        minionLoc.setYaw(player.getLocation().getYaw() + 180f); // Face player

        Minion minion = new Minion(UUID.randomUUID(), player.getUniqueId(), type, tier, minionLoc);
        minion.setTotalHarvested(harvested);

        // Register minion
        SkyBlockMinions.getInstance().getMinionManager().register(minion);

        // Spawn visual representations
        MinionVisual.spawnOrUpdate(minion);
        MinionHologram.updateHologram(minion);

        // Consume 1 item from hand
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        }

        player.sendMessage("§6[Minions] §aYou placed a " + type.getDisplayName() + " Minion!");
        player.playSound(minionLoc, Sound.BLOCK_ANVIL_USE, 0.7f, 1.2f);
        player.playSound(minionLoc, Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.8f);
    }

    private boolean isAreaSuitable(Block center) {
        // Simple check: floor under center should be solid
        Block floor = center.getRelative(BlockFace.DOWN);
        return floor.getType().isSolid();
    }
}
