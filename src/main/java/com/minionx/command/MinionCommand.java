package com.minionx.command;

import com.minionx.quarantine.QuarantineManager;
import com.minionx.api.MinionStorageRegistry;
import com.minionx.api.CustomMinionStorage;
import com.minionx.gui.MinionSkinCreatorGUI;
import com.minionx.gui.MinionStorageCreatorGUI;
import com.minionx.gui.FuelCreatorGUI;


import com.minionx.MinionX;
import com.minionx.api.*;
import com.minionx.core.Minion;
import com.minionx.core.PlayerLimitManager;
import com.minionx.gui.MinionCreatorGUI;
import com.minionx.item.MinionItemFactory;
import com.minionx.util.ColorUtil;
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

    private final MinionX plugin;

    public MinionCommand(MinionX plugin) {
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
            case "create" -> handleCreate(sender, args);
            case "tier" -> handleTier(sender, args);
            case "quarantine" -> handleQuarantine(sender, args);
            case "give" -> handleGive(sender, args);
            case "list" -> handleList(sender);
            case "limit" -> handleLimit(sender, args);
            case "killall", "purge" -> handleKillAll(sender);
            case "reload" -> handleReload(sender);
            default -> sendHelp(sender);
        }

        return true;
    }

    private boolean hasAdminPermission(CommandSender sender) {
        return sender.hasPermission("minionx.admin");
    }

    private void handleCreate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtil.color("&cOnly players can open Creator Studios."));
            return;
        }

        if (!hasAdminPermission(player)) {
            player.sendMessage(ColorUtil.color("&cYou do not have permission to use creator studios."));
            return;
        }

        if (args.length >= 2) {
            if ("skin".equalsIgnoreCase(args[1])) {
                MinionSkinCreatorGUI.open(player, null);
                return;
            } else if ("storage".equalsIgnoreCase(args[1])) {
                MinionStorageCreatorGUI.open(player, null);
                return;
            } else if ("fuel".equalsIgnoreCase(args[1])) {
                FuelCreatorGUI.open(player, null);
                return;
            }
        }

        MinionCreatorGUI.open(player, null);
    }

    private void handleGive(CommandSender sender, String[] args) {
        if (!hasAdminPermission(sender)) {
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

        
        // Check Minion Storage item (/minion give <player> storage <storage_id> [amount])
        if (itemArg.equalsIgnoreCase("storage") && args.length >= 4) {
            String storageId = args[3].toLowerCase();
            CustomMinionStorage st = MinionStorageRegistry.get(storageId);
            if (st != null) {
                int amount = 1;
                if (args.length >= 5) {
                    try { amount = Math.max(1, Integer.parseInt(args[4])); } catch (NumberFormatException ignored) {}
                }
                target.getInventory().addItem(MinionItemFactory.createStorageItem(st, amount));
                sender.sendMessage(ColorUtil.color("&aGave " + amount + "x " + st.getDisplayName() + " &ato " + target.getName() + "."));
                target.sendMessage(ColorUtil.getPrefix() + ColorUtil.color("&aYou received " + amount + "x " + st.getDisplayName() + "!"));
                return;
            } else {
                sender.sendMessage(ColorUtil.color("&cUnknown minion storage: " + args[3]));
                return;
            }
        }

        CustomMinionStorage directStorage = MinionStorageRegistry.get(itemArg);
        if (directStorage != null) {
            int amount = 1;
            if (args.length >= 4) {
                try { amount = Math.max(1, Integer.parseInt(args[3])); } catch (NumberFormatException ignored) {}
            }
            target.getInventory().addItem(MinionItemFactory.createStorageItem(directStorage, amount));
            sender.sendMessage(ColorUtil.color("&aGave " + amount + "x " + directStorage.getDisplayName() + " &ato " + target.getName() + "."));
            target.sendMessage(ColorUtil.getPrefix() + ColorUtil.color("&aYou received " + amount + "x " + directStorage.getDisplayName() + "!"));
            return;
        }

        // 0. Check skin item (/minion give <player> skin <skin_id> or /minion give <player> <skin_id>)
        if (itemArg.equalsIgnoreCase("skin") && args.length >= 4) {
            String skinId = args[3].toLowerCase();
            com.minionx.api.MinionSkin skin = com.minionx.api.MinionSkinRegistry.get(skinId);
            if (skin != null) {
                target.getInventory().addItem(MinionItemFactory.createSkinItem(skin));
                sender.sendMessage(ColorUtil.color("&aGave " + skin.getDisplayName() + " &aminion skin to " + target.getName() + "."));
                target.sendMessage(ColorUtil.getPrefix() + ColorUtil.color("&aYou received a " + skin.getDisplayName() + " &aminion skin!"));
                return;
            } else {
                sender.sendMessage(ColorUtil.color("&cUnknown minion skin: " + args[3]));
                return;
            }
        }

        com.minionx.api.MinionSkin directSkin = com.minionx.api.MinionSkinRegistry.get(itemArg);
        if (directSkin != null) {
            target.getInventory().addItem(MinionItemFactory.createSkinItem(directSkin));
            sender.sendMessage(ColorUtil.color("&aGave " + directSkin.getDisplayName() + " &aminion skin to " + target.getName() + "."));
            target.sendMessage(ColorUtil.getPrefix() + ColorUtil.color("&aYou received a " + directSkin.getDisplayName() + " &aminion skin!"));
            return;
        }

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
            target.sendMessage(ColorUtil.getPrefix() + ColorUtil.color("&aYou received a " + customDef.getDisplayName() + " Minion " + MinionTier.toRoman(tier) + "!"));
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
        target.sendMessage(ColorUtil.getPrefix() + ColorUtil.color("&aYou received a " + type.getDisplayName() + " Minion " + MinionTier.toRoman(tier) + "!"));
    }

    private void handleLimit(CommandSender sender, String[] args) {
        PlayerLimitManager limitManager = plugin.getPlayerLimitManager();
        if (limitManager == null) {
            sender.sendMessage(ColorUtil.color("&cPlayer limit manager is not loaded."));
            return;
        }

        if (args.length == 1 || args[1].equalsIgnoreCase("check")) {
            Player target = (args.length >= 3) ? Bukkit.getPlayer(args[2]) : (sender instanceof Player p ? p : null);
            if (target == null) {
                sender.sendMessage(ColorUtil.color("&cUsage: /minion limit check [player]"));
                return;
            }

            int limit = limitManager.getEffectiveLimit(target);
            int placed = limitManager.getPlacedCount(target.getUniqueId());
            int available = limitManager.getAvailableSlots(target);
            int bonus = limitManager.getBonusSlots(target.getUniqueId());

            String limitStr = limit < 0 ? "&aUnlimited" : "&e" + limit;
            String availStr = available < 0 ? "&aUnlimited" : "&b" + available;

            sender.sendMessage(ColorUtil.color("&6=== Minion Limit: &e" + target.getName() + " &6==="));
            sender.sendMessage(ColorUtil.color("&7Placed Minions: &f" + placed));
            sender.sendMessage(ColorUtil.color("&7Max Limit: " + limitStr));
            sender.sendMessage(ColorUtil.color("&7Available Slots: " + availStr));
            sender.sendMessage(ColorUtil.color("&7Bonus Purchased Slots: &d+" + bonus));
            return;
        }

        if (!hasAdminPermission(sender)) {
            sender.sendMessage(ColorUtil.color("&cYou do not have permission to modify player limits."));
            return;
        }

        if (args.length < 4) {
            sender.sendMessage(ColorUtil.color("&cUsage: /minion limit <add|remove|set> <player> <amount>"));
            return;
        }

        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) {
            sender.sendMessage(ColorUtil.color("&cPlayer '" + args[2] + "' not found."));
            return;
        }

        int amount;
        try {
            amount = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ColorUtil.color("&cInvalid number: " + args[3]));
            return;
        }

        String action = args[1].toLowerCase();
        switch (action) {
            case "add" -> {
                limitManager.addBonusSlots(target.getUniqueId(), amount);
                sender.sendMessage(ColorUtil.color("&aAdded &e+" + amount + " &abonus minion slots to &f" + target.getName() + "&a. Total bonus: &e" + limitManager.getBonusSlots(target.getUniqueId())));
                target.sendMessage(ColorUtil.getPrefix() + ColorUtil.color("&aYou received &e+" + amount + " &abonus minion slots!"));
            }
            case "remove" -> {
                limitManager.removeBonusSlots(target.getUniqueId(), amount);
                sender.sendMessage(ColorUtil.color("&cRemoved &e" + amount + " &cbonus minion slots from &f" + target.getName() + "&c. Total bonus: &e" + limitManager.getBonusSlots(target.getUniqueId())));
            }
            case "set" -> {
                limitManager.setBonusSlots(target.getUniqueId(), amount);
                sender.sendMessage(ColorUtil.color("&aSet bonus minion slots for &f" + target.getName() + " &ato &e" + amount + "&a."));
            }
            default -> sender.sendMessage(ColorUtil.color("&cUsage: /minion limit <add|remove|set|check> <player> <amount>"));
        }
    }

    private void handleList(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtil.color("&cOnly players can list their minions."));
            return;
        }

        List<Minion> playerMinions = plugin.getMinionManager().getMinionsByOwner(player.getUniqueId());
        if (playerMinions.isEmpty()) {
            player.sendMessage(ColorUtil.getPrefix() + ColorUtil.color("&7You currently have no placed minions."));
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

    private void handleKillAll(CommandSender sender) {
        if (!hasAdminPermission(sender)) {
            sender.sendMessage(ColorUtil.color("&cYou do not have permission to kill all minions."));
            return;
        }

        int count = plugin.getMinionManager().killAndPurgeAll();
        sender.sendMessage(ColorUtil.getPrefix() + ColorUtil.color("&aSuccessfully killed and purged &e" + count + " &aactive minions, holograms, and ticking tasks!"));
    }

    private void handleReload(CommandSender sender) {
        if (!hasAdminPermission(sender)) {
            sender.sendMessage(ColorUtil.color("&cYou do not have permission to reload the plugin."));
            return;
        }

        plugin.reloadConfig();
        plugin.loadRegistries();
        sender.sendMessage(ColorUtil.getPrefix() + ColorUtil.color("&aConfiguration reloaded successfully (" +
                FuelRegistry.getKeys().size() + " fuels, " + UpgradeRegistry.getKeys().size() + " upgrades, " +
                MinionRegistry.getAll().size() + " minion definitions)."));
    }

    private void handleTier(CommandSender sender, String[] args) {
        if (!hasAdminPermission(sender)) {
            sender.sendMessage(ColorUtil.color("&cYou do not have permission to manage minion tiers."));
            return;
        }

        if (args.length >= 2 && "list".equalsIgnoreCase(args[1])) {
            sender.sendMessage(ColorUtil.color("&6=== MinionX Tier Configuration (" + MinionTier.getMaxTier() + " Tiers) ==="));
            for (int t = 1; t <= MinionTier.getMaxTier(); t++) {
                MinionTier.TierData data = MinionTier.getTierData(t);
                double delay = data != null ? data.getActionDelay() : MinionTier.getActionDelay(t);
                int slots = data != null ? data.getStorageSlots() : MinionTier.getStorageSlots(t);
                int cost = data != null ? data.getUpgradeCost() : MinionTier.getUpgradeCost(t);
                sender.sendMessage(ColorUtil.color("&eTier " + MinionTier.toRoman(t) + " &7(Lv." + t + "): &fSpeed=" +
                        String.format("%.1f", delay) + "s, Slots=" + slots + ", UpgradeCost=" + cost));
            }
            return;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtil.color("&cOnly players can open the Tier Studio GUI. Use /minion tier list from console."));
            return;
        }

        if (args.length >= 2) {
            String target = args[1].toLowerCase();
            com.minionx.api.MinionDefinition def = com.minionx.api.MinionRegistry.get(target);
            if (def != null) {
                com.minionx.gui.TierEditorGUI.openOverview(player, def.getId());
                return;
            }
        }

        if (args.length >= 3 && "edit".equalsIgnoreCase(args[1])) {
            try {
                int tier = Integer.parseInt(args[2]);
                if (tier >= 1 && tier <= MinionTier.getMaxTier()) {
                    com.minionx.gui.TierEditorListener.startEdit(player, tier);
                    return;
                }
            } catch (NumberFormatException ignored) {}
        }

        com.minionx.gui.TierEditorGUI.openOverview(player);
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ColorUtil.color("&6=== MinionX Commands ==="));
        sender.sendMessage(ColorUtil.color("&e/minion create &7- Open In-Game Minion Creator Studio"));
        sender.sendMessage(ColorUtil.color("&e/minion tier [list|edit|<minion_id>] &7- Open Tier Studio GUI"));
        sender.sendMessage(ColorUtil.color("&e/minion give <player> <type|fuel|upgrade|skin> [tier|skin_id] &7- Give a minion, item, or skin"));
        sender.sendMessage(ColorUtil.color("&e/minion limit <check|add|remove|set> <player> [amount] &7- Manage player limits"));
        sender.sendMessage(ColorUtil.color("&e/minion list &7- View your placed minions"));
        sender.sendMessage(ColorUtil.color("&e/minion killall &7- Kill and purge all active minions and holograms"));
        sender.sendMessage(ColorUtil.color("&e/minion reload &7- Reload configuration"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(List.of("create", "tier", "give", "limit", "list", "killall", "purge", "reload"), args[0]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("create")) {
            return filter(List.of("minion", "skin", "storage", "fuel"), args[1]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("tier")) {
            List<String> options = new ArrayList<>(List.of("list", "edit"));
            options.addAll(MinionRegistry.getKeys());
            return filter(options, args[1]);
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("tier") && args[1].equalsIgnoreCase("edit")) {
            List<String> tiers = new ArrayList<>();
            for (int i = 1; i <= MinionTier.getMaxTier(); i++) {
                tiers.add(String.valueOf(i));
            }
            return filter(tiers, args[2]);
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
            items.add("skin");
            for (MinionType t : MinionType.values()) {
                items.add(t.name().toLowerCase());
            }
            items.addAll(MinionRegistry.getKeys());
            items.addAll(FuelRegistry.getKeys());
            items.addAll(UpgradeRegistry.getKeys());
            items.addAll(com.minionx.api.MinionSkinRegistry.getKeys());
            return filter(items, args[2]);
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("give")) {
            if (args[2].equalsIgnoreCase("skin")) {
                return filter(new ArrayList<>(com.minionx.api.MinionSkinRegistry.getKeys()), args[3]);
            }
            List<String> tiers = new ArrayList<>();
            for (int i = 1; i <= MinionTier.getMaxTier(); i++) {
                tiers.add(String.valueOf(i));
            }
            return filter(tiers, args[3]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("limit")) {
            return filter(List.of("check", "add", "remove", "set"), args[1]);
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("limit")) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                names.add(p.getName());
            }
            return filter(names, args[2]);
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

    private void handleQuarantine(CommandSender sender, String[] args) {
        if (!hasAdminPermission(sender)) {
            sender.sendMessage(ColorUtil.color("&cYou do not have permission to manage quarantine mode."));
            return;
        }

        if (args.length == 1 || "status".equalsIgnoreCase(args[1])) {
            boolean active = QuarantineManager.isQuarantined();
            sender.sendMessage(ColorUtil.color("&6&l[Minion&c&lX&6&l Quarantine] &7Status: " + (active ? "&c&lACTIVE (Anti-Dupe Lockdown)" : "&a&lINACTIVE (Normal Operation)")));
            sender.sendMessage(ColorUtil.color("&7Use &e/minion quarantine on &7or &e/minion quarantine off &7to toggle."));
            return;
        }

        String action = args[1].toLowerCase();
        if ("on".equals(action) || "enable".equals(action)) {
            QuarantineManager.setQuarantined(true);
            sender.sendMessage(ColorUtil.color("&c&l[Minion&c&lX&c&l Quarantine] &lQuarantine mode has been ACTIVATED globally!"));
        } else if ("off".equals(action) || "disable".equals(action)) {
            QuarantineManager.setQuarantined(false);
            sender.sendMessage(ColorUtil.color("&a&l[Minion&c&lX&a&l Quarantine] &lQuarantine mode has been DEACTIVATED. Normal operation restored."));
        } else if ("toggle".equals(action)) {
            boolean newState = !QuarantineManager.isQuarantined();
            QuarantineManager.setQuarantined(newState);
            sender.sendMessage(ColorUtil.color("&6&l[Minion&c&lX&6&l Quarantine] &7Toggled quarantine mode to: " + (newState ? "&c&lACTIVE" : "&a&lINACTIVE")));
        } else {
            sender.sendMessage(ColorUtil.color("&cUsage: /minion quarantine [on|off|status|toggle]"));
        }
    }

}
