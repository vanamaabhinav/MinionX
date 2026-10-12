package com.minionx.gui;

import com.minionx.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * 54-slot In-Game Custom Minion Storage Creator Studio GUI.
 */
@SuppressWarnings("deprecation")
public class MinionStorageCreatorGUI {

    public static final String TITLE = ColorUtil.color("&8Minion Storage Creator");

    public static void open(Player player, MinionStorageCreatorSession session) {
        if (session == null) {
            session = new MinionStorageCreatorSession("custom_storage");
        }

        MinionStorageCreatorHolder holder = new MinionStorageCreatorHolder(session);
        Inventory inv = Bukkit.createInventory(holder, 54, TITLE);

        ItemStack border = createPane(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) inv.setItem(i, border);

        // Slot 2: Storage Identity (ID & Display Name)
        inv.setItem(2, createNamedItem(Material.NAME_TAG, ColorUtil.color("&eStorage Identity"),
                List.of(ColorUtil.color("&7ID: &f" + session.getId()),
                        ColorUtil.color("&7Display Name: &f" + session.getDisplayName()),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&eClick to change ID / Name via chat"))));

        // Slot 4: Item Material Selector
        inv.setItem(4, createNamedItem(session.getMaterial() != null ? session.getMaterial() : Material.CHEST,
                ColorUtil.color("&aStorage Block Material"),
                List.of(ColorUtil.color("&7Current: &f" + session.getMaterial().name()),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&eClick to cycle:"),
                        ColorUtil.color("&7CHEST -> BARREL -> ENDER_CHEST -> TRAPPED_CHEST"))));

        // Slot 6: Live Storage Preview / Test Opener
        inv.setItem(6, createNamedItem(Material.ENDER_CHEST, ColorUtil.color("&bTest GUI Layout Preview"),
                List.of(ColorUtil.color("&7Capacity: &e" + session.getCapacity() + " slots"),
                        ColorUtil.color("&7Layout: Symmetrical (Center slot 13)"),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&eClick to open test chest view!"))));

        // Capacity Controls (Row 2: slots 19, 20, 22, 24, 25)
        inv.setItem(19, createNamedItem(Material.RED_CONCRETE, ColorUtil.color("&c-5 Capacity"),
                List.of(ColorUtil.color("&7Decrease storage by 5 slots"))));
        inv.setItem(20, createNamedItem(Material.RED_STAINED_GLASS_PANE, ColorUtil.color("&c-1 Capacity"),
                List.of(ColorUtil.color("&7Decrease storage by 1 slot"))));

        inv.setItem(22, createNamedItem(Material.CHEST, ColorUtil.color("&eStorage Capacity: &b" + session.getCapacity() + " &7/ 27"),
                List.of(ColorUtil.color("&7Capacity defines how many items"),
                        ColorUtil.color("&7the storage chest can hold."),
                        ColorUtil.color("&7Locked slots are filled with dark glass panes."))));

        inv.setItem(24, createNamedItem(Material.LIME_STAINED_GLASS_PANE, ColorUtil.color("&a+1 Capacity"),
                List.of(ColorUtil.color("&7Increase storage by 1 slot"))));
        inv.setItem(25, createNamedItem(Material.LIME_CONCRETE, ColorUtil.color("&a+5 Capacity"),
                List.of(ColorUtil.color("&7Increase storage by 5 slots"))));

        // Slots 27 to 53: Mini Symmetrical Grid Visualization!
        for (int i = 0; i < 27; i++) {
            int targetSlot = 27 + i;
            if (SymmetricalSlotCalculator.isSlotUnlocked(i, session.getCapacity())) {
                inv.setItem(targetSlot, createNamedItem(Material.LIME_STAINED_GLASS_PANE,
                        ColorUtil.color("&aSlot " + i + " [UNLOCKED]"),
                        List.of(ColorUtil.color("&7Active storage slot"))));
            } else {
                inv.setItem(targetSlot, createNamedItem(Material.GRAY_STAINED_GLASS_PANE,
                        ColorUtil.color("&8Slot " + i + " [LOCKED]"),
                        List.of(ColorUtil.color("&7Locked slot"))));
            }
        }

        // Slot 27 override: Back/Close
        // We can place Save & Close in bottom corners:
        // Slot 0 and Slot 8 or Slot 18 / 26
        inv.setItem(0, createNamedItem(Material.ARROW, ColorUtil.color("&cClose Studio"),
                List.of(ColorUtil.color("&7Discard unsaved changes"))));

        inv.setItem(8, createNamedItem(Material.EMERALD_BLOCK, ColorUtil.color("&a&lSAVE & REGISTER STORAGE"),
                List.of(ColorUtil.color("&7Saves to &fstorages.yml &7and"),
                        ColorUtil.color("&7registers globally!"),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&aClick to save!"))));

        player.openInventory(inv);
    }

    private static ItemStack createNamedItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null) meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack createPane(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        }
        return item;
    }
}
