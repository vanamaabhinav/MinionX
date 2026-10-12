package com.minionx.hook;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;

/**
 * Universal custom item bridge resolving items from ItemsAdder, MMOItems, and EcoItems.
 */
public class CustomItemHook {

    private static boolean itemsAdderAvailable = false;
    private static boolean mmoItemsAvailable = false;
    private static boolean ecoItemsAvailable = false;

    static {
        checkHooks();
    }

    public static void checkHooks() {
        itemsAdderAvailable = Bukkit.getPluginManager().isPluginEnabled("ItemsAdder");
        mmoItemsAvailable = Bukkit.getPluginManager().isPluginEnabled("MMOItems");
        ecoItemsAvailable = Bukkit.getPluginManager().isPluginEnabled("EcoItems");
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

        // 1. ItemsAdder
        if (raw.toLowerCase().startsWith("itemsadder:")) {
            String id = raw.substring("itemsadder:".length());
            if (itemsAdderAvailable) {
                try {
                    Class<?> csClass = Class.forName("dev.lone.itemsadder.api.CustomStack");
                    Method getInstance = csClass.getMethod("getInstance", String.class);
                    Object customStack = getInstance.invoke(null, id);
                    if (customStack != null) {
                        Method getItemStack = customStack.getClass().getMethod("getItemStack");
                        return (ItemStack) getItemStack.invoke(customStack);
                    }
                } catch (Throwable ignored) {}
            }
        }

        // 2. MMOItems
        if (raw.toLowerCase().startsWith("mmoitems:")) {
            String[] parts = raw.split(":", 3);
            if (parts.length == 3 && mmoItemsAvailable) {
                String type = parts[1];
                String id = parts[2];
                try {
                    Class<?> mmoClass = Class.forName("net.Indyuce.mmoitems.MMOItems");
                    Object pluginObj = mmoClass.getField("plugin").get(null);
                    Class<?> typeClass = Class.forName("net.Indyuce.mmoitems.api.Type");
                    Method getType = typeClass.getMethod("get", String.class);
                    Object typeObj = getType.invoke(null, type);
                    if (typeObj != null) {
                        Method getItem = pluginObj.getClass().getMethod("getItem", typeClass, String.class);
                        return (ItemStack) getItem.invoke(pluginObj, typeObj, id);
                    }
                } catch (Throwable ignored) {}
            }
        }

        // 3. EcoItems
        if (raw.toLowerCase().startsWith("ecoitems:")) {
            String id = raw.substring("ecoitems:".length());
            if (ecoItemsAvailable) {
                try {
                    Class<?> ecoClass = Class.forName("com.willfp.ecoitems.items.EcoItems");
                    Method getByID = ecoClass.getMethod("getByID", String.class);
                    Object customItem = getByID.invoke(null, id);
                    if (customItem != null) {
                        Method getItem = customItem.getClass().getMethod("getItem");
                        return (ItemStack) getItem.invoke(customItem);
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
}
