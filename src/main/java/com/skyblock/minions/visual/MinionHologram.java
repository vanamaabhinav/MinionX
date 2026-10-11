package com.skyblock.minions.visual;

import com.skyblock.minions.SkyBlockMinions;
import com.skyblock.minions.api.MinionTier;
import com.skyblock.minions.core.Minion;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

/**
 * Manages the floating hologram text lines above a minion.
 */
public class MinionHologram {

    public static final NamespacedKey HOLOGRAM_KEY = new NamespacedKey(SkyBlockMinions.getInstance(), "minion_hologram_id");

    /**
     * Spawns or updates the floating name and status tag above the minion.
     */
    public static void updateHologram(Minion minion) {
        if (minion == null || minion.getLocation() == null || minion.getLocation().getWorld() == null) return;

        remove(minion);

        Location loc = minion.getLocation().clone();
        loc.setX(loc.getBlockX() + 0.5);
        loc.setZ(loc.getBlockZ() + 0.5);
        loc.add(0, 1.05, 0);

        ArmorStand hologram = loc.getWorld().spawn(loc, ArmorStand.class, h -> {
            h.setInvisible(true);
            h.setMarker(true);
            h.setSmall(true);
            h.setInvulnerable(true);
            h.setGravity(false);
            h.setCustomNameVisible(true);
            h.setPersistent(true);
            h.setRemoveWhenFarAway(false);

            String title = "§e[Lv." + minion.getTier() + "] §f" + minion.getType().getDisplayName() + " Minion " + minion.getState().getDisplayStatus();
            h.setCustomName(title);

            h.getPersistentDataContainer().set(HOLOGRAM_KEY, PersistentDataType.STRING, minion.getMinionId().toString());
        });

        minion.setHologramId(hologram.getUniqueId());
    }

    /**
     * Removes the floating hologram entity.
     */
    public static void remove(Minion minion) {
        if (minion == null || minion.getLocation() == null || minion.getLocation().getWorld() == null) return;

        if (minion.getHologramId() != null) {
            Entity entity = Bukkit.getEntity(minion.getHologramId());
            if (entity != null && entity.isValid()) {
                entity.remove();
            }
        }

        Location loc = minion.getLocation();
        if (loc.isChunkLoaded()) {
            for (Entity nearby : loc.getWorld().getNearbyEntities(loc, 2.0, 3.0, 2.0)) {
                if (nearby instanceof ArmorStand as) {
                    String tag = as.getPersistentDataContainer().get(HOLOGRAM_KEY, PersistentDataType.STRING);
                    if (tag != null && tag.equals(minion.getMinionId().toString())) {
                        as.remove();
                    }
                }
            }
        }
    }
}
