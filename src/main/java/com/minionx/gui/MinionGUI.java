package com.minionx.gui;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.minionx.MinionX;
import com.minionx.api.CustomFuel;
import com.minionx.api.CustomUpgrade;
import com.minionx.api.MinionDefinition;
import com.minionx.api.MinionRegistry;
import com.minionx.api.MinionTier;
import com.minionx.core.Minion;
import com.minionx.util.ColorUtil;
import com.minionx.util.UpgradeCostHelper;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Builds the authentic 54-slot Hypixel-style Minion chest interface.
 * Faithfully formatted with exact Hypixel layout and stained glass pane color codes:
 * - Top Row: Ideal Layout (Torch), Minion Head (Status), Next Tier (Gold Ingot)
 * - Left Modifiers: Green Pane (Skin), Orange Pane (Fuel), Yellow Pane (Shipping), Blue Panes (Upgrades)
 * - Middle-Right Storage Grid: 15 slots (21-25, 30-34, 39-43) with Red Panes for locked slots
 * - Bottom Row: Chest (Collect All), Diamond (Quick-Upgrade), Bedrock (Pickup Minion)
 * - Borders: Sleek Black Stained Glass Panes
 */
@SuppressWarnings("deprecation")
public class MinionGUI {

    /**
     * Creates and opens the Minion GUI for the player.
     */
    public static void open(Player player, Minion minion) {
        // Authentic Hypixel title: Name Minion Tier (e.g. Cobblestone Minion I)
        String title = minion.getDisplayName() + " Minion " + MinionTier.toRoman(minion.getTier());
        Inventory inv = Bukkit.createInventory(new MinionHolder(minion), 54, title);

        // Fillers: Sleek Black Stained Glass Pane
        ItemStack filler = createPane(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) {
            inv.setItem(i, filler);
        }

        // Top Row: Information & Requirements
        inv.setItem(MinionGUIConfig.getIdealLayoutSlot(), createIdealLayoutItem());
        inv.setItem(MinionGUIConfig.getHeadSlot(), createHeadItem(minion));
        inv.setItem(MinionGUIConfig.getNextTierSlot(), createNextTierInfoItem(minion));

        // Left Column: Modifiers & Automation (Colored Panes)
        // Slot 10: Minion Skin (Green Pane)
        int skinSlot = MinionGUIConfig.getSkinSlot();
        if (minion.hasSkin()) {
            inv.setItem(skinSlot, createActiveSkinItem(minion.getSkin()));
        } else {
            inv.setItem(skinSlot, createEmptySkinPane());
        }

        // Slot 19: Fuel (Orange Pane)
        int fuelSlot = MinionGUIConfig.getFuelSlot();
        if (minion.isFuelActive()) {
            inv.setItem(fuelSlot, createActiveFuelItem(minion));
        } else {
            inv.setItem(fuelSlot, createEmptyFuelPane());
        }

        // Slot 28: Automated Shipping (Yellow Pane or Equipped Hopper)
        int shippingSlot = MinionGUIConfig.getAutomatedShippingSlot();
        if (minion.getShippingId() != null) {
            inv.setItem(shippingSlot, createActiveShippingItem(minion));
        } else {
            inv.setItem(shippingSlot, createShippingPane());
        }

        // Slots 37 & 46: Minion Upgrades (Blue Panes)
        inv.setItem(MinionGUIConfig.getUpgrade1Slot(), createUpgradeSlotItem(minion.getCustomUpgrade1(), 1));
        inv.setItem(MinionGUIConfig.getUpgrade2Slot(), createUpgradeSlotItem(minion.getCustomUpgrade2(), 2));

        // Middle Grid: Storage (Slots 21-25, 30-34, 39-43)
        int[] storageSlots = MinionGUIConfig.getStorageSlots();
        int unlockedSlots = minion.getStorageSlots();
        List<ItemStack> items = minion.getInventory();

        for (int i = 0; i < storageSlots.length; i++) {
            int slot = storageSlots[i];
            if (i < unlockedSlots) {
                if (i < items.size() && items.get(i) != null) {
                    inv.setItem(slot, items.get(i).clone());
                } else {
                    inv.setItem(slot, new ItemStack(Material.AIR)); // Empty unlocked storage slot
                }
            } else {
                inv.setItem(slot, createLockedStoragePane(minion, i));
            }
        }

        // Bottom Row: Quick Actions
        inv.setItem(MinionGUIConfig.getCollectAllSlot(), createCollectAllItem(minion));
        inv.setItem(MinionGUIConfig.getUpgradeTierSlot(), createQuickUpgradeItem(minion));
        inv.setItem(MinionGUIConfig.getPickupSlot(), createPickupItem());

        player.openInventory(inv);
    }

    private static ItemStack createIdealLayoutItem() {
        return createNamedItem(Material.REDSTONE_TORCH, ColorUtil.color("&aIdeal Layout"),
                List.of(ColorUtil.color("&7Click to view the optimal 5x5 block setup"),
                        ColorUtil.color("&7and environment required around the minion"),
                        ColorUtil.color("&7for it to harvest and spawn correctly."),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&eClick to view layout guide!")));
    }

    private static ItemStack createHeadItem(Minion minion) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (meta != null) {
            String texture = null;
            if (minion.getCustomDefinitionId() != null) {
                MinionDefinition def = MinionRegistry.get(minion.getCustomDefinitionId());
                if (def != null) {
                    texture = def.getSkullTexture(minion.getTier());
                }
            } else if (minion.getType() != null) {
                texture = minion.getType().getSkullTexture();
            }

            if (texture != null && !texture.isEmpty()) {
                try {
                    PlayerProfile profile = Bukkit.createProfile(UUID.nameUUIDFromBytes(texture.getBytes(StandardCharsets.UTF_8)));
                    profile.setProperty(new ProfileProperty("textures", texture));
                    meta.setPlayerProfile(profile);
                } catch (Exception ignored) {}
            }

            meta.setDisplayName(ColorUtil.color("&a" + minion.getDisplayName() + " Minion " + MinionTier.toRoman(minion.getTier())));

            FileConfiguration config = MinionX.getInstance() != null ? MinionX.getInstance().getConfig() : null;
            List<String> configuredLore = config != null ? config.getStringList("gui.head-lore") : null;

            List<String> lore = new ArrayList<>();
            if (configuredLore != null && !configuredLore.isEmpty()) {
                for (String line : configuredLore) {
                    lore.add(ColorUtil.color(line
                            .replace("%delay%", String.format("%.1f", minion.getEffectiveActionDelay()))
                            .replace("%harvested%", String.valueOf(minion.getTotalHarvested()))
                            .replace("%stored%", String.valueOf(minion.getTotalStoredCount()))
                            .replace("%capacity%", String.valueOf(minion.getMaxCapacity()))
                            .replace("%status%", minion.getState().getDisplayStatus())));
                }
            } else {
                lore.add(ColorUtil.color("&7Current Tier: &e" + MinionTier.toRoman(minion.getTier())));
                lore.add(ColorUtil.color("&7Time Between Actions: &a" + String.format("%.1f", minion.getEffectiveActionDelay()) + "s"));
                lore.add(ColorUtil.color("&7Max Storage: &e" + minion.getMaxCapacity() + " items &7(" + minion.getStorageSlots() + " slots)"));
                lore.add(ColorUtil.color("&7Total Harvested: &e" + minion.getTotalHarvested()));
                lore.add(ColorUtil.color("&7Items Stored: &f" + minion.getTotalStoredCount() + " &7/ &e" + minion.getMaxCapacity()));
                lore.add(ColorUtil.color("&7Status: " + minion.getState().getDisplayStatus()));
            }

            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack createNextTierInfoItem(Minion minion) {
        int currentTier = minion.getTier();
        if (currentTier >= minion.getMaxTier()) {
            return createNamedItem(Material.BEDROCK, ColorUtil.color("&cMax Tier Reached"),
                    List.of(ColorUtil.color("&7This minion has attained its"),
                            ColorUtil.color("&7highest possible tier (" + MinionTier.toRoman(currentTier) + ")!")));
        }

        int nextTier = currentTier + 1;
        MinionDefinition def = minion.getDefinition();
        double curDelay = minion.getBaseActionDelay();
        double nextDelay = def != null ? def.getActionDelay(nextTier) : MinionTier.getActionDelay(nextTier);
        int curCap = minion.getMaxCapacity();
        int nextCap = def != null ? def.getMaxCapacity(nextTier) : MinionTier.getMaxCapacity(nextTier);

        UpgradeCostHelper.Requirement req = UpgradeCostHelper.getRequirement(minion);
        String costDisplay = req.getRequiredAmount() + "x " + req.getDisplayName();

        return createNamedItem(Material.GOLD_INGOT, ColorUtil.color("&aNext Tier: &eTier " + MinionTier.toRoman(nextTier)),
                List.of(ColorUtil.color("&7Action Delay: &f" + String.format("%.1f", curDelay) + "s &8-> &a" + String.format("%.1f", nextDelay) + "s"),
                        ColorUtil.color("&7Storage: &f" + curCap + " &8-> &a" + nextCap),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&7Crafting Cost: &e" + costDisplay),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&eClick or use Quick-Upgrade below to upgrade!")));
    }

    private static ItemStack createEmptySkinPane() {
        return createNamedItem(Material.LIME_STAINED_GLASS_PANE, ColorUtil.color("&aMinion Skin"),
                List.of(ColorUtil.color("&7A cosmetic slot where you can equip"),
                        ColorUtil.color("&7a skin to change the visual appearance"),
                        ColorUtil.color("&7of the minion on your island."),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&eClick with a skin item to equip!")));
    }

    public static ItemStack createActiveSkinItem(com.minionx.api.MinionSkin skin) {
        if (skin == null) return createEmptySkinPane();
        ItemStack item = com.minionx.item.MinionItemFactory.createSkinItem(skin);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add(ColorUtil.color("&7"));
            lore.add(ColorUtil.color("&aCurrently Equipped"));
            lore.add(ColorUtil.color("&eClick to remove and retrieve skin."));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack createEmptyFuelPane() {
        return createNamedItem(Material.ORANGE_STAINED_GLASS_PANE, ColorUtil.color("&6Fuel"),
                List.of(ColorUtil.color("&7A slot used for placing Fuel items"),
                        ColorUtil.color("&7(such as Coal or an Enchanted Lava Bucket)"),
                        ColorUtil.color("&7to boost the minion's production speed."),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&eClick or Shift-Click with fuel to insert!")));
    }

    private static ItemStack createActiveFuelItem(Minion minion) {
        CustomFuel fuel = minion.getCustomFuel();
        String name = fuel != null ? fuel.getDisplayName() : minion.getFuelId();
        int buff = fuel != null ? (int) (fuel.getSpeedBuff() * 100) : 0;
        String timeLeft = minion.isFuelPermanent() ? "Permanent" : minion.getFormattedFuelTimeRemaining();

        boolean destroyOnRemoval = MinionX.getInstance() != null &&
                MinionX.getInstance().getConfig().getBoolean("settings.destroy-temporary-fuel-on-removal", true);

        List<String> lore = new ArrayList<>();
        lore.add(ColorUtil.color("&7Speed Boost: &a+" + buff + "%"));
        lore.add(ColorUtil.color("&7Time Remaining: &e" + timeLeft));
        lore.add(ColorUtil.color("&7"));
        if (minion.isFuelPermanent()) {
            lore.add(ColorUtil.color("&eClick to retrieve fuel!"));
        } else if (destroyOnRemoval) {
            lore.add(ColorUtil.color("&cOne-Time Use Fuel:"));
            lore.add(ColorUtil.color("&7Removing this fuel will burn out"));
            lore.add(ColorUtil.color("&7and consume the active time!"));
            lore.add(ColorUtil.color("&7"));
            lore.add(ColorUtil.color("&eClick with more &f" + (fuel != null ? fuel.getDisplayName() : "fuel") + " &eto extend duration!"));
        } else {
            lore.add(ColorUtil.color("&eClick to retrieve fuel!"));
        }

        return createNamedItem(Material.LAVA_BUCKET, ColorUtil.color("&aFuel Active: " + name), lore);
    }

    private static ItemStack createActiveShippingItem(Minion minion) {
        CustomUpgrade hopper = minion.getShippingUpgrade();
        String name = hopper != null ? hopper.getDisplayName() : "&eAutomated Shipping";
        double ratio = (hopper != null && hopper.getSellRatio() > 0) ? hopper.getSellRatio() * 100.0 : 50.0;
        double coins = minion.getShippingCoins();

        List<String> lore = new ArrayList<>();
        lore.add(ColorUtil.color("&7Equipped: &f" + (hopper != null ? hopper.getDisplayName() : "Hopper")));
        lore.add(ColorUtil.color("&7Sell Ratio: &e" + (int) Math.round(ratio) + "% of NPC sell price"));
        lore.add(ColorUtil.color("&7Stored Coins: &6" + String.format("%,.1f", coins) + " coins"));
        lore.add(ColorUtil.color("&7"));
        if (coins > 0) {
            lore.add(ColorUtil.color("&eClick to collect stored coins!"));
        }
        lore.add(ColorUtil.color("&cShift-Click with empty hand to remove hopper!"));

        return createNamedItem(Material.HOPPER, ColorUtil.color(name), lore);
    }

    private static ItemStack createShippingPane() {
        return createNamedItem(Material.YELLOW_STAINED_GLASS_PANE, ColorUtil.color("&eAutomated Shipping"),
                List.of(ColorUtil.color("&7A slot to equip hoppers (like the"),
                        ColorUtil.color("&7Budget Hopper or Enchanted Hopper)."),
                        ColorUtil.color("&7Automatically sells overflow resources for"),
                        ColorUtil.color("&7coins when minion and storage are full!"),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&eClick with a hopper on cursor to equip!")));
    }

    private static ItemStack createUpgradeSlotItem(CustomUpgrade upgrade, int slotIndex) {
        if (upgrade == null) {
            return createNamedItem(Material.BLUE_STAINED_GLASS_PANE, ColorUtil.color("&9Minion Upgrade " + slotIndex),
                    List.of(ColorUtil.color("&7Utility slot for function-altering upgrades."),
                            ColorUtil.color("&7Equip items like a Super Compactor 3000,"),
                            ColorUtil.color("&7Diamond Spreading, Auto-Smelter,"),
                            ColorUtil.color("&7or Minion Expander!"),
                            ColorUtil.color("&7"),
                            ColorUtil.color("&eClick or Shift-Click with an upgrade to insert!")));
        }

        return createNamedItem(upgrade.getMaterial(), ColorUtil.color("&a" + upgrade.getDisplayName()),
                List.of(ColorUtil.color("&7Active upgrade installed."),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&eClick to retrieve upgrade!")));
    }

    private static ItemStack createLockedStoragePane(Minion minion, int slotIndex) {
        int unlockTier = getUnlockTierForStorageSlot(minion, slotIndex);
        String roman = MinionTier.toRoman(unlockTier);
        return createNamedItem(Material.RED_STAINED_GLASS_PANE, ColorUtil.color("&cLocked Slot &7(Tier " + roman + ")"),
                List.of(ColorUtil.color("&7Requires &eTier " + roman + " &7to unlock!"),
                        ColorUtil.color("&7Upgrade this minion to unlock more storage.")));
    }

    private static int getUnlockTierForStorageSlot(Minion minion, int slotIndex) {
        for (int t = 1; t <= minion.getMaxTier(); t++) {
            int slotsAtTier = minion.getDefinition() != null ? minion.getDefinition().getStorageSlots(t) : MinionTier.getStorageSlots(t);
            if (slotsAtTier > slotIndex) {
                return t;
            }
        }
        return minion.getMaxTier();
    }

    private static ItemStack createCollectAllItem(Minion minion) {
        int count = minion.getTotalStoredCount();
        return createNamedItem(Material.CHEST, ColorUtil.color("&aCollect All &7(" + count + ")"),
                List.of(ColorUtil.color("&7Clicking this gathers all the resources"),
                        ColorUtil.color("&7currently stored inside the minion and its"),
                        ColorUtil.color("&7storage and transfers them directly into your inventory."),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&eClick to collect all!")));
    }

    private static ItemStack createQuickUpgradeItem(Minion minion) {
        int currentTier = minion.getTier();
        if (currentTier >= minion.getMaxTier()) {
            return createNamedItem(Material.BEDROCK, ColorUtil.color("&cMax Tier Reached"),
                    List.of(ColorUtil.color("&7This minion has attained its"),
                            ColorUtil.color("&7absolute maximum tier (" + MinionTier.toRoman(currentTier) + ")!")));
        }

        int nextTier = currentTier + 1;
        UpgradeCostHelper.Requirement req = UpgradeCostHelper.getRequirement(minion);
        String costDisplay = req.getRequiredAmount() + "x " + req.getDisplayName();

        return createNamedItem(Material.DIAMOND, ColorUtil.color("&aQuick-Upgrade Minion"),
                List.of(ColorUtil.color("&7Clicking this instantly levels up the"),
                        ColorUtil.color("&7minion to &eTier " + MinionTier.toRoman(nextTier) + "&7, provided you have"),
                        ColorUtil.color("&7the required resources in your personal inventory."),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&7Required: &e" + costDisplay),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&eClick to quick-upgrade!")));
    }

    private static ItemStack createPickupItem() {
        return createNamedItem(Material.BEDROCK, ColorUtil.color("&cPickup Minion"),
                List.of(ColorUtil.color("&7Clicking this instantly un-places the"),
                        ColorUtil.color("&7minion and returns it, along with any equipped"),
                        ColorUtil.color("&7items or stored resources, back to your inventory."),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&cClick to pickup minion!")));
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
