package craftepxly.me.emeraldeconomy.service;

import craftepxly.me.emeraldeconomy.EmeraldEconomy;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.logging.Level;

/**
 * EconomyService — manages Vault economy integration.
 */
public class EconomyService {
    
    // Reference to main plugin instance
    private final EmeraldEconomy plugin;
    // Vault economy provider (e.g., EssentialsX, CMI)
    private Economy economy;
    
    /**
     * Constructs a new EconomyService.
     * 
     * @param plugin The main EmeraldEconomy plugin instance
     */
    public EconomyService(EmeraldEconomy plugin) {
        // Store plugin reference in memory
        this.plugin = plugin;
    }
    
    /**
     * Sets up Vault economy integration.
     * Searches for an economy provider and hooks into it.
     * 
     * @return true if economy was found and hooked, false otherwise
     */
    public boolean setupEconomy() {
        // Check if Vault plugin is installed
        if (Bukkit.getServer().getPluginManager().getPlugin("Vault") == null) {
            // Vault not found → log error and return false
            plugin.getLogger().severe("Vault not found!");
            return false;
        }
        
        // Get the economy service provider from Vault
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServer()
                .getServicesManager()
                .getRegistration(Economy.class);
        
        // Check if an economy plugin is registered
        if (rsp == null) {
            // No economy plugin found → log error and return false
            plugin.getLogger().severe("No economy plugin found! Please install EssentialsX or similar.");
            return false;
        }
        
        // Get the economy provider instance (e.g., EssentialsX Economy)
        economy = rsp.getProvider();
        // Log successful hook with economy name
        plugin.getLogger().info("Hooked into economy: " + economy.getName());
        // Return true if economy is not null
        return economy != null;
    }
    
    /**
     * Gets the Vault Economy instance.
     * 
     * @return The Economy instance
     */
    public Economy getEconomy() {
        // Return the stored economy provider
        return economy;
    }
    
    /**
     * Checks if a player has an economy account.
     * 
     * @param player The player to check
     * @return true if player has an account, false otherwise
     */
    public boolean hasAccount(OfflinePlayer player) {
        // Call Vault's hasAccount method
        return economy.hasAccount(player);
    }
    
    /**
     * Gets a player's balance.
     * 
     * @param player The player
     * @return The player's balance, or 0.0 if account doesn't exist
     */
    public double getBalance(OfflinePlayer player) {
        // Check if account exists
        if (!hasAccount(player)) {
            // No account → return 0.0
            return 0.0;
        }
        // Return balance from Vault
        return economy.getBalance(player);
    }
    
    /**
     * Checks if a player has at least a certain amount of money.
     * 
     * @param player The player
     * @param amount The amount to check
     * @return true if player has at least this amount, false otherwise
     */
    public boolean has(OfflinePlayer player, double amount) {
        // Compare balance to required amount
        return getBalance(player) >= amount;
    }
    
    /**
     * Withdraws money from a player's account.
     * 
     * @param player The player
     * @param amount The amount to withdraw
     * @return true if withdrawal was successful, false otherwise
     */
    public boolean withdraw(OfflinePlayer player, double amount) {
        // Check if player has an account
        if (!hasAccount(player)) {
            // No account → log warning and return false
            plugin.getLogger().warning("Player " + player.getName() + " has no economy account!");
            return false;
        }
        
        // Check if player has enough money
        if (!has(player, amount)) {
            // Not enough money → return false
            return false;
        }
        
        try {
            // Attempt to withdraw money via Vault
            var response = economy.withdrawPlayer(player, amount);
            // Check if transaction was successful
            if (response.transactionSuccess()) {
                // Success → log if debug enabled
                if (plugin.getConfigManager().isDebugEnabled()) {
                    plugin.getLogger().info("Withdrew " + amount + " from " + player.getName());
                }
                return true;
            } else {
                // Failed → log error message from Vault
                plugin.getLogger().warning("Failed to withdraw from " + player.getName() + ": " + response.errorMessage);
                return false;
            }
        } catch (Exception e) {
            // Exception occurred → log error with stack trace
            plugin.getLogger().log(Level.SEVERE, "Error withdrawing money from " + player.getName(), e);
            return false;
        }
    }
    
    /**
     * Deposits money to a player's account.
     * Creates account if it doesn't exist.
     * 
     * @param player The player
     * @param amount The amount to deposit
     * @return true if deposit was successful, false otherwise
     */
    public boolean deposit(OfflinePlayer player, double amount) {
        // Check if player has an account
        if (!hasAccount(player)) {
            // Try to create account
            if (!economy.createPlayerAccount(player)) {
                // Account creation failed → log warning and return false
                plugin.getLogger().warning("Failed to create account for " + player.getName());
                return false;
            }
        }
        
        try {
            // Attempt to deposit money via Vault
            var response = economy.depositPlayer(player, amount);
            // Check if transaction was successful
            if (response.transactionSuccess()) {
                // Success → log if debug enabled
                if (plugin.getConfigManager().isDebugEnabled()) {
                    plugin.getLogger().info("Deposited " + amount + " to " + player.getName());
                }
                return true;
            } else {
                // Failed → log error message from Vault
                plugin.getLogger().warning("Failed to deposit to " + player.getName() + ": " + response.errorMessage);
                return false;
            }
        } catch (Exception e) {
            // Exception occurred → log error with stack trace
            plugin.getLogger().log(Level.SEVERE, "Error depositing money to " + player.getName(), e);
            return false;
        }
    }
    
    /**
     * Formats an amount as currency string.
     * 
     * @param amount The amount to format
     * @return Formatted string (e.g., "$100.00")
     */
    public String format(double amount) {
        // Call Vault's format method
        return economy.format(amount);
    }
    
    /**
     * Gets the currency name (plural form).
     * 
     * @return Currency name (e.g., "Dollars")
     */
    public String getCurrencyName() {
        // Call Vault's currencyNamePlural method
        return economy.currencyNamePlural();
    }
}