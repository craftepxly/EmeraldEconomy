package craftepxly.me.emeraldeconomy.config;

import craftepxly.me.emeraldeconomy.EmeraldEconomy;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * MessageManager — handles multi-language message system.
 */
public class MessageManager {

    // Reference to main plugin instance
    private final EmeraldEconomy plugin;
    // Holds the current language file (e.g., messages_eng.yml) in memory
    private FileConfiguration messages;
    // MiniMessage parser for modern text formatting (gradients, hover text, etc.)
    private final MiniMessage miniMessage;
    // Legacy color code parser for &a, &c, etc. (backward compatibility)
    private final LegacyComponentSerializer legacySerializer;
    // Current locale name (e.g., "messages_eng" or "messages_id")
    private String currentLocale;

    /**
     * Constructs a new MessageManager and loads messages.
     * 
     * @param plugin The main EmeraldEconomy plugin instance
     */
    public MessageManager(EmeraldEconomy plugin) {
        // Store plugin reference in memory
        this.plugin = plugin;
        // Initialize MiniMessage parser (Adventure API)
        this.miniMessage = MiniMessage.miniMessage();
        // Initialize legacy color code parser (converts &a to §a)
        this.legacySerializer = LegacyComponentSerializer.legacyAmpersand();
        // Load messages from disk
        loadMessages();
    }

    /**
     * Loads messages from the configured locale file.
     * Reads from {@code messages/messages_*.yml} in the plugin folder.
     */
    public void loadMessages() {
        // Get locale setting from config.yml (default: "messages_id")
        currentLocale = plugin.getConfig().getString("messages_locale", "messages_id");

        // Create messages folder if it doesn't exist
        File messagesFolder = new File(plugin.getDataFolder(), "messages");
        if (!messagesFolder.exists()) {
            messagesFolder.mkdirs(); // Create the "messages" directory
        }

        // Build the file name (e.g., "messages_eng.yml")
        String fileName = currentLocale + ".yml";
        File messagesFile = new File(messagesFolder, fileName);

        // Save default message files from resources if they don't exist
        saveDefaultMessagesFiles(messagesFolder);

        // Check if the configured message file exists
        if (!messagesFile.exists()) {
            // File doesn't exist → log warning and fallback to messages_id.yml
            plugin.getLogger().warning("Messages file not found: " + fileName + ", using messages_id.yml as fallback");
            fileName = "messages_id.yml";
            messagesFile = new File(messagesFolder, fileName);
        }

        // Try to load the message file from disk
        if (messagesFile.exists()) {
            // Load YAML file into memory
            messages = YamlConfiguration.loadConfiguration(messagesFile);
            plugin.getLogger().info("Loaded messages from: messages/" + fileName);
        } else {
            // File still doesn't exist → try loading from embedded resources (inside the JAR)
            plugin.getLogger().warning("No messages file found, loading from embedded resources");
            InputStream stream = plugin.getResource("messages/" + currentLocale + ".yml");
            if (stream != null) {
                // Load from embedded resource
                messages = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
            } else {
                // Embedded resource also missing → log error and use empty config
                plugin.getLogger().severe("Could not load messages!");
                messages = new YamlConfiguration();
            }
        }
    }

    /**
     * Saves default message files from plugin resources to disk.
     * Only saves files that don't already exist.
     * 
     * @param messagesFolder The messages folder
     */
    private void saveDefaultMessagesFiles(File messagesFolder) {
        // List of default message files to create
        String[] defaultMessages = {"messages_eng.yml", "messages_id.yml"};

        // Loop through each file name
        for (String fileName : defaultMessages) {
            // Create File object for this message file
            File file = new File(messagesFolder, fileName);
            // Check if file already exists
            if (!file.exists()) {
                try {
                    // Get embedded resource from JAR
                    InputStream stream = plugin.getResource("messages/" + fileName);
                    if (stream != null) {
                        // Copy embedded file to disk
                        java.nio.file.Files.copy(stream, file.toPath());
                        plugin.getLogger().info("Created default messages file: messages/" + fileName);
                    }
                } catch (Exception e) {
                    // If copying fails, log warning
                    plugin.getLogger().warning("Could not save default messages file: " + fileName);
                }
            }
        }
    }

    /**
     * Reloads all message files from disk.
     */
    public void reload() {
        // Re-load messages (calls loadMessages())
        loadMessages();
    }

    /**
     * Gets the raw message string from the messages file without processing.
     * 
     * @param path The message key (e.g., "error.no_permission")
     * @return The raw message string (returns path if not found)
     */
    public String getRaw(String path) {
        // Get message from YAML file (returns path itself if key doesn't exist)
        String value = messages.getString(path, path);
        // Remove square brackets (cleanup for legacy format)
        value = value.replace("[", "").replace("]", "");
        return value;
    }

    /**
     * Gets a message with placeholder replacements.
     * Automatically adds {currency} and {currency_name} placeholders.
     * 
     * @param path         The message key
     * @param placeholders Map of placeholder keys and values
     * @return The processed message string
     */
    public String getMessage(String path, Map<String, String> placeholders) {
        // Get raw message string
        String message = getRaw(path);

        // FIX: Add automatic {currency} placeholder replacement
        // Get currency symbol from config.yml (e.g., "$")
        String currencySymbol = plugin.getConfigManager().getCurrencySymbol();

        // Create a new map that includes currency placeholders
        Map<String, String> allPlaceholders = new HashMap<>();
        // Copy user-provided placeholders if any
        if (placeholders != null) {
            allPlaceholders.putAll(placeholders);
        }

        // Add currency placeholder if not already present
        if (!allPlaceholders.containsKey("currency")) {
            allPlaceholders.put("currency", currencySymbol);
        }

        // Replace all placeholders in the message
        for (Map.Entry<String, String> entry : allPlaceholders.entrySet()) {
            // Replace {key} with value (e.g., {currency} → "$")
            message = message.replace("{" + entry.getKey() + "}", entry.getValue());
        }

        // Add prefix if not already present
        if (!message.contains("{prefix}") && !path.equals("prefix")) {
            // Get prefix from messages file
            String prefix = getRaw("prefix");
            // Prepend prefix to message
            message = prefix + message;
        } else {
            // Replace {prefix} placeholder
            message = message.replace("{prefix}", getRaw("prefix"));
        }

        // Return the fully processed message
        return message;
    }

    /**
     * Gets a message without custom placeholders.
     * 
     * @param path The message key
     * @return The processed message string
     */
    public String getMessage(String path) {
        // Call getMessage with null placeholders
        return getMessage(path, null);
    }

    /**
     * Sends a message to a CommandSender with placeholder replacements.
     * Supports multi-line messages (splits on \n).
     * 
     * @param sender       The recipient
     * @param path         The message key
     * @param placeholders Map of placeholder keys and values
     */
    public void send(CommandSender sender, String path, Map<String, String> placeholders) {
        // Get the processed message
        String message = getMessage(path, placeholders);

        // Check if message contains newline characters (multi-line)
        if (message.contains("\n")) {
            // Split message into lines
            String[] lines = message.split("\n");
            // Loop through each line
            for (String line : lines) {
                // Skip empty lines
                if (!line.trim().isEmpty()) {
                    // Parse line into Adventure Component
                    Component component = parseMessage(line);
                    // Send component to sender
                    sender.sendMessage(component);
                }
            }
        } else {
            // Single-line message → parse and send directly
            Component component = parseMessage(message);
            sender.sendMessage(component);
        }
    }

    /**
     * Sends a message to a CommandSender without custom placeholders.
     * 
     * @param sender The recipient
     * @param path   The message key
     */
    public void send(CommandSender sender, String path) {
        // Call send() with null placeholders
        send(sender, path, null);
    }

    /**
     * Sends a raw message (not from messages file) to a CommandSender.
     * 
     * @param sender  The recipient
     * @param message The raw message string
     */
    public void sendRaw(CommandSender sender, String message) {
        // Check if message is multi-line
        if (message.contains("\n")) {
            // Split into lines
            String[] lines = message.split("\n");
            for (String line : lines) {
                // Skip empty lines
                if (!line.trim().isEmpty()) {
                    // Parse and send
                    Component component = parseMessage(line);
                    sender.sendMessage(component);
                }
            }
        } else {
            // Single line → parse and send
            Component component = parseMessage(message);
            sender.sendMessage(component);
        }
    }

    /**
     * Parses a message string into an Adventure Component.
     * Tries MiniMessage format first, falls back to legacy color codes.
     * 
     * @param message The message string
     * @return The parsed Component
     */
    public Component parseMessage(String message) {
        // Try to parse as MiniMessage first (supports <gradient>, <hover>, etc.)
        try {
            return miniMessage.deserialize(message);
        } catch (Exception e) {
            // MiniMessage parsing failed → fall back to legacy color codes
            return legacySerializer.deserialize(message);
        }
    }

    /**
     * Gets the current locale name.
     * 
     * @return The locale name (e.g., "messages_eng")
     */
    public String getCurrentLocale() {
        return currentLocale;
    }

    /**
     * MessageBuilder — builder pattern for easy message construction.
     * Allows chaining placeholder additions like:
     * <pre>
     * builder("success.convert").placeholder("amount", 64).placeholder("money", 100.0).send(player);
     * </pre>
     */
    public static class MessageBuilder {
        // Reference to parent MessageManager
        private final MessageManager manager;
        // Message key to build
        private final String path;
        // Placeholders to replace
        private final Map<String, String> placeholders;

        /**
         * Constructs a new MessageBuilder.
         * 
         * @param manager The MessageManager instance
         * @param path    The message key
         */
        public MessageBuilder(MessageManager manager, String path) {
            // Store manager reference
            this.manager = manager;
            // Store message path
            this.path = path;
            // Initialize empty placeholder map
            this.placeholders = new HashMap<>();
        }

        /**
         * Adds a placeholder (String value).
         * 
         * @param key   Placeholder key (e.g., "player")
         * @param value Placeholder value (e.g., "Steve")
         * @return This builder (for chaining)
         */
        public MessageBuilder placeholder(String key, String value) {
            // Add key-value pair to placeholders map
            placeholders.put(key, value);
            // Return this for method chaining
            return this;
        }

        /**
         * Adds a placeholder (int value).
         * 
         * @param key   Placeholder key
         * @param value Placeholder value
         * @return This builder (for chaining)
         */
        public MessageBuilder placeholder(String key, int value) {
            // Convert int to String and add
            return placeholder(key, String.valueOf(value));
        }

        /**
         * Adds a placeholder (double value, formatted to 2 decimal places).
         * 
         * @param key   Placeholder key
         * @param value Placeholder value
         * @return This builder (for chaining)
         */
        public MessageBuilder placeholder(String key, double value) {
            // Format double to 2 decimal places and add
            return placeholder(key, String.format("%.2f", value));
        }

        /**
         * Sends the built message to a CommandSender.
         * 
         * @param sender The recipient
         */
        public void send(CommandSender sender) {
            // Call MessageManager's send() with our placeholders
            manager.send(sender, path, placeholders);
        }

        /**
         * Builds the message string without sending it.
         * 
         * @return The processed message string
         */
        public String build() {
            // Call MessageManager's getMessage() with our placeholders
            return manager.getMessage(path, placeholders);
        }

        /**
         * Builds the message as an Adventure Component.
         * 
         * @return The parsed Component
         */
        public Component buildComponent() {
            // Build message string and parse it
            return manager.parseMessage(build());
        }
    }

    /**
     * Creates a new MessageBuilder for the given message key.
     * 
     * @param path The message key
     * @return A new MessageBuilder
     */
    public MessageBuilder builder(String path) {
        // Create and return new MessageBuilder
        return new MessageBuilder(this, path);
    }
}