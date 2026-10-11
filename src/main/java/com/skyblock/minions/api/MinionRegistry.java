package com.skyblock.minions.api;

import com.skyblock.minions.SkyBlockMinions;
import com.skyblock.minions.util.ColorUtil;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Registry holding all built-in and user-created Minion definitions.
 * Serializes custom minions directly to/from minions.yml.
 */
public class MinionRegistry {

    private static final Map<String, MinionDefinition> DEFINITIONS = new ConcurrentHashMap<>();

    static {
        registerDefaults();
    }

    public static void load() {
        DEFINITIONS.clear();
        registerDefaults();

        if (SkyBlockMinions.getInstance() == null) return;
        File file = new File(SkyBlockMinions.getInstance().getDataFolder(), "minions.yml");
        if (!file.exists()) return;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("custom-minions");
        if (section == null) return;

        for (String id : section.getKeys(false)) {
            ConfigurationSection sec = section.getConfigurationSection(id);
            if (sec == null) continue;

            String name = ColorUtil.color(sec.getString("display-name", id));
            String archStr = sec.getString("archetype", "MINER");
            MinionArchetype archetype = "COMBAT".equalsIgnoreCase(archStr) ? MinionArchetype.COMBAT : MinionArchetype.MINER;

            String skull = sec.getString("skull-texture");
            String armorMatName = sec.getString("armor-material", "LEATHER_CHESTPLATE");
            Material armorMat = Material.matchMaterial(armorMatName);

            int red = sec.getInt("armor-color.red", 120);
            int green = sec.getInt("armor-color.green", 120);
            int blue = sec.getInt("armor-color.blue", 120);
            Color armorColor = Color.fromRGB(red, green, blue);

            ItemStack handItem = sec.getItemStack("hand-item", new ItemStack(Material.STONE_PICKAXE));
            String targetBlockName = sec.getString("target-block", "COBBLESTONE");
            Material targetBlock = Material.matchMaterial(targetBlockName);

            String targetMob = sec.getString("target-mob");
            ItemStack dropItem = sec.getItemStack("drop-item", new ItemStack(Material.COBBLESTONE));
            int xp = sec.getInt("xp-per-harvest", 0);
            double delay = sec.getDouble("action-delay", 14.0);
            int slots = sec.getInt("storage-slots", 1);

            MinionDefinition def = new MinionDefinition(id, name, archetype, skull, armorMat, armorColor, handItem,
                    targetBlock, targetMob, dropItem, xp, delay, slots);

            // Load optional per-tier styles
            ConfigurationSection tierSec = sec.getConfigurationSection("tier-styles");
            if (tierSec != null) {
                for (String tKey : tierSec.getKeys(false)) {
                    try {
                        int tier = Integer.parseInt(tKey);
                        ConfigurationSection ts = tierSec.getConfigurationSection(tKey);
                        if (ts != null) {
                            String tSkull = ts.getString("skull-texture");
                            Material tArmor = Material.matchMaterial(ts.getString("armor-material", "LEATHER_CHESTPLATE"));
                            int tr = ts.getInt("armor-color.red", red);
                            int tg = ts.getInt("armor-color.green", green);
                            int tb = ts.getInt("armor-color.blue", blue);
                            ItemStack tHand = ts.getItemStack("hand-item");
                            def.addTierStyle(tier, new MinionDefinition.TierStyle(tSkull, tArmor, Color.fromRGB(tr, tg, tb), tHand));
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }

            DEFINITIONS.put(id.toLowerCase(), def);
        }
    }

    public static void saveCustomMinion(MinionDefinition def) {
        DEFINITIONS.put(def.getId().toLowerCase(), def);

        if (SkyBlockMinions.getInstance() == null) return;
        File file = new File(SkyBlockMinions.getInstance().getDataFolder(), "minions.yml");
        YamlConfiguration config = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();

        String path = "custom-minions." + def.getId().toLowerCase();
        config.set(path + ".display-name", def.getDisplayName());
        config.set(path + ".archetype", def.getArchetype().name());
        config.set(path + ".skull-texture", def.getSkullTexture(1));
        config.set(path + ".armor-material", def.getArmorMaterial(1).name());
        config.set(path + ".armor-color.red", def.getArmorColor(1).getRed());
        config.set(path + ".armor-color.green", def.getArmorColor(1).getGreen());
        config.set(path + ".armor-color.blue", def.getArmorColor(1).getBlue());
        config.set(path + ".hand-item", def.getHandItem(1));
        config.set(path + ".target-block", def.getTargetBlock() != null ? def.getTargetBlock().name() : null);
        config.set(path + ".target-mob", def.getTargetMob());
        config.set(path + ".drop-item", def.getExactDropItem());
        config.set(path + ".xp-per-harvest", def.getXpPerHarvest());
        config.set(path + ".action-delay", def.getDefaultActionDelay());
        config.set(path + ".storage-slots", def.getDefaultStorageSlots());

        try {
            config.save(file);
        } catch (IOException e) {
            SkyBlockMinions.getInstance().getLogger().log(Level.SEVERE, "Failed to save custom minion to minions.yml", e);
        }
    }

    private static void registerDefaults() {
        DEFINITIONS.put("cobblestone", new MinionDefinition("cobblestone", ColorUtil.color("&aCobblestone"),
                MinionArchetype.MINER,
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMTRhMjQzZTU1OWU5OWExMGJmMWFlNGU2YmFmYmY0MTA1ODhhMTVhM2Q2YzVjOGVmMjljMWE4YmRiNWYzMWFjYyJ9fX0=",
                Material.LEATHER_CHESTPLATE, Color.fromRGB(120, 120, 120), new ItemStack(Material.STONE_PICKAXE),
                Material.COBBLESTONE, null, new ItemStack(Material.COBBLESTONE), 0, 14.0, 1));

        DEFINITIONS.put("iron", new MinionDefinition("iron", ColorUtil.color("&fIron"),
                MinionArchetype.MINER,
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTZlOWFlMTVmNTRkOWI5ZTEyZGIzOWZhZTZlODlhMWY4MmU1YzlmOTBhOTRkZjljYzg0MjM1YWM3NzFmIn19fQ==",
                Material.LEATHER_CHESTPLATE, Color.fromRGB(220, 220, 220), new ItemStack(Material.IRON_PICKAXE),
                Material.IRON_ORE, null, new ItemStack(Material.RAW_IRON), 1, 14.0, 1));

        DEFINITIONS.put("diamond", new MinionDefinition("diamond", ColorUtil.color("&bDiamond"),
                MinionArchetype.MINER,
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYTRmODcxMThjZGEyMmQ0OWU4OTU1ZTZiYWY4Nzg2MzIxMzAxYWI5N2M3YjU3Mzg4YTFhMjAxYjU2YTUxZiJ9fX0=",
                Material.LEATHER_CHESTPLATE, Color.fromRGB(80, 220, 230), new ItemStack(Material.DIAMOND_PICKAXE),
                Material.DIAMOND_ORE, null, new ItemStack(Material.DIAMOND), 5, 14.0, 1));

        DEFINITIONS.put("zombie", new MinionDefinition("zombie", ColorUtil.color("&cZombie"),
                MinionArchetype.COMBAT,
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTZmYzg1NGJiODRjZjRiNDcxN2UxNTQ4MDU1NmU3NzM4ZTExYzcxM2NkMzlmYTkyOTAyMTUzNzYzMTIzMzEifX19",
                Material.LEATHER_CHESTPLATE, Color.fromRGB(50, 120, 50), new ItemStack(Material.IRON_SWORD),
                null, "ZOMBIE", new ItemStack(Material.ROTTEN_FLESH), 5, 12.0, 1));

        DEFINITIONS.put("skeleton", new MinionDefinition("skeleton", ColorUtil.color("&fSkeleton"),
                MinionArchetype.COMBAT,
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvM2JmNTM0ZWRhNTJlMzEwODk0ZWVhNzU1ZThkYWE2MWRmZDdhODFjNmUxYWRlZmYzYjVjODhhYmFiZDA5OSJ9fX0=",
                Material.LEATHER_CHESTPLATE, Color.fromRGB(200, 200, 200), new ItemStack(Material.BOW),
                null, "SKELETON", new ItemStack(Material.BONE), 5, 12.0, 1));
    }

    public static MinionDefinition get(String id) {
        if (id == null) return null;
        return DEFINITIONS.get(id.toLowerCase());
    }

    public static Collection<MinionDefinition> getAll() {
        return Collections.unmodifiableCollection(DEFINITIONS.values());
    }

    public static Set<String> getKeys() {
        return Collections.unmodifiableSet(DEFINITIONS.keySet());
    }
}