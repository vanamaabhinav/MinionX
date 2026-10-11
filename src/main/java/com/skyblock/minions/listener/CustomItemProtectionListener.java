package com.skyblock.minions.listener;

import com.skyblock.minions.item.MinionItemFactory;
import com.skyblock.minions.util.ColorUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Prevents players from placing fuel buckets as world liquids, placing upgrade items as blocks,
 * dispensing them, or using custom minion items (such as Diamond Spreading) in vanilla crafting recipes.
 */
public class CustomItemProtectionListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Player player = event.getPlayer();
        ItemStack inHand = player.getInventory().getItem(event.getHand());

        if (isCustomFuel(inHand)) {
            event.setCancelled(true);
            player.sendMessage(ColorUtil.color("&cYou cannot place minion fuel in the world!"));
            player.updateInventory();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (isCustomUpgrade(item) || isCustomFuel(item)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ColorUtil.color("&cYou cannot place minion items as blocks!"));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockDispense(BlockDispenseEvent event) {
        if (isProtectedItem(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        for (ItemStack item : event.getInventory().getMatrix()) {
            if (item == null || item.getType() == Material.AIR) continue;
            if (isProtectedItem(item)) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        for (ItemStack item : event.getInventory().getMatrix()) {
            if (item == null || item.getType() == Material.AIR) continue;
            if (isProtectedItem(item)) {
                event.setCancelled(true);
                if (event.getWhoClicked() instanceof Player player) {
                    player.sendMessage(ColorUtil.color("&cYou cannot use minion items or upgrades in crafting recipes!"));
                }
                return;
            }
        }
    }

    public static boolean isCustomFuel(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        if (!item.hasItemMeta()) return false;
        if (MinionItemFactory.getFuelId(item) != null) return true;
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            String name = meta.getDisplayName();
            return name.contains("Enchanted Lava Bucket") || name.contains("Plasma Bucket") || name.contains("Solar Panel");
        }
        return false;
    }

    public static boolean isCustomUpgrade(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        if (!item.hasItemMeta()) return false;
        if (MinionItemFactory.getUpgradeId(item) != null) return true;
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            String name = meta.getDisplayName();
            return name.contains("Auto-Smelter") || name.contains("Compactor") ||
                    name.contains("Diamond Spreading") || name.contains("Flycatcher") ||
                    name.contains("Minion Expander") || name.contains("Hyper Catalyst");
        }
        return false;
    }

    public static boolean isProtectedItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        if (!item.hasItemMeta()) return false;
        return MinionItemFactory.isMinionItem(item) || isCustomUpgrade(item) || isCustomFuel(item);
    }
}