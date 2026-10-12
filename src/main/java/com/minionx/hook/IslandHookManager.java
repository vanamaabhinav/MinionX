package com.minionx.hook;

import com.minionx.core.Minion;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Backwards compatibility delegate for ProtectionHookManager.
 */
public class IslandHookManager {

    public static void checkHooks() {
        ProtectionHookManager.checkHooks();
    }

    public static boolean canPlaceMinion(Player player, Location location) {
        return ProtectionHookManager.canPlaceMinion(player, location);
    }

    public static boolean canAccessMinion(Player player, Minion minion) {
        return ProtectionHookManager.canAccessMinion(player, minion);
    }
}
