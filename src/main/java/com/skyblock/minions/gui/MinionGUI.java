package com.skyblock.minions.gui;

import com.skyblock.minions.api.*;
import com.skyblock.minions.core.Minion;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the authentic 54-slot Hypixel SkyBlock Minion chest interface.
 */
public class MinionGUI {

    public static final String GUI_TITLE_PREFIX = "Minion: ";

    // Slot definitions
    public static final int HEAD_SLOT = 4;
    public static final int FUEL_SLOT = 9;
    public static final int SHIPPING_SLOT = 18;
    public static final int UPGRADE_SLOT_1 = 27;
    public static final int UPGRADE_SLOT_2 = 36;

    public static final int COLLECT_ALL_SLOT = 45;
    public static final int UPGRADE_MINION_SLOT = 49;
    public static final int PICKUP_SLOT = 53;

    // 15 Storage slots arranged in three rows
    public static final int[] STORAGE_SLOTS = {
            12, 13, 14, 15, 16,
            21, 22, 23, 24, 25,
            30, 31, 32, 33, 34
    };

    /**
     * Creates and opens the Minion GUI for the player.
     */
    public static void open(Player player, Minion minion) {
        String title = GUI_TITLE_PREFIX + minion.getType().getDisplayName() + " " + MinionTier.toRoman(minion.getTier());
        Inventory inv = Bukkit.createInventory(new MinionHolder(minion), 54, title);

        ItemStack filler = createPane(Material.GRAY_STAINED_GLASS_PANE, "§7");
        for (int i = 0; i < 54; i++) {
            inv.setItem(i, filler);
        }

        // 1. Minion Head (Slot 4)
        inv.setItem(HEAD_SLOT, createHeadItem(minion));

        // 2. Fuel Slot (Slot 9)
        if (minion.isFuelActive()) {
            inv.setItem(FUEL_SLOT, createActiveFuelItem(minion));
        } else {
            inv.setItem(FUEL_SLOT, createEmptyFuelItem());
        }

        // 3. Automated Shipping (Slot 18 - Cosmetic placeholder)
        inv.setItem(SHIPPING_SLOT, createPane(Material.HOPPER, "§cAutomated Shipping §7(Locked)"));

        // 4. Upgrade Slots (Slots 27 & 36)
        inv.setItem(UPGRADE_SLOT_1, createUpgradeSlotItem(minion.getCustomUpgrade1(), 1));
        inv.setItem(UPGRADE_SLOT_2, createUpgradeSlotItem(minion.getCustomUpgrade2(), 2));

        // 5. Storage Slots
        int unlockedSlots = MinionTier.getStorageSlots(minion.getTier());
        List<ItemStack> items = minion.getInventory();

        for (int i = 0; i < STORAGE_SLOTS.length; i++) {
            int slot = STORAGE_SLOTS[i];
            if (i < unlockedSlots) {
                if (i < items.size()) {
                    inv.setItem(slot, items.get(i).clone());
                } else {
                    inv.setItem(slot, new ItemStack(Material.AIR)); // Empty unlocked slot
                }
            } else {
                inv.setItem(slot, createLockedStoragePane(i + 1));
            }
        }

        // 6. Action Buttons
        inv.setItem(COLLECT_ALL_SLOT, createCollectAllItem(minion));
        inv.setItem(UPGRADE_MINION_SLOT, createUpgradeButtonItem(minion));
        inv.setItem(PICKUP_SLOT, createPickupItem());

        player.openInventory(inv);
    }

    private static ItemStack createHeadItem(Minion minion) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§a" + minion.getType().getDisplayName() + " Minion " + MinionTier.toRoman(minion.getTier()));
            List<String> lore = new ArrayList<>();
            lore.add("§7Time Between Actions: §a" + String.format("%.1f", minion.getEffectiveActionDelay()) + "s");
            lore.add("§7Total Harvested: §e" + minion.getTotalHarvested() + " items");
            lore.add("§7Storage: §e" + minion.getTotalStoredCount() + " / " + MinionTier.getMaxCapacity(minion.getTier()) + " items");
            lore.add("§7Status: " + minion.getState().getDisplayStatus());
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack createEmptyFuelItem() {
        return createNamedItem(Material.YELLOW_STAINED_GLASS_PANE, "§eFuel Slot",
                List.of("§7Place fuel here to increase", "§7your minion's speed!"));
    }

    private static ItemStack createActiveFuelItem(Minion minion) {
        CustomFuel fuel = minion.getCustomFuel();
        if (fuel == null) return createEmptyFuelItem();
        String duration = fuel.isPermanent() ? "Permanent" :
                (Math.max(0, (minion.getFuelEndTime() - System.currentTimeMillis()) / (60 * 1000L))) + "m remaining";

        return createNamedItem(fuel.getMaterial(), "§bFuel: " + fuel.getDisplayName(),
                List.of("§7Speed Boost: §a+" + (int) (fuel.getSpeedBuff() * 100) + "%",
                        "§7Duration: §e" + duration,
                        "§7",
                        "§eClick to remove fuel!"));
    }

    private static ItemStack createUpgradeSlotItem(CustomUpgrade upgrade, int slotNum) {
        if (upgrade == null) {
            return createNamedItem(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "§7Upgrade Slot " + slotNum,
                    List.of("§7Place minion upgrades here", "§7to enhance functionality!"));
        }
        List<String> lore = new ArrayList<>(upgrade.getLore());
        lore.add("§7");
        lore.add("§eClick to remove upgrade!");
        return createNamedItem(upgrade.getMaterial(), upgrade.getDisplayName(), lore);
    }

    private static ItemStack createLockedStoragePane(int slotNumber) {
        return createNamedItem(Material.RED_STAINED_GLASS_PANE, "§cLocked Storage Slot",
                List.of("§7Upgrade this minion to unlock", "§7more storage space!"));
    }

    private static ItemStack createCollectAllItem(Minion minion) {
        return createNamedItem(Material.CHEST, "§aQuick Collect All",
                List.of("§7Instantly collect all stored items",
                        "§7into your inventory.",
                        "§7",
                        "§7Items Stored: §e" + minion.getTotalStoredCount(),
                        "§eClick to collect!"));
    }

    private static ItemStack createUpgradeButtonItem(Minion minion) {
        int currentTier = minion.getTier();
        if (currentTier >= MinionTier.getMaxTier()) {
            return createNamedItem(Material.BEDROCK, "§cMax Tier Reached",
                    List.of("§7This minion has reached the", "§7maximum craftable tier (Tier XI)!"));
        }

        int nextTier = currentTier + 1;
        int cost = MinionTier.getUpgradeCost(currentTier);
        double nextSpeed = MinionTier.getActionDelay(nextTier);
        int nextSlots = MinionTier.getStorageSlots(nextTier);

        List<String> lore = new ArrayList<>();
        lore.add("§7Upgrade to Tier §e" + MinionTier.toRoman(nextTier));
        lore.add("§7");
        lore.add("§7New Action Delay: §a" + nextSpeed + "s");
        lore.add("§7New Storage: §e" + nextSlots + " slots §7(" + (nextSlots * 64) + " items)");
        lore.add("§7");
        lore.add("§7Cost: §a" + cost + " " + minion.getType().getDisplayName());
        lore.add("§7");
        lore.add("§eClick to upgrade!");

        return createNamedItem(Material.ANVIL, "§aUpgrade Minion", lore);
    }

    private static ItemStack createPickupItem() {
        return createNamedItem(Material.REDSTONE_BLOCK, "§cPickup Minion",
                List.of("§7Reclaims this minion back into your",
                        "§7inventory along with all stored items,",
                        "§7fuel, and upgrades.",
                        "§7",
                        "§cClick to pickup minion!"));
    }

    private static ItemStack createPane(Material mat, String name) {
        return createNamedItem(mat, name, List.of());
    }

    private static ItemStack createNamedItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }
}
