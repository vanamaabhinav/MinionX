package com.minionx.hook;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;

/**
 * Universal custom item bridge resolving items from ItemsAdder, MMOItems, and
 * EcoItems
 * with cached reflection handles for high-frequency pricing, recipes, and drop
 * resolution.
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

    private static boolean oraxenAvailable = false;
    private static Method oraxenGetItemById = null;
    private static Method oraxenGetIdByItem = null;

    private static boolean nexoAvailable = false;
    private static Method nexoItemFromId = null;
    private static Method nexoIdFromItem = null;

    static {
        checkHooks();
    }

    public static synchronized void checkHooks() {
        initItemsAdder();
        initMMOItems();
        initEcoItems();
        initOraxen();
        initNexo();
    }

    private static void initItemsAdder() {
        itemsAdderAvailable = false;
        iaGetInstance = null;
        iaGetItemStack = null;
        iaByItemStack = null;
        iaGetNamespacedId = null;

        if (Bukkit.getServer() == null || Bukkit.getPluginManager() == null)
            return;
        if (!Bukkit.getPluginManager().isPluginEnabled("ItemsAdder"))
            return;

        try {
            Class<?> csClass = Class.forName("dev.lone.itemsadder.api.CustomStack");
            iaGetInstance = csClass.getMethod("getInstance", String.class);
            iaGetItemStack = csClass.getMethod("getItemStack");
            iaByItemStack = csClass.getMethod("byItemStack", ItemStack.class);
            iaGetNamespacedId = csClass.getMethod("getNamespacedID");
            itemsAdderAvailable = true;
        } catch (Throwable ignored) {
        }
    }

    private static void initMMOItems() {
        mmoItemsAvailable = false;
        mmoItemsPluginInstance = null;
        mmoGetType = null;
        mmoGetItem = null;
        mmoGetTypeFromStack = null;
        mmoGetIdFromStack = null;

        if (Bukkit.getServer() == null || Bukkit.getPluginManager() == null)
            return;
        if (!Bukkit.getPluginManager().isPluginEnabled("MMOItems"))
            return;

        try {
            Class<?> mmoClass = Class.forName("net.Indyuce.mmoitems.MMOItems");
            mmoItemsPluginInstance = mmoClass.getField("plugin").get(null);
            Class<?> typeClass = Class.forName("net.Indyuce.mmoitems.api.Type");
            mmoGetType = typeClass.getMethod("get", String.class);
            mmoGetItem = mmoItemsPluginInstance.getClass().getMethod("getItem", typeClass, String.class);

            try {
                mmoGetTypeFromStack = mmoClass.getMethod("getType", ItemStack.class);
                mmoGetIdFromStack = mmoClass.getMethod("getID", ItemStack.class);
            } catch (NoSuchMethodException ignored) {
            }

            mmoItemsAvailable = true;
        } catch (Throwable ignored) {
        }
    }

    private static void initEcoItems() {
        ecoItemsAvailable = false;
        ecoGetById = null;
        ecoGetItem = null;
        ecoGetByItemStack = null;
        ecoGetId = null;

        if (Bukkit.getServer() == null || Bukkit.getPluginManager() == null)
            return;
        if (!Bukkit.getPluginManager().isPluginEnabled("EcoItems"))
            return;

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
                } catch (NoSuchMethodException ignored) {
                }
            }

            try {
                ecoGetId = itemClass.getMethod("getId");
            } catch (NoSuchMethodException e) {
                try {
                    ecoGetId = itemClass.getMethod("getID");
                } catch (NoSuchMethodException ignored) {
                }
            }

            ecoItemsAvailable = true;
        } catch (Throwable ignored) {
        }
    }

    private static void initOraxen() {
        oraxenAvailable = false;
        oraxenGetItemById = null;
        oraxenGetIdByItem = null;

        if (Bukkit.getServer() == null || Bukkit.getPluginManager() == null)
            return;
        if (!Bukkit.getPluginManager().isPluginEnabled("Oraxen"))
            return;

        try {
            Class<?> oiClass = null;
            try {
                oiClass = Class.forName("io.thito.oraxen.api.OraxenItems");
            } catch (ClassNotFoundException e) {
                oiClass = Class.forName("io.thismike.oraxen.api.OraxenItems");
            }
            oraxenGetItemById = oiClass.getMethod("getItemById", String.class);
            try {
                oraxenGetIdByItem = oiClass.getMethod("getIdByItem", ItemStack.class);
            } catch (Throwable ignored) {
            }
            oraxenAvailable = true;
        } catch (Throwable ignored) {
        }
    }

    private static void initNexo() {
        nexoAvailable = false;
        nexoItemFromId = null;
        nexoIdFromItem = null;

        if (Bukkit.getServer() == null || Bukkit.getPluginManager() == null)
            return;
        if (!Bukkit.getPluginManager().isPluginEnabled("Nexo"))
            return;

        try {
            Class<?> nexoClass = Class.forName("com.nexomc.nexo.api.NexoItems");
            nexoItemFromId = nexoClass.getMethod("itemFromId", String.class);
            try {
                nexoIdFromItem = nexoClass.getMethod("idFromItem", ItemStack.class);
            } catch (Throwable ignored) {
            }
            nexoAvailable = true;
        } catch (Throwable ignored) {
        }
    }

    /**
     * Resolves a custom item string into a Bukkit ItemStack.
     * Formats:
     * - itemsadder:namespace:id or itemsadder:id
     * - mmoitems:TYPE:ID
     * - ecoitems:id
     * - VANILLA_MATERIAL
     */
    public static ItemStack getItem(String identifier) {
        if (identifier == null || identifier.trim().isEmpty())
            return null;
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
                } catch (Throwable ignored) {
                }
            }
        }

        // 2. MMOItems (cached reflection)
        if (raw.toLowerCase().startsWith("mmoitems:")) {
            String[] parts = raw.split(":", 3);
            if (parts.length == 3 && mmoItemsAvailable && mmoItemsPluginInstance != null && mmoGetType != null
                    && mmoGetItem != null) {
                String type = parts[1];
                String id = parts[2];
                try {
                    Object typeObj = mmoGetType.invoke(null, type);
                    if (typeObj != null) {
                        return (ItemStack) mmoGetItem.invoke(mmoItemsPluginInstance, typeObj, id);
                    }
                } catch (Throwable ignored) {
                }
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
                } catch (Throwable ignored) {
                }
            }
        }

        // 4. Oraxen (cached reflection)
        if (raw.toLowerCase().startsWith("oraxen:")) {
            String id = raw.substring("oraxen:".length());
            if (oraxenAvailable && oraxenGetItemById != null) {
                try {
                    Object builder = oraxenGetItemById.invoke(null, id);
                    if (builder != null) {
                        Method buildMethod = builder.getClass().getMethod("build");
                        return (ItemStack) buildMethod.invoke(builder);
                    }
                } catch (Throwable ignored) {
                }
            }
        }

        // 5. Nexo (cached reflection)
        if (raw.toLowerCase().startsWith("nexo:")) {
            String id = raw.substring("nexo:".length());
            if (nexoAvailable && nexoItemFromId != null) {
                try {
                    Object builder = nexoItemFromId.invoke(null, id);
                    if (builder != null) {
                        Method buildMethod = builder.getClass().getMethod("build");
                        return (ItemStack) buildMethod.invoke(builder);
                    }
                } catch (Throwable ignored) {
                }
            }
        }

        // 6. Vanilla Material fallback
        Material mat = Material.matchMaterial(raw.toUpperCase());
        if (mat != null) {
            return new ItemStack(mat);
        }

        return null;
    }

    /**
     * Checks if the given ItemStack is a custom item from ItemsAdder, MMOItems, or
     * EcoItems.
     */
    public static boolean isCustomItem(ItemStack item) {
        return identifyCustomItem(item) != null;
    }

    /**
     * Identifies a custom item and returns its namespaced identifier string
     * (e.g. "itemsadder:custom_ore", "mmoitems:SWORD:RUBY_SWORD",
     * "ecoitems:blaze_core"),
     * or null if the item is vanilla.
     */
    public static String identifyCustomItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR)
            return null;

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
            } catch (Throwable ignored) {
            }
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
            } catch (Throwable ignored) {
            }
        }

        // 3. EcoItems
        if (ecoItemsAvailable && ecoGetByItemStack != null) {
            try {
                Object customItem = ecoGetByItemStack.invoke(null, item);
                if (customItem != null) {
                    if (ecoGetId != null) {
                        Object id = ecoGetId.invoke(customItem);
                        if (id != null)
                            return "ecoitems:" + id;
                    }
                    return "ecoitems:" + customItem.toString();
                }
            } catch (Throwable ignored) {
            }
        }

        // 4. Oraxen
        if (oraxenAvailable && oraxenGetIdByItem != null) {
            try {
                Object id = oraxenGetIdByItem.invoke(null, item);
                if (id != null && !id.toString().isEmpty()) {
                    return "oraxen:" + id;
                }
            } catch (Throwable ignored) {
            }
        }

        // 5. Nexo
        if (nexoAvailable && nexoIdFromItem != null) {
            try {
                Object id = nexoIdFromItem.invoke(null, item);
                if (id != null && !id.toString().isEmpty()) {
                    return "nexo:" + id;
                }
            } catch (Throwable ignored) {
            }
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

    public static boolean isOraxenAvailable() {
        return oraxenAvailable;
    }

    public static boolean isNexoAvailable() {
        return nexoAvailable;
    }
}
