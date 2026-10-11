package com.skyblock.minions.visual;

import com.skyblock.minions.SkyBlockMinions;
import com.skyblock.minions.api.MinionState;
import com.skyblock.minions.api.MinionTier;
import com.skyblock.minions.core.Minion;
import com.skyblock.minions.quarantine.QuarantineManager;
import com.skyblock.minions.util.ColorUtil;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;

/**
 * Manages floating hologram lines above a minion.
 * Supports configurable name formatting, status updates, quarantine alerts,
 * and dynamic minion dialogue lines (placed below the name line, or appearing solo when holograms are disabled).
 */
public class MinionHologram {

    public static final NamespacedKey HOLOGRAM_KEY = new NamespacedKey("skyblockminions", "minion_hologram");
    public static final NamespacedKey DIALOGUE_KEY = new NamespacedKey("skyblockminions", "minion_dialogue");

    public static void updateHologram(Minion minion) {
        if (minion.getLocation() == null || minion.getLocation().getWorld() == null) {
            return;
        }

        remove(minion);

        FileConfiguration config = SkyBlockMinions.getInstance() != null ? SkyBlockMinions.getInstance().getConfig() : null;
        String mode = config != null ? config.getString("nametags.mode", "ALWAYS").toUpperCase() : "ALWAYS";
        boolean isMainHoloEnabled = !("DISABLED".equals(mode) || "NONE".equals(mode) || "OFF".equals(mode));
        boolean hasDialogue = minion.hasActiveDialogue();

        Location loc = minion.getLocation().clone();
        loc.setX(loc.getBlockX() + 0.5);
        loc.setZ(loc.getBlockZ() + 0.5);

        // 1. If main hologram is enabled: spawn main name line
        if (isMainHoloEnabled) {
            Location nameLoc = loc.clone().add(0, hasDialogue ? 1.30 : 1.05, 0);

            String template = config != null ? config.getString("nametags.format", "&e[Lv.%tier_roman%] &a%minion_name% Minion &7(%status%&7)") : "&e[Lv.%tier_roman%] &a%minion_name% Minion &7(%status%&7)";
            String statusDisplay = minion.getState() == MinionState.PARTIALLY_OBSTRUCTED
                    ? (config != null ? config.getString("messages.partially-obstructed-status", "&ePartially Obstructed") : "&ePartially Obstructed")
                    : minion.getState().getDisplayStatus();

            if (QuarantineManager.isQuarantined()) {
                statusDisplay = "&c&l[QUARANTINED]";
            }

            String formatted = template
                    .replace("%tier%", String.valueOf(minion.getTier()))
                    .replace("%tier_roman%", MinionTier.toRoman(minion.getTier()))
                    .replace("%minion_name%", minion.getDisplayName())
                    .replace("%status%", statusDisplay)
                    .replace("%total%", String.valueOf(minion.getTotalHarvested()));

            boolean visibleAlways = "ALWAYS".equals(mode);

            nameLoc.getWorld().spawn(nameLoc, ArmorStand.class, h -> {
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

            // If minion has a dialogue, spawn it directly below the name line
            if (hasDialogue) {
                Location dialogueLoc = loc.clone().add(0, 0.95, 0);
                String dialogueFmt = config != null ? config.getString("dialogues.format", "&e\"%message%\"") : "&e\"%message%\"";
                String text = dialogueFmt.replace("%message%", minion.getActiveDialogue());

                dialogueLoc.getWorld().spawn(dialogueLoc, ArmorStand.class, d -> {
                    d.setInvisible(true);
                    d.setMarker(true);
                    d.setSmall(true);
                    d.setInvulnerable(true);
                    d.setGravity(false);
                    d.setCustomNameVisible(true);
                    d.setPersistent(true);
                    d.setRemoveWhenFarAway(false);
                    d.setCustomName(ColorUtil.color(text));

                    d.getPersistentDataContainer().set(HOLOGRAM_KEY, PersistentDataType.STRING, minion.getMinionId().toString());
                    d.getPersistentDataContainer().set(DIALOGUE_KEY, PersistentDataType.STRING, minion.getMinionId().toString());
                });
            }
        } else if (hasDialogue) {
            // Main hologram is disabled, but minion has dialogue:
            // Spawn ONLY the dialogue line as requested!
            Location dialogueLoc = loc.clone().add(0, 1.05, 0);
            String dialogueFmt = config != null ? config.getString("dialogues.format", "&e\"%message%\"") : "&e\"%message%\"";
            String text = dialogueFmt.replace("%message%", minion.getActiveDialogue());

            dialogueLoc.getWorld().spawn(dialogueLoc, ArmorStand.class, d -> {
                d.setInvisible(true);
                d.setMarker(true);
                d.setSmall(true);
                d.setInvulnerable(true);
                d.setGravity(false);
                d.setCustomNameVisible(true);
                d.setPersistent(true);
                d.setRemoveWhenFarAway(false);
                d.setCustomName(ColorUtil.color(text));

                d.getPersistentDataContainer().set(HOLOGRAM_KEY, PersistentDataType.STRING, minion.getMinionId().toString());
                d.getPersistentDataContainer().set(DIALOGUE_KEY, PersistentDataType.STRING, minion.getMinionId().toString());
            });
        }
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
