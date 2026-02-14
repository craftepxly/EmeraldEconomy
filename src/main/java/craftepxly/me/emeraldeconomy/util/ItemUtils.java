package craftepxly.me.emeraldeconomy.util;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * ItemUtils — utility class for item-related operations.
 */
public class ItemUtils {
    
    /**
     * Count emeralds in the inventory
     * 
     * @param inventory The inventory to count
     * @return Total emerald count
     */
    public static int countEmeralds(Inventory inventory) {
        // Initialize counter
        int count = 0;
        
        // Loop through all inventory contents
        for (ItemStack item : inventory.getContents()) {
            // Check if item is a valid emerald
            if (isValidEmerald(item)) {
                // Add item amount to counter
                count += item.getAmount();
            }
        }
        
        // Return total count
        return count;
    }
    
    /**
     * Check if an item is a valid emerald
     * Rejects renamed items (anti-exploit)
     * 
     * @param item The item to check
     * @return true if valid emerald, false otherwise
     */
    public static boolean isValidEmerald(ItemStack item) {
        // Check if item is null or not emerald
        if (item == null || item.getType() != Material.EMERALD) {
            return false;
        }
        
        // Check for renamed items (anti-exploit)
        // We only accept items without custom display names
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            // Item has custom name → reject (could be fake emerald)
            return false;
        }
        
        // Item is valid emerald
        return true;
    }
    
    /**
     * Remove emeralds from inventory
     * 
     * @param inventory The inventory
     * @param amount    Amount to remove
     * @return true if successful, false if not enough emeralds
     */
    public static boolean removeEmeralds(Inventory inventory, int amount) {
        // Track remaining amount to remove
        int remaining = amount;
        
        // Get inventory contents array
        ItemStack[] contents = inventory.getContents();
        // Loop through all slots
        for (int i = 0; i < contents.length; i++) {
            // Get item in this slot
            ItemStack item = contents[i];
            
            // Skip if not a valid emerald
            if (!isValidEmerald(item)) {
                continue;
            }
            
            // Get amount in this stack
            int stackAmount = item.getAmount();
            // Check if we need to remove entire stack or partial
            if (stackAmount <= remaining) {
                // Remove entire stack (set slot to null)
                remaining -= stackAmount;
                inventory.setItem(i, null);
            } else {
                // Remove partial stack (decrease amount)
                item.setAmount(stackAmount - remaining);
                remaining = 0;
            }
            
            // Check if we've removed enough
            if (remaining == 0) {
                // Success → return true
                return true;
            }
        }
        
        // Not enough emeralds were found → return false
        return false;
    }
    
    /**
     * Add emeralds to inventory
     * 
     * @param inventory The inventory
     * @param amount    Amount to add
     * @return true if successful, false if inventory full
     */
    public static boolean addEmeralds(Inventory inventory, int amount) {
        // Track remaining amount to add
        int remaining = amount;
        // Create emerald itemstack template
        ItemStack emerald = new ItemStack(Material.EMERALD);
        
        // Try to add to existing stacks first
        for (ItemStack item : inventory.getContents()) {
            // Check if no more remaining
            if (remaining == 0) break;
            
            // Check if slot has emerald stack with space
            if (item != null && item.getType() == Material.EMERALD && !item.hasItemMeta()) {
                // Calculate space in this stack
                int stackSpace = emerald.getMaxStackSize() - item.getAmount();
                // Check if stack has space
                if (stackSpace > 0) {
                    // Calculate how much to add
                    int toAdd = Math.min(stackSpace, remaining);
                    // Increase stack amount
                    item.setAmount(item.getAmount() + toAdd);
                    // Decrease remaining
                    remaining -= toAdd;
                }
            }
        }
        
        // Add remaining to empty slots
        while (remaining > 0) {
            // Calculate stack size (up to 64)
            int stackSize = Math.min(remaining, emerald.getMaxStackSize());
            // Set emerald amount
            emerald.setAmount(stackSize);
            
            // Try to add to inventory
            if (inventory.addItem(emerald.clone()).isEmpty()) {
                // Success → decrease remaining
                remaining -= stackSize;
            } else {
                // Inventory full, rollback might be needed
                return false;
            }
        }
        
        // All emeralds added successfully
        return true;
    }
    
    /**
     * Check if inventory has enough space for emeralds
     * 
     * @param inventory The inventory
     * @param amount    Amount to check
     * @return true if enough space, false otherwise
     */
    public static boolean hasSpace(Inventory inventory, int amount) {
        // Track remaining space needed
        int remaining = amount;
        
        // Check existing emerald stacks for space
        for (ItemStack item : inventory.getContents()) {
            // Check if slot has emerald stack
            if (item != null && item.getType() == Material.EMERALD && !item.hasItemMeta()) {
                // Calculate space in this stack
                int stackSpace = item.getMaxStackSize() - item.getAmount();
                // Subtract from remaining
                remaining -= stackSpace;
                // Check if enough space found
                if (remaining <= 0) {
                    return true;
                }
            }
        }
        
        // Check empty slots
        int emptySlots = 0;
        // Count empty slots
        for (ItemStack item : inventory.getContents()) {
            if (item == null || item.getType() == Material.AIR) {
                emptySlots++;
            }
        }
        
        // Calculate stacks needed for remaining
        int stacksNeeded = (int) Math.ceil((double) remaining / 64);
        // Return true if enough empty slots
        return emptySlots >= stacksNeeded;
    }
    
    /**
     * Create an emerald itemstack
     * 
     * @param amount Amount of emeralds
     * @return ItemStack of emeralds
     */
    public static ItemStack createEmerald(int amount) {
        // Create and return emerald itemstack with specified amount
        return new ItemStack(Material.EMERALD, amount);
    }
}