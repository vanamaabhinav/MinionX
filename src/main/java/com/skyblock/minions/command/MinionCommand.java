package com.skyblock.minions.command;

import com.skyblock.minions.SkyBlockMinions;
import com.skyblock.minions.api.*;
import com.skyblock.minions.core.Minion;
import com.skyblock.minions.gui.MinionCreatorGUI;
import com.skyblock.minions.item.MinionItemFactory;
import com.skyblock.minions.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Handles /minion subcommands and tab-completions.
 */
public class MinionCommand implements CommandExecutor, TabCompleter {

    private final SkyBlockMinions plugin;

    public MinionCommand(SkyBlockMinions plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "create" -> handleCreate(sender);
            case "give" -> handleGive(sender, args);
            case "list" -> handleList(sender);
            case "reload" -> handleReload(sender);
            default -> sendHelp(sender);
        }

        return true;
    }

    private void handleCreate(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtil.color("&cOnly players can open the Minion Creator Studio."));
            return;
        }

        if (!player.hasPermission("skyblockminions.admin")) {
            player.sendMessage(ColorUtil.color("&cYou do not have permission to use the minion creator."));
            return;
        }

        MinionCreatorGUI.open(player, null);
    }

    private void handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("skyblockminions.admin")) {
            sender.sendMessage(ColorUtil.color("&cYou do not have permission to use this command."));
            return;
        }

        if (args.length < 3) {
            sender.sendMessage(ColorUtil.color("&cUsage: /minion give <player> <type|custom_id|fuel|upgrade> [tier]"));
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(ColorUtil.color("&cPlayer '" + args[1] + "' not found."));
            return;
        }

        String itemArg = args[2].toLowerCase();

        // 1. Check custom minion definitions (from Minion Creator Studio or minions.yml)
        MinionDefinition customDef = MinionRegistry.get(itemArg);
        if (customDef != null) {
            int tier = 1;
            if (args.length >= 4) {
                try {
                    tier = Math.clamp(Integer.parseInt(args[3]), 1, MinionTier.getMaxTier());
                } catch (NumberFormatException ignored) {}
            }
            ItemStack customItem = MinionItemFactory.createCustomMinionItem(customDef, tier, 0);
            target.getInventory().addItem(customItem);
            sender.sendMessage(ColorUtil.color("&aGave " + customDef.getDisplayName() + " Minion " + MinionTier.toRoman(tier) + " to " + target.getName() + "."));
            target.sendMessage(ColorUtil.color("&6[Minions] &aYou received a " + customDef.getDisplayName() + " Minion " + MinionTier.toRoman(tier) + "!"));
            return;
        }

        // 2. Check dynamic custom fuels from config
        CustomFuel customFuel = FuelRegistry.get(itemArg);
        if (customFuel != null) {
            target.getInventory().addItem(MinionItemFactory.createFuelItem(customFuel));
            sender.sendMessage(ColorUtil.color("&aGave " + customFuel.getDisplayName() + " fuel to " + target.getName() + "."));
            return;
        }

        // 3. Check dynamic custom upgrades from config
        CustomUpgrade customUpgrade = UpgradeRegistry.get(itemArg);
        if (customUpgrade != null) {
            target.getInventory().addItem(MinionItemFactory.createUpgradeItem(customUpgrade));
            sender.sendMessage(ColorUtil.color("&aGave " + customUpgrade.getDisplayName() + " upgrade to " + target.getName() + "."));
            return;
        }

        // 4. Check built-in MinionType
        MinionType type = MinionType.fromString(itemArg);
        if (type == null) {
            sender.sendMessage(ColorUtil.color("&cUnknown minion or item type: " + itemArg));
            return;
        }

        int tier = 1;
        if (args.length >= 4) {
            try {
                tier = Math.clamp(Integer.parseInt(args[3]), 1, MinionTier.getMaxTier());
            } catch (NumberFormatException ignored) {}
        }

        ItemStack minionItem = MinionItemFactory.createMinionItem(type, tier, 0);
        target.getInventory().addItem(minionItem);

        sender.sendMessage(ColorUtil.color("&aGave " + type.getDisplayName() + " Minion " + MinionTier.toRoman(tier) + " to " + target.getName() + "."));
        target.sendMessage(ColorUtil.color("&6[Minions] &aYou received a " + type.getDisplayName() + " Minion " + MinionTier.toRoman(tier) + "!"));
    }

    private void handleList(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtil.color("&cOnly players can list their minions."));
            return;
        }

        List<Minion> playerMinions = plugin.getMinionManager().getMinionsByOwner(player.getUniqueId());
        if (playerMinions.isEmpty()) {
            player.sendMessage(ColorUtil.color("&e[Minions] &7You currently have no placed minions."));
            return;
        }

        player.sendMessage(ColorUtil.color("&6=== Your Active Minions (" + playerMinions.size() + ") ==="));
        for (Minion m : playerMinions) {
            Location loc = m.getLocation();
            String coords = loc != null ? String.format("%d, %d, %d", loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()) : "Unknown";
            player.sendMessage(ColorUtil.color("&e- &a" + m.getDisplayName() + " Minion " + MinionTier.toRoman(m.getTier()) +
                    " &7at &f[" + coords + "] &7- " + m.getState().getDisplayStatus()));
        }
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("skyblockminions.admin")) {
            sender.sendMessage(ColorUtil.color("&cYou do not have permission to reload the plugin."));
            return;
        }

        plugin.reloadConfig();
        plugin.loadRegistries();
        sender.sendMessage(ColorUtil.color("&6[Minions] &aConfiguration reloaded successfully (" +
                FuelRegistry.getKeys().size() + " fuels, " + UpgradeRegistry.getKeys().size() + " upgrades, " +
                MinionRegistry.getAll().size() + " minion definitions)."));
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ColorUtil.color("&6=== SkyBlock Minions Commands ==="));
        sender.sendMessage(ColorUtil.color("&e/minion create &7- Open In-Game Minion Creator Studio"));
        sender.sendMessage(ColorUtil.color("&e/minion give <player> <type|fuel|upgrade> [tier] &7- Give a minion or item"));
        sender.sendMessage(ColorUtil.color("&e/minion list &7- View your placed minions"));
        sender.sendMessage(ColorUtil.color("&e/minion reload &7- Reload configuration"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(List.of("create", "give", "list", "reload"), args[0]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                names.add(p.getName());
            }
            return filter(names, args[1]);
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            List<String> items = new ArrayList<>();
            for (MinionType t : MinionType.values()) {
                items.add(t.name().toLowerCase());
            }
            items.addAll(MinionRegistry.getKeys());
            items.addAll(FuelRegistry.getKeys());
            items.addAll(UpgradeRegistry.getKeys());
            return filter(items, args[2]);
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("give")) {
            List<String> tiers = new ArrayList<>();
            for (int i = 1; i <= MinionTier.getMaxTier(); i++) {
                tiers.add(String.valueOf(i));
            }
            return filter(tiers, args[3]);
        }

        return Collections.emptyList();
    }

    private List<String> filter(List<String> options, String prefix) {
        List<String> matches = new ArrayList<>();
        for (String opt : options) {
            if (opt.toLowerCase().startsWith(prefix.toLowerCase())) {
                matches.add(opt);
            }
        }
        return matches;
    }
}
