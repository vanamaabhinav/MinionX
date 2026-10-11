package com.skyblock.minions.listener;

import com.skyblock.minions.quarantine.QuarantineManager;
import org.bukkit.block.TileState;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import com.skyblock.minions.SkyBlockMinions;
import com.skyblock.minions.api.MinionType;
import com.skyblock.minions.core.Minion;
import com.skyblock.minions.core.PlayerLimitManager;
import com.skyblock.minions.hook.ProtectionHookManager;
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
 * Enforces dynamic per-player placement limits, land claim security, and configurable messages.
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

        // Check Emergency Quarantine Mode
        if (QuarantineManager.isQuarantined() && !QuarantineManager.canBypass(player)) {
            player.sendMessage(ColorUtil.color("&c[Quarantine] Minion placement is disabled during emergency quarantine mode!"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }
        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) return;

        FileConfiguration config = SkyBlockMinions.getInstance().getConfig();

        // 1. Dynamic Per-Player Minion Limit Check
        PlayerLimitManager limitManager = SkyBlockMinions.getInstance().getPlayerLimitManager();
        if (limitManager != null && !limitManager.canPlaceMore(player)) {
            int limit = limitManager.getEffectiveLimit(player);
            String limitMsg = config.getString("messages.minion-limit-reached", "&cYou have reached your maximum minion limit of %limit%!")
                    .replace("%limit%", String.valueOf(limit));
            player.sendMessage(ColorUtil.color(limitMsg));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // Placement target is the block directly above clicked block
        Block placeBlock = clickedBlock.getRelative(BlockFace.UP);
        if (!placeBlock.getType().isAir()) {
            player.sendMessage(ColorUtil.color("&cCannot place minion here! The space above must be clear."));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // Prevent placing multiple minions in the same block/slot
        Location targetLoc = placeBlock.getLocation();
        if (SkyBlockMinions.getInstance().getMinionManager().getMinionAt(targetLoc) != null) {
            String occupiedMsg = config.getString("messages.minion-already-exists", "&cThere is already a minion here! Only one minion may occupy a block.");
            player.sendMessage(ColorUtil.color(occupiedMsg));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        if (targetLoc.getWorld() != null) {
            for (org.bukkit.entity.Entity nearby : targetLoc.getWorld().getNearbyEntities(targetLoc.clone().add(0.5, 0.5, 0.5), 0.6, 0.6, 0.6)) {
                if (nearby instanceof org.bukkit.entity.ArmorStand as) {
                    if (as.getPersistentDataContainer().has(MinionVisual.MINION_ENTITY_KEY, org.bukkit.persistence.PersistentDataType.STRING)) {
                        String occupiedMsg = config.getString("messages.minion-already-exists", "&cThere is already a minion here! Only one minion may occupy a block.");
                        player.sendMessage(ColorUtil.color(occupiedMsg));
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                        return;
                    }
                }
            }
        }

        // 2. Check Land & Claim Permissions (GriefPrevention, BentoBox, SuperiorSkyblock2, WorldGuard, Towny, Lands)
        if (!ProtectionHookManager.canPlaceMinion(player, placeBlock.getLocation())) {
            String noPermMsg = config.getString("messages.no-island-place-permission", "&cYou do not have permission to place minions here!");
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

        // Auto-link adjacent unlinked minion storage chest if present
        for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            Block adj = placeBlock.getRelative(face);
            if (adj.getState() instanceof TileState tile) {
                PersistentDataContainer pdc = tile.getPersistentDataContainer();
                if (pdc.has(MinionStorageListener.STORAGE_BLOCK_KEY, PersistentDataType.BYTE)) {
                    String linked = pdc.get(MinionStorageListener.LINKED_MINION_KEY, PersistentDataType.STRING);
                    if (linked == null || linked.isEmpty() || "unlinked".equalsIgnoreCase(linked)) {
                        String stId = pdc.get(MinionStorageListener.STORAGE_ID_KEY, PersistentDataType.STRING);
                        if (stId != null) {
                            minion.linkStorage(adj.getLocation(), stId);
                            pdc.set(MinionStorageListener.LINKED_MINION_KEY, PersistentDataType.STRING, minion.getMinionId().toString());
                            tile.update();
                            player.sendMessage(ColorUtil.color("&aAutomatically linked adjacent &eMinion Storage &ato this minion!"));
                            break;
                        }
                    }
                }
            }
        }


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
