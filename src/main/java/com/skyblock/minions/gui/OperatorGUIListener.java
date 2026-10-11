package com.skyblock.minions.gui;

import com.skyblock.minions.SkyBlockMinions;
import com.skyblock.minions.api.*;
import com.skyblock.minions.item.MinionItemFactory;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;

/**
 * Handles interactions inside the MinionX Operator GUI.
 */
public class OperatorGUIListener implements Listener {

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof OperatorHolder holder)) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR || clicked.getType() == Material.GRAY_STAINED_GLASS_PANE) {
            return;
        }

        int slot = event.getRawSlot();

        // Close button (Slot 45 in main view)
        if (slot == 45 && clicked.getType() == Material.BARRIER) {
            player.closeInventory();
            return;
        }

        if (holder.getViewType() == OperatorHolder.ViewType.MAIN) {
            handleMainClick(player, slot, clicked, event);
        } else if (holder.getViewType() == OperatorHolder.ViewType.MINION_TIERS) {
            handleTierClick(player, slot, clicked, event, (MinionType) holder.getContext());
        }
    }

    private void handleMainClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        // Hot-reload config & registries
        if (slot == 53 && clicked.getType() == Material.REPEATER) {
            SkyBlockMinions.getInstance().reloadConfig();
            SkyBlockMinions.getInstance().loadRegistries();
            player.sendMessage("§6[MinionX] §aConfiguration and registries reloaded successfully.");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
            OperatorGUI.openMain(player);
            return;
        }

        // Minion heads showcase (Slots 11, 13, 15)
        if (slot == 11 || slot == 13 || slot == 15) {
            MinionType type = (slot == 11) ? MinionType.COBBLESTONE : (slot == 13 ? MinionType.IRON : MinionType.DIAMOND);
            if (event.isRightClick()) {
                OperatorGUI.openTierBrowser(player, type);
                return;
            } else {
                int amount = event.isShiftClick() ? 16 : 1;
                giveItem(player, MinionItemFactory.createMinionItem(type, 11, 0), amount);
                return;
            }
        }

        // Admin presets (Slots 38, 40, 42)
        if (slot == 38) {
            giveItem(player, MinionItemFactory.createMinionItem(MinionType.COBBLESTONE, 1, 0), 1);
            giveItem(player, MinionItemFactory.createFuelItem(FuelRegistry.get("coal")), 1);
            giveItem(player, MinionItemFactory.createUpgradeItem(UpgradeRegistry.get("auto_smelter")), 1);
            player.sendMessage("§6[MinionX] §aReceived: §fCobblestone Starter Kit§a.");
            return;
        } else if (slot == 40) {
            giveItem(player, MinionItemFactory.createMinionItem(MinionType.IRON, 5, 0), 1);
            giveItem(player, MinionItemFactory.createFuelItem(FuelRegistry.get("enchanted_charcoal")), 1);
            giveItem(player, MinionItemFactory.createUpgradeItem(UpgradeRegistry.get("auto_smelter")), 1);
            giveItem(player, MinionItemFactory.createUpgradeItem(UpgradeRegistry.get("compactor")), 1);
            player.sendMessage("§6[MinionX] §aReceived: §fIron Automaton Kit§a.");
            return;
        } else if (slot == 42) {
            giveItem(player, MinionItemFactory.createMinionItem(MinionType.DIAMOND, 11, 0), 1);
            giveItem(player, MinionItemFactory.createFuelItem(FuelRegistry.get("enchanted_lava_bucket")), 1);
            giveItem(player, MinionItemFactory.createUpgradeItem(UpgradeRegistry.get("auto_smelter")), 1);
            giveItem(player, MinionItemFactory.createUpgradeItem(UpgradeRegistry.get("diamond_spreading")), 1);
            player.sendMessage("§6[MinionX] §aReceived: §fDiamond Maxed Producer Kit§a.");
            return;
        }

        // Informational header items (Slots 2, 4, 6, 49)
        if (slot == 2 || slot == 4 || slot == 6 || slot == 49) {
            return;
        }

        // Fuels, Upgrades, or generic clicked items
        int amount = event.isShiftClick() ? 64 : 1;
        giveItem(player, clicked, amount);
    }

    private void handleTierClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event, MinionType type) {
        if (slot == 49 && clicked.getType() == Material.ARROW) {
            OperatorGUI.openMain(player);
            return;
        }

        if (clicked.getType() == Material.CHEST || clicked.getType() == Material.GRAY_STAINED_GLASS_PANE) {
            return;
        }

        int amount = event.isShiftClick() ? 16 : 1;
        giveItem(player, clicked, amount);
    }

    private void giveItem(Player player, ItemStack item, int amount) {
        if (item == null || item.getType() == Material.AIR) return;
        ItemStack toGive = item.clone();
        toGive.setAmount(amount);

        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(toGive);
        for (ItemStack extra : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), extra);
        }

        String displayName = toGive.hasItemMeta() && toGive.getItemMeta().hasDisplayName()
                ? toGive.getItemMeta().getDisplayName()
                : toGive.getType().name();

        player.sendMessage("§6[MinionX] §aReceived: §f" + displayName + " §7(x" + amount + ")§a.");
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.2f);
    }
}
