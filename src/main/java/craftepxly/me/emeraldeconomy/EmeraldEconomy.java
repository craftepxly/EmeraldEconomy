package craftepxly.me.emeraldeconomy;

import craftepxly.me.emeraldeconomy.command.AdminCommand;
import craftepxly.me.emeraldeconomy.command.EmeraldConverterCommand;
import craftepxly.me.emeraldeconomy.config.ConfigManager;
import craftepxly.me.emeraldeconomy.config.MessageManager;
import craftepxly.me.emeraldeconomy.gui.MenuManager;
import craftepxly.me.emeraldeconomy.listener.ChatListener;
import craftepxly.me.emeraldeconomy.listener.InventoryClickListener;
import craftepxly.me.emeraldeconomy.placeholder.EmeraldEconomyPlaceholderExpansion;
import craftepxly.me.emeraldeconomy.service.EconomyService;
import craftepxly.me.emeraldeconomy.service.PriceManager;
import craftepxly.me.emeraldeconomy.storage.IStorage;
import craftepxly.me.emeraldeconomy.storage.StorageManager;
import craftepxly.me.emeraldeconomy.storage.StorageType;
import craftepxly.me.emeraldeconomy.storage.TransactionLogger;
import craftepxly.me.emeraldeconomy.transaction.TransactionManager;
import craftepxly.me.emeraldeconomy.util.UpdateChecker;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.logging.Level;

/**
 * EmeraldEconomy - Main plugin class
 */
public class EmeraldEconomy extends JavaPlugin {

    /** Singleton instance of the plugin */
    // Singleton instance (accessible via getInstance())
    private static EmeraldEconomy instance;

    // ===== Core Managers =====
    
    /** Handles all configuration file operations */
    // Manages config.yml and menu.yml
    private ConfigManager configManager;
    
    /** Manages multi-language message system */
    // Manages messages_*.yml files
    private MessageManager messageManager;
    
    /** Interfaces with Vault economy API */
    // Handles money deposits/withdrawals via Vault
    private EconomyService economyService;
    
    /** Manages dynamic pricing calculations */
    // Calculates buy/sell prices based on supply/demand
    private PriceManager priceManager;
    
    /** Handles GUI menu creation and updates */
    // Creates and manages inventory GUIs
    private MenuManager menuManager;
    
    /** Processes emerald ↔ money transactions */
    // Handles buy/sell transactions
    private TransactionManager transactionManager;
    
    /** Logs all transactions to file */
    // Writes to transactions.log (YAML storage only)
    private TransactionLogger transactionLogger;
    
    /** Manages storage backend with automatic fallback (YAML/SQLite/MySQL) */
    // Manages storage backend (YAML/SQLite/MySQL)
    private StorageManager storageManager;
    
    /** Listens for chat input (custom amount feature) */
    // Handles custom amount input via chat
    private ChatListener chatListener;

    // ===== Feature Flags =====
    
    /** Whether PlaceholderAPI is enabled and hooked */
    // True if PlaceholderAPI is present and registered
    private boolean placeholderAPIEnabled = false;
    
    /** Whether GeyserMC is detected (Bedrock Edition support) */
    // True if GeyserMC is present
    private boolean geyserEnabled = false;

    /**
     * Called when the plugin is enabled.
     * Initializes all managers, registers listeners, and sets up integrations.
     */
    @Override
    public void onEnable() {
        // Set singleton instance
        instance = this;
        // Record start time for performance measurement
        long startTime = System.currentTimeMillis();

        // Log ASCII banner
        logBanner();

        // 1. Initialize configuration
        if (!initializeConfiguration()) {
            // Failed → disable plugin
            getLogger().severe("Failed to initialize configuration!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 2. Initialize storage
        if (!initializeStorage()) {
            // Failed → disable plugin
            getLogger().severe("Failed to initialize storage!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 3. Hook into Vault economy
        if (!setupEconomy()) {
            // Failed → disable plugin
            getLogger().severe("Vault economy not found! Plugin disabled.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 4. Initialize managers
        initializeManagers();

        // 5. Register listeners
        registerListeners();

        // 6. Register commands
        registerCommands();

        // 7. Optional integrations
        setupOptionalIntegrations();

        // Calculate load time
        long loadTime = System.currentTimeMillis() - startTime;
        // Log success message
        getLogger().info(String.format("Enabled successfully! (took %dms)", loadTime));
        getLogger().info(String.format("Economy: %s | PlaceholderAPI: %s | Geyser: %s",
                economyService.getCurrencyName(),
                placeholderAPIEnabled ? "✓" : "✗",
                geyserEnabled ? "✓" : "✗"));
    }

    /**
     * Called when the plugin is disabled.
     * Saves all data and cleanly shuts down managers.
     */
    @Override
    public void onDisable() {
        // Check if storage manager exists
        if (storageManager != null) {
            // Shutdown storage (saves all data, closes connections)
            storageManager.shutdown();
            // Log success
            getLogger().info("Player statistics saved.");
        }

        // Check if transaction logger exists
        if (transactionLogger != null) {
            // Close logger (flushes remaining queue, closes file)
            transactionLogger.close();
            // Log success
            getLogger().info("Transaction logs flushed.");
        }

        // Log shutdown message
        getLogger().info("EmeraldEconomy disabled successfully.");
    }

    /**
     * Initializes configuration files.
     * Creates default files if they don't exist, loads message locales.
     * 
     * @return true if configuration loaded successfully, false on critical error
     */
    private boolean initializeConfiguration() {
        try {
            // Save default config.yml (if doesn't exist)
            saveDefaultConfig();

            // Initialize managers
            // Create ConfigManager (loads config.yml and menu.yml)
            this.configManager = new ConfigManager(this);
            // Create MessageManager (loads messages_*.yml)
            this.messageManager = new MessageManager(this);

            // Create messages directory if doesn't exist
            File messagesDir = new File(getDataFolder(), "messages");
            if (!messagesDir.exists()) {
                messagesDir.mkdirs();
            }

            // Save default message files (if don't exist)
            saveMessageFile("messages_eng.yml");
            saveMessageFile("messages_id.yml");

            // Return success
            return true;
        } catch (Exception e) {
            // Log error
            getLogger().log(Level.SEVERE, "Failed to initialize configuration", e);
            // Return failure
            return false;
        }
    }

    /**
     * Saves a message file from resources to the plugin folder if it doesn't exist.
     * 
     * @param fileName Name of the message file (e.g., "messages_eng.yml")
     */
    private void saveMessageFile(String fileName) {
        // Create File object for message file
        File file = new File(getDataFolder(), "messages/" + fileName);
        // Check if file already exists
        if (!file.exists()) {
            try (InputStream in = getResource("messages/" + fileName)) {
                // Check if resource exists in JAR
                if (in != null) {
                    // Copy resource to file
                    Files.copy(in, file.toPath());
                    // Log success
                    getLogger().info("Created default " + fileName);
                }
            } catch (Exception e) {
                // Log warning if copy fails
                getLogger().log(Level.WARNING, "Could not save " + fileName, e);
            }
        }
    }

    /**
     * Initialises the storage backend (YAML / SQLite / MySQL) with automatic
     * fallback, and also opens the flat-file transaction log when the active
     * backend is YAML (SQL backends persist transactions in the database directly).
     *
     * @return true if at least one storage backend started successfully
     */
    private boolean initializeStorage() {
        try {
            // Create storage manager
            this.storageManager = new StorageManager(this);
            // Initialize storage (tries configured type, falls back if needed)
            if (!storageManager.initialize()) {
                // All storage types failed → return false
                return false;
            }

            // Only create the flat-file transaction log when YAML storage is active.
            // MySQL and SQLite backends write transactions to their own tables,
            // so creating an extra transactions.log would be redundant.
            // Get active storage type
            StorageType activeType = storageManager.getStorage().getStorageType();
            // Check if YAML
            if (activeType == StorageType.YAML) {
                // Create transaction logger (writes to transactions.log)
                this.transactionLogger = new TransactionLogger(this);
                // Log success
                getLogger().info("File-based transaction log enabled (transactions.log).");
            } else {
                // Log info (SQL backends log to database)
                getLogger().info("Transaction logging handled by " + activeType + " database — skipping transactions.log.");
            }

            // Return success
            return true;
        } catch (Exception e) {
            // Log error
            getLogger().log(Level.SEVERE, "Failed to initialize storage", e);
            // Return failure
            return false;
        }
    }

    /**
     * Sets up Vault economy integration.
     * 
     * @return true if Vault and economy plugin found, false otherwise
     */
    private boolean setupEconomy() {
        // Check if Vault plugin is present
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            // Vault not found → return false
            return false;
        }

        // Create economy service
        this.economyService = new EconomyService(this);
        // Setup economy (hooks into Vault)
        return economyService.setupEconomy();
    }

    /**
     * Initializes all core managers in the correct order.
     * Price manager must be initialized before transaction manager.
     */
    private void initializeManagers() {
        // Create price manager (handles dynamic pricing)
        this.priceManager = new PriceManager(this);
        // Create transaction manager (handles buy/sell)
        this.transactionManager = new TransactionManager(this);
        // Create menu manager (handles GUI menus)
        this.menuManager = new MenuManager(this);
        // Create chat listener (handles custom amount input)
        this.chatListener = new ChatListener(this);

        // Log success
        getLogger().info("Core managers initialized.");
    }

    /**
     * Registers event listeners for inventory clicks and chat input.
     */
    private void registerListeners() {
        // Register inventory click listener (handles GUI clicks)
        getServer().getPluginManager().registerEvents(new InventoryClickListener(this), this);
        // Register chat listener (handles custom amount input)
        getServer().getPluginManager().registerEvents(chatListener, this);

        // Log success
        getLogger().info("Event listeners registered.");
    }

    /**
     * Registers plugin commands (/ec and /ecadmin).
     */
    private void registerCommands() {
        // Register /emeraldconverter command
        getCommand("emeraldconverter").setExecutor(new EmeraldConverterCommand(this));
        // Register /ecadmin command
        getCommand("ecadmin").setExecutor(new AdminCommand(this));

        // Log success
        getLogger().info("Commands registered.");
    }

    /**
     * Sets up optional plugin integrations (PlaceholderAPI, GeyserMC).
     * These are soft dependencies and won't prevent the plugin from loading.
     */
    private void setupOptionalIntegrations() {
        // PlaceholderAPI integration
        // Check if PlaceholderAPI plugin is present
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            // Register placeholder expansion
            new EmeraldEconomyPlaceholderExpansion(this).register();
            // Set flag
            placeholderAPIEnabled = true;
            // Log success
            getLogger().info("PlaceholderAPI integration enabled! (40 placeholders registered)");
        }

        // GeyserMC detection (for Bedrock Edition compatibility)
        // Check if Geyser-Spigot plugin is present
        if (getServer().getPluginManager().getPlugin("Geyser-Spigot") != null) {
            // Set flag
            geyserEnabled = true;
            // Log success
            getLogger().info("GeyserMC detected - Bedrock Edition support enabled!");
        }

        // Update checker (async)
        // Create update checker and check for updates
        new UpdateChecker(this).checkForUpdates();
    }

    /**
     * Logs the plugin banner to console on startup.
     * Displays ASCII art logo and version information.
     */
    private void logBanner() {
        // Log ASCII art banner
        getLogger().info("╔═══════════════════════════════════╗");
        getLogger().info("║                                   ║");
        getLogger().info("║   EmeraldEconomy                  ║");
        getLogger().info("║   Advanced Emerald Converter      ║");
        getLogger().info("║                                   ║");
        getLogger().info("║   Author: CraftePxly              ║");
        getLogger().info("║                                   ║");
        getLogger().info("╚═══════════════════════════════════╝");
    }

    // ===== Getters for Managers =====

    /**
     * Gets the singleton instance of the plugin.
     * 
     * @return Plugin instance
     */
    public static EmeraldEconomy getInstance() {
        // Return singleton instance
        return instance;
    }

    /**
     * Gets the configuration manager.
     * 
     * @return ConfigManager instance
     */
    public ConfigManager getConfigManager() {
        return configManager;
    }

    /**
     * Gets the message manager for multi-language support.
     * 
     * @return MessageManager instance
     */
    public MessageManager getMessageManager() {
        return messageManager;
    }

    /**
     * Gets the economy service (Vault integration).
     * 
     * @return EconomyService instance
     */
    public EconomyService getEconomyService() {
        return economyService;
    }

    /**
     * Gets the price manager for dynamic pricing calculations.
     * 
     * @return PriceManager instance
     */
    public PriceManager getPriceManager() {
        return priceManager;
    }

    /**
     * Gets the menu manager for GUI operations.
     * 
     * @return MenuManager instance
     */
    public MenuManager getMenuManager() {
        return menuManager;
    }

    /**
     * Gets the transaction manager for emerald ↔ money conversions.
     * 
     * @return TransactionManager instance
     */
    public TransactionManager getTransactionManager() {
        return transactionManager;
    }

    /**
     * Gets the transaction logger for audit trails.
     * 
     * @return TransactionLogger instance
     */
    public TransactionLogger getTransactionLogger() {
        return transactionLogger;
    }

    /**
     * Gets the active storage backend (YAML / SQLite / MySQL).
     *
     * @return Active {@link IStorage} instance
     */
    public IStorage getPlayerStatsStorage() {
        // Return active storage from storage manager
        return storageManager.getStorage();
    }

    /**
     * Gets the storage manager that controls backend selection and fallback.
     *
     * @return StorageManager instance
     */
    public StorageManager getStorageManager() {
        return storageManager;
    }

    /**
     * Gets the chat listener for custom amount input.
     * 
     * @return ChatListener instance
     */
    public ChatListener getChatListener() {
        return chatListener;
    }

    // ===== Feature Flag Getters =====

    /**
     * Checks if PlaceholderAPI is enabled.
     * 
     * @return true if PlaceholderAPI is hooked, false otherwise
     */
    public boolean isPlaceholderAPIEnabled() {
        return placeholderAPIEnabled;
    }

    /**
     * Checks if GeyserMC is detected (Bedrock Edition support).
     * 
     * @return true if GeyserMC is present, false otherwise
     */
    public boolean isGeyserEnabled() {
        return geyserEnabled;
    }

    /**
     * Reloads all configuration files.
     * Used by /ecadmin reload command.
     */
    public void reloadConfigurations() {
        // Reload config.yml from disk
        reloadConfig();
        // Recreate config manager (re-reads config.yml and menu.yml)
        configManager = new ConfigManager(this);
        // Recreate message manager (re-reads messages_*.yml)
        messageManager = new MessageManager(this);
        // Reload price manager configuration
        priceManager.loadConfiguration();
        // Reload menus
        menuManager.loadMenus();
        // Log success
        getLogger().info("Configuration reloaded successfully.");
    }
}