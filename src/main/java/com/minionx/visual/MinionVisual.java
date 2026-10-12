package com.minionx.visual;

import com.minionx.util.ServerCompatibilityUtil;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.minionx.api.MinionDefinition;
import com.minionx.api.MinionRegistry;
import com.minionx.api.MinionSkin;
import com.minionx.core.Minion;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

/**
 * Handles spawning, styling, and despawning of the visual ArmorStand minion entity.
 * Supports both standard MinionType and custom data-driven MinionDefinitions with per-tier styles.
 */
public class MinionVisual {

    public static final NamespacedKey MINION_ENTITY_KEY = new NamespacedKey("minionx", "minion_entity_id");

    /**
     * Spawns or updates the visual ArmorStand for a minion.
     */
    public static ArmorStand spawnOrUpdate(Minion minion) {
        if (minion == null || minion.getLocation() == null || minion.getLocation().getWorld() == null) {
            return null;
        }

        Location loc = minion.getLocation().clone();
        loc.setX(loc.getBlockX() + 0.5);
        loc.setZ(loc.getBlockZ() + 0.5);

        // Despawn existing if present
        remove(minion);

        MinionDefinition def = minion.getCustomDefinitionId() != null
                ? MinionRegistry.get(minion.getCustomDefinitionId())
                : (minion.getType() != null ? MinionRegistry.get(minion.getType().name().toLowerCase()) : null);

        int tier = minion.getTier();

        String skullTexture = def != null ? def.getSkullTexture(tier) : (minion.getType() != null ? minion.getType().getSkullTexture() : null);
        Material armorMat = def != null ? def.getArmorMaterial(tier) : Material.LEATHER_CHESTPLATE;
        Color armorColor = def != null ? def.getArmorColor(tier) : (minion.getType() != null ? minion.getType().getArmorColor() : Color.fromRGB(120, 120, 120));
        ItemStack handItem = def != null ? def.getHandItem(tier) : (minion.getType() != null ? new ItemStack(minion.getType().getToolMaterial()) : new ItemStack(Material.STONE_PICKAXE));

        // Cosmetic Minion Skin Override
        if (minion.getSkinId() != null) {
            MinionSkin skin = minion.getSkin();
            if (skin != null) {
                if (skin.getSkullTexture() != null) {
                    skullTexture = skin.getSkullTexture();
                }
                if (skin.hasArmorMaterial()) {
                    armorMat = skin.getArmorMaterial();
                }
                if (skin.hasArmorColor()) {
                    armorColor = skin.getArmorColor();
                }
                if (skin.hasHandItem()) {
                    handItem = skin.getHandItem();
                }
            }
        }

        final String finalSkullTexture = skullTexture;
        final Material finalArmorMat = armorMat;
        final Color finalArmorColor = armorColor;
        final ItemStack finalHandItem = handItem;

        ArmorStand stand = loc.getWorld().spawn(loc, ArmorStand.class, as -> {
            as.setSmall(true);
            as.setArms(true);
            as.setBasePlate(false);
            as.setInvulnerable(true);
            as.setGravity(false);
            as.setCollidable(false);
            as.setRemoveWhenFarAway(false);
            as.setPersistent(true);
            ServerCompatibilityUtil.disableEntityTicking(as);

            as.getPersistentDataContainer().set(MINION_ENTITY_KEY, PersistentDataType.STRING, minion.getMinionId().toString());

            // Skull
            as.getEquipment().setHelmet(createCustomSkull(finalSkullTexture));

            // Armor (full set: chestplate, leggings, boots)
            equipMinionArmor(as, finalArmorMat, finalArmorColor);

            // Hand Tool / Weapon
            as.getEquipment().setItemInMainHand(finalHandItem);

            // Zero drop chances to guarantee zero item duplication if destroyed
            as.getEquipment().setHelmetDropChance(0.0f);
            as.getEquipment().setChestplateDropChance(0.0f);
            as.getEquipment().setLeggingsDropChance(0.0f);
            as.getEquipment().setBootsDropChance(0.0f);
            as.getEquipment().setItemInMainHandDropChance(0.0f);
            as.getEquipment().setItemInOffHandDropChance(0.0f);
        });

        minion.setArmorStandId(stand.getUniqueId());
        return stand;
    }

    /**
     * Resolves the matching chestplate material for a given armor material or preset.
     */
    public static Material getMatchingChestplate(Material armorMat) {
        if (armorMat == null || armorMat.isAir()) return null;
        String name = armorMat.name();
        if (name.startsWith("LEATHER_")) {
            return Material.LEATHER_CHESTPLATE;
        }
        String prefix = name.contains("_") ? name.substring(0, name.lastIndexOf('_')) : name;
        if (prefix.equalsIgnoreCase("GOLD")) {
            prefix = "GOLDEN";
        }
        Material match = Material.matchMaterial(prefix + "_CHESTPLATE");
        if (match != null) return match;
        if (name.endsWith("_CHESTPLATE")) return armorMat;
        return armorMat;
    }

    /**
     * Resolves the matching leggings material for a given armor material or preset.
     */
    public static Material getMatchingLeggings(Material armorMat) {
        if (armorMat == null || armorMat.isAir()) return null;
        String name = armorMat.name();
        if (name.startsWith("LEATHER_")) {
            return Material.LEATHER_LEGGINGS;
        }
        String prefix = name.contains("_") ? name.substring(0, name.lastIndexOf('_')) : name;
        if (prefix.equalsIgnoreCase("GOLD")) {
            prefix = "GOLDEN";
        }
        Material match = Material.matchMaterial(prefix + "_LEGGINGS");
        if (match != null) return match;
        if (name.endsWith("_LEGGINGS")) return armorMat;
        return null;
    }

    /**
     * Resolves the matching boots material for a given armor material or preset.
     */
    public static Material getMatchingBoots(Material armorMat) {
        if (armorMat == null || armorMat.isAir()) return null;
        String name = armorMat.name();
        if (name.startsWith("LEATHER_")) {
            return Material.LEATHER_BOOTS;
        }
        String prefix = name.contains("_") ? name.substring(0, name.lastIndexOf('_')) : name;
        if (prefix.equalsIgnoreCase("GOLD")) {
            prefix = "GOLDEN";
        }
        Material match = Material.matchMaterial(prefix + "_BOOTS");
        if (match != null) return match;
        if (name.endsWith("_BOOTS")) return armorMat;
        return null;
    }

    /**
     * Equips full armor (chestplate, leggings, boots) on the visual ArmorStand.
     * For leather, dyes the armor with the specified color.
     * For non-leather, equips matching chestplate, leggings, and boots.
     */
    public static void equipMinionArmor(ArmorStand as, Material armorMat, Color armorColor) {
        if (as == null || as.getEquipment() == null || armorMat == null || armorMat.isAir()) {
            return;
        }

        String matName = armorMat.name();
        if (matName.startsWith("LEATHER_")) {
            as.getEquipment().setChestplate(createDyedArmor(Material.LEATHER_CHESTPLATE, armorColor));
            as.getEquipment().setLeggings(createDyedArmor(Material.LEATHER_LEGGINGS, armorColor));
            as.getEquipment().setBoots(createDyedArmor(Material.LEATHER_BOOTS, armorColor));
            return;
        }

        Material chestMat = getMatchingChestplate(armorMat);
        Material legMat = getMatchingLeggings(armorMat);
        Material bootMat = getMatchingBoots(armorMat);

        if (chestMat != null) {
            as.getEquipment().setChestplate(new ItemStack(chestMat));
        } else {
            as.getEquipment().setChestplate(new ItemStack(armorMat));
        }

        if (legMat != null) {
            as.getEquipment().setLeggings(new ItemStack(legMat));
        }

        if (bootMat != null) {
            as.getEquipment().setBoots(new ItemStack(bootMat));
        }
    }

    /**
     * Removes the visual armor stand and cleans up.
     */
    public static void remove(Minion minion) {
        if (minion == null || minion.getLocation() == null || minion.getLocation().getWorld() == null) return;

        if (minion.getArmorStandId() != null) {
            Entity entity = Bukkit.getEntity(minion.getArmorStandId());
            if (entity != null && entity.isValid()) {
                entity.remove();
            }
        }

        Location loc = minion.getLocation();
        if (loc.isChunkLoaded()) {
            for (Entity nearby : loc.getWorld().getNearbyEntities(loc, 2.0, 2.0, 2.0)) {
                if (nearby instanceof ArmorStand as) {
                    PersistentDataContainer pdc = as.getPersistentDataContainer();
                    String tag = pdc.get(MINION_ENTITY_KEY, PersistentDataType.STRING);
                    if (tag != null && tag.equals(minion.getMinionId().toString())) {
                        as.remove();
                    }
                }
            }
        }
    }

    private static ItemStack createCustomSkull(String base64Texture) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        if (base64Texture == null || base64Texture.isEmpty()) return skull;

        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        if (meta != null) {
            PlayerProfile profile = Bukkit.createProfile(UUID.nameUUIDFromBytes(base64Texture.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            profile.setProperty(new ProfileProperty("textures", base64Texture));
            meta.setPlayerProfile(profile);
            skull.setItemMeta(meta);
        }
        return skull;
    }

    private static ItemStack createDyedArmor(Material material, Color color) {
        ItemStack armor = new ItemStack(material);
        LeatherArmorMeta meta = (LeatherArmorMeta) armor.getItemMeta();
        if (meta != null) {
            meta.setColor(color);
            armor.setItemMeta(meta);
        }
        return armor;
    }
}
