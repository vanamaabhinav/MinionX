package com.skyblock.minions.visual;

import com.skyblock.minions.SkyBlockMinions;
import com.skyblock.minions.core.Minion;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;

/**
 * Handles minion arm swinging, rotation toward target blocks, sounds, and particle effects.
 */
public class MinionAnimation {

    private static final EulerAngle RESTING_ARM_POSE = new EulerAngle(0.0, 0.0, 0.0);
    private static final EulerAngle SWING_ARM_POSE = new EulerAngle(Math.toRadians(-50), 0.0, Math.toRadians(15));

    /**
     * Plays a block placement animation: rotates minion towards block, swings arm, plays placement sound.
     */
    public static void playPlaceAnimation(Minion minion, Block targetBlock) {
        if (minion == null || targetBlock == null) return;
        ArmorStand stand = getStand(minion);
        if (stand == null) return;

        rotateTowards(stand, targetBlock.getLocation());
        animateArmSwing(stand);

        Location soundLoc = targetBlock.getLocation().add(0.5, 0.5, 0.5);
        soundLoc.getWorld().playSound(soundLoc, Sound.BLOCK_STONE_PLACE, 0.8f, 1.0f);
        soundLoc.getWorld().spawnParticle(Particle.BLOCK_CRACK, soundLoc, 8, 0.2, 0.2, 0.2, targetBlock.getBlockData());
    }

    /**
     * Plays a mining animation: rotates minion towards block, swings pickaxe, plays break sound and block break particles.
     */
    public static void playMineAnimation(Minion minion, Block targetBlock) {
        if (minion == null || targetBlock == null) return;
        ArmorStand stand = getStand(minion);
        if (stand == null) return;

        rotateTowards(stand, targetBlock.getLocation());
        animateArmSwing(stand);

        Location soundLoc = targetBlock.getLocation().add(0.5, 0.5, 0.5);
        soundLoc.getWorld().playSound(soundLoc, Sound.BLOCK_STONE_BREAK, 1.0f, 1.0f);
        soundLoc.getWorld().spawnParticle(Particle.BLOCK_CRACK, soundLoc, 15, 0.3, 0.3, 0.3, targetBlock.getBlockData());
    }

    private static void animateArmSwing(ArmorStand stand) {
        stand.setRightArmPose(SWING_ARM_POSE);

        // Reset arm pose back to resting after 7 ticks
        Bukkit.getScheduler().runTaskLater(SkyBlockMinions.getInstance(), () -> {
            if (stand.isValid()) {
                stand.setRightArmPose(RESTING_ARM_POSE);
            }
        }, 7L);
    }

    private static void rotateTowards(ArmorStand stand, Location target) {
        Location standLoc = stand.getLocation();
        Vector direction = target.toVector().subtract(standLoc.toVector()).normalize();

        float yaw = (float) Math.toDegrees(Math.atan2(-direction.getX(), direction.getZ()));
        standLoc.setYaw(yaw);
        stand.teleport(standLoc);
    }

    private static ArmorStand getStand(Minion minion) {
        if (minion.getArmorStandId() == null) return null;
        Entity entity = Bukkit.getEntity(minion.getArmorStandId());
        return (entity instanceof ArmorStand as && as.isValid()) ? as : null;
    }
}
