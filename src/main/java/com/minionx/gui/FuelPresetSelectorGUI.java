package com.minionx.gui;

import com.minionx.api.CustomFuel;
import com.minionx.api.FuelRegistry;
import com.minionx.item.MinionItemFactory;
import com.minionx.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI for browsing and selecting existing preset fuels to customize in Fuel Studio.
 */
@SuppressWarnings("deprecation")
public class FuelPresetSelectorGUI {

    public static final String TITLE = ColorUtil.color("&8» &6&lSelect Preset Fuel to Edit");

    private static final int[] CONTENT_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    public static void open(Player player, FuelCreatorSession session) {
        FuelPresetSelectorHolder holder = new FuelPresetSelectorHolder(session);
        Inventory inv = Bukkit.createInventory(holder, 54, TITLE);

        ItemStack border = createPane(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) {
            inv.setItem(i, border);
        }

        inv.setItem(4, createNamedItem(Material.KNOWLEDGE_BOOK, ColorUtil.color("&6&lAvailable Preset Fuels"),
                List.of(ColorUtil.color("&7Click any preset fuel below to load it"),
                        ColorUtil.color("&7into the Fuel Creator Studio for customization!"))));

        int idx = 0;
        for (CustomFuel fuel : FuelRegistry.getAll()) {
            if (idx >= CONTENT_SLOTS.length) break;
            ItemStack fuelItem = MinionItemFactory.createFuelItem(fuel);
            ItemMeta meta = fuelItem.getItemMeta();
            if (meta != null) {
                List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                lore.add(ColorUtil.color("&7"));
                lore.add(ColorUtil.color("&eClick to customize in Fuel Studio!"));
                meta.setLore(lore);
                fuelItem.setItemMeta(meta);
            }
            inv.setItem(CONTENT_SLOTS[idx++], fuelItem);
        }

        inv.setItem(49, createNamedItem(Material.ARROW, ColorUtil.color("&cBack to Fuel Studio"),
                List.of(ColorUtil.color("&7Return to the Fuel Creator Studio."))));

        player.openInventory(inv);
    }

    private static ItemStack createPane(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorUtil.color(name));
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack createNamedItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorUtil.color(name));
            if (lore != null) {
                meta.setLore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}
