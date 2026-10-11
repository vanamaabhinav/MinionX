package com.skyblock.minions.api;

import org.bukkit.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a cosmetic Minion Skin in Hypixel SkyBlock.
 * Modifies the minion ArmorStand's head texture and optionally armor color.
 */
public class MinionSkin {

    private final String id;
    private final String displayName;
    private final String skullTexture;
    private final Color armorColor;
    private final List<String> description;

    public MinionSkin(String id, String displayName, String skullTexture) {
        this(id, displayName, skullTexture, null, Collections.emptyList());
    }

    public MinionSkin(String id, String displayName, String skullTexture, Color armorColor, List<String> description) {
        this.id = id != null ? id.toLowerCase() : "unknown";
        this.displayName = displayName != null ? displayName : id;
        this.skullTexture = skullTexture;
        this.armorColor = armorColor;
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

    public boolean hasArmorColor() {
        return armorColor != null;
    }

    public List<String> getDescription() {
        return Collections.unmodifiableList(description);
    }
}
