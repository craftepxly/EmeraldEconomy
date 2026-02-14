package craftepxly.me.emeraldeconomy.util;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * InventoryUtils — utility class for inventory-related calculations.
 */
public class InventoryUtils {
    
    // Emerald stack size constant (max items per stack)
    private static final int EMERALD_STACK_SIZE = 64;
    
    /**
     * Calculate free space for emeralds in player's inventory
     * 
     * @param player The player
     * @return Total emeralds that can fit in inventory
     */
    public static int calculateEmeraldSpace(Player player) {
        // Initialize free space counter
        int freeSpace = 0;
        
        // Loop through storage contents (excludes armor/offhand)
        for (ItemStack item : player.getInventory().getStorageContents()) {
            // Check if slot is empty or air
            if (item == null || item.getType() == Material.AIR) {
                // Empty slot can hold full stack (64 emeralds)
                freeSpace += EMERALD_STACK_SIZE;
            } else if (item.getType() == Material.EMERALD) {
                // Partial stack - calculate remaining space
                // (64 - current amount)
                freeSpace += Math.max(0, EMERALD_STACK_SIZE - item.getAmount());
            }
        }
        
        // Return total free space
        return freeSpace;
    }
    
    /**
     * Check if player has space for specified amount of emeralds
     * 
     * @param player The player
     * @param amount Amount to check
     * @return true if player has enough space
     */
    public static boolean hasSpaceForEmeralds(Player player, int amount) {
        // Compare calculated space to required amount
        return calculateEmeraldSpace(player) >= amount;
    }
    
    /**
     * Count total emeralds in player's inventory (delegates to ItemUtils)
     * 
     * @param player The player
     * @return Total emerald count
     */
    public static int countEmeralds(Player player) {
        // Delegate to ItemUtils.countEmeralds()
        return ItemUtils.countEmeralds(player.getInventory());
    }
}