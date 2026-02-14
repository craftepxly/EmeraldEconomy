package craftepxly.me.emeraldeconomy.gui;

import craftepxly.me.emeraldeconomy.EmeraldEconomy;
import craftepxly.me.emeraldeconomy.transaction.TransactionResult;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.logging.Level;

/**
 * ActionExecutor — executes actions defined in menu.yml.
 */
public class ActionExecutor {
    
    // Reference to main plugin instance
    private final EmeraldEconomy plugin;
    
    /**
     * Constructs a new ActionExecutor.
     * 
     * @param plugin The main EmeraldEconomy plugin instance
     */
    public ActionExecutor(EmeraldEconomy plugin) {
        // Store plugin reference
        this.plugin = plugin;
    }
    
    /**
     * Executes a list of actions for a player.
     * 
     * @param player  The player who triggered the actions
     * @param actions List of action strings from menu.yml
     */
    public void executeActions(Player player, List<String> actions) {
        // Loop through each action string
        for (String action : actions) {
            // Execute this action
            executeAction(player, action);
        }
    }
    
    /**
     * Executes a single action for a player.
     * 
     * @param player The player
     * @param action The action string (e.g., "[console] give {player} diamond 1")
     */
    private void executeAction(Player player, String action) {
        // Remove leading/trailing whitespace
        action = action.trim();
        
        // Skip empty actions
        if (action.isEmpty()) return;
        
        try {
            // Parse action type (format: [type] value)
            // Check if action starts with [ and contains ]
            if (action.startsWith("[") && action.contains("]")) {
                // Find the closing bracket position
                int endBracket = action.indexOf("]");
                // Extract the action type (e.g., "console", "player", "message")
                String type = action.substring(1, endBracket).toLowerCase();
                // Extract the action value (everything after the closing bracket)
                String value = action.substring(endBracket + 1);
                
                // Route to the appropriate handler based on type
                switch (type) {
                    case "console":
                        // Run command as console
                        executeConsoleCommand(player, value);
                        break;
                        
                    case "player":
                        // Run command as player
                        executePlayerCommand(player, value);
                        break;
                        
                    case "message":
                        // Send message to player
                        sendMessage(player, value);
                        break;
                        
                    case "sound":
                        // Play sound to player
                        playSound(player, value);
                        break;
                        
                    case "close":
                        // Close player's inventory
                        closeMenu(player);
                        break;
                        
                    case "refresh":
                        // Refresh the current menu
                        refreshMenu(player);
                        break;
                        
                    case "async":
                        // Execute async action (buy/sell transactions)
                        executeAsyncAction(player, value);
                        break;
                        
                    case "requirement":
                        // Requirements are checked elsewhere (not an action)
                        break;
                        
                    default:
                        // Unknown action type → log warning
                        plugin.getLogger().warning("Unknown action type: " + type);
                }
            }
        } catch (Exception e) {
            // If anything goes wrong, log the error
            plugin.getLogger().log(Level.SEVERE, "Error executing action: " + action, e);
        }
    }
    
    /**
     * Executes a command as console.
     * 
     * @param player  The player (for {player} placeholder)
     * @param command The command to execute
     */
    private void executeConsoleCommand(Player player, String command) {
        // Replace {player} placeholder with player's name
        command = command.replace("{player}", player.getName());
        // Store final command in a final variable (required for lambda)
        String finalCommand = command;
        
        // Execute command on main thread (Bukkit requirement)
        Bukkit.getScheduler().runTask(plugin, () -> {
            // Dispatch command as console
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand);
        });
    }
    
    /**
     * Executes a command as the player.
     * 
     * @param player  The player
     * @param command The command to execute
     */
    private void executePlayerCommand(Player player, String command) {
        // Execute command on main thread
        Bukkit.getScheduler().runTask(plugin, () -> {
            // Make player execute command
            player.performCommand(command);
        });
    }
    
    /**
     * Sends a message to the player.
     * 
     * @param player  The player
     * @param message The message to send
     */
    private void sendMessage(Player player, String message) {
        // Replace & with § (legacy color codes)
        message = message.replace("&", "§");
        // Store in final variable for lambda
        String finalMessage = message;
        
        // Send message on main thread
        Bukkit.getScheduler().runTask(plugin, () -> {
            // Send raw message to player
            plugin.getMessageManager().sendRaw(player, finalMessage);
        });
    }
    
    /**
     * Plays a sound to the player.
     * 
     * @param player      The player
     * @param soundConfig Sound configuration (format: "SOUND:volume:pitch")
     */
    private void playSound(Player player, String soundConfig) {
        try {
            // Split sound config by colon (e.g., "ENTITY_EXPERIENCE_ORB_PICKUP:1.0:1.0")
            String[] parts = soundConfig.split(":");
            // Parse sound enum (e.g., "ENTITY_EXPERIENCE_ORB_PICKUP")
            Sound sound = Sound.valueOf(parts[0].toUpperCase());
            // Parse volume (default 1.0 if not provided)
            float volume = parts.length > 1 ? Float.parseFloat(parts[1]) : 1.0f;
            // Parse pitch (default 1.0 if not provided)
            float pitch = parts.length > 2 ? Float.parseFloat(parts[2]) : 1.0f;
            
            // Play sound on main thread
            Bukkit.getScheduler().runTask(plugin, () -> {
                // Play sound at player's location
                player.playSound(player.getLocation(), sound, volume, pitch);
            });
        } catch (Exception e) {
            // Invalid sound config → log warning
            plugin.getLogger().warning("Invalid sound configuration: " + soundConfig);
        }
    }
    
    /**
     * Closes the player's inventory.
     * 
     * @param player The player
     */
    private void closeMenu(Player player) {
        // Close inventory on main thread
        Bukkit.getScheduler().runTask(plugin, () -> {
            // Close player's open inventory
            player.closeInventory();
        });
    }
    
    /**
     * Refreshes the player's current menu.
     * 
     * @param player The player
     */
    private void refreshMenu(Player player) {
        // Refresh on main thread
        Bukkit.getScheduler().runTask(plugin, () -> {
            // Check if player has a menu open
            if (plugin.getMenuManager().hasMenuOpen(player)) {
                // Update the inventory view
                player.updateInventory();
            }
        });
    }
    
    /**
     * Executes custom async actions (buy/sell transactions).
     * 
     * @param player      The player
     * @param actionValue The action value (e.g., "convert_sell:64")
     */
    private void executeAsyncAction(Player player, String actionValue) {
        // Parse custom actions based on action value
        if (actionValue.startsWith("convert_sell:")) {
            // Extract amount from "convert_sell:64"
            String amountStr = actionValue.substring("convert_sell:".length());
            try {
                // Parse amount as integer
                int amount = Integer.parseInt(amountStr);
                // Execute sell transaction
                handleConvertSell(player, amount);
            } catch (NumberFormatException e) {
                // Invalid amount → log warning
                plugin.getLogger().warning("Invalid amount in convert_sell: " + amountStr);
            }
        } else if (actionValue.equals("convert_sell_all")) {
            // Sell all emeralds in inventory
            handleConvertSellAll(player);
        } else if (actionValue.startsWith("convert_buy:")) {
            // Extract amount from "convert_buy:64"
            String amountStr = actionValue.substring("convert_buy:".length());
            try {
                // Parse amount as integer
                int amount = Integer.parseInt(amountStr);
                // Execute buy transaction
                handleConvertBuy(player, amount);
            } catch (NumberFormatException e) {
                // Invalid amount → log warning
                plugin.getLogger().warning("Invalid amount in convert_buy: " + amountStr);
            }
        } else if (actionValue.equals("convert_buy_inventory") || actionValue.equals("convert_buy_all")) {
            // Fill inventory with emeralds (backward compatible with convert_buy_all)
            handleConvertBuyInventory(player);
        } else if (actionValue.equals("custom_amount_sell")) {
            // Start custom amount input for selling
            handleCustomAmountSell(player);
        } else if (actionValue.equals("custom_amount_buy")) {
            // Start custom amount input for buying
            handleCustomAmountBuy(player);
        }
    }
    
    /**
     * Handles selling a specific amount of emeralds.
     * 
     * @param player The player
     * @param amount The amount to sell
     */
    private void handleConvertSell(Player player, int amount) {
        // Execute sell transaction via TransactionManager
        TransactionResult result = plugin.getTransactionManager().sellEmerald(player, amount);
        
        // Send result message on main thread
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (result.isSuccess()) {
                // Success → send success message
                plugin.getMessageManager().send(player, result.getMessageKey(), result.getPlaceholders());
            } else {
                // Failure → send error message
                plugin.getMessageManager().send(player, result.getMessageKey(), result.getPlaceholders());
            }
        });
    }
    
    /**
     * Handles selling all emeralds in player's inventory.
     * 
     * @param player The player
     */
    private void handleConvertSellAll(Player player) {
        // Execute sell-all transaction via TransactionManager
        TransactionResult result = plugin.getTransactionManager().sellAllEmeralds(player);
        
        // Send result message on main thread
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (result.isSuccess()) {
                // Success → send success message
                String messageKey = "success.convert_all";
                plugin.getMessageManager().send(player, messageKey, result.getPlaceholders());
            } else {
                // Failure → send error message
                plugin.getMessageManager().send(player, result.getMessageKey(), result.getPlaceholders());
            }
        });
    }
    
    /**
     * Handles buying a specific amount of emeralds.
     * 
     * @param player The player
     * @param amount The amount to buy
     */
    private void handleConvertBuy(Player player, int amount) {
        // Execute buy transaction via TransactionManager
        TransactionResult result = plugin.getTransactionManager().buyEmerald(player, amount);
        
        // Send result message on main thread
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (result.isSuccess()) {
                // Success → send success message
                plugin.getMessageManager().send(player, result.getMessageKey(), result.getPlaceholders());
            } else {
                // Failure → send error message
                plugin.getMessageManager().send(player, result.getMessageKey(), result.getPlaceholders());
            }
        });
    }
    
    /**
     * Fills player's inventory with emeralds (up to available space).
     * 
     * @param player The player
     */
    private void handleConvertBuyInventory(Player player) {
        // Initialize free space counter
        int freeSpace = 0;
        
        // Calculate free space for emeralds
        for (ItemStack item : player.getInventory().getStorageContents()) {
            // Check if slot is empty or air
            if (item == null || item.getType() == Material.AIR) {
                // Empty slot = 64 emeralds
                freeSpace += 64;
            } else if (item.getType() == Material.EMERALD) {
                // Partial emerald stack = remaining space
                freeSpace += Math.max(0, 64 - item.getAmount());
            }
        }
        
        // Check if there's any space
        if (freeSpace <= 0) {
            // No space → send inventory full error on main thread
            Bukkit.getScheduler().runTask(plugin, () -> {
                plugin.getMessageManager().send(player, "error.inventory_full");
            });
            return;
        }
        
        // Try to buy as many as possible (up to free space)
        TransactionResult result = plugin.getTransactionManager().buyEmerald(player, freeSpace);
        
        // Send result message on main thread
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (result.isSuccess()) {
                // Success → send success message
                plugin.getMessageManager().send(player, result.getMessageKey(), result.getPlaceholders());
            } else {
                // Failure → send error message
                plugin.getMessageManager().send(player, result.getMessageKey(), result.getPlaceholders());
            }
        });
    }
    
    /**
     * Starts a custom amount input session for selling.
     * Closes the inventory and prompts player to type amount in chat.
     * 
     * @param player The player
     */
    private void handleCustomAmountSell(Player player) {
        // Execute on main thread
        Bukkit.getScheduler().runTask(plugin, () -> {
            // Close player's inventory
            player.closeInventory();
            // Get chat listener
            craftepxly.me.emeraldeconomy.listener.ChatListener chatListener = plugin.getChatListener();
            if (chatListener != null) {
                // Start custom amount session for SELL
                chatListener.startCustomAmountSession(player, 
                    craftepxly.me.emeraldeconomy.listener.ChatListener.CustomAmountType.SELL);
            }
        });
    }
    
    /**
     * Starts a custom amount input session for buying.
     * Closes the inventory and prompts player to type amount in chat.
     * 
     * @param player The player
     */
    private void handleCustomAmountBuy(Player player) {
        // Execute on main thread
        Bukkit.getScheduler().runTask(plugin, () -> {
            // Close player's inventory
            player.closeInventory();
            // Get chat listener
            craftepxly.me.emeraldeconomy.listener.ChatListener chatListener = plugin.getChatListener();
            if (chatListener != null) {
                // Start custom amount session for BUY
                chatListener.startCustomAmountSession(player, 
                    craftepxly.me.emeraldeconomy.listener.ChatListener.CustomAmountType.BUY);
            }
        });
    }
}