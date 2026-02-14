package craftepxly.me.emeraldeconomy.listener;

import craftepxly.me.emeraldeconomy.EmeraldEconomy;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

/**
 * InventoryClickListener — handles inventory click and close events for GUI menus.
 */
public class InventoryClickListener implements Listener {
    
    // Reference to main plugin instance
    private final EmeraldEconomy plugin;
    
    /**
     * Constructs a new InventoryClickListener.
     * 
     * @param plugin The main EmeraldEconomy plugin instance
     */
    public InventoryClickListener(EmeraldEconomy plugin) {
        // Store plugin reference in memory
        this.plugin = plugin;
    }
    
    /**
     * Handles inventory click events.
     * Cancels item movement and routes to MenuManager.
     * 
     * @param event The inventory click event
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onInventoryClick(InventoryClickEvent event) {
        // Check if the clicker is a player (not a hopper or other entity)
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        
        // Cast to Player
        Player player = (Player) event.getWhoClicked();
        
        // Check if player has a menu open (managed by MenuManager)
        if (!plugin.getMenuManager().hasMenuOpen(player)) {
            // Player doesn't have a plugin menu open → this is their normal inventory → allow
            return;
        }
        
        // Cancel the event to prevent item movement (players can't steal menu items)
        event.setCancelled(true);
        
        // Get the clicked slot number (raw slot = slot in the top inventory)
        int slot = event.getRawSlot();
        
        // Check if clicked in top inventory (our menu)
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) {
            // Clicked in bottom inventory (player's own inventory) or outside → ignore
            return;
        }
        
        // Handle the click (execute actions for this slot)
        plugin.getMenuManager().handleClick(player, slot);
    }
    
    /**
     * Handles inventory close events.
     * Cleans up menu tracking when player closes a menu.
     * 
     * @param event The inventory close event
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        // Check if the closer is a player
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }
        
        // Cast to Player
        Player player = (Player) event.getPlayer();
        // Remove player from menu tracking (cleanup)
        plugin.getMenuManager().closeMenu(player);
    }
}