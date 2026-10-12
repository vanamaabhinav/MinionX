package com.minionx.core;

import com.minionx.MinionX;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Handles execution of configurable console commands (e.g. Skill EXP, Collections, Economy)
 * when players collect resources from a minion.
 */
public class CollectionCommandHandler {

    public static void executeCollectionCommands(Player player, Minion minion, Material itemMaterial, int amount) {
        if (player == null || minion == null || amount <= 0) return;

        FileConfiguration config = MinionX.getInstance().getConfig();
        if (!config.getBoolean("collection-commands.enabled", false)) {
            return;
        }

        // 1. Global commands
        List<String> globalCmds = config.getStringList("collection-commands.global");
        for (String cmd : globalCmds) {
            dispatchParsedCommand(cmd, player, minion, itemMaterial, amount);
        }

        // 2. Type-specific commands
        String typeKey = minion.getType().name().toLowerCase();
        List<String> typeCmds = config.getStringList("collection-commands.types." + typeKey);
        for (String cmd : typeCmds) {
            dispatchParsedCommand(cmd, player, minion, itemMaterial, amount);
        }
    }

    private static void dispatchParsedCommand(String rawCmd, Player player, Minion minion, Material mat, int amount) {
        if (rawCmd == null || rawCmd.isBlank()) return;

        String parsed = PlaceholderUtil.parse(rawCmd, player, minion, mat, amount);

        // Run synchronously on the main server thread
        if (Bukkit.isPrimaryThread()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
        } else {
            Bukkit.getScheduler().runTask(MinionX.getInstance(), () -> {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
            });
        }
    }
}
