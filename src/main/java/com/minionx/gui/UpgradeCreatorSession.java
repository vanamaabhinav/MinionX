package com.minionx.gui;

import com.minionx.api.CustomUpgrade;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

/**
 * Stores state while an administrator configures a custom upgrade or hopper in the Upgrade Creator Studio.
 */
public class UpgradeCreatorSession {

    private String id;
    private String displayName;
    private Material material;
    private CustomUpgrade.UpgradeBehavior behavior;
    private double speedBuff;
    private double chance;
    private double sellRatio;
    private boolean glowing;
    private List<String> lore;

    public UpgradeCreatorSession(String id) {
        this.id = id != null ? id.toLowerCase() : "custom_upgrade";
        this.displayName = "&e" + capitalize(this.id);
        this.material = Material.HOPPER;
        this.behavior = CustomUpgrade.UpgradeBehavior.AUTO_SELLER;
        this.speedBuff = 0.0;
        this.chance = 0.0;
        this.sellRatio = 0.50;
        this.glowing = true;
        this.lore = new ArrayList<>(List.of("&7Custom minion upgrade accessory."));
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id != null ? id.toLowerCase() : "custom_upgrade";
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Material getMaterial() {
        return material;
    }

    public void setMaterial(Material material) {
        if (material != null && !material.isAir()) {
            this.material = material;
        }
    }

    public CustomUpgrade.UpgradeBehavior getBehavior() {
        return behavior;
    }

    public void setBehavior(CustomUpgrade.UpgradeBehavior behavior) {
        this.behavior = behavior != null ? behavior : CustomUpgrade.UpgradeBehavior.SPEED_BUFF;
    }

    public double getSpeedBuff() {
        return speedBuff;
    }

    public void setSpeedBuff(double speedBuff) {
        this.speedBuff = Math.max(0.0, Math.min(2.0, speedBuff));
    }

    public double getChance() {
        return chance;
    }

    public void setChance(double chance) {
        this.chance = Math.max(0.0, Math.min(1.0, chance));
    }

    public double getSellRatio() {
        return sellRatio;
    }

    public void setSellRatio(double sellRatio) {
        this.sellRatio = Math.max(0.05, Math.min(1.0, sellRatio));
    }

    public boolean isGlowing() {
        return glowing;
    }

    public void setGlowing(boolean glowing) {
        this.glowing = glowing;
    }

    public List<String> getLore() {
        return lore;
    }

    public void setLore(List<String> lore) {
        this.lore = lore != null ? new ArrayList<>(lore) : new ArrayList<>();
    }

    public CustomUpgrade buildUpgrade() {
        return new CustomUpgrade(id, displayName, material, behavior, speedBuff, chance, sellRatio, lore, glowing, 0);
    }

    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) return "";
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}
