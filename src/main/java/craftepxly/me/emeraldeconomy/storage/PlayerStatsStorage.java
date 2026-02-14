package craftepxly.me.emeraldeconomy.storage;

import craftepxly.me.emeraldeconomy.EmeraldEconomy;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * PlayerStatsStorage — legacy file-based player statistics storage.
 */
public class PlayerStatsStorage {
    
    // Reference to main plugin instance
    private final EmeraldEconomy plugin;
    // File object pointing to player_stats.yml
    private final File statsFile;
    // YAML configuration for player_stats.yml
    private FileConfiguration stats;
    // In-memory cache (UUID → PlayerStats)
    private final Map<UUID, PlayerStats> cache = new ConcurrentHashMap<>();
    
    /**
     * Constructs a new PlayerStatsStorage and loads data.
     * 
     * @param plugin The main EmeraldEconomy plugin instance
     */
    public PlayerStatsStorage(EmeraldEconomy plugin) {
        // Store plugin reference
        this.plugin = plugin;
        // Create File object for player_stats.yml
        this.statsFile = new File(plugin.getDataFolder(), "player_stats.yml");
        // Load stats from disk
        loadStats();
    }
    
    /**
     * Loads player stats from player_stats.yml into memory cache.
     */
    private void loadStats() {
        // Check if file exists
        if (!statsFile.exists()) {
            try {
                // Create parent directories if they don't exist
                statsFile.getParentFile().mkdirs();
                // Create empty file
                statsFile.createNewFile();
            } catch (IOException e) {
                // Log error if file creation fails
                plugin.getLogger().log(Level.SEVERE, "Failed to create player stats file", e);
            }
        }
        
        // Load YAML file into memory
        stats = YamlConfiguration.loadConfiguration(statsFile);
        
        // Load all entries into cache
        for (String key : stats.getKeys(false)) {
            try {
                // Parse UUID from key
                UUID uuid = UUID.fromString(key);
                // Read total_converted (or 0 if missing)
                int totalConverted = stats.getInt(key + ".total_converted", 0);
                // Read last_transaction timestamp (or 0 if missing)
                long lastTransaction = stats.getLong(key + ".last_transaction", 0);
                
                // Create PlayerStats object and add to cache
                cache.put(uuid, new PlayerStats(totalConverted, lastTransaction));
            } catch (IllegalArgumentException e) {
                // Invalid UUID → log warning and skip
                plugin.getLogger().warning("Invalid UUID in player stats: " + key);
            }
        }
        
        // Log number of players loaded
        plugin.getLogger().info("Loaded stats for " + cache.size() + " players");
    }
    
    /**
     * Saves all cached stats to player_stats.yml.
     */
    public void saveAll() {
        // Loop through all cached entries
        for (Map.Entry<UUID, PlayerStats> entry : cache.entrySet()) {
            // Get UUID and stats
            UUID uuid = entry.getKey();
            PlayerStats playerStats = entry.getValue();
            
            // Write to YAML config
            stats.set(uuid.toString() + ".total_converted", playerStats.totalConverted);
            stats.set(uuid.toString() + ".last_transaction", playerStats.lastTransaction);
        }
        
        try {
            // Save YAML config to disk
            stats.save(statsFile);
            // Log success message
            plugin.getLogger().info("Saved stats for " + cache.size() + " players");
        } catch (IOException e) {
            // Log error if save fails
            plugin.getLogger().log(Level.SEVERE, "Failed to save player stats", e);
        }
    }
    
    /**
     * Gets total emeralds converted for a player.
     * 
     * @param playerId Player UUID
     * @return Total converted, or 0 if not found
     */
    public int getTotalConverted(UUID playerId) {
        // Get stats from cache
        PlayerStats playerStats = cache.get(playerId);
        // Return total_converted (or 0 if null)
        return playerStats != null ? playerStats.totalConverted : 0;
    }
    
    /**
     * Increments total converted for a player.
     * 
     * @param playerId Player UUID
     * @param amount   Amount to add
     */
    public void incrementConverted(UUID playerId, int amount) {
        // Get existing stats or create new entry
        PlayerStats playerStats = cache.computeIfAbsent(playerId, k -> new PlayerStats(0, 0));
        // Increment total_converted
        playerStats.totalConverted += amount;
        // Update last_transaction timestamp
        playerStats.lastTransaction = System.currentTimeMillis();
    }
    
    /**
     * Gets last transaction timestamp for a player.
     * 
     * @param playerId Player UUID
     * @return Last transaction timestamp, or 0 if not found
     */
    public long getLastTransaction(UUID playerId) {
        // Get stats from cache
        PlayerStats playerStats = cache.get(playerId);
        // Return last_transaction (or 0 if null)
        return playerStats != null ? playerStats.lastTransaction : 0;
    }
    
    /**
     * PlayerStats — simple data class for storing player statistics.
     */
    private static class PlayerStats {
        // Total emeralds converted
        int totalConverted;
        // Last transaction timestamp
        long lastTransaction;
        
        /**
         * Constructs a new PlayerStats.
         * 
         * @param totalConverted  Total converted
         * @param lastTransaction Last transaction timestamp
         */
        PlayerStats(int totalConverted, long lastTransaction) {
            // Store fields in memory
            this.totalConverted = totalConverted;
            this.lastTransaction = lastTransaction;
        }
    }
}