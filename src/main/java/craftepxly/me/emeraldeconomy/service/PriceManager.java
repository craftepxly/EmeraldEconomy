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
 *
 * NAMING CONVENTION (player perspective):
 *   getBuyPrice()  = price player PAYS when buying emeralds from server  (player spends money)
 *   getSellPrice() = price player RECEIVES when selling emeralds to server (player gets money)
 *
 * Invariant: buyPrice >= sellPrice  (server always keeps a spread to prevent arbitrage)
 */
public class PriceManager {

    // Reference to main plugin instance
    private final EmeraldEconomy plugin;
    // Thread-safe lock for reading/writing price data
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    // Base prices from config (starting values, player-perspective)
    // baseBuyPrice  = config prices.buy  = what player PAYS when buying
    // baseSellPrice = config prices.sell = what player RECEIVES when selling
    private double baseBuyPrice;
    private double baseSellPrice;

    // Current dynamic prices (updated by algorithm, player-perspective)
    // currentBuyPrice  = what player currently PAYS when buying
    // currentSellPrice = what player currently RECEIVES when selling
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
    private double demandEWMA = 0.0;  // Smoothed buy volume (player buying activity)
    private double supplyEWMA = 0.0;  // Smoothed sell volume (player selling activity)
    // Smoothing factor (0-1, higher = more weight on recent data)
    private final double ewmaAlpha = 0.3;

    // Resource depletion (simulates mining difficulty)
    private long totalEmeraldsConverted = 0;  // Total emeralds transacted globally
    private double depletionFactor = 1.0;     // Current depletion factor (1.0 = no depletion)

    // Transaction tax (money sink mechanism)
    private double transactionTaxRate;

    // Dynamic pricing parameters (from config)
    private boolean dynamicEnabled;           // Is dynamic pricing enabled?
    private double demandSensitivity;         // How much player buying demand raises the buy price
    private double supplySensitivity;         // How much player selling supply lowers the sell price
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
        this.plugin = plugin;
        loadConfiguration();
    }

    /**
     * Loads configuration values from config.yml.
     * Called on plugin startup and on /ecadmin reload.
     */
    public void loadConfiguration() {
        lock.writeLock().lock();
        try {
            // Load base prices from config (player-perspective)
            // prices.buy  → what player PAYS when buying emeralds
            // prices.sell → what player RECEIVES when selling emeralds
            baseBuyPrice  = plugin.getConfigManager().getBuyPrice();
            baseSellPrice = plugin.getConfigManager().getSellPrice();

            // Initialize current prices to base prices
            currentBuyPrice  = baseBuyPrice;
            currentSellPrice = baseSellPrice;

            minPrice = plugin.getConfig().getDouble("dynamic_pricing.min_price", 1.0);
            maxPrice = plugin.getConfig().getDouble("dynamic_pricing.max_price", 1000.0);

            dynamicEnabled  = plugin.getConfig().getBoolean("dynamic_pricing.enabled", true);
            windowSeconds   = plugin.getConfig().getInt("dynamic_pricing.window_seconds", 300);
            updateInterval  = plugin.getConfig().getInt("dynamic_pricing.update_interval", 5);

            demandSensitivity       = plugin.getConfig().getDouble("dynamic_pricing.demand_sensitivity", 0.02);
            supplySensitivity       = plugin.getConfig().getDouble("dynamic_pricing.supply_sensitivity", 0.02);
            depletionRate           = plugin.getConfig().getDouble("dynamic_pricing.depletion_rate", 0.0001);
            depletionRecoveryPeriod = plugin.getConfig().getLong("dynamic_pricing.depletion_recovery_seconds", 3600);
            maxImpactPerTransaction = plugin.getConfig().getInt("dynamic_pricing.max_impact_per_transaction", 100);
            transactionTaxRate      = plugin.getConfig().getDouble("dynamic_pricing.transaction_tax_rate", 0.05);

            plugin.getLogger().info("Dynamic pricing loaded: " + (dynamicEnabled ? "ENABLED" : "DISABLED"));
            plugin.getLogger().info("Tax rate: " + (transactionTaxRate * 100) + "%");
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Records a transaction for dynamic pricing calculations.
     *
     * @param type   BUY = player bought emeralds, SELL = player sold emeralds
     * @param amount Emerald amount
     * @param price  Price at time of transaction
     */
    public void recordTransaction(TransactionType type, int amount, double price) {
        if (!dynamicEnabled) return;

        lock.writeLock().lock();
        try {
            int effectiveAmount = Math.min(amount, maxImpactPerTransaction);
            Transaction tx = new Transaction(type, effectiveAmount, price, System.currentTimeMillis());
            recentTransactions.addLast(tx);
            totalEmeraldsConverted += amount;
            cleanupOldTransactions();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Removes transactions older than the configured time window.
     */
    private void cleanupOldTransactions() {
        long cutoff = System.currentTimeMillis() - (windowSeconds * 1000L);
        while (!recentTransactions.isEmpty() && recentTransactions.peekFirst().timestamp < cutoff) {
            recentTransactions.pollFirst();
        }
    }

    /**
     * Updates dynamic prices based on recent market activity.
     * Called periodically by update task (default: every 5 seconds).
     *
     * Economics (player perspective):
     *   - High player BUYING demand  → buy price goes UP   (players pay more)
     *   - High player SELLING supply → sell price goes DOWN (players receive less)
     *   - Invariant: buyPrice >= sellPrice  (server keeps a spread to prevent arbitrage)
     */
    public void updatePrices() {
        if (!dynamicEnabled) return;

        lock.writeLock().lock();
        try {
            cleanupOldTransactions();

            // Aggregate player activity from recent transactions
            int buyVolume  = 0; // Emeralds bought by players (demand)
            int sellVolume = 0; // Emeralds sold by players   (supply)

            for (Transaction tx : recentTransactions) {
                if (tx.type == TransactionType.BUY) {
                    // Player BOUGHT emeralds → demand pressure → buy price rises
                    buyVolume  += tx.amount;
                } else {
                    // Player SOLD emeralds → supply pressure → sell price falls
                    sellVolume += tx.amount;
                }
            }

            // Update EWMA for smoothing (prevents sudden price spikes)
            // EWMA formula: new_value = alpha * raw + (1 - alpha) * old_value
            demandEWMA = ewmaAlpha * buyVolume  + (1 - ewmaAlpha) * demandEWMA;
            supplyEWMA = ewmaAlpha * sellVolume + (1 - ewmaAlpha) * supplyEWMA;

            updateDepletionFactor();

            // === Buy Price: what player PAYS when buying ===
            // Higher buying demand → player pays more (demand pressure increases buy price)
            // Higher depletion (scarcity) → buy price increases further
            double demandPressure = demandEWMA * demandSensitivity * depletionFactor;
            double newBuyPrice = baseBuyPrice + demandPressure + (baseBuyPrice * (1 - depletionFactor) * 0.5);

            // === Sell Price: what player RECEIVES when selling ===
            // Higher selling supply → player receives less (supply pressure lowers sell price)
            double supplyPressure = supplyEWMA * supplySensitivity;
            double newSellPrice = baseSellPrice - supplyPressure;

            // Clamp prices within configured bounds
            newBuyPrice  = clamp(newBuyPrice,  minPrice, maxPrice);
            newSellPrice = clamp(newSellPrice, minPrice, maxPrice);

            // Ensure buyPrice >= sellPrice (server always keeps a spread to prevent arbitrage)
            if (newBuyPrice < newSellPrice) {
                double mid = (newBuyPrice + newSellPrice) / 2;
                newBuyPrice  = mid * 1.05; // player pays 5% above mid
                newSellPrice = mid * 0.95; // player receives 5% below mid
            }

            // Commit updated prices (volatile fields, visible to all threads)
            currentBuyPrice  = newBuyPrice;
            currentSellPrice = newSellPrice;

        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Updates the resource depletion factor.
     * More total emeralds transacted = higher scarcity = higher buy price.
     */
    private void updateDepletionFactor() {
        long timeSinceStart = System.currentTimeMillis() / 1000;
        double recoveryFactor = Math.min(1.0, (double) timeSinceStart / depletionRecoveryPeriod);
        double depletionAmount = totalEmeraldsConverted * depletionRate;
        depletionFactor = Math.max(0.1, 1.0 - depletionAmount + (recoveryFactor * 0.5));
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    // =========================================================================
    // Public getters — ALL named from PLAYER perspective
    // =========================================================================

    /**
     * Gets the current buy price — what the player PAYS to buy emeralds from the server.
     * Increases when player buying demand is high.
     * Thread-safe.
     *
     * @return Current buy price per emerald (player spends this amount)
     */
    public double getBuyPrice() {
        lock.readLock().lock();
        try {
            return currentBuyPrice;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Gets the current sell price — what the player RECEIVES when selling emeralds to the server.
     * Decreases when player selling supply is high.
     * Thread-safe.
     *
     * @return Current sell price per emerald (player receives this amount)
     */
    public double getSellPrice() {
        lock.readLock().lock();
        try {
            return currentSellPrice;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Gets the base buy price from config.yml (before dynamic adjustments).
     * Corresponds to prices.buy — what player PAYS to buy.
     *
     * @return Base buy price (player perspective)
     */
    public double getBaseBuyPrice() {
        return baseBuyPrice;
    }

    /**
     * Gets the base sell price from config.yml (before dynamic adjustments).
     * Corresponds to prices.sell — what player RECEIVES when selling.
     *
     * @return Base sell price (player perspective)
     */
    public double getBaseSellPrice() {
        return baseSellPrice;
    }

    /**
     * Calculates transaction tax for a given amount using the global tax rate.
     * Used for generic display purposes (e.g., /ecadmin info, static tooltips).
     * For actual player transactions, use {@link #getTransactionTax(double, double)}.
     *
     * @param amount The transaction amount
     * @return Tax amount using the global rate
     */
    public double getTransactionTax(double amount) {
        return amount * transactionTaxRate;
    }

    /**
     * Calculates transaction tax for a given amount using an explicit tax rate.
     * Used for per-player group-based tax during actual transactions.
     *
     * @param amount  The transaction amount
     * @param taxRate The player's effective tax rate (0.0–1.0), e.g. 0.03 = 3%
     * @return Tax amount for this specific rate
     */
    public double getTransactionTax(double amount, double taxRate) {
        return amount * taxRate;
    }

    /**
     * Gets the global transaction tax rate (fallback for players without a group).
     *
     * @return Tax rate (e.g., 0.05 = 5%)
     */
    public double getTransactionTaxRate() {
        return transactionTaxRate;
    }

    /**
     * Gets the recent transaction volume within the time window.
     * Thread-safe.
     *
     * @return Total emeralds transacted recently
     */
    public int getRecentVolume() {
        lock.readLock().lock();
        try {
            int volume = 0;
            for (Transaction tx : recentTransactions) {
                volume += tx.amount;
            }
            return volume;
        } finally {
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
     * Gets the total emeralds transacted globally.
     *
     * @return Total emeralds converted
     */
    public long getTotalEmeraldsConverted() {
        return totalEmeraldsConverted;
    }

    /**
     * Starts the dynamic pricing update task.
     */
    public void startDynamicPricing() {
        if (!dynamicEnabled) {
            plugin.getLogger().info("Dynamic pricing is disabled in config");
            return;
        }
        if (updateTask != null) {
            updateTask.cancel();
        }
        updateTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin,
            this::updatePrices,
            20L * updateInterval,
            20L * updateInterval
        );
        plugin.getLogger().info("Dynamic pricing started (update every " + updateInterval + "s)");
    }

    /**
     * Stops the dynamic pricing update task.
     */
    public void stopDynamicPricing() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
    }

    /**
     * Transaction — internal record for dynamic pricing history.
     * BUY = player bought emeralds, SELL = player sold emeralds.
     */
    private static class Transaction {
        final TransactionType type;
        final int amount;
        final double price;
        final long timestamp;

        Transaction(TransactionType type, int amount, double price, long timestamp) {
            this.type      = type;
            this.amount    = amount;
            this.price     = price;
            this.timestamp = timestamp;
        }
    }
}
