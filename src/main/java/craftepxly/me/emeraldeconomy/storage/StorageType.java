package craftepxly.me.emeraldeconomy.storage;

/**
 * StorageType — represents the available storage backend types.
 */
public enum StorageType {
    
    /**
     * YAML file-based storage.
     * Data stored in plugins/EmeraldEconomy/player_stats.yml
     */
    YAML,
    
    /**
     * SQLite database storage.
     * Data stored in plugins/EmeraldEconomy/emeraldeconomy.db
     */
    SQLITE,
    
    /**
     * MySQL/MariaDB database storage.
     * Requires external database server.
     */
    MYSQL;
    
    /**
     * Parses storage type from string (case-insensitive).
     * 
     * @param value String value to parse (e.g., "mysql", "YAML", "SqLiTe")
     * @return StorageType enum, defaults to YAML if invalid
     */
    public static StorageType fromString(String value) {
        try {
            // Try to parse as enum (case-insensitive)
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            // Invalid value → return YAML as default
            return YAML; // Default fallback
        }
    }
    
    /**
     * Checks if this storage type requires external database.
     * 
     * @return true for MySQL, false for YAML/SQLite
     */
    public boolean requiresExternalDatabase() {
        // Only MySQL requires external database server
        return this == MYSQL;
    }
    
    /**
     * Checks if this storage type supports async operations.
     * 
     * @return true for SQLite/MySQL, false for YAML
     */
    public boolean supportsAsync() {
        // YAML doesn't support true async (file I/O is blocking)
        return this != YAML;
    }
}