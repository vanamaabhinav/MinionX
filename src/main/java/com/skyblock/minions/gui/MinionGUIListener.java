package com.skyblock.minions.gui;

import com.skyblock.minions.SkyBlockMinions;
import com.skyblock.minions.api.*;
import com.skyblock.minions.core.CollectionCommandHandler;
import com.skyblock.minions.core.Minion;
import com.skyblock.minions.item.MinionItemFactory;
import com.skyblock.minions.visual.MinionHologram;
import com.skyblock.minions.visual.MinionVisual;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;

/**
 * Handles interactions within the Minion GUI.
 */
public class MinionGUIListener implements Listener {

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof MinionHolder holder)) {
            return;
        }

        Minion minion = holder.getMinion();
        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();

        // If clicked outside GUI (in player's own inventory)
        if (slot >= 54) {
            return;
        }

        // Cancel all clicks in the minion GUI by default for safety
        event.setCancelled(true);

        // 1. Quick Collect All
        if (slot == MinionGUI.COLLECT_ALL_SLOT) {
            handleCollectAll(player, minion);
            return;
        }

        // 2. Upgrade Minion Tier
        if (slot == MinionGUI.UPGRADE_MINION_SLOT) {
            handleUpgrade(player, minion);
            return;
        }

        // 3. Pickup Minion
        if (slot == MinionGUI.PICKUP_SLOT) {
            handlePickup(player, minion);
            return;
        }

        // 4. Fuel Slot
        if (slot == MinionGUI.FUEL_SLOT) {
            handleFuelSlot(player, minion);
            return;
        }

        // 5. Upgrade Slots
        if (slot == MinionGUI.UPGRADE_SLOT_1) {
            handleUpgradeSlot(player, minion, 1);
            return;
        }
        if (slot == MinionGUI.UPGRADE_SLOT_2) {
            handleUpgradeSlot(player, minion, 2);
            return;
        }

        // 6. Direct Storage Slot clicks (Taking individual items)
        for (int i = 0; i < MinionGUI.STORAGE_SLOTS.length; i++) {
            if (slot == MinionGUI.STORAGE_SLOTS[i]) {
                handleStorageSlotClick(player, minion, i);
                return;
            }
        }
    }

    private void handleCollectAll(Player player, Minion minion) {
        if (minion.getInventory().isEmpty()) {
            player.sendMessage("§cThere are no stored items to collect!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        int collectedCount = 0;
        for (ItemStack item : minion.getInventory()) {
            int amount = item.getAmount();
            Material mat = item.getType();
            HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);
            collectedCount += amount;
            CollectionCommandHandler.executeCollectionCommands(player, minion, mat, amount);
            for (ItemStack left : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), left);
            }
        }

        minion.getInventory().clear();
        minion.setState(MinionState.IDLE);
        MinionHologram.updateHologram(minion);

        player.sendMessage("§aCollected " + collectedCount + " items from your minion!");
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);

        MinionGUI.open(player, minion);
    }

    private void handleUpgrade(Player player, Minion minion) {
        int currentTier = minion.getTier();
        if (currentTier >= MinionTier.getMaxTier()) {
            player.sendMessage("§cThis minion is already at the maximum tier!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        int cost = MinionTier.getUpgradeCost(currentTier);
        Material required = minion.getType().getDropMaterial();

        if (!hasEnoughItems(player, required, cost)) {
            player.sendMessage("§cYou need " + cost + " " + minion.getType().getDisplayName() + " to upgrade this minion!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // Deduct items
        removeItems(player, required, cost);

        // Level up
        minion.setTier(currentTier + 1);
        MinionVisual.spawnOrUpdate(minion);
        MinionHologram.updateHologram(minion);

        player.sendMessage("§aUpgraded " + minion.getType().getDisplayName() + " Minion to Tier " + MinionTier.toRoman(minion.getTier()) + "!");
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.2f);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);

        MinionGUI.open(player, minion);
    }

    private void handlePickup(Player player, Minion minion) {
        player.closeInventory();

        // Return stored items
        for (ItemStack item : minion.getInventory()) {
            HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);
            for (ItemStack left : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), left);
            }
        }
        minion.getInventory().clear();

        // Return fuel item if present
        if (minion.getFuel() != null) {
            player.getInventory().addItem(new ItemStack(minion.getFuel().getMaterial()));
        }

        // Return upgrade items if present
        if (minion.getUpgrade1() != null) {
            player.getInventory().addItem(MinionItemFactory.createUpgradeItem(minion.getUpgrade1()));
        }
        if (minion.getUpgrade2() != null) {
            player.getInventory().addItem(MinionItemFactory.createUpgradeItem(minion.getUpgrade2()));
        }

        // Give Minion item
        ItemStack minionItem = MinionItemFactory.createMinionItem(minion.getType(), minion.getTier(), minion.getTotalHarvested());
        HashMap<Integer, ItemStack> left = player.getInventory().addItem(minionItem);
        for (ItemStack extra : left.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), extra);
        }

        // Unregister and despawn
        SkyBlockMinions.getInstance().getMinionManager().unregister(minion);

        player.sendMessage("§eYou picked up your " + minion.getType().getDisplayName() + " Minion!");
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 0.8f);
    }

    private void handleFuelSlot(Player player, Minion minion) {
        if (minion.isFuelActive()) {
            CustomFuel currentFuel = minion.getCustomFuel();
            minion.setFuel((CustomFuel) null);
            if (currentFuel != null) {
                player.getInventory().addItem(MinionItemFactory.createFuelItem(currentFuel));
                player.sendMessage("§eRemoved " + currentFuel.getDisplayName() + " §efuel from minion.");
            } else {
                player.sendMessage("§eRemoved fuel from minion.");
            }
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
            MinionGUI.open(player, minion);
        } else {
            ItemStack inHand = player.getItemOnCursor();
            CustomFuel fuel = FuelRegistry.fromItemStack(inHand);
            if (fuel != null) {
                inHand.setAmount(inHand.getAmount() - 1);
                minion.setFuel(fuel);
                player.sendMessage("§aEquipped " + fuel.getDisplayName() + " §afuel! Speed boosted by +" + (int) (fuel.getSpeedBuff() * 100) + "%.");
                player.playSound(player.getLocation(), Sound.BLOCK_BREWING_STAND_BREW, 1.0f, 1.2f);
                MinionGUI.open(player, minion);
            }
        }
    }

    private void handleUpgradeSlot(Player player, Minion minion, int slotNum) {
        CustomUpgrade current = (slotNum == 1) ? minion.getCustomUpgrade1() : minion.getCustomUpgrade2();
        if (current != null) {
            if (slotNum == 1) minion.setUpgrade1((CustomUpgrade) null);
            else minion.setUpgrade2((CustomUpgrade) null);
            player.getInventory().addItem(MinionItemFactory.createUpgradeItem(current));
            player.sendMessage("§eRemoved " + current.getDisplayName() + ".");
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
            MinionGUI.open(player, minion);
        } else {
            ItemStack inHand = player.getItemOnCursor();
            CustomUpgrade upgrade = UpgradeRegistry.fromItemStack(inHand);
            if (upgrade != null) {
                inHand.setAmount(inHand.getAmount() - 1);
                if (slotNum == 1) minion.setUpgrade1(upgrade);
                else minion.setUpgrade2(upgrade);
                player.sendMessage("§aEquipped " + upgrade.getDisplayName() + "!");
                player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 1.5f);
                MinionGUI.open(player, minion);
            }
        }
    }

    private void handleStorageSlotClick(Player player, Minion minion, int index) {
        int unlocked = MinionTier.getStorageSlots(minion.getTier());
        if (index < unlocked && index < minion.getInventory().size()) {
            ItemStack item = minion.getInventory().remove(index);
            int amount = item.getAmount();
            Material itemType = item.getType();
            HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);
            for (ItemStack left : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), left);
            }
            CollectionCommandHandler.executeCollectionCommands(player, minion, itemType, amount);
            minion.setState(MinionState.IDLE);
            MinionHologram.updateHologram(minion);
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
            MinionGUI.open(player, minion);
        }
    }

    private boolean hasEnoughItems(Player player, Material mat, int amount) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == mat) {
                count += item.getAmount();
                if (count >= amount) return true;
            }
        }
        return false;
    }

    private void removeItems(Player player, Material mat, int amount) {
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item != null && item.getType() == mat) {
                int take = Math.min(remaining, item.getAmount());
                item.setAmount(item.getAmount() - take);
                remaining -= take;
                if (item.getAmount() <= 0) {
                    contents[i] = null;
                }
                if (remaining <= 0) break;
            }
        }
        player.getInventory().setContents(contents);
    }
}
