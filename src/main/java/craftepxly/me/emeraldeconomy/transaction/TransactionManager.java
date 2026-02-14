package craftepxly.me.emeraldeconomy.transaction;

import craftepxly.me.emeraldeconomy.EmeraldEconomy;
import craftepxly.me.emeraldeconomy.service.PriceManager;
import craftepxly.me.emeraldeconomy.util.ItemUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * TransactionManager — manages all emerald↔money transactions.
 */
public class TransactionManager {

    // Reference to main plugin instance
    private final EmeraldEconomy plugin;

    // Per-player locks to prevent concurrent transactions (prevents duping)
    private final Map<UUID, ReentrantLock> playerLocks = new ConcurrentHashMap<>();

    // Cooldown tracking (UUID → last transaction timestamp)
    private final Map<UUID, Long> lastTransaction = new ConcurrentHashMap<>();

    // Rate limiting (UUID → transaction counter)
    private final Map<UUID, TransactionCounter> transactionCounters = new ConcurrentHashMap<>();

    /**
     * Constructs a new TransactionManager.
     * 
     * @param plugin The main EmeraldEconomy plugin instance
     */
    public TransactionManager(EmeraldEconomy plugin) {
        // Store plugin reference
        this.plugin = plugin;

        // Start cleanup task for old data (runs every minute)
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            // Get current time
            long now = System.currentTimeMillis();
            // Remove cooldown entries older than 60 seconds
            lastTransaction.entrySet().removeIf(entry -> now - entry.getValue() > 60000);
            // Remove expired transaction counters
            transactionCounters.entrySet().removeIf(entry -> entry.getValue().isExpired());
        }, 1200L, 1200L); // Every minute (1200 ticks = 60 seconds)
    }

    /**
     * Gets or creates a ReentrantLock for a player.
     * Ensures only one transaction per player at a time.
     * 
     * @param playerId Player UUID
     * @return ReentrantLock for this player
     */
    private ReentrantLock getLock(UUID playerId) {
        // Get existing lock or create new one (thread-safe)
        return playerLocks.computeIfAbsent(playerId, k -> new ReentrantLock());
    }

    /**
     * Executes a sell transaction (player sells emeralds to server).
     * 
     * @param player The player
     * @param amount Emerald amount to sell
     * @return TransactionResult with success/failure and message
     */
    public TransactionResult sellEmerald(Player player, int amount) {
        // Get player UUID
        UUID playerId = player.getUniqueId();

        // Check cooldown
        if (!checkCooldown(player)) {
            // Still on cooldown → return failure
            return TransactionResult.failure("error.cooldown");
        }

        // Check rate limit
        if (!checkRateLimit(player)) {
            // Rate limit exceeded → return failure
            return TransactionResult.failure("error.rate_limit");
        }

        // Get player's lock (ensures only one transaction at a time for this player)
        ReentrantLock lock = getLock(playerId);
        // Acquire lock (blocks until available)
        lock.lock();

        try {
            // Validate amount
            if (amount <= 0) {
                // Invalid amount → return failure
                return TransactionResult.failure("error.invalid_amount");
            }

            // Count emeralds in inventory
            int emeraldCount = ItemUtils.countEmeralds(player.getInventory());
            // Check if player has enough emeralds
            if (emeraldCount < amount) {
                // Not enough emeralds → return failure with details
                return TransactionResult.failure("error.not_enough_emerald",
                        Map.of("required", String.valueOf(amount), "current", String.valueOf(emeraldCount)));
            }

            // Calculate money to give (per emerald price)
            double pricePerEmerald = plugin.getPriceManager().getBuyPrice();
            double moneyAmount = pricePerEmerald * amount;

            // Apply transaction tax (money sink)
            double tax = plugin.getPriceManager().getTransactionTax(moneyAmount);
            double finalAmount = moneyAmount - tax; // Player receives less due to tax

            // Execute transaction on main thread (Bukkit API requirement)
            return Bukkit.getScheduler().callSyncMethod(plugin, () -> {
                try {
                    // === CRITICAL SECTION START ===

                    // Remove emeralds from inventory
                    if (!ItemUtils.removeEmeralds(player.getInventory(), amount)) {
                        // Failed to remove (shouldn't happen, we checked count above)
                        return TransactionResult.failure("error.transaction_failed");
                    }

                    // Deposit money (after tax)
                    if (!plugin.getEconomyService().deposit(player, finalAmount)) {
                        // Rollback: return emeralds
                        ItemUtils.addEmeralds(player.getInventory(), amount);
                        // Return failure
                        return TransactionResult.failure("error.transaction_failed");
                    }

                    // === CRITICAL SECTION END ===

                    // FIX #2: Record transaction data BEFORE returning success
                    // These operations are non-critical but should be attempted before declaring success
                    try {
                        // Record cooldown timestamp
                        recordTransaction(player);
                        // Record for dynamic pricing
                        plugin.getPriceManager().recordTransaction(TransactionType.SELL, amount, pricePerEmerald);
                        // Update player stats
                        plugin.getPlayerStatsStorage().incrementConverted(playerId, player.getName(), amount);
                    } catch (Exception e) {
                        // Log error but don't fail the transaction since items/money already transferred
                        plugin.getLogger().warning("Failed to record transaction data for " + player.getName() + ": " + e.getMessage());
                    }

                    // Create transaction object for logging
                    Transaction tx = new Transaction(
                            playerId,
                            player.getName(),
                            TransactionType.SELL,
                            amount,
                            finalAmount,  // Money after tax
                            pricePerEmerald
                    );

                    // Create success result BEFORE logging
                    // FIX: Pass currency symbol from config so {currency} placeholder gets replaced
                    String currencySymbol = plugin.getConfigManager().getCurrencySymbol();
                    TransactionResult result = TransactionResult.success(amount, finalAmount, TransactionType.SELL, currencySymbol);

                    // FIX #3: Log asynchronously AFTER critical operations complete
                    // This separates non-critical I/O from critical transaction logic
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                        try {
                            // Log to TransactionLogger (flat file) or storage (database)
                            if (plugin.getTransactionLogger() != null) {
                                plugin.getTransactionLogger().log(tx);
                            } else {
                                plugin.getPlayerStatsStorage().logTransaction(tx);
                            }
                        } catch (Exception e) {
                            plugin.getLogger().warning("Failed to log transaction for " + player.getName() + ": " + e.getMessage());
                        }
                    });

                    // Return success result
                    return result;

                } catch (Exception e) {
                    // Log error
                    plugin.getLogger().severe("Transaction error for " + player.getName() + ": " + e.getMessage());
                    e.printStackTrace();
                    // Return failure
                    return TransactionResult.failure("error.transaction_failed");
                }
            }).get(); // Wait for result (blocks until sync task completes)

        } catch (Exception e) {
            // Log async error
            plugin.getLogger().severe("Async transaction error: " + e.getMessage());
            // Return failure
            return TransactionResult.failure("error.transaction_failed");
        } finally {
            // Always release lock
            lock.unlock();
        }
    }

    /**
     * Executes a buy transaction (player buys emeralds from server).
     * 
     * @param player The player
     * @param amount Emerald amount to buy
     * @return TransactionResult with success/failure and message
     */
    public TransactionResult buyEmerald(Player player, int amount) {
        // Get player UUID
        UUID playerId = player.getUniqueId();

        // Check cooldown
        if (!checkCooldown(player)) {
            return TransactionResult.failure("error.cooldown");
        }

        // Check rate limit
        if (!checkRateLimit(player)) {
            return TransactionResult.failure("error.rate_limit");
        }

        // Get player's lock
        ReentrantLock lock = getLock(playerId);
        // Acquire lock
        lock.lock();

        try {
            // Validate amount
            if (amount <= 0) {
                return TransactionResult.failure("error.invalid_amount");
            }

            // Calculate cost (per emerald price)
            double pricePerEmerald = plugin.getPriceManager().getSellPrice();
            double cost = pricePerEmerald * amount;

            // Apply transaction tax
            double tax = plugin.getPriceManager().getTransactionTax(cost);
            double totalCost = cost + tax;  // Player pays more due to tax

            // Check if player has enough money (including tax)
            if (!plugin.getEconomyService().has(player, totalCost)) {
                // Not enough money → return failure with details
                double balance = plugin.getEconomyService().getBalance(player);
                return TransactionResult.failure("error.not_enough_money",
                        Map.of("required", String.format("%.2f", totalCost), "current", String.format("%.2f", balance)));
            }

            // Execute transaction on main thread
            return Bukkit.getScheduler().callSyncMethod(plugin, () -> {
                try {
                    // === CRITICAL SECTION START ===

                    // FIX #1: Check inventory space RIGHT BEFORE adding items
                    // This prevents TOCTOU (Time-of-Check-Time-of-Use) race condition
                    if (!ItemUtils.hasSpace(player.getInventory(), amount)) {
                        // No space → return failure
                        return TransactionResult.failure("error.inventory_full");
                    }

                    // Withdraw money (including tax)
                    if (!plugin.getEconomyService().withdraw(player, totalCost)) {
                        // Failed to withdraw → return failure
                        return TransactionResult.failure("error.transaction_failed");
                    }

                    // Give emeralds (space already verified above)
                    if (!ItemUtils.addEmeralds(player.getInventory(), amount)) {
                        // Rollback: return money
                        plugin.getEconomyService().deposit(player, totalCost);
                        // Return failure
                        return TransactionResult.failure("error.transaction_failed");
                    }

                    // === CRITICAL SECTION END ===

                    // FIX #2: Record transaction data BEFORE returning success
                    try {
                        recordTransaction(player);
                        plugin.getPriceManager().recordTransaction(TransactionType.BUY, amount, pricePerEmerald);
                        // Note: Stats increment for BUY transactions (consistency with SELL)
                        plugin.getPlayerStatsStorage().incrementConverted(playerId, player.getName(), amount);
                    } catch (Exception e) {
                        plugin.getLogger().warning("Failed to record transaction data for " + player.getName() + ": " + e.getMessage());
                    }

                    // Create transaction object for logging
                    Transaction tx = new Transaction(
                            playerId,
                            player.getName(),
                            TransactionType.BUY,
                            amount,
                            totalCost,  // Total cost including tax
                            pricePerEmerald
                    );

                    // Create success result BEFORE logging
                    String currencySymbol = plugin.getConfigManager().getCurrencySymbol();
                    TransactionResult result = TransactionResult.success(amount, totalCost, TransactionType.BUY, currencySymbol);

                    // FIX #3: Log asynchronously AFTER critical operations complete
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                        try {
                            if (plugin.getTransactionLogger() != null) {
                                plugin.getTransactionLogger().log(tx);
                            } else {
                                plugin.getPlayerStatsStorage().logTransaction(tx);
                            }
                        } catch (Exception e) {
                            plugin.getLogger().warning("Failed to log transaction for " + player.getName() + ": " + e.getMessage());
                        }
                    });

                    return result;

                } catch (Exception e) {
                    plugin.getLogger().severe("Transaction error for " + player.getName() + ": " + e.getMessage());
                    e.printStackTrace();
                    return TransactionResult.failure("error.transaction_failed");
                }
            }).get();

        } catch (Exception e) {
            plugin.getLogger().severe("Async transaction error: " + e.getMessage());
            return TransactionResult.failure("error.transaction_failed");
        } finally {
            lock.unlock();
        }
    }

    /**
     * Sells all emeralds in player's inventory.
     * 
     * @param player The player
     * @return TransactionResult
     */
    public TransactionResult sellAllEmeralds(Player player) {
        // Count emeralds in inventory
        int emeraldCount = ItemUtils.countEmeralds(player.getInventory());

        // Check if player has any emeralds
        if (emeraldCount == 0) {
            // No emeralds → return failure
            return TransactionResult.failure("error.not_enough_emerald",
                    Map.of("required", "1", "current", "0"));
        }

        // Sell all emeralds
        return sellEmerald(player, emeraldCount);
    }

    /**
     * Checks if player has passed cooldown.
     * 
     * @param player The player
     * @return true if cooldown passed, false otherwise
     */
    private boolean checkCooldown(Player player) {
        // Check if player has bypass permission
        if (player.hasPermission("emeraldeconomy.bypass.cooldown")) {
            return true;
        }

        // Get cooldown duration from config
        int cooldown = plugin.getConfigManager().getTransactionCooldown();
        // Check if cooldown is disabled (0 or negative)
        if (cooldown <= 0) {
            return true;
        }

        // Get player UUID
        UUID playerId = player.getUniqueId();
        // Get last transaction timestamp
        Long lastTime = lastTransaction.get(playerId);

        // Check if player has no previous transaction
        if (lastTime == null) {
            return true;
        }

        // Calculate elapsed time in seconds
        long elapsed = (System.currentTimeMillis() - lastTime) / 1000;
        // Return true if cooldown has passed
        return elapsed >= cooldown;
    }

    /**
     * Checks if player has not exceeded rate limit.
     * 
     * @param player The player
     * @return true if within limit, false if exceeded
     */
    private boolean checkRateLimit(Player player) {
        // Check if rate limiting is disabled
        if (!plugin.getConfigManager().isRateLimitEnabled()) {
            return true;
        }

        // Check if player has bypass permission
        if (player.hasPermission("emeraldeconomy.bypass.cooldown")) {
            return true;
        }

        // Get player UUID
        UUID playerId = player.getUniqueId();
        // Get or create transaction counter
        TransactionCounter counter = transactionCounters.computeIfAbsent(playerId, k -> new TransactionCounter());

        // Check if player can transact (not exceeded max per minute)
        return counter.canTransact(plugin.getConfigManager().getMaxTransactionsPerMinute());
    }

    /**
     * Records a transaction (updates cooldown and rate limit counter).
     * 
     * @param player The player
     */
    private void recordTransaction(Player player) {
        // Update last transaction timestamp
        lastTransaction.put(player.getUniqueId(), System.currentTimeMillis());

        // Get or create transaction counter
        TransactionCounter counter = transactionCounters.computeIfAbsent(
                player.getUniqueId(),
                k -> new TransactionCounter()
        );
        // Increment counter
        counter.increment();
    }

    /**
     * Gets remaining cooldown time for a player.
     * 
     * @param player The player
     * @return Remaining cooldown in seconds, or 0 if no cooldown
     */
    public long getCooldownRemaining(Player player) {
        // Get cooldown duration from config
        int cooldown = plugin.getConfigManager().getTransactionCooldown();
        // Check if cooldown is disabled
        if (cooldown <= 0) {
            return 0;
        }

        // Get last transaction timestamp
        Long lastTime = lastTransaction.get(player.getUniqueId());
        // Check if no previous transaction
        if (lastTime == null) {
            return 0;
        }

        // Calculate elapsed time in seconds
        long elapsed = (System.currentTimeMillis() - lastTime) / 1000;
        // Return remaining time (or 0 if cooldown passed)
        return Math.max(0, cooldown - elapsed);
    }

    /**
     * TransactionCounter — tracks transaction count per player for rate limiting.
     */
    private static class TransactionCounter {
        // Transaction count in current time window
        private int count = 0;
        // Time window start timestamp
        private long windowStart = System.currentTimeMillis();

        /**
         * Checks if player can perform another transaction.
         * 
         * @param maxPerMinute Maximum transactions per minute
         * @return true if can transact, false if limit exceeded
         */
        boolean canTransact(int maxPerMinute) {
            // Reset window if needed (new minute)
            resetIfNeeded();
            // Return true if count is below max
            return count < maxPerMinute;
        }

        /**
         * Increments transaction count.
         */
        void increment() {
            // Reset window if needed
            resetIfNeeded();
            // Increment count
            count++;
        }

        /**
         * Resets counter if time window has expired.
         */
        void resetIfNeeded() {
            // Get current time
            long now = System.currentTimeMillis();
            // Check if 60 seconds have passed since window start
            if (now - windowStart >= 60000) {
                // Reset counter
                count = 0;
                // Start new window
                windowStart = now;
            }
        }

        /**
         * Checks if this counter is expired (can be cleaned up).
         * 
         * @return true if expired (older than 2 minutes)
         */
        boolean isExpired() {
            // Return true if older than 2 minutes (120 seconds)
            return System.currentTimeMillis() - windowStart >= 120000; // 2 minutes
        }
    }
}