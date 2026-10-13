package com.minionx.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.minionx.MinionX;
import com.minionx.util.ColorUtil;
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
        load();
    }

    public static void load() {
        loadBuiltinSkins();
        loadCustomSkins();
    }

    public static void load(ConfigurationSection customSkinsSection) {
        loadBuiltinSkins();
        if (customSkinsSection != null) {
            loadCustomSkins(customSkinsSection);
        } else {
            loadCustomSkins();
        }
    }

    /**
     * Loads authentic built-in minion skins from minion_skins.json.
     * Applies thematic armor colors and equipment presets to built-in skins.
     */
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

                    Color armorColor = null;
                    if (obj.has("armorColor")) {
                        armorColor = ColorUtil.parseColor(obj.get("armorColor").getAsString());
                    }
                    if (armorColor == null) {
                        armorColor = getDefaultSkinArmorColor(id);
                    }

                    Material armorMaterial = Material.LEATHER_CHESTPLATE;
                    if (obj.has("armorMaterial")) {
                        Material customMat = parseArmorMaterial(obj.get("armorMaterial").getAsString());
                        if (customMat != null) {
                            armorMaterial = customMat;
                        }
                    }

                    ItemStack handItem = null;
                    if (obj.has("handItem")) {
                        handItem = parseHandItem(obj.get("handItem").getAsString());
                    }

                    List<String> lore = new ArrayList<>();
                    if (obj.has("description") && obj.get("description").isJsonArray()) {
                        JsonArray arr = obj.getAsJsonArray("description");
                        for (JsonElement el : arr) {
                            lore.add(el.getAsString());
                        }
                    }

                    register(new MinionSkin(id, displayName, texture, armorColor, armorMaterial, handItem, lore));
                }

                if (MinionX.getInstance() != null) {
                    MinionX.getInstance().getLogger().info("Loaded " + SKINS.size() + " authentic Minion Skins.");
                }
            } catch (Exception e) {
                if (MinionX.getInstance() != null) {
                    MinionX.getInstance().getLogger().log(Level.SEVERE, "Failed to load minion_skins.json", e);
                }
            }
        }
    }

    /**
     * Loads custom skins configured in plugins/MinionX/skins.yml.
     */
    public static void loadCustomSkins() {
        if (MinionX.getInstance() == null) return;
        File file = new File(MinionX.getInstance().getDataFolder(), "skins.yml");
        if (!file.exists()) return;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("skins");
        if (section == null) {
            section = config;
        }
        loadCustomSkins(section);
    }

    /**
     * Loads custom skins from a ConfigurationSection (from skins.yml).
     */
    public static void loadCustomSkins(ConfigurationSection section) {
        if (section == null) return;

        for (String id : section.getKeys(false)) {
            ConfigurationSection s = section.getConfigurationSection(id);
            if (s == null) continue;

            String name = s.getString("display-name", s.getString("displayName", "&a" + id));
            String texture = s.getString("skull-texture", s.getString("skullTexture", null));

            Color armorColor = ColorUtil.parseColor(s.get("armor-color") != null ? s.get("armor-color") : s.get("armorColor"));
            String armorMatStr = s.getString("armor-material", s.getString("armorMaterial", null));
            Material armorMat = parseArmorMaterial(armorMatStr);

            if (armorMat == null && armorColor != null) {
                armorMat = Material.LEATHER_CHESTPLATE;
            }

            Object handObj = s.get("hand-item") != null ? s.get("hand-item") : s.get("handItem");
            ItemStack hand = parseHandItem(handObj);

            List<String> lore = s.getStringList("description");
            if (lore == null || lore.isEmpty()) {
                lore = s.getStringList("lore");
            }

            register(new MinionSkin(id, name, texture, armorColor, armorMat, hand, lore));
        }

        if (MinionX.getInstance() != null) {
            MinionX.getInstance().getLogger().info("Registered custom skins from skins.yml. Total skins: " + SKINS.size());
        }
    }

    /**
     * Resolves an armor material from config strings like LEATHER, DIAMOND, GOLD, IRON, NETHERITE, CHAINMAIL.
     */
    public static Material parseArmorMaterial(String input) {
        if (input == null || input.trim().isEmpty()) return null;
        input = input.trim().toUpperCase();

        if (input.equalsIgnoreCase("GOLD") || input.equalsIgnoreCase("GOLDEN")) {
            return Material.GOLDEN_CHESTPLATE;
        }
        if (input.equalsIgnoreCase("DIAMOND")) {
            return Material.DIAMOND_CHESTPLATE;
        }
        if (input.equalsIgnoreCase("IRON")) {
            return Material.IRON_CHESTPLATE;
        }
        if (input.equalsIgnoreCase("LEATHER")) {
            return Material.LEATHER_CHESTPLATE;
        }
        if (input.equalsIgnoreCase("CHAINMAIL") || input.equalsIgnoreCase("CHAIN")) {
            return Material.CHAINMAIL_CHESTPLATE;
        }
        if (input.equalsIgnoreCase("NETHERITE")) {
            return Material.NETHERITE_CHESTPLATE;
        }

        Material mat = Material.matchMaterial(input);
        if (mat != null) return mat;

        return Material.matchMaterial(input + "_CHESTPLATE");
    }

    /**
     * Resolves a hand item from a config Object (ItemStack, Material String, or ConfigurationSection).
     */
    public static ItemStack parseHandItem(Object value) {
        if (value == null) return null;
        if (value instanceof ItemStack is) return is.clone();
        if (value instanceof String str) {
            Material m = Material.matchMaterial(str.trim().toUpperCase());
            if (m != null && m != Material.AIR) {
                return new ItemStack(m);
            }
        }
        if (value instanceof ConfigurationSection cs) {
            String matName = cs.getString("material", cs.getString("type", ""));
            Material m = Material.matchMaterial(matName.trim().toUpperCase());
            if (m != null && m != Material.AIR) {
                int amount = cs.getInt("amount", 1);
                return new ItemStack(m, Math.max(1, amount));
            }
        }
        return null;
    }

    /**
     * Provides authentic thematic armor colors for built-in cosmetic minion skins.
     */
    public static Color getDefaultSkinArmorColor(String id) {
        if (id == null) return null;
        return switch (id.toLowerCase()) {
            case "snowman" -> Color.fromRGB(240, 248, 255);       // Alice / Snow White
            case "pufferfish" -> Color.fromRGB(255, 160, 0);      // Amber Orange
            case "fish" -> Color.fromRGB(0, 188, 212);            // Cyan / Aqua
            case "white_tiger" -> Color.fromRGB(235, 235, 235);   // Snow White
            case "hermit_crab" -> Color.fromRGB(255, 110, 97);    // Coral Peach
            case "ghost" -> Color.fromRGB(225, 230, 235);         // Ghostly Pale
            case "choco_rabbit" -> Color.fromRGB(93, 64, 55);     // Chocolate Brown
            case "reindeer" -> Color.fromRGB(121, 85, 72);        // Reindeer Brown
            case "sun" -> Color.fromRGB(255, 215, 0);             // Sun Gold
            case "happy_emoji" -> Color.fromRGB(255, 238, 88);    // Emoji Yellow
            case "lady_bug" -> Color.fromRGB(211, 47, 47);        // Ladybug Crimson
            case "grinch" -> Color.fromRGB(76, 175, 80);          // Grinch Green
            case "present" -> Color.fromRGB(198, 40, 40);         // Present Red
            case "easter_egg" -> Color.fromRGB(225, 190, 231);    // Easter Pastel Pink
            case "basketball" -> Color.fromRGB(230, 81, 0);       // Basketball Orange
            case "bunny" -> Color.fromRGB(215, 204, 200);         // Bunny Cream
            case "penguin" -> Color.fromRGB(33, 33, 33);          // Tuxedo Black
            case "end_stone" -> Color.fromRGB(74, 20, 140);       // Ender Void Purple
            case "shark" -> Color.fromRGB(55, 71, 79);            // Shark Slate Navy
            case "sloth" -> Color.fromRGB(141, 110, 99);          // Sloth Earth Brown
            case "ice_cream" -> Color.fromRGB(248, 187, 208);     // Strawberry Pink
            case "zombie_eye" -> Color.fromRGB(51, 105, 30);      // Zombie Rot Green
            case "skull" -> Color.fromRGB(207, 216, 220);         // Bone White
            case "beach_ball" -> Color.fromRGB(2, 136, 209);      // Sky Azure Blue
            case "pink_donut" -> Color.fromRGB(240, 98, 146);     // Donut Frosting Pink
            case "undead" -> Color.fromRGB(46, 125, 50);          // Undead Moss Green
            case "gingerbread" -> Color.fromRGB(141, 85, 36);     // Gingerbread Cookie
            case "santa" -> Color.fromRGB(183, 28, 28);           // Santa Velvet Red
            case "killer" -> Color.fromRGB(136, 14, 79);          // Blood Crimson
            case "festive_jerry" -> Color.fromRGB(27, 94, 32);    // Festive Spruce Green
            case "robot" -> Color.fromRGB(144, 164, 174);         // Chrome Silver
            case "ice_lolly" -> Color.fromRGB(128, 222, 234);     // Ice Cyan
            case "pumpkin" -> Color.fromRGB(230, 74, 25);         // Deep Pumpkin Orange
            case "seal" -> Color.fromRGB(120, 144, 156);          // Oceanic Seal Gray
            case "bee" -> Color.fromRGB(255, 179, 0);             // Honey Gold
            case "headless" -> Color.fromRGB(26, 26, 26);         // Dark Obsidian Black
            case "pink_bunny" -> Color.fromRGB(244, 143, 177);    // Bubblegum Pink
            case "mummy" -> Color.fromRGB(215, 204, 200);         // Desert Mummy Beige
            case "scarecrow" -> Color.fromRGB(188, 170, 164);     // Burlap Straw
            case "fishy_penguin" -> Color.fromRGB(26, 35, 126);   // Deep Sea Navy
            case "clownfish" -> Color.fromRGB(255, 112, 67);      // Clownfish Orange
            case "cyclops" -> Color.fromRGB(81, 45, 168);         // Cyclops Mystic Purple
            case "festive_zombie" -> Color.fromRGB(46, 125, 50);  // Christmas Holly Green
            case "festive_skeleton" -> Color.fromRGB(211, 47, 47);// Candy Cane Red
            case "apothejerry" -> Color.fromRGB(142, 36, 170);    // Apothecary Magenta
            case "polar_bear" -> Color.fromRGB(245, 245, 245);    // Arctic White
            case "melon" -> Color.fromRGB(124, 179, 66);          // Melon Lime Green
            case "banana_monkey" -> Color.fromRGB(253, 216, 53);  // Banana Yellow
            case "sandcastle" -> Color.fromRGB(210, 180, 140);    // Warm Beach Sand
            case "tropical_bird_1" -> Color.fromRGB(25, 118, 210);// Deep Hyacinth Blue
            case "tropical_bird_2" -> Color.fromRGB(0, 137, 123); // Tropical Teal
            default -> Color.fromRGB(140, 140, 140);
        };
    }

    /**
     * Saves a custom skin directly to skins.yml.
     */
    public static void saveCustomSkin(MinionSkin skin) {
        if (skin == null) return;
        register(skin);

        if (MinionX.getInstance() == null) return;
        File file = new File(MinionX.getInstance().getDataFolder(), "skins.yml");
        if (!file.getParentFile().exists()) file.getParentFile().mkdirs();

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        String path = "skins." + skin.getId();
        config.set(path + ".display-name", skin.getDisplayName());
        config.set(path + ".skull-texture", skin.getSkullTexture());
        if (skin.hasArmorMaterial()) {
            config.set(path + ".armor-material", skin.getArmorMaterial().name());
        }
        if (skin.hasArmorColor()) {
            config.set(path + ".armor-color", ColorUtil.toHexString(skin.getArmorColor()));
        }
        if (skin.hasHandItem() && skin.getHandItem().getType() != Material.AIR) {
            config.set(path + ".hand-item", skin.getHandItem().getType().name());
        }
        config.set(path + ".description", skin.getDescription());

        try {
            config.save(file);
        } catch (IOException e) {
            MinionX.getInstance().getLogger().log(Level.SEVERE, "Failed to save skin to skins.yml", e);
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
