package com.skyblock.minions.visual;

import com.skyblock.minions.SkyBlockMinions;
import com.skyblock.minions.api.MinionTier;
import com.skyblock.minions.core.Minion;
import com.skyblock.minions.util.ColorUtil;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;

/**
 * Manages the floating hologram text lines above a minion.
 * Fully configurable through config.yml (modes: ALWAYS, TARGETED, DISABLED).
 */
public class MinionHologram {

    public static final NamespacedKey HOLOGRAM_KEY = new NamespacedKey("skyblockminions", "minion_hologram");

    public static void updateHologram(Minion minion) {
        if (minion.getLocation() == null || minion.getLocation().getWorld() == null) {
            return;
        }

        remove(minion);

        FileConfiguration config = SkyBlockMinions.getInstance().getConfig();
        String mode = config.getString("nametags.mode", "ALWAYS").toUpperCase();

        // If nametags are disabled to prevent lag, skip entity spawn completely
        if ("DISABLED".equals(mode) || "NONE".equals(mode) || "OFF".equals(mode)) {
            return;
        }

        Location loc = minion.getLocation().clone();
        loc.setX(loc.getBlockX() + 0.5);
        loc.setZ(loc.getBlockZ() + 0.5);
        loc.add(0, 1.05, 0);

        String template = config.getString("nametags.format", "&e[Lv.%tier_roman%] &a%minion_name% Minion &7(%status%&7)");
        String formatted = template
                .replace("%tier%", String.valueOf(minion.getTier()))
                .replace("%tier_roman%", MinionTier.toRoman(minion.getTier()))
                .replace("%minion_name%", minion.getType().getDisplayName())
                .replace("%status%", minion.getState().getDisplayStatus())
                .replace("%total%", String.valueOf(minion.getTotalHarvested()));

        boolean visibleAlways = "ALWAYS".equals(mode);

        loc.getWorld().spawn(loc, ArmorStand.class, h -> {
            h.setInvisible(true);
            h.setMarker(true);
            h.setSmall(true);
            h.setInvulnerable(true);
            h.setGravity(false);
            h.setCustomNameVisible(visibleAlways);
            h.setPersistent(true);
            h.setRemoveWhenFarAway(false);
            h.setCustomName(ColorUtil.color(formatted));

            h.getPersistentDataContainer().set(HOLOGRAM_KEY, PersistentDataType.STRING, minion.getMinionId().toString());
        });
    }

    public static void remove(Minion minion) {
        if (minion.getLocation() == null || minion.getLocation().getWorld() == null) {
            return;
        }

        Location loc = minion.getLocation();
        for (Entity entity : loc.getWorld().getNearbyEntities(loc, 3, 3, 3)) {
            if (entity instanceof ArmorStand stand) {
                if (stand.getPersistentDataContainer().has(HOLOGRAM_KEY, PersistentDataType.STRING)) {
                    String id = stand.getPersistentDataContainer().get(HOLOGRAM_KEY, PersistentDataType.STRING);
                    if (minion.getMinionId().toString().equals(id)) {
                        stand.remove();
                    }
                }
            }
        }
    }
}