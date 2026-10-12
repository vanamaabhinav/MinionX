package com.minionx.quarantine;

import com.minionx.MinionX;
import com.minionx.util.ColorUtil;
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
        init(MinionX.getInstance());
    }

    public static void init(MinionX plugin) {
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
        if (MinionX.getInstance() != null) {
            MinionX.getInstance().getConfig().set("settings.quarantine-mode", enabled);
            MinionX.getInstance().saveConfig();
        }

        if (Bukkit.getServer() != null) {
            try {
                com.minionx.api.event.MinionQuarantineEvent qEvent = new com.minionx.api.event.MinionQuarantineEvent(null, enabled, "Administrative Quarantine Mode Toggle");
                Bukkit.getPluginManager().callEvent(qEvent);
            } catch (Exception ignored) {}
            try {
                if (enabled) {
                    Bukkit.broadcast(ColorUtil.component("&4&l[MinionX Emergency] &cServer Minion Quarantine Mode is now &lACTIVE&c! Minion item collection, placement, and pickup are temporarily frozen."));
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 0.5f);
                    }
                } else {
                    Bukkit.broadcast(ColorUtil.component("&2&l[MinionX] &aServer Minion Quarantine Mode has been &lDISABLED&a. Standard minion operations have resumed."));
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.2f);
                    }
                }
            } catch (Throwable ignored) {}
        }
    }

    public static boolean canBypass(Player player) {
        if (player == null) return false;
        return player.hasPermission("minionx.admin") || player.hasPermission("minionx.admin.bypass");
    }
}
