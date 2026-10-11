package com.skyblock.minions.storage;

import com.skyblock.minions.SkyBlockMinions;
import com.skyblock.minions.api.*;
import com.skyblock.minions.core.Minion;
import com.skyblock.minions.core.MinionManager;
import com.skyblock.minions.visual.MinionHologram;
import com.skyblock.minions.visual.MinionVisual;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Handles YAML serialization and persistence for active minions.
 */
public class MinionStorage {

    private final SkyBlockMinions plugin;
    private final File dataFile;

    public MinionStorage(SkyBlockMinions plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "data/minions.yml");
    }

    public void saveAll(MinionManager manager) {
        if (!dataFile.getParentFile().exists()) {
            dataFile.getParentFile().mkdirs();
        }

        YamlConfiguration config = new YamlConfiguration();

        for (Minion minion : manager.getAllMinions()) {
            String path = "minions." + minion.getMinionId().toString();

            config.set(path + ".owner", minion.getOwnerId() != null ? minion.getOwnerId().toString() : null);
            config.set(path + ".type", minion.getType().name());
            config.set(path + ".tier", minion.getTier());

            Location loc = minion.getLocation();
            config.set(path + ".world", loc.getWorld() != null ? loc.getWorld().getName() : "world");
            config.set(path + ".x", loc.getX());
            config.set(path + ".y", loc.getY());
            config.set(path + ".z", loc.getZ());
            config.set(path + ".yaw", loc.getYaw());
            config.set(path + ".pitch", loc.getPitch());

            config.set(path + ".fuel", minion.getFuelId());
            config.set(path + ".fuel-end", minion.getFuelEndTime());

            config.set(path + ".upgrade1", minion.getUpgrade1Id());
            config.set(path + ".upgrade2", minion.getUpgrade2Id());

            config.set(path + ".last-action", minion.getLastActionTime());
            config.set(path + ".last-was-place", minion.wasLastActionPlace());
            config.set(path + ".harvested", minion.getTotalHarvested());

            config.set(path + ".items", minion.getInventory());
        }

        try {
            config.save(dataFile);
            plugin.getLogger().info("Saved " + manager.getAllMinions().size() + " minions to minions.yml.");
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save minions to file!", e);
        }
    }

    public void loadAll(MinionManager manager) {
        if (!dataFile.exists()) return;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection section = config.getConfigurationSection("minions");
        if (section == null) return;

        int loadedCount = 0;
        for (String idStr : section.getKeys(false)) {
            try {
                UUID id = UUID.fromString(idStr);
                String ownerStr = section.getString(idStr + ".owner");
                UUID ownerId = ownerStr != null ? UUID.fromString(ownerStr) : null;

                String typeStr = section.getString(idStr + ".type");
                MinionType type = MinionType.fromString(typeStr);
                if (type == null) continue;

                int tier = section.getInt(idStr + ".tier", 1);

                String worldName = section.getString(idStr + ".world", "world");
                World world = Bukkit.getWorld(worldName);
                if (world == null) {
                    plugin.getLogger().warning("World '" + worldName + "' not found for minion " + id + ", skipping.");
                    continue;
                }

                double x = section.getDouble(idStr + ".x");
                double y = section.getDouble(idStr + ".y");
                double z = section.getDouble(idStr + ".z");
                float yaw = (float) section.getDouble(idStr + ".yaw", 0.0);
                float pitch = (float) section.getDouble(idStr + ".pitch", 0.0);
                Location loc = new Location(world, x, y, z, yaw, pitch);

                Minion minion = new Minion(id, ownerId, type, tier, loc);

                String fuelStr = section.getString(idStr + ".fuel");
                if (fuelStr != null && !fuelStr.isBlank()) {
                    minion.setFuel(fuelStr);
                    minion.setFuelEndTime(section.getLong(idStr + ".fuel-end", 0L));
                }

                String up1Str = section.getString(idStr + ".upgrade1");
                if (up1Str != null && !up1Str.isBlank()) {
                    minion.setUpgrade1Id(up1Str);
                }

                String up2Str = section.getString(idStr + ".upgrade2");
                if (up2Str != null && !up2Str.isBlank()) {
                    minion.setUpgrade2Id(up2Str);
                }

                minion.setLastActionTime(section.getLong(idStr + ".last-action", System.currentTimeMillis()));
                minion.setLastActionWasPlace(section.getBoolean(idStr + ".last-was-place", false));
                minion.setTotalHarvested(section.getInt(idStr + ".harvested", 0));

                List<?> rawItems = section.getList(idStr + ".items");
                if (rawItems != null) {
                    for (Object obj : rawItems) {
                        if (obj instanceof ItemStack stack) {
                            minion.getInventory().add(stack);
                        }
                    }
                }

                manager.register(minion);

                // Spawn visual entities if chunk is currently loaded
                if (loc.isChunkLoaded()) {
                    MinionVisual.spawnOrUpdate(minion);
                    MinionHologram.updateHologram(minion);
                }

                loadedCount++;
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load minion " + idStr, e);
            }
        }

        plugin.getLogger().info("Successfully loaded " + loadedCount + " minions.");
    }
}
