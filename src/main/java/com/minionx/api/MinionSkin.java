package com.minionx.api;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a cosmetic Minion Skin in MinionX.
 * Modifies the minion ArmorStand's head texture, armor material & color, and held tool/weapon.
 */
public class MinionSkin {

    private final String id;
    private final String displayName;
    private final String skullTexture;
    private Color armorColor;
    private Material armorMaterial;
    private ItemStack handItem;
    private final List<String> description;

    public MinionSkin(String id, String displayName, String skullTexture) {
        this(id, displayName, skullTexture, null, null, null, Collections.emptyList());
    }

    public MinionSkin(String id, String displayName, String skullTexture, Color armorColor, List<String> description) {
        this(id, displayName, skullTexture, armorColor, null, null, description);
    }

    public MinionSkin(String id, String displayName, String skullTexture, Color armorColor, Material armorMaterial, ItemStack handItem, List<String> description) {
        this.id = id != null ? id.toLowerCase() : "unknown";
        this.displayName = displayName != null ? displayName : id;
        this.skullTexture = skullTexture;
        this.armorColor = armorColor;
        this.armorMaterial = armorMaterial;
        this.handItem = handItem != null ? handItem.clone() : null;
        this.description = description != null ? new ArrayList<>(description) : new ArrayList<>();
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getSkullTexture() {
        return skullTexture;
    }

    public Color getArmorColor() {
        return armorColor;
    }

    public void setArmorColor(Color armorColor) {
        this.armorColor = armorColor;
    }

    public boolean hasArmorColor() {
        return armorColor != null;
    }

    public Material getArmorMaterial() {
        return armorMaterial;
    }

    public void setArmorMaterial(Material armorMaterial) {
        this.armorMaterial = armorMaterial;
    }

    public boolean hasArmorMaterial() {
        return armorMaterial != null;
    }

    public ItemStack getHandItem() {
        return handItem != null ? handItem.clone() : null;
    }

    public void setHandItem(ItemStack handItem) {
        this.handItem = handItem != null ? handItem.clone() : null;
    }

    public boolean hasHandItem() {
        return handItem != null && handItem.getType() != Material.AIR;
    }

    public List<String> getDescription() {
        return Collections.unmodifiableList(description);
    }
}

