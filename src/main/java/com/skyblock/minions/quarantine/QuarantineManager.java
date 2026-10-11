package com.skyblock.minions.quarantine;

import com.skyblock.minions.SkyBlockMinions;
import com.skyblock.minions.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

/**
 * Controls emergency Quarantine Mode across the server.
 * When enabled, freezes item collection, minion removal/pickup, placement, and storage access
 * as an emergency failsafe against serious dupe glitches and exploits.
 */
public class QuarantineManager {

    private static boolean quarantined = false;
    private static boolean freezeTicks = false;

    public static void init() {
        init(SkyBlockMinions.getInstance());
    }

    public static void init(SkyBlockMinions plugin) {
        if (plugin == null) return;
        FileConfiguration config = plugin.getConfig();
        quarantined = config.getBoolean("settings.quarantine-mode", false);
        freezeTicks = config.getBoolean("settings.quarantine-freeze-ticks", false);
    }

    public static boolean isQuarantined() {
        return quarantined;
    }

    public static boolean isFreezeTicks() {
        return freezeTicks;
    }

    public static void setQuarantined(boolean enabled) {
        quarantined = enabled;
        if (SkyBlockMinions.getInstance() != null) {
            SkyBlockMinions.getInstance().getConfig().set("settings.quarantine-mode", enabled);
            SkyBlockMinions.getInstance().saveConfig();
        }

        if (Bukkit.getServer() != null) {
            try {
                if (enabled) {
                    Bukkit.broadcastMessage(ColorUtil.color("&4&l[MinionX Emergency] &cServer Minion Quarantine Mode is now &lACTIVE&c! Minion item collection, placement, and pickup are temporarily frozen."));
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 0.5f);
                    }
                } else {
                    Bukkit.broadcastMessage(ColorUtil.color("&2&l[MinionX] &aServer Minion Quarantine Mode has been &lDISABLED&a. Standard minion operations have resumed."));
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.2f);
                    }
                }
            } catch (Throwable ignored) {}
        }
    }

    public static boolean canBypass(Player player) {
        if (player == null) return false;
        return player.hasPermission("skyblockminions.admin") || player.hasPermission("skyblockminions.admin.bypass");
    }
}
