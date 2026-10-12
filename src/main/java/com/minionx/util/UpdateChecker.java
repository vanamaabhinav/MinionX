package com.minionx.util;

import com.minionx.MinionX;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.function.Consumer;

/**
 * Asynchronous, non-blocking GitHub update checker for MinionX.
 */
@SuppressWarnings("deprecation")
public class UpdateChecker implements Listener {

    private static final String GITHUB_REPO = "vanamaabhinav/MinionX";
    private static String latestVersion = null;
    private static boolean updateAvailable = false;

    public static void init(MinionX plugin) {
        if (!plugin.getConfig().getBoolean("update-checker.enabled", true)) {
            return;
        }

        Bukkit.getPluginManager().registerEvents(new UpdateChecker(), plugin);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            checkForUpdates(plugin.getDescription().getVersion(), version -> {
                latestVersion = version;
                updateAvailable = isNewerVersion(plugin.getDescription().getVersion(), version);
                if (updateAvailable) {
                    plugin.getLogger().warning("=================================================");
                    plugin.getLogger().warning("A new update of MinionX is available: v" + version);
                    plugin.getLogger().warning("Current version: v" + plugin.getDescription().getVersion());
                    plugin.getLogger().warning("Download: https://github.com/" + GITHUB_REPO + "/releases/latest");
                    plugin.getLogger().warning("=================================================");
                }
            });
        });
    }

    public static void checkForUpdates(String currentVersion, Consumer<String> onResult) {
        try {
            URL url = URI.create("https://api.github.com/repos/" + GITHUB_REPO + "/releases/latest").toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "MinionX-UpdateChecker");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            if (conn.getResponseCode() == 200) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    String body = response.toString();
                    int tagIdx = body.indexOf("\"tag_name\":");
                    if (tagIdx != -1) {
                        int start = body.indexOf("\"", tagIdx + 11) + 1;
                        int end = body.indexOf("\"", start);
                        String tag = body.substring(start, end).replace("v", "").trim();
                        if (onResult != null) onResult.accept(tag);
                    }
                }
            }
        } catch (Throwable ignored) {
            // Graceful fallback when offline or in tests
        }
    }

    public static boolean isNewerVersion(String current, String latest) {
        if (latest == null || current == null) return false;
        String curClean = current.replace("v", "").replace("-SNAPSHOT", "").trim();
        String latClean = latest.replace("v", "").replace("-SNAPSHOT", "").trim();

        String[] curParts = curClean.split("\\.");
        String[] latParts = latClean.split("\\.");
        int len = Math.max(curParts.length, latParts.length);

        for (int i = 0; i < len; i++) {
            int c = i < curParts.length ? parseOrZero(curParts[i]) : 0;
            int l = i < latParts.length ? parseOrZero(latParts[i]) : 0;
            if (l > c) return true;
            if (l < c) return false;
        }
        return false;
    }

    private static int parseOrZero(String s) {
        try {
            return Integer.parseInt(s.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return 0;
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (updateAvailable && player.hasPermission("minionx.admin")) {
            Bukkit.getScheduler().runTaskLater(MinionX.getInstance(), () -> {
                if (player.isOnline()) {
                    player.sendMessage(ColorUtil.getPrefix() + ColorUtil.color("&eA new update of &bMinionX &eis available: &av" + latestVersion + " &e(Current: &7v" + MinionX.getInstance().getDescription().getVersion() + "&e)"));
                    player.sendMessage(ColorUtil.getPrefix() + ColorUtil.color("&7Download: &fhttps://github.com/" + GITHUB_REPO + "/releases/latest"));
                }
            }, 40L);
        }
    }
}
