package com.skyblock.minions.visual;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.skyblock.minions.SkyBlockMinions;
import com.skyblock.minions.core.Minion;
import org.bukkit.Bukkit;
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
        // Center the armor stand precisely in the middle of the block
        loc.setX(loc.getBlockX() + 0.5);
        loc.setZ(loc.getBlockZ() + 0.5);

        // Despawn existing if present
        remove(minion);

        ArmorStand stand = loc.getWorld().spawn(loc, ArmorStand.class, as -> {
            as.setSmall(true);
            as.setArms(true);
            as.setBasePlate(false);
            as.setInvulnerable(true);
            as.setGravity(false);
            as.setCollidable(false);
            as.setRemoveWhenFarAway(false);
            as.setPersistent(true);

            // Tag with minion UUID in PersistentDataContainer
            as.getPersistentDataContainer().set(MINION_ENTITY_KEY, PersistentDataType.STRING, minion.getMinionId().toString());

            // Equip custom textured skull
            as.getEquipment().setHelmet(createCustomSkull(minion.getType().getSkullTexture()));

            // Equip dyed leather armor
            as.getEquipment().setChestplate(createDyedArmor(Material.LEATHER_CHESTPLATE, minion.getType().getArmorColor()));
            as.getEquipment().setLeggings(createDyedArmor(Material.LEATHER_LEGGINGS, minion.getType().getArmorColor()));
            as.getEquipment().setBoots(createDyedArmor(Material.LEATHER_BOOTS, minion.getType().getArmorColor()));

            // Equip appropriate pickaxe tool
            as.getEquipment().setItemInMainHand(new ItemStack(minion.getType().getToolMaterial()));
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

        // Also clean up any lingering armor stand with matching PDC tag in that chunk
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

    private static ItemStack createDyedArmor(Material material, org.bukkit.Color color) {
        ItemStack armor = new ItemStack(material);
        LeatherArmorMeta meta = (LeatherArmorMeta) armor.getItemMeta();
        if (meta != null) {
            meta.setColor(color);
            armor.setItemMeta(meta);
        }
        return armor;
    }
}
