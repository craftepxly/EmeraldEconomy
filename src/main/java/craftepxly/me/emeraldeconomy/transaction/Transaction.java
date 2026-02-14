package craftepxly.me.emeraldeconomy.transaction;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Transaction — represents a single emerald↔money transaction.
 */
public class Transaction {
    
    // Static counter for generating unique transaction IDs (atomic for thread safety)
    private static final AtomicLong ID_COUNTER = new AtomicLong(0);
    // Base-36 server-start token ensures IDs are unique across restarts
    // (epoch seconds mod 36^4 = 1,679,616 unique epochs before wraparound ~50 days)
    private static final String SESSION_TOKEN = Long.toString(
        (System.currentTimeMillis() / 1000L) % 1_679_616L, 36).toUpperCase();
    
    // Transaction unique identifier
    private final String transactionId;
    // Player UUID
    private final UUID playerUuid;
    // Player name (for display)
    private final String playerName;
    // Transaction type (BUY or SELL)
    private final TransactionType type;
    // Number of emeralds transacted
    private final int emeraldAmount;
    // Amount of money transacted
    private final double moneyAmount;
    // Price per emerald at time of transaction
    private final double priceAtTime;
    // Transaction timestamp
    private final Instant timestamp;
    
    /**
     * Creates a new Transaction.
     * 
     * @param playerUuid    Player UUID
     * @param playerName    Player name
     * @param type          Transaction type
     * @param emeraldAmount Emerald amount
     * @param moneyAmount   Money amount
     * @param priceAtTime   Price at time of transaction
     */
    public Transaction(UUID playerUuid, String playerName, TransactionType type,
                      int emeraldAmount, double moneyAmount, double priceAtTime) {
        // Generate unique transaction ID
        this.transactionId = generateId();
        // Store player UUID in memory
        this.playerUuid = playerUuid;
        // Store player name in memory
        this.playerName = playerName;
        // Store transaction type in memory
        this.type = type;
        // Store emerald amount in memory
        this.emeraldAmount = emeraldAmount;
        // Store money amount in memory
        this.moneyAmount = moneyAmount;
        // Store price at time in memory
        this.priceAtTime = priceAtTime;
        // Record current timestamp
        this.timestamp = Instant.now();
    }
    
    /**
     * Generates a unique transaction ID.
     * Format: ec_<session-token>_<seq>
     * 
     * @return Unique transaction ID
     */
    private static String generateId() {
        // Increment counter atomically (thread-safe)
        long id = ID_COUNTER.incrementAndGet();
        // Format: ec_<session-token>_<seq> — unique across server restarts
        return String.format("ec_%s_%06d", SESSION_TOKEN, id);
    }
    
    /**
     * Gets transaction ID.
     * 
     * @return Transaction ID
     */
    public String getTransactionId() {
        return transactionId;
    }
    
    /**
     * Gets player UUID.
     * 
     * @return Player UUID
     */
    public UUID getPlayerUuid() {
        return playerUuid;
    }
    
    /**
     * Gets player name.
     * 
     * @return Player name
     */
    public String getPlayerName() {
        return playerName;
    }
    
    /**
     * Gets transaction type.
     * 
     * @return Transaction type
     */
    public TransactionType getType() {
        return type;
    }
    
    /**
     * Gets emerald amount.
     * 
     * @return Emerald amount
     */
    public int getEmeraldAmount() {
        return emeraldAmount;
    }
    
    /**
     * Gets money amount.
     * 
     * @return Money amount
     */
    public double getMoneyAmount() {
        return moneyAmount;
    }
    
    /**
     * Gets price at time of transaction.
     * 
     * @return Price per emerald
     */
    public double getPriceAtTime() {
        return priceAtTime;
    }
    
    /**
     * Gets transaction timestamp.
     * 
     * @return Timestamp
     */
    public Instant getTimestamp() {
        return timestamp;
    }
    
    /**
     * Returns string representation of transaction.
     * Used for logging to transactions.log file.
     * 
     * @return Formatted transaction string
     */
    @Override
    public String toString() {
        // Format: Timestamp | UUID=<uuid> | name=<name> | TYPE=<type> | EMERALD=<amount> | MONEY=<amount> | PRICE=<price> | TXID=<id>
        return String.format("%s | UUID=%s | name=%s | TYPE=%s | EMERALD=%d | MONEY=%.2f | PRICE=%.2f | TXID=%s",
            timestamp.toString(),
            playerUuid.toString(),
            playerName,
            type.name(),
            emeraldAmount,
            moneyAmount,
            priceAtTime,
            transactionId
        );
    }
}