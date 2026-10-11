package com.skyblock.minions.visual;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.skyblock.minions.api.MinionDefinition;
import com.skyblock.minions.api.MinionRegistry;
import com.skyblock.minions.core.Minion;
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
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

/**
 * Handles spawning, styling, and despawning of the visual ArmorStand minion entity.
 * Supports both standard MinionType and custom data-driven MinionDefinitions with per-tier styles.
 */
public class MinionVisual {

    public static final NamespacedKey MINION_ENTITY_KEY = new NamespacedKey("skyblockminions", "minion_entity_id");

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

        ArmorStand stand = loc.getWorld().spawn(loc, ArmorStand.class, as -> {
            as.setSmall(true);
            as.setArms(true);
            as.setBasePlate(false);
            as.setInvulnerable(true);
            as.setGravity(false);
            as.setCollidable(false);
            as.setRemoveWhenFarAway(false);
            as.setPersistent(true);

            as.getPersistentDataContainer().set(MINION_ENTITY_KEY, PersistentDataType.STRING, minion.getMinionId().toString());

            // Skull
            as.getEquipment().setHelmet(createCustomSkull(skullTexture));

            // Armor
            if (armorMat == Material.LEATHER_CHESTPLATE || armorMat == Material.LEATHER_LEGGINGS || armorMat == Material.LEATHER_BOOTS) {
                as.getEquipment().setChestplate(createDyedArmor(Material.LEATHER_CHESTPLATE, armorColor));
                as.getEquipment().setLeggings(createDyedArmor(Material.LEATHER_LEGGINGS, armorColor));
                as.getEquipment().setBoots(createDyedArmor(Material.LEATHER_BOOTS, armorColor));
            } else {
                as.getEquipment().setChestplate(new ItemStack(armorMat));
            }

            // Hand Tool / Weapon
            as.getEquipment().setItemInMainHand(handItem);
        });

        minion.setArmorStandId(stand.getUniqueId());
        return stand;
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
                    String tag = as.getPersistentDataContainer().get(MINION_ENTITY_KEY, PersistentDataType.STRING);
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
            PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID());
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