package com.skyblock.minions.gui;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

/**
 * State session for creating and customizing a Minion Storage in-game.
 */
public class MinionStorageCreatorSession {

    private String id;
    private String displayName;
    private int capacity;
    private Material material;
    private List<String> lore = new ArrayList<>();

    public MinionStorageCreatorSession(String id) {
        this.id = id != null ? id : "custom_storage";
        this.displayName = "&6" + this.id + " Storage";
        this.capacity = 9;
        this.material = Material.CHEST;
        this.lore.add("&7Expands minion storage.");
        this.lore.add("&ePlace adjacent to minion to link!");
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public int getCapacity() { return capacity; }
    public void setCapacity(int capacity) { this.capacity = Math.clamp(capacity, 1, 27); }

    public Material getMaterial() { return material; }
    public void setMaterial(Material material) { this.material = material; }

    public List<String> getLore() { return lore; }
    public void setLore(List<String> lore) { this.lore = lore; }
}
