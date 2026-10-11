package com.skyblock.minions.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.skyblock.minions.SkyBlockMinions;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Registry storing all available cosmetic minion skins.
 * Loads authentic skins from minion_skins.json and custom skins from skins.yml.
 */
public class MinionSkinRegistry {

    private static final Map<String, MinionSkin> SKINS = new ConcurrentHashMap<>();

    static {
        loadBuiltinSkins();
        loadCustomSkins();
    }

    public static void load() {
        loadBuiltinSkins();
        loadCustomSkins();
    }

    public static void loadBuiltinSkins() {
        SKINS.clear();

        InputStream in = MinionSkinRegistry.class.getClassLoader().getResourceAsStream("minion_skins.json");
        if (in != null) {
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

        loadCustomSkins();
    }

    public static void loadCustomSkins() {
        if (SkyBlockMinions.getInstance() == null) return;
        File file = new File(SkyBlockMinions.getInstance().getDataFolder(), "skins.yml");
        if (!file.exists()) return;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("skins");
        if (section == null) return;

        for (String id : section.getKeys(false)) {
            ConfigurationSection s = section.getConfigurationSection(id);
            if (s == null) continue;

            String name = s.getString("display-name", "&a" + id);
            String texture = s.getString("skull-texture");
            Material armorMat = Material.matchMaterial(s.getString("armor-material", ""));
            Color armorColor = null;
            if (s.contains("armor-color")) {
                int rgb = s.getInt("armor-color");
                armorColor = Color.fromRGB(rgb);
            }
            ItemStack hand = s.getItemStack("hand-item");
            List<String> lore = s.getStringList("description");

            register(new MinionSkin(id, name, texture, armorColor, armorMat, hand, lore));
        }
    }

    public static void saveCustomSkin(MinionSkin skin) {
        if (skin == null) return;
        register(skin);

        if (SkyBlockMinions.getInstance() == null) return;
        File file = new File(SkyBlockMinions.getInstance().getDataFolder(), "skins.yml");
        if (!file.getParentFile().exists()) file.getParentFile().mkdirs();

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        String path = "skins." + skin.getId();
        config.set(path + ".display-name", skin.getDisplayName());
        config.set(path + ".skull-texture", skin.getSkullTexture());
        if (skin.hasArmorMaterial()) config.set(path + ".armor-material", skin.getArmorMaterial().name());
        if (skin.hasArmorColor()) config.set(path + ".armor-color", skin.getArmorColor().asRGB());
        if (skin.hasHandItem()) config.set(path + ".hand-item", skin.getHandItem());
        config.set(path + ".description", skin.getDescription());

        try {
            config.save(file);
        } catch (IOException e) {
            SkyBlockMinions.getInstance().getLogger().log(Level.SEVERE, "Failed to save skin to skins.yml", e);
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
