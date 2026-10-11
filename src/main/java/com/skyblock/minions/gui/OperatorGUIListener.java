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
            return;
        }

        // 7. Skins Catalog View
        if (holder.getViewType() == OperatorHolder.ViewType.SKINS_CATALOG) {
            handleSkinsCatalogClick(player, slot, clicked, holder, event);
        }
    }

    private void handleMainClick(Player player, int slot, ItemStack clicked) {
        if (slot == 20) {
            OperatorGUI.openMinionsCatalog(player);
        } else if (slot == 22) {
            OperatorGUI.openFuelsCatalog(player);
        } else if (slot == 24) {
            OperatorGUI.openUpgradesCatalog(player);
        } else if (slot == 28) {
            OperatorGUI.openSkinsCatalog(player, 0);
        } else if (slot == 30) {
            OperatorGUI.openKitsCatalog(player);
        } else if (slot == 32) {
            MinionCreatorGUI.open(player, null);
        } else if (slot == 48 && clicked.getType() == Material.TNT) {
            int count = SkyBlockMinions.getInstance().getMinionManager().killAndPurgeAll();
            player.sendMessage(ColorUtil.color("&6[MinionX] &aSuccessfully killed and purged " + count + " active minions, holograms, and tasks!"));
            player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.2f);
            OperatorGUI.openMain(player);
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

        // Category tabs
        if (slot == 1) { OperatorGUI.openMinionsCatalog(player, null); return; }
        if (slot == 2) { OperatorGUI.openMinionsCatalog(player, "MINER"); return; }
        if (slot == 3) { OperatorGUI.openMinionsCatalog(player, "FORAGING"); return; }
        if (slot == 4) { OperatorGUI.openMinionsCatalog(player, "FARMING"); return; }
        if (slot == 5) { OperatorGUI.openMinionsCatalog(player, "COMBAT"); return; }
        if (slot == 6) { OperatorGUI.openMinionsCatalog(player, "CUSTOM"); return; }

        String customId = MinionItemFactory.getCustomMinionId(clicked);
        MinionDefinition def = customId != null ? MinionRegistry.get(customId) : null;
        if (def == null && clicked.getType() == Material.PLAYER_HEAD && clicked.getItemMeta() != null) {
            String name = clicked.getItemMeta().getDisplayName();
            for (MinionDefinition d : MinionRegistry.getAll()) {
                if (name.contains(d.getDisplayName()) || name.toLowerCase().contains(d.getId().toLowerCase())) {
                    def = d;
                    break;
                }
            }
        }

        if (def != null) {
            if (event.getClick() == org.bukkit.event.inventory.ClickType.MIDDLE ||
                    event.getClick() == org.bukkit.event.inventory.ClickType.DROP ||
                    event.getClick() == org.bukkit.event.inventory.ClickType.CONTROL_DROP ||
                    (event.isShiftClick() && event.isRightClick())) {
                MinionCreatorGUI.open(player, MinionCreatorSession.fromDefinition(def));
                return;
            }

            if (com.skyblock.minions.hook.GeyserHook.isBedrockPlayer(player) || event.isRightClick()) {
                OperatorGUI.openTierBrowser(player, def);
            } else {
                int tier = event.isShiftClick() ? 1 : 11;
                giveItem(player, MinionItemFactory.createCustomMinionItem(def, tier, 0), 1);
            }
        }
    }

    private void handleTierClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        if (slot == 49 && clicked.getType() == Material.ARROW) {
            OperatorGUI.openMinionsCatalog(player);
            return;
        }

        if (slot == 50 && clicked.getType() == Material.ANVIL) {
            OperatorHolder holder = (OperatorHolder) event.getInventory().getHolder();
            if (holder != null) {
                Object ctx = holder.getContext();
                MinionDefinition d = null;
                if (ctx instanceof MinionDefinition md) {
                    d = md;
                } else if (ctx instanceof com.skyblock.minions.api.MinionType mt) {
                    d = MinionRegistry.get(mt.name().toLowerCase());
                }
                if (d != null) {
                    MinionCreatorGUI.open(player, MinionCreatorSession.fromDefinition(d));
                    return;
                }
            }
        }

        if (clicked.getType() == Material.CHEST || clicked.getType() == Material.BLACK_STAINED_GLASS_PANE) {
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
            player.sendMessage(ColorUtil.color("&6[MinionX] &aReceived Cobblestone Starter Kit!"));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
        } else if (slot == 22) {
            giveItem(player, MinionItemFactory.createMinionItem(MinionType.IRON, 5, 0), 1);
            giveItem(player, MinionItemFactory.createFuelItem(FuelRegistry.get("enchanted_charcoal")), 1);
            giveItem(player, MinionItemFactory.createUpgradeItem(UpgradeRegistry.get("auto_smelter")), 1);
            giveItem(player, MinionItemFactory.createUpgradeItem(UpgradeRegistry.get("compactor")), 1);
            player.sendMessage(ColorUtil.color("&6[MinionX] &aReceived Iron Automaton Kit!"));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
        } else if (slot == 24) {
            giveItem(player, MinionItemFactory.createMinionItem(MinionType.DIAMOND, 11, 0), 1);
            giveItem(player, MinionItemFactory.createFuelItem(FuelRegistry.get("enchanted_lava_bucket")), 1);
            giveItem(player, MinionItemFactory.createUpgradeItem(UpgradeRegistry.get("auto_smelter")), 1);
            giveItem(player, MinionItemFactory.createUpgradeItem(UpgradeRegistry.get("diamond_spreading")), 1);
            player.sendMessage(ColorUtil.color("&6[MinionX] &aReceived Diamond Maxed Producer Kit!"));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
        }
    }

    private void handleSkinsCatalogClick(Player player, int slot, ItemStack clicked, OperatorHolder holder, InventoryClickEvent event) {
        int page = 0;
        if (holder.getContext() instanceof Integer p) {
            page = p;
        }

        if (slot == 45 && clicked.getType() == Material.ARROW) {
            OperatorGUI.openSkinsCatalog(player, page - 1);
            return;
        }

        if (slot == 49 && clicked.getType() == Material.ARROW) {
            OperatorGUI.openMain(player);
            return;
        }

        if (slot == 53 && clicked.getType() == Material.ARROW) {
            OperatorGUI.openSkinsCatalog(player, page + 1);
            return;
        }

        if (clicked.getType() == Material.LEATHER_CHESTPLATE && slot == 4) {
            return;
        }

        String skinId = MinionItemFactory.getSkinId(clicked);
        if (skinId != null) {
            int amount = event.isShiftClick() ? 16 : 1;
            giveItem(player, clicked, amount);
        }
    }

        private void giveItem(Player player, ItemStack item, int amount) {
        if (item == null || item.getType() == Material.AIR) return;
        ItemStack toGive = item.clone();
        toGive.setAmount(amount);

        HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(toGive);
        if (!overflow.isEmpty()) {
            overflow.values().forEach(drop -> player.getWorld().dropItemNaturally(player.getLocation(), drop));
        }

        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.2f);
    }
}
