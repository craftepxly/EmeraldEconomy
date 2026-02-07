# EmeraldEconomy Plugin

**Advanced Emerald ↔ Money Converter with Dynamic Economics for Minecraft Paper 1.21+**

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://adoptium.net/)
[![Paper](https://img.shields.io/badge/Paper-1.21+-blue.svg)](https://papermc.io/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![Version](https://img.shields.io/badge/Version-3.0-blue.svg)](CHANGELOG-v3.0.md)

## 📋 Overview

EmeraldEconomy is a feature-rich Minecraft plugin that allows players to convert emeralds to in-game currency (and vice versa) through an intuitive GUI system. Built with modern Java 21 and Paper API, it features:

- 💎 **Bidirectional Conversion**: Buy and sell emeralds with advanced dynamic pricing
- 📊 **Advanced Economics**: Supply & demand system with resource depletion mechanics
- 💰 **Transaction Tax**: Built-in money sink (5% default) to combat inflation
- 🎒 **Fill Inventory**: Buy emeralds to fill entire inventory in one click
- 🎨 **Fully Customizable GUI**: Configure menus via `menu.yml` (DeluxeMenus-style)
- 🔒 **Secure Transactions**: Atomic operations with rollback support
- 📝 **Transaction Logging**: Complete audit trail of all conversions
- 🎯 **PlaceholderAPI Integration**: 22 rich placeholders for complete customization
- 🌐 **GeyserMC Compatible**: Works with Bedrock Edition players
- ⚡ **High Performance**: Async operations, thread-safe architecture
- 🌍 **Multi-Language**: English & Indonesian built-in

## 🆕 What's New in v3.0

### 🔥 Major Features

#### 1. **Fill Inventory** (NEW)
Buy emeralds to fill your entire inventory in one click!
- Automatically calculates available space
- Shows total cost with tax breakdown
- Transparent pricing display
- Smart inventory management

```yaml
# In-game display:
Fill Inventory
├─ Available Space: 512 emeralds
├─ Cost (Base): $5,222.40
├─ Tax (5%): $261.12
├─ Total Cost: $5,483.52
└─ Balance: $6,000.00
```

#### 2. **Advanced Dynamic Pricing System** (COMPLETE REWRITE)
Real economics simulation with:
- **Supply & Demand**: Separate tracking for buy/sell pressure
- **EWMA Smoothing**: Reduces price volatility (α=0.3)
- **Resource Depletion**: Simulates mining difficulty
- **Anti-Manipulation**: Max 100 emeralds impact per transaction
- **Price Recovery**: Gradual return to base prices

**Economic Formula**:
```
Buy Price = Base + (Demand × Sensitivity × Depletion) - Depletion Penalty
Sell Price = Base - (Supply × Sensitivity) + Premium
Depletion = 1.0 - (Total Converted × Rate) + Recovery
```

#### 3. **Transaction Tax System** (NEW)
Automatic money sink to combat inflation:
- **Default**: 5% tax on all transactions
- **Configurable**: 0-100% via config
- **Transparent**: Players see tax amount in GUI
- **Selling**: Tax deducted from earnings
- **Buying**: Tax added to cost

**Example**:
```
Selling: 64 emeralds × $10 = $640
Tax (5%): $32 removed from economy
Player receives: $608

Buying: 64 emeralds × $12 = $768
Tax (5%): $38.40
Player pays: $806.40
```

#### 4. **Multi-Language Support** (NEW)
Built-in translations with hot-reload:
- 🇬🇧 English (`messages_eng.yml`)
- 🇮🇩 Indonesian (`messages_id.yml`)
- Switch languages via `messages_locale` in config
- Easy to add more languages
- All messages customizable

#### 5. **Custom Amount Feature** (NEW)
Input any amount via chat:
- Click "Custom Amount" button in GUI
- Type amount in chat (e.g., "50" or "cancel")
- 60-second timeout with cleanup
- Full input validation
- Works for both buying and selling

### 📊 22 PlaceholderAPI Placeholders (10 NEW)

New placeholders for advanced features:
- `%emeraldeconomy_inventory_buy_value_with_tax%` - Total cost with tax
- `%emeraldeconomy_inventory_buy_tax%` - Tax amount for filling inventory
- `%emeraldeconomy_can_afford_inventory_fill%` - Can afford check (true/false)
- `%emeraldeconomy_inventory_buy_space%` - Free inventory space
- `%emeraldeconomy_depletion_factor%` - Resource depletion percentage
- `%emeraldeconomy_total_emeralds_converted%` - Global emerald conversions
- `%emeraldeconomy_transaction_tax_rate%` - Current tax rate
- `%emeraldeconomy_base_buy_price%` - Base buy price
- `%emeraldeconomy_base_sell_price%` - Base sell price

### 🗑️ Removed Features
- **HolographicDisplays**: Removed dependency to simplify plugin
- Cleaner codebase, faster startup
- Smaller plugin size (~42KB vs ~45KB)

### 🔧 Breaking Changes
- `PriceManager` completely rewritten - API changes
- Old `loadPricesFromConfig()` → `loadConfiguration()`
- Old `setBasePrice()` removed - use config + reload
- Price methods no longer take amount parameter

### 📈 Performance Improvements
- Thread-safe price calculations with ReadWriteLock
- Async transaction processing
- Optimized inventory space calculations
- Reduced memory footprint

## 🚀 Features

### Core Features
- **Buy/Sell Emeralds**: Convert between emeralds and money seamlessly
- **Bulk Conversion**: Special pricing for bulk transactions
- **Convert All**: One-click conversion of all emeralds in inventory
- **Fill Inventory** ⭐ *NEW v3.0*: Buy emeralds to completely fill inventory space
- **Custom Amount**: Type exact amount for precise conversions
- **Cooldown System**: Prevent spam with configurable cooldowns
- **Rate Limiting**: Anti-abuse protection

### Advanced Dynamic Pricing System ⭐ *NEW v3.0*
- **Supply & Demand Economics**: Prices adjust based on market activity
  - High demand → Buy prices increase
  - High supply → Sell prices decrease
- **EWMA Smoothing**: Exponential weighted moving average reduces volatility
- **Resource Depletion**: Simulates "mining difficulty" - more conversions = lower buying pressure
- **Gradual Recovery**: Depletion recovers over time (configurable)
- **Transaction Tax**: 5% default (configurable 0-100%) - removes money from economy
- **Anti-Manipulation**: 
  - Max impact per transaction (prevents price manipulation)
  - Rate limiting and cooldowns
- **Price Bounds**: Configurable min/max prices prevent extreme fluctuations

### GUI System
- **No Hard-Coded GUIs**: All menus defined in `menu.yml`
- **Action System**: DeluxeMenus-style action execution
- **Real-time Updates**: Dynamic placeholder updates
- **Custom Items**: Support for custom model data and player heads
- **Interactive Buttons**: 
  - Sell 1, 64, or all emeralds
  - Buy 1, 64, or fill inventory
  - Custom amount input via chat
  - Price information panel

### Integrations
- **Vault API**: Universal economy support
- **PlaceholderAPI**: 22 rich placeholders (10 new in v3.0)
- **GeyserMC**: Bedrock Edition compatibility

### Security & Performance
- Atomic transactions with rollback
- Per-player transaction locks
- Strict emerald validation (anti-exploit)
- Async task processing
- Thread-safe dynamic pricing
- Transaction tax as money sink

## 📦 Installation

### Requirements
- **Minecraft Server**: Paper 1.21.1+ (or any 1.21.x patch)
- **Java**: JDK 21 or higher
- **Required Plugins**:
  - [Vault](https://www.spigotmc.org/resources/vault.34315/)
  - Economy plugin (EssentialsX, CMI, etc.)
- **Optional Plugins**:
  - [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) (Recommended for full features)
  - [GeyserMC](https://geysermc.org/) (For Bedrock Edition support)

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
# Language/Messages Configuration (NEW v3.0)
messages_locale: "messages_id"   # Options: messages_eng | messages_id

currency:
  name: "Dollar"
  symbol: "$"

prices:
  buy: 9.5          # Server buys emerald from player
  sell: 10.0        # Server sells emerald to player
  bulk_multiplier: 0.98
  bulk_threshold: 16

# Dynamic Pricing System (v3.0 - COMPLETELY REWRITTEN)
dynamic_pricing:
  enabled: true
  
  # Time window in seconds to track transactions
  window_seconds: 300
  
  # How often to update prices (seconds)
  update_interval: 5
  
  # Price bounds
  min_price: 1.0
  max_price: 1000.0
  
  # Supply & Demand Sensitivity (NEW v3.0)
  # Higher values = more price volatility
  demand_sensitivity: 0.02   # How much demand affects buy price
  supply_sensitivity: 0.02   # How much supply affects sell price
  
  # Resource Depletion System (NEW v3.0)
  # Simulates "mining difficulty" - more conversions = lower buying pressure
  depletion_rate: 0.0001           # Depletion per emerald converted
  depletion_recovery_seconds: 3600  # Time for full recovery (1 hour)
  
  # Anti-Manipulation (NEW v3.0)
  max_impact_per_transaction: 100  # Max emeralds that affect price per transaction
  
  # Transaction Tax (Money Sink) (NEW v3.0)
  # Percentage of transaction value removed from economy
  transaction_tax_rate: 0.05  # 5% tax (0.05 = 5%, 0.10 = 10%)

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
- `convert_sell:<amount>` - Sell specific amount of emeralds
- `convert_sell_all` - Sell all emeralds in inventory
- `convert_buy:<amount>` - Buy specific amount of emeralds
- `convert_buy_inventory` or `convert_buy_all` ⭐ *NEW v3.0* - Fill inventory with emeralds
- `custom_amount_sell` ⭐ *NEW v3.0* - Input custom amount via chat (sell)
- `custom_amount_buy` ⭐ *NEW v3.0* - Input custom amount via chat (buy)

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

### PlaceholderAPI Placeholders (22 Total)

#### Price Information (4)
| Placeholder | Description |
|-------------|-------------|
| `%emeraldeconomy_price_buy%` | Current buy price (server buys from player) |
| `%emeraldeconomy_price_sell%` | Current sell price (server sells to player) |
| `%emeraldeconomy_base_buy_price%` ⭐ *NEW* | Base buy price (before adjustments) |
| `%emeraldeconomy_base_sell_price%` ⭐ *NEW* | Base sell price (before adjustments) |

#### Stack & Bulk (2)
| Placeholder | Description |
|-------------|-------------|
| `%emeraldeconomy_stack_sell_value%` | Value of 64 emeralds when player sells |
| `%emeraldeconomy_stack_buy_value%` | Cost of 64 emeralds when player buys |

#### Player Stats (3)
| Placeholder | Description |
|-------------|-------------|
| `%emeraldeconomy_total_converted%` | Player's total emeralds converted |
| `%emeraldeconomy_player_emeralds%` | Emeralds in player's inventory |
| `%emeraldeconomy_all_sell_value%` | Total value of all player's emeralds |

#### Inventory Fill Feature (5) ⭐ *NEW v3.0*
| Placeholder | Description |
|-------------|-------------|
| `%emeraldeconomy_inventory_buy_space%` | Free space for emeralds in inventory |
| `%emeraldeconomy_inventory_buy_value%` | Base cost to fill inventory (no tax) |
| `%emeraldeconomy_inventory_buy_value_with_tax%` | **Total cost** to fill inventory (with tax) |
| `%emeraldeconomy_inventory_buy_tax%` | Tax amount for filling inventory |
| `%emeraldeconomy_can_afford_inventory_fill%` | Can afford? Returns `true` or `false` |

#### Dynamic Pricing Stats (4) ⭐ *NEW v3.0*
| Placeholder | Description |
|-------------|-------------|
| `%emeraldeconomy_depletion_factor%` | Resource depletion percentage |
| `%emeraldeconomy_total_emeralds_converted%` | Global emerald conversions |
| `%emeraldeconomy_transaction_tax_rate%` | Transaction tax rate percentage |
| `%emeraldeconomy_recent_volume%` | Recent transaction volume |

#### Currency (2)
| Placeholder | Description |
|-------------|-------------|
| `%emeraldeconomy_currency_symbol%` | Currency symbol ($) |
| `%emeraldeconomy_currency_name%` | Currency name (Dollar) |

**Total: 22 placeholders** (10 new in v3.0)

## 🔧 Building from Source

### Prerequisites
- JDK 21+
- Gradle 9.0.0+

### Build Steps

```bash
# Clone repository
git clone https://github.com/craftepxly/EmeraldEconomy.git
cd EmeraldEconomy

# Build with Gradle
./gradlew clean shadowJar

# Output: build/libs/EmeraldEconomy-1.0.0.jar
```

### Development Setup (IntelliJ IDEA)

1. Install [Minecraft Development plugin](https://plugins.jetbrains.com/plugin/8327-minecraft-development)
2. Import project as Gradle project
3. Wait for Gradle sync to complete
4. Run configurations will be auto-generated

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

## 🤝 Contributing

Contributions are welcome! Please:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📬 Support

- **Issues**: [GitHub Issues](https://github.com/craftepxly/EmeraldEconomy/issues)
- **Discord**: [Join our Discord](#)
- **Documentation**: [Wiki](https://github.com/craftepxly/EmeraldEconomy/wiki)

## 🎉 Credits

**Author**: craftepxly  
**Contributors**: [View Contributors](https://github.com/craftepxly/EmeraldEconomy/graphs/contributors)

Special thanks to:
- Paper team for the excellent server software
- Vault developers for the economy API
- PlaceholderAPI team for the placeholder system

---

## Private plugin for [Stresmen SMP S2](https://youtube.com/@stresmen) (for now)
