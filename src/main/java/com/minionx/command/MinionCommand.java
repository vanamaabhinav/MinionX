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
        if (!hasAdminPermission(sender)) {
            if (args.length >= 1 && args[0].equalsIgnoreCase("list")) {
                handleList(sender);
                return true;
            }
            if (args.length >= 2 && args[0].equalsIgnoreCase("limit") && args[1].equalsIgnoreCase("check")) {
                handleLimit(sender, args);
                return true;
            }
            if (args.length >= 1 && args[0].equalsIgnoreCase("claim")) {
                handleClaim(sender);
                return true;
            }
            sender.sendMessage(ColorUtil.color("&cYou do not have permission to use this command."));
            return true;
        }

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
            case "claim" -> handleClaim(sender);
            case "recover" -> handleRecover(sender, args);
            case "reload" -> handleReload(sender);
            default -> sendHelp(sender);
        }

        return true;
    }

    private boolean hasAdminPermission(CommandSender sender) {
        return sender.isOp() || sender.hasPermission("minionx.admin");
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
            } else if ("upgrade".equalsIgnoreCase(args[1]) || "hopper".equalsIgnoreCase(args[1])) {
                com.minionx.gui.UpgradeCreatorGUI.open(player, null);
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
        if (!hasAdminPermission(sender)) {
            sender.sendMessage(ColorUtil.color("&cYou do not have permission to use this command."));
            return;
        }

        sender.sendMessage(ColorUtil.color("&6=== MinionX Commands ==="));
        sender.sendMessage(ColorUtil.color("&e/minion create &7- Open In-Game Minion Creator Studio"));
        sender.sendMessage(ColorUtil.color("&e/minion tier [list|edit|<minion_id>] &7- Open Tier Studio GUI"));
        sender.sendMessage(ColorUtil.color("&e/minion give <player> <type|fuel|upgrade|skin> [tier|skin_id] &7- Give a minion, item, or skin"));
        sender.sendMessage(ColorUtil.color("&e/minion limit <check|add|remove|set> <player> [amount] &7- Manage player limits"));
        sender.sendMessage(ColorUtil.color("&e/minion list &7- View your placed minions"));
        sender.sendMessage(ColorUtil.color("&e/minion claim &7- Claim pending or quarantined escrow items"));
        sender.sendMessage(ColorUtil.color("&e/minion recover [list|resolve] &7- Audit and resolve escrow operations"));
        sender.sendMessage(ColorUtil.color("&e/minion quarantine [on|off|status|toggle] &7- Manage anti-dupe quarantine mode"));
        sender.sendMessage(ColorUtil.color("&e/minion killall &7- Kill and purge all active minions and holograms"));
        sender.sendMessage(ColorUtil.color("&e/minion reload &7- Reload configuration"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!hasAdminPermission(sender)) {
            if (args.length == 1) {
                return filter(List.of("list", "claim"), args[0]);
            }
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return filter(List.of("create", "tier", "give", "limit", "list", "claim", "recover", "quarantine", "killall", "purge", "reload"), args[0]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("create")) {
            return filter(List.of("minion", "skin", "storage", "fuel", "upgrade", "hopper"), args[1]);
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

        if (args.length == 2 && args[0].equalsIgnoreCase("recover")) {
            return filter(List.of("list", "resolve"), args[1]);
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("recover") && args[1].equalsIgnoreCase("resolve")) {
            com.minionx.storage.MinionStorage storage = plugin != null ? plugin.getMinionStorage() : null;
            if (storage != null) {
                List<String> opIds = new ArrayList<>();
                for (com.minionx.storage.PendingOperation op : storage.getPendingOperations()) {
                    opIds.add(op.operationId());
                }
                return filter(opIds, args[2]);
            }
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("recover") && args[1].equalsIgnoreCase("resolve")) {
            return filter(List.of("deliver", "complete"), args[3]);
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

    private void handleClaim(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtil.color("&cOnly players can claim pending escrow items."));
            return;
        }

        com.minionx.storage.MinionStorage storage = plugin != null ? plugin.getMinionStorage() : null;
        if (storage == null) {
            player.sendMessage(ColorUtil.color("&cPersistence storage is not available."));
            return;
        }

        List<com.minionx.storage.PendingOperation> pending = storage.getPendingOperations();
        List<com.minionx.storage.PendingOperation> playerOps = new ArrayList<>();
        for (com.minionx.storage.PendingOperation op : pending) {
            if (player.getUniqueId().equals(op.playerUuid())) {
                playerOps.add(op);
            }
        }

        if (playerOps.isEmpty()) {
            player.sendMessage(ColorUtil.color("&7You have no unclaimed escrow items or pending operations."));
            return;
        }

        int claimedOps = 0;
        int totalItemsRestored = 0;

        for (com.minionx.storage.PendingOperation op : playerOps) {
            String state = op.state();
            // 1. Strict Ambiguous Delivery Protection:
            // Prohibit automatic player claim for ANY ambiguous or unresolved state
            boolean isAmbiguous = "DELIVERY_ATTEMPTED".equalsIgnoreCase(state)
                    || "UNKNOWN".equalsIgnoreCase(state)
                    || "AMBIGUOUS_PARTIAL_DELIVERY".equalsIgnoreCase(state)
                    || "COMPLETION_UNCONFIRMED".equalsIgnoreCase(state);
            if (isAmbiguous) {
                player.sendMessage(ColorUtil.color("&cOperation " + op.operationId() + " requires administrator audit due to an ambiguous delivery outcome (" + state + "). Automatic claim is prohibited to prevent item/XP duplication."));
                continue;
            }
            if (!"MINION_REMOVED".equalsIgnoreCase(state) && !"QUARANTINED_LEFTOVERS".equalsIgnoreCase(state) && !"DELIVERY_FAILED_OFFLINE".equalsIgnoreCase(state) && !"PREPARED".equalsIgnoreCase(state) && !"DELIVERY_CLAIM_FAILED".equalsIgnoreCase(state)) {
                continue;
            }

            String payload = op.payload();
            if (payload == null || payload.isBlank()) continue;

            int xpToGrant = 0;
            boolean xpAlreadyDelivered = false;
            if (payload.startsWith("XP_DELIVERED:") || payload.startsWith("XP_GRANTED:")) {
                xpAlreadyDelivered = true;
                int prefixLen = payload.startsWith("XP_DELIVERED:") ? 13 : 11;
                int sep = payload.indexOf(";;;PAYLOAD:::");
                if (sep >= prefixLen) {
                    try {
                        xpToGrant = Integer.parseInt(payload.substring(prefixLen, sep));
                    } catch (Exception ignored) {}
                    payload = payload.substring(sep + 13);
                }
            } else if ((payload.startsWith("XP:") || payload.startsWith("XP_PENDING:")) && payload.contains(";;;PAYLOAD:::")) {
                int prefixLen = payload.startsWith("XP_PENDING:") ? 11 : 3;
                int sep = payload.indexOf(";;;PAYLOAD:::");
                if (sep >= prefixLen) {
                    try {
                        xpToGrant = Integer.parseInt(payload.substring(prefixLen, sep));
                    } catch (Exception ignored) {}
                    payload = payload.substring(sep + 13);
                }
            }

            String[] rawItems = payload.split(";;;ITEM;;;");
            List<ItemStack> toDeliver = new ArrayList<>();
            for (String raw : rawItems) {
                if (raw == null || raw.isBlank()) continue;
                try {
                    org.bukkit.configuration.file.YamlConfiguration yc = new org.bukkit.configuration.file.YamlConfiguration();
                    yc.loadFromString(raw);
                    ItemStack stack = yc.getItemStack("item");
                    if (stack != null) toDeliver.add(stack);
                } catch (Exception ignored) {}
            }

            if (toDeliver.isEmpty()) {
                if (xpToGrant > 0 && !xpAlreadyDelivered) {
                    player.giveExp(xpToGrant);
                    player.sendMessage(ColorUtil.color("&aClaimed &b" + xpToGrant + " XP &afrom preserved minion state!"));
                }
                try {
                    storage.removePendingOperation(op.operationId());
                } catch (Exception ignored) {}
                continue;
            }

            // 2. Crash-Safety Boundary: Transition operation to DELIVERY_ATTEMPTED before modifying player inventory or granting XP!
            // If the server stops or crashes at any point during delivery or before completion acknowledgment,
            // the operation remains in DELIVERY_ATTEMPTED on reboot, preventing automatic re-claims and duplicate XP!
            boolean claimAttemptPersisted = false;
            try {
                claimAttemptPersisted = storage.updatePendingOperationState(op.operationId(), "DELIVERY_ATTEMPTED", op.payload());
            } catch (Throwable t) {
                claimAttemptPersisted = false;
            }
            if (!claimAttemptPersisted) {
                player.sendMessage(ColorUtil.color("&cFailed to confirm durable claim transaction for operation " + op.operationId() + ". Transaction aborted safely to prevent item/XP loss."));
                continue;
            }

            List<ItemStack> leftovers = new ArrayList<>();
            for (ItemStack item : toDeliver) {
                int origAmount = item.getAmount();
                ItemStack itemClone = item.clone();
                java.util.HashMap<Integer, ItemStack> left = player.getInventory().addItem(itemClone);
                if (!left.isEmpty()) {
                    leftovers.addAll(left.values());
                    int leftoverAmount = left.values().stream().mapToInt(ItemStack::getAmount).sum();
                    totalItemsRestored += (origAmount - leftoverAmount);
                } else {
                    totalItemsRestored += origAmount;
                }
            }

            if (leftovers.isEmpty()) {
                if (xpToGrant > 0 && !xpAlreadyDelivered) {
                    player.giveExp(xpToGrant);
                    player.sendMessage(ColorUtil.color("&aClaimed &b" + xpToGrant + " XP &afrom preserved minion state!"));
                }
                try {
                    storage.removePendingOperation(op.operationId());
                    claimedOps++;
                } catch (Exception ignored) {}
            } else {
                List<String> remSerialized = new ArrayList<>();
                for (ItemStack rem : leftovers) {
                    org.bukkit.configuration.file.YamlConfiguration yc = new org.bukkit.configuration.file.YamlConfiguration();
                    yc.set("item", rem);
                    remSerialized.add(yc.saveToString());
                }
                String remPayloadStr = String.join(";;;ITEM;;;", remSerialized);
                if (xpToGrant > 0 && !xpAlreadyDelivered) {
                    remPayloadStr = "XP:" + xpToGrant + ";;;PAYLOAD:::" + remPayloadStr;
                }
                try {
                    storage.updatePendingOperationState(op.operationId(), "QUARANTINED_LEFTOVERS", remPayloadStr);
                } catch (Exception ignored) {}
                player.sendMessage(ColorUtil.color("&eYour inventory became full! Some items remain in your escrow claim. Make space and run /minion claim again."));
                break;
            }
        }

        if (totalItemsRestored > 0) {
            player.sendMessage(ColorUtil.color("&aSuccessfully claimed &e" + totalItemsRestored + " &aescrow items!"));
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
        } else if (claimedOps > 0) {
            player.sendMessage(ColorUtil.color("&aEscrow operations resolved."));
        }
    }

    private void handleRecover(CommandSender sender, String[] args) {
        if (!hasAdminPermission(sender)) {
            sender.sendMessage(ColorUtil.color("&cYou do not have permission to access escrow recovery."));
            return;
        }

        com.minionx.storage.MinionStorage storage = plugin != null ? plugin.getMinionStorage() : null;
        if (storage == null) {
            sender.sendMessage(ColorUtil.color("&cPersistence storage is not available."));
            return;
        }

        if (args.length <= 1 || args[1].equalsIgnoreCase("list")) {
            List<com.minionx.storage.PendingOperation> pending = storage.getPendingOperations();
            if (pending.isEmpty()) {
                sender.sendMessage(ColorUtil.color("&aNo pending or quarantined operations in escrow ledger."));
                return;
            }
            sender.sendMessage(ColorUtil.color("&6=== Pending Escrow Operations (" + pending.size() + ") ==="));
            for (com.minionx.storage.PendingOperation op : pending) {
                sender.sendMessage(ColorUtil.color("&e• &f" + op.operationId() + " &7[" + op.state() + "] Minion: &b" + op.minionId() + " &7Player: &a" + op.playerUuid()));
            }
            sender.sendMessage(ColorUtil.color("&7Use &e/minion recover resolve <opId> <deliver|complete> [reason] &7to audit and resolve."));
            return;
        }

        if (args[1].equalsIgnoreCase("resolve")) {
            if (args.length < 4) {
                sender.sendMessage(ColorUtil.color("&cUsage: /minion recover resolve <opId> <deliver|complete> [reason]"));
                return;
            }
            String opId = args[2];
            String action = args[3].toLowerCase();
            String reason = args.length >= 5 ? String.join(" ", java.util.Arrays.copyOfRange(args, 4, args.length)) : "Operator audit resolution";

            com.minionx.storage.PendingOperation targetOp = null;
            for (com.minionx.storage.PendingOperation op : storage.getPendingOperations()) {
                if (op.operationId().equalsIgnoreCase(opId)) {
                    targetOp = op;
                    break;
                }
            }

            if (targetOp == null) {
                sender.sendMessage(ColorUtil.color("&cOperation '" + opId + "' not found in escrow ledger."));
                return;
            }

            String operatorId = (sender instanceof Player p) ? p.getUniqueId().toString() : "CONSOLE";
            long now = System.currentTimeMillis();

            if ("deliver".equals(action) || "deliver-force-all".equals(action) || "deliver-force-all-with-xp".equals(action)) {
                if (("UNKNOWN".equalsIgnoreCase(targetOp.state()) || "DELIVERY_ATTEMPTED".equalsIgnoreCase(targetOp.state())) && !"deliver-force-all".equals(action) && !"deliver-force-all-with-xp".equals(action)) {
                    sender.sendMessage(ColorUtil.color("&cCRITICAL: Operation " + opId + " has state '" + targetOp.state() + "'. Delivery may have partially succeeded before interruption! Plain 'deliver' is prohibited to prevent duplicate items."));
                    sender.sendMessage(ColorUtil.color("&cIf you have verified the player received 0 items, use: /minion recover resolve " + opId + " deliver-force-all [reason] (items only) or deliver-force-all-with-xp [reason]"));
                    sender.sendMessage(ColorUtil.color("&cOtherwise, use /minion recover resolve " + opId + " complete <reason> to clear."));
                    return;
                }

                Player targetPlayer = Bukkit.getPlayer(targetOp.playerUuid());
                if (targetPlayer == null || !targetPlayer.isOnline()) {
                    sender.sendMessage(ColorUtil.color("&cCannot deliver: player " + targetOp.playerUuid() + " is offline. Operation retained in escrow ledger."));
                    return;
                }

                String payload = targetOp.payload();

                // 1. Extract XP if present (must occur BEFORE leftover extraction to avoid stripping prefix)
                int xpToGrant = 0;
                boolean xpAlreadyDelivered = false;
                if (payload != null && (payload.startsWith("XP_DELIVERED:") || payload.startsWith("XP_GRANTED:"))) {
                    xpAlreadyDelivered = true;
                    int prefixLen = payload.startsWith("XP_DELIVERED:") ? 13 : 11;
                    int sep = payload.indexOf(";;;PAYLOAD:::");
                    if (sep >= prefixLen) {
                        try {
                            xpToGrant = Integer.parseInt(payload.substring(prefixLen, sep));
                        } catch (Exception ignored) {}
                        payload = payload.substring(sep + 13);
                    }
                } else if (payload != null && (payload.startsWith("XP:") || payload.startsWith("XP_PENDING:")) && payload.contains(";;;PAYLOAD:::")) {
                    int prefixLen = payload.startsWith("XP_PENDING:") ? 11 : 3;
                    int sep = payload.indexOf(";;;PAYLOAD:::");
                    if (sep >= prefixLen) {
                        try {
                            xpToGrant = Integer.parseInt(payload.substring(prefixLen, sep));
                        } catch (Exception ignored) {}
                        payload = payload.substring(sep + 13);
                    }
                }

                boolean isAmbiguousDelivery = "UNKNOWN".equalsIgnoreCase(targetOp.state())
                        || "DELIVERY_ATTEMPTED".equalsIgnoreCase(targetOp.state())
                        || "AMBIGUOUS_PARTIAL_DELIVERY".equalsIgnoreCase(targetOp.state());

                // 2. Extract leftover items only if AMBIGUOUS_PARTIAL_DELIVERY
                if ("AMBIGUOUS_PARTIAL_DELIVERY".equalsIgnoreCase(targetOp.state())) {
                    int leftoverIndex = (payload != null) ? payload.indexOf(";;;LEFTOVER:::") : -1;
                    if (leftoverIndex >= 0) {
                        payload = payload.substring(leftoverIndex + 14);
                    } else {
                        sender.sendMessage(ColorUtil.color("&cCRITICAL: Operation " + opId + " is AMBIGUOUS_PARTIAL_DELIVERY but leftover section is missing. Cannot deliver safely."));
                        return;
                    }
                }

                // 3. Delivery logic & Zero-Leftover XP Invariant
                boolean isForceOverrideWithXp = "deliver-force-all-with-xp".equals(action);
                boolean allowXpGrant = isForceOverrideWithXp || (!isAmbiguousDelivery && !xpAlreadyDelivered);

                int itemsDelivered = 0;
                int actualXpGranted = 0;
                boolean operationResolved = false;

                if (payload != null && !payload.isBlank()) {
                    String[] rawItems = payload.split(";;;ITEM;;;");
                    List<ItemStack> toDeliver = new ArrayList<>();
                    for (String raw : rawItems) {
                        try {
                            org.bukkit.configuration.file.YamlConfiguration yc = new org.bukkit.configuration.file.YamlConfiguration();
                            yc.loadFromString(raw);
                            ItemStack stack = yc.getItemStack("item");
                            if (stack != null) toDeliver.add(stack);
                        } catch (Exception ignored) {}
                    }

                    if (isForceOverrideWithXp) {
                        // Explicit administrative override: force-deliver all items (drop overflow) and force-grant XP
                        if (xpToGrant > 0) {
                            targetPlayer.giveExp(xpToGrant);
                            actualXpGranted = xpToGrant;
                        }
                        for (ItemStack stack : toDeliver) {
                            int origAmount = stack.getAmount();
                            ItemStack clone = stack.clone();
                            java.util.HashMap<Integer, ItemStack> left = targetPlayer.getInventory().addItem(clone);
                            for (ItemStack rem : left.values()) {
                                targetPlayer.getWorld().dropItemNaturally(targetPlayer.getLocation(), rem);
                            }
                            itemsDelivered += origAmount;
                        }
                        try {
                            storage.removePendingOperation(targetOp.operationId());
                            operationResolved = true;
                        } catch (Exception ignored) {}
                    } else {
                        // Ordinary recovery (deliver or deliver-force-all):
                        // Enforce the Zero-Leftover XP Invariant!
                        List<ItemStack> leftoverItems = new ArrayList<>();
                        for (ItemStack stack : toDeliver) {
                            int origAmount = stack.getAmount();
                            ItemStack clone = stack.clone();
                            java.util.HashMap<Integer, ItemStack> left = targetPlayer.getInventory().addItem(clone);
                            if (!left.isEmpty()) {
                                leftoverItems.addAll(left.values());
                                int leftoverAmount = left.values().stream().mapToInt(ItemStack::getAmount).sum();
                                itemsDelivered += (origAmount - leftoverAmount);
                            } else {
                                itemsDelivered += origAmount;
                            }
                        }

                        if (leftoverItems.isEmpty()) {
                            // All items fit: zero leftovers! Now grant XP safely
                            if (xpToGrant > 0 && allowXpGrant) {
                                targetPlayer.giveExp(xpToGrant);
                                actualXpGranted = xpToGrant;
                            } else if (xpToGrant > 0) {
                                if (xpAlreadyDelivered) {
                                    sender.sendMessage(ColorUtil.color("&e[Notice] Operation " + opId + " was marked as XP_GRANTED (" + xpToGrant + " XP). Stored XP was already delivered previously."));
                                } else {
                                    sender.sendMessage(ColorUtil.color("&e[Notice] Operation " + opId + " has ambiguous delivery state (" + targetOp.state() + "). Stored " + xpToGrant + " XP withheld to prevent duplicate granting."));
                                }
                            }
                            try {
                                storage.removePendingOperation(targetOp.operationId());
                                operationResolved = true;
                            } catch (Exception ignored) {}
                        } else {
                            // Inventory became full: items remain in leftovers!
                            // Zero-leftover invariant: DO NOT grant pending XP!
                            List<String> remSerialized = new ArrayList<>();
                            for (ItemStack rem : leftoverItems) {
                                org.bukkit.configuration.file.YamlConfiguration yc = new org.bukkit.configuration.file.YamlConfiguration();
                                yc.set("item", rem);
                                remSerialized.add(yc.saveToString());
                            }
                            String remPayloadStr = String.join(";;;ITEM;;;", remSerialized);
                            if (xpToGrant > 0 && !xpAlreadyDelivered) {
                                remPayloadStr = "XP:" + xpToGrant + ";;;PAYLOAD:::" + remPayloadStr;
                            }
                            try {
                                storage.updatePendingOperationState(targetOp.operationId(), "QUARANTINED_LEFTOVERS", remPayloadStr);
                            } catch (Exception ignored) {}
                            sender.sendMessage(ColorUtil.color("&e[Warning] Target player's inventory became full (" + leftoverItems.size() + " leftover stacks). Remaining items and ungranted XP (" + xpToGrant + " XP) preserved in QUARANTINED_LEFTOVERS."));
                        }
                    }
                    targetPlayer.sendMessage(ColorUtil.color("&aAn administrator resolved and delivered your quarantined minion items!"));
                }

                if (plugin != null) {
                    plugin.getLogger().info("[MinionX Escrow Audit] Operator " + operatorId + " at " + now + " resolved operation " + opId + " as " + action.toUpperCase() + " (" + itemsDelivered + " items, " + actualXpGranted + " XP). Reason: " + reason);
                }
                if (operationResolved) {
                    sender.sendMessage(ColorUtil.color("&aOperation &e" + opId + " &aresolved and delivered (" + itemsDelivered + " items, " + actualXpGranted + " XP). Audit logged."));
                } else {
                    sender.sendMessage(ColorUtil.color("&eOperation &e" + opId + " &apartially delivered (" + itemsDelivered + " items, " + actualXpGranted + " XP granted). Leftovers retained in ledger. Audit logged."));
                }
            } else if ("complete".equals(action)) {
                try {
                    storage.removePendingOperation(targetOp.operationId());
                } catch (Exception ignored) {}
                if (plugin != null) {
                    plugin.getLogger().info("[MinionX Escrow Audit] Operator " + operatorId + " at " + now + " resolved operation " + opId + " as COMPLETE (cleared). Reason: " + reason);
                }
                sender.sendMessage(ColorUtil.color("&aOperation &e" + opId + " &amarked as COMPLETE and removed from ledger. Audit logged."));
            } else {
                sender.sendMessage(ColorUtil.color("&cUnknown action '" + action + "'. Valid actions: deliver, deliver-force-all, complete"));
            }
            return;
        }

        sender.sendMessage(ColorUtil.color("&cUsage: /minion recover [list|resolve <opId> <deliver|deliver-force-all|complete> [reason]]"));
    }

}
