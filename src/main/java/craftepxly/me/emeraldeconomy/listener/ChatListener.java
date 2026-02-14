package craftepxly.me.emeraldeconomy.listener;

import craftepxly.me.emeraldeconomy.EmeraldEconomy;
import craftepxly.me.emeraldeconomy.transaction.TransactionResult;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import io.papermc.paper.event.player.AsyncChatEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ChatListener — handles custom amount input via chat for buy/sell transactions.
 */
public class ChatListener implements Listener {
    
    // Reference to main plugin instance
    private final EmeraldEconomy plugin;
    // Tracks active custom amount sessions (UUID → session data)
    private final Map<UUID, CustomAmountSession> activeSessions = new ConcurrentHashMap<>();
    
    /**
     * Constructs a new ChatListener and starts cleanup task.
     * 
     * @param plugin The main EmeraldEconomy plugin instance
     */
    public ChatListener(EmeraldEconomy plugin) {
        // Store plugin reference
        this.plugin = plugin;
        
        // Cleanup task for expired sessions (runs every 60 seconds)
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            // Get current time in milliseconds
            long now = System.currentTimeMillis();
            // Remove expired sessions (older than 60 seconds)
            activeSessions.entrySet().removeIf(entry -> 
                now - entry.getValue().startTime > 60000 // 1 minute timeout
            );
        }, 1200L, 1200L); // Initial delay: 60s, Period: 60s (1200 ticks = 60 seconds)
    }
    
    /**
     * Handles async chat events for custom amount input.
     * 
     * @param event The chat event
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        // Get the player who sent the chat message
        Player player = event.getPlayer();
        // Get player's UUID
        UUID playerId = player.getUniqueId();
        
        // Check if player has active custom amount session
        CustomAmountSession session = activeSessions.get(playerId);
        // If no session, this is normal chat → ignore
        if (session == null) {
            return;
        }
        
        // Player has an active session → cancel the chat event (don't broadcast)
        event.setCancelled(true);
        
        // Remove session from map (one-time use)
        activeSessions.remove(playerId);
        
        // Get message content using Adventure API (Paper's modern chat API)
        String message = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        
        // Check if player wants to cancel
        if (message.equalsIgnoreCase("cancel") || message.equalsIgnoreCase("batal")) {
            // Send cancellation message
            plugin.getMessageManager().send(player, "custom_amount.cancelled");
            return;
        }
        
        // Parse amount from message
        int amount;
        try {
            // Try to parse as integer
            amount = Integer.parseInt(message);
            
            // Amount must be positive
            if (amount <= 0) {
                // Send error message
                plugin.getMessageManager().send(player, "custom_amount.invalid_input");
                return;
            }
            
        } catch (NumberFormatException e) {
            // Not a valid number → send error message
            plugin.getMessageManager().send(player, "custom_amount.invalid_input");
            return;
        }
        
        // Execute transaction based on session type (BUY or SELL)
        TransactionResult result;
        if (session.type == CustomAmountType.SELL) {
            // Execute sell transaction
            result = plugin.getTransactionManager().sellEmerald(player, amount);
        } else {
            // Execute buy transaction
            result = plugin.getTransactionManager().buyEmerald(player, amount);
        }
        
        // Send result message on main thread (Bukkit API requirement)
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
     * Handles player quit events.
     * Cleans up active sessions when player disconnects.
     * 
     * @param event The quit event
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Remove player's session from map (cleanup)
        activeSessions.remove(event.getPlayer().getUniqueId());
    }
    
    /**
     * Starts a custom amount session for a player.
     * 
     * @param player The player
     * @param type   The transaction type (BUY or SELL)
     */
    public void startCustomAmountSession(Player player, CustomAmountType type) {
        // Create new session and store in map
        activeSessions.put(player.getUniqueId(), new CustomAmountSession(type));
    }
    
    /**
     * CustomAmountSession — holds session data for custom amount input.
     */
    public static class CustomAmountSession {
        // Transaction type (BUY or SELL)
        public final CustomAmountType type;
        // Session start time in milliseconds (for timeout)
        public final long startTime;
        
        /**
         * Constructs a new session.
         * 
         * @param type Transaction type
         */
        public CustomAmountSession(CustomAmountType type) {
            // Store transaction type
            this.type = type;
            // Record start time
            this.startTime = System.currentTimeMillis();
        }
    }
    
    /**
     * CustomAmountType — the transaction type for custom amount input.
     */
    public enum CustomAmountType {
        // Player wants to buy emeralds
        BUY,
        // Player wants to sell emeralds
        SELL
    }
}