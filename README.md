# EmeraldEconomy Plugin

**Advanced Emerald ↔ Money Converter for Minecraft Paper 1.21+**

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://adoptium.net/)
[![Paper](https://img.shields.io/badge/Paper-1.21+-blue.svg)](https://papermc.io/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

## 📋 Overview

EmeraldEconomy is a feature-rich Minecraft plugin that allows players to convert emeralds to in-game currency (and vice versa) through an intuitive GUI system. Built with modern Java 21 and Paper API, it features:

- 💎 **Bidirectional Conversion**: Buy and sell emeralds with dynamic pricing
- 📊 **Dynamic Pricing System**: Prices adjust based on market volume
- 🎨 **Fully Customizable GUI**: Configure menus via `menu.yml` (DeluxeMenus-style)
- 🔒 **Secure Transactions**: Atomic operations with rollback support
- 📝 **Transaction Logging**: Complete audit trail of all conversions
- 🎯 **PlaceholderAPI Integration**: Rich placeholder support
- 🌐 **GeyserMC Compatible**: Works with Bedrock Edition players
- ⚡ **High Performance**: Async operations, thread-safe architecture

## 🚀 Features

### Core Features
- **Buy/Sell Emeralds**: Convert between emeralds and money seamlessly
- **Bulk Conversion**: Special pricing for bulk transactions
- **Convert All**: One-click conversion of all emeralds in inventory
- **Cooldown System**: Prevent spam with configurable cooldowns
- **Rate Limiting**: Anti-abuse protection

### Dynamic Pricing
- Market-based price adjustments
- Volume-triggered price changes
- Configurable min/max prices
- Smooth price transitions

### GUI System
- **No Hard-Coded GUIs**: All menus defined in `menu.yml`
- **Action System**: DeluxeMenus-style action execution
- **Real-time Updates**: Dynamic placeholder updates
- **Custom Items**: Support for custom model data and player heads

### Integrations
- **Vault API**: Universal economy support
- **PlaceholderAPI**: Rich placeholder system
- **GeyserMC**: Bedrock Edition compatibility
- **HolographicDisplays**: Price display holograms (optional)

### Security & Performance
- Atomic transactions with rollback
- Per-player transaction locks
- Strict emerald validation (anti-exploit)
- Async task processing
- Thread-safe dynamic pricing

## 📦 Installation

### Requirements
- **Minecraft Server**: Paper 1.21.1+ (or any 1.21.x patch)
- **Java**: JDK 21 or higher
- **Required Plugins**:
  - [Vault](https://www.spigotmc.org/resources/vault.34315/)
  - Economy plugin (EssentialsX, CMI, etc.)
- **Optional Plugins**:
  - [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/)
  - [GeyserMC](https://geysermc.org/)
  - [HolographicDisplays](https://dev.bukkit.org/projects/holographic-displays)

### Installation Steps

1. **Install Dependencies**
   ```bash
   # Install Vault and your economy plugin (e.g., EssentialsX)
   ```

2. **Download Plugin**
   ```bash
   # Place EmeraldEconomy.jar in your plugins/ folder
   ```

3. **Start Server**
   ```bash
   # The plugin will generate default configuration files
   ```

4. **Configure**
   - Edit `config.yml` for pricing and settings
   - Customize `menu.yml` for GUI layouts
   - Modify `messages.yml` for language/messages

5. **Reload**
   ```bash
   /ecadmin reload
   ```

## ⚙️ Configuration

### config.yml

```yaml
currency:
  name: "Dollar"
  symbol: "$"

prices:
  buy: 9.5          # Server buys emerald from player
  sell: 10.0        # Server sells emerald to player
  bulk_multiplier: 0.98
  bulk_threshold: 16

dynamic_pricing:
  enabled: true
  window_seconds: 300
  volume_threshold: 100
  adjustment_percent: 0.5
  min_price: 1.0
  max_price: 1000.0
  update_interval: 5

transaction:
  cooldown: 3       # Seconds between transactions
  rate_limit:
    enabled: true
    max_per_minute: 20

security:
  strict_emerald_check: true
  verify_economy_balance: true
```

### menu.yml

Define custom GUIs with an action system:

```yaml
menus:
  emerald-converter:
    title: "<gradient:#FFD700:#FFA500>Emerald Converter</gradient>"
    size: 27
    update_interval: 20
    
    items:
      convert-one:
        slot: 10
        material: EMERALD
        name: "<green><bold>Sell 1 Emerald"
        lore:
          - "<gray>Click to sell 1 emerald"
          - "<yellow>You receive: <white>%emeraldeconomy_price_buy% $"
        actions:
          - "[async]convert_sell:1"
          - "[sound]ENTITY_EXPERIENCE_ORB_PICKUP:1.0:1.0"
          - "[message]&aSuccess!"
          - "[refresh]"
```

### Action System

Supported action types:
- `[console]<command>` - Execute console command
- `[player]<command>` - Execute player command
- `[message]<text>` - Send message to player
- `[sound]<sound:volume:pitch>` - Play sound
- `[close]` - Close menu
- `[refresh]` - Update menu
- `[async]<action>` - Execute custom async action

Custom async actions:
- `convert_sell:<amount>` - Sell emeralds
- `convert_sell_all` - Sell all emeralds
- `convert_buy:<amount>` - Buy emeralds

## 🎮 Usage

### Commands

| Command | Aliases | Description | Permission |
|---------|---------|-------------|------------|
| `/emeraldconverter` | `/ec`, `/emerald` | Open converter GUI | `emeraldeconomy.use` |
| `/ecadmin reload` | `/eca reload` | Reload configurations | `emeraldeconomy.admin` |
| `/ecadmin stats [player]` | - | View conversion statistics | `emeraldeconomy.admin` |
| `/ecadmin setprice <buy\|sell> <price>` | - | Set prices | `emeraldeconomy.admin` |
| `/ecadmin info` | - | View current market info | `emeraldeconomy.admin` |

### Permissions

```yaml
emeraldeconomy.*            # All permissions (default: op)
emeraldeconomy.use          # Use the converter (default: true)
emeraldeconomy.admin        # Admin commands (default: op)
emeraldeconomy.bypass.cooldown  # Bypass cooldowns (default: op)
```

### PlaceholderAPI Placeholders

| Placeholder | Description |
|-------------|-------------|
| `%emeraldeconomy_price_buy%` | Current buy price (server buys) |
| `%emeraldeconomy_price_sell%` | Current sell price (server sells) |
| `%emeraldeconomy_total_converted%` | Player's total converted emeralds |
| `%emeraldeconomy_player_emeralds%` | Emeralds in player's inventory |
| `%emeraldeconomy_all_sell_value%` | Total value of all player's emeralds |
| `%emeraldeconomy_stack_sell_value%` | Value of 64 emeralds (sell) |
| `%emeraldeconomy_stack_buy_value%` | Cost of 64 emeralds (buy) |
| `%emeraldeconomy_recent_volume%` | Recent market volume |
| `%emeraldeconomy_currency_symbol%` | Currency symbol |
| `%emeraldeconomy_currency_name%` | Currency name |

## 📊 Transaction Logging

All transactions are logged to `plugins/EmeraldEconomy/transactions.log`:

```
2026-02-05T14:22:33Z | UUID=xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx | name=Player | TYPE=SELL | EMERALD=64 | MONEY=608.00 | PRICE=9.50 | TXID=ec_00000123
```

Log format:
- **Timestamp**: ISO-8601 format
- **UUID**: Player's unique ID
- **Name**: Player name
- **Type**: `BUY` or `SELL`
- **Emerald**: Amount of emeralds
- **Money**: Amount of money
- **Price**: Price at transaction time
- **TXID**: Unique transaction ID

## 🛡️ Security Features

### Anti-Exploit Protection
- **Strict Emerald Validation**: Only accepts genuine emeralds (no renamed items)
- **Atomic Transactions**: All-or-nothing operation with rollback
- **Race Condition Prevention**: Per-player locks
- **Double-Spend Protection**: Balance verification before withdrawal

### Rate Limiting
- **Transaction Cooldown**: Configurable delay between transactions
- **Per-Minute Limit**: Maximum transactions per player per minute
- **Bypass Permission**: Admins can bypass limits

## 🎨 Customization Examples

### Custom Menu Item

```yaml
custom-buy-10:
  slot: 14
  material: EMERALD_BLOCK
  amount: 10
  name: "<aqua><bold>Buy 10 Emeralds"
  lore:
    - "<gray>Special bulk deal!"
    - "<yellow>Price: <white>%emeraldeconomy_price_sell% * 10"
  actions:
    - "[async]convert_buy:10"
    - "[sound]BLOCK_NOTE_BLOCK_PLING:1.0:2.0"
    - "[message]&bPurchased 10 emeralds!"
```

### Dynamic Pricing Example

With default settings:
- **Base Prices**: Buy $9.50, Sell $10.00
- **Volume Threshold**: 100 emeralds in 5 minutes
- **Adjustment**: -0.5% per 50 emeralds over threshold

If 200 emeralds traded in 5 minutes:
- Buy price: ~$9.02 (server pays less)
- Sell price: ~$10.53 (players pay more)

## 🐛 Troubleshooting

### Common Issues

**Plugin won't enable**
- Check you have Vault and an economy plugin installed
- Verify Java 21+ is being used
- Check console for error messages

**Transactions fail**
- Ensure economy plugin is working (`/balance`)
- Check player has sufficient funds/emeralds
- Verify inventory has space for emeralds

**GUI doesn't open**
- Check `menu.yml` for syntax errors
- Ensure player has `emeraldeconomy.use` permission
- Reload plugin: `/ecadmin reload`

**Prices not updating**
- Check `dynamic_pricing.enabled` is `true`
- Verify sufficient transaction volume
- Check console for errors

## 📝 API Usage

### Maven Dependency

```xml
<dependency>
    <groupId>craftepxly.me</groupId>
    <artifactId>EmeraldEconomy</artifactId>
    <version>1.0.0</version>
    <scope>provided</scope>
</dependency>
```

### Example API Usage

```java
EmeraldEconomy plugin = (EmeraldEconomy) Bukkit.getPluginManager().getPlugin("EmeraldEconomy");

// Get current prices
double buyPrice = plugin.getPriceManager().getBuyPrice();
double sellPrice = plugin.getPriceManager().getSellPrice();

// Execute transaction
TransactionResult result = plugin.getTransactionManager().sellEmerald(player, 64);
if (result.isSuccess()) {
    // Transaction successful
}

// Get player stats
int totalConverted = plugin.getPlayerStatsStorage().getTotalConverted(player.getUniqueId());
```

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.


## 🎉 Credits

**Author**: craftepxly  

Special thanks to:
- Paper team for the excellent server software
- Vault developers for the economy API
- PlaceholderAPI team for the placeholder system

---

## Private plugin for [Stresmen SMP S2](https://youtube.com/@stresmen) (for now)
