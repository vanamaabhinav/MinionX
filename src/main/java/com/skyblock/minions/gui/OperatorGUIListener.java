package com.skyblock.minions.gui;

import com.skyblock.minions.SkyBlockMinions;
import com.skyblock.minions.api.*;
import com.skyblock.minions.item.MinionItemFactory;
import com.skyblock.minions.util.ColorUtil;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;

/**
 * Handles interactions inside the categorized MinionX Operator GUI.
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
        if (clicked == null || clicked.getType() == Material.AIR || clicked.getType() == Material.BLACK_STAINED_GLASS_PANE || clicked.getType() == Material.GRAY_STAINED_GLASS_PANE) {
            return;
        }

        int slot = event.getRawSlot();

        // 1. Main Hub View
        if (holder.getViewType() == OperatorHolder.ViewType.MAIN) {
            handleMainClick(player, slot, clicked);
            return;
        }

        // 2. Minions Catalog View
        if (holder.getViewType() == OperatorHolder.ViewType.MINIONS_CATALOG) {
            handleMinionsCatalogClick(player, slot, clicked, event);
            return;
        }

        // 3. Minion Tiers View
        if (holder.getViewType() == OperatorHolder.ViewType.MINION_TIERS) {
            handleTierClick(player, slot, clicked, event);
            return;
        }

        // 4. Fuels Catalog View
        if (holder.getViewType() == OperatorHolder.ViewType.FUELS_CATALOG) {
            handleFuelsCatalogClick(player, slot, clicked, event);
            return;
        }

        // 5. Upgrades Catalog View
        if (holder.getViewType() == OperatorHolder.ViewType.UPGRADES_CATALOG) {
            handleUpgradesCatalogClick(player, slot, clicked, event);
            return;
        }

        // 6. Kits Catalog View
        if (holder.getViewType() == OperatorHolder.ViewType.KITS_CATALOG) {
            handleKitsCatalogClick(player, slot, clicked);
        }
    }

    private void handleMainClick(Player player, int slot, ItemStack clicked) {
        if (slot == 20) {
            OperatorGUI.openMinionsCatalog(player);
        } else if (slot == 22) {
            OperatorGUI.openFuelsCatalog(player);
        } else if (slot == 24) {
            OperatorGUI.openUpgradesCatalog(player);
        } else if (slot == 30) {
            OperatorGUI.openKitsCatalog(player);
        } else if (slot == 32) {
            MinionCreatorGUI.open(player, null);
        } else if (slot == 45 && clicked.getType() == Material.BARRIER) {
            player.closeInventory();
        } else if (slot == 53 && clicked.getType() == Material.REPEATER) {
            SkyBlockMinions.getInstance().reloadConfig();
            SkyBlockMinions.getInstance().loadRegistries();
            player.sendMessage(ColorUtil.color("&6[MinionX] &aConfiguration and registries reloaded successfully."));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
            OperatorGUI.openMain(player);
        }
    }

    private void handleMinionsCatalogClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        if (slot == 49 && clicked.getType() == Material.ARROW) {
            OperatorGUI.openMain(player);
            return;
        }

        MinionType type = null;
        if (slot == 20) type = MinionType.COBBLESTONE;
        else if (slot == 22) type = MinionType.IRON;
        else if (slot == 24) type = MinionType.DIAMOND;

        if (type != null) {
            if (event.isRightClick()) {
                OperatorGUI.openTierBrowser(player, type);
            } else {
                int amount = event.isShiftClick() ? 16 : 1;
                giveItem(player, MinionItemFactory.createMinionItem(type, 11, 0), amount);
            }
        }
    }

    private void handleTierClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        if (slot == 49 && clicked.getType() == Material.ARROW) {
            OperatorGUI.openMinionsCatalog(player);
            return;
        }

        if (clicked.getType() == Material.CHEST) {
            return;
        }

        int amount = event.isShiftClick() ? 16 : 1;
        giveItem(player, clicked, amount);
    }

    private void handleFuelsCatalogClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        if (slot == 49 && clicked.getType() == Material.ARROW) {
            OperatorGUI.openMain(player);
            return;
        }

        if (clicked.getType() == Material.LAVA_BUCKET && slot == 4) {
            return;
        }

        int amount = event.isShiftClick() ? 64 : 1;
        giveItem(player, clicked, amount);
    }

    private void handleUpgradesCatalogClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        if (slot == 49 && clicked.getType() == Material.ARROW) {
            OperatorGUI.openMain(player);
            return;
        }

        if (clicked.getType() == Material.NETHER_STAR && slot == 4) {
            return;
        }

        int amount = event.isShiftClick() ? 64 : 1;
        giveItem(player, clicked, amount);
    }

    private void handleKitsCatalogClick(Player player, int slot, ItemStack clicked) {
        if (slot == 49 && clicked.getType() == Material.ARROW) {
            OperatorGUI.openMain(player);
            return;
        }

        if (slot == 20) {
            giveItem(player, MinionItemFactory.createMinionItem(MinionType.COBBLESTONE, 1, 0), 1);
            giveItem(player, MinionItemFactory.createFuelItem(FuelRegistry.get("coal")), 1);
            giveItem(player, MinionItemFactory.createUpgradeItem(UpgradeRegistry.get("auto_smelter")), 1);
            player.sendMessage(ColorUtil.color("&6[MinionX] &aReceived: &fCobblestone Starter Kit&a."));
        } else if (slot == 22) {
            giveItem(player, MinionItemFactory.createMinionItem(MinionType.IRON, 5, 0), 1);
            giveItem(player, MinionItemFactory.createFuelItem(FuelRegistry.get("enchanted_charcoal")), 1);
            giveItem(player, MinionItemFactory.createUpgradeItem(UpgradeRegistry.get("auto_smelter")), 1);
            giveItem(player, MinionItemFactory.createUpgradeItem(UpgradeRegistry.get("compactor")), 1);
            player.sendMessage(ColorUtil.color("&6[MinionX] &aReceived: &fIron Automaton Kit&a."));
        } else if (slot == 24) {
            giveItem(player, MinionItemFactory.createMinionItem(MinionType.DIAMOND, 11, 0), 1);
            giveItem(player, MinionItemFactory.createFuelItem(FuelRegistry.get("enchanted_lava_bucket")), 1);
            giveItem(player, MinionItemFactory.createUpgradeItem(UpgradeRegistry.get("auto_smelter")), 1);
            giveItem(player, MinionItemFactory.createUpgradeItem(UpgradeRegistry.get("diamond_spreading")), 1);
            player.sendMessage(ColorUtil.color("&6[MinionX] &aReceived: &fDiamond Maxed Producer Kit&a."));
        }
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

        player.sendMessage(ColorUtil.color("&6[MinionX] &aReceived: &f" + displayName + " &7(x" + amount + ")&a."));
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.2f);
    }
}
