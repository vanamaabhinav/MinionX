package com.minionx.api;

import org.bukkit.Color;
import org.bukkit.Material;

/**
 * Supported minion types in the initial implementation.
 */
public enum MinionType {
    COBBLESTONE(
            "Cobblestone",
            Material.COBBLESTONE,
            Material.COBBLESTONE,
            Material.COBBLESTONE,
            Material.STONE_PICKAXE,
            Color.fromRGB(120, 120, 120),
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMTRhMjQzZTU1OWU5OWExMGJmMWFlNGU2YmFmYmY0MTA1ODhhMTVhM2Q2YzVjOGVmMjljMWE4YmRiNWYzMWFjYyJ9fX0="
    ),
    IRON(
            "Iron",
            Material.IRON_ORE,
            Material.IRON_ORE,
            Material.RAW_IRON,
            Material.IRON_PICKAXE,
            Color.fromRGB(220, 220, 220),
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTZlOWFlMTVmNTRkOWI5ZTEyZGIzOWZhZTZlODlhMWY4MmU1YzlmOTBhOTRkZjljYzg0MjM1YWM3NzFmIn19fQ=="
    ),
    DIAMOND(
            "Diamond",
            Material.DIAMOND_ORE,
            Material.DIAMOND_ORE,
            Material.DIAMOND,
            Material.DIAMOND_PICKAXE,
            Color.fromRGB(80, 220, 230),
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYTRmODcxMThjZGEyMmQ0OWU4OTU1ZTZiYWY4Nzg2MzIxMzAxYWI5N2M3YjU3Mzg4YTFhMjAxYjU2YTUxZiJ9fX0="
    );

    private final String displayName;
    private final Material placedMaterial;
    private final Material targetMaterial;
    private final Material dropMaterial;
    private final Material toolMaterial;
    private final Color armorColor;
    private final String skullTexture;

    MinionType(String displayName, Material placedMaterial, Material targetMaterial,
               Material dropMaterial, Material toolMaterial, Color armorColor, String skullTexture) {
        this.displayName = displayName;
        this.placedMaterial = placedMaterial;
        this.targetMaterial = targetMaterial;
        this.dropMaterial = dropMaterial;
        this.toolMaterial = toolMaterial;
        this.armorColor = armorColor;
        this.skullTexture = skullTexture;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Material getPlacedMaterial() {
        return placedMaterial;
    }

    public Material getTargetMaterial() {
        return targetMaterial;
    }

    public Material getDropMaterial() {
        return dropMaterial;
    }

    public Material getToolMaterial() {
        return toolMaterial;
    }

    public Color getArmorColor() {
        return armorColor;
    }

    public String getSkullTexture() {
        return skullTexture;
    }

    public static MinionType fromString(String name) {
        if (name == null) return null;
        for (MinionType type : values()) {
            if (type.name().equalsIgnoreCase(name) || type.getDisplayName().equalsIgnoreCase(name)) {
                return type;
            }
        }
        return null;
    }
}
