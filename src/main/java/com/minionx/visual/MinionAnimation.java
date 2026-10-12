package com.minionx.visual;

import com.minionx.MinionX;
import com.minionx.core.Minion;
import com.minionx.util.ServerCompatibilityUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;

/**
 * Handles minion arm swinging, rotation toward target blocks, sounds, and particle effects.
 * Optimized with modern setRotation / fallback teleportation and distance culling for sounds/particles.
 */
public class MinionAnimation {

    private static final EulerAngle RESTING_ARM_POSE = new EulerAngle(0.0, 0.0, 0.0);
    private static final EulerAngle SWING_ARM_POSE = new EulerAngle(Math.toRadians(-50), 0.0, Math.toRadians(15));
    private static final double CULLING_RADIUS_SQUARED = 24.0 * 24.0; // 24 block radius

    /**
     * Plays a block placement animation: rotates minion towards block, swings arm, plays placement sound and particles.
     */
    public static void playPlaceAnimation(Minion minion, Block targetBlock) {
        if (minion == null || targetBlock == null) return;
        ArmorStand stand = getStand(minion);
        if (stand == null) return;

        rotateTowards(minion, stand, targetBlock.getLocation());
        animateArmSwing(stand);

        Location soundLoc = targetBlock.getLocation().add(0.5, 0.5, 0.5);
        if (hasPlayerNearby(soundLoc)) {
            soundLoc.getWorld().playSound(soundLoc, Sound.BLOCK_STONE_PLACE, 0.8f, 1.0f);
            soundLoc.getWorld().spawnParticle(Particle.BLOCK_CRACK, soundLoc, 8, 0.2, 0.2, 0.2, targetBlock.getBlockData());
        }
    }

    /**
     * Plays a mining animation: rotates minion towards block, swings pickaxe, plays break sound and block break particles.
     */
    public static void playMineAnimation(Minion minion, Block targetBlock) {
        if (minion == null || targetBlock == null) return;
        ArmorStand stand = getStand(minion);
        if (stand == null) return;

        rotateTowards(minion, stand, targetBlock.getLocation());
        animateArmSwing(stand);

        Location soundLoc = targetBlock.getLocation().add(0.5, 0.5, 0.5);
        if (hasPlayerNearby(soundLoc)) {
            soundLoc.getWorld().playSound(soundLoc, Sound.BLOCK_STONE_BREAK, 1.0f, 1.0f);
            soundLoc.getWorld().spawnParticle(Particle.BLOCK_CRACK, soundLoc, 15, 0.3, 0.3, 0.3, targetBlock.getBlockData());
        }
    }

    private static void animateArmSwing(ArmorStand stand) {
        stand.setRightArmPose(SWING_ARM_POSE);

        // Reset arm pose back to resting after 7 ticks
        Bukkit.getScheduler().runTaskLater(MinionX.getInstance(), () -> {
            if (stand.isValid()) {
                stand.setRightArmPose(RESTING_ARM_POSE);
            }
        }, 7L);
    }

    private static void rotateTowards(Minion minion, ArmorStand stand, Location target) {
        Location standLoc = stand.getLocation();
        Vector direction = target.toVector().subtract(standLoc.toVector()).normalize();

        float yaw = (float) Math.toDegrees(Math.atan2(-direction.getX(), direction.getZ()));
        if (!Float.isNaN(minion.getLastRenderedYaw()) && Math.abs(minion.getLastRenderedYaw() - yaw) < 0.5f) {
            return;
        }
        minion.setLastRenderedYaw(yaw);
        ServerCompatibilityUtil.setEntityRotation(stand, yaw, 0f, standLoc);
    }

    /**
     * Distance culling check: prevents generating particle and sound packets if no player is within 24 blocks.
     */
    private static boolean hasPlayerNearby(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        for (Player p : loc.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(loc) <= CULLING_RADIUS_SQUARED) {
                return true;
            }
        }
        return false;
    }

    private static ArmorStand getStand(Minion minion) {
        if (minion.getArmorStandId() == null) return null;
        Entity entity = Bukkit.getEntity(minion.getArmorStandId());
        return (entity instanceof ArmorStand as && as.isValid()) ? as : null;
    }
}
