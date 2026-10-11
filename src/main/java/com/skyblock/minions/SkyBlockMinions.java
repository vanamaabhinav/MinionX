package com.skyblock.minions;

import com.skyblock.minions.api.FuelRegistry;
import com.skyblock.minions.api.UpgradeRegistry;
import com.skyblock.minions.command.MinionCommand;
import com.skyblock.minions.core.MinionManager;
import com.skyblock.minions.core.MinionTickTask;
import com.skyblock.minions.gui.MinionGUIListener;
import com.skyblock.minions.listener.ChunkLifecycleListener;
import com.skyblock.minions.listener.MinionInteractListener;
import com.skyblock.minions.listener.MinionPlaceListener;
import com.skyblock.minions.storage.MinionStorage;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class SkyBlockMinions extends JavaPlugin {

    private static SkyBlockMinions instance;
    private MinionManager minionManager;
    private MinionStorage minionStorage;
    private MinionTickTask minionTickTask;

    @Override
    public void onEnable() {
        instance = this;

        // Save default config
        saveDefaultConfig();

        // Load dynamic custom fuels and upgrades from config.yml
        loadRegistries();

        // Initialize Managers
        this.minionManager = new MinionManager();
        this.minionStorage = new MinionStorage(this);

        // Load all saved minions from disk
        this.minionStorage.loadAll(this.minionManager);

        // Register Listeners
        getServer().getPluginManager().registerEvents(new MinionPlaceListener(), this);
        getServer().getPluginManager().registerEvents(new MinionInteractListener(), this);
        getServer().getPluginManager().registerEvents(new MinionGUIListener(), this);
        getServer().getPluginManager().registerEvents(new ChunkLifecycleListener(), this);

        // Register Commands
        MinionCommand minionCommand = new MinionCommand(this);
        PluginCommand cmd = getCommand("minion");
        if (cmd != null) {
            cmd.setExecutor(minionCommand);
            cmd.setTabCompleter(minionCommand);
        }

        // Start repeating tick task (every 20 ticks = 1s)
        int tickInterval = getConfig().getInt("settings.tick-interval", 20);
        this.minionTickTask = new MinionTickTask(this.minionManager);
        this.minionTickTask.runTaskTimer(this, 20L, tickInterval);

        // Start periodic auto-save task (every 5 minutes)
        int autoSaveInterval = getConfig().getInt("settings.auto-save-interval", 6000);
        getServer().getScheduler().runTaskTimer(this, () -> {
            minionStorage.saveAll(minionManager);
        }, autoSaveInterval, autoSaveInterval);

        getLogger().info("SkyBlockMinions has been enabled successfully!");
    }

    @Override
    public void onDisable() {
        // Cancel tasks
        if (minionTickTask != null) {
            minionTickTask.cancel();
        }

        // Save all minion states to disk
        if (minionStorage != null && minionManager != null) {
            minionStorage.saveAll(minionManager);
            // Cleanly despawn all visual entities to prevent duplicates on restart
            minionManager.removeAllVisuals();
        }

        getLogger().info("SkyBlockMinions has been disabled.");
    }

    public static SkyBlockMinions getInstance() {
        return instance;
    }

    public MinionManager getMinionManager() {
        return minionManager;
    }

    public MinionStorage getMinionStorage() {
        return minionStorage;
    }

    public void loadRegistries() {
        FuelRegistry.load(getConfig().getConfigurationSection("fuels"));
        UpgradeRegistry.load(getConfig().getConfigurationSection("upgrades"));
        getLogger().info("Loaded " + FuelRegistry.getKeys().size() + " fuels and " + UpgradeRegistry.getKeys().size() + " upgrades.");
    }
}
