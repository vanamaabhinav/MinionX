package com.minionx.gui;

import com.minionx.item.MinionItemFactory;
import com.minionx.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * 54-slot In-Game Custom Minion Fuel Creator Studio GUI.
 */
@SuppressWarnings("deprecation")
public class FuelCreatorGUI {

    public static final String TITLE = ColorUtil.color("&8&lMinion&c&lX &8- Fuel Creator Studio");

    public static final Material[] PRESET_MATERIALS = {
            Material.BLAZE_POWDER,
            Material.LAVA_BUCKET,
            Material.COAL,
            Material.CHARCOAL,
            Material.FIRE_CHARGE,
            Material.DAYLIGHT_DETECTOR,
            Material.DRAGON_BREATH,
            Material.REDSTONE_BLOCK,
            Material.MAGMA_BLOCK,
            Material.NETHER_STAR,
            Material.SOUL_CAMPFIRE,
            Material.BLAZE_ROD,
            Material.GLOWSTONE_DUST
    };

    public static void open(Player player, FuelCreatorSession session) {
        if (session == null) {
            session = new FuelCreatorSession("custom_fuel");
        }

        FuelCreatorHolder holder = new FuelCreatorHolder(session);
        Inventory inv = Bukkit.createInventory(holder, 54, TITLE);

        ItemStack border = createPane(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) inv.setItem(i, border);

        // Slot 2: Fuel Identity (ID & Display Name)
        inv.setItem(2, createNamedItem(Material.NAME_TAG, ColorUtil.color("&eFuel Identity"),
                List.of(ColorUtil.color("&7ID: &f" + session.getId()),
                        ColorUtil.color("&7Display Name: &f" + session.getDisplayName()),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&eClick to change ID / Name via chat"))));

        // Slot 4: Fuel Material Selector
        inv.setItem(4, createNamedItem(session.getMaterial(),
                ColorUtil.color("&aFuel Item Material"),
                List.of(ColorUtil.color("&7Current: &f" + session.getMaterial().name()),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&eClick to cycle popular materials"),
                        ColorUtil.color("&7(or cursor-drop any item to set)"))));

        // Slot 6: Custom Model Data
        inv.setItem(6, createNamedItem(Material.ITEM_FRAME,
                ColorUtil.color("&dCustom Model Data"),
                List.of(ColorUtil.color("&7Current: &e" + session.getCustomModelData()),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&eLeft-Click: &a+1  &7|  &eRight-Click: &c-1"),
                        ColorUtil.color("&eShift-Left: &a+100 &7|  &eShift-Right: &cReset (0)"))));

        // Speed Multiplier Controls (Row 2: slots 19, 20, 22, 24, 25)
        inv.setItem(19, createNamedItem(Material.RED_CONCRETE, ColorUtil.color("&c-10% Speed"),
                List.of(ColorUtil.color("&7Decrease speed buff by 10%"))));
        inv.setItem(20, createNamedItem(Material.RED_STAINED_GLASS_PANE, ColorUtil.color("&c-5% Speed"),
                List.of(ColorUtil.color("&7Decrease speed buff by 5%"))));

        int percent = (int) Math.round(session.getSpeedBuff() * 100);
        inv.setItem(22, createNamedItem(Material.GOLD_INGOT,
                ColorUtil.color("&eSpeed Multiplier: &a+" + percent + "%"),
                List.of(ColorUtil.color("&7Speed Buff: &f" + String.format("%.2f", session.getSpeedBuff())),
                        ColorUtil.color("&7Effective formula: T = T_base / (1 + sum Buffs)"),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&7Use the buttons on the left & right to adjust."))));

        inv.setItem(24, createNamedItem(Material.LIME_STAINED_GLASS_PANE, ColorUtil.color("&a+5% Speed"),
                List.of(ColorUtil.color("&7Increase speed buff by 5%"))));
        inv.setItem(25, createNamedItem(Material.LIME_CONCRETE, ColorUtil.color("&a+10% Speed"),
                List.of(ColorUtil.color("&7Increase speed buff by 10%"))));

        // Duration & Aesthetic Controls (Row 3: slots 29, 31, 33)
        inv.setItem(29, createNamedItem(Material.CLOCK,
                ColorUtil.color("&eBurn Duration: " + session.getFormattedDuration()),
                List.of(ColorUtil.color("&7Minutes: &f" + session.getDurationMinutes()),
                        ColorUtil.color("&7Formatted string: &f" + session.getDurationString()),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&eClick to cycle presets:"),
                        ColorUtil.color("&730m -> 1h -> 2h -> 6h -> 12h -> 1d -> 3d -> 7d"))));

        inv.setItem(31, createNamedItem(session.isPermanent() ? Material.NETHER_STAR : Material.CAMPFIRE,
                ColorUtil.color(session.isPermanent() ? "&b&lPermanent Fuel: &aYES" : "&e&lTemporary Fuel: &cNO"),
                List.of(ColorUtil.color("&7Mode: " + (session.isPermanent() ? "&bPermanent (Never expires)" : "&eTemporary (Burns down)")),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&eClick to toggle Permanent vs Temporary"))));

        inv.setItem(33, createNamedItem(Material.EXPERIENCE_BOTTLE,
                ColorUtil.color("&dEnchantment Glint: " + (session.isGlow() ? "&aON" : "&cOFF")),
                List.of(ColorUtil.color("&7Adds glowing enchantment glint to item."),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&eClick to toggle glint"))));

        // Slot 40: Live Item Preview
        ItemStack preview = MinionItemFactory.createFuelItem(session.toCustomFuel());
        inv.setItem(40, preview);

        // Bottom Navigation & Actions (Row 5: slots 45, 49, 53)
        inv.setItem(45, createNamedItem(Material.ARROW, ColorUtil.color("&cClose Studio"),
                List.of(ColorUtil.color("&7Discard unsaved changes and close."))));

        inv.setItem(49, createNamedItem(Material.EMERALD_BLOCK, ColorUtil.color("&a&lSAVE & REGISTER FUEL"),
                List.of(ColorUtil.color("&7Persists to &ffuels.yml&7,"),
                        ColorUtil.color("&7registers globally in &fFuelRegistry&7,"),
                        ColorUtil.color("&7and gives you 1x fuel item."),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&aClick to save!"))));

        inv.setItem(53, createNamedItem(Material.CHEST, ColorUtil.color("&eGive Test Copy"),
                List.of(ColorUtil.color("&7Receive 1x test copy of current fuel"),
                        ColorUtil.color("&7without closing the studio."))));

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
        ItemStack item = new ItemStack(mat != null ? mat : Material.STONE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorUtil.color(name));
            if (lore != null) {
                meta.setLore(lore);
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }
}
