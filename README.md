# EmeraldEconomy

**Turn emeralds into money (and back) with real market economics**

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://adoptium.net/)
[![Paper](https://img.shields.io/badge/Paper-1.21+-blue.svg)](https://papermc.io/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![Version](https://img.shields.io/badge/Version-3.0.1-brightgreen.svg)](CHANGELOG.md)

A Minecraft economy plugin that actually makes sense. Players can trade emeralds for server currency through a clean GUI, complete with supply-demand pricing, transaction taxes, and protection against exploitation.

---

## What's Different

Most economy plugins just let you set a fixed price and call it a day. EmeraldEconomy goes further:

- **Prices fluctuate based on actual trading activity** - high demand drives prices up, oversupply brings them down
- **Built-in money sink** - configurable transaction tax helps combat inflation
- **Resource depletion mechanics** - simulates mining difficulty (the more people convert, the less valuable emeralds become)
- **Everything's transparent** - players see exactly what they'll pay, including taxes, before clicking

---

## The Economic Engine

This is where EmeraldEconomy differs from typical "set price and forget" plugins. The system simulates real market dynamics.

### Supply & Demand Tracking

The plugin tracks buy and sell transactions separately:
- **High buying activity** → Demand pressure increases → Buy prices rise
- **High selling activity** → Supply pressure increases → Sell prices fall

This creates natural price discovery. If players are dumping emeralds on the market, prices drop. If everyone's buying, prices climb.

### EWMA Smoothing (α=0.3)

Raw transaction data gets smoothed using Exponential Weighted Moving Average to prevent price whiplash:
```
Current Pressure = (0.3 × New Data) + (0.7 × Previous Pressure)
```

This means prices adjust gradually rather than spiking on single large trades. The market "remembers" recent activity but doesn't overreact.

### The Pricing Formula

Here's what actually happens when prices update:

**Buy Price (what server pays players):**
```
Buy Price = Base Buy Price 
          + (Demand Pressure × Demand Sensitivity × Depletion Factor) 
          - Depletion Penalty
```

**Sell Price (what players pay server):**
```
Sell Price = Base Sell Price 
           - (Supply Pressure × Supply Sensitivity) 
           + Market Premium
```

**Resource Depletion:**
```
Depletion Factor = max(0.1, 1.0 - (Total Converted × Depletion Rate) + Recovery Bonus)
```

The depletion factor simulates emeralds getting harder to find. As more get converted server-wide, the factor drops from 1.0 toward 0.1 (minimum 10%). This reduces buying pressure - the server pays less for emeralds as they become "common." The factor gradually recovers over time (default: 1 hour for full recovery).

### Transaction Tax (Money Sink)

Every trade removes money from the economy:

**When selling emeralds:**
```
Player sells: 64 emeralds at $10/ea = $640 gross
Tax (5%): $640 × 0.05 = $32
Player receives: $640 - $32 = $608
Economy loses: $32 (destroyed, not given to anyone)
```

**When buying emeralds:**
```
Player buys: 64 emeralds at $12/ea = $768 base cost
Tax (5%): $768 × 0.05 = $38.40
Player pays: $768 + $38.40 = $806.40
Economy loses: $38.40 (destroyed)
```

The taxed money just disappears. This combats inflation - especially useful on servers with aggressive mob farms or voting rewards.

### Anti-Manipulation Safeguards

**Max Impact Per Transaction:** Individual trades can't move prices dramatically. By default, only 100 emeralds per transaction count toward price pressure. Someone dumping 1000 emeralds at once affects prices the same as dumping 100.

**Price Bounds:** Configured min/max prices (default: $1-$1000) prevent runaway inflation or deflation.

**Cooldowns & Rate Limits:** 3-second cooldown between trades per player, max 20 trades per minute. Prevents rapid-fire manipulation attempts.

### Real-World Example

**Starting state:**
- Base buy price: $9.50
- Base sell price: $10.00
- Depletion: 100% (fresh)
- Tax: 5%

**Player A sells 200 emeralds over 5 minutes:**
- Supply pressure builds
- Sell price drops to ~$9.80
- Buy price drops slightly to ~$9.30
- Depletion drops to 98%
- Tax collected: ~$95 removed from economy

**One hour later (no activity):**
- Depletion recovers to 99.5%
- Prices drift back toward base
- Supply pressure decays

**Player B tries to buy 500 emeralds:**
- Demand pressure spikes
- Buy price rises to ~$9.65
- Only first 100 emeralds affect price (anti-manipulation)
- Player pays ~$5,075 (including tax)
- Tax collected: ~$242 removed from economy

This creates a living economy where prices reflect actual trading patterns rather than admin-set constants.

### Why This Matters

**Typical economy plugin:**
- Admin sets emerald = $10
- Price never changes
- Players exploit this (emerald farms → infinite money)
- Server economy inflates
- Money becomes worthless

**EmeraldEconomy:**
- Admin sets base prices ($9.50 buy, $10.00 sell)
- Prices adjust based on trading (±20% range typical)
- Emerald farms still work, but returns diminish over time
- Tax removes money from circulation
- Economy stays balanced

**Comparison:**

| Feature | Typical Plugin | EmeraldEconomy |
|---------|---------------|----------------|
| **Price Stability** | Fixed forever | Adjusts to market |
| **Inflation Control** | None | Tax + depletion |
| **Exploit Protection** | Hope for the best | Built-in dampening |
| **Transparency** | Hidden calculations | Everything visible |
| **Farm Impact** | Unlimited profit | Diminishing returns |
| **Admin Maintenance** | Constant rebalancing | Set and forget |

---

## Quick Start

1. Drop the jar in your plugins folder
2. Install Vault + any economy plugin (EssentialsX works great)
3. Restart server
4. Players use `/ec` to open the converter

That's it. The plugin generates sane defaults, but you can tweak everything in `config.yml` and `menu.yml`.

### Economic Flow Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                    EMERALD ECONOMY LOOP                      │
└─────────────────────────────────────────────────────────────┘

Player Actions:
  ┌──────────┐         ┌──────────┐
  │  SELL    │         │   BUY    │
  │ Emeralds │         │ Emeralds │
  └────┬─────┘         └─────┬────┘
       │                     │
       ▼                     ▼
  ┌─────────────────────────────────┐
  │   TRANSACTION PROCESSOR          │
  │  • Validate items/balance        │
  │  • Calculate price + tax         │
  │  • Execute atomically            │
  └─────────────┬───────────────────┘
                │
                ▼
  ┌─────────────────────────────────┐
  │      PRICE MANAGER               │
  │  • Record transaction            │
  │  • Update demand/supply          │
  │  • Apply EWMA smoothing          │
  │  • Calculate depletion           │
  │  • Update prices (every 5s)      │
  └─────────────┬───────────────────┘
                │
       ┌────────┴────────┐
       ▼                 ▼
  ┌─────────┐       ┌─────────┐
  │  TAX    │       │ MARKET  │
  │ REMOVAL │       │ SIGNALS │
  └─────────┘       └─────────┘
       │                 │
       ▼                 ▼
  Money leaves      Prices adjust
  economy           for next trade
  (inflation        (supply/demand)
   control)

Key Numbers (defaults):
• Tax rate: 5% (configurable 0-100%)
• EWMA alpha: 0.3 (30% new data, 70% history)
• Depletion rate: 0.0001 per emerald converted
• Recovery time: 1 hour to full recovery
• Max price impact: 100 emeralds/transaction
• Update interval: 5 seconds
• Price bounds: $1.00 - $1000.00
```

---

## Core Features

### The Converter GUI
Players hit `/ec` and get a menu that shows:
- Current buy/sell prices (updates every second)
- Their balance and emerald count
- Buttons to sell 1, sell 64, sell all
- Buttons to buy 1, buy 64, fill inventory
- Custom amount option (type in chat)
- Complete cost breakdowns with tax

### Smart Pricing
The plugin tracks every transaction and adjusts prices accordingly:
- Someone sells 100 emeralds → price drops slightly
- Someone buys 200 emeralds → price rises slightly
- EWMA smoothing prevents wild swings
- Configurable min/max bounds keep things reasonable

### Transaction Tax
Every trade removes a small percentage from the economy (5% by default):
- Selling 64 emeralds? You get the value minus 5%
- Buying 64 emeralds? You pay the cost plus 5%
- The taxed money just disappears - helps control inflation

### Fill Inventory
One-click button calculates your available space and buys that many emeralds:
- Counts empty slots and partial stacks
- Shows you the full cost breakdown first
- Only goes through if you can afford it

### Resource Depletion
The more emeralds get converted server-wide, the less valuable they become:
- Simulates miners exhausting easy veins
- Gradually recovers over time (configurable)
- Factors into buy prices (sell prices stay stable)
- Prevents runaway inflation from emerald farms

---

## Installation

**Requirements:**
- Paper 1.21+ (or any compatible fork)
- Java 21
- Vault
- Any economy plugin (EssentialsX, CMI, etc)

**Optional:**
- PlaceholderAPI (for the 40 placeholders)
- GeyserMC (if you have Bedrock players)

**Steps:**
1. Install Vault and your economy plugin
2. Drop EmeraldEconomy.jar in plugins/
3. Start server (generates default configs)
4. Edit configs if you want
5. `/ecadmin reload` if you changed anything

---

## Configuration

### config.yml - The Important Bits

```yaml
# Which language file to use
messages_locale: "messages_eng"  # or messages_id

# Your server's currency
currency:
  name: "Dollar" # Dollar or Emerald
  symbol: "$"

# Starting prices (these will fluctuate)
prices:
  buy: 9.5   # Server buys emeralds from players at this price
  sell: 10.0 # Server sells emeralds to players at this price

# Market dynamics
dynamic_pricing:
  enabled: true
  update_interval: 5  # Recalculate every 5 seconds
  
  # How sensitive prices are to trading
  demand_sensitivity: 0.02  # Higher = more volatile buy prices
  supply_sensitivity: 0.02  # Higher = more volatile sell prices
  
  # Resource depletion (simulates mining difficulty)
  depletion_rate: 0.0001           # How fast emeralds "run out"
  depletion_recovery_seconds: 3600 # How long to recover (1 hour)
  
  # Protection
  max_impact_per_transaction: 100  # Whales can't manipulate prices
  
  # Money sink
  transaction_tax_rate: 0.05  # 5% tax on all trades
  
  # Safety limits
  min_price: 1.0    # Floor
  max_price: 1000.0 # Ceiling

# Anti-spam
transaction:
  cooldown: 3  # Seconds between trades per player
  rate_limit:
    enabled: true
    max_per_minute: 20
```

### menu.yml - Customize the GUI

The GUI is completely configurable. Here's how a button looks:

```yaml
sell-64:
  slot: 11
  material: EMERALD
  amount: 64
  name: "<green><bold>Sell 64 Emeralds"
  lore:
    - "<dark_gray>ᴇxᴄʜᴀɴɢᴇ sʏsᴛᴇᴍ"
    - ""
    - “<gray>Sell 64 emeralds at once”
    - “<gray>from your inventory.”
    - ""
    - "<gray>Price: <green>%emeraldeconomy_price_buy%/ea"
    - "<gray>Gross Value: <green>$%emeraldeconomy_stack_sell_value%"
    - "<gray>Tax (5%): <green>$%emeraldeconomy_sell_64_tax%"
    - "<gray>You get: <green>$%emeraldeconomy_sell_64_value%"
    - ""
    - "<gray>Your money: <green>$%vault_eco_balance_formatted%"
    - "<gray>Your emeralds: <green>%emeraldeconomy_player_emeralds%"
    - ""
    - "<gray>• <yellow>Click <white>to sell"
  enchantments:
    - "DURABILITY:1"
  flags:
    - "HIDE_ENCHANTS"
  actions:
    - "[requirement]has_emerald:64"
    - "[async]convert_sell:64"
    - "[sound]ENTITY_EXPERIENCE_ORB_PICKUP:1.0:1.2"
    - "[refresh]"
```

The menu is 54 slots (6 rows). Check the included menu.yml for the full layout.

### Custom Heads

You can use player heads for decoration:

```yaml
player-stats:
  slot: 31
  material: PLAYER_HEAD
  skull-owner: "%player_name%"  # Shows their own head
  name: "<green>%player_name%'s Stats"
```

Or use custom textures (base64) from minecraft-heads.com:

```yaml
info-panel:
  slot: 22
  material: PLAYER_HEAD
  skull-texture: "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6..."
  name: "<yellow>Market Info"
```

---

## Commands & Permissions

### Commands
| Command | What it does |
|---------|--------------|
| `/ec` (or `/emerald`) | Open the converter |
| `/ecadmin reload` | Reload configs |
| `/ecadmin info` | View current prices and stats |
| `/ecadmin stats [player]` | Check someone's conversion history |
| `/ecadmin setprice <buy\|sell> <amount>` | Override base prices |

### Permissions
| Permission | Who needs it |
|------------|--------------|
| `emeraldeconomy.use` | Everyone (default: true) |
| `emeraldeconomy.admin` | Admins only (default: op) |
| `emeraldeconomy.bypass.cooldown` | Skip the transaction cooldown |

---

## Placeholders (40 total)

Install PlaceholderAPI to use these anywhere.

### Prices & Economy
```
%emeraldeconomy_price_buy%          Current buy price (what server pays)
%emeraldeconomy_price_sell%         Current sell price (what players pay)
%emeraldeconomy_base_buy_price%     Base buy price (before adjustments)
%emeraldeconomy_base_sell_price%    Base sell price (before adjustments)
%emeraldeconomy_transaction_tax_rate%  Tax rate (e.g., "5.0%")
%emeraldeconomy_depletion_factor%   Resource depletion (e.g., "95.00%")
```

### Player Stats
```
%emeraldeconomy_player_emeralds%       Emeralds in inventory
%emeraldeconomy_total_converted%       Lifetime conversions
%emeraldeconomy_all_sell_value%        Value of all emeralds in inventory
%emeraldeconomy_inventory_buy_space%   How many emeralds fit in inventory
```

### Transaction Previews
```
%emeraldeconomy_sell_1_value%       What you'll get for selling 1 (after tax)
%emeraldeconomy_sell_1_tax%         Tax deducted from selling 1
%emeraldeconomy_sell_64_value%      What you'll get for selling 64 (after tax)
%emeraldeconomy_sell_64_tax%        Tax deducted from selling 64
%emeraldeconomy_sell_all_value%     What you'll get for selling everything
%emeraldeconomy_sell_all_tax%       Tax deducted from selling everything
%emeraldeconomy_sell_all_count%     Total emeralds in inventory

%emeraldeconomy_buy_1_cost%         What you'll pay for 1 emerald (with tax)
%emeraldeconomy_buy_1_tax%          Tax added to buying 1
%emeraldeconomy_buy_64_cost%        What you'll pay for 64 (with tax)
%emeraldeconomy_buy_64_tax%         Tax added to buying 64
```

### Stack Values
```
%emeraldeconomy_stack_sell_value%   Value of 64 emeralds (gross, before tax)
%emeraldeconomy_stack_buy_value%    Cost of 64 emeralds (base, before tax)
```

### Fill Inventory
```
%emeraldeconomy_inventory_buy_value%           Base cost to fill inventory
%emeraldeconomy_inventory_buy_value_with_tax%  Total cost to fill (with tax)
%emeraldeconomy_inventory_buy_tax%             Tax for filling inventory
```

### Affordability Checks (Localized)
```
%emeraldeconomy_can_afford_1_emerald%        "✔ Yes" or "✘ No" (English)
%emeraldeconomy_can_afford_64_emeralds%      "✔ Mampu" or "✘ Tidak Mampu" (Indo)
%emeraldeconomy_can_afford_inventory_fill%   Shows if player can afford to fill
%emeraldeconomy_has_1_emerald%               Does player have 1 emerald?
%emeraldeconomy_has_64_emeralds%             Does player have 64 emeralds?
%emeraldeconomy_has_any_emeralds%            Does player have any emeralds?
```

### Server Stats
```
%emeraldeconomy_total_emeralds_converted%  Emeralds traded globally
%emeraldeconomy_recent_volume%             Recent transaction volume
```

### Currency
```
%emeraldeconomy_currency_name%    e.g., "Dollar"
%emeraldeconomy_currency_symbol%  e.g., "$"
```

---

## Actions (DeluxeMenus-style)

The menu.yml supports these action types:

### Basic Actions
```yaml
actions:
  - "[close]"                              # Close the menu
  - "[refresh]"                            # Update placeholders
  - "[message]&aTransaction complete!"     # Send message
  - "[sound]ENTITY_PLAYER_LEVELUP:1.0:1.2" # Play sound (sound:volume:pitch)
  - "[console]give %player_name% diamond"  # Run console command
  - "[player]spawn"                        # Run command as player
```

### Converter-Specific Actions
```yaml
actions:
  - "[async]convert_sell:1"        # Sell 1 emerald
  - "[async]convert_sell:64"       # Sell 64 emeralds
  - "[async]convert_sell_all"      # Sell everything
  - "[async]convert_buy:1"         # Buy 1 emerald
  - "[async]convert_buy:64"        # Buy 64 emeralds
  - "[async]convert_buy_inventory" # Fill inventory (or convert_buy_all)
  - "[async]custom_amount_sell"    # Open chat input (sell)
  - "[async]custom_amount_buy"     # Open chat input (buy)
```

Chain them together:
```yaml
actions:
  - "[async]convert_sell_all"
  - "[sound]ENTITY_PLAYER_LEVELUP:1.0:1.0"
  - "[message]&aSold all emeralds!"
  - "[refresh]"
```

---

## Security & Anti-Exploit

The plugin has several layers of protection:

**Strict validation** - only genuine emeralds count (renamed items won't work)  
**Atomic transactions** - either the whole trade succeeds or none of it does  
**Per-player locks** - prevents race conditions  
**Rate limiting** - max 20 transactions per minute per player  
**Transaction cooldown** - 3 seconds between trades (configurable)  
**Price manipulation protection** - single large trades can't swing prices dramatically  

All transactions are logged to `plugins/EmeraldEconomy/transactions.log` with timestamps, player UUIDs, amounts, and unique transaction IDs.

---

## Performance

- Prices update every 5 seconds (configurable)
- Calculations run async - won't lag your server
- Thread-safe throughout
- Minimal overhead (tested with 100+ concurrent users)
- Works fine on Bedrock Edition via GeyserMC

---

## API for Developers

Add to your pom.xml:

```xml
<dependency>
    <groupId>me.craftepxly</groupId>
    <artifactId>EmeraldEconomy</artifactId>
    <version>3.0.1</version>
    <scope>provided</scope>
</dependency>
```

Basic usage:

```java
EmeraldEconomy plugin = (EmeraldEconomy) Bukkit.getPluginManager()
    .getPlugin("EmeraldEconomy");

// Get prices
double buyPrice = plugin.getPriceManager().getBuyPrice();
double sellPrice = plugin.getPriceManager().getSellPrice();
double tax = plugin.getPriceManager().getTransactionTax(amount);

// Execute trade
TransactionResult result = plugin.getTransactionManager()
    .sellEmerald(player, 64);

if (result.isSuccess()) {
    // It worked
}

// Get player stats
int converted = plugin.getPlayerStatsStorage()
    .getTotalConverted(player.getUniqueId());
```

---

## Building from Source

```bash
git clone https://github.com/craftepxly/EmeraldEconomy.git
cd EmeraldEconomy
./gradlew clean build
# jar is in build/libs/
```

Requires Java 21 and Gradle 9.0+

---

## Troubleshooting

**"Economy system unavailable"**  
→ Install Vault and an economy plugin (EssentialsX, CMI, etc)

**Menu won't open**  
→ Check console for errors  
→ Verify player has `emeraldeconomy.use` permission  
→ Try `/ecadmin reload`

**Prices not changing**  
→ Make sure `dynamic_pricing.enabled: true` in config  
→ Prices need actual transactions to adjust  
→ Check console for errors

**"Insufficient balance" but player has money**  
→ Tax is added to the cost - they need more than the base price  
→ Use the placeholders to show full cost with tax

---

## Support & Contributing

- **Bug reports:** Open an issue on GitHub
- **Feature requests:** Same - open an issue
- **Pull requests:** Always welcome

This plugin is actively maintained and used on Stresmen SMP S2.

---

## Credits

**Author:** CraftePxly  
**Built with:** Paper API, Vault, PlaceholderAPI

Special thanks to the Paper team, Vault developers, and the PlaceholderAPI team for making this possible.

---

## License

MIT License - do whatever you want with it, just don't blame me if something breaks.

---

*Currently running on [Stresmen SMP S2](https://youtube.com/@stresmen)*
