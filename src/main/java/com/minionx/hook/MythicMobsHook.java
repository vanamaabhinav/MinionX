package com.minionx.hook;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.lang.reflect.Method;

/**
 * Soft-dependency hook for spawning and managing MythicMobs and vanilla combat entities.
 */
public class MythicMobsHook {

    public static final NamespacedKey MINION_MOB_KEY = new NamespacedKey("minionx", "minion_spawned_mob");

    public static boolean isMythicMobsEnabled() {
        return Bukkit.getPluginManager().isPluginEnabled("MythicMobs");
    }

    /**
     * Spawns a mob (either via MythicMobs by mob ID or vanilla EntityType fallback).
     */
    public static Entity spawnMob(String mobIdOrType, Location location, boolean disableAi) {
        if (mobIdOrType == null || location == null || location.getWorld() == null) return null;

        Entity spawnedEntity = null;

        // 1. Try MythicMobs if enabled
        if (isMythicMobsEnabled()) {
            try {
                Class<?> mythicBukkitClass = Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
                Object mythicBukkit = mythicBukkitClass.getMethod("get").invoke(null);
                Object mobManager = mythicBukkit.getClass().getMethod("getMobManager").invoke(mythicBukkit);
                Method spawnMobMethod = mobManager.getClass().getMethod("spawnMob", String.class, Location.class);
                Object activeMob = spawnMobMethod.invoke(mobManager, mobIdOrType, location);

                if (activeMob != null) {
                    Method getEntityMethod = activeMob.getClass().getMethod("getEntity");
                    Object abstractEntity = getEntityMethod.invoke(activeMob);
                    Method getBukkitEntity = abstractEntity.getClass().getMethod("getBukkitEntity");
                    spawnedEntity = (Entity) getBukkitEntity.invoke(abstractEntity);
                }
            } catch (Throwable ignored) {}
        }

        // 2. Vanilla EntityType fallback
        if (spawnedEntity == null) {
            try {
                EntityType type = EntityType.valueOf(mobIdOrType.toUpperCase());
                spawnedEntity = location.getWorld().spawnEntity(location, type);
            } catch (IllegalArgumentException ignored) {}
        }

        // 3. Apply tag, gravity, persistence, and optional AI disabling
        if (spawnedEntity != null) {
            spawnedEntity.getPersistentDataContainer().set(MINION_MOB_KEY, PersistentDataType.BYTE, (byte) 1);
            spawnedEntity.setGravity(true);
            if (spawnedEntity instanceof LivingEntity living) {
                living.setRemoveWhenFarAway(false);
                living.setPersistent(true);
                if (disableAi) {
                    living.setAI(false);
                }
                // Prevent sunlight burn on undead mobs so daylight setups do not kill spawned mobs
                if (living.getEquipment() != null) {
                    if (living instanceof org.bukkit.entity.Zombie || living instanceof org.bukkit.entity.Skeleton) {
                        if (living.getEquipment().getHelmet() == null || living.getEquipment().getHelmet().getType().isAir()) {
                            living.getEquipment().setHelmet(new org.bukkit.inventory.ItemStack(org.bukkit.Material.LEATHER_HELMET));
                        }
                    }
                }
            }
        }

        return spawnedEntity;
    }

    public static boolean isMinionSpawnedMob(Entity entity) {
        if (entity == null) return false;
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        return pdc.has(MINION_MOB_KEY, PersistentDataType.BYTE);
    }
}
