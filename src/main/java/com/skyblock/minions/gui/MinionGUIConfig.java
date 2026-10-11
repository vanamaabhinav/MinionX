package com.skyblock.minions.gui;

import com.skyblock.minions.SkyBlockMinions;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

/**
 * Configuration holder for the 54-slot Minion chest menu.
 * Faithfully mirrors the authentic Hypixel SkyBlock layout by default,
 * while allowing server owners to customize every slot and item in config.yml.
 */
public class MinionGUIConfig {

    public static int getHeadSlot() {
        return getInt("gui.minion-menu.slots.head", 4);
    }

    public static int getFuelSlot() {
        return getInt("gui.minion-menu.slots.fuel", 19);
    }

    public static int getAutomatedShippingSlot() {
        return getInt("gui.minion-menu.slots.automated-shipping", 20);
    }

    public static int getUpgrade1Slot() {
        return getInt("gui.minion-menu.slots.upgrade-1", 28);
    }

    public static int getUpgrade2Slot() {
        return getInt("gui.minion-menu.slots.upgrade-2", 29);
    }

    public static int getSkinSlot() {
        return getInt("gui.minion-menu.slots.skin", 37);
    }

    public static int getUpgradeTierSlot() {
        return getInt("gui.minion-menu.slots.upgrade-tier", 48);
    }

    public static int getCollectAllSlot() {
        return getInt("gui.minion-menu.slots.collect-all", 49);
    }

    public static int getPickupSlot() {
        return getInt("gui.minion-menu.slots.pickup", 51);
    }

    public static int[] getStorageSlots() {
        if (SkyBlockMinions.getInstance() == null || SkyBlockMinions.getInstance().getConfig() == null) {
            return new int[]{
                    21, 22, 23, 24, 25,
                    30, 31, 32, 33, 34,
                    39, 40, 41, 42, 43
            };
        }
        List<Integer> list = SkyBlockMinions.getInstance().getConfig().getIntegerList("gui.minion-menu.slots.storage");
        if (list == null || list.isEmpty()) {
            return new int[]{
                    21, 22, 23, 24, 25,
                    30, 31, 32, 33, 34,
                    39, 40, 41, 42, 43
            };
        }
        int[] arr = new int[list.size()];
        for (int i = 0; i < list.size(); i++) {
            arr[i] = list.get(i);
        }
        return arr;
    }

    public static int getStorageIndex(int slot) {
        int[] storage = getStorageSlots();
        for (int i = 0; i < storage.length; i++) {
            if (storage[i] == slot) return i;
        }
        return -1;
    }

    private static int getInt(String path, int def) {
        if (SkyBlockMinions.getInstance() == null || SkyBlockMinions.getInstance().getConfig() == null) {
            return def;
        }
        return SkyBlockMinions.getInstance().getConfig().getInt(path, def);
    }
}
