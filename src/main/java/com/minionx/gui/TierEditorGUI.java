package com.minionx.gui;

import com.minionx.api.MinionTier;
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
 * In-game Tier Studio GUI allowing server owners to browse, edit, create,
 * and configure minion tiers (action delays, storage capacities, upgrade costs)
 * with direct serialization to tiers.yml.
 */
@SuppressWarnings("deprecation")
public class TierEditorGUI {

    public static final String OVERVIEW_TITLE = ColorUtil.color("&8MinionX - Tier Studio");
    public static final String EDITOR_TITLE_PREFIX = ColorUtil.color("&8Tier Editor: Tier ");

    public static void openOverview(Player player) {
        int maxTier = MinionTier.getMaxTier();
        int size = 54;
        Inventory inv = Bukkit.createInventory(null, size, OVERVIEW_TITLE);

        ItemStack border = createItem(Material.GRAY_STAINED_GLASS_PANE, " ", null);
        for (int i = 0; i < size; i++) {
            inv.setItem(i, border);
        }

        Material[] icons = {
                Material.COAL_BLOCK, Material.COPPER_BLOCK, Material.IRON_BLOCK, Material.GOLD_BLOCK,
                Material.REDSTONE_BLOCK, Material.LAPIS_BLOCK, Material.DIAMOND_BLOCK, Material.EMERALD_BLOCK,
                Material.NETHERITE_BLOCK, Material.AMETHYST_BLOCK, Material.BEACON, Material.NETHER_STAR,
                Material.END_CRYSTAL, Material.DRAGON_EGG, Material.CONDUIT
        };

        for (int tier = 1; tier <= maxTier && tier <= 45; tier++) {
            MinionTier.TierData data = MinionTier.getTierData(tier);
            double delay = data != null ? data.getActionDelay() : MinionTier.getActionDelay(tier);
            int slots = data != null ? data.getStorageSlots() : MinionTier.getStorageSlots(tier);
            int cost = data != null ? data.getUpgradeCost() : MinionTier.getUpgradeCost(tier);

            Material iconMat = icons[(tier - 1) % icons.length];
            List<String> lore = new ArrayList<>();
            lore.add(ColorUtil.color("&8----------------------------"));
            lore.add(ColorUtil.color("&7Action Delay: &e" + String.format("%.1f", delay) + "s"));
            lore.add(ColorUtil.color("&7Storage: &e" + slots + " slots &7(" + (slots * 64) + " items)"));
            lore.add(ColorUtil.color("&7Upgrade Cost: &a" + (cost > 0 ? (cost + " items") : "Max Tier")));
            lore.add(ColorUtil.color("&8----------------------------"));
            lore.add(ColorUtil.color("&eLeft-Click to configure tier!"));
            if (tier > 12) {
                lore.add(ColorUtil.color("&cShift-Right-Click to delete custom tier!"));
            }

            inv.setItem(tier - 1, createItem(iconMat, ColorUtil.color("&6&lTier " + MinionTier.toRoman(tier) + " &7(Level " + tier + ")"), lore));
        }

        // Add Tier Button at Slot 49
        List<String> addLore = List.of(
                ColorUtil.color("&7Click to create Tier &e" + (maxTier + 1) + "&7."),
                ColorUtil.color("&7Immediately saves to &ftiers.yml&7.")
        );
        inv.setItem(49, createItem(Material.ANVIL, ColorUtil.color("&a&l[+] Create Tier " + (maxTier + 1)), addLore));

        // Back / Close at Slot 45
        inv.setItem(45, createItem(Material.BARRIER, ColorUtil.color("&c&lClose"), List.of(ColorUtil.color("&7Return to game."))));

        player.openInventory(inv);
    }

    public static void openEditor(Player player, int tier, double delay, int slots, int cost) {
        String title = EDITOR_TITLE_PREFIX + MinionTier.toRoman(tier);
        Inventory inv = Bukkit.createInventory(null, 27, title);

        ItemStack border = createItem(Material.GRAY_STAINED_GLASS_PANE, " ", null);
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, border);
        }

        // Action Delay - Slot 10
        List<String> delayLore = List.of(
                ColorUtil.color("&8----------------------------"),
                ColorUtil.color("&7Current Speed: &e" + String.format("%.1f", delay) + " seconds"),
                ColorUtil.color("&8----------------------------"),
                ColorUtil.color("&aLeft-Click: &f-0.5s &7(Faster)"),
                ColorUtil.color("&cRight-Click: &f+0.5s &7(Slower)"),
                ColorUtil.color("&aShift-Left: &f-0.1s"),
                ColorUtil.color("&cShift-Right: &f+0.1s")
        );
        inv.setItem(10, createItem(Material.CLOCK, ColorUtil.color("&6&lAction Delay: &e" + String.format("%.1f", delay) + "s"), delayLore));

        // Storage Slots - Slot 12
        List<String> slotLore = List.of(
                ColorUtil.color("&8----------------------------"),
                ColorUtil.color("&7Current: &e" + slots + " slots &7(" + (slots * 64) + " items)"),
                ColorUtil.color("&8----------------------------"),
                ColorUtil.color("&aLeft-Click: &f+1 slot"),
                ColorUtil.color("&cRight-Click: &f-1 slot"),
                ColorUtil.color("&aShift-Left: &f+3 slots"),
                ColorUtil.color("&cShift-Right: &f-3 slots")
        );
        inv.setItem(12, createItem(Material.CHEST, ColorUtil.color("&6&lStorage Slots: &e" + slots + " Slots"), slotLore));

        // Upgrade Cost - Slot 14
        List<String> costLore = List.of(
                ColorUtil.color("&8----------------------------"),
                ColorUtil.color("&7Current Cost: &a" + cost + " items"),
                ColorUtil.color("&8----------------------------"),
                ColorUtil.color("&aLeft-Click: &f+64 items"),
                ColorUtil.color("&cRight-Click: &f-64 items"),
                ColorUtil.color("&aShift-Left: &f+512 items"),
                ColorUtil.color("&cShift-Right: &f-512 items")
        );
        inv.setItem(14, createItem(Material.EMERALD, ColorUtil.color("&6&lUpgrade Cost: &a" + cost + " items"), costLore));

        // Back - Slot 18
        inv.setItem(18, createItem(Material.ARROW, ColorUtil.color("&c« Back to Overview"), List.of(ColorUtil.color("&7Cancel changes and return."))));

        // Save & Apply - Slot 22
        List<String> saveLore = List.of(
                ColorUtil.color("&7Writes directly to &ftiers.yml&7"),
                ColorUtil.color("&7and instantly updates all active minions."),
                ColorUtil.color("&eClick to confirm and save!")
        );
        inv.setItem(22, createItem(Material.LIME_CONCRETE, ColorUtil.color("&a&l✔ Save & Apply Changes"), saveLore));

        player.openInventory(inv);
    }

    private static ItemStack createItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null) meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }
}
