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
 * Supports modern MythicMobs v5 (MythicBukkit APIHelper / MobManager) and legacy v4 with cached reflection handles.
 */
public class MythicMobsHook {

    public static final NamespacedKey MINION_MOB_KEY = new NamespacedKey("minionx", "minion_spawned_mob");
    public static final NamespacedKey MINION_OWNER_KEY = new NamespacedKey("minionx", "minion_owner_id");

    private static boolean mythicMobsAvailable = false;
    private static Object apiHelperInstance = null;
    private static Method spawnMythicMobMethod = null;

    // Fallback via MobManager -> ActiveMob -> BukkitEntity
    private static Object mobManagerInstance = null;
    private static Method spawnMobMethod = null;
    private static Method getEntityMethod = null;
    private static Method getBukkitEntityMethod = null;

    static {
        checkHooks();
    }

    public static synchronized void checkHooks() {
        mythicMobsAvailable = false;
        apiHelperInstance = null;
        spawnMythicMobMethod = null;
        mobManagerInstance = null;
        spawnMobMethod = null;
        getEntityMethod = null;
        getBukkitEntityMethod = null;

        if (Bukkit.getServer() == null || Bukkit.getPluginManager() == null) return;
        if (!Bukkit.getPluginManager().isPluginEnabled("MythicMobs")) return;

        // 1. Try modern MythicMobs v5 (io.lumine.mythic.bukkit.MythicBukkit)
        try {
            Class<?> mythicBukkitClass = Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
            Object mythicBukkit;
            try {
                mythicBukkit = mythicBukkitClass.getMethod("inst").invoke(null);
            } catch (NoSuchMethodException e) {
                mythicBukkit = mythicBukkitClass.getMethod("get").invoke(null);
            }

            if (mythicBukkit != null) {
                // Check getAPIHelper().spawnMythicMob(String, Location)
                try {
                    Method getApi = mythicBukkitClass.getMethod("getAPIHelper");
                    apiHelperInstance = getApi.invoke(mythicBukkit);
                    if (apiHelperInstance != null) {
                        spawnMythicMobMethod = apiHelperInstance.getClass().getMethod("spawnMythicMob", String.class, Location.class);
                        mythicMobsAvailable = true;
                        return;
                    }
                } catch (Throwable ignored) {}

                // Fallback to getMobManager().spawnMob(String, Location)
                try {
                    Method getMobManager = mythicBukkitClass.getMethod("getMobManager");
                    mobManagerInstance = getMobManager.invoke(mythicBukkit);
                    if (mobManagerInstance != null) {
                        spawnMobMethod = mobManagerInstance.getClass().getMethod("spawnMob", String.class, Location.class);
                        mythicMobsAvailable = true;
                        return;
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}

        // 2. Try legacy MythicMobs v4 (io.lumine.xikage.mythicmobs.MythicMobs)
        try {
            Class<?> mmClass = Class.forName("io.lumine.xikage.mythicmobs.MythicMobs");
            Object mm = mmClass.getMethod("inst").invoke(null);
            if (mm != null) {
                Method getApi = mmClass.getMethod("getAPIHelper");
                apiHelperInstance = getApi.invoke(mm);
                if (apiHelperInstance != null) {
                    spawnMythicMobMethod = apiHelperInstance.getClass().getMethod("spawnMythicMob", String.class, Location.class);
                    mythicMobsAvailable = true;
                }
            }
        } catch (Throwable ignored) {}
    }

    public static boolean isMythicMobsEnabled() {
        if (Bukkit.getServer() == null || Bukkit.getPluginManager() == null) return false;
        return Bukkit.getPluginManager().isPluginEnabled("MythicMobs");
    }

    /**
     * Spawns a mob (either via MythicMobs by mob ID or vanilla EntityType fallback).
     */
    public static Entity spawnMob(String mobIdOrType, Location location, boolean disableAi) {
        return spawnMob(mobIdOrType, location, disableAi, null);
    }

    /**
     * Spawns a mob with explicit minion owner attribution.
     */
    public static Entity spawnMob(String mobIdOrType, Location location, boolean disableAi, java.util.UUID minionId) {
        if (mobIdOrType == null || location == null || location.getWorld() == null) return null;

        Entity spawnedEntity = null;

        // 1. Try MythicMobs if available (cached reflection)
        if (mythicMobsAvailable) {
            if (apiHelperInstance != null && spawnMythicMobMethod != null) {
                try {
                    Object result = spawnMythicMobMethod.invoke(apiHelperInstance, mobIdOrType, location);
                    if (result instanceof Entity bukkitEnt) {
                        spawnedEntity = bukkitEnt;
                    }
                } catch (Throwable ignored) {}
            } else if (mobManagerInstance != null && spawnMobMethod != null) {
                try {
                    Object activeMob = spawnMobMethod.invoke(mobManagerInstance, mobIdOrType, location);
                    if (activeMob != null) {
                        if (getEntityMethod == null) {
                            getEntityMethod = activeMob.getClass().getMethod("getEntity");
                        }
                        Object abstractEntity = getEntityMethod.invoke(activeMob);
                        if (abstractEntity != null) {
                            if (getBukkitEntityMethod == null) {
                                getBukkitEntityMethod = abstractEntity.getClass().getMethod("getBukkitEntity");
                            }
                            spawnedEntity = (Entity) getBukkitEntityMethod.invoke(abstractEntity);
                        }
                    }
                } catch (Throwable ignored) {}
            }
        }

        // 2. Vanilla EntityType fallback
        if (spawnedEntity == null) {
            try {
                EntityType type = EntityType.valueOf(mobIdOrType.toUpperCase());
                spawnedEntity = location.getWorld().spawnEntity(location, type);
            } catch (IllegalArgumentException ignored) {}
        }

        // 3. Apply tag, owner attribution, gravity, persistence, and optional AI disabling
        if (spawnedEntity != null) {
            spawnedEntity.getPersistentDataContainer().set(MINION_MOB_KEY, PersistentDataType.BYTE, (byte) 1);
            if (minionId != null) {
                spawnedEntity.getPersistentDataContainer().set(MINION_OWNER_KEY, PersistentDataType.STRING, minionId.toString());
            }
            spawnedEntity.setGravity(true);
            if (spawnedEntity instanceof LivingEntity living) {
                living.setRemoveWhenFarAway(false);
                living.setPersistent(true);
                if (disableAi) {
                    living.setAI(false);
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
