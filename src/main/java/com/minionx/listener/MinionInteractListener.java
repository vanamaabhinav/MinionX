package com.minionx.listener;

import com.minionx.MinionX;
import com.minionx.core.Minion;
import com.minionx.core.OfflineCalculator;
import com.minionx.gui.MinionGUI;
import com.minionx.hook.IslandHookManager;
import com.minionx.util.ColorUtil;
import com.minionx.visual.MinionHologram;
import com.minionx.visual.MinionVisual;
import org.bukkit.Sound;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
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
            String noAccessMsg = MinionX.getInstance().getConfig().getString(
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

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof ArmorStand as) {
            String minionIdStr = as.getPersistentDataContainer().get(MinionVisual.MINION_ENTITY_KEY, PersistentDataType.STRING);
            if (minionIdStr != null) {
                // Zero drops to eliminate any duplicate gear/skull exploits
                event.getDrops().clear();
                event.setDroppedExp(0);

                try {
                    UUID id = UUID.fromString(minionIdStr);
                    Minion m = MinionX.getInstance().getMinionManager().getMinionById(id);
                    if (m != null) {
                        // Re-spawn visual on next tick if minion is still active in the world
                        org.bukkit.Bukkit.getScheduler().runTask(MinionX.getInstance(), () -> {
                            if (MinionX.getInstance().getMinionManager().getMinionById(id) != null) {
                                MinionVisual.spawnOrUpdate(m);
                            }
                        });
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }
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
                return MinionX.getInstance().getMinionManager().getMinionById(id);
            } catch (IllegalArgumentException ignored) {
            }
        }

        return MinionX.getInstance().getMinionManager().getMinionByEntityId(entity.getUniqueId());
    }
}