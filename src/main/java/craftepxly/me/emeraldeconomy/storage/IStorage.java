package craftepxly.me.emeraldeconomy.storage;

import craftepxly.me.emeraldeconomy.storage.model.PlayerStats;
import craftepxly.me.emeraldeconomy.transaction.Transaction;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * IStorage — interface for all storage backends (YAML, SQLite, MySQL).
 */
public interface IStorage {
    
    /**
     * Initializes the storage backend.
     * Creates tables/files if they don't exist.
     * 
     * @return CompletableFuture that completes with true if successful, false otherwise
     */
    CompletableFuture<Boolean> initialize();
    
    /**
     * Closes the storage backend.
     * Saves all data and closes connections.
     * 
     * @return CompletableFuture that completes when shutdown is finished
     */
    CompletableFuture<Void> close();
    
    /**
     * Gets player statistics by UUID.
     * 
     * @param uuid Player UUID
     * @return CompletableFuture with PlayerStats, or null if not found
     */
    CompletableFuture<PlayerStats> getPlayerStats(UUID uuid);
    
    /**
     * Saves player statistics.
     * Inserts if new, updates if exists.
     * 
     * @param stats PlayerStats to save
     * @return CompletableFuture that completes when save is finished
     */
    CompletableFuture<Void> savePlayerStats(PlayerStats stats);
    
    /**
     * Increments player's total converted amount.
     * Creates player record if it doesn't exist.
     * 
     * @param uuid       Player UUID
     * @param playerName Player name
     * @param amount     Amount to add
     * @return CompletableFuture that completes when increment is finished
     */
    CompletableFuture<Void> incrementConverted(UUID uuid, String playerName, int amount);
    
    /**
     * Logs a transaction to storage.
     * 
     * @param transaction Transaction to log
     * @return CompletableFuture that completes when logging is finished
     */
    CompletableFuture<Void> logTransaction(Transaction transaction);
    
    /**
     * Gets total emeralds converted by a player.
     * 
     * @param uuid Player UUID
     * @return CompletableFuture with total converted, or 0 if not found
     */
    CompletableFuture<Integer> getTotalConverted(UUID uuid);
    
    /**
     * Checks if the storage backend is available.
     * 
     * @return true if connected/available, false otherwise
     */
    boolean isAvailable();
    
    /**
     * Gets the storage type.
     * 
     * @return StorageType enum
     */
    StorageType getStorageType();
    
    /**
     * Saves all cached data to persistent storage.
     * Used for graceful shutdowns.
     * 
     * @return CompletableFuture that completes when save is finished
     */
    CompletableFuture<Void> saveAll();
}