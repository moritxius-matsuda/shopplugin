# ShopPlugin

A flexible shop system for Minecraft servers with advanced features like NBT support, potions, and automatic restock system.

[![Available on Modrinth](https://raw.githubusercontent.com/vLuckyyy/badges/main/avaiable-on-modrinth.svg)](https://modrinth.com/plugin/shopplugin)

[![Available on SpigotMC](https://raw.githubusercontent.com/vLuckyyy/badges/refs/heads/main/available-on-spigotmc.svg)](https://www.spigotmc.org/resources/shop-plugin.127331/) 

[![Available on BStats](https://raw.githubusercontent.com/vLuckyyy/badges/main/available-on-bstats.svg)](https://bstats.org/plugin/bukkit/Shop%20Plugin/26220)

## Features

- 🏪 **Flexible Shop Creation** - Create unlimited shops with individual names
- 🎨 **Customizable Display Names** - Shops can have beautiful, colored display names
- ⚡ **NBT Support** - Items with custom names and enchantments
- 🧪 **Potion System** - Easy creation of potion trades with all effects
- 🔄 **Automatic Restock** - Trades become available again after configurable time
- 🔐 **Permission System** - Control over shop access and management
- 💾 **Persistent Storage** - All shops are automatically saved
- 📋 **Tab Completion** - Full auto-completion for all commands

## Installation

1. Download the `shopplugin-*.jar` file
2. Place it in your `plugins/` folder
3. Restart the server
4. The plugin automatically creates the `plugins/ShopPlugin/shops/` folder

## Commands

### Basic Commands (for all players)
```
/shop <shopname>           - Opens a shop
/shop list                 - Shows all available shops
/shop info                 - Plugin information
```

### Admin Commands (requires `shopplugin.admin` permission)
```
/shop create <shopname> [display_name]                    - Creates a new shop
/shop addtrade <shop> <input> <amount> <output> <amount> [max_uses]  - Adds a trade
/shop addpotion <shop> <input> <amount> <potion_type> [max_uses]     - Adds a potion trade
/shop addtradeadvanced <shop> <input> <amount> <output> <amount> [max_uses] [input_name] [output_name] [input_enchants] [output_enchants] [input_potion] [output_potion]  - Advanced trade creation
/shop removetrade <shopname> <trade_index>                - Removes a trade
/shop rename <shopname> <new_display_name>                - Changes the display name
/shop trades <shopname>                                   - Shows all trades in a shop
/shop remove <shopname>                                   - Deletes a shop
/shop potions                                             - Shows all available potion types
/shop restock <shopname> [hours]                          - Sets restock interval
```

## Permissions

```yaml
shopplugin.admin: true     # Full access to all shop management functions
shopplugin.use.*: true     # Access to all shops
shopplugin.use.<shopname>: true  # Access to specific shop
```

## Examples

### Create a simple shop
```
/shop create general "§6General Shop"
```

### Add basic trade
```
/shop addtrade general DIAMOND 1 DIAMOND_SWORD 1 10
```

### Add potion trade
```
/shop addpotion general EMERALD 2 HEALING 5
/shop addpotion general GOLD_INGOT 1 HEALING:UPGRADED 3
/shop addpotion general DIAMOND 1 REGENERATION:EXTENDED 1
```

### Add advanced trade with NBT
```
/shop addtradeadvanced general DIAMOND 1 DIAMOND_SWORD 1 10 "&bMagical Diamond" "&cFire Sword" "" "FIRE_ASPECT:2,SHARPNESS:5"
```

### Set restock interval
```
/shop restock general 12    # Every 12 hours
/shop restock vip 6         # Every 6 hours
```

## Potion Types

### Available Base Potions:
- `HEALING` - Instant Health
- `HARMING` - Instant Damage
- `REGENERATION` - Regeneration
- `POISON` - Poison
- `STRENGTH` - Strength
- `WEAKNESS` - Weakness
- `SPEED` - Speed
- `SLOWNESS` - Slowness
- `JUMP` - Jump Boost
- `FIRE_RESISTANCE` - Fire Resistance
- `WATER_BREATHING` - Water Breathing
- `INVISIBILITY` - Invisibility
- `NIGHT_VISION` - Night Vision

### Modifiers:
- `:EXTENDED` - Longer duration
- `:UPGRADED` - Stronger effect (Level II)

### Examples:
- `HEALING:UPGRADED` - Healing II
- `SPEED:EXTENDED` - Speed with longer duration
- `REGENERATION:UPGRADED` - Regeneration II

## Enchantments

Enchantments are specified in the format `ENCHANTMENT:LEVEL`, multiple separated by comma:

```
"SHARPNESS:5,FIRE_ASPECT:2,UNBREAKING:3"
```

### Common Enchantments:
- `SHARPNESS` - Sharpness
- `FIRE_ASPECT` - Fire Aspect
- `UNBREAKING` - Unbreaking
- `EFFICIENCY` - Efficiency
- `FORTUNE` - Fortune
- `SILK_TOUCH` - Silk Touch
- `PROTECTION` - Protection
- `THORNS` - Thorns

## Restock System

The plugin features an automatic restock system:

- **Default Interval**: 24 hours
- **Customizable**: Individually adjustable per shop
- **Automatic**: Trades are checked and restocked when opening the shop
- **Persistent**: Restock times are saved and survive server restarts

### How it works:
1. When a trade reaches its maximum uses, it's marked as "sold out"
2. After the configured restock time expires, the trade becomes available again
3. The check occurs every time a player opens the shop

## Configuration

Shops are automatically saved in `plugins/ShopPlugin/shops/` as YAML files. Each shop has its own file with the following format:

```yaml
display-name: "§6My Shop"
restock-hours: 24
trades:
  - input-material: DIAMOND
    input-amount: 1
    output-material: DIAMOND_SWORD
    output-amount: 1
    max-uses: 10
    output-display-name: "&cFire Sword"
    output-enchantments: "FIRE_ASPECT:2,SHARPNESS:5"
restock-times:
  0: 1640995200000
```

## Troubleshooting

### Shop won't open
- Check the permission `shopplugin.use.<shopname>` or `shopplugin.use.*`
- Make sure the shop exists with `/shop list`

### Trades don't work
- Check material names (must be exact)
- Use `/shop trades <shopname>` to see all trades

### Potions have no effects
- Make sure you use `POTION`, `SPLASH_POTION` or `LINGERING_POTION` as material
- Check the potion type with `/shop potions`

## Support

For problems or questions:
1. Check server logs for error messages
2. Make sure all permissions are set correctly
3. Use `/shop info` to get plugin information
