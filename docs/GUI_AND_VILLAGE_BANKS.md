# Banker GUI and village banks

## Player experience

The Emerald Standard is designed as a natural village service rather than a command console.

1. Discover or visit a village.
2. Locate the Village Bank and Exchange or a villager named Banker.
3. Right-click the Banker or the lectern at the bank counter.
4. Use the five-page graphical dashboard.

No command is required for ordinary banking, investing, lending, exchange, or village-support gameplay.

## Dashboard pages

### Overview

Shows net worth, bank cash, savings, invested value, physical emeralds, current economic day, market regime, market news, and the selected investment chart. Quick actions include Deposit, Withdraw, Save, and Recover.

### Market

The nine investments expose current price, chart history, sector, risk, shares held, and holding value. Players can buy a selected emerald amount, sell 25 percent, or sell all. Sell-all requires confirmation. The 0.25 percent per-side trading spread remains in effect.

### Banking

Shows savings, locked-rate 30/90/180/365-day CDs, and player-funded villager business lending. Lending visibly warns that principal can be lost while also stating that no debt can be created. Funding lending and closing an immature CD require confirmation.

### Exchange

Cycles through supported valuable resources, shows current inventory count and dynamic emerald quote, and converts the selected amount into bank cash.

### Village

Shows the local settlement connected to the current bank access point:

- Lifecycle and development tier
- Productive population and housing
- Prosperity and safety
- Food, materials, and treasury
- Farming, mining, and trade output
- Current development project and visual backlog
- Recent incident information
- Village support or restoration action when available

Village support is a contribution to local development. It is not a loan to the player and never creates debt.

## Amount presets

The interface uses the same one-click presets throughout: `1`, `5`, `10`, `32`, `64`, and `All`.

## Village Bank and Exchange generation

The first time a player loads a village region, the server searches deterministic positions for a safe, relatively flat, dry, loaded, and unoccupied bank plot. The bank uses plains, desert, savanna, snowy, or taiga palettes and includes a lectern counter, storage, bookshelves, lighting, and a persistent Banker.

Generation is discovery-based rather than injected into vanilla jigsaw pools. This supports existing worlds, keeps Fabric and NeoForge aligned, and reduces conflict with world-generation mods.

## Banker safety and identity

Fallback Banker selection uses only an untouched adult villager with no profession, XP, custom name, or established trades. Otherwise the mod spawns a new Banker. Existing player-developed villagers are never repurposed.

Bankers are associated with persisted bank identity and the surrounding stable settlement record. Banker replacement is suppressed while the settlement is Extinct or Abandoned. Player financial accounts are global, so losing a Banker or village never destroys player wealth.

## Village Prosperity structures

The first physical prosperity projects are Cottage, Warehouse, and Mine Entrance. They are not spawned instantly after offline catch-up. Approved projects enter a bounded queue and place only a small number of blocks while a player is nearby.

Candidate sites must be loaded, flat, naturally surfaced, dry, and clear. Project floors are placed above terrain rather than replacing it. Solid or protected placements stop construction instead of being overwritten. Cottages contain real beds, and Warehouses use chests rather than barrels.

## Interaction safety

The server remains authoritative for all actions. The menu closes when the player dies, is removed, or leaves the allowed interaction range. A configurable cooldown prevents duplicate button and packet spam.

## Configuration

The world `data/the_emerald_standard-config.properties` file controls bank generation and Village Prosperity. The four key prosperity switches are:

```properties
village_prosperity.simulation_enabled=true
village_prosperity.visual_progression_enabled=true
village_prosperity.market_integration_enabled=true
village_prosperity.automatic_recovery_enabled=true
```

Administrators can inspect or reload configuration with `/emerald config show` and `/emerald config reload`.
