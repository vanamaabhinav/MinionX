package com.minionx;

import com.minionx.api.FuelRegistry;
import com.minionx.api.MinionRegistry;
import com.minionx.api.MinionSkinRegistry;
import com.minionx.api.MinionTier;
import com.minionx.api.UpgradeRegistry;
import com.minionx.command.MinionCommand;
import com.minionx.command.OperatorCommand;
import com.minionx.core.CompressionManager;
import com.minionx.core.MinionManager;
import com.minionx.core.MinionTickTask;
import com.minionx.core.PlayerLimitManager;
import com.minionx.gui.MinionCreatorListener;
import com.minionx.gui.MinionGUIListener;
import com.minionx.gui.OperatorGUIListener;
import com.minionx.hook.EconomyHook;
import com.minionx.hook.ProtectionHookManager;
import com.minionx.hook.SkillHook;
import com.minionx.listener.ChunkLifecycleListener;
import com.minionx.listener.CustomItemProtectionListener;
import com.minionx.listener.MinionInteractListener;
import com.minionx.listener.MinionPlaceListener;
import com.minionx.listener.MinionStorageListener;

import com.minionx.storage.MinionStorage;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class MinionX extends JavaPlugin {

    private static MinionX instance;
    private MinionManager minionManager;
    private MinionStorage minionStorage;
    private MinionTickTask minionTickTask;
    private PlayerLimitManager playerLimitManager;

    @Override
    public void onEnable() {
        instance = this;

        // Save default config
        saveDefaultConfig();

        // Check external hooks
        ProtectionHookManager.checkHooks();
        EconomyHook.setupEconomy();
        SkillHook.checkHooks();

        // Load dynamic custom tiers, fuels, upgrades, compression recipes, and custom minions
        loadRegistries();

        // Initialize Managers
        this.minionManager = new MinionManager();
        this.playerLimitManager = new PlayerLimitManager(this);
        this.minionStorage = new MinionStorage(this);

        // Load all saved minions from disk / database
        this.minionStorage.loadAll(this.minionManager);

        // Register Listeners
        getServer().getPluginManager().registerEvents(new MinionPlaceListener(), this);
        getServer().getPluginManager().registerEvents(new MinionStorageListener(), this);
        getServer().getPluginManager().registerEvents(new MinionInteractListener(), this);
        getServer().getPluginManager().registerEvents(new MinionGUIListener(), this);
        getServer().getPluginManager().registerEvents(new MinionCreatorListener(), this);
        getServer().getPluginManager().registerEvents(new OperatorGUIListener(), this);
        getServer().getPluginManager().registerEvents(new ChunkLifecycleListener(), this);
        getServer().getPluginManager().registerEvents(new CustomItemProtectionListener(), this);

        // Register Commands
        MinionCommand minionCommand = new MinionCommand(this);
        PluginCommand cmd = getCommand("minion");
        if (cmd != null) {
            cmd.setExecutor(minionCommand);
            cmd.setTabCompleter(minionCommand);
        }

        // Register Operator Command (/minionsx and /minionx)
        OperatorCommand operatorCommand = new OperatorCommand();
        PluginCommand opCmd = getCommand("minionsx");
        if (opCmd != null) {
            opCmd.setExecutor(operatorCommand);
        }

        // Start repeating tick task (every 20 ticks = 1s)
        int tickInterval = getConfig().getInt("settings.tick-interval", 20);
        this.minionTickTask = new MinionTickTask(this.minionManager);
        this.minionTickTask.runTaskTimer(this, 20L, tickInterval);

        // Start periodic auto-save task (every 5 minutes) asynchronously
        int autoSaveInterval = getConfig().getInt("settings.auto-save-interval", 6000);
        getServer().getScheduler().runTaskTimer(this, () -> {
            minionStorage.saveAllAsync(minionManager);
        }, autoSaveInterval, autoSaveInterval);

        getLogger().info("MinionX has been enabled successfully!");
    }

    @Override
    public void onDisable() {
        // Cancel tasks
        if (minionTickTask != null) {
            minionTickTask.cancel();
        }

        // Save player limits
        if (playerLimitManager != null) {
            playerLimitManager.save();
        }

        // Save all minion states to disk / database
        if (minionStorage != null && minionManager != null) {
            minionStorage.saveAll(minionManager);
            minionStorage.close();
            // Cleanly despawn all visual entities to prevent duplicates on restart
            minionManager.removeAllVisuals();
        }

        getLogger().info("MinionX has been disabled.");
    }

    public static MinionX getInstance() {
        return instance;
    }

    public MinionManager getMinionManager() {
        return minionManager;
    }

    public MinionStorage getMinionStorage() {
        return minionStorage;
    }

    public PlayerLimitManager getPlayerLimitManager() {
        return playerLimitManager;
    }

    public void loadRegistries() {
        MinionTier.load(getConfig().getConfigurationSection("tiers"));
        FuelRegistry.load(getConfig().getConfigurationSection("fuels"));
        UpgradeRegistry.load(getConfig().getConfigurationSection("upgrades"));
        CompressionManager.loadRecipes();
        MinionRegistry.load();
        MinionSkinRegistry.loadBuiltinSkins();
        getLogger().info("Loaded " + MinionTier.getMaxTier() + " tiers, " + FuelRegistry.getKeys().size() + " fuels, " +
                UpgradeRegistry.getKeys().size() + " upgrades, " + MinionSkinRegistry.getAll().size() + " skins, and " + MinionRegistry.getAll().size() + " minion definitions.");
    }
}
