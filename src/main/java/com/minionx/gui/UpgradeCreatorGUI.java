package com.minionx.gui;

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
 * 54-slot In-Game Custom Minion Upgrade & Hopper Creator Studio GUI.
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
            Material.PISTON,
            Material.NETHER_STAR,
            Material.STRING,
            Material.BLAZE_POWDER,
            Material.REDSTONE,
            Material.EMERALD,
            Material.GOLD_INGOT,
            Material.IRON_BLOCK
    };

    public static void open(Player player, UpgradeCreatorSession session) {
        if (session == null) {
            session = new UpgradeCreatorSession("custom_hopper");
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

        // Slot 10: Behavior / Type
        Material behaviorIcon = switch (session.getBehavior()) {
            case AUTO_SELLER -> Material.HOPPER;
            case AUTO_SMELTER -> Material.FURNACE;
            case COMPACTOR -> Material.DROPPER;
            case SUPER_COMPACTOR -> Material.DISPENSER;
            case DIAMOND_SPREADING -> Material.DIAMOND;
            case SPEED_BUFF -> Material.SUGAR;
        };
        inv.setItem(10, createNamedItem(behaviorIcon, ColorUtil.color("&6Upgrade Function / Type"),
                List.of(ColorUtil.color("&7Current: &e" + session.getBehavior().name()),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&7- &eAUTO_SELLER: &7Hoppers that sell overflow items for % price"),
                        ColorUtil.color("&7- &6AUTO_SMELTER: &7Smelts iron/gold/cobblestone/wood"),
                        ColorUtil.color("&7- &6COMPACTOR: &7Compacts items into standard blocks"),
                        ColorUtil.color("&7- &dSUPER_COMPACTOR: &7Compresses items into enchanted forms"),
                        ColorUtil.color("&7- &bDIAMOND_SPREADING: &7Adds extra diamond bonus chance"),
                        ColorUtil.color("&7- &aSPEED_BUFF: &7Flat speed boost multiplier (+%)"),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&eClick to cycle function type"))));

        // Slot 12: Ratio / Speed / Chance Value Tuning
        if (session.getBehavior() == CustomUpgrade.UpgradeBehavior.AUTO_SELLER) {
            inv.setItem(12, createNamedItem(Material.GOLD_INGOT, ColorUtil.color("&6Sell Price Ratio"),
                    List.of(ColorUtil.color("&7Current: &e" + (int) Math.round(session.getSellRatio() * 100) + "% of NPC price"),
                            ColorUtil.color("&7(Hypixel: Budget Hopper = 50%, Enchanted Hopper = 90%)"),
                            ColorUtil.color("&7"),
                            ColorUtil.color("&aLeft-Click: &f+5%"),
                            ColorUtil.color("&cRight-Click: &f-5%"))));
        } else if (session.getBehavior() == CustomUpgrade.UpgradeBehavior.SPEED_BUFF) {
            inv.setItem(12, createNamedItem(Material.SUGAR, ColorUtil.color("&aSpeed Boost Percentage"),
                    List.of(ColorUtil.color("&7Current: &a+" + (int) Math.round(session.getSpeedBuff() * 100) + "% speed"),
                            ColorUtil.color("&7"),
                            ColorUtil.color("&aLeft-Click: &f+5%"),
                            ColorUtil.color("&cRight-Click: &f-5%"))));
        } else if (session.getBehavior() == CustomUpgrade.UpgradeBehavior.DIAMOND_SPREADING) {
            inv.setItem(12, createNamedItem(Material.DIAMOND, ColorUtil.color("&bDiamond Bonus Chance"),
                    List.of(ColorUtil.color("&7Current: &b" + (int) Math.round(session.getChance() * 100) + "% chance"),
                            ColorUtil.color("&7"),
                            ColorUtil.color("&aLeft-Click: &f+5%"),
                            ColorUtil.color("&cRight-Click: &f-5%"))));
        } else {
            inv.setItem(12, createNamedItem(Material.GRAY_DYE, ColorUtil.color("&7Automatic Mode"),
                    List.of(ColorUtil.color("&7This upgrade operates automatically with 100% efficiency."))));
        }

        // Slot 14: Glow Toggle
        inv.setItem(14, createNamedItem(session.isGlowing() ? Material.GLOWSTONE_DUST : Material.GUNPOWDER,
                ColorUtil.color("&eEnchantment Glow"),
                List.of(ColorUtil.color("&7Glowing: &f" + (session.isGlowing() ? "&aYES" : "&cNO")),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&eClick to toggle enchantment shine"))));

        // Slot 16: Preview Item
        CustomUpgrade previewUpgrade = session.buildUpgrade();
        ItemStack previewItem = MinionItemFactory.createUpgradeItem(previewUpgrade);
        inv.setItem(16, previewItem);

        // Slot 48: Cancel & Return
        inv.setItem(48, createNamedItem(Material.BARRIER, ColorUtil.color("&cCancel & Return"),
                List.of(ColorUtil.color("&7Discard changes and return to Upgrades Catalog."))));

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
