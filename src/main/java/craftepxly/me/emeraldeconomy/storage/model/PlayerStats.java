package craftepxly.me.emeraldeconomy.storage.model;

import java.util.UUID;

/**
 * PlayerStats — data model for player statistics.
 */
public class PlayerStats {
    
    // Player's unique identifier (immutable)
    private final UUID uuid;
    // Player's username (immutable for this instance)
    private final String playerName;
    // Total emeralds converted (mutable)
    private int totalConverted;
    // Last update timestamp in milliseconds (mutable)
    private long lastUpdated;
    
    /**
     * Creates a new PlayerStats instance.
     * 
     * @param uuid           Player's UUID
     * @param playerName     Player's name
     * @param totalConverted Total emeralds converted
     * @param lastUpdated    Last update timestamp
     */
    public PlayerStats(UUID uuid, String playerName, int totalConverted, long lastUpdated) {
        // Store UUID in memory (final field, cannot be changed)
        this.uuid = uuid;
        // Store player name in memory (final field, cannot be changed)
        this.playerName = playerName;
        // Store total converted in memory (can be updated via setter)
        this.totalConverted = totalConverted;
        // Store last updated timestamp in memory (can be updated via setter)
        this.lastUpdated = lastUpdated;
    }
    
    /**
     * Gets the player's UUID.
     * 
     * @return UUID
     */
    public UUID getUuid() {
        // Return the stored UUID
        return uuid;
    }
    
    /**
     * Gets the player's name.
     * 
     * @return Player name
     */
    public String getPlayerName() {
        // Return the stored player name
        return playerName;
    }
    
    /**
     * Gets total emeralds converted.
     * 
     * @return Total converted
     */
    public int getTotalConverted() {
        // Return the stored total converted value
        return totalConverted;
    }
    
    /**
     * Sets total emeralds converted.
     * Also updates the lastUpdated timestamp to current time.
     * 
     * @param totalConverted New total
     */
    public void setTotalConverted(int totalConverted) {
        // Update total converted field in memory
        this.totalConverted = totalConverted;
        // Update last updated timestamp to current time
        this.lastUpdated = System.currentTimeMillis();
    }
    
    /**
     * Adds to total emeralds converted.
     * Convenience method that increments instead of replacing.
     * Also updates the lastUpdated timestamp.
     * 
     * @param amount Amount to add
     */
    public void addConverted(int amount) {
        // Add amount to current total
        this.totalConverted += amount;
        // Update last updated timestamp to current time
        this.lastUpdated = System.currentTimeMillis();
    }
    
    /**
     * Gets last update timestamp.
     * 
     * @return Timestamp in milliseconds
     */
    public long getLastUpdated() {
        // Return the stored last updated timestamp
        return lastUpdated;
    }
    
    /**
     * Returns a string representation of this PlayerStats.
     * Useful for debugging and logging.
     * 
     * @return String representation
     */
    @Override
    public String toString() {
        // Format as string with all fields
        return String.format("PlayerStats{uuid=%s, name=%s, total=%d, updated=%d}",
            uuid, playerName, totalConverted, lastUpdated);
    }
}