package com.minionx.command;

import com.minionx.gui.OperatorGUI;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

/**
 * Handles /minionx (and aliases /minionsx, /mxop) to open the operator control center interface.
 */
public class OperatorCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can open the MinionX operator interface.");
            return true;
        }

        if (!player.hasPermission("minionx.admin")) {
            player.sendMessage("§cYou do not have permission to access the MinionX Operator Panel.");
            return true;
        }

        OperatorGUI.openMain(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }
}
