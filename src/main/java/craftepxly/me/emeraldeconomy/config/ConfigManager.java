package craftepxly.me.emeraldeconomy.config;

import craftepxly.me.emeraldeconomy.EmeraldEconomy;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

/**
 * ConfigManager — manages configuration files (config.yml, menu.yml).
 */
public class ConfigManager {
    
    // Reference to main plugin instance
    private final EmeraldEconomy plugin;
    // Main config.yml loaded into memory
    private FileConfiguration config;
    // Menu config (menu.yml) loaded into memory
    private FileConfiguration menuConfig;
    
    /**
     * Constructs a new ConfigManager and loads all config files.
     * 
     * @param plugin The main EmeraldEconomy plugin instance
     */
    public ConfigManager(EmeraldEconomy plugin) {
        // Store plugin reference in memory
        this.plugin = plugin;
        // Load all configuration files
        loadConfigs();
    }
    
    /**
     * Loads all configuration files from disk.
     * Reloads main config and menu config.
     */
    public void loadConfigs() {
        // Reload main config from config.yml
        plugin.reloadConfig();
        // Get config instance from plugin
        config = plugin.getConfig();
        
        // Load menu config from menu.yml
        // Create File object for menu.yml
        File menuFile = new File(plugin.getDataFolder(), "menu.yml");
        // Check if menu.yml doesn't exist
        if (!menuFile.exists()) {
            // Copy default menu.yml from JAR resources to disk
            plugin.saveResource("menu.yml", false);
        }
        // Load menu.yml into memory
        menuConfig = YamlConfiguration.loadConfiguration(menuFile);
    }
    
    /**
     * Saves the main config (config.yml) to disk.
     */
    public void saveConfig() {
        // Write config.yml to disk
        plugin.saveConfig();
    }
    
    /**
     * Saves the menu config (menu.yml) to disk.
     */
    public void saveMenuConfig() {
        // Create File object for menu.yml
        File menuFile = new File(plugin.getDataFolder(), "menu.yml");
        try {
            // Write menuConfig to disk
            menuConfig.save(menuFile);
        } catch (IOException e) {
            // Log error if save fails
            plugin.getLogger().severe("Could not save menu.yml: " + e.getMessage());
        }
    }
    
    /**
     * Gets the main config (config.yml) instance.
     * 
     * @return FileConfiguration for config.yml
     */
    public FileConfiguration getConfig() {
        return config;
    }
    
    /**
     * Gets the menu config (menu.yml) instance.
     * 
     * @return FileConfiguration for menu.yml
     */
    public FileConfiguration getMenuConfig() {
        return menuConfig;
    }
    
    // =========================================================================
    // Convenience methods for commonly used config values
    // These methods provide type-safe access to config.yml settings
    // =========================================================================
    
    /**
     * Gets the base buy price (what server pays player per emerald).
     * 
     * @return Buy price (default: 9.5)
     */
    public double getBuyPrice() {
        // Read prices.buy from config (default 9.5 if not found)
        return config.getDouble("prices.buy", 9.5);
    }
    
    /**
     * Gets the base sell price (what player pays server per emerald).
     * 
     * @return Sell price (default: 10.0)
     */
    public double getSellPrice() {
        // Read prices.sell from config (default 10.0 if not found)
        return config.getDouble("prices.sell", 10.0);
    }
    
    /**
     * Gets the currency name (e.g., "Dollar", "Emerald").
     * 
     * @return Currency name (default: "Dollar")
     */
    public String getCurrencyName() {
        // Read currency.name from config (default "Dollar" if not found)
        return config.getString("currency.name", "Dollar");
    }
    
    /**
     * Gets the currency symbol (e.g., "$", "€", "Rp").
     * 
     * @return Currency symbol (default: "$")
     */
    public String getCurrencySymbol() {
        // Read currency.symbol from config (default "$" if not found)
        return config.getString("currency.symbol", "$");
    }
    
    /**
     * Checks if dynamic pricing is enabled.
     * 
     * @return true if dynamic pricing enabled, false otherwise
     */
    public boolean isDynamicPricingEnabled() {
        // Read dynamic_pricing.enabled from config (default true)
        return config.getBoolean("dynamic_pricing.enabled", true);
    }
    
    /**
     * Gets the dynamic pricing time window in seconds.
     * Only transactions within this window affect prices.
     * 
     * @return Window in seconds (default: 300 = 5 minutes)
     */
    public int getDynamicPricingWindow() {
        // Read dynamic_pricing.window_seconds from config (default 300)
        return config.getInt("dynamic_pricing.window_seconds", 300);
    }
    
    /**
     * Gets the minimum price per emerald.
     * 
     * @return Minimum price (default: 1.0)
     */
    public double getMinPrice() {
        // Read dynamic_pricing.min_price from config (default 1.0)
        return config.getDouble("dynamic_pricing.min_price", 1.0);
    }
    
    /**
     * Gets the maximum price per emerald.
     * 
     * @return Maximum price (default: 1000.0)
     */
    public double getMaxPrice() {
        // Read dynamic_pricing.max_price from config (default 1000.0)
        return config.getDouble("dynamic_pricing.max_price", 1000.0);
    }
    
    /**
     * Gets the price update interval in seconds.
     * 
     * @return Update interval (default: 5 seconds)
     */
    public int getUpdateInterval() {
        // Read dynamic_pricing.update_interval from config (default 5)
        return config.getInt("dynamic_pricing.update_interval", 5);
    }
    
    /**
     * Gets the transaction cooldown in seconds.
     * 
     * @return Cooldown in seconds (default: 3)
     */
    public int getTransactionCooldown() {
        // Read transaction.cooldown from config (default 3)
        return config.getInt("transaction.cooldown", 3);
    }
    
    /**
     * Checks if rate limiting is enabled.
     * 
     * @return true if rate limiting enabled, false otherwise
     */
    public boolean isRateLimitEnabled() {
        // Read transaction.rate_limit.enabled from config (default true)
        return config.getBoolean("transaction.rate_limit.enabled", true);
    }
    
    /**
     * Gets the maximum transactions per player per minute.
     * 
     * @return Max transactions per minute (default: 20)
     */
    public int getMaxTransactionsPerMinute() {
        // Read transaction.rate_limit.max_per_minute from config (default 20)
        return config.getInt("transaction.rate_limit.max_per_minute", 20);
    }
    
    /**
     * Checks if strict emerald checking is enabled.
     * When enabled, only accepts genuine emeralds (no renamed items).
     * 
     * @return true if strict checking enabled, false otherwise
     */
    public boolean isStrictEmeraldCheck() {
        // Read security.strict_emerald_check from config (default true)
        return config.getBoolean("security.strict_emerald_check", true);
    }
    
    /**
     * Checks if debug mode is enabled.
     * 
     * @return true if debug mode enabled, false otherwise
     */
    public boolean isDebugEnabled() {
        // Read debug from config (default false)
        return config.getBoolean("debug", false);
    }
}