package com.skyblock.minions.gui;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.skyblock.minions.SkyBlockMinions;
import com.skyblock.minions.api.CustomFuel;
import com.skyblock.minions.api.CustomUpgrade;
import com.skyblock.minions.api.MinionDefinition;
import com.skyblock.minions.api.MinionRegistry;
import com.skyblock.minions.api.MinionTier;
import com.skyblock.minions.core.Minion;
import com.skyblock.minions.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Builds the authentic 54-slot Hypixel SkyBlock Minion chest interface.
 * All slots, materials, and items are customizable via config.yml.
 */
public class MinionGUI {

    public static final String GUI_TITLE_PREFIX = "Minion: ";

    /**
     * Creates and opens the Minion GUI for the player.
     */
    public static void open(Player player, Minion minion) {
        String title = GUI_TITLE_PREFIX + minion.getDisplayName() + " " + MinionTier.toRoman(minion.getTier());
        Inventory inv = Bukkit.createInventory(new MinionHolder(minion), 54, title);

        // Hypixel style: Sleek Black Stained Glass Pane borders
        ItemStack filler = createPane(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) {
            inv.setItem(i, filler);
        }

        // 1. Minion Head
        inv.setItem(MinionGUIConfig.getHeadSlot(), createHeadItem(minion));

        // 2. Fuel Slot
        int fuelSlot = MinionGUIConfig.getFuelSlot();
        if (minion.isFuelActive()) {
            inv.setItem(fuelSlot, createActiveFuelItem(minion));
        } else {
            inv.setItem(fuelSlot, createEmptyFuelItem());
        }

        // 3. Automated Shipping Slot
        inv.setItem(MinionGUIConfig.getAutomatedShippingSlot(), createNamedItem(Material.HOPPER, ColorUtil.color("&cAutomated Shipping &7(Locked)"),
                List.of(ColorUtil.color("&7Automatically sells excess items"), ColorUtil.color("&7when storage fills."))));

        // 4. Upgrade Slots
        inv.setItem(MinionGUIConfig.getUpgrade1Slot(), createUpgradeSlotItem(minion.getCustomUpgrade1(), 1));
        inv.setItem(MinionGUIConfig.getUpgrade2Slot(), createUpgradeSlotItem(minion.getCustomUpgrade2(), 2));

        // 5. Minion Skin Cosmetic Slot
        inv.setItem(MinionGUIConfig.getSkinSlot(), createNamedItem(Material.LEATHER_CHESTPLATE, ColorUtil.color("&dMinion Skin &7(None)"),
                List.of(ColorUtil.color("&7Customize your minion with"), ColorUtil.color("&7exclusive cosmetic skins!"))));

        // 6. Storage Slots
        int[] storageSlots = MinionGUIConfig.getStorageSlots();
        int unlockedSlots = MinionTier.getStorageSlots(minion.getTier());
        List<ItemStack> items = minion.getInventory();

        for (int i = 0; i < storageSlots.length; i++) {
            int slot = storageSlots[i];
            if (i < unlockedSlots) {
                if (i < items.size()) {
                    inv.setItem(slot, items.get(i).clone());
                } else {
                    inv.setItem(slot, new ItemStack(Material.AIR)); // Empty unlocked slot
                }
            } else {
                inv.setItem(slot, createLockedStoragePane(i + 1));
            }
        }

        // 7. Action Buttons
        inv.setItem(MinionGUIConfig.getUpgradeTierSlot(), createUpgradeButtonItem(minion));
        inv.setItem(MinionGUIConfig.getCollectAllSlot(), createCollectAllItem(minion));
        inv.setItem(MinionGUIConfig.getPickupSlot(), createPickupItem());

        player.openInventory(inv);
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
                    PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID());
                    profile.setProperty(new ProfileProperty("textures", texture));
                    meta.setPlayerProfile(profile);
                } catch (Exception ignored) {}
            }

            meta.setDisplayName(ColorUtil.color("&a" + minion.getDisplayName() + " Minion " + MinionTier.toRoman(minion.getTier())));

            FileConfiguration config = SkyBlockMinions.getInstance().getConfig();
            List<String> configuredLore = config.getStringList("gui.head-lore");

            List<String> lore = new ArrayList<>();
            if (configuredLore != null && !configuredLore.isEmpty()) {
                for (String line : configuredLore) {
                    lore.add(ColorUtil.color(line
                            .replace("%delay%", String.format("%.1f", minion.getEffectiveActionDelay()))
                            .replace("%harvested%", String.valueOf(minion.getTotalHarvested()))
                            .replace("%stored%", String.valueOf(minion.getTotalStoredCount()))
                            .replace("%capacity%", String.valueOf(MinionTier.getMaxCapacity(minion.getTier())))
                            .replace("%status%", minion.getState().getDisplayStatus())));
                }
            } else {
                lore.add(ColorUtil.color("&7Time Between Actions: &a" + String.format("%.1f", minion.getEffectiveActionDelay()) + "s"));
                lore.add(ColorUtil.color("&7Total Harvested: &e" + minion.getTotalHarvested()));
                lore.add(ColorUtil.color("&7Items Stored: &f" + minion.getTotalStoredCount() + " &7/ &e" + MinionTier.getMaxCapacity(minion.getTier())));
                lore.add(ColorUtil.color("&7Status: " + minion.getState().getDisplayStatus()));
            }

            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack createEmptyFuelItem() {
        return createNamedItem(Material.BUCKET, ColorUtil.color("&6Fuel Slot"),
                List.of(ColorUtil.color("&7Increase the speed of your"),
                        ColorUtil.color("&7minion by adding fuel!"),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&eClick with fuel on cursor or shift-click!"),
                        ColorUtil.color("&7Stackable fuels (Coal, etc.) add all quantity duration.")));
    }

    private static ItemStack createActiveFuelItem(Minion minion) {
        CustomFuel fuel = minion.getCustomFuel();
        String name = fuel != null ? fuel.getDisplayName() : minion.getFuelId();
        int buff = fuel != null ? (int) (fuel.getSpeedBuff() * 100) : 0;
        String timeLeft = minion.isFuelPermanent() ? "Permanent" : minion.getFormattedFuelTimeRemaining();

        boolean destroyOnRemoval = SkyBlockMinions.getInstance() != null &&
                SkyBlockMinions.getInstance().getConfig().getBoolean("settings.destroy-temporary-fuel-on-removal", true);

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

    private static ItemStack createUpgradeSlotItem(CustomUpgrade upgrade, int slotIndex) {
        if (upgrade == null) {
            return createNamedItem(Material.IRON_BARS, ColorUtil.color("&dUpgrade Slot " + slotIndex),
                    List.of(ColorUtil.color("&7Enhance your minion with upgrades"),
                            ColorUtil.color("&7like the Auto-Smelter, Compactor,"),
                            ColorUtil.color("&7or Diamond Spreading!"),
                            ColorUtil.color("&7"),
                            ColorUtil.color("&eClick with an upgrade to insert!")));
        }

        return createNamedItem(upgrade.getMaterial(), ColorUtil.color("&a" + upgrade.getDisplayName()),
                List.of(ColorUtil.color("&7Active upgrade installed."),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&eClick to retrieve upgrade!")));
    }

    private static ItemStack createLockedStoragePane(int slotIndex) {
        return createPane(Material.RED_STAINED_GLASS_PANE, ColorUtil.color("&cLocked Storage Slot &7(#" + slotIndex + ")"));
    }

    private static ItemStack createCollectAllItem(Minion minion) {
        int count = minion.getTotalStoredCount();
        return createNamedItem(Material.CHEST, ColorUtil.color("&aCollect All Items &7(" + count + ")"),
                List.of(ColorUtil.color("&7Click to instantly transfer all"),
                        ColorUtil.color("&7stored resources and vanilla XP"),
                        ColorUtil.color("&7into your inventory!"),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&eClick to collect!")));
    }

    private static ItemStack createUpgradeButtonItem(Minion minion) {
        int currentTier = minion.getTier();
        if (currentTier >= MinionTier.getMaxTier()) {
            return createNamedItem(Material.BEDROCK, ColorUtil.color("&cMax Tier Reached"),
                    List.of(ColorUtil.color("&7This minion has attained its"),
                            ColorUtil.color("&7absolute maximum tier (" + MinionTier.toRoman(currentTier) + ")!")));
        }

        int nextTier = currentTier + 1;
        int cost = MinionTier.getUpgradeCost(currentTier);

        Material mat = minion.getType().getDropMaterial();
        if (minion.getCustomDefinitionId() != null) {
            MinionDefinition def = MinionRegistry.get(minion.getCustomDefinitionId());
            if (def != null && def.getExactDropItem() != null) {
                mat = def.getExactDropItem().getType();
            }
        }

        String matName = mat.name().replace("_", " ").toLowerCase();

        return createNamedItem(Material.ANVIL, ColorUtil.color("&aUpgrade to Tier " + MinionTier.toRoman(nextTier)),
                List.of(ColorUtil.color("&7Action Delay: &f" + MinionTier.getActionDelay(currentTier) + "s &8-> &a" + MinionTier.getActionDelay(nextTier) + "s"),
                        ColorUtil.color("&7Storage: &f" + MinionTier.getMaxCapacity(currentTier) + " &8-> &a" + MinionTier.getMaxCapacity(nextTier)),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&7Cost: &e" + cost + "x " + matName),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&eClick with items to upgrade!")));
    }

    private static ItemStack createPickupItem() {
        return createNamedItem(Material.REDSTONE_BLOCK, ColorUtil.color("&cPickup Minion"),
                List.of(ColorUtil.color("&7Packs up the minion and delivers"),
                        ColorUtil.color("&7it back into your inventory."),
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
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }
}
