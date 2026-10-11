package com.skyblock.minions.gui;

import com.skyblock.minions.SkyBlockMinions;
import com.skyblock.minions.api.*;
import com.skyblock.minions.core.Minion;
import com.skyblock.minions.hook.IslandHookManager;
import com.skyblock.minions.item.MinionItemFactory;
import com.skyblock.minions.core.CollectionCommandHandler;
import com.skyblock.minions.util.ColorUtil;
import com.skyblock.minions.visual.MinionHologram;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;

/**
 * Handles interactions inside the authentic Hypixel Minion Chest GUI.
 */
public class MinionGUIListener implements Listener {

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof MinionHolder holder)) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        Minion minion = holder.getMinion();
        if (minion == null) return;

        // Verify island / region access permission
        if (!IslandHookManager.canAccessMinion(player, minion)) {
            player.closeInventory();
            String msg = SkyBlockMinions.getInstance().getConfig().getString("messages.no-island-access-permission", "&cYou do not have permission to access this minion!");
            player.sendMessage(ColorUtil.color(msg));
            return;
        }

        int slot = event.getRawSlot();

        // 1. Collect All Items & Claim Stored XP
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

        // 4. Fuel Slot Click
        if (slot == MinionGUI.FUEL_SLOT) {
            handleFuelSlot(player, minion);
            return;
        }

        // 5. Upgrade Slot 1 Click
        if (slot == MinionGUI.UPGRADE_SLOT_1) {
            handleUpgradeSlot(player, minion, 1);
            return;
        }

        // 6. Upgrade Slot 2 Click
        if (slot == MinionGUI.UPGRADE_SLOT_2) {
            handleUpgradeSlot(player, minion, 2);
            return;
        }

        // 7. Individual Storage Slot Clicks
        for (int storageSlot : MinionGUI.STORAGE_SLOTS) {
            if (slot == storageSlot) {
                handleStorageSlotClick(player, minion, slot);
                return;
            }
        }
    }

    private void handleCollectAll(Player player, Minion minion) {
        if (minion.getInventory().isEmpty()) {
            player.sendMessage(ColorUtil.color("&cYour minion has no items to collect!"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        int collectedCount = 0;
        for (ItemStack item : minion.getInventory()) {
            collectedCount += item.getAmount();
            HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);
            for (ItemStack left : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), left);
            }
        }

        minion.getInventory().clear();

        if (minion.getState() == MinionState.FULL) {
            minion.setState(MinionState.IDLE);
        }
        MinionHologram.updateHologram(minion);

        int xp = minion.claimStoredXp();
        if (xp > 0) {
            player.giveExp(xp);
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
        }

        String template = SkyBlockMinions.getInstance().getConfig().getString("messages.collected", "&aCollected %amount% items and &e+%xp% XP &afrom your minion!");
        String msg = template.replace("%amount%", String.valueOf(collectedCount)).replace("%xp%", String.valueOf(xp));
        player.sendMessage(ColorUtil.color(msg));
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);

        MinionGUI.open(player, minion);
    }

    private void handleUpgrade(Player player, Minion minion) {
        int currentTier = minion.getTier();
        if (currentTier >= MinionTier.getMaxTier()) {
            player.sendMessage(ColorUtil.color("&cThis minion is already at the maximum tier!"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        int cost = MinionTier.getUpgradeCost(currentTier);
        Material required = minion.getType().getDropMaterial();
        if (minion.getCustomDefinitionId() != null) {
            MinionDefinition def = MinionRegistry.get(minion.getCustomDefinitionId());
            if (def != null && def.getExactDropItem() != null) {
                required = def.getExactDropItem().getType();
            }
        }

        if (!hasEnoughItems(player, required, cost)) {
            player.sendMessage(ColorUtil.color("&cYou need &e" + cost + "x " + required.name().replace("_", " ").toLowerCase() + " &cto upgrade!"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        removeItems(player, required, cost);
        minion.setTier(currentTier + 1);

        String template = SkyBlockMinions.getInstance().getConfig().getString("messages.upgraded", "&aUpgraded your %minion% to &bTier %tier%&a!");
        String msg = template.replace("%minion%", minion.getDisplayName() + " Minion").replace("%tier%", MinionTier.toRoman(minion.getTier()));
        player.sendMessage(ColorUtil.color(msg));

        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);

        MinionHologram.updateHologram(minion);
        MinionGUI.open(player, minion);
    }

    private void handlePickup(Player player, Minion minion) {
        player.closeInventory();

        // 1. Return stored items
        for (ItemStack item : minion.getInventory()) {
            HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);
            for (ItemStack left : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), left);
            }
        }
        minion.getInventory().clear();

        // 2. Deliver stored XP
        int xp = minion.claimStoredXp();
        if (xp > 0) {
            player.giveExp(xp);
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
        }

        // 3. Return fuel item if present
        if (minion.isFuelActive() && minion.getCustomFuel() != null) {
            player.getInventory().addItem(MinionItemFactory.createFuelItem(minion.getCustomFuel()));
        }

        // 4. Return upgrade 1
        if (minion.getCustomUpgrade1() != null) {
            player.getInventory().addItem(MinionItemFactory.createUpgradeItem(minion.getCustomUpgrade1()));
        }

        // 5. Return upgrade 2
        if (minion.getCustomUpgrade2() != null) {
            player.getInventory().addItem(MinionItemFactory.createUpgradeItem(minion.getCustomUpgrade2()));
        }

        // 6. Return minion item itself!
        ItemStack minionItem;
        if (minion.getCustomDefinitionId() != null) {
            MinionDefinition def = MinionRegistry.get(minion.getCustomDefinitionId());
            minionItem = def != null
                    ? MinionItemFactory.createCustomMinionItem(def, minion.getTier(), minion.getTotalHarvested())
                    : MinionItemFactory.createMinionItem(minion.getType(), minion.getTier(), minion.getTotalHarvested());
        } else {
            minionItem = MinionItemFactory.createMinionItem(minion.getType(), minion.getTier(), minion.getTotalHarvested());
        }
        HashMap<Integer, ItemStack> leftoverMinion = player.getInventory().addItem(minionItem);
        for (ItemStack left : leftoverMinion.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), left);
        }

        // Unregister and despawn
        SkyBlockMinions.getInstance().getMinionManager().unregister(minion);

        player.sendMessage(ColorUtil.color("&eYou picked up your " + minion.getDisplayName() + " Minion!"));
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 0.8f);
    }

    private void handleFuelSlot(Player player, Minion minion) {
        if (minion.isFuelActive()) {
            CustomFuel currentFuel = minion.getCustomFuel();
            minion.setFuel((CustomFuel) null);
            if (currentFuel != null) {
                player.getInventory().addItem(MinionItemFactory.createFuelItem(currentFuel));
                player.sendMessage(ColorUtil.color("&eRemoved " + currentFuel.getDisplayName() + " &efuel from minion."));
            } else {
                player.sendMessage(ColorUtil.color("&eRemoved fuel from minion."));
            }
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
            MinionGUI.open(player, minion);
        } else {
            ItemStack inHand = player.getItemOnCursor();
            CustomFuel fuel = FuelRegistry.fromItemStack(inHand);
            if (fuel != null) {
                inHand.setAmount(inHand.getAmount() - 1);
                minion.setFuel(fuel);
                player.sendMessage(ColorUtil.color("&aEquipped " + fuel.getDisplayName() + " &afuel! Speed boosted by +" + (int) (fuel.getSpeedBuff() * 100) + "%."));
                player.playSound(player.getLocation(), Sound.BLOCK_BREWING_STAND_BREW, 1.0f, 1.2f);
                MinionGUI.open(player, minion);
            }
        }
    }

    private void handleUpgradeSlot(Player player, Minion minion, int slotNum) {
        CustomUpgrade current = slotNum == 1 ? minion.getCustomUpgrade1() : minion.getCustomUpgrade2();

        if (current != null) {
            if (slotNum == 1) minion.setUpgrade1((CustomUpgrade) null);
            else minion.setUpgrade2((CustomUpgrade) null);
            player.getInventory().addItem(MinionItemFactory.createUpgradeItem(current));
            player.sendMessage(ColorUtil.color("&eRemoved " + current.getDisplayName() + "."));
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
            MinionGUI.open(player, minion);
        } else {
            ItemStack inHand = player.getItemOnCursor();
            CustomUpgrade upgrade = UpgradeRegistry.fromItemStack(inHand);
            if (upgrade != null) {
                inHand.setAmount(inHand.getAmount() - 1);
                if (slotNum == 1) minion.setUpgrade1(upgrade);
                else minion.setUpgrade2(upgrade);
                player.sendMessage(ColorUtil.color("&aEquipped " + upgrade.getDisplayName() + "!"));
                player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 1.5f);
                MinionGUI.open(player, minion);
            }
        }
    }

    private void handleStorageSlotClick(Player player, Minion minion, int clickedSlot) {
        int listIndex = -1;
        for (int i = 0; i < MinionGUI.STORAGE_SLOTS.length; i++) {
            if (MinionGUI.STORAGE_SLOTS[i] == clickedSlot) {
                listIndex = i;
                break;
            }
        }

        if (listIndex >= 0 && listIndex < minion.getInventory().size()) {
            ItemStack item = minion.getInventory().remove(listIndex);
            int amount = item.getAmount();
            Material itemType = item.getType();

            HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);
            for (ItemStack left : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), left);
            }
            CollectionCommandHandler.executeCollectionCommands(player, minion, itemType, amount);

            int xp = minion.claimStoredXp();
            if (xp > 0) {
                player.giveExp(xp);
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
            }

            if (minion.getState() == MinionState.FULL) {
                minion.setState(MinionState.IDLE);
            }
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
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == mat) {
                int take = Math.min(remaining, item.getAmount());
                item.setAmount(item.getAmount() - take);
                remaining -= take;
                if (remaining <= 0) break;
            }
        }
    }
}
