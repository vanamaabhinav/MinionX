package com.skyblock.minions.gui;

import com.skyblock.minions.api.*;
import com.skyblock.minions.item.MinionItemFactory;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the MinionX Operator interface for browsing, testing, and giving all minions, fuels, and upgrades.
 */
public class OperatorGUI {

    public static final String MAIN_TITLE = "§8MinionX — Operator Panel";

    public static void openMain(Player player) {
        OperatorHolder holder = new OperatorHolder(OperatorHolder.ViewType.MAIN);
        Inventory inv = Bukkit.createInventory(holder, 54, MAIN_TITLE);

        // Fill background borders
        ItemStack border = createPane(Material.GRAY_STAINED_GLASS_PANE, "§7");
        for (int i = 0; i < 9; i++) inv.setItem(i, border);
        for (int i = 45; i < 54; i++) inv.setItem(i, border);

        // Header Category Labels
        inv.setItem(2, createNamedItem(Material.DIAMOND_PICKAXE, "§bMinions Showcase",
                List.of("§7Left-Click to receive Tier XI.", "§7Right-Click to browse all Tiers (I–XI).")));
        inv.setItem(4, createNamedItem(Material.LAVA_BUCKET, "§6Fuel Supply",
                List.of("§7Click any fuel item to receive it.")));
        inv.setItem(6, createNamedItem(Material.NETHER_STAR, "§dUpgrades & Accessories",
                List.of("§7Click any upgrade item to receive it.")));

        // Row 1: Minion Types Showcase (Slots 11, 13, 15)
        MinionType[] types = MinionType.values();
        int[] minionSlots = {11, 13, 15};
        for (int i = 0; i < types.length && i < minionSlots.length; i++) {
            MinionType type = types[i];
            ItemStack minionHead = MinionItemFactory.createMinionItem(type, 11, 0);
            ItemMeta meta = minionHead.getItemMeta();
            if (meta != null) {
                List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                lore.add("§8§m-------------------------");
                lore.add("§a▶ Left-Click: §fGet Tier XI (Maxed)");
                lore.add("§e▶ Right-Click: §fBrowse Tiers I–XI");
                meta.setLore(lore);
                minionHead.setItemMeta(meta);
            }
            inv.setItem(minionSlots[i], minionHead);
        }

        // Row 2: Fuels (Slots 19 to 25)
        List<CustomFuel> fuels = new ArrayList<>(FuelRegistry.getAll());
        int fuelSlot = 19;
        for (CustomFuel fuel : fuels) {
            if (fuelSlot > 25) break;
            ItemStack fuelItem = MinionItemFactory.createFuelItem(fuel);
            inv.setItem(fuelSlot++, fuelItem);
        }

        // Row 3: Upgrades (Slots 28 to 34)
        List<CustomUpgrade> upgrades = new ArrayList<>(UpgradeRegistry.getAll());
        int upSlot = 28;
        for (CustomUpgrade upgrade : upgrades) {
            if (upSlot > 34) break;
            ItemStack upItem = MinionItemFactory.createUpgradeItem(upgrade);
            inv.setItem(upSlot++, upItem);
        }

        // Row 4: Complete Admin Presets (Slots 38, 40, 42)
        inv.setItem(38, createNamedItem(Material.CHEST, "§aKit: Cobblestone Starter",
                List.of("§7Includes:", "§8• §71x Cobblestone Minion I", "§8• §71x Coal Fuel", "§8• §71x Auto-Smelter", "", "§eClick to receive full kit!")));

        inv.setItem(40, createNamedItem(Material.CHEST, "§6Kit: Iron Automaton",
                List.of("§7Includes:", "§8• §71x Iron Minion V", "§8• §71x Enchanted Charcoal Fuel", "§8• §71x Auto-Smelter", "§8• §71x Compactor", "", "§eClick to receive full kit!")));

        inv.setItem(42, createNamedItem(Material.ENDER_CHEST, "§bKit: Diamond Maxed Producer",
                List.of("§7Includes:", "§8• §71x Diamond Minion XI", "§8• §71x Enchanted Lava Bucket (Permanent)", "§8• §71x Auto-Smelter", "§8• §71x Diamond Spreading", "", "§eClick to receive full kit!")));

        // Bottom Controls
        inv.setItem(45, createNamedItem(Material.BARRIER, "§cClose Menu", List.of("§7Exit the operator panel.")));
        inv.setItem(49, createNamedItem(Material.BOOK, "§eMinionX Operator Panel",
                List.of("§7Click any item to receive it directly.", "§7Shift-Click gives a stack (16/64).")));
        inv.setItem(53, createNamedItem(Material.REPEATER, "§6Reload Config & Registries",
                List.of("§7Click to instantly reload config.yml.")));

        player.openInventory(inv);
    }

    public static void openTierBrowser(Player player, MinionType type) {
        OperatorHolder holder = new OperatorHolder(OperatorHolder.ViewType.MINION_TIERS, type);
        Inventory inv = Bukkit.createInventory(holder, 54, "§8MinionX — " + type.getDisplayName() + " Tiers");

        ItemStack border = createPane(Material.GRAY_STAINED_GLASS_PANE, "§7");
        for (int i = 0; i < 9; i++) inv.setItem(i, border);
        for (int i = 45; i < 54; i++) inv.setItem(i, border);

        // Header
        inv.setItem(4, createNamedItem(Material.CHEST, "§b" + type.getDisplayName() + " Minion Tiers (I – XI)",
                List.of("§7Click any tier to receive that minion item.", "§7Shift-Click to receive a stack.")));

        // Tiers 1 through 11 in grid: slots 19..25 (Tiers 1-7), slots 29..32 (Tiers 8-11)
        int[] tierSlots = {19, 20, 21, 22, 23, 24, 25, 29, 30, 31, 32};
        for (int tier = 1; tier <= 11; tier++) {
            ItemStack minionItem = MinionItemFactory.createMinionItem(type, tier, 0);
            inv.setItem(tierSlots[tier - 1], minionItem);
        }

        // Back button
        inv.setItem(49, createNamedItem(Material.ARROW, "§c◀ Back to Overview",
                List.of("§7Return to the main operator interface.")));

        player.openInventory(inv);
    }

    private static ItemStack createPane(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack createNamedItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null) {
                meta.setLore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}
