package com.minionx.core;

import com.minionx.MinionX;
import com.minionx.quarantine.QuarantineManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * PlaceholderAPI expansion for MinionX.
 * Exposes player minion placement statistics, slot limits, and quarantine status.
 */
public class MinionXPlaceholderExpansion extends PlaceholderExpansion {

    private final MinionX plugin;

    public MinionXPlaceholderExpansion(MinionX plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "minionx";
    }

    @Override
    public @NotNull String getAuthor() {
        return "AbhinavVanama";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin != null ? plugin.getDescription().getVersion() : "1.0.3";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (params.equalsIgnoreCase("quarantine_status")) {
            return QuarantineManager.isQuarantined() ? "&cQUARANTINED" : "&aNORMAL";
        }
        if (params.equalsIgnoreCase("is_quarantined") || params.equalsIgnoreCase("quarantined")) {
            return String.valueOf(QuarantineManager.isQuarantined());
        }

        if (player == null) return null;

        PlayerLimitManager limitManager = plugin != null ? plugin.getPlayerLimitManager() : null;
        Player online = player.getPlayer();

        switch (params.toLowerCase()) {
            case "placed":
                return limitManager != null ? String.valueOf(limitManager.getPlacedCount(player.getUniqueId())) : "0";
            case "limit":
                if (limitManager == null) return "5";
                if (online != null) {
                    int lim = limitManager.getEffectiveLimit(online);
                    return lim < 0 ? "Unlimited" : String.valueOf(lim);
                } else {
                    int base = plugin != null ? plugin.getConfig().getInt("settings.max-minions-per-player", 5) : 5;
                    int bonus = limitManager.getBonusSlots(player.getUniqueId());
                    return String.valueOf(base + bonus);
                }
            case "available":
                if (limitManager == null) return "5";
                if (online != null) {
                    int avail = limitManager.getAvailableSlots(online);
                    return avail < 0 ? "Unlimited" : String.valueOf(avail);
                } else {
                    int base = plugin != null ? plugin.getConfig().getInt("settings.max-minions-per-player", 5) : 5;
                    int bonus = limitManager.getBonusSlots(player.getUniqueId());
                    int placed = limitManager.getPlacedCount(player.getUniqueId());
                    return String.valueOf(Math.max(0, (base + bonus) - placed));
                }
            case "bonus":
                return limitManager != null ? String.valueOf(limitManager.getBonusSlots(player.getUniqueId())) : "0";
            default:
                return null;
        }
    }
}
