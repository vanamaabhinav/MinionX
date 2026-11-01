package com.minionx.hook;

import com.minionx.core.Minion;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Universal Collections Integration Hook.
 * Supports EcoCollections, SuperiorSkyblock2 Collections, and custom collection providers
 * with cached reflection handles for zero-allocation performance on minion harvest cycles.
 */
public class CollectionsHook {

    private static boolean ecoCollectionsAvailable = false;
    private static boolean superiorSkyblockAvailable = false;

    // --- Cached Reflection Handles ---
    private static Method ecoAddProgress = null;

    private static Method ssGetPlayer = null;
    private static Method ssGetIsland = null;
    private static Method ssHandleBlockBreak = null;
    private static boolean ssGetPlayerTakesUuid = false;

    public interface CollectionProvider {
        void onCollect(Player player, Minion minion, Material material, int amount);
    }

    private static final List<CollectionProvider> CUSTOM_PROVIDERS = new CopyOnWriteArrayList<>();

    static {
        checkHooks();
    }

    public static void checkHooks() {
        initEcoCollections();
        initSuperiorSkyblock();
    }

    private static void initEcoCollections() {
        try {
            ecoCollectionsAvailable = Bukkit.getPluginManager() != null && Bukkit.getPluginManager().isPluginEnabled("EcoCollections");
        } catch (Throwable t) {
            ecoCollectionsAvailable = false;
        }
        if (!ecoCollectionsAvailable) {
            ecoAddProgress = null;
            return;
        }
        try {
            Class<?> apiClass = Class.forName("com.willfp.ecocollections.api.EcoCollectionsAPI");
            ecoAddProgress = apiClass.getMethod("addProgress", Player.class, Material.class, int.class);
        } catch (Throwable t) {
            ecoCollectionsAvailable = false;
            ecoAddProgress = null;
        }
    }

    private static void initSuperiorSkyblock() {
        try {
            superiorSkyblockAvailable = Bukkit.getPluginManager() != null && Bukkit.getPluginManager().isPluginEnabled("SuperiorSkyblock2");
        } catch (Throwable t) {
            superiorSkyblockAvailable = false;
        }
        if (!superiorSkyblockAvailable) {
            ssGetPlayer = null;
            ssGetIsland = null;
            ssHandleBlockBreak = null;
            ssGetPlayerTakesUuid = false;
            return;
        }
        try {
            Class<?> ssClass = Class.forName("com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI");
            try {
                ssGetPlayer = ssClass.getMethod("getPlayer", Player.class);
                ssGetPlayerTakesUuid = false;
            } catch (NoSuchMethodException e) {
                ssGetPlayer = ssClass.getMethod("getPlayer", UUID.class);
                ssGetPlayerTakesUuid = true;
            }

            Class<?> superiorPlayerClass = Class.forName("com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer");
            ssGetIsland = superiorPlayerClass.getMethod("getIsland");

            Class<?> islandClass = Class.forName("com.bgsoftware.superiorskyblock.api.island.Island");
            ssHandleBlockBreak = islandClass.getMethod("handleBlockBreak", Material.class, int.class);
        } catch (Throwable t) {
            superiorSkyblockAvailable = false;
            ssGetPlayer = null;
            ssGetIsland = null;
            ssHandleBlockBreak = null;
            ssGetPlayerTakesUuid = false;
        }
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

        // 1. EcoCollections integration (cached reflection)
        if (ecoCollectionsAvailable && ecoAddProgress != null) {
            try {
                ecoAddProgress.invoke(null, player, material, amount);
            } catch (Throwable ignored) {}
        }

        // 2. SuperiorSkyblock2 Collections integration (cached reflection)
        if (superiorSkyblockAvailable && ssGetPlayer != null && ssGetIsland != null && ssHandleBlockBreak != null) {
            try {
                Object ssPlayer = ssGetPlayerTakesUuid
                        ? ssGetPlayer.invoke(null, player.getUniqueId())
                        : ssGetPlayer.invoke(null, player);
                if (ssPlayer != null) {
                    Object island = ssGetIsland.invoke(ssPlayer);
                    if (island != null) {
                        ssHandleBlockBreak.invoke(island, material, amount);
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

    public static boolean isEcoCollectionsAvailable() {
        return ecoCollectionsAvailable;
    }

    public static boolean isSuperiorSkyblockAvailable() {
        return superiorSkyblockAvailable;
    }
}
