package com.minionx.gui;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * State session for creating and editing a custom Minion Skin in-game.
 */
public class MinionSkinCreatorSession {

    private String id;
    private String displayName;
    private String skullTexture;
    private Material armorMaterial;
    private Color leatherColor;
    private ItemStack handItem;
    private List<String> description = new ArrayList<>();

    public MinionSkinCreatorSession(String id) {
        this.id = id != null ? id : "custom_skin";
        this.displayName = "&e" + this.id;
        this.armorMaterial = Material.LEATHER_CHESTPLATE;
        this.leatherColor = Color.fromRGB(240, 180, 40); // Gold Yellow
        this.description.add("&7Custom created minion skin.");
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getSkullTexture() { return skullTexture; }
    public void setSkullTexture(String skullTexture) { this.skullTexture = skullTexture; }

    public Material getArmorMaterial() { return armorMaterial; }
    public void setArmorMaterial(Material armorMaterial) { this.armorMaterial = armorMaterial; }

    public Color getLeatherColor() { return leatherColor; }
    public void setLeatherColor(Color leatherColor) { this.leatherColor = leatherColor; }

    public ItemStack getHandItem() { return handItem; }
    public void setHandItem(ItemStack handItem) { this.handItem = handItem; }

    public List<String> getDescription() { return description; }
    public void setDescription(List<String> description) { this.description = description; }
}
