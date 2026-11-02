package com.minionx.hook;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;

/**
 * Universal custom item bridge resolving items from ItemsAdder, MMOItems, and EcoItems
 * with cached reflection handles for high-frequency pricing, recipes, and drop resolution.
 */
public class CustomItemHook {

    private static boolean itemsAdderAvailable = false;
    private static Method iaGetInstance = null;
    private static Method iaGetItemStack = null;
    private static Method iaByItemStack = null;
    private static Method iaGetNamespacedId = null;

    private static boolean mmoItemsAvailable = false;
    private static Object mmoItemsPluginInstance = null;
    private static Method mmoGetType = null;
    private static Method mmoGetItem = null;
    private static Method mmoGetTypeFromStack = null;
    private static Method mmoGetIdFromStack = null;

    private static boolean ecoItemsAvailable = false;
    private static Method ecoGetById = null;
    private static Method ecoGetItem = null;
    private static Method ecoGetByItemStack = null;
    private static Method ecoGetId = null;

    static {
        checkHooks();
    }

    public static synchronized void checkHooks() {
        initItemsAdder();
        initMMOItems();
        initEcoItems();
    }

    private static void initItemsAdder() {
        itemsAdderAvailable = false;
        iaGetInstance = null;
        iaGetItemStack = null;
        iaByItemStack = null;
        iaGetNamespacedId = null;

        if (Bukkit.getServer() == null || Bukkit.getPluginManager() == null) return;
        if (!Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) return;

        try {
            Class<?> csClass = Class.forName("dev.lone.itemsadder.api.CustomStack");
            iaGetInstance = csClass.getMethod("getInstance", String.class);
            iaGetItemStack = csClass.getMethod("getItemStack");
            iaByItemStack = csClass.getMethod("byItemStack", ItemStack.class);
            iaGetNamespacedId = csClass.getMethod("getNamespacedID");
            itemsAdderAvailable = true;
        } catch (Throwable ignored) {}
    }

    private static void initMMOItems() {
        mmoItemsAvailable = false;
        mmoItemsPluginInstance = null;
        mmoGetType = null;
        mmoGetItem = null;
        mmoGetTypeFromStack = null;
        mmoGetIdFromStack = null;

        if (Bukkit.getServer() == null || Bukkit.getPluginManager() == null) return;
        if (!Bukkit.getPluginManager().isPluginEnabled("MMOItems")) return;

        try {
            Class<?> mmoClass = Class.forName("net.Indyuce.mmoitems.MMOItems");
            mmoItemsPluginInstance = mmoClass.getField("plugin").get(null);
            Class<?> typeClass = Class.forName("net.Indyuce.mmoitems.api.Type");
            mmoGetType = typeClass.getMethod("get", String.class);
            mmoGetItem = mmoItemsPluginInstance.getClass().getMethod("getItem", typeClass, String.class);

            try {
                mmoGetTypeFromStack = mmoClass.getMethod("getType", ItemStack.class);
                mmoGetIdFromStack = mmoClass.getMethod("getID", ItemStack.class);
            } catch (NoSuchMethodException ignored) {}

            mmoItemsAvailable = true;
        } catch (Throwable ignored) {}
    }

    private static void initEcoItems() {
        ecoItemsAvailable = false;
        ecoGetById = null;
        ecoGetItem = null;
        ecoGetByItemStack = null;
        ecoGetId = null;

        if (Bukkit.getServer() == null || Bukkit.getPluginManager() == null) return;
        if (!Bukkit.getPluginManager().isPluginEnabled("EcoItems")) return;

        try {
            Class<?> ecoClass = Class.forName("com.willfp.ecoitems.items.EcoItems");
            ecoGetById = ecoClass.getMethod("getByID", String.class);
            Class<?> itemClass = Class.forName("com.willfp.ecoitems.items.EcoItem");
            ecoGetItem = itemClass.getMethod("getItem");

            try {
                ecoGetByItemStack = ecoClass.getMethod("getByID", ItemStack.class);
            } catch (NoSuchMethodException e) {
                try {
                    ecoGetByItemStack = ecoClass.getMethod("getCustomItem", ItemStack.class);
                } catch (NoSuchMethodException ignored) {}
            }

            try {
                ecoGetId = itemClass.getMethod("getId");
            } catch (NoSuchMethodException e) {
                try {
                    ecoGetId = itemClass.getMethod("getID");
                } catch (NoSuchMethodException ignored) {}
            }

            ecoItemsAvailable = true;
        } catch (Throwable ignored) {}
    }

    /**
     * Resolves a custom item string into a Bukkit ItemStack.
     * Formats:
     * - itemsadder:namespace:id  or  itemsadder:id
     * - mmoitems:TYPE:ID
     * - ecoitems:id
     * - VANILLA_MATERIAL
     */
    public static ItemStack getItem(String identifier) {
        if (identifier == null || identifier.trim().isEmpty()) return null;
        String raw = identifier.trim();

        // 1. ItemsAdder (cached reflection)
        if (raw.toLowerCase().startsWith("itemsadder:")) {
            String id = raw.substring("itemsadder:".length());
            if (itemsAdderAvailable && iaGetInstance != null && iaGetItemStack != null) {
                try {
                    Object customStack = iaGetInstance.invoke(null, id);
                    if (customStack != null) {
                        return (ItemStack) iaGetItemStack.invoke(customStack);
                    }
                } catch (Throwable ignored) {}
            }
        }

        // 2. MMOItems (cached reflection)
        if (raw.toLowerCase().startsWith("mmoitems:")) {
            String[] parts = raw.split(":", 3);
            if (parts.length == 3 && mmoItemsAvailable && mmoItemsPluginInstance != null && mmoGetType != null && mmoGetItem != null) {
                String type = parts[1];
                String id = parts[2];
                try {
                    Object typeObj = mmoGetType.invoke(null, type);
                    if (typeObj != null) {
                        return (ItemStack) mmoGetItem.invoke(mmoItemsPluginInstance, typeObj, id);
                    }
                } catch (Throwable ignored) {}
            }
        }

        // 3. EcoItems (cached reflection)
        if (raw.toLowerCase().startsWith("ecoitems:")) {
            String id = raw.substring("ecoitems:".length());
            if (ecoItemsAvailable && ecoGetById != null && ecoGetItem != null) {
                try {
                    Object customItem = ecoGetById.invoke(null, id);
                    if (customItem != null) {
                        return (ItemStack) ecoGetItem.invoke(customItem);
                    }
                } catch (Throwable ignored) {}
            }
        }

        // 4. Vanilla Material fallback
        Material mat = Material.matchMaterial(raw.toUpperCase());
        if (mat != null) {
            return new ItemStack(mat);
        }

        return null;
    }

    /**
     * Checks if the given ItemStack is a custom item from ItemsAdder, MMOItems, or EcoItems.
     */
    public static boolean isCustomItem(ItemStack item) {
        return identifyCustomItem(item) != null;
    }

    /**
     * Identifies a custom item and returns its namespaced identifier string
     * (e.g. "itemsadder:custom_ore", "mmoitems:SWORD:RUBY_SWORD", "ecoitems:blaze_core"),
     * or null if the item is vanilla.
     */
    public static String identifyCustomItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return null;

        // 1. ItemsAdder
        if (itemsAdderAvailable && iaByItemStack != null && iaGetNamespacedId != null) {
            try {
                Object customStack = iaByItemStack.invoke(null, item);
                if (customStack != null) {
                    Object id = iaGetNamespacedId.invoke(customStack);
                    if (id != null) {
                        return "itemsadder:" + id;
                    }
                }
            } catch (Throwable ignored) {}
        }

        // 2. MMOItems
        if (mmoItemsAvailable && mmoGetTypeFromStack != null && mmoGetIdFromStack != null) {
            try {
                Object typeObj = mmoGetTypeFromStack.invoke(null, item);
                Object idObj = mmoGetIdFromStack.invoke(null, item);
                if (typeObj != null && idObj != null) {
                    String typeStr = typeObj.toString();
                    String idStr = idObj.toString();
                    if (!typeStr.isEmpty() && !idStr.isEmpty()) {
                        return "mmoitems:" + typeStr + ":" + idStr;
                    }
                }
            } catch (Throwable ignored) {}
        }

        // 3. EcoItems
        if (ecoItemsAvailable && ecoGetByItemStack != null) {
            try {
                Object customItem = ecoGetByItemStack.invoke(null, item);
                if (customItem != null) {
                    if (ecoGetId != null) {
                        Object id = ecoGetId.invoke(customItem);
                        if (id != null) return "ecoitems:" + id;
                    }
                    return "ecoitems:" + customItem.toString();
                }
            } catch (Throwable ignored) {}
        }

        return null;
    }

    public static boolean isItemsAdderAvailable() {
        return itemsAdderAvailable;
    }

    public static boolean isMmoItemsAvailable() {
        return mmoItemsAvailable;
    }

    public static boolean isEcoItemsAvailable() {
        return ecoItemsAvailable;
    }
}
