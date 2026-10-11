package com.skyblock.minions.gui;

import com.skyblock.minions.api.CustomMinionStorage;
import com.skyblock.minions.core.Minion;
import com.skyblock.minions.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * Builds and renders the 27-slot chest GUI for an attached Minion Storage.
 * Symmetrically places unlocked slots and fills locked slots with dark stained glass panes.
 */
public class MinionStorageGUI {

    public static void open(Player player, Minion minion) {
        open(player, minion, null);
    }

    public static void open(Player player, Minion minion, CustomMinionStorage storageOverride) {
        CustomMinionStorage storage = storageOverride != null ? storageOverride : (minion != null ? minion.getLinkedStorage() : null);
        int capacity = storage != null ? storage.getCapacity() : 9;
        String title = storage != null ? ColorUtil.color(storage.getDisplayName() + " &8(" + capacity + " Slots)") : ColorUtil.color("&8Minion Storage");

        MinionStorageHolder holder = new MinionStorageHolder(minion);
        Inventory inv = Bukkit.createInventory(holder, 27, title);

        List<Integer> unlockedSlots = SymmetricalSlotCalculator.getSlots(capacity);
        ItemStack lockedPane = createLockedPane();

        // Fill all 27 slots: locked pane if not unlocked
        for (int i = 0; i < 27; i++) {
            if (!unlockedSlots.contains(i)) {
                inv.setItem(i, lockedPane);
            }
        }

        // Place stored items into unlocked slots if minion exists
        if (minion != null) {
            List<ItemStack> storedItems = minion.getStorageContents();
            for (int i = 0; i < unlockedSlots.size(); i++) {
                int slot = unlockedSlots.get(i);
                if (i < storedItems.size()) {
                    inv.setItem(slot, storedItems.get(i).clone());
                } else {
                    inv.setItem(slot, new ItemStack(Material.AIR));
                }
            }
        }

        player.openInventory(inv);
    }

    private static ItemStack createLockedPane() {
        ItemStack item = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorUtil.color("&8Locked Slot"));
            meta.setLore(List.of(
                    ColorUtil.color("&7This slot is unavailable in this"),
                    ColorUtil.color("&7storage tier. Upgrade storage capacity"),
                    ColorUtil.color("&7to unlock additional slots!")
            ));
            item.setItemMeta(meta);
        }
        return item;
    }
}
