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
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;

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
            player.sendMessage(ColorUtil.color("&e----------------------------------------"));
            player.sendMessage(ColorUtil.color("&6&lMinion Ideal Layout Guide"));
            player.sendMessage(ColorUtil.color("&7To function optimally, this minion requires a"));
            player.sendMessage(ColorUtil.color("&a5x5 square area &7centered around the minion"));
            player.sendMessage(ColorUtil.color("&7on the same ground level."));
            player.sendMessage(ColorUtil.color("&7Keep the area flat and free of obstacles!"));
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
            MinionSkin oldSkin = minion.getSkin();
            minion.setSkinId(null);
            ItemStack skinItem = com.minionx.item.MinionItemFactory.createSkinItem(oldSkin);
            java.util.HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(skinItem);
            for (ItemStack item : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), item);
            }
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
            MinionSkin oldSkin = minion.getSkin();
            ItemStack oldItem = com.minionx.item.MinionItemFactory.createSkinItem(oldSkin);
            java.util.HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(oldItem);
            for (ItemStack item : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), item);
            }
        }

        minion.setSkinId(skinId);
        skinItem.setAmount(skinItem.getAmount() - 1);
        com.minionx.visual.MinionVisual.spawnOrUpdate(minion);
        MinionGUI.open(player, minion);
        player.sendMessage(ColorUtil.getPrefix() + ColorUtil.color("&aApplied &d" + newSkin.getDisplayName() + " &ato minion!"));
        player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 1.0f, 1.2f);
    }

    private void handleCollectAll(Player player, Minion minion) {
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

        // Fire MinionCollectionEvent
        com.minionx.api.event.MinionCollectionEvent collectEvent = new com.minionx.api.event.MinionCollectionEvent(
                player, minion, minion.getInventory(), minion.getStoredXp());
        org.bukkit.Bukkit.getPluginManager().callEvent(collectEvent);
        if (collectEvent.isCancelled()) {
            return;
        }

        int collectedCount = 0;
        // 1. Collect from minion internal inventory
        for (ItemStack item : minion.getInventory()) {
            if (item == null || item.getType() == Material.AIR) continue;
            int amount = item.getAmount();
            Material itemType = item.getType();
            collectedCount += amount;
            HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);
            for (ItemStack left : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), left);
            }
            CollectionCommandHandler.executeCollectionCommands(player, minion, itemType, amount);
            com.minionx.hook.CollectionsHook.recordCollection(player, minion, itemType, amount);
        }
        minion.getInventory().clear();

        // 2. Collect from linked minion storage chest
        for (ItemStack item : minion.getStorageContents()) {
            if (item == null || item.getType() == Material.AIR) continue;
            int amount = item.getAmount();
            Material itemType = item.getType();
            collectedCount += amount;
            HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);
            for (ItemStack left : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), left);
            }
            CollectionCommandHandler.executeCollectionCommands(player, minion, itemType, amount);
            com.minionx.hook.CollectionsHook.recordCollection(player, minion, itemType, amount);
        }
        minion.getStorageContents().clear();

        // 3. Collect coins from Automated Shipping hopper
        if (minion.getShippingCoins() > 0) {
            double coins = minion.claimShippingCoins();
            if (com.minionx.hook.EconomyHook.hasEconomy()) {
                com.minionx.hook.EconomyHook.deposit(player, coins);
            }
            player.sendMessage(ColorUtil.color("&aCollected &6" + String.format("%,.1f", coins) + " coins &afrom Automated Shipping!"));
        }

        if (minion.getState() == MinionState.FULL) {
            minion.setState(MinionState.IDLE);
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

        MinionGUI.open(player, minion);
    }

    private void handleShippingSlotClick(Player player, Minion minion, InventoryClickEvent event) {
        ItemStack cursor = event.getCursor();

        // 1. If a shipping hopper is currently equipped:
        if (minion.getShippingId() != null) {
            if (event.isShiftClick()) {
                CustomUpgrade hopper = minion.getShippingUpgrade();
                minion.setShippingId(null);

                // Collect accumulated coins
                if (minion.getShippingCoins() > 0) {
                    double coins = minion.claimShippingCoins();
                    if (com.minionx.hook.EconomyHook.hasEconomy()) {
                        com.minionx.hook.EconomyHook.deposit(player, coins);
                    }
                    player.sendMessage(ColorUtil.color("&aCollected &6" + String.format("%,.1f", coins) + " coins &afrom Automated Shipping!"));
                }

                if (hopper != null) {
                    giveOrDrop(player, MinionItemFactory.createUpgradeItem(hopper));
                    player.sendMessage(ColorUtil.color("&aRemoved Automated Shipping hopper (&f" + hopper.getDisplayName() + "&a)!"));
                }
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
                MinionGUI.open(player, minion);
                return;
            } else {
                // Regular click: collect coins
                if (minion.getShippingCoins() > 0) {
                    double coins = minion.claimShippingCoins();
                    if (com.minionx.hook.EconomyHook.hasEconomy()) {
                        com.minionx.hook.EconomyHook.deposit(player, coins);
                        player.sendMessage(ColorUtil.color("&aCollected &6" + String.format("%,.1f", coins) + " coins &afrom Automated Shipping!"));
                    } else {
                        player.sendMessage(ColorUtil.color("&aCollected &6" + String.format("%,.1f", coins) + " coins &7(Vault not installed)."));
                    }
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

        // 1. Anti-dupe check: verify minion is still registered (prevents rapid double-click packet dupe)
        if (MinionX.getInstance().getMinionManager().getMinionById(minion.getMinionId()) == null) {
            return;
        }

        if (QuarantineManager.isQuarantined() && !QuarantineManager.canBypass(player)) {
            player.sendMessage(ColorUtil.color("&c[Quarantine] Minion pickup is disabled during emergency quarantine mode!"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // Check if player has at least 1 free inventory slot
        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage(ColorUtil.color("&cYour inventory is full! Make space before picking up this minion."));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // Fire MinionPickupEvent
        com.minionx.api.event.MinionPickupEvent pickupEvent = new com.minionx.api.event.MinionPickupEvent(player, minion);
        org.bukkit.Bukkit.getPluginManager().callEvent(pickupEvent);
        if (pickupEvent.isCancelled()) {
            return;
        }

        // 2. Unregister from MinionManager FIRST so no concurrent events or clicks can trigger pickup twice
        MinionX.getInstance().getMinionManager().unregister(minion);

        // If minion has a linked storage chest, remove it from the world and drop storage item + contents
        MinionStorageListener.removeStorageOnMinionRemoval(minion);

        // Return stored inventory items
        for (ItemStack item : minion.getInventory()) {
            HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);
            for (ItemStack left : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), left);
            }
        }
        minion.getInventory().clear();

        // Return permanent fuel if active, or temporary fuel if configured to not destroy
        if (minion.isFuelActive()) {
            CustomFuel fuel = minion.getCustomFuel();
            if (fuel != null) {
                if (fuel.isPermanent()) {
                    giveOrDrop(player, MinionItemFactory.createFuelItem(fuel));
                } else {
                    boolean destroyOnRemoval = MinionX.getInstance().getConfig()
                            .getBoolean("settings.destroy-temporary-fuel-on-removal", true);
                    if (!destroyOnRemoval) {
                        giveOrDrop(player, MinionItemFactory.createFuelItem(fuel));
                    }
                }
            }
        }

        // Return upgrades
        if (minion.getCustomUpgrade1() != null) {
            giveOrDrop(player, MinionItemFactory.createUpgradeItem(minion.getCustomUpgrade1()));
        }
        if (minion.getCustomUpgrade2() != null) {
            giveOrDrop(player, MinionItemFactory.createUpgradeItem(minion.getCustomUpgrade2()));
        }

        // Claim stored XP
        int xp = minion.claimStoredXp();
        if (xp > 0) {
            player.giveExp(xp);
        }

        // Give placeable minion item
        ItemStack minionItem;
        if (minion.getCustomDefinitionId() != null) {
            MinionDefinition def = MinionRegistry.get(minion.getCustomDefinitionId());
            minionItem = MinionItemFactory.createCustomMinionItem(def, minion.getTier(), minion.getTotalHarvested());
        } else {
            minionItem = MinionItemFactory.createMinionItem(minion.getType(), minion.getTier(), minion.getTotalHarvested());
        }
        giveOrDrop(player, minionItem);

        player.sendMessage(ColorUtil.color("&eYou picked up your " + minion.getDisplayName() + " Minion!"));
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 0.8f);
    }

    private void giveOrDrop(Player player, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return;
        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        for (ItemStack left : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), left);
        }
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

            if (minion.getState() == MinionState.FULL) {
                minion.setState(MinionState.IDLE);
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
