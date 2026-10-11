package com.skyblock.minions.core;

import com.skyblock.minions.SkyBlockMinions;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages dynamic per-player minion placement limits.
 * Integrates permissions (minions.limit.<N>), LuckPerms meta (minion-limit),
 * bonus progression slots, and bypass checks.
 */
public class PlayerLimitManager {

    private final SkyBlockMinions plugin;
    private final Map<UUID, Integer> bonusSlots = new ConcurrentHashMap<>();
    private final File file;

    public PlayerLimitManager(SkyBlockMinions plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data/player_limits.yml");
        load();
    }

    /**
     * Resolves the maximum number of minions a player is allowed to place.
     * @return Positive limit, or -1 for unlimited.
     */
    public int getEffectiveLimit(Player player) {
        if (player == null) return 5;
        if (player.hasPermission("minions.bypass.limit")) return -1;

        int baseLimit = -1;

        // 1. Scan permission nodes: minions.limit.<N> (picks highest)
        int highestPermLimit = -1;
        for (PermissionAttachmentInfo pai : player.getEffectivePermissions()) {
            String perm = pai.getPermission().toLowerCase();
            if (perm.startsWith("minions.limit.") && pai.getValue()) {
                String sub = perm.substring("minions.limit.".length());
                try {
                    int val = Integer.parseInt(sub);
                    if (val > highestPermLimit) {
                        highestPermLimit = val;
                    }
                } catch (NumberFormatException ignored) {}
            }
        }

        if (highestPermLimit >= 0) {
            baseLimit = highestPermLimit;
        }

        // 2. LuckPerms Meta check (minion-limit) if available
        int luckPermsLimit = getLuckPermsMetaLimit(player);
        if (luckPermsLimit > baseLimit) {
            baseLimit = luckPermsLimit;
        }

        // 3. Fallback to default in config.yml
        if (baseLimit < 0) {
            baseLimit = plugin.getConfig().getInt("settings.max-minions-per-player", 5);
        }

        if (baseLimit < 0) return -1; // Configured unlimited

        // 4. Add persistent bonus progression slots
        int bonus = getBonusSlots(player.getUniqueId());
        return baseLimit + bonus;
    }

    /**
     * Count of currently placed minions owned by the player.
     */
    public int getPlacedCount(UUID playerUuid) {
        if (plugin.getMinionManager() == null) return 0;
        return plugin.getMinionManager().getMinionsByOwner(playerUuid).size();
    }

    /**
     * Remaining minion slots available for placement.
     * @return Number of slots remaining, or -1 if unlimited.
     */
    public int getAvailableSlots(Player player) {
        int limit = getEffectiveLimit(player);
        if (limit < 0) return -1;
        int placed = getPlacedCount(player.getUniqueId());
        return Math.max(0, limit - placed);
    }

    public boolean canPlaceMore(Player player) {
        int limit = getEffectiveLimit(player);
        if (limit < 0) return true;
        return getPlacedCount(player.getUniqueId()) < limit;
    }

    public int getBonusSlots(UUID uuid) {
        return bonusSlots.getOrDefault(uuid, 0);
    }

    public void setBonusSlots(UUID uuid, int bonus) {
        bonusSlots.put(uuid, Math.max(0, bonus));
        saveAsync();
    }

    public void addBonusSlots(UUID uuid, int delta) {
        int current = getBonusSlots(uuid);
        setBonusSlots(uuid, current + delta);
    }

    public void removeBonusSlots(UUID uuid, int delta) {
        int current = getBonusSlots(uuid);
        setBonusSlots(uuid, Math.max(0, current - delta));
    }

    public void load() {
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String key : yaml.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                bonusSlots.put(uuid, yaml.getInt(key, 0));
            } catch (IllegalArgumentException ignored) {}
        }
    }

    public void save() {
        try {
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            YamlConfiguration yaml = new YamlConfiguration();
            for (Map.Entry<UUID, Integer> entry : bonusSlots.entrySet()) {
                yaml.set(entry.getKey().toString(), entry.getValue());
            }
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save player limits: " + e.getMessage());
        }
    }

    public void saveAsync() {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, this::save);
    }

    private int getLuckPermsMetaLimit(Player player) {
        try {
            Class<?> lpProvider = Class.forName("net.luckperms.api.LuckPermsProvider");
            Object lp = lpProvider.getMethod("get").invoke(null);
            Object userManager = lp.getClass().getMethod("getUserManager").invoke(lp);
            Object user = userManager.getClass().getMethod("getUser", UUID.class).invoke(userManager, player.getUniqueId());
            if (user != null) {
                Object cachedData = user.getClass().getMethod("getCachedData").invoke(user);
                Object metaData = cachedData.getClass().getMethod("getMetaData").invoke(cachedData);
                String metaVal = (String) metaData.getClass().getMethod("getMetaValue", String.class).invoke(metaData, "minion-limit");
                if (metaVal != null) {
                    return Integer.parseInt(metaVal.trim());
                }
            }
        } catch (Throwable ignored) {}
        return -1;
    }
}
