package com.skyblock.minions.command;

import com.skyblock.minions.gui.OperatorGUI;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Handles /minionsx and /minionx to open the operator interface.
 */
public class OperatorCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can open the MinionX operator interface.");
            return true;
        }

        if (!player.hasPermission("skyblockminions.admin")) {
            player.sendMessage("§cYou do not have permission to access the MinionX Operator Panel.");
            return true;
        }

        OperatorGUI.openMain(player);
        return true;
    }
}
