package com.minionx.util;

import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;

/**
 * Utility for Paper/Purpur 1.20.4 - 1.21+ server optimizations and cross-version compatibility:
 * - ArmorStand#setCanTick(false): disables tick passes on minion visual armor stands to maximize server TPS.
 * - Entity#setRotation(yaw, pitch): direct lightweight entity rotation.
 * - Dynamic Particle dispatch: resolves 1.20.4 vs 1.21+ particle renames (e.g., BLOCK_CRACK -> BLOCK, VILLAGER_HAPPY -> HAPPY_VILLAGER)
 *   without embedding static enum field references that cause NoSuchFieldError on modern Paper 1.21 runtimes.
 */
public final class ServerCompatibilityUtil {

    private ServerCompatibilityUtil() {}

    private static final org.bukkit.Particle BLOCK_PARTICLE = resolveParticle("BLOCK", "BLOCK_CRACK");
    private static final org.bukkit.Particle HAPPY_PARTICLE = resolveParticle("HAPPY_VILLAGER", "VILLAGER_HAPPY");

    private static org.bukkit.Particle resolveParticle(String... candidateNames) {
        for (String name : candidateNames) {
            try {
                return org.bukkit.Particle.valueOf(name);
            } catch (Throwable ignored) {}
        }
        return null;
    }

    /**
     * Spawns block-break / block-crack particles safely across Paper 1.20.4 and 1.21+.
     */
    public static void spawnBlockBreakParticles(Location loc, org.bukkit.block.data.BlockData data, int count, double ox, double oy, double oz) {
        if (loc == null || loc.getWorld() == null || BLOCK_PARTICLE == null || data == null) return;
        try {
            loc.getWorld().spawnParticle(BLOCK_PARTICLE, loc, count, ox, oy, oz, data);
        } catch (Throwable ignored) {}
    }

    /**
     * Spawns happy villager / success growth particles safely across Paper 1.20.4 and 1.21+.
     */
    public static void spawnHappyParticles(Location loc, int count, double ox, double oy, double oz, double speed) {
        if (loc == null || loc.getWorld() == null || HAPPY_PARTICLE == null) return;
        try {
            loc.getWorld().spawnParticle(HAPPY_PARTICLE, loc, count, ox, oy, oz, speed);
        } catch (Throwable ignored) {}
    }

    /**
     * Returns the resolved block crack particle (or null if unsupported).
     */
    public static org.bukkit.Particle getBlockParticle() {
        return BLOCK_PARTICLE;
    }

    /**
     * Returns the resolved happy villager particle (or null if unsupported).
     */
    public static org.bukkit.Particle getHappyParticle() {
        return HAPPY_PARTICLE;
    }

    /**
     * Disables server-side ticking for minion visual armor stands on Paper servers.
     */
    public static void disableEntityTicking(Entity entity) {
        if (entity instanceof ArmorStand stand) {
            try {
                stand.setCanTick(false);
            } catch (Throwable ignored) {}
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
