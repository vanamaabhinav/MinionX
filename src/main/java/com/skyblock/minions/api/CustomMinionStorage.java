package com.skyblock.minions.api;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

/**
 * Defines a Minion Storage container type that can be placed adjacent to a minion.
 */
public class CustomMinionStorage {

    private final String id;
    private String displayName;
    private int capacity;
    private Material material;
    private List<String> lore;

    public CustomMinionStorage(String id, String displayName, int capacity, Material material, List<String> lore) {
        this.id = id != null ? id.toLowerCase() : "custom_storage";
        this.displayName = displayName != null ? displayName : "&aMinion Storage";
        this.capacity = Math.clamp(capacity, 1, 27);
        this.material = material != null ? material : Material.CHEST;
        this.lore = lore != null ? new ArrayList<>(lore) : new ArrayList<>();
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = Math.clamp(capacity, 1, 27);
    }

    public Material getMaterial() {
        return material;
    }

    public void setMaterial(Material material) {
        this.material = material != null ? material : Material.CHEST;
    }

    public List<String> getLore() {
        return lore;
    }

    public void setLore(List<String> lore) {
        this.lore = lore != null ? new ArrayList<>(lore) : new ArrayList<>();
    }
}
