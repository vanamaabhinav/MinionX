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
    private static final Set<String> DEFAULT_IDS = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public static boolean isCustom(String id) {
        if (id == null) return false;
        return !DEFAULT_IDS.contains(id.toLowerCase());
    }

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
            String archStr = sec.getString("archetype", "MINER").toUpperCase();
            MinionArchetype archetype;
            try {
                archetype = MinionArchetype.valueOf(archStr);
            } catch (IllegalArgumentException e) {
                archetype = MinionArchetype.MINER;
            }

            String skull = sec.getString("skull-texture");
            String armorMatName = sec.getString("armor-material", "LEATHER_CHESTPLATE");
            Material armorMat = Material.matchMaterial(armorMatName);

            int red = sec.getInt("armor-color.red", 120);
            int green = sec.getInt("armor-color.green", 120);
            int blue = sec.getInt("armor-color.blue", 120);
            Color armorColor = Color.fromRGB(red, green, blue);

            ItemStack handItem = sec.getItemStack("hand-item", new ItemStack(Material.STONE_PICKAXE));
            String targetBlockName = sec.getString("target-block");
            Material targetBlock = targetBlockName != null ? Material.matchMaterial(targetBlockName) : null;

            String targetMob = sec.getString("target-mob");
            ItemStack dropItem = sec.getItemStack("drop-item", new ItemStack(Material.COBBLESTONE));
            ItemStack secondaryDrop = sec.getItemStack("secondary-drop");
            double secondaryChance = sec.getDouble("secondary-chance", 0.0);

            int xp = sec.getInt("xp-per-harvest", 0);
            double delay = sec.getDouble("action-delay", 14.0);
            int slots = sec.getInt("storage-slots", 1);

            MinionDefinition def = new MinionDefinition(id, name, archetype, skull, armorMat, armorColor, handItem,
                    targetBlock, targetMob, dropItem, secondaryDrop, secondaryChance, xp, delay, slots);

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
        if (def.hasSecondaryDrop()) {
            config.set(path + ".secondary-drop", def.getSecondaryDropItem());
            config.set(path + ".secondary-chance", def.getSecondaryDropChance());
        }
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
        DEFAULT_IDS.clear();
        // --- 1. MINING MINIONS ---
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

        DEFINITIONS.put("coal", new MinionDefinition("coal", ColorUtil.color("&8Coal"),
                MinionArchetype.MINER,
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZWIzNTY3NWI5MmI0YmZkOTVhYjFiN2UyMDQyNTEwMzE4OGFlNjI2ZGUzY2Y0NDNmYzNjNjhmYjNmMDY3YWM4NyJ9fX0=",
                Material.LEATHER_CHESTPLATE, Color.fromRGB(40, 40, 40), new ItemStack(Material.STONE_PICKAXE),
                Material.COAL_ORE, null, new ItemStack(Material.COAL), 1, 15.0, 1));

        DEFINITIONS.put("gold", new MinionDefinition("gold", ColorUtil.color("&6Gold"),
                MinionArchetype.MINER,
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZmM4MDY0NWExY2UzYzI4NWMyODJhNmUxOTAzMDMzMjhkOTFmOTJlMTBiODRiY2Y3OTZkYmUzMjE0YzI1In19fQ==",
                Material.LEATHER_CHESTPLATE, Color.fromRGB(255, 215, 0), new ItemStack(Material.IRON_PICKAXE),
                Material.GOLD_ORE, null, new ItemStack(Material.RAW_GOLD), 3, 14.0, 1));

        DEFINITIONS.put("emerald", new MinionDefinition("emerald", ColorUtil.color("&aEmerald"),
                MinionArchetype.MINER,
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzkxOTAzMGIyZjM0OTJmMTMwOWVmOGQ0YWRlODljNGU3MmY2NDkxMWMyYzYzOTRjYzBkOWQ2NzMwYzI0ZTMifX19",
                Material.LEATHER_CHESTPLATE, Color.fromRGB(50, 205, 50), new ItemStack(Material.IRON_PICKAXE),
                Material.EMERALD_ORE, null, new ItemStack(Material.EMERALD), 5, 14.0, 1));

        DEFINITIONS.put("redstone", new MinionDefinition("redstone", ColorUtil.color("&cRedstone"),
                MinionArchetype.MINER,
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNWY3OGI2YzI3MmY5MTM0NGVkZTVjOTgyNjM3Nzg3ZjVlNDlhNDQ4ZTk0YTUxMmI4ZjJjODZmMzExN2RjNCJ9fX0=",
                Material.LEATHER_CHESTPLATE, Color.fromRGB(220, 20, 60), new ItemStack(Material.IRON_PICKAXE),
                Material.REDSTONE_ORE, null, new ItemStack(Material.REDSTONE, 4), 2, 14.0, 1));

        DEFINITIONS.put("lapis", new MinionDefinition("lapis", ColorUtil.color("&9Lapis"),
                MinionArchetype.MINER,
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTI4ZmYyMTdmMzAwNWIxZjc2MmFiYjU2YTUyOGUxYjcxM2M5ZWFhYzU4OTFiY2VmZThmYTcwNjRjN2RkOCJ9fX0=",
                Material.LEATHER_CHESTPLATE, Color.fromRGB(30, 60, 180), new ItemStack(Material.IRON_PICKAXE),
                Material.LAPIS_ORE, null, new ItemStack(Material.LAPIS_LAZULI, 4), 2, 14.0, 1));

        DEFINITIONS.put("obsidian", new MinionDefinition("obsidian", ColorUtil.color("&5Obsidian"),
                MinionArchetype.MINER,
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNDdlYTYwOTQ1YmU3MTlmOWYxMTU4M2E0YjljNGVmZWEzOWZkMDFlMWE4ZmRkYmFmY2YxMjQyNThjMmRiOGEzIn19fQ==",
                Material.LEATHER_CHESTPLATE, Color.fromRGB(30, 20, 45), new ItemStack(Material.DIAMOND_PICKAXE),
                Material.OBSIDIAN, null, new ItemStack(Material.OBSIDIAN), 8, 24.0, 1));

        DEFINITIONS.put("quartz", new MinionDefinition("quartz", ColorUtil.color("&fNether Quartz"),
                MinionArchetype.MINER,
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODdiN2YzNmQ2MWUxNDk5MmQ2YTU5YTAwYWYzZDJhYzA2YmUyNjljNDVhOTRiZDE0MjAyZjdhMDU0NzM5ZTgifX19",
                Material.LEATHER_CHESTPLATE, Color.fromRGB(240, 235, 230), new ItemStack(Material.IRON_PICKAXE),
                Material.NETHER_QUARTZ_ORE, null, new ItemStack(Material.QUARTZ), 3, 14.0, 1));

        // --- 2. FORAGING MINIONS ---
        DEFINITIONS.put("oak", new MinionDefinition("oak", ColorUtil.color("&aOak Wood"),
                MinionArchetype.FORAGING,
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNmYxNWUzODZkZTZiY2NjMTBiZGY4MTZhN2ZkNTI5YmZiMTM0N2NlZWI5MmQxN2Y1M2MwZWRiY2NlOWI3OTQ4NyJ9fX0=",
                Material.LEATHER_CHESTPLATE, Color.fromRGB(140, 100, 60), new ItemStack(Material.STONE_AXE),
                Material.OAK_LOG, null, new ItemStack(Material.OAK_LOG), new ItemStack(Material.APPLE), 0.05, 1, 14.0, 1));

        DEFINITIONS.put("birch", new MinionDefinition("birch", ColorUtil.color("&fBirch Wood"),
                MinionArchetype.FORAGING,
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzA2YTEyMWU0MjBhNDc5NDNlOGNlNzdmYTNlMzE5ZmEzZDhjN2QxY2QyZjg3NzYyMGFkZmRkZjJjZDgyIn19fQ==",
                Material.LEATHER_CHESTPLATE, Color.fromRGB(220, 215, 190), new ItemStack(Material.STONE_AXE),
                Material.BIRCH_LOG, null, new ItemStack(Material.BIRCH_LOG), new ItemStack(Material.BIRCH_SAPLING), 0.05, 1, 14.0, 1));

        DEFINITIONS.put("spruce", new MinionDefinition("spruce", ColorUtil.color("&2Spruce Wood"),
                MinionArchetype.FORAGING,
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMmViNDc2ODhhMTgyNGJkZTM3OGE0NWJkMzdlN2RiMDFiMmIzODZhZTc1OWZlMjM0Yzc3MGQ3YjZlNGU1OGY4In19fQ==",
                Material.LEATHER_CHESTPLATE, Color.fromRGB(80, 50, 30), new ItemStack(Material.STONE_AXE),
                Material.SPRUCE_LOG, null, new ItemStack(Material.SPRUCE_LOG), null, 0.0, 1, 14.0, 1));

        DEFINITIONS.put("dark_oak", new MinionDefinition("dark_oak", ColorUtil.color("&8Dark Oak Wood"),
                MinionArchetype.FORAGING,
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYjJkY2NjNjFlNTFkMWVjYjI5ZWY4YTNlOTExMjgxMzljMzdlNGY0MmJmNTQyYmQxNzM4MjQxOGNhMzIxIn19fQ==",
                Material.LEATHER_CHESTPLATE, Color.fromRGB(60, 40, 25), new ItemStack(Material.STONE_AXE),
                Material.DARK_OAK_LOG, null, new ItemStack(Material.DARK_OAK_LOG), new ItemStack(Material.APPLE), 0.05, 1, 14.0, 1));

        DEFINITIONS.put("acacia", new MinionDefinition("acacia", ColorUtil.color("&6Acacia Wood"),
                MinionArchetype.FORAGING,
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZmRhMWQ2Y2JjOWIyMjEyYTY2ZWI0MWY5ZjY1MDY2MmIwN2JmMDhkYWRhNzJjMzFkM2E4MTcyMmNkMDM2In19fQ==",
                Material.LEATHER_CHESTPLATE, Color.fromRGB(190, 95, 45), new ItemStack(Material.STONE_AXE),
                Material.ACACIA_LOG, null, new ItemStack(Material.ACACIA_LOG), null, 0.0, 1, 14.0, 1));

        DEFINITIONS.put("jungle", new MinionDefinition("jungle", ColorUtil.color("&eJungle Wood"),
                MinionArchetype.FORAGING,
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNDg0NWFmOGMxZGEzNWQyM2E4YmI5MTI0NzJhZDE5ZjVhNmMxN2FiMWFmMTVhNDc0ZTZkYzQ0Yjc0NmVlIn19fQ==",
                Material.LEATHER_CHESTPLATE, Color.fromRGB(150, 110, 70), new ItemStack(Material.STONE_AXE),
                Material.JUNGLE_LOG, null, new ItemStack(Material.JUNGLE_LOG), null, 0.0, 1, 14.0, 1));

        // --- 3. FARMING MINIONS ---
        DEFINITIONS.put("wheat", new MinionDefinition("wheat", ColorUtil.color("&eWheat"),
                MinionArchetype.FARMING,
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZWJkM2I0NTU2NGJmMmJmMTQzYTU3MWRhNzYxYWExOGViNGViNmE5YWQ3ODcyZWU5Yzc4M2JmMjg5YjRmMDA5YiJ9fX0=",
                Material.LEATHER_CHESTPLATE, Color.fromRGB(220, 190, 80), new ItemStack(Material.IRON_HOE),
                Material.WHEAT, null, new ItemStack(Material.WHEAT), new ItemStack(Material.WHEAT_SEEDS), 1.0, 1, 12.0, 1));

        DEFINITIONS.put("carrot", new MinionDefinition("carrot", ColorUtil.color("&6Carrot"),
                MinionArchetype.FARMING,
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOWMzODg4NWY1NWMxNzJjZjZkMTQyYWNhYWI0MGQxMDk4Mzg4OTJhOTExYTI3M2FkZTkzMTc1MjgxIn19fQ==",
                Material.LEATHER_CHESTPLATE, Color.fromRGB(230, 130, 30), new ItemStack(Material.IRON_HOE),
                Material.CARROTS, null, new ItemStack(Material.CARROT), null, 0.0, 1, 12.0, 1));

        DEFINITIONS.put("potato", new MinionDefinition("potato", ColorUtil.color("&ePotato"),
                MinionArchetype.FARMING,
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMmMzNGY1NWI1MTFlMzcyYzY2YTMwOGU0YTZjNTdiMGM4OGFlYTE3Yzk4YjFkNjAxYzgxMDk3MjFlNDg4In19fQ==",
                Material.LEATHER_CHESTPLATE, Color.fromRGB(200, 160, 90), new ItemStack(Material.IRON_HOE),
                Material.POTATOES, null, new ItemStack(Material.POTATO), new ItemStack(Material.POISONOUS_POTATO), 0.02, 1, 12.0, 1));

        DEFINITIONS.put("pumpkin", new MinionDefinition("pumpkin", ColorUtil.color("&6Pumpkin"),
                MinionArchetype.FARMING,
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvY2QxY2ExYzg5ZmZiOTgwZjIxNTA3ZTJmMGU5MmVlYjhhMGIyYzE4Y2Y0NDk1MDJkYTBiYTc4YWRjNzYxIn19fQ==",
                Material.LEATHER_CHESTPLATE, Color.fromRGB(220, 120, 20), new ItemStack(Material.IRON_AXE),
                Material.PUMPKIN, null, new ItemStack(Material.PUMPKIN), null, 0.0, 1, 16.0, 1));

        DEFINITIONS.put("melon", new MinionDefinition("melon", ColorUtil.color("&aMelon"),
                MinionArchetype.FARMING,
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZWQ3YjU1MjU2ZGIwOTZiNWRlYTEwN2IxNzk4MTU1MWU4NzhiNmQ2NTQzNzFiOGI0N2I5MWU4OGZjMmI3In19fQ==",
                Material.LEATHER_CHESTPLATE, Color.fromRGB(120, 180, 50), new ItemStack(Material.IRON_AXE),
                Material.MELON, null, new ItemStack(Material.MELON_SLICE, 4), null, 0.0, 1, 14.0, 1));

        DEFINITIONS.put("sugar_cane", new MinionDefinition("sugar_cane", ColorUtil.color("&aSugar Cane"),
                MinionArchetype.FARMING,
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOGE5YjFmOGFkZDJjZTRiYjNmMDYyNDEzNGNmYTk2YWRjYjc1MDM3MmJmNzEyYzdiYzcwNGNhZTcwMDcxIn19fQ==",
                Material.LEATHER_CHESTPLATE, Color.fromRGB(140, 210, 100), new ItemStack(Material.DIAMOND_HOE),
                Material.SUGAR_CANE, null, new ItemStack(Material.SUGAR_CANE), null, 0.0, 1, 14.0, 1));

        DEFINITIONS.put("cactus", new MinionDefinition("cactus", ColorUtil.color("&2Cactus"),
                MinionArchetype.FARMING,
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOWJhYmE5ZmNmZDI0NzNmZGM0OTFiZDU3OGU1MWU3N2M3OTlhMjNkNWI0YWRiOTIxZmM5MjY0ZDc3ZGNjIn19fQ==",
                Material.LEATHER_CHESTPLATE, Color.fromRGB(50, 140, 50), new ItemStack(Material.IRON_SWORD),
                Material.CACTUS, null, new ItemStack(Material.CACTUS), null, 0.0, 1, 16.0, 1));

        DEFINITIONS.put("nether_wart", new MinionDefinition("nether_wart", ColorUtil.color("&4Nether Wart"),
                MinionArchetype.FARMING,
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYzNmN2U4OTVlMWRjMDQyYzY1ZDMzMDM1M2U2Nzk3NDJhZGU0MGRkZTM2ZTlhNjI4MDVjZjI1OTgwZDY1In19fQ==",
                Material.LEATHER_CHESTPLATE, Color.fromRGB(150, 20, 30), new ItemStack(Material.IRON_HOE),
                Material.NETHER_WART, null, new ItemStack(Material.NETHER_WART), null, 0.0, 1, 16.0, 1));

        DEFINITIONS.put("red_mushroom", new MinionDefinition("red_mushroom", ColorUtil.color("&cRed Mushroom"),
                MinionArchetype.FARMING,
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMmVkZjQzOTBhMDUzYTM0MWE0MjNmNmE2ZDUwOTIxOWRhOTU1NDJjZWFjMzFiODIxYmFlNzBmYTY1NDQ2In19fQ==",
                Material.LEATHER_CHESTPLATE, Color.fromRGB(180, 40, 40), new ItemStack(Material.IRON_HOE),
                Material.RED_MUSHROOM, null, new ItemStack(Material.RED_MUSHROOM), null, 0.0, 1, 16.0, 1));

        DEFINITIONS.put("brown_mushroom", new MinionDefinition("brown_mushroom", ColorUtil.color("&6Brown Mushroom"),
                MinionArchetype.FARMING,
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYjFmOGM0ZDVkMGUyOWVmYTVmOGI1ODk0ZjQxYTVmOTI0NDg0M2JjZDFjOWFjOTNjMDQ1MGE4Y2IxYzAxIn19fQ==",
                Material.LEATHER_CHESTPLATE, Color.fromRGB(140, 90, 50), new ItemStack(Material.IRON_HOE),
                Material.BROWN_MUSHROOM, null, new ItemStack(Material.BROWN_MUSHROOM), null, 0.0, 1, 16.0, 1));

        // --- 4. COMBAT & HUSBANDRY MINIONS ---
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

        DEFINITIONS.put("spider", new MinionDefinition("spider", ColorUtil.color("&8Spider"),
                MinionArchetype.COMBAT,
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvY2Q1NDE1NDFkYWFmZjUwODk2Y2Y2ZTIzNjg4NTdhYmQ5MmJjZmVkZWMzZTNmODVhMzM1YTQ4NRjODg4YmNmIn19fQ==",
                Material.LEATHER_CHESTPLATE, Color.fromRGB(50, 40, 40), new ItemStack(Material.IRON_SWORD),
                null, "SPIDER", new ItemStack(Material.STRING), new ItemStack(Material.SPIDER_EYE), 0.5, 4, 13.0, 1));

        DEFINITIONS.put("creeper", new MinionDefinition("creeper", ColorUtil.color("&aCreeper"),
                MinionArchetype.COMBAT,
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODlhNzdhODZmMmFkY2M5YTgzYzRhMmJmZTI1YmNjMTY5NmZlZTQ4NTVkNWQyYzgxY2U1NGY4YzA4ZjQ3In19fQ==",
                Material.LEATHER_CHESTPLATE, Color.fromRGB(50, 180, 50), new ItemStack(Material.IRON_SWORD),
                null, "CREEPER", new ItemStack(Material.GUNPOWDER), 5, 14.0, 1));

        DEFINITIONS.put("enderman", new MinionDefinition("enderman", ColorUtil.color("&5Enderman"),
                MinionArchetype.COMBAT,
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvN2E1OWJiMGE3YTMyOTY1YjNkOTBkOGVhZmE4OTlkMTgzNWY0MjQ1NWFmMzg4ZmU1ODFhMzQ0MmNwNWVmM2EifX19",
                Material.LEATHER_CHESTPLATE, Color.fromRGB(20, 20, 25), new ItemStack(Material.DIAMOND_SWORD),
                null, "ENDERMAN", new ItemStack(Material.ENDER_PEARL), 6, 16.0, 1));

        DEFINITIONS.put("blaze", new MinionDefinition("blaze", ColorUtil.color("&6Blaze"),
                MinionArchetype.COMBAT,
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYjc4ZWYyZVc4OTdhMmU1ZWY2M2U0NTc3OWFmNzExNGQ1MWNkNWJmODc4MjkxMzQzMjk1NDE0NzAwMWNkNmMifX19",
                Material.LEATHER_CHESTPLATE, Color.fromRGB(255, 140, 0), new ItemStack(Material.DIAMOND_SWORD),
                null, "BLAZE", new ItemStack(Material.BLAZE_ROD), 6, 16.0, 1));

        DEFINITIONS.put("cow", new MinionDefinition("cow", ColorUtil.color("&fCow"),
                MinionArchetype.FARMING,
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNWQ2YzZlZGE5NDJmN2Y1ZjcxYzMxNjFjNzMwNmY0YWVkMzA3ZDIwNmY1M2MzOThkYWM3NTFhNjgyZTAifX19",
                Material.LEATHER_CHESTPLATE, Color.fromRGB(90, 60, 40), new ItemStack(Material.IRON_SWORD),
                null, "COW", new ItemStack(Material.BEEF), new ItemStack(Material.LEATHER), 0.5, 3, 15.0, 1));

        DEFINITIONS.put("pig", new MinionDefinition("pig", ColorUtil.color("&dPig"),
                MinionArchetype.FARMING,
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNjIxNjY4ZWY3Y2I3OWRkOWMyMmNlM2QxZjNmNGNiNmE2MWU5ODFkZTZkYTE2ZGU4YzU0N2IzOWFjOTMifX19",
                Material.LEATHER_CHESTPLATE, Color.fromRGB(240, 150, 160), new ItemStack(Material.IRON_SWORD),
                null, "PIG", new ItemStack(Material.PORKCHOP), null, 0.0, 3, 15.0, 1));

        DEFINITIONS.put("chicken", new MinionDefinition("chicken", ColorUtil.color("&fChicken"),
                MinionArchetype.FARMING,
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMTYzODQ2OWE1OTlkZWVmNzJlNzExNDQzNjc1MmZlNDJhNTFkMTJmOWExNzQzNTQ2NWFjNjc3YmU5N2Y1ZDYifX19",
                Material.LEATHER_CHESTPLATE, Color.fromRGB(230, 230, 230), new ItemStack(Material.IRON_SWORD),
                null, "CHICKEN", new ItemStack(Material.CHICKEN), new ItemStack(Material.FEATHER), 0.5, 2, 14.0, 1));

        DEFINITIONS.put("sheep", new MinionDefinition("sheep", ColorUtil.color("&fSheep"),
                MinionArchetype.FARMING,
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZmIzZjEzZTZkMzg2OGRiZWExMmE2ZWVjMmM4Yzg4NmZhMmQ1OWM0MmRhMWYxNWZhMmY2ODVkM2YyYTVmMyJ9fX0=",
                Material.LEATHER_CHESTPLATE, Color.fromRGB(220, 220, 220), new ItemStack(Material.SHEARS),
                null, "SHEEP", new ItemStack(Material.MUTTON), new ItemStack(Material.WHITE_WOOL), 1.0, 3, 14.0, 1));

        DEFINITIONS.put("rabbit", new MinionDefinition("rabbit", ColorUtil.color("&6Rabbit"),
                MinionArchetype.FARMING,
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvN2QxYzg5N2U5NGQ4ZjA3OTQ4NWU4OWQ1ZjM4MDliZDNkZTAyYmRhNGE5OWMyOTBlMzc3MzliYmY4N2E2In19fQ==",
                Material.LEATHER_CHESTPLATE, Color.fromRGB(180, 130, 90), new ItemStack(Material.IRON_SWORD),
                null, "RABBIT", new ItemStack(Material.RABBIT), new ItemStack(Material.RABBIT_HIDE), 0.5, 3, 14.0, 1));
        DEFAULT_IDS.addAll(DEFINITIONS.keySet());
    }

    public static MinionDefinition get(String id) {
        if (id == null) return null;
        return DEFINITIONS.get(id.toLowerCase());
    }

    public static Collection<MinionDefinition> getAll() {
        return DEFINITIONS.values();
    }

    public static Set<String> getKeys() {
        return DEFINITIONS.keySet();
    }
}
