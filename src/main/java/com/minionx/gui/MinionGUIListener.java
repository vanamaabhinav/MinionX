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

                // Shift-click Upgrade item -> auto-equip into first free upgrade slot
                CustomUpgrade upgrade = UpgradeRegistry.fromItemStack(clicked);
                if (upgrade != null) {
                    event.setCancelled(true);
                    handleUpgradeShiftClick(player, minion, clicked, upgrade);
                    return;
                }

                // Shift-click Skin item -> auto-apply into skin slot
                String skinId = com.minionx.item.MinionItemFactory.getSkinId(clicked);
                if (skinId != null) {
                    event.setCancelled(true);
                    handleSkinApply(player, minion, clicked, skinId);
                    return;
                }

                // Normal player inventory item shift-click
                event.setCancelled(false);
                return;
            } else {
                // Allow normal item movement/pickup in player's own inventory
                event.setCancelled(false);
                return;
            }
        }

        // Click is within top minion GUI (0 <= slot <= 53)
        event.setCancelled(true);

        // 2. Collect All Items & Claim Stored XP (Slot 46 or 45)
        if (slot == MinionGUIConfig.getCollectAllSlot() || slot == 45) {
            handleCollectAll(player, minion);
            return;
        }

        // 3. Upgrade Minion Tier (Slot 49)
        if (slot == MinionGUIConfig.getUpgradeTierSlot()) {
            handleUpgrade(player, minion);
            return;
        }

        // 4. Pickup Minion (Slot 53)
        if (slot == MinionGUIConfig.getPickupSlot()) {
            handlePickup(player, minion);
            return;
        }

        // 5. Fuel Slot Click (Slot 9)
        if (slot == MinionGUIConfig.getFuelSlot()) {
            handleFuelSlot(player, minion, event);
            return;
        }

        // 6. Upgrade Slot 1 Click (Slot 27)
        if (slot == MinionGUIConfig.getUpgrade1Slot()) {
            handleUpgradeSlot(player, minion, 1, event);
            return;
        }

        // 7. Upgrade Slot 2 Click (Slot 36)
        if (slot == MinionGUIConfig.getUpgrade2Slot()) {
            handleUpgradeSlot(player, minion, 2, event);
            return;
        }

        // 7.5. Minion Skin Slot Click (Slot 37)
        if (slot == MinionGUIConfig.getSkinSlot()) {
            handleSkinSlot(player, minion, event);
            return;
        }

        // 8. Individual Storage Slot Clicks (Slots 12-16, 21-25, 30-34)
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
            String template = MinionX.getInstance().getConfig().getString("messages.not-enough-items", "&cYou need %cost%x %material% to upgrade to Tier %tier%!");
            String msg = template.replace("%cost%", String.valueOf(cost))
                    .replace("%material%", required.name())
                    .replace("%tier%", MinionTier.toRoman(currentTier + 1));
            player.sendMessage(ColorUtil.color(msg));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        removeItems(player, required, cost);
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
