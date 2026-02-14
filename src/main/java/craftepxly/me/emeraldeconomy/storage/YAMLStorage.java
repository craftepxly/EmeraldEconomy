package craftepxly.me.emeraldeconomy.storage;

import craftepxly.me.emeraldeconomy.EmeraldEconomy;
import craftepxly.me.emeraldeconomy.storage.model.PlayerStats;
import craftepxly.me.emeraldeconomy.transaction.Transaction;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;

/**
 * YAMLStorage — YAML file-based storage implementation.
 */
public class YAMLStorage implements IStorage {

    // Reference to main plugin instance
    private final EmeraldEconomy plugin;
    // File object for data file (player_stats.yml or custom name)
    private final File dataFile;
    // YAML configuration loaded into memory
    private YamlConfiguration data;

    /** In-memory cache for fast reads — written back on every save/increment */
    // In-memory cache (UUID → PlayerStats)
    private final Map<UUID, PlayerStats> cache = new ConcurrentHashMap<>();

    // Executor for async file I/O (single-threaded to avoid write conflicts)
    private ExecutorService asyncExecutor;
    // Availability flag
    private boolean available = false;

    /**
     * Buat YAMLStorage dengan nama file default (player_stats.yml).
     * 
     * Creates YAMLStorage with default file name (player_stats.yml).
     * 
     * @param plugin The main EmeraldEconomy plugin instance
     */
    public YAMLStorage(EmeraldEconomy plugin) {
        // Call constructor with default file name
        this(plugin, "player_stats.yml");
    }

    /**
     * Buat YAMLStorage dengan nama file custom.
     * Dipakai misalnya buat emergency_cache.yml saat MySQL down.
     * 
     * Creates YAMLStorage with custom file name.
     * Used for example for emergency_cache.yml when MySQL is down.
     * 
     * @param plugin   The main EmeraldEconomy plugin instance
     * @param fileName Custom file name
     */
    public YAMLStorage(EmeraldEconomy plugin, String fileName) {
        // Store plugin reference
        this.plugin = plugin;
        // Create File object for data file
        this.dataFile = new File(plugin.getDataFolder(), fileName);
    }

    /**
     * Initializes YAML storage (creates file, loads data, starts executor).
     * 
     * @return CompletableFuture with true if successful, false otherwise
     */
    @Override
    public CompletableFuture<Boolean> initialize() {
        // Run initialization asynchronously
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Ensure data folder exists
                File dataFolder = plugin.getDataFolder();
                if (!dataFolder.exists()) {
                    // Create directory
                    dataFolder.mkdirs();
                }

                // Create file if missing (backward-compatible)
                if (!dataFile.exists()) {
                    // Create new file
                    dataFile.createNewFile();
                    plugin.getLogger().info("Created new player_stats.yml file.");
                }

                // Load YAML into memory
                data = YamlConfiguration.loadConfiguration(dataFile);

                // Warm up the cache (load all data into memory)
                loadAllData();

                // Single-threaded executor to serialise file writes
                asyncExecutor = Executors.newSingleThreadExecutor(r -> {
                    // Create daemon thread with name
                    Thread t = new Thread(r, "EmeraldEconomy-YAML-Writer");
                    t.setDaemon(true);
                    return t;
                });

                // Set available flag
                available = true;
                // Log success
                plugin.getLogger().info("YAML storage initialised — loaded " + cache.size() + " player(s).");
                return true;

            } catch (IOException e) {
                // Log error
                plugin.getLogger().log(Level.SEVERE, "Failed to initialise YAML storage: " + e.getMessage(), e);
                // Set unavailable
                available = false;
                return false;
            }
        });
    }

    /**
     * Loads all entries from the YAML file into the in-memory cache.
     */
    private void loadAllData() {
        // Loop through all top-level keys (UUIDs)
        for (String key : data.getKeys(false)) {
            try {
                // Parse UUID from key
                UUID uuid = UUID.fromString(key);

                // Support both new-format and legacy-format keys
                // Get name (try ".name", fallback to ".player_name", default "Unknown")
                String name = data.getString(key + ".name", data.getString(key + ".player_name", "Unknown"));
                // Get total (try ".total", fallback to ".total_converted", default 0)
                int total = data.getInt(key + ".total",
                    data.getInt(key + ".total_converted", 0));
                // Get updated timestamp (try ".updated", fallback to ".last_transaction", default now)
                long updated = data.getLong(key + ".updated",
                    data.getLong(key + ".last_transaction", System.currentTimeMillis()));

                // Create PlayerStats and add to cache
                cache.put(uuid, new PlayerStats(uuid, name, total, updated));

            } catch (IllegalArgumentException e) {
                // Invalid UUID → log warning and skip
                plugin.getLogger().warning("Skipping invalid UUID entry in player_stats.yml: " + key);
            }
        }
    }

    /**
     * Gets player statistics by UUID.
     * 
     * @param uuid Player UUID
     * @return CompletableFuture with PlayerStats, or null if not found
     */
    @Override
    public CompletableFuture<PlayerStats> getPlayerStats(UUID uuid) {
        // Cache hit — no I/O needed (return immediately)
        return CompletableFuture.completedFuture(cache.get(uuid));
    }

    /**
     * Saves player statistics.
     * 
     * @param stats PlayerStats to save
     * @return CompletableFuture that completes when save is done
     */
    @Override
    public CompletableFuture<Void> savePlayerStats(PlayerStats stats) {
        // Run async on executor
        return CompletableFuture.runAsync(() -> {
            // Update cache
            cache.put(stats.getUuid(), stats);
            // Write to YAML config
            writeStatToYaml(stats);
            // Flush to disk
            persistFile();
        }, asyncExecutor);
    }

    /**
     * Increments player's total converted amount.
     * 
     * @param uuid       Player UUID
     * @param playerName Player name
     * @param amount     Amount to add
     * @return CompletableFuture that completes when increment is done
     */
    @Override
    public CompletableFuture<Void> incrementConverted(UUID uuid, String playerName, int amount) {
        // Run async on executor
        return CompletableFuture.runAsync(() -> {
            // Get existing stats or create new entry
            PlayerStats stats = cache.computeIfAbsent(uuid,
                k -> new PlayerStats(k, playerName, 0, System.currentTimeMillis()));

            // Increment converted amount
            stats.addConverted(amount);
            // Write to YAML config
            writeStatToYaml(stats);
            // Flush to disk
            persistFile();
        }, asyncExecutor);
    }

    /**
     * Logs a transaction.
     * YAML backend does not persist transactions to this file;
     * file-based transaction logging is handled by TransactionLogger.
     * 
     * @param transaction Transaction to log
     * @return CompletableFuture (completes immediately)
     */
    @Override
    public CompletableFuture<Void> logTransaction(Transaction transaction) {
        // YAML backend doesn't log transactions to player_stats.yml
        // TransactionLogger handles flat-file logging separately
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Gets total emeralds converted by a player.
     * 
     * @param uuid Player UUID
     * @return CompletableFuture with total converted, or 0 if not found
     */
    @Override
    public CompletableFuture<Integer> getTotalConverted(UUID uuid) {
        // Get from cache
        PlayerStats stats = cache.get(uuid);
        // Return total (or 0 if null)
        return CompletableFuture.completedFuture(stats != null ? stats.getTotalConverted() : 0);
    }

    /**
     * Saves all cached data to disk.
     * 
     * @return CompletableFuture that completes when save is done
     */
    @Override
    public CompletableFuture<Void> saveAll() {
        // Run async on executor
        return CompletableFuture.runAsync(() -> {
            // Re-write every cached entry
            for (PlayerStats stats : cache.values()) {
                writeStatToYaml(stats);
            }
            // Flush to disk once
            persistFile();
            // Log success
            plugin.getLogger().info("YAML storage — saved " + cache.size() + " player(s).");
        }, asyncExecutor);
    }

    /**
     * Closes YAML storage (flushes data and shuts down executor).
     * 
     * @return CompletableFuture that completes when shutdown is done
     */
    @Override
    public CompletableFuture<Void> close() {
        // Submit the final flush onto the asyncExecutor so it runs AFTER all
        // pending savePlayerStats / incrementConverted tasks already queued.
        // Check if executor exists
        if (asyncExecutor == null || asyncExecutor.isShutdown()) {
            // Already closed → set unavailable and return
            available = false;
            return CompletableFuture.completedFuture(null);
        }

        // Run flush task on executor (runs after all pending tasks)
        return CompletableFuture.runAsync(() -> {
            // This task runs on asyncExecutor — all previously queued writes finish first
            // Check if data exists
            if (data != null) {
                // Write all cached stats
                for (PlayerStats stats : cache.values()) {
                    writeStatToYaml(stats);
                }
                // Flush to disk
                persistFile();
            }
            // Set unavailable
            available = false;
            // Log close message
            plugin.getLogger().info("YAML storage closed.");
        }, asyncExecutor).whenComplete((v, ex) -> {
            // Initiate orderly shutdown after the flush task completes
            asyncExecutor.shutdown();
            try {
                // Wait up to 5 seconds for termination
                if (!asyncExecutor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    // Force shutdown if timeout
                    asyncExecutor.shutdownNow();
                    plugin.getLogger().warning("YAML writer did not terminate cleanly within 5s.");
                }
            } catch (InterruptedException ie) {
                // Force shutdown if interrupted
                asyncExecutor.shutdownNow();
                // Set interrupt flag
                Thread.currentThread().interrupt();
            }
        });
    }

    /**
     * Checks if storage is available.
     * 
     * @return true if available, false otherwise
     */
    @Override
    public boolean isAvailable() {
        return available;
    }

    /**
     * Gets storage type.
     * 
     * @return YAML
     */
    @Override
    public StorageType getStorageType() {
        return StorageType.YAML;
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Writes a single {@link PlayerStats} entry into the in-memory
     * {@link YamlConfiguration} object (does NOT flush to disk).
     *
     * @param stats Player stats to serialise
     */
    private void writeStatToYaml(PlayerStats stats) {
        // Build path prefix (UUID as string)
        String path = stats.getUuid().toString();
        // Set name in YAML
        data.set(path + ".name", stats.getPlayerName());
        // Set total in YAML
        data.set(path + ".total", stats.getTotalConverted());
        // Set updated timestamp in YAML
        data.set(path + ".updated", stats.getLastUpdated());
    }

    /**
     * Saves the in-memory {@link YamlConfiguration} to disk.
     * All errors are logged but never propagated to callers.
     */
    private void persistFile() {
        try {
            // Write YAML to file
            data.save(dataFile);
        } catch (IOException e) {
            // Log error
            plugin.getLogger().log(Level.SEVERE, "YAML save failed: " + e.getMessage(), e);
        }
    }
}