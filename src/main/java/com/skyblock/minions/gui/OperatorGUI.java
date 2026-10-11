package com.skyblock.minions.gui;

import com.skyblock.minions.api.*;
import com.skyblock.minions.item.MinionItemFactory;
import com.skyblock.minions.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the categorized MinionX Operator interface for testing and obtaining minions, fuels, upgrades, and kits.
 */
public class OperatorGUI {

    public static final String MAIN_TITLE = ColorUtil.color("&8MinionX Operator Hub");
    public static final String MINIONS_TITLE = ColorUtil.color("&8MinionX - Minions Catalog");
    public static final String FUELS_TITLE = ColorUtil.color("&8MinionX - Fuel Supplies");
    public static final String UPGRADES_TITLE = ColorUtil.color("&8MinionX - Upgrades & Accessories");
    public static final String KITS_TITLE = ColorUtil.color("&8MinionX - Admin Kits");

    /**
     * Opens the categorized Main Hub interface.
     */
    public static void openMain(Player player) {
        OperatorHolder holder = new OperatorHolder(OperatorHolder.ViewType.MAIN);
        Inventory inv = Bukkit.createInventory(holder, 54, MAIN_TITLE);

        ItemStack border = createPane(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) inv.setItem(i, border);

        // Center Category Cards
        inv.setItem(20, createNamedItem(Material.DIAMOND_PICKAXE, ColorUtil.color("&bMinions Catalog"),
                List.of(ColorUtil.color("&7Browse and obtain all minion types"),
                        ColorUtil.color("&7(Mining, Foraging, Farming, Combat, Custom)."),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&eClick to open catalog"))));

        inv.setItem(22, createNamedItem(Material.LAVA_BUCKET, ColorUtil.color("&6Fuel Supplies"),
                List.of(ColorUtil.color("&7Explore temporary and permanent fuels"),
                        ColorUtil.color("&7with custom speed multipliers."),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&eClick to open fuel supplies"))));

        inv.setItem(24, createNamedItem(Material.NETHER_STAR, ColorUtil.color("&dUpgrades & Accessories"),
                List.of(ColorUtil.color("&7Access automated smelters, compactors,"),
                        ColorUtil.color("&7diamond spreading, and boosters."),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&eClick to open upgrades"))));

        inv.setItem(30, createNamedItem(Material.CHEST, ColorUtil.color("&aAdmin Starter Kits"),
                List.of(ColorUtil.color("&7Pre-packaged minion combinations"),
                        ColorUtil.color("&7for instant deployment and testing."),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&eClick to open kits"))));

        inv.setItem(32, createNamedItem(Material.ANVIL, ColorUtil.color("&eMinion Creator Studio"),
                List.of(ColorUtil.color("&7Design and deploy custom Miner, Combat,"),
                        ColorUtil.color("&7Foraging, or Farming minions with custom heads,"),
                        ColorUtil.color("&7tools, exact drop item clones, and MythicMobs!"),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&eClick to open Creator Studio"))));

        // Bottom Controls
        inv.setItem(45, createNamedItem(Material.BARRIER, ColorUtil.color("&cClose"),
                List.of(ColorUtil.color("&7Close the operator interface."))));

        inv.setItem(53, createNamedItem(Material.REPEATER, ColorUtil.color("&eReload Plugin"),
                List.of(ColorUtil.color("&7Hot-reload config.yml and minion registries."))));

        player.openInventory(inv);
    }

    /**
     * Opens the Minions Catalog category.
     */
    public static void openMinionsCatalog(Player player) {
        openMinionsCatalog(player, null);
    }

    /**
     * Opens the Minions Catalog with an optional category filter.
     */
    public static void openMinionsCatalog(Player player, String categoryFilter) {
        OperatorHolder holder = new OperatorHolder(OperatorHolder.ViewType.MINIONS_CATALOG, categoryFilter);
        Inventory inv = Bukkit.createInventory(holder, 54, MINIONS_TITLE);

        ItemStack border = createPane(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) inv.setItem(i, border);

        // Top Row: Category Filter Tabs
        inv.setItem(1, createNamedItem(Material.NETHER_STAR, ColorUtil.color((categoryFilter == null ? "&a▶ " : "&7") + "All Minions"),
                List.of(ColorUtil.color("&7Browse all registered minions."))));
        inv.setItem(2, createNamedItem(Material.DIAMOND_PICKAXE, ColorUtil.color(("MINER".equalsIgnoreCase(categoryFilter) ? "&a▶ " : "&6") + "Mining"),
                List.of(ColorUtil.color("&7Cobblestone, Coal, Iron, Gold, Diamond, etc."))));
        inv.setItem(3, createNamedItem(Material.IRON_AXE, ColorUtil.color(("FORAGING".equalsIgnoreCase(categoryFilter) ? "&a▶ " : "&2") + "Foraging"),
                List.of(ColorUtil.color("&7Oak, Birch, Spruce, Dark Oak, Acacia, Jungle."))));
        inv.setItem(4, createNamedItem(Material.DIAMOND_HOE, ColorUtil.color(("FARMING".equalsIgnoreCase(categoryFilter) ? "&a▶ " : "&e") + "Farming & Animals"),
                List.of(ColorUtil.color("&7Crops, Stems, Canes, Cactus, Mushrooms, Animals."))));
        inv.setItem(5, createNamedItem(Material.IRON_SWORD, ColorUtil.color(("COMBAT".equalsIgnoreCase(categoryFilter) ? "&a▶ " : "&c") + "Combat"),
                List.of(ColorUtil.color("&7Zombie, Skeleton, Spider, Creeper, etc."))));
        inv.setItem(6, createNamedItem(Material.WRITABLE_BOOK, ColorUtil.color(("CUSTOM".equalsIgnoreCase(categoryFilter) ? "&a▶ " : "&d") + "Custom Minions"),
                List.of(ColorUtil.color("&7Minions created via Creator Studio."))));

        // Center Catalog
        List<MinionDefinition> defs = new ArrayList<>(MinionRegistry.getAll());
        if (categoryFilter != null) {
            if ("CUSTOM".equalsIgnoreCase(categoryFilter)) {
                defs.removeIf(d -> !MinionRegistry.isCustom(d.getId()));
            } else {
                defs.removeIf(d -> !d.getArchetype().name().equalsIgnoreCase(categoryFilter));
            }
        }

        int[] slots = {
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34,
                37, 38, 39, 40, 41, 42, 43
        };

        for (int i = 0; i < defs.size() && i < slots.length; i++) {
            MinionDefinition def = defs.get(i);
            ItemStack item = MinionItemFactory.createCustomMinionItem(def, 11, 0);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                lore.add(ColorUtil.color("&8------------------------"));
                lore.add(ColorUtil.color("&aLeft-Click: &fGet Tier XI (Maxed)"));
                lore.add(ColorUtil.color("&eRight-Click: &fBrowse Tiers I to XI"));
                lore.add(ColorUtil.color("&bShift-Click: &fGet Tier I (Starter)"));
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
            inv.setItem(slots[i], item);
        }

        inv.setItem(49, createNamedItem(Material.ARROW, ColorUtil.color("&cBack to Main Menu"),
                List.of(ColorUtil.color("&7Return to the main operator interface."))));

        player.openInventory(inv);
    }

    /**
     * Opens the Tier Browser for a MinionType.
     */
    public static void openTierBrowser(Player player, MinionType type) {
        MinionDefinition def = MinionRegistry.get(type.name().toLowerCase());
        if (def != null) {
            openTierBrowser(player, def);
            return;
        }

        OperatorHolder holder = new OperatorHolder(OperatorHolder.ViewType.MINION_TIERS, type);
        Inventory inv = Bukkit.createInventory(holder, 54, ColorUtil.color("&8MinionX - " + type.getDisplayName() + " Tiers"));

        ItemStack border = createPane(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) inv.setItem(i, border);

        inv.setItem(4, createNamedItem(Material.CHEST, ColorUtil.color("&b" + type.getDisplayName() + " Minion Tiers (I to XI)"),
                List.of(ColorUtil.color("&7Click any tier to receive that minion item."),
                        ColorUtil.color("&7Shift-Click to receive a full stack."))));

        int[] tierSlots = {19, 20, 21, 22, 23, 24, 25, 29, 30, 31, 32};
        int max = Math.min(MinionTier.getMaxTier(), tierSlots.length);
        for (int tier = 1; tier <= max; tier++) {
            ItemStack minionItem = MinionItemFactory.createMinionItem(type, tier, 0);
            inv.setItem(tierSlots[tier - 1], minionItem);
        }

        inv.setItem(49, createNamedItem(Material.ARROW, ColorUtil.color("&cBack to Minions Catalog"),
                List.of(ColorUtil.color("&7Return to the minions catalog."))));

        player.openInventory(inv);
    }

    /**
     * Opens the Tier Browser for a MinionDefinition.
     */
    public static void openTierBrowser(Player player, MinionDefinition def) {
        OperatorHolder holder = new OperatorHolder(OperatorHolder.ViewType.MINION_TIERS, def);
        Inventory inv = Bukkit.createInventory(holder, 54, ColorUtil.color("&8MinionX - " + def.getDisplayName() + " Tiers"));

        ItemStack border = createPane(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) inv.setItem(i, border);

        inv.setItem(4, createNamedItem(Material.CHEST, ColorUtil.color("&b" + def.getDisplayName() + " Minion Tiers (I to XI)"),
                List.of(ColorUtil.color("&7Click any tier to receive that minion item."),
                        ColorUtil.color("&7Shift-Click to receive a full stack."))));

        int[] tierSlots = {19, 20, 21, 22, 23, 24, 25, 29, 30, 31, 32};
        int max = Math.min(MinionTier.getMaxTier(), tierSlots.length);
        for (int tier = 1; tier <= max; tier++) {
            ItemStack minionItem = MinionItemFactory.createCustomMinionItem(def, tier, 0);
            inv.setItem(tierSlots[tier - 1], minionItem);
        }

        inv.setItem(49, createNamedItem(Material.ARROW, ColorUtil.color("&cBack to Minions Catalog"),
                List.of(ColorUtil.color("&7Return to the minions catalog."))));

        player.openInventory(inv);
    }

    /**
     * Opens the Fuel Supplies category.
     */
    public static void openFuelsCatalog(Player player) {
        OperatorHolder holder = new OperatorHolder(OperatorHolder.ViewType.FUELS_CATALOG);
        Inventory inv = Bukkit.createInventory(holder, 54, FUELS_TITLE);

        ItemStack border = createPane(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) inv.setItem(i, border);

        inv.setItem(4, createNamedItem(Material.LAVA_BUCKET, ColorUtil.color("&6Fuel Supplies Catalog"),
                List.of(ColorUtil.color("&7Click any fuel item to receive it directly."),
                        ColorUtil.color("&7Shift-Click to receive 64 items."))));

        int[] slots = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
        int idx = 0;
        for (CustomFuel fuel : FuelRegistry.getAll()) {
            if (idx >= slots.length) break;
            inv.setItem(slots[idx++], MinionItemFactory.createFuelItem(fuel));
        }

        inv.setItem(49, createNamedItem(Material.ARROW, ColorUtil.color("&cBack to Main Menu"),
                List.of(ColorUtil.color("&7Return to the main operator interface."))));

        player.openInventory(inv);
    }

    /**
     * Opens the Upgrades category.
     */
    public static void openUpgradesCatalog(Player player) {
        OperatorHolder holder = new OperatorHolder(OperatorHolder.ViewType.UPGRADES_CATALOG);
        Inventory inv = Bukkit.createInventory(holder, 54, UPGRADES_TITLE);

        ItemStack border = createPane(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) inv.setItem(i, border);

        inv.setItem(4, createNamedItem(Material.NETHER_STAR, ColorUtil.color("&dUpgrades & Accessories Catalog"),
                List.of(ColorUtil.color("&7Click any upgrade item to receive it directly."),
                        ColorUtil.color("&7Shift-Click to receive 64 items."))));

        int[] slots = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
        int idx = 0;
        for (CustomUpgrade upgrade : UpgradeRegistry.getAll()) {
            if (idx >= slots.length) break;
            inv.setItem(slots[idx++], MinionItemFactory.createUpgradeItem(upgrade));
        }

        inv.setItem(49, createNamedItem(Material.ARROW, ColorUtil.color("&cBack to Main Menu"),
                List.of(ColorUtil.color("&7Return to the main operator interface."))));

        player.openInventory(inv);
    }

    /**
     * Opens the Admin Starter Kits category.
     */
    public static void openKitsCatalog(Player player) {
        OperatorHolder holder = new OperatorHolder(OperatorHolder.ViewType.KITS_CATALOG);
        Inventory inv = Bukkit.createInventory(holder, 54, KITS_TITLE);

        ItemStack border = createPane(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) inv.setItem(i, border);

        inv.setItem(4, createNamedItem(Material.CHEST, ColorUtil.color("&aAdmin Starter Kits"),
                List.of(ColorUtil.color("&7Click any kit below to receive complete setups instantly."))));

        inv.setItem(20, createNamedItem(Material.CHEST, ColorUtil.color("&aKit: Cobblestone Starter"),
                List.of(ColorUtil.color("&7Includes:"),
                        ColorUtil.color("&8- &71x Cobblestone Minion I"),
                        ColorUtil.color("&8- &71x Coal Fuel"),
                        ColorUtil.color("&8- &71x Auto-Smelter"),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&eClick to receive full kit"))));

        inv.setItem(22, createNamedItem(Material.CHEST, ColorUtil.color("&6Kit: Iron Automaton"),
                List.of(ColorUtil.color("&7Includes:"),
                        ColorUtil.color("&8- &71x Iron Minion V"),
                        ColorUtil.color("&8- &71x Enchanted Charcoal Fuel"),
                        ColorUtil.color("&8- &71x Auto-Smelter"),
                        ColorUtil.color("&8- &71x Compactor"),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&eClick to receive full kit"))));

        inv.setItem(24, createNamedItem(Material.ENDER_CHEST, ColorUtil.color("&bKit: Diamond Maxed Producer"),
                List.of(ColorUtil.color("&7Includes:"),
                        ColorUtil.color("&8- &71x Diamond Minion XI"),
                        ColorUtil.color("&8- &71x Enchanted Lava Bucket (Permanent)"),
                        ColorUtil.color("&8- &71x Auto-Smelter"),
                        ColorUtil.color("&8- &71x Diamond Spreading"),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&eClick to receive full kit"))));

        inv.setItem(49, createNamedItem(Material.ARROW, ColorUtil.color("&cBack to Main Menu"),
                List.of(ColorUtil.color("&7Return to the main operator interface."))));

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
