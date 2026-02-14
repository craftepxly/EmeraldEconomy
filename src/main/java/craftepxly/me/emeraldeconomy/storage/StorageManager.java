package craftepxly.me.emeraldeconomy.storage;

import craftepxly.me.emeraldeconomy.EmeraldEconomy;
import craftepxly.me.emeraldeconomy.storage.database.MySQLStorage;
import craftepxly.me.emeraldeconomy.storage.database.SQLiteStorage;
import craftepxly.me.emeraldeconomy.storage.model.PlayerStats;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * StorageManager — the "parking attendant" for all plugin data operations.
 */
public class StorageManager {

    // Reference to main plugin instance
    private final EmeraldEconomy plugin;
    // Currently active storage backend
    private IStorage activeStorage;
    // Storage type configured in config.yml
    private final StorageType configuredType;

    // --- Fallback & reconnect state ---
    /** YAML cache darurat saat MySQL/SQLite mendadak down */
    // Emergency YAML cache used when MySQL/SQLite goes down
    private YAMLStorage emergencyCache;
    // Flag indicating if we're currently using emergency cache
    private final AtomicBoolean usingEmergencyCache = new AtomicBoolean(false);
    // Scheduled executor for reconnection monitoring
    private ScheduledExecutorService reconnectScheduler;

    /** Nama stamp file — kalau sudah ada, skip migrasi YAML→DB */
    // Name of migration stamp file — if exists, skip YAML→DB migration
    private static final String MIGRATION_STAMP = ".migrated";

    /**
     * Constructs a new StorageManager.
     * 
     * @param plugin The main EmeraldEconomy plugin instance
     */
    public StorageManager(EmeraldEconomy plugin) {
        // Store plugin reference in memory
        this.plugin = plugin;
        // Read storage type from config.yml (default: YAML)
        this.configuredType = StorageType.fromString(
            plugin.getConfig().getString("storage.type", "YAML")
        );
    }

    // =========================================================================
    // Inisialisasi utama
    // Main initialization
    // =========================================================================

    /**
     * Coba nyalakan storage sesuai config, dengan fallback otomatis.
     * Kalau berhasil ke SQL backend, auto-migrate data dari YAML juga.
     * 
     * Tries to start storage according to config, with automatic fallback.
     * If successful with SQL backend, auto-migrates data from YAML too.
     * 
     * @return true if at least one storage backend started successfully
     */
    public boolean initialize() {
        // Start with configured type
        StorageType typeToTry = configuredType;

        // Try each storage type in fallback order
        while (typeToTry != null) {
            // Log attempt
            plugin.getLogger().info("[Storage] Mencoba backend: " + typeToTry + "...");

            // Create storage instance for this type
            IStorage candidate = createStorage(typeToTry);
            // Check if creation succeeded
            if (candidate != null) {
                try {
                    // Try to initialize this storage (may involve connecting to DB)
                    if (candidate.initialize().join()) {
                        // Success! Use this storage
                        activeStorage = candidate;

                        // Check if we're using a fallback
                        if (typeToTry != configuredType) {
                            // Log fallback warning
                            plugin.getLogger().warning(
                                "[Storage] '" + configuredType + "' gagal, pakai fallback: " + typeToTry
                            );
                        } else {
                            // Log success
                            plugin.getLogger().info("[Storage] Backend aktif: " + typeToTry);
                        }

                        // Kalau SQL, cek perlu migrasi dari YAML nggak
                        // If SQL, check if we need to migrate from YAML
                        if (typeToTry != StorageType.YAML) {
                            runMigrationIfNeeded();
                        }

                        // MySQL? Nyalakan monitor reconnect
                        // MySQL? Start reconnect monitor
                        if (typeToTry == StorageType.MYSQL) {
                            startReconnectMonitor();
                        }

                        // Return success
                        return true;
                    }
                } catch (Exception e) {
                    // Initialization failed → log warning and try next fallback
                    plugin.getLogger().warning("[Storage] " + typeToTry + " error: " + e.getMessage());
                }
            }
            // Get next fallback type
            typeToTry = getNextFallback(typeToTry);
        }

        // All storage types failed → log critical error
        plugin.getLogger().severe("[Storage] Semua backend gagal! Plugin tidak bisa berjalan.");
        return false;
    }

    // =========================================================================
    // Poin 4: Migrasi YAML → SQL
    // Point 4: YAML → SQL Migration
    // =========================================================================

    /**
     * Baca player_stats.yml dan import semua datanya ke database aktif.
     * Hanya dijalankan sekali — setelah selesai diberi stamp file .migrated
     * supaya nggak dijalankan lagi tiap restart.
     * 
     * Reads player_stats.yml and imports all data to active database.
     * Only runs once — after completion creates .migrated stamp file
     * so it won't run again on every restart.
     */
    private void runMigrationIfNeeded() {
        // Get plugin data folder
        File dataFolder = plugin.getDataFolder();
        // Create File object for stamp file
        File stampFile  = new File(dataFolder, MIGRATION_STAMP);
        // Create File object for YAML file
        File yamlFile   = new File(dataFolder, "player_stats.yml");

        // Check if migration already done, or no YAML file exists
        if (stampFile.exists() || !yamlFile.exists() || yamlFile.length() == 0) {
            // Skip migration
            return;
        }

        // Log migration start
        plugin.getLogger().info("[Storage] Ditemukan player_stats.yml — mulai migrasi ke "
            + activeStorage.getStorageType() + "...");

        // Jalankan sepenuhnya di async thread (poin 5)
        // Run entirely on async thread (point 5)
        CompletableFuture.runAsync(() -> {
            try {
                // Load YAML file
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(yamlFile);
                // Counter for migrated players
                int count = 0;

                // Loop through all keys (UUIDs) in YAML
                for (String key : yaml.getKeys(false)) {
                    try {
                        // Parse UUID from key
                        UUID uuid  = UUID.fromString(key);
                        // Get player name (supports both old and new format)
                        String name = yaml.getString(key + ".name",
                                      yaml.getString(key + ".player_name", "Unknown"));
                        // Get total converted (supports both old and new format)
                        int total   = yaml.getInt(key + ".total",
                                      yaml.getInt(key + ".total_converted", 0));
                        // Get last updated timestamp (supports both old and new format)
                        long upd    = yaml.getLong(key + ".updated",
                                      yaml.getLong(key + ".last_transaction",
                                      System.currentTimeMillis()));

                        // Save to active storage (SQL database)
                        activeStorage.savePlayerStats(new PlayerStats(uuid, name, total, upd)).join();
                        // Increment counter
                        count++;
                    } catch (IllegalArgumentException ignored) { 
                        /* bukan UUID valid, skip */ 
                        /* not a valid UUID, skip */
                    }
                }

                // Create stamp file to mark migration as complete
                stampFile.createNewFile();
                // Log success
                plugin.getLogger().info("[Storage] Migrasi selesai! " + count
                    + " player berhasil dipindah ke " + activeStorage.getStorageType() + ".");

                // Hapus file YAML setelah migrasi berhasil — konsisten dengan transactions.log
                // yang juga tidak dibuat saat storage type adalah SQLite/MySQL
                // Delete YAML file after successful migration — consistent with transactions.log
                // which is also not created when storage type is SQLite/MySQL
                if (yamlFile.delete()) {
                    plugin.getLogger().info("[Storage] File player_stats.yml dihapus — data sekarang di database.");
                } else {
                    plugin.getLogger().warning("[Storage] Tidak bisa hapus player_stats.yml — silakan hapus manual.");
                }

            } catch (Exception e) {
                // Migration failed → log error
                plugin.getLogger().severe("[Storage] Migrasi gagal: " + e.getMessage());
                plugin.getLogger().severe("[Storage] File player_stats.yml TIDAK DIHAPUS karena migrasi gagal.");
            }
        });
    }

    // =========================================================================
    // Poin 6: Fallback otomatis + reconnect MySQL
    // Point 6: Automatic fallback + MySQL reconnect
    // =========================================================================

    /**
     * Nyalakan scheduler yang setiap 30 detik ngecek apakah MySQL masih hidup.
     * Kalau mati → aktifkan emergency cache YAML lokal.
     * Kalau balik online → flush cache ke MySQL lagi.
     * 
     * Starts scheduler that checks every 30 seconds if MySQL is still alive.
     * If down → activate local YAML emergency cache.
     * If back online → flush cache to MySQL again.
     */
    private void startReconnectMonitor() {
        // Create single-threaded scheduler
        reconnectScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            // Create daemon thread with name
            Thread t = new Thread(r, "EmeraldEconomy-Reconnect-Monitor");
            t.setDaemon(true);
            return t;
        });

        // Schedule task to run every 30 seconds
        reconnectScheduler.scheduleAtFixedRate(() -> {
            // Check if active storage is unavailable
            if (!activeStorage.isAvailable()) {
                // Check if not already using emergency cache
                if (!usingEmergencyCache.get()) {
                    // Activate emergency cache
                    activateEmergencyCache();
                }
                // Try to reconnect to MySQL
                tryReconnectMySQL();
            } else if (usingEmergencyCache.get()) {
                // MySQL is back online and we're using cache → flush cache
                flushEmergencyCacheToMySQL();
            }
        }, 30, 30, TimeUnit.SECONDS); // Initial delay: 30s, Period: 30s
    }

    /**
     * Activates emergency YAML cache when MySQL goes down.
     */
    private void activateEmergencyCache() {
        // Log warning
        plugin.getLogger().warning(
            "[Storage] MySQL terputus! Mengaktifkan emergency cache (YAML lokal)..."
        );
        try {
            // Create emergency YAML storage
            emergencyCache = new YAMLStorage(plugin, "emergency_cache.yml");
            // Initialize it
            emergencyCache.initialize().join();
            // Set flag
            usingEmergencyCache.set(true);
            // Log success
            plugin.getLogger().warning(
                "[Storage] Emergency cache aktif. Data tersimpan lokal di emergency_cache.yml"
            );
        } catch (Exception e) {
            // Log error if emergency cache fails
            plugin.getLogger().severe("[Storage] Emergency cache gagal: " + e.getMessage());
        }
    }

    /**
     * Tries to reconnect to MySQL.
     */
    private void tryReconnectMySQL() {
        // Log attempt
        plugin.getLogger().info("[Storage] Mencoba reconnect ke MySQL...");
        try {
            // Create new MySQL connection
            MySQLStorage newConn = new MySQLStorage(plugin);
            // Try to initialize
            if (newConn.initialize().join()) {
                // Success → switch to new connection
                activeStorage = newConn;
                // Log success
                plugin.getLogger().info("[Storage] Reconnect MySQL berhasil!");
            }
        } catch (Exception e) {
            // Reconnect failed → log warning
            plugin.getLogger().warning("[Storage] Reconnect MySQL gagal: " + e.getMessage());
        }
    }

    /**
     * Flushes emergency cache data to MySQL when connection is restored.
     */
    private void flushEmergencyCacheToMySQL() {
        // Check if emergency cache exists
        if (emergencyCache == null) return;
        // Log flush start
        plugin.getLogger().info("[Storage] MySQL online lagi — sinkronisasi emergency cache...");

        // Run async
        CompletableFuture.runAsync(() -> {
            try {
                // Create File object for cache file
                File cacheFile = new File(plugin.getDataFolder(), "emergency_cache.yml");
                // Check if file exists
                if (cacheFile.exists()) {
                    // Load YAML file
                    YamlConfiguration yaml = YamlConfiguration.loadConfiguration(cacheFile);
                    // Counter
                    int count = 0;
                    // Loop through all keys
                    for (String key : yaml.getKeys(false)) {
                        try {
                            // Parse UUID
                            UUID uuid   = UUID.fromString(key);
                            // Get name
                            String name = yaml.getString(key + ".name", "Unknown");
                            // Get total
                            int total   = yaml.getInt(key + ".total", 0);
                            // Get updated timestamp
                            long upd    = yaml.getLong(key + ".updated", System.currentTimeMillis());
                            // Save to MySQL
                            activeStorage.savePlayerStats(
                                new PlayerStats(uuid, name, total, upd)).join();
                            // Increment counter
                            count++;
                        } catch (Exception ignored) {}
                    }
                    // Log success
                    plugin.getLogger().info(
                        "[Storage] Sinkronisasi selesai! " + count + " data player dikirim ke MySQL."
                    );
                    // Delete cache file
                    cacheFile.delete();
                }
                // Close emergency cache
                emergencyCache.close().join();
                // Clear reference
                emergencyCache = null;
                // Clear flag
                usingEmergencyCache.set(false);
            } catch (Exception e) {
                // Log error
                plugin.getLogger().severe("[Storage] Flush emergency cache gagal: " + e.getMessage());
            }
        });
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Ambil storage aktif.
     * Kalau lagi pakai emergency cache (MySQL down), kembalikan itu
     * supaya data player nggak hilang.
     * 
     * Gets active storage.
     * If using emergency cache (MySQL down), return that
     * so player data doesn't get lost.
     * 
     * @return Active storage instance
     */
    public IStorage getStorage() {
        // Check if using emergency cache
        if (usingEmergencyCache.get() && emergencyCache != null) {
            // Return emergency cache
            return emergencyCache;
        }
        // Return active storage
        return activeStorage;
    }

    /**
     * Gets the configured storage type from config.yml.
     * 
     * @return Configured StorageType
     */
    public StorageType getConfiguredType() { 
        return configuredType; 
    }

    /**
     * Checks if currently using emergency cache.
     * 
     * @return true if using emergency cache, false otherwise
     */
    public boolean isUsingEmergencyCache() { 
        return usingEmergencyCache.get(); 
    }

    /**
     * Shutdown bersih — hentikan scheduler, flush semua data, tutup koneksi.
     * 
     * Clean shutdown — stop scheduler, flush all data, close connections.
     */
    public void shutdown() {
        // Check if reconnect scheduler exists
        if (reconnectScheduler != null && !reconnectScheduler.isShutdown()) {
            // Shut down scheduler
            reconnectScheduler.shutdown();
            try { 
                // Wait up to 5 seconds for termination
                reconnectScheduler.awaitTermination(5, TimeUnit.SECONDS); 
            }
            catch (InterruptedException ie) { 
                // Interrupted → set interrupt flag
                Thread.currentThread().interrupt(); 
            }
        }
        // Check if emergency cache exists
        if (emergencyCache != null) {
            try { 
                // Close emergency cache
                emergencyCache.close().join(); 
            }
            catch (Exception e) {
                // Log error
                plugin.getLogger().warning("[Storage] Error menutup emergency cache: " + e.getMessage());
            }
        }
        // Check if active storage exists
        if (activeStorage != null) {
            // Log shutdown message
            plugin.getLogger().info("[Storage] Menutup " + activeStorage.getStorageType() + "...");
            try { 
                // Close active storage
                activeStorage.close().join(); 
            }
            catch (Exception e) {
                // Log error
                plugin.getLogger().warning("[Storage] Error saat shutdown: " + e.getMessage());
            }
        }
    }

    /**
     * Creates a storage instance for the given type.
     * 
     * @param type Storage type
     * @return Storage instance
     */
    private IStorage createStorage(StorageType type) {
        // Use switch expression to create appropriate storage
        return switch (type) {
            case MYSQL  -> new MySQLStorage(plugin);
            case SQLITE -> new SQLiteStorage(plugin);
            case YAML   -> new YAMLStorage(plugin);
        };
    }

    /**
     * Gets the next fallback storage type.
     * 
     * @param current Current storage type
     * @return Next fallback type, or null if no more fallbacks
     */
    private StorageType getNextFallback(StorageType current) {
        // Return next fallback in chain: MySQL → SQLite → YAML → null
        return switch (current) {
            case MYSQL  -> StorageType.SQLITE;
            case SQLITE -> StorageType.YAML;
            case YAML   -> null;
        };
    }
}