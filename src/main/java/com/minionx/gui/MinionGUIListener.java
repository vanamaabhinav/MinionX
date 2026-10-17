package com.minionx.gui;

import com.minionx.quarantine.QuarantineManager;
import com.minionx.listener.MinionStorageListener;

import com.minionx.MinionX;
import com.minionx.api.*;
import com.minionx.core.CollectionCommandHandler;
import com.minionx.core.Minion;
import com.minionx.hook.ProtectionHookManager;
import com.minionx.hook.SkillHook;
import com.minionx.item.MinionItemFactory;
import com.minionx.util.ColorUtil;
import com.minionx.util.UpgradeCostHelper;
import com.minionx.visual.MinionHologram;
import com.minionx.visual.MinionVisual;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;
import com.minionx.storage.MinionStorage;
import com.minionx.storage.DatabaseStorage;

/**
 * Handles clicks and interactions inside the Minion GUI.
 * Supports multi-item fuel stacking, one-time use fuel destruction toggle,
 * swift shift-click equipping, and smooth Bedrock/Java inventory navigation.
 */
public class MinionGUIListener implements Listener {

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof MinionHolder)) {
            return;
        }

        // Cancel drag if it touches any top inventory slots (0 - 53)
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < 54) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof MinionHolder holder)) {
            return;
        }

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        Minion minion = holder.getMinion();
        if (minion == null) return;

        // Verify island / region access permission
        if (!ProtectionHookManager.canAccessMinion(player, minion)) {
            event.setCancelled(true);
            player.closeInventory();
            String msg = MinionX.getInstance().getConfig().getString("messages.no-island-access-permission", "&cYou do not have permission to access this minion!");
            player.sendMessage(ColorUtil.color(msg));
            return;
        }

        int slot = event.getRawSlot();

        // 1. Bottom Player Inventory Click (slot >= 54)
        if (slot >= 54) {
            if (event.isShiftClick()) {
                ItemStack clicked = event.getCurrentItem();
                if (clicked == null || clicked.getType() == Material.AIR) {
                    event.setCancelled(false);
                    return;
                }

                // Shift-click Fuel item -> auto-deposit into fuel socket
                CustomFuel fuel = FuelRegistry.fromItemStack(clicked);
                if (fuel != null) {
                    event.setCancelled(true);
                    handleFuelDeposit(player, minion, clicked, clicked.getAmount());
                    return;
                }

                // Shift-click Upgrade item -> auto-equip into first free upgrade slot OR automated shipping if auto-seller
                CustomUpgrade upgrade = UpgradeRegistry.fromItemStack(clicked);
                if (upgrade != null) {
                    event.setCancelled(true);
                    if (upgrade.isAutoSeller()) {
                        handleShippingShiftClick(player, minion, clicked, upgrade);
                    } else {
                        handleUpgradeShiftClick(player, minion, clicked, upgrade);
                    }
                    return;
                }

                // Shift-click Skin item -> auto-apply into skin slot
                String skinId = com.minionx.item.MinionItemFactory.getSkinId(clicked);
                if (skinId != null) {
                    event.setCancelled(true);
                    handleSkinApply(player, minion, clicked, skinId);
                    return;
                }

                // Prevent arbitrary player items from being shift-clicked into the virtual Minion GUI (Issue 7 & 10)
                event.setCancelled(true);
                return;
            } else {
                // Allow normal item movement/pickup in player's own inventory
                event.setCancelled(false);
                return;
            }
        }

        // Click is within top minion GUI (0 <= slot <= 53)
        event.setCancelled(true);

        // Ideal Layout (Slot 3)
        if (slot == MinionGUIConfig.getIdealLayoutSlot()) {
            int r = minion.getWorkingRadius();
            int dim = (r * 2) + 1;
            int blocks = (dim * dim) - 1;
            int expanders = minion.getAreaExpansion();

            player.sendMessage(ColorUtil.color("&e----------------------------------------"));
            player.sendMessage(ColorUtil.color("&6&lMinion Ideal Layout Guide &7(" + dim + "x" + dim + ")"));
            player.sendMessage(ColorUtil.color("&7Working Area: &a" + dim + "x" + dim + " square &7(" + blocks + " blocks)"));
            player.sendMessage(ColorUtil.color("&7Radius: &f" + r + " blocks &7centered around the minion ground level."));
            player.sendMessage(ColorUtil.color("&7Minion Expanders equipped: &e" + expanders + "/2" + (expanders == 0 ? " &7(Base 5x5)" : (expanders == 1 ? " &7(+1 -> 7x7)" : " &b(+2 -> 9x9 Max)"))));
            player.sendMessage(ColorUtil.color("&7Keep the area flat and free of obstructions!"));
            player.sendMessage(ColorUtil.color("&e----------------------------------------"));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
            return;
        }

        // Next Tier Info (Slot 5)
        if (slot == MinionGUIConfig.getNextTierSlot()) {
            int nextTier = minion.getTier() + 1;
            if (nextTier > minion.getMaxTier()) {
                player.sendMessage(ColorUtil.color("&cThis minion is already at the maximum tier (" + MinionTier.toRoman(minion.getMaxTier()) + ")!"));
            } else {
                UpgradeCostHelper.Requirement req = UpgradeCostHelper.getRequirement(minion);
                player.sendMessage(ColorUtil.color("&e[Next Tier] Upgrade to Tier " + MinionTier.toRoman(nextTier) + " requires: &a" + req.getRequiredAmount() + "x " + req.getDisplayName() + " &7(Use Quick-Upgrade below!)"));
            }
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.2f);
            return;
        }

        // Automated Shipping Slot Click (Slot 28)
        if (slot == MinionGUIConfig.getAutomatedShippingSlot()) {
            handleShippingSlotClick(player, minion, event);
            return;
        }

        // Collect All Items & Claim Stored XP
        if (slot == MinionGUIConfig.getCollectAllSlot()) {
            handleCollectAll(player, minion);
            return;
        }

        // Quick-Upgrade Minion Tier
        if (slot == MinionGUIConfig.getUpgradeTierSlot()) {
            handleUpgrade(player, minion);
            return;
        }

        // Pickup Minion
        if (slot == MinionGUIConfig.getPickupSlot()) {
            handlePickup(player, minion);
            return;
        }

        // Fuel Slot Click
        if (slot == MinionGUIConfig.getFuelSlot()) {
            handleFuelSlot(player, minion, event);
            return;
        }

        // Upgrade Slot 1 Click
        if (slot == MinionGUIConfig.getUpgrade1Slot()) {
            handleUpgradeSlot(player, minion, 1, event);
            return;
        }

        // Upgrade Slot 2 Click
        if (slot == MinionGUIConfig.getUpgrade2Slot()) {
            handleUpgradeSlot(player, minion, 2, event);
            return;
        }

        // Minion Skin Slot Click
        if (slot == MinionGUIConfig.getSkinSlot()) {
            handleSkinSlot(player, minion, event);
            return;
        }

        // Storage Slot Clicks (Slots 21-25, 30-34, 39-43)
        int storageIndex = MinionGUIConfig.getStorageIndex(slot);
        if (storageIndex >= 0) {
            handleStorageSlotClick(player, minion, storageIndex);
        }
    }

    private void handleSkinSlot(Player player, Minion minion, org.bukkit.event.inventory.InventoryClickEvent event) {
        ItemStack cursor = event.getCursor();
        String skinId = com.minionx.item.MinionItemFactory.getSkinId(cursor);

        if (skinId != null) {
            // Apply skin from cursor
            handleSkinApply(player, minion, cursor, skinId);
            return;
        }

        // Click with empty hand on active skin -> retrieve skin
        if (minion.hasSkin()) {
            if (player.getInventory().firstEmpty() == -1) {
                player.sendMessage(ColorUtil.color("&cYour inventory is full! Make space to retrieve the minion skin."));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }
            MinionSkin oldSkin = minion.getSkin();
            minion.setSkinId(null);
            ItemStack skinItem = com.minionx.item.MinionItemFactory.createSkinItem(oldSkin);
            player.getInventory().addItem(skinItem);
            com.minionx.visual.MinionVisual.spawnOrUpdate(minion);
            MinionGUI.open(player, minion);
            player.sendMessage(ColorUtil.getPrefix() + ColorUtil.color("&aRemoved &d" + oldSkin.getDisplayName() + " &afrom minion!"));
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
        } else {
            player.sendMessage(ColorUtil.getPrefix() + ColorUtil.color("&7Place a Minion Skin into this slot to transform your minion's appearance!"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.2f);
        }
    }

    private void handleSkinApply(Player player, Minion minion, ItemStack skinItem, String skinId) {
        MinionSkin newSkin = MinionSkinRegistry.get(skinId);
        if (newSkin == null) return;

        // If minion already has a skin, return old skin first
        if (minion.hasSkin()) {
            if (player.getInventory().firstEmpty() == -1 && skinItem.getAmount() > 1) {
                player.sendMessage(ColorUtil.color("&cYour inventory is full! Make space to receive the previous minion skin."));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }
            MinionSkin oldSkin = minion.getSkin();
            ItemStack oldItem = com.minionx.item.MinionItemFactory.createSkinItem(oldSkin);
            player.getInventory().addItem(oldItem);
        }

        minion.setSkinId(skinId);
        skinItem.setAmount(skinItem.getAmount() - 1);
        com.minionx.visual.MinionVisual.spawnOrUpdate(minion);
        MinionGUI.open(player, minion);
        player.sendMessage(ColorUtil.getPrefix() + ColorUtil.color("&aApplied &d" + newSkin.getDisplayName() + " &ato minion!"));
        player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 1.0f, 1.2f);
    }

    private void handleCollectAll(Player player, Minion minion) {
        if (!minion.tryAcquireTransaction()) {
            player.sendMessage(ColorUtil.color("&cMinion is busy. Please try again."));
            return;
        }
        try {
            if (QuarantineManager.isQuarantined() && !QuarantineManager.canBypass(player)) {
                player.sendMessage(ColorUtil.color("&c[Quarantine] Item collection is disabled during emergency quarantine mode!"));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }
            if (minion.getInventory().isEmpty() && minion.getStorageContents().isEmpty() && minion.getShippingCoins() <= 0) {
                player.sendMessage(ColorUtil.color("&cYour minion has no items to collect!"));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }

            // Check if player has any room at all
            if (player.getInventory().firstEmpty() == -1 && !hasStackableRoom(player, minion)) {
                player.sendMessage(ColorUtil.color("&cYour inventory is full! Make space to collect items."));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }

            // Fire MinionCollectionEvent
            com.minionx.api.event.MinionCollectionEvent collectEvent = new com.minionx.api.event.MinionCollectionEvent(
                    player, minion, minion.getInventory(), minion.getStoredXp());
            org.bukkit.Bukkit.getPluginManager().callEvent(collectEvent);
            if (collectEvent.isCancelled()) {
                return;
            }

            int collectedCount = 0;
            boolean anyLeftover = false;

            // 1. Collect from minion internal inventory
            List<ItemStack> remainingInv = new ArrayList<>();
            for (ItemStack item : minion.getInventory()) {
                if (item == null || item.getType() == Material.AIR) continue;
                int originalAmount = item.getAmount();
                Material itemType = item.getType();
                HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);
                int delivered = originalAmount;
                if (!leftover.isEmpty()) {
                    anyLeftover = true;
                    int remaining = 0;
                    for (ItemStack left : leftover.values()) {
                        remaining += left.getAmount();
                    }
                    delivered = originalAmount - remaining;
                    ItemStack remStack = item.clone();
                    remStack.setAmount(remaining);
                    remainingInv.add(remStack);
                }
                if (delivered > 0) {
                    collectedCount += delivered;
                    CollectionCommandHandler.executeCollectionCommands(player, minion, itemType, delivered);
                    com.minionx.hook.CollectionsHook.recordCollection(player, minion, itemType, delivered);
                }
            }
            minion.getInventory().clear();
            minion.getInventory().addAll(remainingInv);

            // 2. Collect from linked minion storage chest
            List<ItemStack> remainingStore = new ArrayList<>();
            for (ItemStack item : minion.getStorageContents()) {
                if (item == null || item.getType() == Material.AIR) continue;
                int originalAmount = item.getAmount();
                Material itemType = item.getType();
                HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);
                int delivered = originalAmount;
                if (!leftover.isEmpty()) {
                    anyLeftover = true;
                    int remaining = 0;
                    for (ItemStack left : leftover.values()) {
                        remaining += left.getAmount();
                    }
                    delivered = originalAmount - remaining;
                    ItemStack remStack = item.clone();
                    remStack.setAmount(remaining);
                    remainingStore.add(remStack);
                }
                if (delivered > 0) {
                    collectedCount += delivered;
                    CollectionCommandHandler.executeCollectionCommands(player, minion, itemType, delivered);
                    com.minionx.hook.CollectionsHook.recordCollection(player, minion, itemType, delivered);
                }
            }
            minion.getStorageContents().clear();
            minion.getStorageContents().addAll(remainingStore);

            // 3. Collect coins from Automated Shipping hopper
            if (minion.getShippingCoins() > 0) {
                collectShippingCoinsSafely(player, minion);
            }

            minion.wakeUp();
            if (minion.getState() == MinionState.FULL && !anyLeftover) {
                minion.setState(MinionState.IDLE);
                // Advance timing anchor so paused time is never credited
                minion.setLastActionTime(System.currentTimeMillis());
            }
            MinionHologram.updateHologram(minion);
            MinionVisual.spawnOrUpdate(minion);

            int xp = minion.claimStoredXp();
            if (xp > 0) {
                player.giveExp(xp);
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
            }

            grantSkillRewards(player, minion, collectedCount);

            String template = MinionX.getInstance().getConfig().getString("messages.collected", "&aCollected %amount% items and &e+%xp% XP &afrom your minion!");
            String msg = template.replace("%amount%", String.valueOf(collectedCount)).replace("%xp%", String.valueOf(xp));
            player.sendMessage(ColorUtil.color(msg));
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);

            if (anyLeftover) {
                player.sendMessage(ColorUtil.color("&eSome items could not fit in your inventory and remain safely inside the minion."));
            }

            minion.advanceStateRevision();
            MinionGUI.open(player, minion);
        } finally {
            minion.releaseTransaction();
        }
    }

    private void handleShippingSlotClick(Player player, Minion minion, InventoryClickEvent event) {
        ItemStack cursor = event.getCursor();

        // 1. If a shipping hopper is currently equipped:
        if (minion.getShippingId() != null) {
            if (event.isShiftClick()) {
                CustomUpgrade hopper = minion.getShippingUpgrade();
                if (hopper != null && player.getInventory().firstEmpty() == -1) {
                    player.sendMessage(ColorUtil.color("&cYour inventory is full! Make space to retrieve the hopper."));
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    return;
                }
                minion.setShippingId(null);

                // Collect accumulated coins
                if (minion.getShippingCoins() > 0) {
                    collectShippingCoinsSafely(player, minion);
                }

                if (hopper != null) {
                    player.getInventory().addItem(MinionItemFactory.createUpgradeItem(hopper));
                    player.sendMessage(ColorUtil.color("&aRemoved Automated Shipping hopper (&f" + hopper.getDisplayName() + "&a)!"));
                }
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
                MinionGUI.open(player, minion);
                return;
            } else {
                // Regular click: collect coins
                if (minion.getShippingCoins() > 0) {
                    collectShippingCoinsSafely(player, minion);
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
                    MinionGUI.open(player, minion);
                    return;
                } else {
                    player.sendMessage(ColorUtil.color("&7No coins accumulated yet! Overflow items will automatically sell for coins when storage is full."));
                    player.sendMessage(ColorUtil.color("&7Shift-Click this slot to retrieve your hopper."));
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.0f);
                    return;
                }
            }
        }

        // 2. If no hopper equipped, check if cursor holds an auto-seller hopper:
        if (cursor != null && !cursor.getType().isAir()) {
            String upId = MinionItemFactory.getUpgradeId(cursor);
            CustomUpgrade upgrade = upId != null ? UpgradeRegistry.get(upId) : null;
            if (upgrade != null && upgrade.isAutoSeller()) {
                minion.setShippingId(upId);
                cursor.setAmount(cursor.getAmount() - 1);
                player.setItemOnCursor(cursor.getAmount() > 0 ? cursor : null);

                player.sendMessage(ColorUtil.color("&aEquipped &f" + upgrade.getDisplayName() + " &ain Automated Shipping!"));
                player.playSound(player.getLocation(), Sound.BLOCK_CHEST_LOCKED, 1.0f, 1.2f);
                MinionGUI.open(player, minion);
                return;
            }
        }

        player.sendMessage(ColorUtil.color("&e[Automated Shipping] Place a Budget Hopper or Enchanted Hopper here to auto-sell overflow items!"));
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
    }

    private void handleUpgrade(Player player, Minion minion) {
        int currentTier = minion.getTier();
        if (currentTier >= minion.getMaxTier()) {
            player.sendMessage(ColorUtil.color("&cThis minion is already at the maximum tier (" + MinionTier.toRoman(minion.getMaxTier()) + ")!"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        UpgradeCostHelper.Requirement req = UpgradeCostHelper.getRequirement(minion);

        if (!UpgradeCostHelper.hasEnoughItems(player, req)) {
            String template = MinionX.getInstance().getConfig().getString("messages.not-enough-items", "&cYou need %cost%x %material% to upgrade to Tier %tier%!");
            String msg = template.replace("%cost%", String.valueOf(req.getRequiredAmount()))
                    .replace("%material%", req.getDisplayName())
                    .replace("%tier%", MinionTier.toRoman(currentTier + 1));
            player.sendMessage(ColorUtil.color(msg));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // Fire MinionUpgradeEvent
        com.minionx.api.event.MinionUpgradeEvent upgradeEvent = new com.minionx.api.event.MinionUpgradeEvent(
                player, minion, currentTier, currentTier + 1);
        org.bukkit.Bukkit.getPluginManager().callEvent(upgradeEvent);
        if (upgradeEvent.isCancelled()) {
            return;
        }

        UpgradeCostHelper.removeMatchingItems(player, req);
        minion.setTier(currentTier + 1);

        MinionHologram.updateHologram(minion);
        MinionVisual.spawnOrUpdate(minion);

        String template = MinionX.getInstance().getConfig().getString("messages.upgraded", "&aUpgraded to %minion% %tier%!");
        String msg = template.replace("%minion%", minion.getDisplayName())
                .replace("%tier%", MinionTier.toRoman(minion.getTier()));
        player.sendMessage(ColorUtil.color(msg));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);

        MinionGUI.open(player, minion);
    }

    private void handlePickup(Player player, Minion minion) {
        player.closeInventory();

        if (!minion.tryAcquireTransaction()) {
            player.sendMessage(ColorUtil.color("&cMinion is busy. Please try again."));
            return;
        }

        // 1. Anti-dupe check: verify minion is still registered
        if (MinionX.getInstance().getMinionManager().getMinionById(minion.getMinionId()) == null) {
            minion.releaseTransaction();
            return;
        }

        // Check if minion is undergoing automated shipping payout
        MinionStorage storage = MinionX.getInstance() != null ? MinionX.getInstance().getMinionStorage() : null;
        if (storage != null && storage.isPayoutActive(minion.getMinionId())) {
            player.sendMessage(ColorUtil.color("&cCannot pick up minion while automated shipping payout is in progress."));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            minion.releaseTransaction();
            return;
        }

        if (QuarantineManager.isQuarantined() && !QuarantineManager.canBypass(player)) {
            player.sendMessage(ColorUtil.color("&c[Quarantine] Minion pickup is disabled during emergency quarantine mode!"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            minion.releaseTransaction();
            return;
        }

        // Fire MinionPickupEvent
        com.minionx.api.event.MinionPickupEvent pickupEvent = new com.minionx.api.event.MinionPickupEvent(player, minion);
        org.bukkit.Bukkit.getPluginManager().callEvent(pickupEvent);
        if (pickupEvent.isCancelled()) {
            minion.releaseTransaction();
            return;
        }

        // 2. Preflight: Collect all candidate items that must be transferred to player
        List<ItemStack> itemsToTransfer = new ArrayList<>();

        // Placeable minion item
        ItemStack minionItem;
        if (minion.getCustomDefinitionId() != null) {
            MinionDefinition def = MinionRegistry.get(minion.getCustomDefinitionId());
            minionItem = MinionItemFactory.createCustomMinionItem(def, minion.getTier(), minion.getTotalHarvested());
        } else {
            minionItem = MinionItemFactory.createMinionItem(minion.getType(), minion.getTier(), minion.getTotalHarvested());
        }
        if (minionItem != null) itemsToTransfer.add(minionItem);

        // Equipped upgrades
        if (minion.getCustomUpgrade1() != null) {
            itemsToTransfer.add(MinionItemFactory.createUpgradeItem(minion.getCustomUpgrade1()));
        }
        if (minion.getCustomUpgrade2() != null) {
            itemsToTransfer.add(MinionItemFactory.createUpgradeItem(minion.getCustomUpgrade2()));
        }

        // Shipping upgrade
        if (minion.getShippingUpgrade() != null) {
            itemsToTransfer.add(MinionItemFactory.createUpgradeItem(minion.getShippingUpgrade()));
        }

        // Skin
        if (minion.hasSkin()) {
            itemsToTransfer.add(MinionItemFactory.createSkinItem(minion.getSkin()));
        }

        // Fuel
        if (minion.isFuelActive()) {
            CustomFuel fuel = minion.getCustomFuel();
            if (fuel != null) {
                if (fuel.isPermanent()) {
                    itemsToTransfer.add(MinionItemFactory.createFuelItem(fuel));
                } else {
                    boolean destroyOnRemoval = MinionX.getInstance().getConfig()
                            .getBoolean("settings.destroy-temporary-fuel-on-removal", true);
                    if (!destroyOnRemoval) {
                        itemsToTransfer.add(MinionItemFactory.createFuelItem(fuel));
                    }
                }
            }
        }

        // Linked storage chest item
        if (minion.hasLinkedStorage()) {
            CustomMinionStorage st = minion.getLinkedStorage();
            if (st != null) {
                itemsToTransfer.add(MinionItemFactory.createStorageItem(st));
            }
        }

        // Internal inventory items
        for (ItemStack item : minion.getInventory()) {
            if (item != null && item.getType() != Material.AIR) {
                itemsToTransfer.add(item.clone());
            }
        }

        // Linked storage contents
        for (ItemStack item : minion.getStorageContents()) {
            if (item != null && item.getType() != Material.AIR) {
                itemsToTransfer.add(item.clone());
            }
        }

        // 3. Preflight capacity check across ALL candidate items
        if (!canHoldAll(player.getInventory(), itemsToTransfer)) {
            player.sendMessage(ColorUtil.color("&cYour inventory cannot hold all items from this minion! Make room before picking it up."));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            minion.releaseTransaction();
            return;
        }

        // 4. Validate and serialize escrow payload
        List<String> serializedPayload = new ArrayList<>();
        for (ItemStack stack : itemsToTransfer) {
            YamlConfiguration yc = new YamlConfiguration();
            yc.set("item", stack);
            serializedPayload.add(yc.saveToString());
        }
        String payloadStr = String.join(";;;ITEM;;;", serializedPayload);

        // 5. Phase 1: Atomic Minion Removal & Escrow Commit (MINION_REMOVED)
        String operationId = UUID.randomUUID().toString();
        DatabaseStorage.PickupRemovalResult removalResult = MinionX.getInstance().getMinionStorage().executePickupRemoval(
                operationId,
                minion.getMinionId(),
                player.getUniqueId(),
                minion.getStateRevision(),
                payloadStr
        );
        if (removalResult != DatabaseStorage.PickupRemovalResult.COMMITTED_NOW &&
            removalResult != DatabaseStorage.PickupRemovalResult.ALREADY_COMMITTED_MINION_REMOVED) {
            if (removalResult == DatabaseStorage.PickupRemovalResult.ALREADY_DELIVERY_ATTEMPTED) {
                player.sendMessage(ColorUtil.color("&cPickup delivery is already in progress or attempted."));
            } else if (removalResult == DatabaseStorage.PickupRemovalResult.ALREADY_COMPLETED) {
                player.sendMessage(ColorUtil.color("&eThis minion pickup has already been completed."));
            } else {
                player.sendMessage(ColorUtil.color("&cFailed to initiate secure pickup transaction. Please try again."));
            }
            minion.releaseTransaction();
            return;
        }

        // Clean up runtime visual and manager entities immediately upon confirmed removal
        MinionX.getInstance().getMinionManager().unregister(minion);
        MinionVisual.remove(minion);
        MinionHologram.remove(minion);
        if (minion.hasLinkedStorage()) {
            MinionStorageListener.removeStorageOnMinionRemoval(minion, false);
        }

        // 6. Phase 2: Claim Delivery Attempt (MINION_REMOVED -> DELIVERY_ATTEMPTED)
        boolean claimed = MinionX.getInstance().getMinionStorage().claimDeliveryAttempt(operationId);
        if (!claimed) {
            player.sendMessage(ColorUtil.color("&cPickup delivery attempt claim failed."));
            minion.releaseTransaction();
            return;
        }

        List<ItemStack> leftoverItems = new ArrayList<>();
        for (ItemStack item : itemsToTransfer) {
            HashMap<Integer, ItemStack> left = player.getInventory().addItem(item);
            if (!left.isEmpty()) {
                leftoverItems.addAll(left.values());
            }
        }

        // 7. Phase 3: Explicit Delivery Confirmation / Leftover Quarantine
        if (leftoverItems.isEmpty()) {
            // Full delivery confirmed! Mark COMPLETED and remove pending operation
            MinionX.getInstance().getMinionStorage().completeDelivery(operationId);
            try {
                MinionX.getInstance().getMinionStorage().removePendingOperation(operationId);
            } catch (Exception ignored) {}
            player.sendMessage(ColorUtil.color("&eYou picked up your " + minion.getDisplayName() + " Minion!"));
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 0.8f);
        } else {
            // Partial delivery: DO NOT mark COMPLETED! Operation remains quarantined in escrow ledger.
            List<String> remPayload = new ArrayList<>();
            for (ItemStack rem : leftoverItems) {
                YamlConfiguration yc = new YamlConfiguration();
                yc.set("item", rem);
                remPayload.add(yc.saveToString());
                if (player.isOnline()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), rem);
                }
            }
            try {
                MinionX.getInstance().getMinionStorage().updatePendingOperationState(
                        operationId, "QUARANTINED_LEFTOVERS", String.join(";;;ITEM;;;", remPayload));
            } catch (Exception ignored) {}
            player.sendMessage(ColorUtil.color("&eMinion picked up! Some items did not fit in your inventory and were placed at your feet."));
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 0.8f);
        }

        // Claim XP and coins safely
        int xp = minion.claimStoredXp();
        if (xp > 0) {
            player.giveExp(xp);
        }
        collectShippingCoinsSafely(player, minion);
        minion.releaseTransaction();
    }

    private void collectShippingCoinsSafely(Player player, Minion minion) {
        double coins = minion.getClaimableShippingCoins();
        if (coins <= 0.0) return;
        double normalizedCoins = java.math.BigDecimal.valueOf(coins).setScale(4, java.math.RoundingMode.HALF_UP).doubleValue();
        if (normalizedCoins <= 0.0) return;

        MinionStorage storage = MinionX.getInstance() != null ? MinionX.getInstance().getMinionStorage() : null;
        if (storage == null) return;

        // 1. Acquire main-thread payout lock
        if (!storage.acquirePayoutLock(minion.getMinionId())) {
            player.sendMessage(ColorUtil.color("&cAutomated shipping payout is already in progress for this minion."));
            return;
        }

        // 2. Main-Thread State Ownership: capture DB revision and reserve coins in dual-balance model
        long dbRev = minion.getStateRevision();
        if (!minion.reserveClaimableCoins(normalizedCoins)) {
            storage.releasePayoutLock(minion.getMinionId());
            return;
        }

        String opId = "payout-" + UUID.randomUUID();
        UUID minionId = minion.getMinionId();
        UUID playerUuid = player.getUniqueId();

        // 3. Dispatch atomic reservation to persistence
        storage.getPersistenceExecutor().submit(() -> {
            boolean reserved = storage.reservePayoutAtomic(opId, minionId, playerUuid, normalizedCoins, dbRev);
            if (!reserved) {
                // If minion is quarantined due to ambiguous commit, keep payout lock and do not cancel reserved balance
                if (storage.isQuarantined(minionId)) {
                    Runnable quarantineNotify = () -> {
                        if (player.isOnline()) {
                            player.sendMessage(ColorUtil.color("&cPayout ledger outcome is uncertain. Minion has been quarantined pending reconciliation."));
                        }
                    };
                    if (MinionX.getInstance() != null && MinionX.getInstance().isEnabled()) {
                        Bukkit.getScheduler().runTask(MinionX.getInstance(), quarantineNotify);
                    } else {
                        quarantineNotify.run();
                    }
                    return;
                }

                // Reservation failed cleanly: restore live balance on main thread
                Runnable restoreTask = () -> {
                    minion.cancelReservedCoins();
                    storage.releasePayoutLock(minionId);
                    if (player.isOnline()) {
                        player.sendMessage(ColorUtil.color("&cFailed to secure payout ledger. Payout aborted."));
                    }
                };
                if (MinionX.getInstance() != null && MinionX.getInstance().isEnabled()) {
                    Bukkit.getScheduler().runTask(MinionX.getInstance(), restoreTask);
                } else {
                    restoreTask.run();
                }
                return;
            }

            // 4. Main-Thread: Invoke Vault API
            Runnable vaultTask = () -> {
                com.minionx.hook.EconomyHook.PayoutResult result = com.minionx.hook.EconomyHook.depositWithResult(player, normalizedCoins);

                if (result.isSuccess()) {
                    minion.commitReservedCoins();
                    storage.getPersistenceExecutor().submit(() -> {
                        storage.confirmPayoutAtomic(opId, minionId);
                    });
                    storage.releasePayoutLock(minionId);
                    if (player.isOnline()) {
                        player.sendMessage(ColorUtil.color("&aCollected &6" + String.format("%,.1f", normalizedCoins) + " coins &afrom Automated Shipping!"));
                    }
                } else if (result.isDefinitiveFailure()) {
                    storage.getPersistenceExecutor().submit(() -> {
                        boolean rolledBack = storage.rollbackPayoutAtomic(opId, minionId, normalizedCoins);
                        Runnable mainRestore = () -> {
                            if (rolledBack) {
                                minion.cancelReservedCoins();
                            }
                            storage.releasePayoutLock(minionId);
                            if (player.isOnline()) {
                                player.sendMessage(ColorUtil.color("&cEconomy deposit failed (" + result.errorMessage() + "). Coins restored to minion."));
                            }
                        };
                        if (MinionX.getInstance() != null && MinionX.getInstance().isEnabled()) {
                            Bukkit.getScheduler().runTask(MinionX.getInstance(), mainRestore);
                        } else {
                            mainRestore.run();
                        }
                    });
                } else {
                    // Ambiguous error or timeout: commit reserved coins (permanently deducted) and mark PAYOUT_AMBIGUOUS
                    minion.commitReservedCoins();
                    storage.getPersistenceExecutor().submit(() -> {
                        storage.ambiguousPayoutAtomic(opId);
                    });
                    storage.releasePayoutLock(minionId);
                    if (player.isOnline()) {
                        player.sendMessage(ColorUtil.color("&cEconomy transaction was ambiguous. Payout locked for administrator audit."));
                    }
                }
            };

            if (MinionX.getInstance() != null && MinionX.getInstance().isEnabled()) {
                Bukkit.getScheduler().runTask(MinionX.getInstance(), vaultTask);
            } else {
                vaultTask.run();
            }
        });
    }

    public static boolean canHoldAll(org.bukkit.inventory.PlayerInventory inv, List<ItemStack> items) {
        if (items == null || items.isEmpty()) return true;
        ItemStack[] storage = inv.getStorageContents();
        ItemStack[] sim = new ItemStack[storage.length];
        for (int i = 0; i < storage.length; i++) {
            sim[i] = storage[i] != null ? storage[i].clone() : null;
        }

        for (ItemStack item : items) {
            if (item == null || item.getType() == Material.AIR) continue;
            ItemStack rem = item.clone();

            for (int i = 0; i < sim.length; i++) {
                if (sim[i] != null && sim[i].isSimilar(rem)) {
                    int space = sim[i].getMaxStackSize() - sim[i].getAmount();
                    if (space > 0) {
                        int toAdd = Math.min(space, rem.getAmount());
                        sim[i].setAmount(sim[i].getAmount() + toAdd);
                        rem.setAmount(rem.getAmount() - toAdd);
                        if (rem.getAmount() <= 0) break;
                    }
                }
            }

            if (rem.getAmount() > 0) {
                for (int i = 0; i < sim.length; i++) {
                    if (sim[i] == null || sim[i].getType() == Material.AIR) {
                        int toAdd = Math.min(rem.getMaxStackSize(), rem.getAmount());
                        ItemStack added = rem.clone();
                        added.setAmount(toAdd);
                        sim[i] = added;
                        rem.setAmount(rem.getAmount() - toAdd);
                        if (rem.getAmount() <= 0) break;
                    }
                }
            }

            if (rem.getAmount() > 0) {
                return false;
            }
        }
        return true;
    }

    private boolean hasStackableRoom(Player player, Minion minion) {
        for (ItemStack item : minion.getInventory()) {
            if (item != null && item.getType() != Material.AIR) {
                for (ItemStack pItem : player.getInventory().getStorageContents()) {
                    if (pItem != null && pItem.isSimilar(item) && pItem.getAmount() < pItem.getMaxStackSize()) {
                        return true;
                    }
                }
            }
        }
        for (ItemStack item : minion.getStorageContents()) {
            if (item != null && item.getType() != Material.AIR) {
                for (ItemStack pItem : player.getInventory().getStorageContents()) {
                    if (pItem != null && pItem.isSimilar(item) && pItem.getAmount() < pItem.getMaxStackSize()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void handleFuelSlot(Player player, Minion minion, InventoryClickEvent event) {
        ItemStack inHand = event.getCursor();
        if (inHand == null || inHand.getType() == Material.AIR) {
            inHand = player.getItemOnCursor();
        }
        CustomFuel fuel = FuelRegistry.fromItemStack(inHand);

        // A. Player has fuel item on cursor -> Deposit / Stack
        if (fuel != null) {
            int depositAmount = event.isRightClick() ? 1 : inHand.getAmount();
            handleFuelDeposit(player, minion, inHand, depositAmount);
            if (inHand.getAmount() <= 0) {
                player.setItemOnCursor(new ItemStack(Material.AIR));
            } else {
                player.setItemOnCursor(inHand);
            }
            return;
        }

        // B. Cursor is empty (AIR) -> Retrieve / Remove active fuel
        if (minion.isFuelActive()) {
            CustomFuel currentFuel = minion.getCustomFuel();
            if (currentFuel == null) {
                minion.setFuel((CustomFuel) null);
                MinionGUI.open(player, minion);
                return;
            }

            if (currentFuel.isPermanent()) {
                minion.setFuel((CustomFuel) null);
                player.getInventory().addItem(MinionItemFactory.createFuelItem(currentFuel));
                player.sendMessage(ColorUtil.color("&eRemoved " + currentFuel.getDisplayName() + " &efuel from minion."));
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
                MinionGUI.open(player, minion);
                return;
            }

            // Temporary fuel removal
            boolean destroyOnRemoval = MinionX.getInstance().getConfig()
                    .getBoolean("settings.destroy-temporary-fuel-on-removal", true);

            if (destroyOnRemoval) {
                minion.setFuel((CustomFuel) null);
                String template = MinionX.getInstance().getConfig()
                        .getString("messages.fuel-burned-out", "&cThe active %fuel% burned out and was consumed!");
                player.sendMessage(ColorUtil.color(template.replace("%fuel%", currentFuel.getDisplayName())));
                player.playSound(player.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1.0f, 1.2f);
            } else {
                minion.setFuel((CustomFuel) null);
                player.getInventory().addItem(MinionItemFactory.createFuelItem(currentFuel));
                player.sendMessage(ColorUtil.color("&eRemoved " + currentFuel.getDisplayName() + " &efuel from minion."));
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
            }

            MinionGUI.open(player, minion);
        }
    }

    private void handleFuelDeposit(Player player, Minion minion, ItemStack fuelItem, int amount) {
        CustomFuel fuel = FuelRegistry.fromItemStack(fuelItem);
        if (fuel == null || amount <= 0) return;

        if (fuel.isPermanent()) {
            if (minion.isFuelPermanent()) {
                String msg = MinionX.getInstance().getConfig()
                        .getString("messages.cannot-stack-permanent-fuel", "&cThis minion already has permanent fuel installed!");
                player.sendMessage(ColorUtil.color(msg));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }

            handlePreviousFuelRemoval(player, minion);
            consumeItem(fuelItem, 1);
            minion.setFuel(fuel, 1);
            player.sendMessage(ColorUtil.color("&aEquipped " + fuel.getDisplayName() + " &a(Permanent)! Speed boosted by +" + (int) (fuel.getSpeedBuff() * 100) + "%."));
            player.playSound(player.getLocation(), Sound.BLOCK_BREWING_STAND_BREW, 1.0f, 1.2f);
            MinionGUI.open(player, minion);
            return;
        }

        int actualDeposit = Math.min(amount, fuelItem.getAmount());
        if (actualDeposit <= 0) return;

        // If minion already has same active temporary fuel -> extend duration!
        if (minion.isFuelActive() && minion.getFuelId() != null && minion.getFuelId().equalsIgnoreCase(fuel.getId())) {
            minion.extendFuel(fuel, actualDeposit);
            consumeItem(fuelItem, actualDeposit);

            String template = MinionX.getInstance().getConfig()
                    .getString("messages.fuel-extended", "&aAdded &f%amount%x %fuel%&a! Fuel duration extended to &e%time%&a.");
            String msg = template.replace("%amount%", String.valueOf(actualDeposit))
                    .replace("%fuel%", fuel.getDisplayName())
                    .replace("%time%", minion.getFormattedFuelTimeRemaining());
            player.sendMessage(ColorUtil.color(msg));
            player.playSound(player.getLocation(), Sound.BLOCK_BREWING_STAND_BREW, 1.0f, 1.2f);
            MinionGUI.open(player, minion);
            return;
        }

        // Different fuel or empty slot -> install new fuel
        handlePreviousFuelRemoval(player, minion);
        consumeItem(fuelItem, actualDeposit);
        minion.setFuel(fuel, actualDeposit);

        String template = MinionX.getInstance().getConfig()
                .getString("messages.fuel-extended", "&aEquipped &f%amount%x %fuel%&a! Total duration: &e%time%&a.");
        String msg = template.replace("%amount%", String.valueOf(actualDeposit))
                .replace("%fuel%", fuel.getDisplayName())
                .replace("%time%", minion.getFormattedFuelTimeRemaining());
        player.sendMessage(ColorUtil.color(msg));
        player.playSound(player.getLocation(), Sound.BLOCK_BREWING_STAND_BREW, 1.0f, 1.2f);
        MinionGUI.open(player, minion);
    }

    private void handlePreviousFuelRemoval(Player player, Minion minion) {
        if (!minion.isFuelActive()) return;
        CustomFuel oldFuel = minion.getCustomFuel();
        if (oldFuel == null) return;

        if (oldFuel.isPermanent()) {
            player.getInventory().addItem(MinionItemFactory.createFuelItem(oldFuel));
            player.sendMessage(ColorUtil.color("&eReturned previous " + oldFuel.getDisplayName() + " &eto your inventory."));
        } else {
            boolean destroyOnRemoval = MinionX.getInstance().getConfig()
                    .getBoolean("settings.destroy-temporary-fuel-on-removal", true);
            if (destroyOnRemoval) {
                String template = MinionX.getInstance().getConfig()
                        .getString("messages.fuel-burned-out", "&cThe previous %fuel% burned out and was consumed!");
                player.sendMessage(ColorUtil.color(template.replace("%fuel%", oldFuel.getDisplayName())));
                player.playSound(player.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.8f, 1.2f);
            } else {
                player.getInventory().addItem(MinionItemFactory.createFuelItem(oldFuel));
            }
        }
    }

    private void handleUpgradeShiftClick(Player player, Minion minion, ItemStack clicked, CustomUpgrade upgrade) {
        if (minion.getCustomUpgrade1() == null) {
            consumeItem(clicked, 1);
            minion.setUpgrade1(upgrade);
            player.sendMessage(ColorUtil.color("&aEquipped " + upgrade.getDisplayName() + "!"));
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 1.5f);
            MinionGUI.open(player, minion);
        } else if (minion.getCustomUpgrade2() == null) {
            consumeItem(clicked, 1);
            minion.setUpgrade2(upgrade);
            player.sendMessage(ColorUtil.color("&aEquipped " + upgrade.getDisplayName() + "!"));
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 1.5f);
            MinionGUI.open(player, minion);
        } else {
            player.sendMessage(ColorUtil.color("&cUpgrade slots are full!"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
        }
    }

    private void handleShippingShiftClick(Player player, Minion minion, ItemStack clicked, CustomUpgrade upgrade) {
        if (minion.getShippingId() == null) {
            consumeItem(clicked, 1);
            minion.setShippingId(upgrade.getId());
            player.sendMessage(ColorUtil.color("&aEquipped &f" + upgrade.getDisplayName() + " &ain Automated Shipping!"));
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_LOCKED, 1.0f, 1.2f);
            MinionGUI.open(player, minion);
        } else {
            player.sendMessage(ColorUtil.color("&cAutomated Shipping slot is already occupied!"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
        }
    }

    private void consumeItem(ItemStack item, int amount) {
        if (item.getAmount() <= amount) {
            item.setAmount(0);
        } else {
            item.setAmount(item.getAmount() - amount);
        }
    }

    private void handleUpgradeSlot(Player player, Minion minion, int slotNum, InventoryClickEvent event) {
        ItemStack inHand = event.getCursor();
        if (inHand == null || inHand.getType() == Material.AIR) {
            inHand = player.getItemOnCursor();
        }

        CustomUpgrade upgrade = UpgradeRegistry.fromItemStack(inHand);

        // A. Installing upgrade from cursor
        if (upgrade != null) {
            if (upgrade.isAutoSeller()) {
                player.sendMessage(ColorUtil.color("&cAuto-Sellers (Hoppers) must be placed in the Automated Shipping slot (Slot 28)!"));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }

            CustomUpgrade previous = slotNum == 1 ? minion.getCustomUpgrade1() : minion.getCustomUpgrade2();
            if (previous != null) {
                player.getInventory().addItem(MinionItemFactory.createUpgradeItem(previous));
                player.sendMessage(ColorUtil.color("&eReturned previous " + previous.getDisplayName() + " &eto your inventory."));
            }

            if (slotNum == 1) minion.setUpgrade1(upgrade);
            else minion.setUpgrade2(upgrade);

            if (inHand.getAmount() > 1) {
                inHand.setAmount(inHand.getAmount() - 1);
                player.setItemOnCursor(inHand);
            } else {
                player.setItemOnCursor(new ItemStack(Material.AIR));
            }

            player.sendMessage(ColorUtil.color("&aEquipped " + upgrade.getDisplayName() + "!"));
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 1.5f);
            MinionGUI.open(player, minion);
            return;
        }

        // B. Cursor empty -> Removing existing upgrade
        CustomUpgrade current = slotNum == 1 ? minion.getCustomUpgrade1() : minion.getCustomUpgrade2();
        if (current != null) {
            if (slotNum == 1) minion.setUpgrade1((CustomUpgrade) null);
            else minion.setUpgrade2((CustomUpgrade) null);

            player.getInventory().addItem(MinionItemFactory.createUpgradeItem(current));
            player.sendMessage(ColorUtil.color("&eRemoved " + current.getDisplayName() + "."));
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
            MinionGUI.open(player, minion);
        }
    }

    private void handleStorageSlotClick(Player player, Minion minion, int listIndex) {
        if (listIndex >= 0 && listIndex < minion.getInventory().size()) {
            ItemStack item = minion.getInventory().remove(listIndex);
            int amount = item.getAmount();
            Material itemType = item.getType();

            HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);
            for (ItemStack left : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), left);
            }
            CollectionCommandHandler.executeCollectionCommands(player, minion, itemType, amount);
            com.minionx.hook.CollectionsHook.recordCollection(player, minion, itemType, amount);

            int xp = minion.claimStoredXp();
            if (xp > 0) {
                player.giveExp(xp);
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
            }

            grantSkillRewards(player, minion, amount);

            minion.wakeUp();
            if (minion.getState() == MinionState.FULL) {
                minion.setState(MinionState.IDLE);
                minion.setLastActionTime(System.currentTimeMillis());
            }
            MinionHologram.updateHologram(minion);
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
            MinionGUI.open(player, minion);
        }
    }

    private void grantSkillRewards(Player player, Minion minion, int amount) {
        String typeId = minion.getCustomDefinitionId() != null
                ? minion.getCustomDefinitionId()
                : (minion.getType() != null ? minion.getType().name().toLowerCase() : "cobblestone");
        String skill = MinionX.getInstance().getConfig().getString("skill-rewards.types." + typeId + ".skill");
        double xpPer = MinionX.getInstance().getConfig().getDouble("skill-rewards.types." + typeId + ".xp-per-item", 0.0);
        if (skill != null && xpPer > 0) {
            SkillHook.addSkillXp(player, skill, xpPer * amount);
        }
    }
}
