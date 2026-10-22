package com.minionx.gui;

import com.minionx.api.MinionTier;
import com.minionx.util.ColorUtil;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listens for interactions in the in-game Tier Studio and Editor GUIs.
 */
@SuppressWarnings("deprecation")
public class TierEditorListener implements Listener {

    public static class TierEditSession {
        public final String minionId;
        public final int tier;
        public double delay;
        public int slots;
        public int cost;

        public TierEditSession(String minionId, int tier, double delay, int slots, int cost) {
            this.minionId = minionId;
            this.tier = tier;
            this.delay = delay;
            this.slots = slots;
            this.cost = cost;
        }
    }

    private static final Map<UUID, TierEditSession> SESSIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, String> OVERVIEW_SESSIONS = new ConcurrentHashMap<>();

    public static void setOverviewSession(UUID uuid, String minionId) {
        if (minionId != null) OVERVIEW_SESSIONS.put(uuid, minionId);
        else OVERVIEW_SESSIONS.remove(uuid);
    }

    public static String getOverviewSession(UUID uuid) {
        return OVERVIEW_SESSIONS.get(uuid);
    }

    public static void startEdit(Player player, int tier) {
        startEdit(player, null, tier);
    }

    public static void startEdit(Player player, String minionId, int tier) {
        com.minionx.api.MinionDefinition def = minionId != null ? com.minionx.api.MinionRegistry.get(minionId) : null;
        double delay = def != null ? def.getActionDelay(tier) : MinionTier.getActionDelay(tier);
        int slots = def != null ? def.getStorageSlots(tier) : MinionTier.getStorageSlots(tier);
        int cost = def != null ? def.getUpgradeCost(tier) : MinionTier.getUpgradeCost(tier);

        SESSIONS.put(player.getUniqueId(), new TierEditSession(minionId, tier, delay, slots, cost));
        TierEditorGUI.openEditor(player, minionId, tier, delay, slots, cost);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String title = event.getView().getTitle();
        if (title.equals(TierEditorGUI.OVERVIEW_TITLE) || title.startsWith(TierEditorGUI.MINION_OVERVIEW_PREFIX)) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            if (slot < 0 || slot >= 54) return;

            String minionId = OVERVIEW_SESSIONS.get(player.getUniqueId());
            com.minionx.api.MinionDefinition def = minionId != null ? com.minionx.api.MinionRegistry.get(minionId) : null;
            int maxTier = def != null ? def.getMaxTier() : MinionTier.getMaxTier();

            if (slot == 45) {
                OVERVIEW_SESSIONS.remove(player.getUniqueId());
                player.closeInventory();
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
                if (minionId != null) {
                    MinionCreatorGUI.open(player, new MinionCreatorSession(minionId));
                } else {
                    OperatorGUI.openMain(player);
                }
                return;
            }

            if (slot == 49) {
                if (def != null) {
                    // Adjust minion max-tier
                    int currentMax = def.getMaxTier();
                    if (event.getClick() == ClickType.RIGHT || event.getClick() == ClickType.SHIFT_RIGHT) {
                        def.setMaxTier(Math.max(1, currentMax - 1));
                    } else {
                        def.setMaxTier(currentMax + 1);
                    }
                    com.minionx.api.MinionRegistry.saveCustomMinion(def);
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
                    player.sendMessage(ColorUtil.getPrefix() + ColorUtil.color("&aUpdated max tier for &e" + def.getDisplayName() +
                            " &ato &eTier " + MinionTier.toRoman(def.getMaxTier()) + " &7(" + def.getMaxTier() + ")&a!"));
                    TierEditorGUI.openOverview(player, minionId);
                    return;
                }

                // Add new global tier
                int newTier = MinionTier.getMaxTier() + 1;
                MinionTier.TierData prev = MinionTier.getTierData(newTier - 1);
                double newDelay = prev != null ? Math.max(1.0, prev.getActionDelay() - 0.5) : 5.0;
                int newSlots = prev != null ? prev.getStorageSlots() : 15;
                int newCost = prev != null ? prev.getUpgradeCost() * 2 : 100000;

                MinionTier.saveTier(newTier, new MinionTier.TierData(newDelay, newSlots, newCost));
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
                player.sendMessage(ColorUtil.getPrefix() + ColorUtil.color("&aCreated custom Tier &e" + newTier + " &a(&e" + MinionTier.toRoman(newTier) + "&a)!"));
                TierEditorGUI.openOverview(player);
                return;
            }

            int tier = slot + 1;
            if (tier <= maxTier) {
                if (def == null && event.isShiftClick() && event.isRightClick() && tier > 12) {
                    MinionTier.removeTier(tier);
                    player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_BREAK, 1.0f, 1.0f);
                    player.sendMessage(ColorUtil.getPrefix() + ColorUtil.color("&cRemoved custom Tier &e" + tier + " &cfrom tiers.yml."));
                    TierEditorGUI.openOverview(player);
                    return;
                }

                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                startEdit(player, minionId, tier);
            }
            return;
        }

        if (title.startsWith(TierEditorGUI.EDITOR_TITLE_PREFIX) || title.startsWith(TierEditorGUI.MINION_EDITOR_PREFIX)) {
            event.setCancelled(true);
            TierEditSession session = SESSIONS.get(player.getUniqueId());
            if (session == null) {
                String minionId = OVERVIEW_SESSIONS.get(player.getUniqueId());
                TierEditorGUI.openOverview(player, minionId);
                return;
            }

            int slot = event.getRawSlot();
            ClickType click = event.getClick();

            if (slot == 18) { // Back to overview
                SESSIONS.remove(player.getUniqueId());
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                TierEditorGUI.openOverview(player, session.minionId);
                return;
            }

            if (slot == 22) { // Save & Apply
                if (session.minionId != null) {
                    com.minionx.api.MinionRegistry.saveMinionTier(session.minionId, session.tier,
                            new MinionTier.TierData(session.delay, session.slots, session.cost));
                    player.sendMessage(ColorUtil.getPrefix() + ColorUtil.color("&aSuccessfully saved Tier &e" + MinionTier.toRoman(session.tier) +
                            " &afor &e" + session.minionId + " &a(&e" + String.format("%.1f", session.delay) + "s&a, &e" + session.slots + " slots&a, &e" + session.cost + " items&a) to &fminions.yml&a!"));
                } else {
                    MinionTier.saveTier(session.tier, new MinionTier.TierData(session.delay, session.slots, session.cost));
                    player.sendMessage(ColorUtil.getPrefix() + ColorUtil.color("&aSuccessfully saved Tier &e" + MinionTier.toRoman(session.tier) +
                            " &a(&e" + String.format("%.1f", session.delay) + "s&a, &e" + session.slots + " slots&a, &e" + session.cost + " items&a) to &ftiers.yml&a!"));
                }
                SESSIONS.remove(player.getUniqueId());
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                TierEditorGUI.openOverview(player, session.minionId);
                return;
            }

            if (slot == 10) { // Action Delay
                double delta = 0.0;
                if (click == ClickType.LEFT) delta = -0.5;
                else if (click == ClickType.RIGHT) delta = 0.5;
                else if (click == ClickType.SHIFT_LEFT) delta = -0.1;
                else if (click == ClickType.SHIFT_RIGHT) delta = 0.1;

                session.delay = Math.max(0.5, Math.round((session.delay + delta) * 10.0) / 10.0);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
                TierEditorGUI.openEditor(player, session.minionId, session.tier, session.delay, session.slots, session.cost);
                return;
            }

            if (slot == 12) { // Storage Slots
                int delta = 0;
                if (click == ClickType.LEFT) delta = 1;
                else if (click == ClickType.RIGHT) delta = -1;
                else if (click == ClickType.SHIFT_LEFT) delta = 3;
                else if (click == ClickType.SHIFT_RIGHT) delta = -3;

                session.slots = Math.clamp(session.slots + delta, 1, 27);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
                TierEditorGUI.openEditor(player, session.minionId, session.tier, session.delay, session.slots, session.cost);
                return;
            }

            if (slot == 14) { // Upgrade Cost
                int delta = 0;
                if (click == ClickType.LEFT) delta = 64;
                else if (click == ClickType.RIGHT) delta = -64;
                else if (click == ClickType.SHIFT_LEFT) delta = 512;
                else if (click == ClickType.SHIFT_RIGHT) delta = -512;

                session.cost = Math.max(0, session.cost + delta);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
                TierEditorGUI.openEditor(player, session.minionId, session.tier, session.delay, session.slots, session.cost);
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        String title = event.getView().getTitle();
        if (title.startsWith(TierEditorGUI.EDITOR_TITLE_PREFIX) || title.startsWith(TierEditorGUI.MINION_EDITOR_PREFIX)) {
            SESSIONS.remove(event.getPlayer().getUniqueId());
        }
    }
}
