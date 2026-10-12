package com.minionx.hook;

import com.minionx.core.Minion;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Universal Collections Integration Hook.
 * Supports EcoCollections, SuperiorSkyblock2 Collections, and custom collection providers.
 */
public class CollectionsHook {

    private static boolean ecoCollectionsAvailable = false;
    private static boolean superiorSkyblockAvailable = false;

    public interface CollectionProvider {
        void onCollect(Player player, Minion minion, Material material, int amount);
    }

    private static final List<CollectionProvider> CUSTOM_PROVIDERS = new CopyOnWriteArrayList<>();

    static {
        checkHooks();
    }

    public static void checkHooks() {
        ecoCollectionsAvailable = Bukkit.getPluginManager().isPluginEnabled("EcoCollections");
        superiorSkyblockAvailable = Bukkit.getPluginManager().isPluginEnabled("SuperiorSkyblock2");
    }

    public static void registerProvider(CollectionProvider provider) {
        if (provider != null) {
            CUSTOM_PROVIDERS.add(provider);
        }
    }

    /**
     * Records harvested / collected resource progress towards player collections.
     */
    public static void recordCollection(Player player, Minion minion, Material material, int amount) {
        if (player == null || minion == null || material == null || amount <= 0) return;

        // 1. EcoCollections integration (reflection-safe)
        if (ecoCollectionsAvailable) {
            try {
                Class<?> apiClass = Class.forName("com.willfp.ecocollections.api.EcoCollectionsAPI");
                Method addProgress = apiClass.getMethod("addProgress", Player.class, Material.class, int.class);
                addProgress.invoke(null, player, material, amount);
            } catch (Throwable ignored) {}
        }

        // 2. SuperiorSkyblock2 Collections integration (reflection-safe)
        if (superiorSkyblockAvailable) {
            try {
                Class<?> ssClass = Class.forName("com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI");
                Method getPlayerMethod = ssClass.getMethod("getPlayer", Player.class);
                Object ssPlayer = getPlayerMethod.invoke(null, player);
                if (ssPlayer != null) {
                    Method getIslandMethod = ssPlayer.getClass().getMethod("getIsland");
                    Object island = getIslandMethod.invoke(ssPlayer);
                    if (island != null) {
                        Method handleBlock = island.getClass().getMethod("handleBlockBreak", Material.class, int.class);
                        handleBlock.invoke(island, material, amount);
                    }
                }
            } catch (Throwable ignored) {}
        }

        // 3. Custom Registered Third-Party Providers
        for (CollectionProvider provider : CUSTOM_PROVIDERS) {
            try {
                provider.onCollect(player, minion, material, amount);
            } catch (Throwable t) {
                Bukkit.getLogger().warning("[MinionX] Error in custom CollectionProvider: " + t.getMessage());
            }
        }
    }
}
