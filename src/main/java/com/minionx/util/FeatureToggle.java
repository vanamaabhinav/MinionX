package com.minionx.util;

import com.minionx.MinionX;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * High-performance, centralized toggle manager for all modular MinionX features.
 * Caches configuration flags for hot tick-loop paths and reloads cleanly on /minion reload.
 */
public class FeatureToggle {

    private static boolean animationsEnabled = true;
    private static boolean soundsEnabled = true;
    private static boolean particlesEnabled = true;
    private static boolean geyserTouchFriendly = true;
    private static boolean dialoguesEnabled = true;
    private static boolean offlineCatchupEnabled = true;
    private static boolean fuelsEnabled = true;
    private static boolean upgradesEnabled = true;
    private static boolean shippingEnabled = true;
    private static boolean skinsEnabled = true;
    private static boolean storageChestsEnabled = true;
    private static boolean customStorageLayoutsEnabled = false;
    private static String nametagMode = "ALWAYS";

    static {
        reload();
    }

    public static void reload() {
        MinionX plugin = MinionX.getInstance();
        FileConfiguration config = plugin != null ? plugin.getConfig() : null;
        FileConfiguration msgConfig = plugin != null ? plugin.getMessagesConfig() : null;
        reload(config, msgConfig);
    }

    public static void reload(FileConfiguration config) {
        reload(config, null);
    }

    public static void reload(FileConfiguration config, FileConfiguration msgConfig) {
        // 1. Visual & Audio Toggles (supports new 'features.*' and legacy 'settings.*' paths)
        animationsEnabled = getBooleanWithFallback(config, "features.enable-animations", "settings.enable-animations", true);
        soundsEnabled = getBooleanWithFallback(config, "features.enable-sounds", "settings.enable-sounds", true);
        particlesEnabled = getBooleanWithFallback(config, "features.enable-particles", "settings.enable-particles", true);
        geyserTouchFriendly = getBooleanWithFallback(config, "features.geyser-touch-friendly", "settings.geyser-touch-friendly", true);

        // 2. Dialogues (checked in messages.yml first, then config.yml)
        if (msgConfig != null && msgConfig.contains("dialogues.enabled")) {
            dialoguesEnabled = msgConfig.getBoolean("dialogues.enabled", true);
        } else if (config != null && config.contains("features.dialogues")) {
            dialoguesEnabled = config.getBoolean("features.dialogues", true);
        } else if (config != null && config.contains("dialogues.enabled")) {
            dialoguesEnabled = config.getBoolean("dialogues.enabled", true);
        } else {
            dialoguesEnabled = true;
        }

        // 3. Offline Catch-Up
        boolean offlineFlag = getBooleanWithFallback(config, "features.offline-catchup", "settings.offline-catchup", true);
        int maxOfflineHours = config != null ? config.getInt("settings.max-offline-hours", 720) : 720;
        offlineCatchupEnabled = offlineFlag && (maxOfflineHours > 0);

        // 4. Subsystem Master Toggles
        fuelsEnabled = config == null || config.getBoolean("features.fuels", true);
        upgradesEnabled = config == null || config.getBoolean("features.upgrades", true);
        shippingEnabled = config == null || config.getBoolean("features.automated-shipping", true);
        skinsEnabled = config == null || config.getBoolean("features.skins", true);
        storageChestsEnabled = config == null || config.getBoolean("features.storage-chests", true);

        // 5. Storage Custom Layouts
        customStorageLayoutsEnabled = config != null && config.getBoolean("storage-layouts.use-custom-layouts", false);

        // 6. Nametags Mode (valid modes: ALWAYS, TARGETED, DISABLED)
        if (config != null && config.contains("nametags.mode")) {
            String modeStr = config.getString("nametags.mode", "ALWAYS").trim().toUpperCase();
            if ("TARGETED".equals(modeStr) || "DISABLED".equals(modeStr)) {
                nametagMode = modeStr;
            } else {
                nametagMode = "ALWAYS";
            }
        } else {
            nametagMode = "ALWAYS";
        }
    }

    private static boolean getBooleanWithFallback(FileConfiguration config, String primaryPath, String legacyPath, boolean def) {
        if (config == null) return def;
        if (config.contains(primaryPath)) {
            return config.getBoolean(primaryPath, def);
        }
        if (config.contains(legacyPath)) {
            return config.getBoolean(legacyPath, def);
        }
        return def;
    }

    public static boolean isAnimationsEnabled() {
        return animationsEnabled;
    }

    public static boolean isSoundsEnabled() {
        return soundsEnabled;
    }

    public static boolean isParticlesEnabled() {
        return particlesEnabled;
    }

    public static boolean isGeyserTouchFriendly() {
        return geyserTouchFriendly;
    }

    public static boolean isDialoguesEnabled() {
        return dialoguesEnabled;
    }

    public static boolean isOfflineCatchupEnabled() {
        return offlineCatchupEnabled;
    }

    public static boolean isFuelsEnabled() {
        return fuelsEnabled;
    }

    public static boolean isUpgradesEnabled() {
        return upgradesEnabled;
    }

    public static boolean isShippingEnabled() {
        return shippingEnabled;
    }

    public static boolean isSkinsEnabled() {
        return skinsEnabled;
    }

    public static boolean isStorageChestsEnabled() {
        return storageChestsEnabled;
    }

    public static boolean isCustomStorageLayoutsEnabled() {
        return customStorageLayoutsEnabled;
    }

    public static String getNametagMode() {
        return nametagMode;
    }
}
