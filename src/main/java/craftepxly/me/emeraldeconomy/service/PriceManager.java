package craftepxly.me.emeraldeconomy.service;

import craftepxly.me.emeraldeconomy.EmeraldEconomy;
import craftepxly.me.emeraldeconomy.transaction.TransactionType;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.Deque;
import java.util.LinkedList;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * PriceManager — advanced dynamic pricing system with supply-demand economics.
 */
public class PriceManager {
    
    // Reference to main plugin instance
    private final EmeraldEconomy plugin;
    // Thread-safe lock for reading/writing price data
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    
    // Base prices from config (starting values)
    private double baseBuyPrice;
    private double baseSellPrice;
    
    // Current dynamic prices (updated by algorithm)
    private volatile double currentBuyPrice;
    private volatile double currentSellPrice;
    
    // Price bounds (prevent extreme values)
    private double minPrice;
    private double maxPrice;
    
    // Transaction tracking (recent transactions within time window)
    private final Deque<Transaction> recentTransactions = new LinkedList<>();
    // Time window in seconds (e.g., 300 = 5 minutes)
    private int windowSeconds;
    
    // EWMA (Exponential Weighted Moving Average) for smoothing
    private double demandEWMA = 0.0;  // Smoothed buy volume
    private double supplyEWMA = 0.0;  // Smoothed sell volume
    // Smoothing factor (0-1, higher = more weight on recent data)
    private final double ewmaAlpha = 0.3;
    
    // Resource depletion (simulates mining difficulty)
    private long totalEmeraldsConverted = 0;  // Total emeralds converted globally
    private double depletionFactor = 1.0;     // Current depletion factor (1.0 = no depletion)
    
    // Transaction tax (money sink mechanism)
    private double transactionTaxRate;
    
    // Dynamic pricing parameters (from config)
    private boolean dynamicEnabled;           // Is dynamic pricing enabled?
    private double demandSensitivity;         // How much demand affects price
    private double supplySensitivity;         // How much supply affects price
    private double depletionRate;             // How fast depletion increases
    private long depletionRecoveryPeriod;     // Time for depletion to recover (seconds)
    private int maxImpactPerTransaction;      // Max emeralds counted per transaction (anti-manipulation)
    
    // Update task (runs periodically to recalculate prices)
    private BukkitTask updateTask;
    private int updateInterval;
    
    /**
     * Constructs a new PriceManager and loads configuration.
     * 
     * @param plugin The main EmeraldEconomy plugin instance
     */
    public PriceManager(EmeraldEconomy plugin) {
        // Store plugin reference in memory
        this.plugin = plugin;
        // Load configuration from config.yml
        loadConfiguration();
    }
    
    /**
     * Loads configuration values from config.yml.
     * Called on plugin startup and on /ecadmin reload.
     */
    public void loadConfiguration() {
        // Acquire write lock (blocks all readers until done)
        lock.writeLock().lock();
        try {
            // Load base prices from config
            baseBuyPrice = plugin.getConfigManager().getBuyPrice();
            baseSellPrice = plugin.getConfigManager().getSellPrice();
            
            // Initialize current prices to base prices
            currentBuyPrice = baseBuyPrice;
            currentSellPrice = baseSellPrice;
            
            // Load price bounds from config
            minPrice = plugin.getConfig().getDouble("dynamic_pricing.min_price", 1.0);
            maxPrice = plugin.getConfig().getDouble("dynamic_pricing.max_price", 1000.0);
            
            // Load dynamic pricing settings
            dynamicEnabled = plugin.getConfig().getBoolean("dynamic_pricing.enabled", true);
            windowSeconds = plugin.getConfig().getInt("dynamic_pricing.window_seconds", 300);
            updateInterval = plugin.getConfig().getInt("dynamic_pricing.update_interval", 5);
            
            // Load advanced settings
            demandSensitivity = plugin.getConfig().getDouble("dynamic_pricing.demand_sensitivity", 0.02);
            supplySensitivity = plugin.getConfig().getDouble("dynamic_pricing.supply_sensitivity", 0.02);
            depletionRate = plugin.getConfig().getDouble("dynamic_pricing.depletion_rate", 0.0001);
            depletionRecoveryPeriod = plugin.getConfig().getLong("dynamic_pricing.depletion_recovery_seconds", 3600);
            maxImpactPerTransaction = plugin.getConfig().getInt("dynamic_pricing.max_impact_per_transaction", 100);
            transactionTaxRate = plugin.getConfig().getDouble("dynamic_pricing.transaction_tax_rate", 0.05);
            
            // Log configuration status
            plugin.getLogger().info("Dynamic pricing loaded: " + (dynamicEnabled ? "ENABLED" : "DISABLED"));
            plugin.getLogger().info("Tax rate: " + (transactionTaxRate * 100) + "%");
        } finally {
            // Release write lock (allow readers again)
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Records a transaction for dynamic pricing calculations.
     * Adds transaction to recent history and updates totals.
     * 
     * @param type   Transaction type (BUY or SELL)
     * @param amount Emerald amount
     * @param price  Price at time of transaction
     */
    public void recordTransaction(TransactionType type, int amount, double price) {
        // Skip if dynamic pricing is disabled
        if (!dynamicEnabled) return;
        
        // Acquire write lock (thread-safe modification)
        lock.writeLock().lock();
        try {
            // Cap impact per transaction to prevent manipulation
            int effectiveAmount = Math.min(amount, maxImpactPerTransaction);
            
            // Create transaction record with current timestamp
            Transaction tx = new Transaction(type, effectiveAmount, price, System.currentTimeMillis());
            // Add to end of deque (most recent)
            recentTransactions.addLast(tx);
            
            // Update total converted for depletion calculation
            totalEmeraldsConverted += amount;
            
            // Remove old transactions outside time window
            cleanupOldTransactions();
            
        } finally {
            // Release write lock
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Removes transactions older than the configured time window.
     * Called after each transaction recording.
     */
    private void cleanupOldTransactions() {
        // Calculate cutoff time (current time - window duration)
        long cutoff = System.currentTimeMillis() - (windowSeconds * 1000L);
        // Remove transactions from front of deque until we hit a recent one
        while (!recentTransactions.isEmpty() && recentTransactions.peekFirst().timestamp < cutoff) {
            // Remove oldest transaction
            recentTransactions.pollFirst();
        }
    }
    
    /**
     * Updates dynamic prices based on recent market activity.
     * Called periodically by update task (default: every 5 seconds).
     */
    public void updatePrices() {
        // Skip if dynamic pricing is disabled
        if (!dynamicEnabled) return;
        
        // Acquire write lock (thread-safe price update)
        lock.writeLock().lock();
        try {
            // Remove old transactions first
            cleanupOldTransactions();
            
            // Calculate demand and supply metrics
            int buyTransactions = 0;   // Number of buy transactions
            int sellTransactions = 0;  // Number of sell transactions
            int buyVolume = 0;         // Total emeralds bought
            int sellVolume = 0;        // Total emeralds sold
            
            // Loop through recent transactions
            for (Transaction tx : recentTransactions) {
                if (tx.type == TransactionType.BUY) {
                    // Player bought emeralds (demand)
                    buyTransactions++;
                    buyVolume += tx.amount;
                } else {
                    // Player sold emeralds (supply)
                    sellTransactions++;
                    sellVolume += tx.amount;
                }
            }
            
            // Update EWMA for smoothing (prevents sudden price spikes)
            double rawDemand = buyVolume;
            double rawSupply = sellVolume;
            
            // EWMA formula: new_value = alpha * raw + (1 - alpha) * old_value
            demandEWMA = ewmaAlpha * rawDemand + (1 - ewmaAlpha) * demandEWMA;
            supplyEWMA = ewmaAlpha * rawSupply + (1 - ewmaAlpha) * supplyEWMA;
            
            // Calculate resource depletion factor
            updateDepletionFactor();
            
            // Calculate new prices using supply-demand economics
            double demandPressure = demandEWMA * demandSensitivity * depletionFactor;
            double supplyPressure = supplyEWMA * supplySensitivity;
            
            // Buy price: increases with demand, decreases with depletion
            double newBuyPrice = baseBuyPrice + demandPressure - (baseBuyPrice * (1 - depletionFactor) * 0.5);
            
            // Sell price: decreases with supply, slightly higher than buy (markup)
            double newSellPrice = baseSellPrice - supplyPressure + (baseSellPrice * 0.05);
            
            // Clamp prices within configured bounds
            newBuyPrice = clamp(newBuyPrice, minPrice, maxPrice);
            newSellPrice = clamp(newSellPrice, minPrice, maxPrice);
            
            // Ensure sell >= buy (prevent arbitrage)
            if (newSellPrice < newBuyPrice) {
                // Calculate midpoint
                double mid = (newBuyPrice + newSellPrice) / 2;
                // Set buy 5% below mid, sell 5% above mid
                newBuyPrice = mid * 0.95;
                newSellPrice = mid * 1.05;
            }
            
            // Update current prices (volatile fields, visible to all threads)
            currentBuyPrice = newBuyPrice;
            currentSellPrice = newSellPrice;
            
        } finally {
            // Release write lock
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Updates the resource depletion factor.
     * Simulates mining difficulty increasing over time.
     */
    private void updateDepletionFactor() {
        // Depletion: more conversions = lower buying pressure (resource scarcity)
        // Recovery: gradually recovers over time (simulates new mining)
        
        // Time since server started (in seconds)
        long timeSinceStart = System.currentTimeMillis() / 1000;
        // Recovery factor (0.0 to 1.0, increases over time)
        double recoveryFactor = Math.min(1.0, (double) timeSinceStart / depletionRecoveryPeriod);
        
        // Calculate depletion amount (more conversions = more depletion)
        double depletionAmount = totalEmeraldsConverted * depletionRate;
        // Calculate depletion factor (1.0 = no depletion, 0.1 = max depletion)
        // Factor = 1.0 - depletion + (recovery * 0.5)
        depletionFactor = Math.max(0.1, 1.0 - depletionAmount + (recoveryFactor * 0.5));
    }
    
    /**
     * Clamps a value between min and max bounds.
     * 
     * @param value The value to clamp
     * @param min   Minimum bound
     * @param max   Maximum bound
     * @return Clamped value
     */
    private double clamp(double value, double min, double max) {
        // Return max of (min, min of (value, max))
        return Math.max(min, Math.min(max, value));
    }
    
    /**
     * Gets the current buy price (what server pays to players for emeralds).
     * Thread-safe read.
     * 
     * @return Current buy price per emerald
     */
    public double getBuyPrice() {
        // Acquire read lock (allows multiple concurrent readers)
        lock.readLock().lock();
        try {
            // Return current buy price
            return currentBuyPrice;
        } finally {
            // Release read lock
            lock.readLock().unlock();
        }
    }
    
    /**
     * Gets the current sell price (what players pay to server for emeralds).
     * Thread-safe read.
     * 
     * @return Current sell price per emerald
     */
    public double getSellPrice() {
        // Acquire read lock
        lock.readLock().lock();
        try {
            // Return current sell price
            return currentSellPrice;
        } finally {
            // Release read lock
            lock.readLock().unlock();
        }
    }
    
    /**
     * Gets the base buy price (before dynamic adjustments).
     * 
     * @return Base buy price
     */
    public double getBaseBuyPrice() {
        return baseBuyPrice;
    }
    
    /**
     * Gets the base sell price (before dynamic adjustments).
     * 
     * @return Base sell price
     */
    public double getBaseSellPrice() {
        return baseSellPrice;
    }
    
    /**
     * Calculates transaction tax for a given amount.
     * 
     * @param amount The transaction amount
     * @return Tax amount
     */
    public double getTransactionTax(double amount) {
        // Tax = amount * tax rate (e.g., 100 * 0.05 = 5)
        return amount * transactionTaxRate;
    }
    
    /**
     * Gets the transaction tax rate.
     * 
     * @return Tax rate (e.g., 0.05 = 5%)
     */
    public double getTransactionTaxRate() {
        return transactionTaxRate;
    }
    
    /**
     * Gets the recent transaction volume within the time window.
     * Thread-safe read.
     * 
     * @return Total emeralds transacted recently
     */
    public int getRecentVolume() {
        // Acquire read lock
        lock.readLock().lock();
        try {
            // Initialize volume counter
            int volume = 0;
            // Sum up all transaction amounts
            for (Transaction tx : recentTransactions) {
                volume += tx.amount;
            }
            // Return total volume
            return volume;
        } finally {
            // Release read lock
            lock.readLock().unlock();
        }
    }
    
    /**
     * Gets the current depletion factor.
     * 
     * @return Depletion factor (0.1 to 1.0)
     */
    public double getDepletionFactor() {
        return depletionFactor;
    }
    
    /**
     * Gets the total emeralds converted globally.
     * 
     * @return Total emeralds converted
     */
    public long getTotalEmeraldsConverted() {
        return totalEmeraldsConverted;
    }
    
    /**
     * Starts the dynamic pricing update task.
     * Runs periodically to recalculate prices.
     */
    public void startDynamicPricing() {
        // Skip if disabled
        if (!dynamicEnabled) {
            plugin.getLogger().info("Dynamic pricing is disabled in config");
            return;
        }
        
        // Cancel existing task if running
        if (updateTask != null) {
            updateTask.cancel();
        }
        
        // Schedule repeating async task (runs every updateInterval seconds)
        updateTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, 
            this::updatePrices,           // Method to run
            20L * updateInterval,         // Initial delay (ticks)
            20L * updateInterval          // Period (ticks)
        );
        
        // Log startup message
        plugin.getLogger().info("Dynamic pricing started (update every " + updateInterval + "s)");
    }
    
    /**
     * Stops the dynamic pricing update task.
     */
    public void stopDynamicPricing() {
        // Check if task exists
        if (updateTask != null) {
            // Cancel task
            updateTask.cancel();
            // Clear reference
            updateTask = null;
        }
    }
    
    /**
     * Transaction — internal class for storing transaction records.
     */
    private static class Transaction {
        // Transaction type (BUY or SELL)
        final TransactionType type;
        // Emerald amount
        final int amount;
        // Price at time of transaction
        final double price;
        // Timestamp in milliseconds
        final long timestamp;
        
        /**
         * Constructs a new transaction record.
         * 
         * @param type      Transaction type
         * @param amount    Emerald amount
         * @param price     Price at time
         * @param timestamp Timestamp
         */
        Transaction(TransactionType type, int amount, double price, long timestamp) {
            // Store all fields in memory
            this.type = type;
            this.amount = amount;
            this.price = price;
            this.timestamp = timestamp;
        }
    }
}