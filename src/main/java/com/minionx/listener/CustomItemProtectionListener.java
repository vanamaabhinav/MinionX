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

    private final java.util.Map<java.util.UUID, Long> lastEatWarn = new java.util.concurrent.ConcurrentHashMap<>();

    private void warnCannotEat(Player player) {
        if (player == null) return;
        long now = System.currentTimeMillis();
        Long last = lastEatWarn.get(player.getUniqueId());
        if (last == null || now - last > 1500L) {
            lastEatWarn.put(player.getUniqueId(), now);
            player.sendMessage(ColorUtil.color(getMessage("cannot-eat-minion-item", "&cYou cannot consume minion items or fuels!")));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemConsume(org.bukkit.event.player.PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        if (isProtectedItem(item) || isCustomFuel(item) || isCustomUpgrade(item)) {
            event.setCancelled(true);
            warnCannotEat(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (item == null || item.getType() == Material.AIR) return;

        if (isCustomUpgrade(item) || isCustomFuel(item) || isProtectedItem(item)) {
            // If the item is edible (like Enchanted Bread), deny eating usage immediately
            if (item.getType().isEdible()) {
                event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
                if (event.getAction() == Action.RIGHT_CLICK_AIR ||
                        (event.getAction() == Action.RIGHT_CLICK_BLOCK && (event.getClickedBlock() == null || !event.getClickedBlock().getType().isInteractable() || event.getPlayer().isSneaking()))) {
                    warnCannotEat(event.getPlayer());
                }
                return;
            }

            // If the item is a placeable block or bucket liquid, prevent placing it from hand into the world
            if (item.getType().isBlock() || item.getType().name().contains("BUCKET")) {
                event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
                // Actual placement is cancelled and messaged by BlockPlaceEvent and PlayerBucketEmptyEvent
            }
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
        try {
            if (MinionItemFactory.getFuelId(item) != null) return true;
            if (FuelRegistry.fromItemStack(item) != null) {
                // Only consider custom if it has custom meta or is custom fuel
                if (item.hasItemMeta() && item.getItemMeta() != null && (item.getItemMeta().hasDisplayName() || !item.getItemMeta().getPersistentDataContainer().isEmpty())) {
                    return true;
                }
            }
            if (!item.hasItemMeta()) return false;
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.hasDisplayName()) {
                String name = meta.getDisplayName();
                return name.contains("Enchanted Lava Bucket") || name.contains("Plasma Bucket") ||
                        name.contains("Solar Panel") || name.contains("Enchanted Charcoal") ||
                        name.contains("Enchanted Bread") || name.contains("Magma Bucket") ||
                        name.contains("Coal") || name.contains("Charcoal");
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public static boolean isCustomUpgrade(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        try {
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
        } catch (Throwable ignored) {}
        return false;
    }

    public static boolean isProtectedItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        return MinionItemFactory.isMinionItem(item) || isCustomUpgrade(item) || isCustomFuel(item);
    }
}