package craftepxly.me.emeraldeconomy.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import craftepxly.me.emeraldeconomy.EmeraldEconomy;
import craftepxly.me.emeraldeconomy.util.ItemUtils;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * EmeraldEconomyPlaceholderExpansion — PlaceholderAPI integration.
 *
 * All price placeholders use PLAYER PERSPECTIVE:
 *   getBuyPrice()  = price player PAYS when buying   (player spends money)
 *   getSellPrice() = price player RECEIVES when selling (player gets money)
 *
 * Tax placeholders are PLAYER-AWARE:
 *   - When player is available, tax is calculated using the player's effective
 *     group-based tax rate (from ConfigManager.getPlayerTaxRate(player)).
 *   - When player is null (global context), falls back to the global tax rate.
 *   - This means the GUI always shows the correct personalised tax/cost/value
 *     that matches exactly what TransactionManager will charge or pay.
 */
public class EmeraldEconomyPlaceholderExpansion extends PlaceholderExpansion {

    private final EmeraldEconomy plugin;

    public EmeraldEconomyPlaceholderExpansion(EmeraldEconomy plugin) {
        this.plugin = plugin;
    }

    @Override public @NotNull String getIdentifier() { return "emeraldeconomy"; }
    @Override public @NotNull String getAuthor()     { return "craftepxly"; }
    @Override public @NotNull String getVersion()    { return plugin.getDescription().getVersion(); }
    @Override public boolean persist()               { return true; }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String identifier) {
        switch (identifier.toLowerCase()) {

            // ===== Price Placeholders =====

            case "price_buy":
                // Price player PAYS when buying emeralds from the server
                return String.format("%.2f", plugin.getPriceManager().getBuyPrice());

            case "price_sell":
                // Price player RECEIVES when selling emeralds to the server
                return String.format("%.2f", plugin.getPriceManager().getSellPrice());

            // ===== Stack Calculations (64 emeralds, gross — before tax) =====

            case "stack_sell_value":
                // Gross money player receives when selling 64 emeralds (before tax)
                return String.format("%.2f", plugin.getPriceManager().getSellPrice() * 64);

            case "stack_buy_value":
                // Base cost for player to buy 64 emeralds (before tax)
                return String.format("%.2f", plugin.getPriceManager().getBuyPrice() * 64);

            // ===== Player-Specific Placeholders =====

            case "total_converted":
                if (player == null) return "0";
                return String.valueOf(
                    plugin.getPlayerStatsStorage()
                          .getTotalConverted(player.getUniqueId())
                          .getNow(0)
                );

            case "player_emeralds":
                if (player == null) return "0";
                return String.valueOf(ItemUtils.countEmeralds(player.getInventory()));

            case "all_sell_value":
                // Gross money player receives when selling ALL emeralds (before tax)
                if (player == null) return "0.00";
                return String.format("%.2f",
                        plugin.getPriceManager().getSellPrice()
                        * ItemUtils.countEmeralds(player.getInventory()));

            case "inventory_buy_space":
                // Number of emeralds that can fit in player's inventory
                if (player == null) return "0";
                return String.valueOf(calcFreeSpace(player));

            case "inventory_buy_value":
                // Base cost for player to fill inventory with emeralds (before tax)
                if (player == null) return "0.00";
                return String.format("%.2f",
                        plugin.getPriceManager().getBuyPrice() * calcFreeSpace(player));

            // ===== Tax Group Placeholders =====

            case "player_tax_rate": {
                // Player's effective tax rate (group-based, or global fallback)
                // Returns a percentage string e.g. "3.0%" for group "rt"
                if (player == null)
                    return String.format("%.1f%%",
                            plugin.getConfigManager().getGlobalTaxRate() * 100);
                return String.format("%.1f%%",
                        plugin.getConfigManager().getPlayerTaxRate(player) * 100);
            }

            case "player_tax_group": {
                // Name of the tax group the player currently falls under,
                // or "default" if no group permission matches.
                if (player == null) return "default";
                java.util.Map<String, Double> groups = plugin.getConfigManager().getTaxGroups();
                double playerRate = plugin.getConfigManager().getPlayerTaxRate(player);
                double globalRate = plugin.getConfigManager().getGlobalTaxRate();
                // If the resolved rate matches the global rate and no group has that rate → "default"
                for (java.util.Map.Entry<String, Double> entry : groups.entrySet()) {
                    String perm = "emeraldeconomy.group." + entry.getKey().toLowerCase();
                    if (player.hasPermission(perm) && entry.getValue() == playerRate) {
                        return entry.getKey();
                    }
                }
                return "default";
            }

            // ===== Buy Action Placeholders (player buys emeralds, player-aware tax) =====

            case "buy_1_cost": {
                // Total cost for player to buy 1 emerald (base + player's tax)
                double base    = plugin.getPriceManager().getBuyPrice();
                double taxRate = player != null
                        ? plugin.getConfigManager().getPlayerTaxRate(player)
                        : plugin.getPriceManager().getTransactionTaxRate();
                return String.format("%.2f", base + plugin.getPriceManager().getTransactionTax(base, taxRate));
            }

            case "buy_1_tax": {
                // Tax amount when player buys 1 emerald (using player's rate)
                double base    = plugin.getPriceManager().getBuyPrice();
                double taxRate = player != null
                        ? plugin.getConfigManager().getPlayerTaxRate(player)
                        : plugin.getPriceManager().getTransactionTaxRate();
                return String.format("%.2f", plugin.getPriceManager().getTransactionTax(base, taxRate));
            }

            case "buy_64_cost": {
                // Total cost for player to buy 64 emeralds (base + player's tax)
                double base    = plugin.getPriceManager().getBuyPrice() * 64;
                double taxRate = player != null
                        ? plugin.getConfigManager().getPlayerTaxRate(player)
                        : plugin.getPriceManager().getTransactionTaxRate();
                return String.format("%.2f", base + plugin.getPriceManager().getTransactionTax(base, taxRate));
            }

            case "buy_64_tax": {
                // Tax amount when player buys 64 emeralds (using player's rate)
                double base    = plugin.getPriceManager().getBuyPrice() * 64;
                double taxRate = player != null
                        ? plugin.getConfigManager().getPlayerTaxRate(player)
                        : plugin.getPriceManager().getTransactionTaxRate();
                return String.format("%.2f", plugin.getPriceManager().getTransactionTax(base, taxRate));
            }

            // ===== Sell Action Placeholders (player sells emeralds, player-aware tax) =====

            case "sell_1_value": {
                // Net money player receives when selling 1 emerald (after player's tax)
                double base    = plugin.getPriceManager().getSellPrice();
                double taxRate = player != null
                        ? plugin.getConfigManager().getPlayerTaxRate(player)
                        : plugin.getPriceManager().getTransactionTaxRate();
                return String.format("%.2f", base - plugin.getPriceManager().getTransactionTax(base, taxRate));
            }

            case "sell_1_tax": {
                // Tax deducted when player sells 1 emerald (using player's rate)
                double base    = plugin.getPriceManager().getSellPrice();
                double taxRate = player != null
                        ? plugin.getConfigManager().getPlayerTaxRate(player)
                        : plugin.getPriceManager().getTransactionTaxRate();
                return String.format("%.2f", plugin.getPriceManager().getTransactionTax(base, taxRate));
            }

            case "sell_64_value": {
                // Net money player receives when selling 64 emeralds (after player's tax)
                double base    = plugin.getPriceManager().getSellPrice() * 64;
                double taxRate = player != null
                        ? plugin.getConfigManager().getPlayerTaxRate(player)
                        : plugin.getPriceManager().getTransactionTaxRate();
                return String.format("%.2f", base - plugin.getPriceManager().getTransactionTax(base, taxRate));
            }

            case "sell_64_tax": {
                // Tax deducted when player sells 64 emeralds (using player's rate)
                double base    = plugin.getPriceManager().getSellPrice() * 64;
                double taxRate = player != null
                        ? plugin.getConfigManager().getPlayerTaxRate(player)
                        : plugin.getPriceManager().getTransactionTaxRate();
                return String.format("%.2f", plugin.getPriceManager().getTransactionTax(base, taxRate));
            }

            case "sell_all_value": {
                // Net money player receives when selling ALL emeralds (after player's tax)
                if (player == null) return "0.00";
                int    count   = ItemUtils.countEmeralds(player.getInventory());
                double base    = plugin.getPriceManager().getSellPrice() * count;
                double taxRate = plugin.getConfigManager().getPlayerTaxRate(player);
                return String.format("%.2f", base - plugin.getPriceManager().getTransactionTax(base, taxRate));
            }

            case "sell_all_tax": {
                // Tax deducted when selling ALL emeralds (using player's rate)
                if (player == null) return "0.00";
                int    count   = ItemUtils.countEmeralds(player.getInventory());
                double base    = plugin.getPriceManager().getSellPrice() * count;
                double taxRate = plugin.getConfigManager().getPlayerTaxRate(player);
                return String.format("%.2f", plugin.getPriceManager().getTransactionTax(base, taxRate));
            }

            case "sell_all_count":
                if (player == null) return "0";
                return String.valueOf(ItemUtils.countEmeralds(player.getInventory()));

            // ===== Affordability Placeholders (player-aware tax) =====

            case "can_afford_1_emerald": {
                if (player == null) return plugin.getMessageManager().getRaw("affordability.cannot_afford");
                double base    = plugin.getPriceManager().getBuyPrice();
                double taxRate = plugin.getConfigManager().getPlayerTaxRate(player);
                double total   = base + plugin.getPriceManager().getTransactionTax(base, taxRate);
                boolean can    = plugin.getEconomyService().getBalance(player) >= total;
                return plugin.getMessageManager().getRaw(can ? "affordability.can_afford" : "affordability.cannot_afford");
            }

            case "can_afford_64_emeralds": {
                if (player == null) return plugin.getMessageManager().getRaw("affordability.cannot_afford");
                double base    = plugin.getPriceManager().getBuyPrice() * 64;
                double taxRate = plugin.getConfigManager().getPlayerTaxRate(player);
                double total   = base + plugin.getPriceManager().getTransactionTax(base, taxRate);
                boolean can    = plugin.getEconomyService().getBalance(player) >= total;
                return plugin.getMessageManager().getRaw(can ? "affordability.can_afford" : "affordability.cannot_afford");
            }

            case "has_1_emerald": {
                if (player == null) return plugin.getMessageManager().getRaw("affordability.no_emeralds");
                boolean has = ItemUtils.countEmeralds(player.getInventory()) >= 1;
                return plugin.getMessageManager().getRaw(has ? "affordability.has_emeralds" : "affordability.no_emeralds");
            }

            case "has_64_emeralds": {
                if (player == null) return plugin.getMessageManager().getRaw("affordability.no_emeralds");
                boolean has = ItemUtils.countEmeralds(player.getInventory()) >= 64;
                return plugin.getMessageManager().getRaw(has ? "affordability.has_emeralds" : "affordability.no_emeralds");
            }

            case "has_any_emeralds": {
                if (player == null) return plugin.getMessageManager().getRaw("affordability.no_emeralds");
                boolean has = ItemUtils.countEmeralds(player.getInventory()) > 0;
                return plugin.getMessageManager().getRaw(has ? "affordability.has_emeralds" : "affordability.no_emeralds");
            }

            case "inventory_buy_value_with_tax": {
                // Total cost (with player's tax) to fill inventory with emeralds
                if (player == null) return "0.00";
                double base    = plugin.getPriceManager().getBuyPrice() * calcFreeSpace(player);
                double taxRate = plugin.getConfigManager().getPlayerTaxRate(player);
                return String.format("%.2f", base + plugin.getPriceManager().getTransactionTax(base, taxRate));
            }

            case "inventory_buy_tax": {
                // Tax amount (player's rate) for filling inventory with emeralds
                if (player == null) return "0.00";
                double base    = plugin.getPriceManager().getBuyPrice() * calcFreeSpace(player);
                double taxRate = plugin.getConfigManager().getPlayerTaxRate(player);
                return String.format("%.2f", plugin.getPriceManager().getTransactionTax(base, taxRate));
            }

            case "can_afford_inventory_fill": {
                // Can player afford to completely fill their inventory (with their personal tax)?
                if (player == null) return plugin.getMessageManager().getRaw("affordability.cannot_afford");
                double base    = plugin.getPriceManager().getBuyPrice() * calcFreeSpace(player);
                double taxRate = plugin.getConfigManager().getPlayerTaxRate(player);
                double total   = base + plugin.getPriceManager().getTransactionTax(base, taxRate);
                boolean can    = plugin.getEconomyService().getBalance(player) >= total;
                return plugin.getMessageManager().getRaw(can ? "affordability.can_afford" : "affordability.cannot_afford");
            }

            // ===== Dynamic Pricing Info =====

            case "depletion_factor":
                return String.format("%.2f%%", plugin.getPriceManager().getDepletionFactor() * 100);

            case "total_emeralds_converted":
                return String.valueOf(plugin.getPriceManager().getTotalEmeraldsConverted());

            case "transaction_tax_rate":
                // Global (fallback) tax rate — use %emeraldeconomy_player_tax_rate% for per-player rate
                return String.format("%.1f%%", plugin.getPriceManager().getTransactionTaxRate() * 100);

            case "base_buy_price":
                return String.format("%.2f", plugin.getPriceManager().getBaseBuyPrice());

            case "base_sell_price":
                return String.format("%.2f", plugin.getPriceManager().getBaseSellPrice());

            case "recent_volume":
                return String.valueOf(plugin.getPriceManager().getRecentVolume());

            case "currency_symbol":
                return plugin.getConfigManager().getCurrencySymbol();

            case "currency_name":
                return plugin.getConfigManager().getCurrencyName();

            default:
                return null;
        }
    }

    /**
     * Calculates how many emeralds can fit in the player's inventory.
     * Counts empty slots (×64) plus partial emerald stacks.
     */
    private int calcFreeSpace(Player player) {
        int space = 0;
        for (org.bukkit.inventory.ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType() == org.bukkit.Material.AIR) {
                space += 64;
            } else if (item.getType() == org.bukkit.Material.EMERALD) {
                space += Math.max(0, 64 - item.getAmount());
            }
        }
        return space;
    }
}
