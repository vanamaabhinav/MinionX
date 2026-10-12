package com.minionx.listener;

import com.minionx.MinionX;
import com.minionx.api.CustomMinionStorage;
import com.minionx.api.MinionStorageRegistry;
import com.minionx.core.Minion;
import com.minionx.core.MinionManager;
import com.minionx.gui.MinionStorageGUI;
import com.minionx.gui.MinionStorageHolder;
import com.minionx.gui.SymmetricalSlotCalculator;
import com.minionx.item.MinionItemFactory;
import com.minionx.quarantine.QuarantineManager;
import com.minionx.util.ColorUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Handles placing, opening, breaking, and protecting Minion Storage chests.
 * Enforces adjacency checks, multi-minion resolution, automatic cleanup when minion is removed,
 * custom inventory interaction, and anti-vanilla-chest protections.
 */
public class MinionStorageListener implements Listener {

    public static final NamespacedKey STORAGE_BLOCK_KEY = new NamespacedKey("minionx", "is_minion_storage");
    public static final NamespacedKey LINKED_MINION_KEY = new NamespacedKey("minionx", "linked_minion_id");
    public static final NamespacedKey STORAGE_ID_KEY = new NamespacedKey("minionx", "storage_def_id");

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        String storageId = MinionItemFactory.getStorageId(item);
        if (storageId == null) return;

        Player player = event.getPlayer();
        Block block = event.getBlockPlaced();

        // 1. Quarantine Mode Check
        if (QuarantineManager.isQuarantined() && !QuarantineManager.canBypass(player)) {
            event.setCancelled(true);
            player.sendMessage(ColorUtil.color("&c[Quarantine] Minion placement and storage deployment are disabled during emergency quarantine mode!"));
            return;
        }

        CustomMinionStorage storageDef = MinionStorageRegistry.get(storageId);
        if (storageDef == null) {
            event.setCancelled(true);
            player.sendMessage(ColorUtil.color("&cInvalid minion storage definition!"));
            return;
        }

        MinionManager manager = MinionX.getInstance().getMinionManager();
        List<Minion> adjacentMinions = findAdjacentMinions(manager, block.getLocation());

        // Storage MUST be placed adjacent to a minion!
        if (adjacentMinions.isEmpty()) {
            event.setCancelled(true);
            player.sendMessage(ColorUtil.color("&cMinion Storages must be placed adjacent to a minion!"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // Filter to minions without existing storage
        List<Minion> unlinked = new ArrayList<>();
        for (Minion m : adjacentMinions) {
            if (!m.hasLinkedStorage()) {
                unlinked.add(m);
            }
        }

        if (unlinked.isEmpty()) {
            event.setCancelled(true);
            player.sendMessage(ColorUtil.color("&cAll adjacent minions already have a storage chest linked!"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // Resolve multi-minion placement: pick the one the player is facing / closest to
        Minion targetMinion = unlinked.getFirst();
        if (unlinked.size() > 1) {
            double bestAngle = Double.MAX_VALUE;
            Location eye = player.getEyeLocation();
            org.bukkit.util.Vector dir = eye.getDirection().normalize();

            for (Minion m : unlinked) {
                if (m.getLocation() == null) continue;
                org.bukkit.util.Vector toMinion = m.getLocation().toVector().subtract(eye.toVector()).normalize();
                double angle = dir.angle(toMinion);
                if (angle < bestAngle) {
                    bestAngle = angle;
                    targetMinion = m;
                }
            }
        }

        // Fire MinionStorageLinkEvent
        com.minionx.api.event.MinionStorageLinkEvent linkEvent = new com.minionx.api.event.MinionStorageLinkEvent(player, targetMinion, block.getLocation(), true);
        org.bukkit.Bukkit.getPluginManager().callEvent(linkEvent);
        if (linkEvent.isCancelled()) {
            return;
        }

        // Link storage to minion!
        targetMinion.linkStorage(block.getLocation(), storageId);
        if (com.minionx.MinionX.getInstance() != null && com.minionx.MinionX.getInstance().getMinionManager() != null) {
            com.minionx.MinionX.getInstance().getMinionManager().registerStorageBlock(block.getLocation(), targetMinion.getMinionId());
        }

        // Mark block with PDC
        if (block.getState() instanceof TileState tile) {
            PersistentDataContainer pdc = tile.getPersistentDataContainer();
            pdc.set(STORAGE_BLOCK_KEY, PersistentDataType.BYTE, (byte) 1);
            pdc.set(LINKED_MINION_KEY, PersistentDataType.STRING, targetMinion.getMinionId().toString());
            pdc.set(STORAGE_ID_KEY, PersistentDataType.STRING, storageId);
            tile.update();
        }

        if (unlinked.size() > 1) {
            player.sendMessage(ColorUtil.getPrefix() + ColorUtil.color("&aPlaced between multiple minions! Linked storage to adjacent &e" + targetMinion.getDisplayName() + " Minion &a(facing direction)."));
        } else {
            player.sendMessage(ColorUtil.getPrefix() + ColorUtil.color("&aLinked storage to adjacent &e" + targetMinion.getDisplayName() + " Minion&a!"));
        }
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_LOCKED, 1.0f, 1.2f);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Block block = event.getClickedBlock();
        if (block == null) return;

        Minion minion = getLinkedMinionAt(block);
        if (minion == null) return;

        // Prevent opening as regular vanilla chest!
        event.setCancelled(true);
        Player player = event.getPlayer();

        // Quarantine check
        if (QuarantineManager.isQuarantined() && !QuarantineManager.canBypass(player)) {
            player.sendMessage(ColorUtil.color("&c[Quarantine] Minion storage access is frozen during emergency quarantine mode!"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // Open custom MinionStorageGUI
        MinionStorageGUI.open(player, minion);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.8f, 1.0f);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Minion minion = getLinkedMinionAt(block);
        if (minion == null) return;

        Player player = event.getPlayer();

        // Quarantine check
        if (QuarantineManager.isQuarantined() && !QuarantineManager.canBypass(player)) {
            event.setCancelled(true);
            player.sendMessage(ColorUtil.color("&c[Quarantine] Minion storage breaking is disabled during emergency quarantine mode!"));
            return;
        }

        event.setDropItems(false);

        // Drop stored contents
        for (ItemStack item : minion.getStorageContents()) {
            if (item != null && item.getType() != Material.AIR) {
                block.getWorld().dropItemNaturally(block.getLocation(), item);
            }
        }
        minion.getStorageContents().clear();

        // Drop the storage item itself
        CustomMinionStorage st = minion.getLinkedStorage();
        if (st != null) {
            block.getWorld().dropItemNaturally(block.getLocation(), MinionItemFactory.createStorageItem(st));
        }

        if (com.minionx.MinionX.getInstance() != null && com.minionx.MinionX.getInstance().getMinionManager() != null && minion.getLinkedStorageLocation() != null) {
            com.minionx.MinionX.getInstance().getMinionManager().unregisterStorageBlock(minion.getLinkedStorageLocation());
        }
        minion.unlinkStorage();
        player.sendMessage(ColorUtil.getPrefix() + ColorUtil.color("&eRemoved Minion Storage chest and unlinked from " + minion.getDisplayName() + " Minion."));
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof MinionStorageHolder holder)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();

        // Quarantine check
        if (QuarantineManager.isQuarantined() && !QuarantineManager.canBypass(player)) {
            event.setCancelled(true);
            player.sendMessage(ColorUtil.color("&c[Quarantine] Minion item collection is currently frozen by server administrators!"));
            return;
        }

        int rawSlot = event.getRawSlot();
        Minion minion = holder.getMinion();
        CustomMinionStorage st = minion.getLinkedStorage();
        int capacity = st != null ? st.getCapacity() : 9;
        List<Integer> unlocked = SymmetricalSlotCalculator.getSlots(capacity);

        // Click inside top chest inventory
        if (rawSlot >= 0 && rawSlot < 27) {
            if (!unlocked.contains(rawSlot)) {
                // Clicked on a locked pane slot!
                event.setCancelled(true);
                return;
            }
        } else if (event.isShiftClick()) {
            // Shift-clicking from player inventory into storage
            // Check if there is space in unlocked slots
            int emptyUnlocked = 0;
            for (int s : unlocked) {
                ItemStack item = event.getInventory().getItem(s);
                if (item == null || item.getType() == Material.AIR) {
                    emptyUnlocked++;
                }
            }
            if (emptyUnlocked == 0) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof MinionStorageHolder holder)) {
            return;
        }

        Minion minion = holder.getMinion();
        CustomMinionStorage st = minion.getLinkedStorage();
        int capacity = st != null ? st.getCapacity() : 9;
        List<Integer> unlocked = SymmetricalSlotCalculator.getSlots(capacity);

        // Synchronize items from unlocked slots back into minion.getStorageContents()
        minion.getStorageContents().clear();
        for (int slot : unlocked) {
            ItemStack item = event.getInventory().getItem(slot);
            if (item != null && item.getType() != Material.AIR) {
                minion.getStorageContents().add(item.clone());
            }
        }

        if (event.getPlayer() instanceof Player p && minion.getLocation() != null) {
            p.playSound(minion.getLocation(), Sound.BLOCK_CHEST_CLOSE, 0.8f, 1.0f);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExplosion(BlockExplodeEvent event) {
        event.blockList().removeIf(b -> getLinkedMinionAt(b) != null);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplosion(EntityExplodeEvent event) {
        event.blockList().removeIf(b -> getLinkedMinionAt(b) != null);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHopperMove(InventoryMoveItemEvent event) {
        // Prevent hoppers from interacting with minion storages
        if (event.getSource().getHolder() instanceof MinionStorageHolder || event.getDestination().getHolder() instanceof MinionStorageHolder) {
            event.setCancelled(true);
        }
    }

    public static List<Minion> findAdjacentMinions(MinionManager manager, Location chestLoc) {
        List<Minion> list = new ArrayList<>();
        if (manager == null || chestLoc == null || chestLoc.getWorld() == null) return list;

        int cx = chestLoc.getBlockX();
        int cy = chestLoc.getBlockY();
        int cz = chestLoc.getBlockZ();

        for (Minion m : manager.getAllMinions()) {
            Location ml = m.getLocation();
            if (ml == null || ml.getWorld() == null) continue;
            if (!ml.getWorld().equals(chestLoc.getWorld())) continue;

            int mx = ml.getBlockX();
            int my = ml.getBlockY();
            int mz = ml.getBlockZ();

            // Adjacent horizontally (N, S, E, W) and vertical tolerance (-1, 0, +1)
            int dx = Math.abs(cx - mx);
            int dy = Math.abs(cy - my);
            int dz = Math.abs(cz - mz);

            if (dy <= 1 && ((dx == 1 && dz == 0) || (dx == 0 && dz == 1))) {
                list.add(m);
            }
        }
        return list;
    }

    public static Minion getLinkedMinionAt(Block block) {
        if (block == null) return null;
        MinionManager manager = MinionX.getInstance() != null ? MinionX.getInstance().getMinionManager() : null;
        if (manager == null) return null;

        UUID minionId = manager.getStorageMinionAt(block.getLocation());
        if (minionId != null) {
            return manager.getMinionById(minionId);
        }

        // Secondary fallback to TileState PDC
        if (block.getState() instanceof TileState tile) {
            PersistentDataContainer pdc = tile.getPersistentDataContainer();
            String mId = pdc.get(LINKED_MINION_KEY, PersistentDataType.STRING);
            if (mId != null) {
                try {
                    UUID uuid = UUID.fromString(mId);
                    Minion found = manager.getMinionById(uuid);
                    if (found != null) {
                        manager.registerStorageBlock(block.getLocation(), uuid);
                        return found;
                    }
                } catch (Exception ignored) {}
            }
        }
        return null;
    }

    /**
     * Completely removes and unlinks the storage chest block when a minion is removed or picked up.
     */
    public static void removeStorageOnMinionRemoval(Minion minion) {
        if (minion == null || !minion.hasLinkedStorage()) return;
        Location sl = minion.getLinkedStorageLocation();
        if (sl == null || sl.getWorld() == null) return;

        Block block = sl.getBlock();
        if (block.getType() != Material.AIR) {
            // Drop stored items
            for (ItemStack item : minion.getStorageContents()) {
                if (item != null && item.getType() != Material.AIR) {
                    sl.getWorld().dropItemNaturally(sl, item);
                }
            }
            minion.getStorageContents().clear();

            // Drop storage item
            CustomMinionStorage st = minion.getLinkedStorage();
            if (st != null) {
                sl.getWorld().dropItemNaturally(sl, MinionItemFactory.createStorageItem(st));
            }

            block.setType(Material.AIR);
        }
        if (com.minionx.MinionX.getInstance() != null && com.minionx.MinionX.getInstance().getMinionManager() != null && sl != null) {
            com.minionx.MinionX.getInstance().getMinionManager().unregisterStorageBlock(sl);
        }
        minion.unlinkStorage();
    }
}
