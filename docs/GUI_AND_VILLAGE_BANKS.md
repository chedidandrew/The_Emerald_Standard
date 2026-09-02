# Banker GUI and village banks

## Player experience

The Emerald Standard is designed to feel like a natural village service rather than a command console.

1. Discover or visit a village.
2. Locate the Village Bank and Exchange or a villager named Banker.
3. Right-click the Banker or the lectern at the bank counter.
4. Use the four-page graphical dashboard.

No command is required for ordinary deposits, withdrawals, saving, investing, lending, or commodity exchange.

## Dashboard pages

### Overview

The Overview page presents:

- Total net worth
- Bank cash
- Savings balance
- Invested value
- Physical emeralds in the inventory
- Current economic day and market regime
- Recent market-event headline or current regime bulletin
- A chart for the selected investment
- Direct Deposit, Withdraw, Save, and Recover actions

### Market

Selecting one of the nine investments updates:

- Full investment name
- Current price
- Recent chart
- Change over the visible chart period
- Sector
- Risk label
- Shares held
- Current holding value

Players buy a selected emerald amount or sell 25 percent or all of a holding. Sell-all requires confirmation. Charts use a minimum scale range so small moves are not visually exaggerated, show high and low values, and expose sampled prices on hover. The 0.25 percent per-side spread remains in effect.

### Banking

The Banking page separates lower-risk and higher-risk choices:

- Savings with a visible current annual rate
- 30, 90, 180, and 365-day locked-rate CDs
- 30, 90, 180, and 365-day villager business lending

The interface states that lending can lose principal but can never create player debt. Tooltips show the offered rate and estimated opening default risk. Funding lending requires confirmation. Closing an immature CD also requires confirmation and explains the one percent principal penalty and forfeited interest.

### Exchange

The Exchange page cycles through supported valuable resources, shows the inventory count and current per-item emerald quote, and converts the selected amount into bank cash.

## Amount presets

Every page uses the same transaction presets:

- 1
- 5
- 10
- 32
- 64
- All

This avoids text entry and keeps the interface controller-friendly and easy to learn.

## Village Bank and Exchange generation

The first time a player loads a village region, the server searches deterministic positions around the village for a safe, relatively flat, dry, loaded, and unoccupied plot.

When a plot is found, the mod constructs an 11 by 9 bank containing:

- A stable floor and foundation
- Village-biome wall, roof, corner, and fence materials
- Green stained-glass windows
- A lectern and barrel banking counter
- Bookshelves and lanterns
- A persistent Banker behind the counter

The palette follows plains, desert, savanna, snowy, and taiga village biome tags. The structure deliberately avoids valuable emerald-block decoration that could be farmed.

Generation is discovery-based instead of injected into vanilla jigsaw pools. This allows existing worlds to receive banks, keeps Fabric and NeoForge behavior aligned, and avoids broad compatibility conflicts with village world-generation mods.

Generated village regions and exact Banker counter anchors are stored in the economy save so they are not processed repeatedly. The default region size is 256 blocks and can be changed in the world configuration.

## Banker safety and identity

Each Banker receives a durable region identity tag. Nearby banks therefore cannot accidentally share or replace the same villager.

If a generated Banker is lost, the bank creates a replacement at the persisted service point. An older unscoped alpha Banker may be adopted once for migration.

When no bank plot is available, the fallback follows this order:

1. Use an untouched adult villager with no profession, no XP, no custom name, and no prior Banker identity.
2. Otherwise spawn a new persistent Banker at a safe village surface.
3. Never repurpose an established or player-customized villager.

Bankers currently use the vanilla librarian profession and lectern behavior while retaining their custom Banker name, persistent tag, home restriction, and banking interaction. This provides stable cross-loader behavior without overwriting player trades.

## Interaction safety

The server remains authoritative for every action. The menu closes when the player dies, is removed, or moves more than eight blocks from the Banker or bank access point. A short configurable cooldown prevents duplicate button and packet spam.

## Configuration

The world `data/the_emerald_standard-config.properties` file controls village-bank generation, scan interval, region size, Banker restriction radius, and transaction cooldown. Administrators can use `/emerald config show` and `/emerald config reload` to inspect or apply edits.

## Administrative access

The `/emerald` command tree requires permission level 2. `/emerald open` remains an administrator and development shortcut. It intentionally omits the Banker-distance check.
