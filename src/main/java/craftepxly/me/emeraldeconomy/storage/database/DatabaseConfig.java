package craftepxly.me.emeraldeconomy.storage.database;

import craftepxly.me.emeraldeconomy.EmeraldEconomy;
import craftepxly.me.emeraldeconomy.storage.StorageType;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * DatabaseConfig — manages database configuration from config.yml.
 */
public class DatabaseConfig {

    // Reference to main plugin instance
    private final EmeraldEconomy plugin;
    // Storage type from config (YAML, SQLITE, or MYSQL)
    private final StorageType storageType;

    // MySQL configuration fields
    private final String mysqlHost;          // Database server hostname
    private final int mysqlPort;             // Database server port
    private final String mysqlDatabase;      // Database name
    private final String mysqlUsername;      // Database username
    private final String mysqlPassword;      // Database password
    private final int mysqlPoolSize;         // Connection pool size
    private final String mysqlProperties;    // Additional JDBC properties

    /**
     * Creates a new database configuration from config.yml.
     *
     * @param plugin Plugin instance
     */
    public DatabaseConfig(EmeraldEconomy plugin) {
        // Store plugin reference in memory
        this.plugin = plugin;
        // Get config.yml FileConfiguration
        FileConfiguration config = plugin.getConfig();

        // Storage type
        // Read storage.type from config (default "YAML")
        String typeStr = config.getString("storage.type", "YAML");
        // Parse string to StorageType enum
        this.storageType = StorageType.fromString(typeStr);

        // MySQL configuration
        // Read MySQL host (default "localhost")
        this.mysqlHost = config.getString("storage.mysql.host", "localhost");
        // Read MySQL port (default 3306)
        this.mysqlPort = config.getInt("storage.mysql.port", 3306);
        // Read database name (default "EmeraldEconomy")
        this.mysqlDatabase = config.getString("storage.mysql.database", "EmeraldEconomy");
        // Read username (default "root")
        this.mysqlUsername = config.getString("storage.mysql.username", "root");
        // Read password (default "password")
        this.mysqlPassword = config.getString("storage.mysql.password", "password");
        // Read connection pool size (default 10)
        this.mysqlPoolSize = config.getInt("storage.mysql.pool-size", 10);
        // [SECURITY FIX: HIGH-02/HIGH-03/LOW-01] Safer default properties
        // Removed autoReconnect=true (deprecated, HikariCP handles reconnection)
        // Changed useSSL=true, allowPublicKeyRetrieval=false as secure defaults
        this.mysqlProperties = config.getString("storage.mysql.properties",
                "useSSL=true&requireSSL=true&verifyServerCertificate=false&allowPublicKeyRetrieval=false");
    }

    /**
     * Gets the configured storage type.
     *
     * @return StorageType enum
     */
    public StorageType getStorageType() {
        // Return the parsed storage type
        return storageType;
    }

    /**
     * Gets MySQL host address.
     *
     * @return Host (e.g., "localhost" or "192.168.1.100")
     */
    public String getMysqlHost() {
        // Return MySQL host string
        return mysqlHost;
    }

    /**
     * Gets MySQL port number.
     *
     * @return Port (default: 3306)
     */
    public int getMysqlPort() {
        // Return MySQL port number
        return mysqlPort;
    }

    /**
     * Gets MySQL database name.
     *
     * @return Database name
     */
    public String getMysqlDatabase() {
        // Return MySQL database name
        return mysqlDatabase;
    }

    /**
     * Gets MySQL username.
     *
     * @return Username
     */
    public String getMysqlUsername() {
        // Return MySQL username
        return mysqlUsername;
    }

    /**
     * Gets MySQL password.
     *
     * @return Password
     */
    public String getMysqlPassword() {
        // Return MySQL password
        return mysqlPassword;
    }

    /**
     * Gets MySQL connection pool size.
     *
     * @return Pool size (default: 10)
     */
    public int getMysqlPoolSize() {
        // Return MySQL pool size
        return mysqlPoolSize;
    }

    /**
     * Gets MySQL connection properties.
     *
     * @return Properties string (e.g., "useSSL=true&requireSSL=true")
     */
    public String getMysqlProperties() {
        // Return MySQL JDBC properties string
        return mysqlProperties;
    }

    /**
     * Builds the complete MySQL JDBC URL.
     *
     * @return JDBC URL (e.g., "jdbc:mysql://localhost:3306/emeraldeconomy?useSSL=true")
     */
    public String getMysqlJdbcUrl() {
        // Format JDBC URL using host, port, database, and properties
        return String.format("jdbc:mysql://%s:%d/%s?%s",
                mysqlHost, mysqlPort, mysqlDatabase, mysqlProperties);
    }

    /**
     * Gets SQLite database file path.
     *
     * @return File path (plugins/EmeraldEconomy/emeraldeconomy.db)
     */
    public String getSqlitePath() {
        // Build absolute path to SQLite database file
        return plugin.getDataFolder().getAbsolutePath() + "/emeraldeconomy.db";
    }

    /**
     * Builds the complete SQLite JDBC URL.
     *
     * @return JDBC URL (e.g., "jdbc:sqlite:plugins/EmeraldEconomy/emeraldeconomy.db")
     */
    public String getSqliteJdbcUrl() {
        // Format SQLite JDBC URL
        return "jdbc:sqlite:" + getSqlitePath();
    }

    /**
     * Validates the database configuration.
     *
     * @return true if configuration is valid, false otherwise
     */
    public boolean isValid() {
        // Check if storage type is MySQL
        if (storageType == StorageType.MYSQL) {
            // Check MySQL credentials
            // Validate host is not empty
            if (mysqlHost == null || mysqlHost.isEmpty()) {
                plugin.getLogger().warning("MySQL host is not configured!");
                return false;
            }
            // Validate database name is not empty
            if (mysqlDatabase == null || mysqlDatabase.isEmpty()) {
                plugin.getLogger().warning("MySQL database is not configured!");
                return false;
            }
            // Validate username is not empty
            if (mysqlUsername == null || mysqlUsername.isEmpty()) {
                plugin.getLogger().warning("MySQL username is not configured!");
                return false;
            }
            // [SECURITY FIX: HIGH-01] Detect default credentials and refuse to start
            if ("root".equals(mysqlUsername) && "password".equals(mysqlPassword)) {
                plugin.getLogger().severe("══════════════════════════════════════════════════════════════");
                plugin.getLogger().severe("[SECURITY] Default MySQL credentials detected (root/password)!");
                plugin.getLogger().severe("[SECURITY] Change username & password in config.yml before use!");
                plugin.getLogger().severe("══════════════════════════════════════════════════════════════");
                return false;
            }
            // [SECURITY FIX: HIGH-08] Warn about using root MySQL user
            if ("root".equalsIgnoreCase(mysqlUsername)) {
                plugin.getLogger().warning("[SECURITY] Using MySQL 'root' user is not recommended.");
                plugin.getLogger().warning("[SECURITY] Create a dedicated user with only SELECT, INSERT, UPDATE, CREATE TABLE, INDEX permissions.");
            }
            // [SECURITY FIX: HIGH-02] Warn about disabled SSL
            if (mysqlProperties != null && mysqlProperties.contains("useSSL=false")) {
                plugin.getLogger().warning("[SECURITY] MySQL SSL is disabled — database traffic is unencrypted!");
            }
            // [SECURITY FIX: HIGH-03] Warn about MITM risk
            if (mysqlProperties != null && mysqlProperties.contains("allowPublicKeyRetrieval=true")
                    && mysqlProperties.contains("useSSL=false")) {
                plugin.getLogger().severe("[SECURITY] allowPublicKeyRetrieval=true with useSSL=false enables MITM credential theft!");
            }
            // [SECURITY FIX: LOW-01] Warn about deprecated autoReconnect
            if (mysqlProperties != null && mysqlProperties.contains("autoReconnect=true")) {
                plugin.getLogger().warning("[SECURITY] autoReconnect=true is deprecated in MySQL 8.0+. HikariCP handles reconnection automatically.");
            }
        }
        // Configuration is valid
        return true;
    }

    /**
     * Logs the current database configuration (sanitized).
     */
    public void logConfiguration() {
        // Log header
        plugin.getLogger().info("=== Database Configuration ===");
        // Log storage type
        plugin.getLogger().info("Storage Type: " + storageType);

        // Log details based on storage type
        switch (storageType) {
            case MYSQL:
                // Log MySQL configuration
                plugin.getLogger().info("MySQL Host: " + mysqlHost + ":" + mysqlPort);
                plugin.getLogger().info("MySQL Database: " + mysqlDatabase);
                plugin.getLogger().info("MySQL Username: " + mysqlUsername);
                // Log masked password (only show first 2 chars)
                plugin.getLogger().info("MySQL Password: " + maskPassword(mysqlPassword));
                plugin.getLogger().info("Connection Pool Size: " + mysqlPoolSize);
                break;

            case SQLITE:
                // Log SQLite configuration
                plugin.getLogger().info("SQLite Database: " + getSqlitePath());
                break;

            case YAML:
                // Log YAML configuration
                plugin.getLogger().info("YAML Files: player_stats.yml, transactions.log");
                break;
        }
    }

    /**
     * Masks password for logging (shows first 2 chars and ***).
     *
     * @param password Password to mask
     * @return Masked password (e.g., "pa***")
     */
    private String maskPassword(String password) {
        // Check if password is null or empty
        if (password == null || password.isEmpty()) {
            return "***";
        }
        // Check if password is too short
        if (password.length() <= 2) {
            return "***";
        }
        // Return first 2 chars + ***
        return password.substring(0, 2) + "***";
    }
}