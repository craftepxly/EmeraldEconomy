package craftepxly.me.emeraldeconomy.gui;

import craftepxly.me.emeraldeconomy.EmeraldEconomy;
import craftepxly.me.emeraldeconomy.util.ItemUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MenuManager — manages GUI menus defined in menu.yml.
 */
public class MenuManager {
    
    // Reference to main plugin instance
    private final EmeraldEconomy plugin;
    // Tracks which menu each player currently has open (UUID → menu ID)
    private final Map<UUID, String> openMenus = new ConcurrentHashMap<>();
    // Stores all loaded menu configurations (menu ID → MenuConfig)
    private final Map<String, MenuConfig> menus = new HashMap<>();
    // Executes actions when players click menu items
    private final ActionExecutor actionExecutor;
    
    /**
     * Constructs a new MenuManager and loads all menus from menu.yml.
     * 
     * @param plugin The main EmeraldEconomy plugin instance
     */
    public MenuManager(EmeraldEconomy plugin) {
        // Store plugin reference in memory
        this.plugin = plugin;
        // Initialize action executor (handles item click actions)
        this.actionExecutor = new ActionExecutor(plugin);
        // Load all menu configurations from menu.yml
        loadMenus();
    }
    
    /**
     * Loads all menu configurations from menu.yml.
     * Clears existing menus and re-reads from disk.
     */
    public void loadMenus() {
        // Clear existing menu configurations
        menus.clear();
        
        // Get the "menus" section from menu.yml
        ConfigurationSection menusSection = plugin.getConfigManager().getMenuConfig().getConfigurationSection("menus");
        // If section doesn't exist, log warning and return
        if (menusSection == null) {
            plugin.getLogger().warning("No menus found in menu.yml!");
            return;
        }
        
        // Loop through each menu ID (e.g., "emerald-converter", "admin-panel")
        for (String menuId : menusSection.getKeys(false)) {
            // Get the configuration section for this menu
            ConfigurationSection menuSection = menusSection.getConfigurationSection(menuId);
            // If section exists, load menu config
            if (menuSection != null) {
                // Parse menu configuration
                MenuConfig config = loadMenuConfig(menuId, menuSection);
                // Store in menus map
                menus.put(menuId, config);
                // Log successful load
                plugin.getLogger().info("Loaded menu: " + menuId);
            }
        }
    }
    
    /**
     * Loads a single menu configuration from a YAML section.
     * 
     * @param menuId      The menu identifier
     * @param section     The YAML configuration section
     * @return The loaded MenuConfig object
     */
    private MenuConfig loadMenuConfig(String menuId, ConfigurationSection section) {
        // Read menu title from config (default: "Menu")
        String title = section.getString("title", "Menu");
        // Read menu size (number of slots, default: 27)
        int size = section.getInt("size", 27);
        // Read update interval in ticks (default: 20 = 1 second)
        int updateInterval = section.getInt("update_interval", 20);
        
        // Map to store menu items (slot number → MenuItem)
        Map<Integer, MenuItem> items = new HashMap<>();
        // Get the "items" section
        ConfigurationSection itemsSection = section.getConfigurationSection("items");
        
        // If items section exists, parse each item
        if (itemsSection != null) {
            // Loop through each item ID
            for (String itemId : itemsSection.getKeys(false)) {
                // Get configuration section for this item
                ConfigurationSection itemSection = itemsSection.getConfigurationSection(itemId);
                // If section exists, load menu item
                if (itemSection != null) {
                    // Parse menu item configuration
                    MenuItem menuItem = loadMenuItem(itemId, itemSection);
                    
                    // Handle single slot assignment
                    if (itemSection.contains("slot")) {
                        // Get slot number
                        int slot = itemSection.getInt("slot");
                        // Store item at this slot
                        items.put(slot, menuItem);
                    }
                    
                    // Handle multiple slot assignments (for filling borders, etc.)
                    if (itemSection.contains("slots")) {
                        // Get list of slot numbers
                        List<Integer> slots = itemSection.getIntegerList("slots");
                        // Place same item in all specified slots
                        for (int slot : slots) {
                            items.put(slot, menuItem);
                        }
                    }
                }
            }
        }
        
        // Create and return MenuConfig object
        return new MenuConfig(menuId, title, size, updateInterval, items);
    }
    
    /**
     * Loads a single menu item configuration from a YAML section.
     * 
     * @param itemId  The item identifier
     * @param section The YAML configuration section
     * @return The loaded MenuItem object
     */
    private MenuItem loadMenuItem(String itemId, ConfigurationSection section) {
        // Read material name (default: "STONE")
        String materialName = section.getString("material", "STONE");
        Material material;
        try {
            // Try to parse material enum
            material = Material.valueOf(materialName.toUpperCase());
        } catch (IllegalArgumentException e) {
            // Invalid material → log warning and use STONE as fallback
            plugin.getLogger().warning("Invalid material for item " + itemId + ": " + materialName);
            material = Material.STONE;
        }
        
        // Read item amount (default: 1)
        int amount = section.getInt("amount", 1);
        // Read item display name (default: empty string)
        String name = section.getString("name", "");
        // Read item lore (list of strings)
        List<String> lore = section.getStringList("lore");
        // Read actions to execute on click
        List<String> actions = section.getStringList("actions");
        // Read requirements (not used in this version)
        List<String> requirements = section.getStringList("requirements");
        
        // Create and return MenuItem object
        return new MenuItem(itemId, material, amount, name, lore, actions, requirements);
    }
    
    /**
     * Opens a menu for a player.
     * 
     * @param player The player
     * @param menuId The menu identifier
     */
    public void openMenu(Player player, String menuId) {
        // Get menu configuration by ID
        MenuConfig config = menus.get(menuId);
        // If menu doesn't exist, log warning and return
        if (config == null) {
            plugin.getLogger().warning("Menu not found: " + menuId);
            return;
        }
        
        // Create inventory from config
        Inventory inventory = createInventory(player, config);
        // Open inventory for player
        player.openInventory(inventory);
        // Track that this player has this menu open
        openMenus.put(player.getUniqueId(), menuId);
        
        // Send opening message to player
        plugin.getMessageManager().send(player, "info.opened_gui");
        
        // Start update task if update interval is configured
        if (config.updateInterval > 0) {
            startUpdateTask(player, config);
        }
    }
    
    /**
     * Creates an inventory GUI from a menu configuration.
     * 
     * @param player The player (for placeholder replacements)
     * @param config The menu configuration
     * @return The created Inventory
     */
    private Inventory createInventory(Player player, MenuConfig config) {
        // Parse menu title with placeholders
        Component title = plugin.getMessageManager().parseMessage(
            replacePlaceholders(player, config.title)
        );
        // Create inventory with parsed title and size
        Inventory inventory = Bukkit.createInventory(null, config.size, title);
        
        // Populate items in inventory
        for (Map.Entry<Integer, MenuItem> entry : config.items.entrySet()) {
            // Get slot number
            int slot = entry.getKey();
            // Get menu item
            MenuItem menuItem = entry.getValue();
            
            // Create ItemStack with placeholders replaced
            ItemStack item = createItemStack(player, menuItem);
            // Place item in inventory at specified slot
            inventory.setItem(slot, item);
        }
        
        // Return the fully populated inventory
        return inventory;
    }
    
    /**
     * Creates an ItemStack from a MenuItem with placeholder replacements.
     * 
     * @param player   The player (for placeholders)
     * @param menuItem The menu item configuration
     * @return The created ItemStack
     */
    private ItemStack createItemStack(Player player, MenuItem menuItem) {
        // Create ItemStack with material and amount
        ItemStack item = new ItemStack(menuItem.material, menuItem.amount);
        // Get item meta for modifying display name and lore
        ItemMeta meta = item.getItemMeta();
        
        // If meta exists, set display name and lore
        if (meta != null) {
            // Set display name if configured
            if (!menuItem.name.isEmpty()) {
                // Replace placeholders in name
                String processedName = replacePlaceholders(player, menuItem.name);
                // Parse as Component
                Component nameComponent = plugin.getMessageManager().parseMessage(processedName);
                // Set display name
                meta.displayName(nameComponent);
            }
            
            // Set lore if configured
            if (!menuItem.lore.isEmpty()) {
                // List to store parsed lore components
                List<Component> loreComponents = new ArrayList<>();
                // Loop through each lore line
                for (String loreLine : menuItem.lore) {
                    // Replace placeholders in lore line
                    String processedLine = replacePlaceholders(player, loreLine);
                    // Parse as Component
                    Component component = plugin.getMessageManager().parseMessage(processedLine);
                    // Add to lore list
                    loreComponents.add(component);
                }
                // Set lore
                meta.lore(loreComponents);
            }
            
            // Apply meta to item
            item.setItemMeta(meta);
        }
        
        // Return the created item
        return item;
    }
    
    /**
     * Replaces placeholders in a string with actual values.
     * Supports PlaceholderAPI if enabled.
     * 
     * @param player The player (for player-specific placeholders)
     * @param text   The text with placeholders
     * @return The text with placeholders replaced
     */
    private String replacePlaceholders(Player player, String text) {
        // If PlaceholderAPI is enabled, replace PAPI placeholders
        if (plugin.isPlaceholderAPIEnabled()) {
            text = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, text);
        }
        
        // Replace currency placeholder with value from config
        text = text.replace("{currency}", plugin.getConfigManager().getCurrencySymbol());
        
        // Return processed text
        return text;
    }
    
    /**
     * Handles a player clicking an item in a menu.
     * 
     * @param player The player who clicked
     * @param slot   The slot that was clicked
     */
    public void handleClick(Player player, int slot) {
        // Get the menu ID that this player has open
        String menuId = openMenus.get(player.getUniqueId());
        // If player doesn't have a menu open, return
        if (menuId == null) return;
        
        // Get menu configuration
        MenuConfig config = menus.get(menuId);
        // If config doesn't exist, return
        if (config == null) return;
        
        // Get the menu item at this slot
        MenuItem menuItem = config.items.get(slot);
        // If no item at this slot, return
        if (menuItem == null) return;
        
        // Execute actions asynchronously (to avoid blocking main thread)
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            // Execute all actions for this item
            actionExecutor.executeActions(player, menuItem.actions);
        });
    }
    
    /**
     * Closes a menu for a player.
     * Removes player from open menu tracking.
     * 
     * @param player The player
     */
    public void closeMenu(Player player) {
        // Remove player from openMenus map
        openMenus.remove(player.getUniqueId());
    }
    
    /**
     * Checks if a player has a menu open.
     * 
     * @param player The player
     * @return true if player has a menu open, false otherwise
     */
    public boolean hasMenuOpen(Player player) {
        // Check if player's UUID is in openMenus map
        return openMenus.containsKey(player.getUniqueId());
    }
    
    /**
     * Starts an automatic update task for a menu.
     * Refreshes inventory at configured intervals.
     * 
     * @param player The player
     * @param config The menu configuration
     */
    private void startUpdateTask(Player player, MenuConfig config) {
        // Schedule delayed task (runs after updateInterval ticks)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // Check if player still has this menu open
            if (openMenus.containsKey(player.getUniqueId())) {
                // Get player's open inventory
                Inventory inv = player.getOpenInventory().getTopInventory();
                // Update inventory items
                updateInventory(player, inv, config);
                // Schedule next update (recursive call)
                startUpdateTask(player, config);
            }
        }, config.updateInterval);
    }
    
    /**
     * Updates all items in an inventory.
     * Re-creates items with fresh placeholder values.
     * 
     * @param player    The player
     * @param inventory The inventory to update
     * @param config    The menu configuration
     */
    private void updateInventory(Player player, Inventory inventory, MenuConfig config) {
        // Loop through all menu items
        for (Map.Entry<Integer, MenuItem> entry : config.items.entrySet()) {
            // Get slot number
            int slot = entry.getKey();
            // Get menu item
            MenuItem menuItem = entry.getValue();
            // Create fresh ItemStack with updated placeholders
            ItemStack item = createItemStack(player, menuItem);
            // Update item in inventory
            inventory.setItem(slot, item);
        }
    }
    
    /**
     * Gets the action executor.
     * 
     * @return The ActionExecutor instance
     */
    public ActionExecutor getActionExecutor() {
        return actionExecutor;
    }
    
    /**
     * MenuConfig — holds configuration for a single menu.
     */
    public static class MenuConfig {
        // Menu identifier (e.g., "emerald-converter")
        final String id;
        // Menu title (with placeholders)
        final String title;
        // Inventory size (9, 18, 27, 36, 45, 54)
        final int size;
        // Update interval in ticks (0 = no updates)
        final int updateInterval;
        // Map of slot numbers to menu items
        final Map<Integer, MenuItem> items;
        
        /**
         * Constructs a new MenuConfig.
         * 
         * @param id             Menu ID
         * @param title          Menu title
         * @param size           Inventory size
         * @param updateInterval Update interval in ticks
         * @param items          Map of items
         */
        MenuConfig(String id, String title, int size, int updateInterval, Map<Integer, MenuItem> items) {
            // Store all fields in memory
            this.id = id;
            this.title = title;
            this.size = size;
            this.updateInterval = updateInterval;
            this.items = items;
        }
    }
    
    /**
     * MenuItem — holds configuration for a single menu item.
     */
    public static class MenuItem {
        // Item identifier (e.g., "sell-1", "buy-64")
        final String id;
        // Item material (e.g., EMERALD, DIAMOND)
        final Material material;
        // Item stack size (1-64)
        final int amount;
        // Item display name (with placeholders)
        final String name;
        // Item lore lines (with placeholders)
        final List<String> lore;
        // Actions to execute on click
        final List<String> actions;
        // Requirements to show item (not implemented)
        final List<String> requirements;
        
        /**
         * Constructs a new MenuItem.
         * 
         * @param id           Item ID
         * @param material     Item material
         * @param amount       Item amount
         * @param name         Display name
         * @param lore         Lore lines
         * @param actions      Click actions
         * @param requirements Show requirements
         */
        MenuItem(String id, Material material, int amount, String name, 
                List<String> lore, List<String> actions, List<String> requirements) {
            // Store all fields in memory
            this.id = id;
            this.material = material;
            this.amount = amount;
            this.name = name;
            this.lore = lore;
            this.actions = actions;
            this.requirements = requirements;
        }
    }
}