# Banker GUI and village banks

## Player experience

The Emerald Standard is designed to feel like a natural village service rather than a command console.

1. Discover or visit a village.
2. Locate the small Village Bank and Exchange or a villager named Banker.
3. Right-click the Banker.
4. Use the four-page graphical dashboard.

No command is required for ordinary deposits, withdrawals, saving, investing, lending, or commodity exchange.

## Dashboard pages

### Overview

The Overview page presents only the information a casual player needs first:

- Total net worth
- Bank cash
- Savings balance
- Invested value
- Physical emeralds in the inventory
- Current economic day and market regime
- A chart for the currently selected investment
- Direct Deposit, Withdraw, Save, and Recover buttons

### Market

The Market page lists the nine available investments as short ticker buttons. Selecting one updates:

- Full investment name
- Current price
- 180-day chart
- Change over the visible chart period
- Risk label
- Shares held
- Current holding value

Players buy a chosen emerald amount or sell 25 percent or all of a holding. The existing 0.25 percent per-side spread remains in effect.

### Banking

The Banking page separates lower-risk and higher-risk choices:

- Savings with a visible current annual rate
- 30, 90, 180, and 365-day locked-rate CDs
- 30, 90, 180, and 365-day villager business lending

The interface explicitly states that lending can lose principal but can never create player debt.

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

The first time a player loads a village region, the server searches several positions around the village for a safe, relatively flat, dry, and unoccupied plot.

When a plot is found, the mod constructs an 11 by 9 vanilla-block bank containing:

- Stone-brick floor and foundation
- Oak walls and dark-oak roof
- Green stained-glass windows
- A lectern and barrel banking counter
- Bookshelves and lanterns
- A persistent Banker villager behind the counter

The structure is intentionally compact and uses no valuable emerald-block decoration that could be farmed.

Generation is discovery-based instead of injected into vanilla jigsaw pools. This gives three practical benefits:

- Existing worlds receive village banks.
- Fabric and NeoForge use the same placement rules.
- The mod avoids broad village-pool compatibility conflicts with other world-generation mods.

Generated 256-block village regions are stored in the economy save so they are not processed repeatedly.

## Natural Banker fallback

If terrain or nearby structures leave no safe plot, the mod designates the nearest adult villager as the local Banker. If no adult villager is available, it spawns a persistent Banker at a safe village surface position.

A generated bank whose Banker is later lost will receive a replacement when a player returns to the village region.

## Administrative access

The `/emerald` command tree requires permission level 2. `/emerald open` is retained as an administrator and development shortcut for opening the dashboard without locating a Banker.
