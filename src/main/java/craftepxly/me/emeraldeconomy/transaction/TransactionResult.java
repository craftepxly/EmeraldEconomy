package craftepxly.me.emeraldeconomy.transaction;

import java.util.HashMap;
import java.util.Map;

/**
 * TransactionResult — represents the result of a transaction.
 * 
 * <p>Contains success status, message key, and placeholders for displaying
 * feedback to the player.</p>
 */
public class TransactionResult {

    // Success status
    private final boolean success;
    // Message key from messages.yml
    private final String messageKey;
    // Placeholder map for message formatting
    private final Map<String, String> placeholders;
    // Emerald amount (for success results)
    private final int emeraldAmount;
    // Money amount (for success results)
    private final double moneyAmount;
    // Transaction type (for success results)
    private final TransactionType type;

    /**
     * Private constructor (use static factory methods).
     * 
     * @param success      Success status
     * @param messageKey   Message key
     * @param placeholders Placeholder map
     * @param emeraldAmount Emerald amount
     * @param moneyAmount  Money amount
     * @param type         Transaction type
     */
    private TransactionResult(boolean success, String messageKey, Map<String, String> placeholders,
                              int emeraldAmount, double moneyAmount, TransactionType type) {
        // Store success status
        this.success = success;
        // Store message key
        this.messageKey = messageKey;
        // Store placeholders (or empty map if null)
        this.placeholders = placeholders != null ? placeholders : new HashMap<>();
        // Store emerald amount
        this.emeraldAmount = emeraldAmount;
        // Store money amount
        this.moneyAmount = moneyAmount;
        // Store transaction type
        this.type = type;
    }

    /**
     * Create a success result with emerald amount, money amount, type, and currency symbol
     *
     * FIX: Added currencySymbol parameter to include {currency} placeholder
     *
     * @param emeraldAmount Number of emeralds transacted
     * @param moneyAmount Amount of money transacted
     * @param type Transaction type (BUY or SELL)
     * @param currencySymbol Currency symbol from config (e.g., "$")
     * @return TransactionResult with success status and all placeholders
     */
    public static TransactionResult success(int emeraldAmount, double moneyAmount, TransactionType type, String currencySymbol) {
        // Select message key based on transaction type
        String messageKey = type == TransactionType.SELL ? "success.convert_sell" : "success.convert_buy";
        // Create placeholder map
        Map<String, String> placeholders = new HashMap<>();
        // Add emerald amount placeholder
        placeholders.put("emerald", String.valueOf(emeraldAmount));
        // Add money amount placeholder (formatted to 2 decimal places)
        placeholders.put("money", String.format("%.2f", moneyAmount));
        // FIX: Add currency placeholder so {currency} in messages gets replaced
        placeholders.put("currency", currencySymbol);

        // Create and return success result
        return new TransactionResult(true, messageKey, placeholders, emeraldAmount, moneyAmount, type);
    }

    /**
     * Creates a failure result with message key only.
     * 
     * @param messageKey Message key from messages.yml
     * @return TransactionResult with failure status
     */
    public static TransactionResult failure(String messageKey) {
        // Create and return failure result (no placeholders, no amounts)
        return new TransactionResult(false, messageKey, null, 0, 0.0, null);
    }

    /**
     * Creates a failure result with message key and custom placeholders.
     * 
     * @param messageKey   Message key from messages.yml
     * @param placeholders Placeholder map
     * @return TransactionResult with failure status
     */
    public static TransactionResult failure(String messageKey, Map<String, String> placeholders) {
        // Create and return failure result with custom placeholders
        return new TransactionResult(false, messageKey, placeholders, 0, 0.0, null);
    }

    /**
     * Checks if transaction was successful.
     * 
     * @return true if successful, false otherwise
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * Gets message key.
     * 
     * @return Message key
     */
    public String getMessageKey() {
        return messageKey;
    }

    /**
     * Gets placeholder map.
     * 
     * @return Placeholder map
     */
    public Map<String, String> getPlaceholders() {
        return placeholders;
    }

    /**
     * Gets emerald amount (for success results).
     * 
     * @return Emerald amount
     */
    public int getEmeraldAmount() {
        return emeraldAmount;
    }

    /**
     * Gets money amount (for success results).
     * 
     * @return Money amount
     */
    public double getMoneyAmount() {
        return moneyAmount;
    }

    /**
     * Gets transaction type (for success results).
     * 
     * @return Transaction type
     */
    public TransactionType getType() {
        return type;
    }
}