package com.minionx.visual;

import com.minionx.MinionX;
import com.minionx.api.MinionTier;
import com.minionx.core.Minion;
import com.minionx.api.MinionState;
import com.minionx.quarantine.QuarantineManager;
import com.minionx.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

/**
 * Manages floating display name tags, levels, statuses, and animated speech bubbles for minions.
 * Upgraded to Paper 1.20.4+ TextDisplay entities with in-place text diff caching.
 * Replaces multiple invisible armor stands with a single billboard-centered TextDisplay entity,
 * eliminating client packet thrashing and entity-ID churn.
 */
public class MinionHologram {

    public static final NamespacedKey HOLOGRAM_KEY = new NamespacedKey("minionx", "minion_hologram");
    public static final NamespacedKey DIALOGUE_KEY = new NamespacedKey("minionx", "minion_dialogue");

    public static void updateHologram(Minion minion) {
        if (minion == null || minion.getLocation() == null || minion.getLocation().getWorld() == null) {
            return;
        }

        FileConfiguration config = MinionX.getInstance() != null ? MinionX.getInstance().getConfig() : null;
        String mode = config != null ? config.getString("nametags.mode", "ALWAYS").toUpperCase() : "ALWAYS";
        boolean isMainHoloEnabled = !("DISABLED".equals(mode) || "NONE".equals(mode) || "OFF".equals(mode));
        boolean hasDialogue = minion.hasActiveDialogue();

        // If both nametag and dialogue are disabled/inactive, remove any existing hologram
        if (!isMainHoloEnabled && !hasDialogue) {
            remove(minion);
            return;
        }

        Location loc = minion.getLocation().clone();
        loc.setX(loc.getBlockX() + 0.5);
        loc.setZ(loc.getBlockZ() + 0.5);
        Location holoLoc = loc.clone().add(0, 1.25, 0);

        // Build combined text string
        StringBuilder sb = new StringBuilder();
        if (isMainHoloEnabled) {
            String template = config != null ? config.getString("nametags.format", "&e[Lv.%tier_roman%] &a%minion_name% Minion &7(%status%&7)") : "&e[Lv.%tier_roman%] &a%minion_name% Minion &7(%status%&7)";
            String statusDisplay;
            if (minion.getState() == MinionState.PARTIALLY_OBSTRUCTED) {
                statusDisplay = config != null ? config.getString("messages.partially-obstructed-status", "&ePartially Obstructed") : "&ePartially Obstructed";
            } else if (minion.getState() == MinionState.NO_MOBS_IN_RANGE) {
                statusDisplay = config != null ? config.getString("messages.no-mobs-in-range-status", "&cNo Mobs in Range!") : "&cNo Mobs in Range!";
            } else {
                statusDisplay = minion.getState().getDisplayStatus();
            }

            if (QuarantineManager.isQuarantined()) {
                statusDisplay = "&c&l[QUARANTINED]";
            }

            String formatted = template
                    .replace("%tier%", String.valueOf(minion.getTier()))
                    .replace("%tier_roman%", MinionTier.toRoman(minion.getTier()))
                    .replace("%minion_name%", minion.getDisplayName())
                    .replace("%status%", statusDisplay)
                    .replace("%total%", String.valueOf(minion.getTotalHarvested()));
            sb.append(formatted);
        }

        if (hasDialogue) {
            String dialogueFmt = config != null ? config.getString("dialogues.format", "&e\"%message%\"") : "&e\"%message%\"";
            String dialogueText = dialogueFmt.replace("%message%", minion.getActiveDialogue());
            if (!sb.isEmpty()) {
                sb.append("\n");
            }
            sb.append(dialogueText);
        }

        String combinedText = sb.toString();

        // 1. Text diff cache: skip if rendered text string has not changed
        if (combinedText.equals(minion.getLastHologramText())) {
            return;
        }

        // 2. Fetch existing TextDisplay if alive
        TextDisplay liveDisplay = minion.getCachedHologram();
        if (liveDisplay == null) {
            liveDisplay = getLiveTextDisplay(minion.getHologramId());
            if (liveDisplay != null) {
                minion.setCachedHologram(liveDisplay);
            }
        }
        if (liveDisplay != null) {
            liveDisplay.text(ColorUtil.component(combinedText));
            minion.setLastHologramText(combinedText);
            return;
        }

        // 3. Clean up any legacy armor stands before spawning TextDisplay
        cleanupLegacyArmorStands(minion);

        // 4. Spawn modern TextDisplay entity
        TextDisplay spawned = holoLoc.getWorld().spawn(holoLoc, TextDisplay.class, d -> {
            d.setBillboard(Display.Billboard.CENTER);
            d.setAlignment(TextDisplay.TextAlignment.CENTER);
            d.setSeeThrough(false);
            d.setShadowed(true);
            d.setDefaultBackground(false);
            d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0)); // transparent background
            d.setPersistent(true);
            d.setInvulnerable(true);
            d.text(ColorUtil.component(combinedText));

            d.getPersistentDataContainer().set(HOLOGRAM_KEY, PersistentDataType.STRING, minion.getMinionId().toString());
        });

        minion.setHologramId(spawned.getUniqueId());
        minion.setCachedHologram(spawned);
        minion.setLastHologramText(combinedText);
    }

    public static void remove(Minion minion) {
        if (minion == null) return;
        minion.setCachedHologram(null);

        if (minion.getHologramId() != null) {
            Entity main = Bukkit.getEntity(minion.getHologramId());
            if (main != null && main.isValid()) main.remove();
            minion.setHologramId(null);
        }

        if (minion.getDialogueHologramId() != null) {
            Entity dialogue = Bukkit.getEntity(minion.getDialogueHologramId());
            if (dialogue != null && dialogue.isValid()) dialogue.remove();
            minion.setDialogueHologramId(null);
        }

        minion.setLastHologramText(null);
        cleanupLegacyArmorStands(minion);
    }

    private static void cleanupLegacyArmorStands(Minion minion) {
        if (minion == null || minion.getLocation() == null || minion.getLocation().getWorld() == null) return;
        Location loc = minion.getLocation();
        if (!loc.isChunkLoaded()) return;
        for (Entity entity : loc.getWorld().getNearbyEntities(loc, 2.5, 2.5, 2.5)) {
            if (entity instanceof TextDisplay || entity instanceof ArmorStand) {
                PersistentDataContainer pdc = entity.getPersistentDataContainer();
                String id = pdc.get(HOLOGRAM_KEY, PersistentDataType.STRING);
                if (id == null) {
                    id = pdc.get(DIALOGUE_KEY, PersistentDataType.STRING);
                }
                if (id != null && minion.getMinionId().toString().equals(id)) {
                    entity.remove();
                }
            }
        }
    }

    private static TextDisplay getLiveTextDisplay(UUID uuid) {
        if (uuid == null) return null;
        Entity e = Bukkit.getEntity(uuid);
        return (e instanceof TextDisplay display && display.isValid() && !display.isDead()) ? display : null;
    }
}
