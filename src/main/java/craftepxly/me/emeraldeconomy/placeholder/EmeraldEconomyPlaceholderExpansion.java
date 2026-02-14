package craftepxly.me.emeraldeconomy.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import craftepxly.me.emeraldeconomy.EmeraldEconomy;
import craftepxly.me.emeraldeconomy.util.ItemUtils;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * EmeraldEconomyPlaceholderExpansion — PlaceholderAPI integration.
 */
public class EmeraldEconomyPlaceholderExpansion extends PlaceholderExpansion {
    
    // Reference to main plugin instance
    private final EmeraldEconomy plugin;
    
    /**
     * Constructs a new placeholder expansion.
     * 
     * @param plugin The main EmeraldEconomy plugin instance
     */
    public EmeraldEconomyPlaceholderExpansion(EmeraldEconomy plugin) {
        // Store plugin reference
        this.plugin = plugin;
    }
    
    /**
     * Gets the identifier for this expansion (used in placeholder prefix).
     * 
     * @return "emeraldeconomy"
     */
    @Override
    public @NotNull String getIdentifier() {
        return "emeraldeconomy";
    }
    
    /**
     * Gets the author of this expansion.
     * 
     * @return "craftepxly"
     */
    @Override
    public @NotNull String getAuthor() {
        return "craftepxly";
    }
    
    /**
     * Gets the version of this expansion (matches plugin version).
     * 
     * @return Plugin version string
     */
    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }
    
    /**
     * Whether this expansion should persist through reloads.
     * 
     * @return true (always persist)
     */
    @Override
    public boolean persist() {
        return true;
    }
    
    /**
     * Handles placeholder requests.
     * 
     * @param player     The player (can be null for global placeholders)
     * @param identifier The placeholder name (without prefix)
     * @return The placeholder value, or null if placeholder doesn't exist
     */
    @Override
    public String onPlaceholderRequest(Player player, @NotNull String identifier) {
        // Route to appropriate handler based on identifier
        switch (identifier.toLowerCase()) {
            // ===== Price Placeholders =====
            case "price_buy":
                // Current buy price (what server pays to players)
                return String.format("%.2f", plugin.getPriceManager().getBuyPrice());
                
            case "price_sell":
                // Current sell price (what players pay to server)
                return String.format("%.2f", plugin.getPriceManager().getSellPrice());
                
            // ===== Stack Calculations (64 emeralds) =====
            case "stack_sell_value":
                // Value when player SELLS 64 emeralds (player receives money)
                return String.format("%.2f", plugin.getPriceManager().getBuyPrice() * 64);
                
            case "stack_buy_value":
                // Cost when player BUYS 64 emeralds (player pays money)
                return String.format("%.2f", plugin.getPriceManager().getSellPrice() * 64);
                
            // ===== Player-Specific Placeholders =====
            case "total_converted":
                // Player's total emeralds converted (all-time stat)
                if (player == null) return "0";
                return String.valueOf(
                    plugin.getPlayerStatsStorage()
                        .getTotalConverted(player.getUniqueId())
                        .getNow(0) // Get value immediately (non-blocking)
                );
                
            case "player_emeralds":
                // Number of emeralds in player's inventory
                if (player == null) return "0";
                return String.valueOf(ItemUtils.countEmeralds(player.getInventory()));
                
            case "all_sell_value":
                // Total value of all emeralds in player's inventory
                if (player == null) return "0.00";
                int emeraldCount = ItemUtils.countEmeralds(player.getInventory());
                return String.format("%.2f", plugin.getPriceManager().getBuyPrice() * emeraldCount);
                
            case "inventory_buy_space":
                // Number of emeralds that can fit in player's inventory
                if (player == null) return "0";
                int freeSpace = 0;
                // Calculate free space
                for (org.bukkit.inventory.ItemStack item : player.getInventory().getStorageContents()) {
                    if (item == null || item.getType() == org.bukkit.Material.AIR) {
                        // Empty slot = 64 emeralds
                        freeSpace += 64;
                    } else if (item.getType() == org.bukkit.Material.EMERALD) {
                        // Partial stack = remaining space
                        freeSpace += Math.max(0, 64 - item.getAmount());
                    }
                }
                return String.valueOf(freeSpace);
                
            case "inventory_buy_value":
                // Cost to fill inventory with emeralds
                if (player == null) return "0.00";
                int space = 0;
                // Calculate free space
                for (org.bukkit.inventory.ItemStack item : player.getInventory().getStorageContents()) {
                    if (item == null || item.getType() == org.bukkit.Material.AIR) {
                        space += 64;
                    } else if (item.getType() == org.bukkit.Material.EMERALD) {
                        space += Math.max(0, 64 - item.getAmount());
                    }
                }
                // Calculate cost (price * space)
                return String.format("%.2f", plugin.getPriceManager().getSellPrice() * space);
                
            // ===== Buy Action Placeholders (NEW v3.0) =====
            case "buy_1_cost":
                // Cost to buy 1 emerald (base + tax)
                double buy1Base = plugin.getPriceManager().getSellPrice();
                double buy1Tax = plugin.getPriceManager().getTransactionTax(buy1Base);
                return String.format("%.2f", buy1Base + buy1Tax);
                
            case "buy_1_tax":
                // Tax for buying 1 emerald
                double buy1Price = plugin.getPriceManager().getSellPrice();
                return String.format("%.2f", plugin.getPriceManager().getTransactionTax(buy1Price));
                
            case "buy_64_cost":
                // Cost to buy 64 emeralds (base + tax)
                double buy64Base = plugin.getPriceManager().getSellPrice() * 64;
                double buy64Tax = plugin.getPriceManager().getTransactionTax(buy64Base);
                return String.format("%.2f", buy64Base + buy64Tax);
                
            case "buy_64_tax":
                // Tax for buying 64 emeralds
                double buy64Price = plugin.getPriceManager().getSellPrice() * 64;
                return String.format("%.2f", plugin.getPriceManager().getTransactionTax(buy64Price));
                
            // ===== Sell Action Placeholders (NEW v3.0) =====
            case "sell_1_value":
                // Value when selling 1 emerald (after tax deduction)
                double sell1Base = plugin.getPriceManager().getBuyPrice();
                double sell1Tax = plugin.getPriceManager().getTransactionTax(sell1Base);
                return String.format("%.2f", sell1Base - sell1Tax);
                
            case "sell_1_tax":
                // Tax deducted when selling 1 emerald
                double sell1Price = plugin.getPriceManager().getBuyPrice();
                return String.format("%.2f", plugin.getPriceManager().getTransactionTax(sell1Price));
                
            case "sell_64_value":
                // Value when selling 64 emeralds (after tax)
                double sell64Base = plugin.getPriceManager().getBuyPrice() * 64;
                double sell64Tax = plugin.getPriceManager().getTransactionTax(sell64Base);
                return String.format("%.2f", sell64Base - sell64Tax);
                
            case "sell_64_tax":
                // Tax deducted when selling 64 emeralds
                double sell64Price = plugin.getPriceManager().getBuyPrice() * 64;
                return String.format("%.2f", plugin.getPriceManager().getTransactionTax(sell64Price));
                
            case "sell_all_value":
                // Value when selling all emeralds (after tax)
                if (player == null) return "0.00";
                int allEmeralds = ItemUtils.countEmeralds(player.getInventory());
                double sellAllBase = plugin.getPriceManager().getBuyPrice() * allEmeralds;
                double sellAllTax = plugin.getPriceManager().getTransactionTax(sellAllBase);
                return String.format("%.2f", sellAllBase - sellAllTax);
                
            case "sell_all_tax":
                // Tax deducted when selling all emeralds
                if (player == null) return "0.00";
                int allEms = ItemUtils.countEmeralds(player.getInventory());
                double sellAllPrice = plugin.getPriceManager().getBuyPrice() * allEms;
                return String.format("%.2f", plugin.getPriceManager().getTransactionTax(sellAllPrice));
                
            case "sell_all_count":
                // Count of all emeralds in inventory
                if (player == null) return "0";
                return String.valueOf(ItemUtils.countEmeralds(player.getInventory()));
                
            // ===== Affordability Placeholders (NEW v3.0) =====
            case "can_afford_1_emerald":
                // Can player afford to buy 1 emerald?
                if (player == null) return plugin.getMessageManager().getRaw("affordability.cannot_afford");
                double cost1 = plugin.getPriceManager().getSellPrice();
                double tax1 = plugin.getPriceManager().getTransactionTax(cost1);
                boolean canAfford1 = plugin.getEconomyService().getBalance(player) >= (cost1 + tax1);
                return plugin.getMessageManager().getRaw(canAfford1 ? "affordability.can_afford" : "affordability.cannot_afford");
                
            case "can_afford_64_emeralds":
                // Can player afford to buy 64 emeralds?
                if (player == null) return plugin.getMessageManager().getRaw("affordability.cannot_afford");
                double cost64 = plugin.getPriceManager().getSellPrice() * 64;
                double tax64 = plugin.getPriceManager().getTransactionTax(cost64);
                boolean canAfford64 = plugin.getEconomyService().getBalance(player) >= (cost64 + tax64);
                return plugin.getMessageManager().getRaw(canAfford64 ? "affordability.can_afford" : "affordability.cannot_afford");
                
            case "has_1_emerald":
                // Does player have at least 1 emerald?
                if (player == null) return plugin.getMessageManager().getRaw("affordability.no_emeralds");
                boolean has1 = ItemUtils.countEmeralds(player.getInventory()) >= 1;
                return plugin.getMessageManager().getRaw(has1 ? "affordability.has_emeralds" : "affordability.no_emeralds");
                
            case "has_64_emeralds":
                // Does player have at least 64 emeralds?
                if (player == null) return plugin.getMessageManager().getRaw("affordability.no_emeralds");
                boolean has64 = ItemUtils.countEmeralds(player.getInventory()) >= 64;
                return plugin.getMessageManager().getRaw(has64 ? "affordability.has_emeralds" : "affordability.no_emeralds");
                
            case "has_any_emeralds":
                // Does player have any emeralds?
                if (player == null) return plugin.getMessageManager().getRaw("affordability.no_emeralds");
                boolean hasAny = ItemUtils.countEmeralds(player.getInventory()) > 0;
                return plugin.getMessageManager().getRaw(hasAny ? "affordability.has_emeralds" : "affordability.no_emeralds");
                
            case "inventory_buy_value_with_tax":
                // Cost to fill inventory INCLUDING tax
                if (player == null) return "0.00";
                int totalSpace = 0;
                // Calculate free space
                for (org.bukkit.inventory.ItemStack item : player.getInventory().getStorageContents()) {
                    if (item == null || item.getType() == org.bukkit.Material.AIR) {
                        totalSpace += 64;
                    } else if (item.getType() == org.bukkit.Material.EMERALD) {
                        totalSpace += Math.max(0, 64 - item.getAmount());
                    }
                }
                // Calculate cost with tax
                double baseCost = plugin.getPriceManager().getSellPrice() * totalSpace;
                double tax = plugin.getPriceManager().getTransactionTax(baseCost);
                return String.format("%.2f", baseCost + tax);
                
            case "inventory_buy_tax":
                // Tax amount for filling inventory
                if (player == null) return "0.00";
                int invSpace = 0;
                // Calculate free space
                for (org.bukkit.inventory.ItemStack item : player.getInventory().getStorageContents()) {
                    if (item == null || item.getType() == org.bukkit.Material.AIR) {
                        invSpace += 64;
                    } else if (item.getType() == org.bukkit.Material.EMERALD) {
                        invSpace += Math.max(0, 64 - item.getAmount());
                    }
                }
                // Calculate tax
                double cost = plugin.getPriceManager().getSellPrice() * invSpace;
                return String.format("%.2f", plugin.getPriceManager().getTransactionTax(cost));
                
            case "can_afford_inventory_fill":
                // Can player afford to fill their inventory?
                if (player == null) return plugin.getMessageManager().getRaw("affordability.cannot_afford");
                int availableSpace = 0;
                // Calculate free space
                for (org.bukkit.inventory.ItemStack item : player.getInventory().getStorageContents()) {
                    if (item == null || item.getType() == org.bukkit.Material.AIR) {
                        availableSpace += 64;
                    } else if (item.getType() == org.bukkit.Material.EMERALD) {
                        availableSpace += Math.max(0, 64 - item.getAmount());
                    }
                }
                // Calculate total cost with tax
                double fillCost = plugin.getPriceManager().getSellPrice() * availableSpace;
                double fillTax = plugin.getPriceManager().getTransactionTax(fillCost);
                double totalCost = fillCost + fillTax;
                double balance = plugin.getEconomyService().getBalance(player);
                boolean canAffordFill = balance >= totalCost;
                return plugin.getMessageManager().getRaw(canAffordFill ? "affordability.can_afford" : "affordability.cannot_afford");
                
            // ===== Dynamic Pricing Info (NEW) =====
            case "depletion_factor":
                // Current resource depletion factor (0.0-1.0)
                return String.format("%.2f%%", plugin.getPriceManager().getDepletionFactor() * 100);
                
            case "total_emeralds_converted":
                // Total emeralds converted globally (all players)
                return String.valueOf(plugin.getPriceManager().getTotalEmeraldsConverted());
                
            case "transaction_tax_rate":
                // Current transaction tax rate (percentage)
                return String.format("%.1f%%", plugin.getPriceManager().getTransactionTaxRate() * 100);
                
            case "base_buy_price":
                // Base buy price (before dynamic adjustments)
                return String.format("%.2f", plugin.getPriceManager().getBaseBuyPrice());
                
            case "base_sell_price":
                // Base sell price (before dynamic adjustments)
                return String.format("%.2f", plugin.getPriceManager().getBaseSellPrice());
                
            case "recent_volume":
                // Recent transaction volume (within time window)
                return String.valueOf(plugin.getPriceManager().getRecentVolume());
                
            case "currency_symbol":
                // Currency symbol from config (e.g., "$")
                return plugin.getConfigManager().getCurrencySymbol();
                
            case "currency_name":
                // Currency name from config (e.g., "Dollar")
                return plugin.getConfigManager().getCurrencyName();
                
            default:
                // Unknown placeholder → return null
                return null;
        }
    }
}