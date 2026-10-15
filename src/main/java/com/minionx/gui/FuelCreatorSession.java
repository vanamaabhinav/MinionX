package com.minionx.gui;

import com.minionx.api.CustomFuel;
import com.minionx.util.ColorUtil;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * State session for creating and customizing a Minion Fuel in-game.
 */
public class FuelCreatorSession {

    private String id;
    private String displayName;
    private Material material;
    private double speedBuff; // e.g. 0.20 = +20%
    private boolean permanent;
    private int durationMinutes; // e.g. 30, 60, 120, 720, 1440, etc.
    private boolean glow;
    private int customModelData;

    public static final int[] DURATION_PRESETS = {
            30,     // 30m
            60,     // 1h
            120,    // 2h
            360,    // 6h
            720,    // 12h
            1440,   // 24h / 1d
            4320,   // 3d
            10080   // 7d
    };

    public FuelCreatorSession(String id) {
        this.id = id != null ? id.toLowerCase().replace(' ', '_') : "custom_fuel";
        this.displayName = "&e" + formatName(this.id);
        this.material = Material.BLAZE_POWDER;
        this.speedBuff = 0.20;
        this.permanent = false;
        this.durationMinutes = 60; // 1 hour default
        this.glow = true;
        this.customModelData = 0;
    }

    public FuelCreatorSession(CustomFuel existing) {
        if (existing == null) {
            this.id = "custom_fuel";
            this.displayName = "&eCustom Fuel";
            this.material = Material.BLAZE_POWDER;
            this.speedBuff = 0.20;
            this.permanent = false;
            this.durationMinutes = 60;
            this.glow = true;
            this.customModelData = 0;
            return;
        }
        this.id = existing.getId();
        this.displayName = existing.getDisplayName();
        this.material = existing.getMaterial() != null ? existing.getMaterial() : Material.BLAZE_POWDER;
        this.speedBuff = existing.getSpeedBuff();
        this.permanent = existing.isPermanent();
        if (existing.isPermanent()) {
            this.durationMinutes = 60;
        } else {
            this.durationMinutes = (int) Math.max(1, existing.getDurationMillis() / (60L * 1000L));
        }
        this.glow = existing.isGlow();
        this.customModelData = existing.getCustomModelData();
    }

    public void applyIconFrom(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return;
        this.material = item.getType();
        if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                if (meta.hasCustomModelData()) {
                    this.customModelData = meta.getCustomModelData();
                }
                if (meta.hasEnchants()) {
                    this.glow = true;
                }
            }
        }
    }

    private static String formatName(String id) {
        String[] parts = id.split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (!p.isEmpty()) {
                sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(" ");
            }
        }
        return sb.toString().trim();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id != null ? id.toLowerCase().replace(' ', '_') : "custom_fuel"; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public Material getMaterial() { return material; }
    public void setMaterial(Material material) { this.material = material != null ? material : Material.BLAZE_POWDER; }

    public double getSpeedBuff() { return speedBuff; }
    public void setSpeedBuff(double speedBuff) {
        this.speedBuff = Math.max(0.01, Math.round(speedBuff * 100.0) / 100.0);
    }

    public boolean isPermanent() { return permanent; }
    public void setPermanent(boolean permanent) { this.permanent = permanent; }

    public int getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(int durationMinutes) { this.durationMinutes = Math.max(1, durationMinutes); }

    public void cycleDuration() {
        int curIdx = 0;
        for (int i = 0; i < DURATION_PRESETS.length; i++) {
            if (DURATION_PRESETS[i] == this.durationMinutes) {
                curIdx = i;
                break;
            }
        }
        this.durationMinutes = DURATION_PRESETS[(curIdx + 1) % DURATION_PRESETS.length];
    }

    public String getDurationString() {
        if (permanent) return "permanent";
        if (durationMinutes < 60) return durationMinutes + "m";
        if (durationMinutes % 1440 == 0) return (durationMinutes / 1440) + "d";
        if (durationMinutes % 60 == 0) return (durationMinutes / 60) + "h";
        return durationMinutes + "m";
    }

    public String getFormattedDuration() {
        if (permanent) return "&bPermanent";
        if (durationMinutes < 60) return "&e" + durationMinutes + " minutes";
        if (durationMinutes == 60) return "&e1 hour";
        if (durationMinutes % 1440 == 0) {
            int days = durationMinutes / 1440;
            return "&e" + days + (days == 1 ? " day" : " days");
        }
        if (durationMinutes % 60 == 0) {
            int hours = durationMinutes / 60;
            return "&e" + hours + (hours == 1 ? " hour" : " hours");
        }
        return "&e" + durationMinutes + " minutes";
    }

    public long getDurationMillis() {
        if (permanent) return -1L;
        return durationMinutes * 60L * 1000L;
    }

    public boolean isGlow() { return glow; }
    public void setGlow(boolean glow) { this.glow = glow; }

    public int getCustomModelData() { return customModelData; }
    public void setCustomModelData(int customModelData) { this.customModelData = Math.max(0, customModelData); }

    public List<String> generateLore() {
        List<String> lore = new ArrayList<>();
        int percent = (int) Math.round(speedBuff * 100);
        lore.add(ColorUtil.color("&7Increases minion speed by &a+" + percent + "%&7."));
        lore.add(ColorUtil.color("&7Duration: " + getFormattedDuration()));
        return lore;
    }

    public CustomFuel toCustomFuel() {
        return new CustomFuel(
                id,
                ColorUtil.color(displayName),
                material,
                speedBuff,
                getDurationMillis(),
                generateLore(),
                glow,
                customModelData
        );
    }
}
