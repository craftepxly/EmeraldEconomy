package craftepxly.me.emeraldeconomy.command;

import craftepxly.me.emeraldeconomy.EmeraldEconomy;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * EmeraldConverterCommand — the command executor for /emeraldconverter (alias: /ec).
 */
public class EmeraldConverterCommand implements CommandExecutor, TabCompleter {
    
    // Reference to main plugin instance so we can access managers
    private final EmeraldEconomy plugin;
    
    /**
     * Constructs a new EmeraldConverterCommand with a reference to the plugin.
     * 
     * @param plugin The main EmeraldEconomy plugin instance
     */
    public EmeraldConverterCommand(EmeraldEconomy plugin) {
        // Store plugin reference in memory for later use
        this.plugin = plugin;
    }
    
    /**
     * Executes the /emeraldconverter command.
     * Opens the emerald converter GUI for the player.
     * 
     * @param sender  The entity that sent the command
     * @param command The command object
     * @param label   The command alias used (e.g., "ec")
     * @param args    The arguments (not used here, but required by API)
     * @return true to indicate command was handled
     */
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, 
                           @NotNull String label, @NotNull String[] args) {
        // Check if the sender is a player (not console or command block)
        if (!(sender instanceof Player)) {
            // Sender is not a player → send error message
            plugin.getMessageManager().send(sender, "error.player_only");
            // Return true = command handled (don't show "Unknown command")
            return true;
        }
        
        // Cast sender to Player so we can call Player-specific methods
        Player player = (Player) sender;
        
        // Check if the player has permission to use the converter
        if (!player.hasPermission("emeraldeconomy.use")) {
            // No permission → send error message
            plugin.getMessageManager().send(player, "error.no_permission");
            return true;
        }
        
        // Player has permission → open the main menu GUI
        plugin.getMenuManager().openMenu(player, "emerald-converter");
        
        // Return true = command handled successfully
        return true;
    }
    
    /**
     * Provides tab completion suggestions for /emeraldconverter.
     * Since this command has no arguments, we return an empty list.
     * 
     * @param sender  The command sender
     * @param command The command object
     * @param alias   The command alias used
     * @param args    The current arguments
     * @return An empty list (no suggestions)
     */
    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        // This command has no arguments, so no tab completions
        return new ArrayList<>();
    }
}