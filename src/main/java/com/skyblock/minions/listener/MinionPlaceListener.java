package com.skyblock.minions.listener;

import com.skyblock.minions.SkyBlockMinions;
import com.skyblock.minions.api.MinionDefinition;
import com.skyblock.minions.api.MinionRegistry;
import com.skyblock.minions.api.MinionType;
import com.skyblock.minions.core.Minion;
import com.skyblock.minions.hook.IslandHookManager;
import com.skyblock.minions.item.MinionItemFactory;
import com.skyblock.minions.util.ColorUtil;
import com.skyblock.minions.visual.MinionHologram;
import com.skyblock.minions.visual.MinionVisual;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.FileConfiguration;
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
 * Enforces per-player placement limits, island build security, and configurable messages.
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

        FileConfiguration config = SkyBlockMinions.getInstance().getConfig();

        // 1. Check Per-Player Minion Limit
        int limit = config.getInt("settings.max-minions-per-player", 5);
        if (limit >= 0 && !player.hasPermission("minions.bypass.limit")) {
            int currentCount = SkyBlockMinions.getInstance().getMinionManager().getMinionsByOwner(player.getUniqueId()).size();
            if (currentCount >= limit) {
                String limitMsg = config.getString("messages.minion-limit-reached", "&cYou have reached your maximum minion limit of %limit%!")
                        .replace("%limit%", String.valueOf(limit));
                player.sendMessage(ColorUtil.color(limitMsg));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }
        }

        // Placement target is the block directly above clicked block
        Block placeBlock = clickedBlock.getRelative(BlockFace.UP);
        if (!placeBlock.getType().isAir()) {
            player.sendMessage(ColorUtil.color("&cCannot place minion here! The space above must be clear."));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // 2. Check Island & Region Permissions (BentoBox / SuperiorSkyblock2 / WorldGuard)
        if (!IslandHookManager.canPlaceMinion(player, placeBlock.getLocation())) {
            String noPermMsg = config.getString("messages.no-island-place-permission", "&cYou do not have permission to place minions on this island!");
            player.sendMessage(ColorUtil.color(noPermMsg));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        String customId = MinionItemFactory.getCustomMinionId(item);
        MinionType type = MinionItemFactory.getMinionType(item);
        int tier = MinionItemFactory.getMinionTier(item);
        int harvested = MinionItemFactory.getMinionHarvested(item);

        if (type == null && customId == null) {
            player.sendMessage(ColorUtil.color("&cInvalid minion item!"));
            return;
        }

        if (type == null) {
            type = MinionType.COBBLESTONE;
        }

        Location minionLoc = placeBlock.getLocation();
        minionLoc.setYaw(player.getLocation().getYaw() + 180f); // Face player

        Minion minion = new Minion(UUID.randomUUID(), player.getUniqueId(), type, tier, minionLoc);
        if (customId != null) {
            minion.setCustomDefinitionId(customId);
        }
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

        String name = minion.getDisplayName();
        String placedMsg = config.getString("messages.placed", "&aYou placed a %minion%!")
                .replace("%minion%", name + " Minion");
        player.sendMessage(ColorUtil.color(config.getString("messages.prefix", "&6[Minions] &r") + placedMsg));
        player.playSound(minionLoc, Sound.BLOCK_ANVIL_USE, 0.7f, 1.2f);
        player.playSound(minionLoc, Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.8f);
    }
}
