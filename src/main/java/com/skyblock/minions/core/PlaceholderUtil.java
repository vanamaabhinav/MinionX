package com.skyblock.minions.core;

import com.skyblock.minions.SkyBlockMinions;
import com.skyblock.minions.api.MinionTier;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility for parsing placeholders and evaluating mathematical expressions in minion commands.
 * Supports player placement limits, available slots, and minion statistics.
 */
public class PlaceholderUtil {

    private static final Pattern MATH_PATTERN = Pattern.compile("\\{([^}]+)\\}");

    public static String parse(String input, Player player, Minion minion, Material itemMaterial, int amount) {
        if (input == null) return "";

        String result = input;

        // Player placeholders
        if (player != null) {
            result = result.replace("%player%", player.getName())
                           .replace("%player_uuid%", player.getUniqueId().toString());

            PlayerLimitManager limitManager = SkyBlockMinions.getInstance() != null
                    ? SkyBlockMinions.getInstance().getPlayerLimitManager()
                    : null;

            if (limitManager != null) {
                int limit = limitManager.getEffectiveLimit(player);
                int placed = limitManager.getPlacedCount(player.getUniqueId());
                int available = limitManager.getAvailableSlots(player);
                int bonus = limitManager.getBonusSlots(player.getUniqueId());

                String limitStr = limit < 0 ? "Unlimited" : String.valueOf(limit);
                String availStr = available < 0 ? "Unlimited" : String.valueOf(available);

                result = result.replace("%minions_placed%", String.valueOf(placed))
                               .replace("%minionx_placed%", String.valueOf(placed))
                               .replace("%minions_limit%", limitStr)
                               .replace("%minionx_limit%", limitStr)
                               .replace("%minions_available%", availStr)
                               .replace("%minionx_available%", availStr)
                               .replace("%minions_bonus%", String.valueOf(bonus))
                               .replace("%minionx_bonus%", String.valueOf(bonus));
            }
        }

        // Minion placeholders
        if (minion != null) {
            result = result.replace("%minion_type%", minion.getDisplayName())
                           .replace("%minion_type_id%", minion.getCustomDefinitionId() != null ? minion.getCustomDefinitionId() : (minion.getType() != null ? minion.getType().name().toLowerCase() : "minion"))
                           .replace("%minion_tier%", String.valueOf(minion.getTier()))
                           .replace("%minion_tier_roman%", MinionTier.toRoman(minion.getTier()))
                           .replace("%total_harvested%", String.valueOf(minion.getTotalHarvested()))
                           .replace("%stored%", String.valueOf(minion.getTotalStoredCount()))
                           .replace("%max_capacity%", String.valueOf(MinionTier.getMaxCapacity(minion.getTier())));

            if (minion.getLocation() != null) {
                result = result.replace("%x%", String.valueOf(minion.getLocation().getBlockX()))
                               .replace("%y%", String.valueOf(minion.getLocation().getBlockY()))
                               .replace("%z%", String.valueOf(minion.getLocation().getBlockZ()))
                               .replace("%world%", minion.getLocation().getWorld() != null ? minion.getLocation().getWorld().getName() : "world");
            }
        }

        // Collection specific placeholders
        result = result.replace("%amount%", String.valueOf(amount));
        if (itemMaterial != null) {
            result = result.replace("%item%", itemMaterial.name())
                           .replace("%item_material%", itemMaterial.name());
        }

        // Evaluate inline math expressions inside {expression}, e.g. {amount * 2.5}
        result = evaluateMathBlocks(result, amount);

        // Optional PlaceholderAPI hook via reflection (soft-depend)
        if (player != null && Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                Class<?> papiClass = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
                java.lang.reflect.Method method = papiClass.getMethod("setPlaceholders", Player.class, String.class);
                result = (String) method.invoke(null, player, result);
            } catch (Throwable ignored) {}
        }

        return result;
    }

    private static String evaluateMathBlocks(String text, int amount) {
        Matcher matcher = MATH_PATTERN.matcher(text);
        StringBuilder sb = new StringBuilder();

        while (matcher.find()) {
            String expr = matcher.group(1).replace("amount", String.valueOf(amount)).trim();
            double val = simpleEval(expr);
            // If whole number, format as integer
            if (val == (long) val) {
                matcher.appendReplacement(sb, String.valueOf((long) val));
            } else {
                matcher.appendReplacement(sb, String.format(java.util.Locale.US, "%.2f", val));
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    public static double simpleEval(String expr) {
        try {
            expr = expr.replaceAll("\\s+", "");
            if (expr.contains("*")) {
                String[] parts = expr.split("\\*");
                return Double.parseDouble(parts[0]) * Double.parseDouble(parts[1]);
            } else if (expr.contains("/")) {
                String[] parts = expr.split("/");
                return Double.parseDouble(parts[0]) / Double.parseDouble(parts[1]);
            } else if (expr.contains("+")) {
                String[] parts = expr.split("\\+");
                return Double.parseDouble(parts[0]) + Double.parseDouble(parts[1]);
            } else if (expr.contains("-")) {
                String[] parts = expr.split("-");
                return Double.parseDouble(parts[0]) - Double.parseDouble(parts[1]);
            }
            return Double.parseDouble(expr);
        } catch (Exception e) {
            return 0.0;
        }
    }
}
