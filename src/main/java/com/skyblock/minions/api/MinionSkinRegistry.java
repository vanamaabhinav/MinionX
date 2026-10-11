package com.skyblock.minions.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.skyblock.minions.SkyBlockMinions;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Registry storing all available cosmetic minion skins.
 * Loads authentic skins from minion_skins.json bundled resource.
 */
public class MinionSkinRegistry {

    private static final Map<String, MinionSkin> SKINS = new ConcurrentHashMap<>();

    static {
        loadBuiltinSkins();
    }

    public static void loadBuiltinSkins() {
        SKINS.clear();

        InputStream in = MinionSkinRegistry.class.getClassLoader().getResourceAsStream("minion_skins.json");
        if (in == null) {
            if (SkyBlockMinions.getInstance() != null) {
                SkyBlockMinions.getInstance().getLogger().warning("minion_skins.json resource not found in classpath!");
            }
            return;
        }

        try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                String id = entry.getKey().toLowerCase();
                JsonObject obj = entry.getValue().getAsJsonObject();

                String displayName = obj.has("displayName") ? obj.get("displayName").getAsString() : id;
                String texture = obj.has("skullTexture") ? obj.get("skullTexture").getAsString() : null;

                List<String> lore = new ArrayList<>();
                if (obj.has("description") && obj.get("description").isJsonArray()) {
                    JsonArray arr = obj.getAsJsonArray("description");
                    for (JsonElement el : arr) {
                        lore.add(el.getAsString());
                    }
                }

                register(new MinionSkin(id, displayName, texture, null, lore));
            }

            if (SkyBlockMinions.getInstance() != null) {
                SkyBlockMinions.getInstance().getLogger().info("Loaded " + SKINS.size() + " authentic Minion Skins.");
            }
        } catch (Exception e) {
            if (SkyBlockMinions.getInstance() != null) {
                SkyBlockMinions.getInstance().getLogger().log(Level.SEVERE, "Failed to load minion_skins.json", e);
            }
        }
    }

    public static void register(MinionSkin skin) {
        if (skin != null && skin.getId() != null) {
            SKINS.put(skin.getId().toLowerCase(), skin);
        }
    }

    public static MinionSkin get(String id) {
        if (id == null) return null;
        return SKINS.get(id.toLowerCase());
    }

    public static Collection<MinionSkin> getAll() {
        return Collections.unmodifiableCollection(SKINS.values());
    }

    public static Set<String> getKeys() {
        return Collections.unmodifiableSet(SKINS.keySet());
    }
}
