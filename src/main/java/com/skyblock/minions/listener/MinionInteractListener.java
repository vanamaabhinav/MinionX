package com.skyblock.minions.listener;

import com.skyblock.minions.SkyBlockMinions;
import com.skyblock.minions.core.Minion;
import com.skyblock.minions.core.OfflineCalculator;
import com.skyblock.minions.gui.MinionGUI;
import com.skyblock.minions.hook.IslandHookManager;
import com.skyblock.minions.util.ColorUtil;
import com.skyblock.minions.visual.MinionHologram;
import com.skyblock.minions.visual.MinionVisual;
import org.bukkit.Sound;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

/**
 * Handles interactions and damage prevention for minion ArmorStands.
 * Enforces island team and owner security checks.
 */
public class MinionInteractListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
        Entity entity = event.getRightClicked();
        if (!(entity instanceof ArmorStand)) return;

        Minion minion = getMinionFromEntity(entity);
        if (minion == null) return;

        // Cancel vanilla armor stand equipment manipulation
        event.setCancelled(true);

        Player player = event.getPlayer();

        // Check island/owner access permissions
        if (!IslandHookManager.canAccessMinion(player, minion)) {
            String noAccessMsg = SkyBlockMinions.getInstance().getConfig().getString(
                    "messages.no-minion-access-permission", "&cYou do not have permission to access this minion!");
            player.sendMessage(ColorUtil.color(noAccessMsg));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // Process any offline/unloaded accumulation catch-up
        OfflineCalculator.processOfflineCatchup(minion);

        // Open minion menu
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.8f, 1.2f);
        MinionGUI.open(player, minion);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamage(EntityDamageEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof ArmorStand && getMinionFromEntity(entity) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof ArmorStand && getMinionFromEntity(entity) != null) {
            event.setCancelled(true);
        }
    }

    private Minion getMinionFromEntity(Entity entity) {
        if (!(entity instanceof ArmorStand as)) return null;

        PersistentDataContainer pdc = as.getPersistentDataContainer();
        String minionIdStr = pdc.get(MinionVisual.MINION_ENTITY_KEY, PersistentDataType.STRING);
        if (minionIdStr == null) {
            minionIdStr = pdc.get(MinionHologram.HOLOGRAM_KEY, PersistentDataType.STRING);
        }

        if (minionIdStr != null) {
            try {
                UUID id = UUID.fromString(minionIdStr);
                return SkyBlockMinions.getInstance().getMinionManager().getMinionById(id);
            } catch (IllegalArgumentException ignored) {
            }
        }

        return SkyBlockMinions.getInstance().getMinionManager().getMinionByEntityId(entity.getUniqueId());
    }
}