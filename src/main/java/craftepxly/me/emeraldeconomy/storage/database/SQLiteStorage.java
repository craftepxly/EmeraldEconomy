package craftepxly.me.emeraldeconomy.storage.database;

import craftepxly.me.emeraldeconomy.EmeraldEconomy;
import craftepxly.me.emeraldeconomy.storage.IStorage;
import craftepxly.me.emeraldeconomy.storage.StorageType;
import craftepxly.me.emeraldeconomy.storage.model.PlayerStats;
import craftepxly.me.emeraldeconomy.transaction.Transaction;

import java.io.File;
import java.sql.*;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * SQLiteStorage — SQLite storage implementation with WAL mode for better concurrency.
 */
public class SQLiteStorage implements IStorage {

    // Reference to main plugin instance
    private final EmeraldEconomy plugin;
    // Database configuration (reads file path from config)
    private final DatabaseConfig config;
    // SQLite JDBC connection (single connection, not pooled)
    private Connection connection;
    // Executor for async database operations
    private ExecutorService asyncExecutor;
    // Availability flag
    private boolean available = false;

    /**
     * Creates a new SQLiteStorage instance.
     *
     * @param plugin Plugin instance
     */
    public SQLiteStorage(EmeraldEconomy plugin) {
        // Store plugin reference in memory
        this.plugin = plugin;
        // Create database config (reads from config.yml)
        this.config = new DatabaseConfig(plugin);
    }

    /**
     * Initializes SQLite storage (creates DB file, enables WAL, creates tables).
     * 
     * @return CompletableFuture with true if successful, false otherwise
     */
    @Override
    public CompletableFuture<Boolean> initialize() {
        // Run initialization asynchronously
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Ensure plugin data folder exists
                File dataFolder = plugin.getDataFolder();
                if (!dataFolder.exists()) {
                    // Create directory
                    dataFolder.mkdirs();
                }

                // Load SQLite JDBC driver (registers driver with DriverManager)
                Class.forName("org.sqlite.JDBC");

                // Open connection to SQLite database (creates file if doesn't exist)
                connection = DriverManager.getConnection(config.getSqliteJdbcUrl());

                // Enable WAL mode for better concurrency
                enableWalMode();

                // Create tables if they don't exist
                createTables();

                // SQLite JDBC Connection is not thread-safe for concurrent access.
                // Use a single-threaded executor to serialise all database operations.
                asyncExecutor = Executors.newSingleThreadExecutor(r -> {
                    // Create daemon thread with name
                    Thread t = new Thread(r, "EmeraldEconomy-SQLite-Worker");
                    t.setDaemon(true);
                    return t;
                });

                // Set available flag
                available = true;
                // Log success
                plugin.getLogger().info("SQLite storage initialized successfully!");
                plugin.getLogger().info("Database file: " + config.getSqlitePath());

                return true;

            } catch (Exception e) {
                // Log error
                plugin.getLogger().severe("Failed to initialize SQLite storage: " + e.getMessage());
                e.printStackTrace();
                // Set unavailable
                available = false;
                return false;
            }
        });
    }

    /**
     * Enables WAL (Write-Ahead Logging) mode for improved concurrency.
     *
     * @throws SQLException if the pragma cannot be set
     */
    private void enableWalMode() throws SQLException {
        // Check if WAL mode is enabled in config
        boolean walEnabled = plugin.getConfig().getBoolean("storage.sqlite.wal-mode", true);
        // Skip if disabled
        if (!walEnabled) return;

        // Execute PRAGMA statements to configure SQLite
        try (Statement stmt = connection.createStatement()) {
            // Enable WAL mode (allows concurrent reads during writes)
            stmt.execute("PRAGMA journal_mode=WAL");
            // Set synchronous mode to NORMAL (faster, still safe)
            stmt.execute("PRAGMA synchronous=NORMAL");
            // Set cache size (2000 pages = ~2MB)
            stmt.execute("PRAGMA cache_size=2000");
            // Store temporary tables in memory (faster)
            stmt.execute("PRAGMA temp_store=MEMORY");
            // Log success
            plugin.getLogger().info("SQLite WAL mode enabled.");
        }
    }

    /**
     * Creates database tables if they don't exist.
     *
     * @throws SQLException if table creation fails
     */
    private void createTables() throws SQLException {
        // SQL to create player_stats table
        String createPlayerStats =
            "CREATE TABLE IF NOT EXISTS player_stats (" +
            "    uuid TEXT PRIMARY KEY," +           // Player UUID (primary key)
            "    player_name TEXT NOT NULL," +       // Player name
            "    total_converted INTEGER NOT NULL DEFAULT 0," + // Total emeralds converted
            "    last_updated INTEGER NOT NULL" +    // Last update timestamp
            ")";

        // SQL to create transactions_log table
        String createTransactions =
            "CREATE TABLE IF NOT EXISTS transactions_log (" +
            "    id INTEGER PRIMARY KEY AUTOINCREMENT," + // Auto-increment ID
            "    uuid TEXT NOT NULL," +                   // Player UUID
            "    player_name TEXT NOT NULL," +            // Player name
            "    transaction_type TEXT NOT NULL," +       // Transaction type (BUY/SELL)
            "    emerald_amount INTEGER NOT NULL," +      // Emerald amount
            "    money_amount REAL NOT NULL," +           // Money amount
            "    price_at_time REAL NOT NULL," +          // Price at transaction time
            "    transaction_id TEXT NOT NULL UNIQUE," +  // Unique transaction ID
            "    timestamp INTEGER NOT NULL" +            // Transaction timestamp
            ")";

        // SQL to create index on transactions_log.uuid
        String createIdxUuid       = "CREATE INDEX IF NOT EXISTS idx_transactions_uuid ON transactions_log (uuid)";
        // SQL to create index on transactions_log.timestamp
        String createIdxTimestamp  = "CREATE INDEX IF NOT EXISTS idx_transactions_timestamp ON transactions_log (timestamp)";
        // SQL to create index on transactions_log.transaction_id
        String createIdxTxId       = "CREATE INDEX IF NOT EXISTS idx_transactions_tx_id ON transactions_log (transaction_id)";

        // Execute all CREATE statements
        try (Statement stmt = connection.createStatement()) {
            // Create player_stats table
            stmt.execute(createPlayerStats);
            // Create transactions_log table
            stmt.execute(createTransactions);
            // Create index on uuid
            stmt.execute(createIdxUuid);
            // Create index on timestamp
            stmt.execute(createIdxTimestamp);
            // Create index on transaction_id
            stmt.execute(createIdxTxId);
            // Log success
            plugin.getLogger().info("SQLite tables created/verified successfully!");
        }
    }

    /**
     * Checks and reconnects if the connection is stale.
     *
     * @throws SQLException if reconnection fails
     */
    private void ensureConnection() throws SQLException {
        // Check if connection is null or closed
        if (connection == null || connection.isClosed()) {
            // Log warning
            plugin.getLogger().warning("SQLite connection lost, attempting to reconnect...");
            // Reconnect to database
            connection = DriverManager.getConnection(config.getSqliteJdbcUrl());
            // Re-enable WAL mode
            enableWalMode();
            // Log success
            plugin.getLogger().info("SQLite reconnected successfully.");
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
        // Run query asynchronously on executor
        return CompletableFuture.supplyAsync(() -> {
            // SQL query
            String sql = "SELECT * FROM player_stats WHERE uuid = ?";

            try {
                // Ensure connection is valid
                ensureConnection();
                // Create prepared statement
                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    // Set UUID parameter
                    stmt.setString(1, uuid.toString());

                    // Execute query
                    try (ResultSet rs = stmt.executeQuery()) {
                        // Check if result exists
                        if (rs.next()) {
                            // Create and return PlayerStats object
                            return new PlayerStats(
                                UUID.fromString(rs.getString("uuid")),
                                rs.getString("player_name"),
                                rs.getInt("total_converted"),
                                rs.getLong("last_updated")
                            );
                        }
                    }
                }
            } catch (SQLException e) {
                // Log error
                plugin.getLogger().severe("SQLite error getting player stats: " + e.getMessage());
            }

            // Not found → return null
            return null;
        }, asyncExecutor);
    }

    /**
     * Saves player statistics (insert or update).
     * 
     * @param stats PlayerStats to save
     * @return CompletableFuture that completes when save is done
     */
    @Override
    public CompletableFuture<Void> savePlayerStats(PlayerStats stats) {
        // Run save asynchronously on executor
        return CompletableFuture.runAsync(() -> {
            // SQL: INSERT ... ON CONFLICT DO UPDATE (SQLite's upsert syntax)
            String sql =
                "INSERT INTO player_stats (uuid, player_name, total_converted, last_updated) " +
                "VALUES (?, ?, ?, ?) " +
                "ON CONFLICT(uuid) DO UPDATE SET " +
                "    player_name = excluded.player_name," +
                "    total_converted = excluded.total_converted," +
                "    last_updated = excluded.last_updated";

            try {
                // Ensure connection is valid
                ensureConnection();
                // Create prepared statement
                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    // Set parameters
                    stmt.setString(1, stats.getUuid().toString());
                    stmt.setString(2, stats.getPlayerName());
                    stmt.setInt(3, stats.getTotalConverted());
                    stmt.setLong(4, stats.getLastUpdated());
                    // Execute update
                    stmt.executeUpdate();
                }
            } catch (SQLException e) {
                // Log error
                plugin.getLogger().severe("SQLite error saving player stats: " + e.getMessage());
            }
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
        // Run increment asynchronously on executor
        return CompletableFuture.runAsync(() -> {
            // SQL: INSERT ... ON CONFLICT DO UPDATE with increment
            String sql =
                "INSERT INTO player_stats (uuid, player_name, total_converted, last_updated) " +
                "VALUES (?, ?, ?, ?) " +
                "ON CONFLICT(uuid) DO UPDATE SET " +
                "    total_converted = total_converted + excluded.total_converted," +
                "    last_updated = excluded.last_updated";

            try {
                // Ensure connection is valid
                ensureConnection();
                // Create prepared statement
                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    // Set parameters
                    stmt.setString(1, uuid.toString());
                    stmt.setString(2, playerName);
                    stmt.setInt(3, amount);
                    stmt.setLong(4, System.currentTimeMillis());
                    // Execute update
                    stmt.executeUpdate();
                }
            } catch (SQLException e) {
                // Log error
                plugin.getLogger().severe("SQLite error incrementing converted: " + e.getMessage());
            }
        }, asyncExecutor);
    }

    /**
     * Logs a transaction to database.
     * 
     * @param transaction Transaction to log
     * @return CompletableFuture that completes when logging is done
     */
    @Override
    public CompletableFuture<Void> logTransaction(Transaction transaction) {
        // Run insert asynchronously on executor
        return CompletableFuture.runAsync(() -> {
            // SQL: INSERT transaction
            String sql =
                "INSERT INTO transactions_log " +
                "(uuid, player_name, transaction_type, emerald_amount, money_amount, " +
                " price_at_time, transaction_id, timestamp) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

            try {
                // Ensure connection is valid
                ensureConnection();
                // Create prepared statement
                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    // Set parameters
                    stmt.setString(1, transaction.getPlayerUuid().toString());
                    stmt.setString(2, transaction.getPlayerName());
                    stmt.setString(3, transaction.getType().name());
                    stmt.setInt(4, transaction.getEmeraldAmount());
                    stmt.setDouble(5, transaction.getMoneyAmount());
                    stmt.setDouble(6, transaction.getPriceAtTime());
                    stmt.setString(7, transaction.getTransactionId());
                    stmt.setLong(8, transaction.getTimestamp().toEpochMilli());
                    // Execute insert
                    stmt.executeUpdate();
                }
            } catch (SQLException e) {
                // Log error
                plugin.getLogger().severe("SQLite error logging transaction: " + e.getMessage());
            }
        }, asyncExecutor);
    }

    /**
     * Gets total emeralds converted by a player.
     * 
     * @param uuid Player UUID
     * @return CompletableFuture with total converted, or 0 if not found
     */
    @Override
    public CompletableFuture<Integer> getTotalConverted(UUID uuid) {
        // Run query asynchronously on executor
        return CompletableFuture.supplyAsync(() -> {
            // SQL query
            String sql = "SELECT total_converted FROM player_stats WHERE uuid = ?";

            try {
                // Ensure connection is valid
                ensureConnection();
                // Create prepared statement
                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    // Set UUID parameter
                    stmt.setString(1, uuid.toString());

                    // Execute query
                    try (ResultSet rs = stmt.executeQuery()) {
                        // Check if result exists
                        if (rs.next()) {
                            // Return total_converted
                            return rs.getInt("total_converted");
                        }
                    }
                }
            } catch (SQLException e) {
                // Log error
                plugin.getLogger().severe("SQLite error getting total converted: " + e.getMessage());
            }

            // Not found → return 0
            return 0;
        }, asyncExecutor);
    }

    /**
     * Closes SQLite storage (flushes WAL and shuts down executor).
     * 
     * @return CompletableFuture that completes when shutdown is done
     */
    @Override
    public CompletableFuture<Void> close() {
        // Submit the close task onto asyncExecutor so all pending DB ops finish first.
        // Check if executor is null or already shutdown
        if (asyncExecutor == null || asyncExecutor.isShutdown()) {
            // Set unavailable
            available = false;
            // Return completed future
            return CompletableFuture.completedFuture(null);
        }

        // Run close task on executor (runs after all pending tasks)
        return CompletableFuture.runAsync(() -> {
            try {
                // Check if connection exists and is open
                if (connection != null && !connection.isClosed()) {
                    // Flush WAL to main database before closing
                    try (Statement stmt = connection.createStatement()) {
                        // Execute WAL checkpoint (writes WAL to main DB file)
                        stmt.execute("PRAGMA wal_checkpoint(FULL)");
                    }
                    // Close connection
                    connection.close();
                    // Log success
                    plugin.getLogger().info("SQLite connection closed.");
                }
            } catch (SQLException e) {
                // Log error
                plugin.getLogger().warning("Error closing SQLite connection: " + e.getMessage());
            }
            // Set unavailable
            available = false;
        }, asyncExecutor).whenComplete((v, ex) -> {
            // Shutdown executor
            asyncExecutor.shutdown();
            try {
                // Wait up to 5 seconds for termination
                if (!asyncExecutor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    // Force shutdown if timeout
                    asyncExecutor.shutdownNow();
                    plugin.getLogger().warning("SQLite worker did not terminate cleanly within 5s.");
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
     * Checks if SQLite storage is available.
     * 
     * @return true if available, false otherwise
     */
    @Override
    public boolean isAvailable() {
        try {
            // Return true if available flag is set AND connection is open
            return available && connection != null && !connection.isClosed();
        } catch (SQLException e) {
            // Exception checking connection status → return false
            return false;
        }
    }

    /**
     * Gets storage type.
     * 
     * @return SQLITE
     */
    @Override
    public StorageType getStorageType() {
        return StorageType.SQLITE;
    }

    /**
     * Saves all data (no-op for SQLite, auto-saves).
     * 
     * @return CompletableFuture (completes immediately)
     */
    @Override
    public CompletableFuture<Void> saveAll() {
        // SQLite auto-saves on each statement; no additional bulk operation needed
        return CompletableFuture.completedFuture(null);
    }
}