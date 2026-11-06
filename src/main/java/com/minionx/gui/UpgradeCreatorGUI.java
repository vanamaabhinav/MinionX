package com.minionx.gui;

import com.minionx.api.BonusDropEntry;
import com.minionx.api.CustomUpgrade;
import com.minionx.item.MinionItemFactory;
import com.minionx.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * 54-slot In-Game Custom Minion Upgrade and Hopper Creator Studio GUI.
 * Features a modular multi-behavior capability dashboard and multi-item spreading shelf.
 */
@SuppressWarnings("deprecation")
public class UpgradeCreatorGUI {

    public static final String TITLE = ColorUtil.color("&8&lMinion&c&lX &8- Upgrade Creator Studio");

    public static final Material[] PRESET_MATERIALS = {
            Material.HOPPER,
            Material.FURNACE,
            Material.DISPENSER,
            Material.DROPPER,
            Material.DIAMOND,
            Material.POTATO,
            Material.GOLDEN_CARROT,
            Material.PISTON,
            Material.NETHER_STAR,
            Material.STRING,
            Material.BLAZE_POWDER,
            Material.REDSTONE,
            Material.EMERALD,
            Material.GOLD_INGOT,
            Material.IRON_BLOCK
    };

    public static final Material[] PRESET_SPREADING = {
            Material.DIAMOND,
            Material.POTATO,
            Material.EMERALD,
            Material.IRON_INGOT,
            Material.GOLD_INGOT,
            Material.COAL,
            Material.REDSTONE,
            Material.LAPIS_LAZULI,
            Material.NETHERITE_INGOT,
            Material.RAW_COPPER
    };

    public static void open(Player player, UpgradeCreatorSession session) {
        if (session == null) {
            session = new UpgradeCreatorSession("custom_upgrade");
        }

        UpgradeCreatorHolder holder = new UpgradeCreatorHolder(session);
        Inventory inv = Bukkit.createInventory(holder, 54, TITLE);

        ItemStack border = createPane(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) inv.setItem(i, border);

        // Slot 2: Identity (ID & Display Name)
        inv.setItem(2, createNamedItem(Material.NAME_TAG, ColorUtil.color("&eUpgrade Identity"),
                List.of(ColorUtil.color("&7ID: &f" + session.getId()),
                        ColorUtil.color("&7Display Name: &f" + session.getDisplayName()),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&eClick to change ID / Name via chat"))));

        // Slot 4: Item Material Selector
        inv.setItem(4, createNamedItem(session.getMaterial(), ColorUtil.color("&aItem Icon Material"),
                List.of(ColorUtil.color("&7Current: &f" + session.getMaterial().name()),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&eClick to cycle preset icons"),
                        ColorUtil.color("&dClick with ANY item on cursor to set!"))));

        // Slot 6: Glow Toggle
        inv.setItem(6, createNamedItem(session.isGlowing() ? Material.GLOWSTONE_DUST : Material.GUNPOWDER,
                ColorUtil.color("&eEnchantment Glow"),
                List.of(ColorUtil.color("&7Glowing: &f" + (session.isGlowing() ? "&aYES" : "&cNO")),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&eClick to toggle enchantment shine"))));

        // =========================================================================
        // ROW 1: FUNCTIONAL CAPABILITIES (MULTI-BEHAVIOR)
        // =========================================================================

        // Slot 10: Auto-Smelter Toggle
        boolean hasSmelt = session.hasBehavior(CustomUpgrade.UpgradeBehavior.AUTO_SMELTER);
        inv.setItem(10, createNamedItem(Material.FURNACE, ColorUtil.color("&6Auto-Smelter"),
                List.of(ColorUtil.color("&7Status: " + (hasSmelt ? "&a[ENABLED]" : "&c[DISABLED]")),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&7Automatically smelts iron, gold, copper,"),
                        ColorUtil.color("&7cobblestone, and wood on harvest."),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&eClick to toggle Auto-Smelter"))));

        // Slot 11: Compactor Mode Cycle
        int cMode = session.getCompactorMode();
        Material cMat = (cMode == 2 ? Material.DISPENSER : cMode == 1 ? Material.DROPPER : Material.CAULDRON);
        String cStatus = (cMode == 2 ? "&d[SUPER COMPACTOR (Enchanted)]" : cMode == 1 ? "&6[STANDARD COMPACTOR (3x3)]" : "&7[DISABLED]");
        inv.setItem(11, createNamedItem(cMat, ColorUtil.color("&dCompaction Mode"),
                List.of(ColorUtil.color("&7Mode: " + cStatus),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&7Compresses resources into blocks or enchanted items."),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&eClick to cycle: NONE -> STANDARD -> SUPER -> NONE"))));

        // Slot 12: Speed Buff Tuner
        double speed = session.getSpeedBuff();
        inv.setItem(12, createNamedItem(Material.SUGAR, ColorUtil.color("&aSpeed Boost Percentage"),
                List.of(ColorUtil.color("&7Current: " + (speed > 0 ? "&a+" + (int) Math.round(speed * 100) + "% speed" : "&7[DISABLED]")),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&7Accelerates minion harvest action speed."),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&aLeft-Click: &f+5%"),
                        ColorUtil.color("&cRight-Click: &f-5%"))));

        // Slot 13: Area Expansion Tuner
        int expand = session.getAreaExpansion();
        inv.setItem(13, createNamedItem(Material.PISTON, ColorUtil.color("&2Area Expansion"),
                List.of(ColorUtil.color("&7Current: " + (expand > 0 ? "&a+" + expand + " blocks radius" : "&7[DISABLED]")),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&7Expands minion working area (5x5 -> 7x7)."),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&aLeft-Click: &f+1 block"),
                        ColorUtil.color("&cRight-Click: &f-1 block"))));

        // Slot 14: Auto-Seller (Hopper) Tuner
        double sell = session.getSellRatio();
        inv.setItem(14, createNamedItem(Material.HOPPER, ColorUtil.color("&eAuto-Seller Ratio"),
                List.of(ColorUtil.color("&7Current: " + (sell > 0 ? "&e" + (int) Math.round(sell * 100) + "% of NPC price" : "&7[DISABLED]")),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&7Sells overflow resources when inventory is full."),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&aLeft-Click: &f+5%"),
                        ColorUtil.color("&cRight-Click: &f-5%"))));

        // Slot 16: Live Preview Item
        CustomUpgrade previewUpgrade = session.buildUpgrade();
        ItemStack previewItem = MinionItemFactory.createUpgradeItem(previewUpgrade);
        inv.setItem(16, previewItem);

        // =========================================================================
        // ROW 2: MULTI-ITEM SPREADING SHELF (SLOTS 19-25)
        // =========================================================================

        inv.setItem(19, createNamedItem(Material.DIAMOND, ColorUtil.color("&bItem Spreading Shelf"),
                List.of(ColorUtil.color("&7Active Drops: &e" + session.getSpreadingItems().size() + "/5"),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&7Minions generate these bonus drops on harvests."),
                        ColorUtil.color("&7Click items to adjust chance & amount."))));

        List<BonusDropEntry> spreading = session.getSpreadingItems();
        for (int i = 0; i < 5; i++) {
            int slot = 20 + i;
            if (i < spreading.size()) {
                BonusDropEntry entry = spreading.get(i);
                inv.setItem(slot, createNamedItem(entry.getMaterial(),
                        ColorUtil.color("&eDrop #" + (i + 1) + ": &f" + (entry.getDisplayName() != null ? entry.getDisplayName() : entry.getMaterial().name())),
                        List.of(ColorUtil.color("&7Chance: &e" + (int) Math.round(entry.getChance() * 100) + "%"),
                                ColorUtil.color("&7Amount: &a" + entry.getAmount() + "x"),
                                ColorUtil.color("&7"),
                                ColorUtil.color("&aLeft-Click: &f+5% chance &8| &cRight-Click: &f-5%"),
                                ColorUtil.color("&bShift-Left: &f+1 amount &8| &bShift-Right: &f-1"),
                                ColorUtil.color("&dClick with item on cursor to set material!"),
                                ColorUtil.color("&cPress [Q] (drop) to delete this drop"))));
            } else if (i == spreading.size()) {
                inv.setItem(slot, createNamedItem(Material.LIME_DYE, ColorUtil.color("&a+ Add Spreading Drop"),
                        List.of(ColorUtil.color("&7Click to add a bonus drop"),
                                ColorUtil.color("&7(or click with an item on your cursor)."))));
            } else {
                inv.setItem(slot, createPane(Material.GRAY_STAINED_GLASS_PANE, " "));
            }
        }

        // =========================================================================
        // NAVIGATION & SAVE
        // =========================================================================

        // Slot 45: Universal Back Button
        inv.setItem(45, createNamedItem(Material.ARROW, ColorUtil.color("&e« Back to Operator Hub"),
                List.of(ColorUtil.color("&7Return to Upgrades & Accessories Catalog."),
                        ColorUtil.color("&8Unsaved changes in this session will be discarded."),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&eClick to Return"))));

        // Slot 49: Save & Deploy
        inv.setItem(49, createNamedItem(Material.EMERALD_BLOCK, ColorUtil.color("&a&lSave & Deploy Upgrade"),
                List.of(ColorUtil.color("&7Saves definition to &fupgrades.yml&7,"),
                        ColorUtil.color("&7registers into registry immediately,"),
                        ColorUtil.color("&7and gives you a testing item!"),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&eClick to Save & Deploy!"))));

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
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }
}
