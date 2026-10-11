package com.skyblock.minions.item;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.skyblock.minions.SkyBlockMinions;
import com.skyblock.minions.api.*;
import com.skyblock.minions.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Creates custom items representing Minions, Upgrades, and Fuels.
 * Supports configurable lores from config.yml.
 */
public class MinionItemFactory {

    public static final NamespacedKey IS_MINION_KEY = new NamespacedKey("skyblockminions", "is_minion");
    public static final NamespacedKey MINION_TYPE_KEY = new NamespacedKey("skyblockminions", "minion_type");
    public static final NamespacedKey MINION_TIER_KEY = new NamespacedKey("skyblockminions", "minion_tier");
    public static final NamespacedKey MINION_HARVESTED_KEY = new NamespacedKey("skyblockminions", "minion_harvested");
    public static final NamespacedKey CUSTOM_MINION_ID_KEY = new NamespacedKey("skyblockminions", "custom_minion_id");
    public static final NamespacedKey UPGRADE_KEY = new NamespacedKey("skyblockminions", "upgrade_type");
    public static final NamespacedKey FUEL_KEY = new NamespacedKey("skyblockminions", "fuel_type");

    /**
     * Creates a placeable minion item for a built-in MinionType.
     */
    public static ItemStack createMinionItem(MinionType type, int tier, int harvested) {
        tier = Math.clamp(tier, 1, MinionTier.getMaxTier());
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();

        if (meta != null) {
            String texture = type.getSkullTexture();
            if (texture != null && !texture.isEmpty()) {
                try {
                    PlayerProfile profile = Bukkit.createProfile(UUID.nameUUIDFromBytes(texture.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                    profile.setProperty(new ProfileProperty("textures", texture));
                    meta.setPlayerProfile(profile);
                } catch (Exception ignored) {}
            }

            String roman = MinionTier.toRoman(tier);
            meta.setDisplayName(ColorUtil.color("&a" + type.getDisplayName() + " Minion " + roman));

            FileConfiguration config = SkyBlockMinions.getInstance() != null ? SkyBlockMinions.getInstance().getConfig() : null;
            List<String> configuredLore = config != null ? config.getStringList("item-lore.minion-item") : null;

            List<String> lore = new ArrayList<>();
            if (configuredLore != null && !configuredLore.isEmpty()) {
                for (String line : configuredLore) {
                    lore.add(ColorUtil.color(line
                            .replace("%type%", type.getDisplayName())
                            .replace("%tier%", String.valueOf(tier))
                            .replace("%tier_roman%", roman)
                            .replace("%delay%", String.format("%.1f", MinionTier.getActionDelay(tier)))
                            .replace("%capacity%", String.valueOf(MinionTier.getMaxCapacity(tier)))
                            .replace("%slots%", String.valueOf(MinionTier.getStorageSlots(tier)))
                            .replace("%harvested%", String.valueOf(harvested))));
                }
            } else {
                lore.add(ColorUtil.color("&7Time Between Actions: &a" + MinionTier.getActionDelay(tier) + "s"));
                lore.add(ColorUtil.color("&7Max Storage: &e" + MinionTier.getMaxCapacity(tier) + " items &7(" + MinionTier.getStorageSlots(tier) + " slots)"));
                lore.add(ColorUtil.color("&7"));
                lore.add(ColorUtil.color("&7Place this minion down to generate"));
                lore.add(ColorUtil.color("&7and harvest resources on your island!"));
                lore.add(ColorUtil.color("&7"));
                lore.add(ColorUtil.color("&eRight-click on the ground to place!"));
            }

            meta.setLore(lore);

            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(IS_MINION_KEY, PersistentDataType.BYTE, (byte) 1);
            pdc.set(MINION_TYPE_KEY, PersistentDataType.STRING, type.name());
            pdc.set(MINION_TIER_KEY, PersistentDataType.INTEGER, tier);
            pdc.set(MINION_HARVESTED_KEY, PersistentDataType.INTEGER, harvested);

            skull.setItemMeta(meta);
        }

        return skull;
    }

    /**
     * Creates a placeable minion item for a custom data-driven MinionDefinition.
     */
    public static ItemStack createCustomMinionItem(MinionDefinition def, int tier, int harvested) {
        if (def == null) return new ItemStack(Material.PLAYER_HEAD);
        tier = Math.clamp(tier, 1, MinionTier.getMaxTier());
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();

        if (meta != null) {
            String texture = def.getSkullTexture(tier);
            if (texture != null && !texture.isEmpty()) {
                try {
                    PlayerProfile profile = Bukkit.createProfile(UUID.nameUUIDFromBytes(texture.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                    profile.setProperty(new ProfileProperty("textures", texture));
                    meta.setPlayerProfile(profile);
                } catch (Exception ignored) {}
            }

            String roman = MinionTier.toRoman(tier);
            meta.setDisplayName(ColorUtil.color("&a" + def.getDisplayName() + " Minion " + roman));

            FileConfiguration config = SkyBlockMinions.getInstance() != null ? SkyBlockMinions.getInstance().getConfig() : null;
            List<String> configuredLore = config != null ? config.getStringList("item-lore.minion-item") : null;

            List<String> lore = new ArrayList<>();
            double delay = def.getDefaultActionDelay();
            int slots = def.getDefaultStorageSlots();
            int capacity = slots * 64;

            if (configuredLore != null && !configuredLore.isEmpty()) {
                for (String line : configuredLore) {
                    lore.add(ColorUtil.color(line
                            .replace("%type%", def.getDisplayName())
                            .replace("%tier%", String.valueOf(tier))
                            .replace("%tier_roman%", roman)
                            .replace("%delay%", String.format("%.1f", delay))
                            .replace("%capacity%", String.valueOf(capacity))
                            .replace("%slots%", String.valueOf(slots))
                            .replace("%harvested%", String.valueOf(harvested))));
                }
            } else {
                lore.add(ColorUtil.color("&7Time Between Actions: &a" + String.format("%.1f", delay) + "s"));
                lore.add(ColorUtil.color("&7Max Storage: &e" + capacity + " items &7(" + slots + " slots)"));
                lore.add(ColorUtil.color("&7Archetype: &e" + def.getArchetype().name()));
                lore.add(ColorUtil.color("&7"));
                lore.add(ColorUtil.color("&7Place this minion down to generate"));
                lore.add(ColorUtil.color("&7and harvest resources on your island!"));
                lore.add(ColorUtil.color("&7"));
                lore.add(ColorUtil.color("&eRight-click on the ground to place!"));
            }

            meta.setLore(lore);

            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(IS_MINION_KEY, PersistentDataType.BYTE, (byte) 1);
            pdc.set(MINION_TYPE_KEY, PersistentDataType.STRING, "COBBLESTONE");
            pdc.set(CUSTOM_MINION_ID_KEY, PersistentDataType.STRING, def.getId());
            pdc.set(MINION_TIER_KEY, PersistentDataType.INTEGER, tier);
            pdc.set(MINION_HARVESTED_KEY, PersistentDataType.INTEGER, harvested);

            skull.setItemMeta(meta);
        }

        return skull;
    }

    public static boolean isMinionItem(ItemStack item) {
        if (item == null || item.getType() != Material.PLAYER_HEAD) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(IS_MINION_KEY, PersistentDataType.BYTE);
    }

    public static MinionType getMinionType(ItemStack item) {
        if (!isMinionItem(item)) return null;
        String typeStr = item.getItemMeta().getPersistentDataContainer().get(MINION_TYPE_KEY, PersistentDataType.STRING);
        return MinionType.fromString(typeStr);
    }

    public static String getCustomMinionId(ItemStack item) {
        if (!isMinionItem(item)) return null;
        return item.getItemMeta().getPersistentDataContainer().get(CUSTOM_MINION_ID_KEY, PersistentDataType.STRING);
    }

    public static int getMinionTier(ItemStack item) {
        if (!isMinionItem(item)) return 1;
        Integer tier = item.getItemMeta().getPersistentDataContainer().get(MINION_TIER_KEY, PersistentDataType.INTEGER);
        return tier != null ? tier : 1;
    }

    public static int getMinionHarvested(ItemStack item) {
        if (!isMinionItem(item)) return 0;
        Integer count = item.getItemMeta().getPersistentDataContainer().get(MINION_HARVESTED_KEY, PersistentDataType.INTEGER);
        return count != null ? count : 0;
    }

    public static String getFuelId(ItemStack item) {
        if (item == null) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(FUEL_KEY, PersistentDataType.STRING);
    }

    public static String getUpgradeId(ItemStack item) {
        if (item == null) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(UPGRADE_KEY, PersistentDataType.STRING);
    }

    /**
     * Creates a custom Upgrade item from a CustomUpgrade definition.
     */
    public static ItemStack createUpgradeItem(CustomUpgrade upgrade) {
        if (upgrade == null) return new ItemStack(Material.AIR);
        ItemStack item = new ItemStack(upgrade.getMaterial());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorUtil.color(upgrade.getDisplayName()));
            List<String> lore = new ArrayList<>(upgrade.getLore());
            lore.add(ColorUtil.color("&7"));
            lore.add(ColorUtil.color("&ePlace in a Minion's upgrade slot!"));
            meta.setLore(lore);

            if (upgrade.isGlowing()) {
                meta.addEnchant(Enchantment.DURABILITY, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }

            if (upgrade.getCustomModelData() > 0) {
                meta.setCustomModelData(upgrade.getCustomModelData());
            }

            meta.getPersistentDataContainer().set(UPGRADE_KEY, PersistentDataType.STRING, upgrade.getId());
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Creates a custom Upgrade item (Auto-Smelter, Compactor, Diamond Spreading, etc.).
     */
    public static ItemStack createUpgradeItem(MinionUpgradeType upgrade) {
        CustomUpgrade custom = UpgradeRegistry.get(upgrade.name().toLowerCase());
        if (custom != null) {
            return createUpgradeItem(custom);
        }
        ItemStack item = new ItemStack(upgrade.getIcon());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorUtil.color("&6" + upgrade.getDisplayName()));
            List<String> lore = new ArrayList<>();
            lore.add(ColorUtil.color("&7" + upgrade.getDescription()));
            lore.add(ColorUtil.color("&7"));
            lore.add(ColorUtil.color("&ePlace in a Minion's upgrade slot!"));
            meta.setLore(lore);

            meta.addEnchant(Enchantment.DURABILITY, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

            meta.getPersistentDataContainer().set(UPGRADE_KEY, PersistentDataType.STRING, upgrade.name().toLowerCase());
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Creates a custom Fuel item from a CustomFuel definition.
     */
    public static ItemStack createFuelItem(CustomFuel fuel) {
        if (fuel == null) return new ItemStack(Material.AIR);
        ItemStack item = new ItemStack(fuel.getMaterial());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorUtil.color(fuel.getDisplayName()));
            List<String> lore = new ArrayList<>(fuel.getLore());
            lore.add(ColorUtil.color("&7"));
            lore.add(ColorUtil.color("&ePlace in a Minion's fuel slot!"));
            meta.setLore(lore);

            if (fuel.isGlowing()) {
                meta.addEnchant(Enchantment.DURABILITY, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }

            if (fuel.getCustomModelData() > 0) {
                meta.setCustomModelData(fuel.getCustomModelData());
            }

            meta.getPersistentDataContainer().set(FUEL_KEY, PersistentDataType.STRING, fuel.getId());
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Creates a custom Fuel item.
     */
    public static ItemStack createFuelItem(MinionFuelType fuel) {
        CustomFuel custom = FuelRegistry.get(fuel.name().toLowerCase());
        if (custom != null) {
            return createFuelItem(custom);
        }
        ItemStack item = new ItemStack(fuel.getMaterial());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorUtil.color("&b" + fuel.getDisplayName()));
            List<String> lore = new ArrayList<>();
            lore.add(ColorUtil.color("&7Increases minion speed by &a+" + (int) (fuel.getSpeedBuff() * 100) + "%.&7"));
            lore.add(ColorUtil.color("&7Duration: &e" + (fuel.isPermanent() ? "Permanent" : (fuel.getDurationMillis() / (3600 * 1000L)) + " hours")));
            lore.add(ColorUtil.color("&7"));
            lore.add(ColorUtil.color("&ePlace in a Minion's fuel slot!"));
            meta.setLore(lore);

            meta.addEnchant(Enchantment.DURABILITY, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

            meta.getPersistentDataContainer().set(FUEL_KEY, PersistentDataType.STRING, fuel.name().toLowerCase());
            item.setItemMeta(meta);
        }
        return item;
    }
}
