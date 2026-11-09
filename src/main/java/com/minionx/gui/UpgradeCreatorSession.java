package com.minionx.gui;

import com.minionx.api.BonusDropEntry;
import com.minionx.api.CustomUpgrade;
import org.bukkit.Material;

import java.util.*;

/**
 * Stores state while an administrator configures a custom upgrade or hopper in the Upgrade Creator Studio.
 * Supports multi-behavior selection, cursor item icon extraction, and multi-item spreading configurations.
 */
@SuppressWarnings("deprecation")
public class UpgradeCreatorSession {

    private String id;
    private String displayName;
    private Material material;
    private final Set<CustomUpgrade.UpgradeBehavior> behaviors = EnumSet.noneOf(CustomUpgrade.UpgradeBehavior.class);
    private double speedBuff;
    private double chance;
    private double sellRatio;
    private int areaExpansion;
    private boolean glowing;
    private int customModelData;
    private List<String> lore;
    private final List<BonusDropEntry> spreadingItems = new ArrayList<>();

    public UpgradeCreatorSession(String id) {
        this.id = MinionCreatorSession.sanitizeId(id);
        this.displayName = "&e" + capitalize(this.id);
        this.material = Material.HOPPER;
        this.behaviors.add(CustomUpgrade.UpgradeBehavior.AUTO_SELLER);
        this.speedBuff = 0.0;
        this.chance = 0.10;
        this.sellRatio = 0.50;
        this.areaExpansion = 0;
        this.glowing = true;
        this.customModelData = 0;
        this.lore = new ArrayList<>(List.of("&7Custom minion upgrade accessory."));
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = MinionCreatorSession.sanitizeId(id);
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

    public void applyIconFrom(org.bukkit.inventory.ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return;
        this.material = item.getType();
        if (item.hasItemMeta()) {
            org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                if (meta.hasDisplayName()) {
                    this.displayName = meta.getDisplayName();
                }
                if (meta.hasLore() && meta.getLore() != null && !meta.getLore().isEmpty()) {
                    this.lore = new ArrayList<>(meta.getLore());
                }
                if (meta.hasCustomModelData()) {
                    this.customModelData = meta.getCustomModelData();
                }
                if (meta.hasItemFlag(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS) || meta.hasEnchants()) {
                    this.glowing = true;
                }
            }
        }
    }

    public int getCustomModelData() {
        return customModelData;
    }

    public void setCustomModelData(int customModelData) {
        this.customModelData = customModelData;
    }

    public Set<CustomUpgrade.UpgradeBehavior> getBehaviors() {
        return behaviors;
    }

    public boolean hasBehavior(CustomUpgrade.UpgradeBehavior behavior) {
        return behavior != null && behaviors.contains(behavior);
    }

    public void toggleBehavior(CustomUpgrade.UpgradeBehavior behavior) {
        if (behavior == null) return;
        if (behaviors.contains(behavior)) {
            behaviors.remove(behavior);
        } else {
            behaviors.add(behavior);
        }
    }

    public void setBehaviorActive(CustomUpgrade.UpgradeBehavior behavior, boolean active) {
        if (behavior == null) return;
        if (active) {
            behaviors.add(behavior);
        } else {
            behaviors.remove(behavior);
        }
    }

    public CustomUpgrade.UpgradeBehavior getBehavior() {
        return behaviors.isEmpty() ? CustomUpgrade.UpgradeBehavior.SPEED_BUFF : behaviors.iterator().next();
    }

    public void setBehavior(CustomUpgrade.UpgradeBehavior behavior) {
        this.behaviors.clear();
        if (behavior != null) {
            this.behaviors.add(behavior);
        }
    }

    /**
     * Compactor mode: 0 = NONE, 1 = COMPACTOR, 2 = SUPER_COMPACTOR
     */
    public int getCompactorMode() {
        if (behaviors.contains(CustomUpgrade.UpgradeBehavior.SUPER_COMPACTOR)) return 2;
        if (behaviors.contains(CustomUpgrade.UpgradeBehavior.COMPACTOR)) return 1;
        return 0;
    }

    public void cycleCompactorMode() {
        int mode = getCompactorMode();
        behaviors.remove(CustomUpgrade.UpgradeBehavior.COMPACTOR);
        behaviors.remove(CustomUpgrade.UpgradeBehavior.SUPER_COMPACTOR);
        if (mode == 0) {
            behaviors.add(CustomUpgrade.UpgradeBehavior.COMPACTOR);
        } else if (mode == 1) {
            behaviors.add(CustomUpgrade.UpgradeBehavior.SUPER_COMPACTOR);
        }
    }

    public double getSpeedBuff() {
        return speedBuff;
    }

    public void setSpeedBuff(double speedBuff) {
        this.speedBuff = Math.max(0.0, Math.min(2.0, speedBuff));
        setBehaviorActive(CustomUpgrade.UpgradeBehavior.SPEED_BUFF, this.speedBuff > 0);
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
        this.sellRatio = Math.max(0.0, Math.min(1.0, sellRatio));
        setBehaviorActive(CustomUpgrade.UpgradeBehavior.AUTO_SELLER, this.sellRatio > 0);
    }

    public int getAreaExpansion() {
        return areaExpansion;
    }

    public void setAreaExpansion(int areaExpansion) {
        this.areaExpansion = Math.max(0, Math.min(4, areaExpansion));
        setBehaviorActive(CustomUpgrade.UpgradeBehavior.EXPANDER, this.areaExpansion > 0);
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

    public List<BonusDropEntry> getSpreadingItems() {
        return spreadingItems;
    }

    public void addSpreadingItem(BonusDropEntry entry) {
        if (entry != null && spreadingItems.size() < 5) {
            spreadingItems.add(entry);
            behaviors.add(CustomUpgrade.UpgradeBehavior.ITEM_SPREADING);
        }
    }

    public void removeSpreadingItem(int index) {
        if (index >= 0 && index < spreadingItems.size()) {
            spreadingItems.remove(index);
            if (spreadingItems.isEmpty()) {
                behaviors.remove(CustomUpgrade.UpgradeBehavior.ITEM_SPREADING);
            }
        }
    }

    public void setSpreadingItem(int index, BonusDropEntry entry) {
        if (index >= 0 && index < spreadingItems.size() && entry != null) {
            spreadingItems.set(index, entry);
        }
    }

    public CustomUpgrade buildUpgrade() {
        Set<CustomUpgrade.UpgradeBehavior> activeBehaviors = EnumSet.copyOf(this.behaviors);
        if (!spreadingItems.isEmpty()) {
            activeBehaviors.add(CustomUpgrade.UpgradeBehavior.ITEM_SPREADING);
        }
        if (speedBuff > 0) {
            activeBehaviors.add(CustomUpgrade.UpgradeBehavior.SPEED_BUFF);
        }
        if (areaExpansion > 0) {
            activeBehaviors.add(CustomUpgrade.UpgradeBehavior.EXPANDER);
        }
        if (sellRatio > 0) {
            activeBehaviors.add(CustomUpgrade.UpgradeBehavior.AUTO_SELLER);
        }
        if (activeBehaviors.isEmpty()) {
            activeBehaviors.add(CustomUpgrade.UpgradeBehavior.SPEED_BUFF);
        }

        return new CustomUpgrade(id, displayName, material, activeBehaviors, speedBuff, chance, sellRatio, areaExpansion, spreadingItems, lore, glowing, customModelData);
    }

    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) return "";
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}
