package craftepxly.me.emeraldeconomy.command;

import craftepxly.me.emeraldeconomy.EmeraldEconomy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * AdminCommand — the command executor for /ecadmin.
 */
public class AdminCommand implements CommandExecutor, TabCompleter {
    
    // This field holds a reference to the main plugin instance so we can access managers
    private final EmeraldEconomy plugin;
    
    /**
     * Constructs a new AdminCommand with a reference to the plugin.
     * 
     * @param plugin The main EmeraldEconomy plugin instance
     */
    public AdminCommand(EmeraldEconomy plugin) {
        // Store plugin reference in memory so we can call getConfigManager(), getMessageManager(), etc.
        this.plugin = plugin;
    }
    
    /**
     * Executes the /ecadmin command and its subcommands.
     * 
     * @param sender  The entity that sent the command (Player or Console)
     * @param command The command object (not used here, but required by Bukkit API)
     * @param label   The command alias used (e.g., "ecadmin")
     * @param args    The arguments passed after the command
     * @return true to indicate command was handled (prevents "Unknown command" message)
     */
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                           @NotNull String label, @NotNull String[] args) {
        // Check if the sender has the admin permission node
        if (!sender.hasPermission("emeraldeconomy.admin")) {
            // They don't have permission → send error message from messages.yml
            plugin.getMessageManager().send(sender, "error.no_permission");
            // Return true = command was handled (don't show "Unknown command")
            return true;
        }
        
        // If no arguments were provided (just "/ecadmin"), show help
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        
        // Convert the first argument to lowercase so "RELOAD" and "reload" are treated the same
        String subCommand = args[0].toLowerCase();
        
        // Use a switch statement to route to the correct handler method
        switch (subCommand) {
            case "reload":
                // User typed "/ecadmin reload" → reload all config files
                handleReload(sender);
                break;
                
            case "stats":
                // User typed "/ecadmin stats [player]" → show player statistics
                handleStats(sender, args);
                break;
                
            case "setprice":
                // User typed "/ecadmin setprice buy|sell price" → change price in config
                handleSetPrice(sender, args);
                break;
                
            case "info":
                // User typed "/ecadmin info" → display current price & economy stats
                handleInfo(sender);
                break;
                
            default:
                // Unknown subcommand → show help
                sendHelp(sender);
                break;
        }
        
        // Return true = we handled it (no "Unknown command" error)
        return true;
    }
    
    /**
     * Sends the admin help message to the sender.
     * 
     * @param sender The command sender
     */
    private void sendHelp(CommandSender sender) {
        // Load the "command.admin_help" key from messages.yml and send it
        plugin.getMessageManager().send(sender, "command.admin_help");
    }
    
    /**
     * Handles the "/ecadmin reload" subcommand.
     * Reloads all configuration files, message files, menus, and prices.
     * 
     * @param sender The command sender
     */
    private void handleReload(CommandSender sender) {
        try {
            // Call the config manager to reload config.yml and menu.yml
            plugin.getConfigManager().loadConfigs();
            // Reload message files (messages_eng.yml, messages_id.yml)
            plugin.getMessageManager().reload();
            // Reload all GUI menus from menu.yml
            plugin.getMenuManager().loadMenus();
            // Reload pricing config (base prices, dynamic pricing settings)
            plugin.getPriceManager().loadConfiguration();
            
            // Send success message to the sender
            plugin.getMessageManager().send(sender, "success.reload");
        } catch (Exception e) {
            // If something goes wrong, send error message to sender
            sender.sendMessage("§cError reloading: " + e.getMessage());
            // Print stack trace to server console for debugging
            e.printStackTrace();
        }
    }
    
    /**
     * Handles the "/ecadmin stats [player]" subcommand.
     * Shows total emeralds converted by a player.
     * 
     * @param sender The command sender
     * @param args   The full argument array (args[0] = "stats", args[1] = player name)
     */
    private void handleStats(CommandSender sender, String[] args) {
        // Check if a player name was provided
        if (args.length < 2) {
            // No player name → check if sender is a player (so we can show their own stats)
            if (!(sender instanceof Player)) {
                // Sender is console → they MUST specify a player name
                sender.sendMessage("§cUsage: /ecadmin stats <player>");
                return;
            }
            
            // Sender is a player → show their own stats
            Player player = (Player) sender;
            showStats(sender, player);
        } else {
            // A player name was provided → get the OfflinePlayer object
            String playerName = args[1];
            OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
            
            // Check if this player has ever joined the server
            if (!target.hasPlayedBefore()) {
                sender.sendMessage("§cPlayer not found: " + playerName);
                return;
            }
            
            // Player exists → show their stats
            showStats(sender, target);
        }
    }
    
    /**
     * Displays player statistics (total emeralds converted).
     * Fetches data asynchronously from storage and sends message when ready.
     * 
     * @param sender The command sender (who will receive the message)
     * @param player The player whose stats we're looking up
     */
    private void showStats(CommandSender sender, OfflinePlayer player) {
        // Get player name (or "Unknown" if null — should never happen)
        String playerName = player.getName() != null ? player.getName() : "Unknown";

        // Fetch total converted emeralds asynchronously from storage (CompletableFuture)
        plugin.getPlayerStatsStorage().getTotalConverted(player.getUniqueId())
            // When the data is ready, execute this code
            .thenAccept(totalConverted ->
                // Build and send the message with placeholders
                plugin.getMessageManager().builder("info.stats")
                    .placeholder("emerald_player_name", playerName)
                    .placeholder("total", totalConverted)
                    .send(sender)
            )
            // If something goes wrong (database error, etc.), execute this
            .exceptionally(ex -> {
                sender.sendMessage("§cError fetching stats: " + ex.getMessage());
                return null; // Required by exceptionally() — we don't return a value
            });
    }
    
    /**
     * Handles the "/ecadmin setprice buy|sell price" subcommand.
     * Updates the base buy or sell price in config.yml and reloads the price manager.
     *
     * buy  = price player PAYS when buying emeralds from the server
     * sell = price player RECEIVES when selling emeralds to the server
     *
     * @param sender The command sender
     * @param args   The full argument array (args[0] = "setprice", args[1] = "buy"/"sell", args[2] = price)
     */
    private void handleSetPrice(CommandSender sender, String[] args) {
        // Check if both price type and value were provided
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /ecadmin setprice <buy|sell> <price>");
            return;
        }
        
        // Get the price type (player perspective: buy = player buys, sell = player sells)
        String priceType = args[1].toLowerCase();
        double price;
        
        // Try to parse the price as a double
        try {
            price = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            // Not a valid number → send error message
            plugin.getMessageManager().send(sender, "error.invalid_price");
            return;
        }
        
        // Price must be positive
        if (price <= 0) {
            plugin.getMessageManager().send(sender, "error.invalid_price");
            return;
        }
        
        // Update the config based on price type (player perspective)
        if (priceType.equals("buy")) {
            // Set prices.buy = what player PAYS when buying emeralds
            plugin.getConfig().set("prices.buy", price);
        } else if (priceType.equals("sell")) {
            // Set prices.sell = what player RECEIVES when selling emeralds
            plugin.getConfig().set("prices.sell", price);
        } else {
            // Invalid price type
            sender.sendMessage("§cInvalid price type. Use 'buy' or 'sell'");
            return;
        }
        
        // Save the updated config to disk
        plugin.saveConfig();
        // Reload price manager so the new values take effect immediately
        plugin.getPriceManager().loadConfiguration();
        
        // Send success message with updated prices
        plugin.getMessageManager().builder("success.price_updated")
                .placeholder("buy", plugin.getPriceManager().getBuyPrice())
                .placeholder("sell", plugin.getPriceManager().getSellPrice())
                .send(sender);
    }
    
    /**
     * Handles the "/ecadmin info" subcommand.
     * Displays current buy/sell prices, dynamic pricing stats, and configured tax groups.
     *
     * @param sender The command sender
     */
    private void handleInfo(CommandSender sender) {
        // Get current buy price from price manager (what player PAYS when buying)
        double buyPrice = plugin.getPriceManager().getBuyPrice();
        // Get current sell price from price manager (what player RECEIVES when selling)
        double sellPrice = plugin.getPriceManager().getSellPrice();
        // Get depletion factor (resource scarcity multiplier)
        double depletionFactor = plugin.getPriceManager().getDepletionFactor();
        // Get total emeralds converted globally (all players)
        long totalConverted = plugin.getPriceManager().getTotalEmeraldsConverted();
        // Get global (fallback) transaction tax rate
        double taxRate = plugin.getPriceManager().getTransactionTaxRate();

        // Build and send the info message with all placeholders
        plugin.getMessageManager().builder("info.current_prices")
                .placeholder("buy", buyPrice)
                .placeholder("sell", sellPrice)
                .placeholder("currency", plugin.getConfigManager().getCurrencySymbol())
                .placeholder("depletion_factor", String.format("%.2f%%", depletionFactor * 100))
                .placeholder("total_converted", totalConverted)
                .placeholder("tax_rate", String.format("%.1f%%", taxRate * 100))
                .send(sender);

        // Display configured tax groups (if any)
        java.util.Map<String, Double> taxGroups = plugin.getConfigManager().getTaxGroups();
        if (!taxGroups.isEmpty()) {
            sender.sendMessage("§6§l[Tax Groups]");
            sender.sendMessage("§7Global fallback: §e" + String.format("%.1f%%", taxRate * 100));
            taxGroups.forEach((groupName, rate) -> {
                String perm = "emeraldeconomy.group." + groupName.toLowerCase();
                sender.sendMessage(String.format(
                        "§7• §b%s §7(permission: §f%s§7) §7→ §a%.1f%%",
                        groupName, perm, rate * 100));
            });
        }
    }
    
    /**
     * Provides tab completion suggestions for /ecadmin commands.
     * 
     * @param sender  The command sender
     * @param command The command object
     * @param alias   The command alias used
     * @param args    The current arguments
     * @return A list of suggestions for tab completion
     */
    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        // If they're typing the first argument (subcommand)
        if (args.length == 1) {
            // Return a list of all subcommands that match what they've typed so far
            return Arrays.asList("reload", "stats", "setprice", "info")
                    .stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("setprice")) {
            // If they're typing "/ecadmin setprice [here]", suggest "buy" and "sell"
            return Arrays.asList("buy", "sell");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("stats")) {
            // If they're typing "/ecadmin stats [here]", suggest online player names
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        
        // No suggestions for other cases
        return new ArrayList<>();
    }
}