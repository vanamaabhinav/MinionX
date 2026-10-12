package com.minionx.util;

import com.minionx.MinionX;
import org.bukkit.Bukkit;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;

/**
 * Lightweight, non-blocking bStats Metrics reporter for MinionX.
 * Sends anonymous statistics to bStats.org if enabled.
 */
public class MinionXMetrics {

    public static final int BSTATS_PLUGIN_ID = 21850;
    private static final String REPORT_URL = "https://bstats.org/api/v1/data/bukkit";

    public static void init(MinionX plugin) {
        if (!plugin.getConfig().getBoolean("metrics.enabled", true)) {
            return;
        }

        // Schedule periodic anonymous metrics push every 30 minutes asynchronously
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            try {
                sendMetrics(plugin);
            } catch (Throwable ignored) {
                // Non-blocking fail-safe
            }
        }, 1200L, 36000L); // 1 minute initial delay, then every 30 minutes
    }

    private static void sendMetrics(MinionX plugin) {
        try {
            int activeMinions = plugin.getMinionManager() != null ? plugin.getMinionManager().getAllMinions().size() : 0;
            String serverVersion = Bukkit.getVersion();
            String pluginVersion = plugin.getDescription().getVersion();
            int onlinePlayers = Bukkit.getOnlinePlayers().size();
            String serverUUID = UUID.nameUUIDFromBytes(Bukkit.getServer().getIp().getBytes(StandardCharsets.UTF_8)).toString();

            String json = String.format(
                    "{\"serverUUID\":\"%s\",\"service\":{\"id\":%d,\"customCharts\":[],\"activeMinions\":%d,\"onlinePlayers\":%d,\"version\":\"%s\",\"serverVersion\":\"%s\"}}",
                    serverUUID, BSTATS_PLUGIN_ID, activeMinions, onlinePlayers, pluginVersion, serverVersion.replace("\"", "")
            );

            byte[] compressed = compress(json);
            URL url = URI.create(REPORT_URL).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.addRequestProperty("Accept", "application/json");
            conn.addRequestProperty("Connection", "close");
            conn.addRequestProperty("Content-Encoding", "gzip");
            conn.addRequestProperty("Content-Length", String.valueOf(compressed.length));
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", "MinionX-Metrics/" + pluginVersion);
            conn.setDoOutput(true);
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(6000);

            try (DataOutputStream out = new DataOutputStream(conn.getOutputStream())) {
                out.write(compressed);
            }

            conn.getResponseCode(); // Trigger request
            conn.disconnect();
        } catch (Throwable ignored) {
            // Silently ignore network failures in offline / sandbox environments
        }
    }

    private static byte[] compress(String str) throws Exception {
        ByteArrayOutputStream obj = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(obj)) {
            gzip.write(str.getBytes(StandardCharsets.UTF_8));
        }
        return obj.toByteArray();
    }
}
