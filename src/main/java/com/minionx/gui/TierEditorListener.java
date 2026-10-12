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
public class TierEditorListener implements Listener {

    public static class TierEditSession {
        public final int tier;
        public double delay;
        public int slots;
        public int cost;

        public TierEditSession(int tier, double delay, int slots, int cost) {
            this.tier = tier;
            this.delay = delay;
            this.slots = slots;
            this.cost = cost;
        }
    }

    private static final Map<UUID, TierEditSession> SESSIONS = new ConcurrentHashMap<>();

    public static void startEdit(Player player, int tier) {
        MinionTier.TierData data = MinionTier.getTierData(tier);
        double delay = data != null ? data.getActionDelay() : MinionTier.getActionDelay(tier);
        int slots = data != null ? data.getStorageSlots() : MinionTier.getStorageSlots(tier);
        int cost = data != null ? data.getUpgradeCost() : MinionTier.getUpgradeCost(tier);

        SESSIONS.put(player.getUniqueId(), new TierEditSession(tier, delay, slots, cost));
        TierEditorGUI.openEditor(player, tier, delay, slots, cost);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String title = event.getView().getTitle();
        if (title.equals(TierEditorGUI.OVERVIEW_TITLE)) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            if (slot < 0 || slot >= 54) return;

            if (slot == 45) {
                player.closeInventory();
                return;
            }

            if (slot == 49) {
                // Add new tier
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
            if (tier <= MinionTier.getMaxTier()) {
                if (event.isShiftClick() && event.isRightClick() && tier > 12) {
                    MinionTier.removeTier(tier);
                    player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_BREAK, 1.0f, 1.0f);
                    player.sendMessage(ColorUtil.getPrefix() + ColorUtil.color("&cRemoved custom Tier &e" + tier + " &cfrom tiers.yml."));
                    TierEditorGUI.openOverview(player);
                    return;
                }

                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                startEdit(player, tier);
            }
            return;
        }

        if (title.startsWith(TierEditorGUI.EDITOR_TITLE_PREFIX)) {
            event.setCancelled(true);
            TierEditSession session = SESSIONS.get(player.getUniqueId());
            if (session == null) {
                TierEditorGUI.openOverview(player);
                return;
            }

            int slot = event.getRawSlot();
            ClickType click = event.getClick();

            if (slot == 18) { // Back to overview
                SESSIONS.remove(player.getUniqueId());
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                TierEditorGUI.openOverview(player);
                return;
            }

            if (slot == 22) { // Save & Apply
                MinionTier.saveTier(session.tier, new MinionTier.TierData(session.delay, session.slots, session.cost));
                SESSIONS.remove(player.getUniqueId());
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                player.sendMessage(ColorUtil.getPrefix() + ColorUtil.color("&aSuccessfully saved Tier &e" + MinionTier.toRoman(session.tier) +
                        " &a(&e" + String.format("%.1f", session.delay) + "s&a, &e" + session.slots + " slots&a, &e" + session.cost + " items&a) to &ftiers.yml&a!"));
                TierEditorGUI.openOverview(player);
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
                TierEditorGUI.openEditor(player, session.tier, session.delay, session.slots, session.cost);
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
                TierEditorGUI.openEditor(player, session.tier, session.delay, session.slots, session.cost);
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
                TierEditorGUI.openEditor(player, session.tier, session.delay, session.slots, session.cost);
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        String title = event.getView().getTitle();
        if (title.startsWith(TierEditorGUI.EDITOR_TITLE_PREFIX)) {
            // Keep or clean session on close
            SESSIONS.remove(event.getPlayer().getUniqueId());
        }
    }
}
