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
        getServer().getPluginManager().registerEvents(new com.minionx.gui.TierEditorListener(), this);
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

        // Initialize UpdateChecker & bStats Metrics
        com.minionx.util.UpdateChecker.init(this);
        com.minionx.util.MinionXMetrics.init(this);

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

    private org.bukkit.configuration.file.FileConfiguration tiersConfig;
    private org.bukkit.configuration.file.FileConfiguration fuelsConfig;
    private org.bukkit.configuration.file.FileConfiguration upgradesConfig;
    private org.bukkit.configuration.file.FileConfiguration recipesConfig;
    private org.bukkit.configuration.file.FileConfiguration messagesConfig;
    private org.bukkit.configuration.file.FileConfiguration minionsConfig;

    public org.bukkit.configuration.file.FileConfiguration loadSubConfig(String fileName) {
        java.io.File file = new java.io.File(getDataFolder(), fileName);
        if (!file.exists()) {
            saveResource(fileName, false);
        }
        return org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
    }

    public void reloadAllConfigs() {
        reloadConfig();
        loadRegistries();
    }

    public void loadRegistries() {
        this.tiersConfig = loadSubConfig("tiers.yml");
        this.fuelsConfig = loadSubConfig("fuels.yml");
        this.upgradesConfig = loadSubConfig("upgrades.yml");
        this.recipesConfig = loadSubConfig("recipes.yml");
        this.messagesConfig = loadSubConfig("messages.yml");
        this.minionsConfig = loadSubConfig("minions.yml");

        org.bukkit.configuration.ConfigurationSection tiersSec = tiersConfig.contains("tiers")
                ? tiersConfig.getConfigurationSection("tiers")
                : getConfig().getConfigurationSection("tiers");
        MinionTier.load(tiersSec);

        org.bukkit.configuration.ConfigurationSection fuelsSec = fuelsConfig.contains("fuels")
                ? fuelsConfig.getConfigurationSection("fuels")
                : getConfig().getConfigurationSection("fuels");
        FuelRegistry.load(fuelsSec);

        org.bukkit.configuration.ConfigurationSection upgradesSec = upgradesConfig.contains("upgrades")
                ? upgradesConfig.getConfigurationSection("upgrades")
                : getConfig().getConfigurationSection("upgrades");
        UpgradeRegistry.load(upgradesSec);

        org.bukkit.configuration.ConfigurationSection recipesSec = recipesConfig.contains("compression-recipes")
                ? recipesConfig.getConfigurationSection("compression-recipes")
                : getConfig().getConfigurationSection("compression-recipes");
        CompressionManager.loadRecipes(recipesSec);

        MinionRegistry.load(minionsConfig);
        MinionSkinRegistry.loadBuiltinSkins();
        getLogger().info("Loaded " + MinionTier.getMaxTier() + " tiers, " + FuelRegistry.getKeys().size() + " fuels, " +
                UpgradeRegistry.getKeys().size() + " upgrades, " + MinionSkinRegistry.getAll().size() + " skins, and " + MinionRegistry.getAll().size() + " minion definitions.");
    }

    public String getMessage(String path, String defaultValue) {
        if (messagesConfig != null && messagesConfig.contains("messages." + path)) {
            return messagesConfig.getString("messages." + path, defaultValue);
        }
        if (messagesConfig != null && messagesConfig.contains(path)) {
            return messagesConfig.getString(path, defaultValue);
        }
        if (getConfig() != null && getConfig().contains("messages." + path)) {
            return getConfig().getString("messages." + path, defaultValue);
        }
        return defaultValue;
    }

    public java.util.List<String> getDialogues(String archetype) {
        if (messagesConfig != null && messagesConfig.contains("dialogues.messages." + archetype)) {
            return messagesConfig.getStringList("dialogues.messages." + archetype);
        }
        if (getConfig() != null && getConfig().contains("dialogues.messages." + archetype)) {
            return getConfig().getStringList("dialogues.messages." + archetype);
        }
        return java.util.List.of();
    }

    public org.bukkit.configuration.file.FileConfiguration getTiersConfig() { return tiersConfig; }
    public org.bukkit.configuration.file.FileConfiguration getFuelsConfig() { return fuelsConfig; }
    public org.bukkit.configuration.file.FileConfiguration getUpgradesConfig() { return upgradesConfig; }
    public org.bukkit.configuration.file.FileConfiguration getRecipesConfig() { return recipesConfig; }
    public org.bukkit.configuration.file.FileConfiguration getMessagesConfig() { return messagesConfig; }
    public org.bukkit.configuration.file.FileConfiguration getMinionsConfig() { return minionsConfig; }
}
