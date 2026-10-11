package com.skyblock.minions.api;

import com.skyblock.minions.SkyBlockMinions;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Registry storing all built-in and custom Minion Storage definitions.
 */
public class MinionStorageRegistry {

    private static final Map<String, CustomMinionStorage> STORAGES = new ConcurrentHashMap<>();

    public static void load() {
        STORAGES.clear();
        registerDefaults();
        loadCustomStorages();
    }

    private static void registerDefaults() {
        register(new CustomMinionStorage(
                "small_storage",
                "&aSmall Minion Storage",
                3,
                Material.CHEST,
                List.of("&7Places next to a minion to expand",
                        "&7storage by &e3 slots&7.",
                        "&7",
                        "&ePlace adjacent to minion to link!")
        ));

        register(new CustomMinionStorage(
                "medium_storage",
                "&9Medium Minion Storage",
                9,
                Material.CHEST,
                List.of("&7Places next to a minion to expand",
                        "&7storage by &e9 slots&7 (3x3 grid).",
                        "&7",
                        "&ePlace adjacent to minion to link!")
        ));

        register(new CustomMinionStorage(
                "large_storage",
                "&5Large Minion Storage",
                15,
                Material.CHEST,
                List.of("&7Places next to a minion to expand",
                        "&7storage by &e15 slots&7 (3x5 grid).",
                        "&7",
                        "&ePlace adjacent to minion to link!")
        ));

        register(new CustomMinionStorage(
                "max_storage",
                "&6Max Minion Storage",
                27,
                Material.CHEST,
                List.of("&7Places next to a minion to expand",
                        "&7storage by &e27 slots&7 (Full chest).",
                        "&7",
                        "&ePlace adjacent to minion to link!")
        ));
    }

    private static void loadCustomStorages() {
        if (SkyBlockMinions.getInstance() == null) return;
        File file = new File(SkyBlockMinions.getInstance().getDataFolder(), "storages.yml");
        if (!file.exists()) return;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("storages");
        if (section == null) return;

        for (String id : section.getKeys(false)) {
            ConfigurationSection s = section.getConfigurationSection(id);
            if (s == null) continue;

            String name = s.getString("display-name", "&a" + id);
            int capacity = s.getInt("capacity", 9);
            Material mat = Material.matchMaterial(s.getString("material", "CHEST"));
            if (mat == null) mat = Material.CHEST;
            List<String> lore = s.getStringList("lore");

            register(new CustomMinionStorage(id, name, capacity, mat, lore));
        }
    }

    public static void saveCustomStorage(CustomMinionStorage storage) {
        if (storage == null) return;
        register(storage);

        if (SkyBlockMinions.getInstance() == null) return;
        File file = new File(SkyBlockMinions.getInstance().getDataFolder(), "storages.yml");
        if (!file.getParentFile().exists()) file.getParentFile().mkdirs();

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        String path = "storages." + storage.getId();
        config.set(path + ".display-name", storage.getDisplayName());
        config.set(path + ".capacity", storage.getCapacity());
        config.set(path + ".material", storage.getMaterial().name());
        config.set(path + ".lore", storage.getLore());

        try {
            config.save(file);
        } catch (IOException e) {
            SkyBlockMinions.getInstance().getLogger().log(Level.SEVERE, "Failed to save storage to storages.yml", e);
        }
    }

    public static void register(CustomMinionStorage storage) {
        if (storage != null) {
            STORAGES.put(storage.getId().toLowerCase(), storage);
        }
    }

    public static CustomMinionStorage get(String id) {
        if (id == null) return null;
        return STORAGES.get(id.toLowerCase());
    }

    public static Collection<CustomMinionStorage> getAll() {
        return Collections.unmodifiableCollection(STORAGES.values());
    }

    public static Set<String> getKeys() {
        return Collections.unmodifiableSet(STORAGES.keySet());
    }
}
