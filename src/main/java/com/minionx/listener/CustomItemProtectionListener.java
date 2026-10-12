package com.minionx.listener;

import com.minionx.MinionX;
import com.minionx.api.FuelRegistry;
import com.minionx.api.UpgradeRegistry;
import com.minionx.item.MinionItemFactory;
import com.minionx.util.ColorUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Prevents players from placing fuel buckets as world liquids, placing upgrade/fuel items as blocks,
 * dispensing them, or using custom minion items in vanilla crafting recipes.
 * All notification messages are fully configurable in config.yml.
 */
@SuppressWarnings("deprecation")
public class CustomItemProtectionListener implements Listener {

    private String getMessage(String key, String def) {
        if (MinionX.getInstance() == null || MinionX.getInstance().getConfig() == null) {
            return def;
        }
        return MinionX.getInstance().getConfig().getString("messages." + key, def);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Player player = event.getPlayer();
        ItemStack inHand = player.getInventory().getItem(event.getHand());

        if (isCustomFuel(inHand)) {
            event.setCancelled(true);
            player.sendMessage(ColorUtil.color(getMessage("cannot-place-fuel-or-upgrade", "&cYou cannot place minion items or fuels in the world!")));
            player.updateInventory();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (isCustomUpgrade(item) || isCustomFuel(item)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ColorUtil.color(getMessage("cannot-place-fuel-or-upgrade", "&cYou cannot place minion items or fuels in the world!")));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = event.getItem();
        if (item == null || item.getType() == Material.AIR) return;

        // Minion heads are handled by MinionPlaceListener.
        // Block placement or world interaction of fuels and upgrades against blocks must be prevented.
        if (isCustomUpgrade(item) || (isCustomFuel(item) && item.getType() != Material.AIR)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ColorUtil.color(getMessage("cannot-place-fuel-or-upgrade", "&cYou cannot place minion items or fuels in the world!")));
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
                    player.sendMessage(ColorUtil.color(getMessage("cannot-craft-with-minion-item", "&cYou cannot use minion items or upgrades in crafting recipes!")));
                }
                return;
            }
        }
    }

    public static boolean isCustomFuel(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        if (MinionItemFactory.getFuelId(item) != null) return true;
        if (FuelRegistry.fromItemStack(item) != null) {
            // Only consider custom if it has custom meta or is custom fuel
            if (item.hasItemMeta() && item.getItemMeta() != null && item.getItemMeta().hasDisplayName()) {
                return true;
            }
        }
        if (!item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            String name = meta.getDisplayName();
            return name.contains("Enchanted Lava Bucket") || name.contains("Plasma Bucket") ||
                    name.contains("Solar Panel") || name.contains("Enchanted Charcoal") ||
                    name.contains("Coal") || name.contains("Charcoal");
        }
        return false;
    }

    public static boolean isCustomUpgrade(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        if (MinionItemFactory.getUpgradeId(item) != null) return true;
        if (UpgradeRegistry.fromItemStack(item) != null) return true;
        if (!item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            String name = meta.getDisplayName();
            return name.contains("Auto-Smelter") || name.contains("Compactor") ||
                    name.contains("Diamond Spreading") || name.contains("Flycatcher") ||
                    name.contains("Minion Expander") || name.contains("Hyper Catalyst") ||
                    name.contains("Super Compactor");
        }
        return false;
    }

    public static boolean isProtectedItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        return MinionItemFactory.isMinionItem(item) || isCustomUpgrade(item) || isCustomFuel(item);
    }
}