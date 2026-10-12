package com.minionx.util;

import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;

/**
 * Utility for Paper/Purpur 1.20.4 - 1.21+ server optimizations:
 * - ArmorStand#setCanTick(false): disables tick passes on minion visual armor stands to maximize server TPS.
 * - Entity#setRotation(yaw, pitch): direct lightweight entity rotation.
 */
public final class ServerCompatibilityUtil {

    private ServerCompatibilityUtil() {}

    /**
     * Disables server-side ticking for minion visual armor stands on Paper servers.
     */
    public static void disableEntityTicking(Entity entity) {
        if (entity instanceof ArmorStand stand) {
            stand.setCanTick(false);
        }
    }

    /**
     * Rotates an entity to the given yaw and pitch.
     */
    public static void setEntityRotation(Entity entity, float yaw, float pitch, Location fallbackLoc) {
        if (entity == null) return;
        try {
            entity.setRotation(yaw, pitch);
        } catch (Throwable fallback) {
            Location targetLoc = fallbackLoc != null ? fallbackLoc.clone() : entity.getLocation();
            targetLoc.setYaw(yaw);
            targetLoc.setPitch(pitch);
            entity.teleport(targetLoc);
        }
    }
}
