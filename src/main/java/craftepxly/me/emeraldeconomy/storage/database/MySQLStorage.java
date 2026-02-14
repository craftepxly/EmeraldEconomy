package craftepxly.me.emeraldeconomy.storage.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import craftepxly.me.emeraldeconomy.EmeraldEconomy;
import craftepxly.me.emeraldeconomy.storage.IStorage;
import craftepxly.me.emeraldeconomy.storage.StorageType;
import craftepxly.me.emeraldeconomy.storage.model.PlayerStats;
import craftepxly.me.emeraldeconomy.transaction.Transaction;

import java.sql.*;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * MySQLStorage — MySQL storage implementation with HikariCP connection pooling.
 */
public class MySQLStorage implements IStorage {
    
    // Reference to main plugin instance
    private final EmeraldEconomy plugin;
    // Database configuration (host, username, password, etc.)
    private final DatabaseConfig config;
    // HikariCP connection pool (manages database connections)
    private HikariDataSource dataSource;
    // Executor for async database operations
    private ExecutorService asyncExecutor;
    // Availability flag
    private boolean available = false;
    
    /**
     * Constructs a new MySQLStorage.
     * 
     * @param plugin The main EmeraldEconomy plugin instance
     */
    public MySQLStorage(EmeraldEconomy plugin) {
        // Store plugin reference in memory
        this.plugin = plugin;
        // Create database config (reads from config.yml)
        this.config = new DatabaseConfig(plugin);
    }
    
    /**
     * Initializes MySQL storage (connection pool, tables, executor).
     * 
     * @return CompletableFuture with true if successful, false otherwise
     */
    @Override
    public CompletableFuture<Boolean> initialize() {
        // Run initialization asynchronously
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Setup HikariCP connection pool
                setupHikariCP();
                
                // Create tables if they don't exist
                createTables();
                
                // Setup async executor — named threads untuk debugging yang lebih mudah
                // Setup async executor — named threads for easier debugging
                // Read thread pool size from config (default 4)
                int poolSize = plugin.getConfig().getInt("storage.async.thread-pool-size", 4);
                // Create fixed thread pool
                asyncExecutor = Executors.newFixedThreadPool(poolSize, r -> {
                    // Create daemon thread with name
                    Thread t = new Thread(r, "EmeraldEconomy-MySQL-Worker");
                    t.setDaemon(true);
                    return t;
                });
                
                // Set available flag
                available = true;
                // Log success
                plugin.getLogger().info("MySQL storage initialized successfully!");
                plugin.getLogger().info("Connection pool: " + config.getMysqlPoolSize() + " connections");
                
                return true;
                
            } catch (Exception e) {
                // Log error
                plugin.getLogger().severe("Failed to initialize MySQL storage: " + e.getMessage());
                e.printStackTrace();
                // Set unavailable
                available = false;
                return false;
            }
        });
    }
    
    /**
     * Sets up HikariCP connection pool.
     * Configures pool size, timeouts, and performance optimizations.
     */
    private void setupHikariCP() {
        // Create HikariCP configuration
        HikariConfig hikariConfig = new HikariConfig();
        
        // JDBC URL (e.g., "jdbc:mysql://localhost:3306/EmeraldEconomy?...")
        hikariConfig.setJdbcUrl(config.getMysqlJdbcUrl());
        // Database username
        hikariConfig.setUsername(config.getMysqlUsername());
        // Database password
        hikariConfig.setPassword(config.getMysqlPassword());
        
        // Pool settings
        // Maximum number of connections in pool
        hikariConfig.setMaximumPoolSize(config.getMysqlPoolSize());
        // Minimum idle connections (20% of max)
        hikariConfig.setMinimumIdle(Math.max(2, config.getMysqlPoolSize() / 5));
        // Connection timeout (30 seconds default)
        hikariConfig.setConnectionTimeout(plugin.getConfig().getLong("storage.mysql.timeout", 30000L));
        // Idle timeout (10 minutes)
        hikariConfig.setIdleTimeout(600000); // 10 minutes
        // Max connection lifetime (30 minutes default)
        hikariConfig.setMaxLifetime(plugin.getConfig().getLong("storage.mysql.max-lifetime", 1800000L));
        
        // Connection test query (fast health check)
        hikariConfig.setConnectionTestQuery("SELECT 1");
        
        // Performance tuning (prepared statement caching, etc.)
        hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        hikariConfig.addDataSourceProperty("useServerPrepStmts", "true");
        hikariConfig.addDataSourceProperty("useLocalSessionState", "true");
        hikariConfig.addDataSourceProperty("rewriteBatchedStatements", "true");
        hikariConfig.addDataSourceProperty("cacheResultSetMetadata", "true");
        hikariConfig.addDataSourceProperty("cacheServerConfiguration", "true");
        hikariConfig.addDataSourceProperty("elideSetAutoCommits", "true");
        hikariConfig.addDataSourceProperty("maintainTimeStats", "false");
        
        // Pool name (for monitoring)
        hikariConfig.setPoolName("EmeraldEconomy-MySQL-Pool");
        
        // Create data source (connection pool)
        this.dataSource = new HikariDataSource(hikariConfig);
        
        // Log success
        plugin.getLogger().info("HikariCP initialized: " + config.getMysqlJdbcUrl());
    }
    
    /**
     * Buat tabel-tabel yang diperlukan kalau belum ada.
     * Pakai CREATE TABLE IF NOT EXISTS + CREATE INDEX IF NOT EXISTS
     * yang terpisah supaya kompatibel dengan MySQL dan MariaDB.
     * 
     * Creates required tables if they don't exist.
     * Uses separate CREATE TABLE IF NOT EXISTS + CREATE INDEX IF NOT EXISTS
     * for compatibility with MySQL and MariaDB.
     */
    private void createTables() throws SQLException {
        // Tabel statistik player
        // Player statistics table
        String createPlayerStats =
            "CREATE TABLE IF NOT EXISTS player_stats (" +
            "  uuid VARCHAR(36) PRIMARY KEY," +          // Player UUID (primary key)
            "  player_name VARCHAR(16) NOT NULL," +      // Player name
            "  total_converted INT NOT NULL DEFAULT 0," + // Total emeralds converted
            "  last_updated BIGINT NOT NULL" +           // Last update timestamp
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";   // InnoDB engine, UTF-8 charset

        // Tabel log transaksi
        // Transaction log table
        String createTransactions =
            "CREATE TABLE IF NOT EXISTS transactions_log (" +
            "  id INT AUTO_INCREMENT PRIMARY KEY," +           // Auto-increment ID
            "  uuid VARCHAR(36) NOT NULL," +                   // Player UUID
            "  player_name VARCHAR(16) NOT NULL," +            // Player name
            "  transaction_type ENUM('BUY','SELL') NOT NULL," + // Transaction type
            "  emerald_amount INT NOT NULL," +                 // Emerald amount
            "  money_amount DECIMAL(10,2) NOT NULL," +         // Money amount
            "  price_at_time DECIMAL(10,2) NOT NULL," +        // Price at transaction time
            "  transaction_id VARCHAR(32) NOT NULL UNIQUE," +  // Unique transaction ID
            "  timestamp BIGINT NOT NULL" +                    // Transaction timestamp
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";         // InnoDB engine, UTF-8 charset

        // Index dibuat terpisah dengan error-ignore supaya kompatibel
        // dengan MySQL 5.7 / MariaDB yang belum support CREATE INDEX IF NOT EXISTS
        // Indices created separately with error-ignore for compatibility
        // with MySQL 5.7 / MariaDB that don't support CREATE INDEX IF NOT EXISTS
        String[][] indices = {
            {"idx_ps_name",    "CREATE INDEX idx_ps_name    ON player_stats (player_name)"},
            {"idx_ps_updated", "CREATE INDEX idx_ps_updated ON player_stats (last_updated)"},
            {"idx_tx_uuid",    "CREATE INDEX idx_tx_uuid    ON transactions_log (uuid)"},
            {"idx_tx_ts",      "CREATE INDEX idx_tx_ts      ON transactions_log (timestamp)"},
            {"idx_tx_type_ts", "CREATE INDEX idx_tx_type_ts ON transactions_log (transaction_type, timestamp)"}
        };

        // Get connection from pool
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            // Create player_stats table
            stmt.execute(createPlayerStats);
            // Create transactions_log table
            stmt.execute(createTransactions);

            // Create indices
            for (String[] idx : indices) {
                try {
                    // Execute CREATE INDEX statement
                    stmt.execute(idx[1]);
                } catch (SQLException e) {
                    // Error 1061 = Duplicate key name (index already exists) — aman di-ignore
                    // Error 1061 = Duplicate key name (index already exists) — safe to ignore
                    if (e.getErrorCode() != 1061) {
                        // Log warning if different error
                        plugin.getLogger().warning("[MySQL] Tidak bisa buat index " + idx[0] + ": " + e.getMessage());
                    }
                }
            }

            // Log success
            plugin.getLogger().info("[MySQL] Tabel player_stats dan transactions_log siap.");

        } catch (SQLException e) {
            // Log error and rethrow
            plugin.getLogger().severe("[MySQL] Gagal membuat tabel: " + e.getMessage());
            throw e;
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
            // SQL query to select player stats
            String sql = "SELECT * FROM player_stats WHERE uuid = ?";
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                // Set UUID parameter (prevents SQL injection)
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
            } catch (SQLException e) {
                // Log error
                plugin.getLogger().severe("MySQL error getting player stats: " + e.getMessage());
            }
            
            // Player not found → return null
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
            // SQL: INSERT ... ON DUPLICATE KEY UPDATE (upsert)
            String sql = """
                INSERT INTO player_stats (uuid, player_name, total_converted, last_updated)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    player_name = VALUES(player_name),
                    total_converted = VALUES(total_converted),
                    last_updated = VALUES(last_updated)
                """;
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                // Set parameters
                stmt.setString(1, stats.getUuid().toString());
                stmt.setString(2, stats.getPlayerName());
                stmt.setInt(3, stats.getTotalConverted());
                stmt.setLong(4, stats.getLastUpdated());
                
                // Execute update
                stmt.executeUpdate();
                
            } catch (SQLException e) {
                // Log error
                plugin.getLogger().severe("MySQL error saving player stats: " + e.getMessage());
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
            // SQL: INSERT ... ON DUPLICATE KEY UPDATE with increment
            String sql = """
                INSERT INTO player_stats (uuid, player_name, total_converted, last_updated)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    total_converted = total_converted + VALUES(total_converted),
                    last_updated = VALUES(last_updated)
                """;
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                // Set parameters
                stmt.setString(1, uuid.toString());
                stmt.setString(2, playerName);
                stmt.setInt(3, amount);
                stmt.setLong(4, System.currentTimeMillis());
                
                // Execute update
                stmt.executeUpdate();
                
            } catch (SQLException e) {
                // Log error
                plugin.getLogger().severe("MySQL error incrementing converted: " + e.getMessage());
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
            String sql = """
                INSERT INTO transactions_log 
                (uuid, player_name, transaction_type, emerald_amount, money_amount, 
                 price_at_time, transaction_id, timestamp)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
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
                
            } catch (SQLException e) {
                // Log error
                plugin.getLogger().severe("MySQL error logging transaction: " + e.getMessage());
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
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
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
            } catch (SQLException e) {
                // Log error
                plugin.getLogger().severe("MySQL error getting total converted: " + e.getMessage());
            }
            
            // Not found → return 0
            return 0;
        }, asyncExecutor);
    }
    
    /**
     * Closes MySQL storage (shuts down pool and executor).
     * 
     * @return CompletableFuture that completes when shutdown is done
     */
    @Override
    public CompletableFuture<Void> close() {
        // Check if executor is null or already shutdown
        if (asyncExecutor == null || asyncExecutor.isShutdown()) {
            // Close data source if not closed
            if (dataSource != null && !dataSource.isClosed()) dataSource.close();
            // Set unavailable
            available = false;
            // Return completed future
            return CompletableFuture.completedFuture(null);
        }

        // Submit close onto asyncExecutor so all pending DB writes finish first.
        return CompletableFuture.runAsync(() -> {
            // Close data source (connection pool)
            if (dataSource != null && !dataSource.isClosed()) {
                dataSource.close();
                plugin.getLogger().info("MySQL connection pool closed.");
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
                    plugin.getLogger().warning("MySQL worker did not terminate cleanly within 5s.");
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
     * Checks if MySQL storage is available.
     * 
     * @return true if available, false otherwise
     */
    @Override
    public boolean isAvailable() {
        // Return true if available flag is set AND data source is not closed
        return available && dataSource != null && !dataSource.isClosed();
    }
    
    /**
     * Gets storage type.
     * 
     * @return MYSQL
     */
    @Override
    public StorageType getStorageType() {
        return StorageType.MYSQL;
    }
    
    /**
     * Saves all data (no-op for MySQL, auto-saves).
     * 
     * @return CompletableFuture (completes immediately)
     */
    @Override
    public CompletableFuture<Void> saveAll() {
        // MySQL auto-saves, no bulk save needed
        return CompletableFuture.completedFuture(null);
    }
}