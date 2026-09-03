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
- Latest local incident cause and age
- Village support or restoration action when available

Village support is a contribution to local development. It is not a loan to the player and never creates debt.

## Amount presets

The interface uses the same one-click presets throughout: `1`, `5`, `10`, `32`, `64`, and `All`.

## Village Bank and Exchange generation

The first time a player loads an Overworld village, the server searches deterministic positions for a fully flat, natural, dry, loaded, and unoccupied bank plot. The player is only the discovery trigger: once a settlement record exists, its persisted stable center drives Bank identity and site selection. Mud and thin snow are rejected as structural support. The floor sits in air above the existing surface; the footprint and margin must contain no solid blocks or block entities. The bank uses plains, desert, savanna, snowy, or taiga palettes and includes a lectern counter, barrel storage, bookshelves, lighting, and a persistent Banker.

Before writing, the server preflights every proposed bank block through `VillageDevelopmentProtection.register(PlacementGuard)`. A veto or thrown guard exception denies the site. Each accepted `setBlock` is then compared with the resulting world state. Any failed build rolls back matching authored block types, including panes or fences changed by neighbor updates, and is not recorded as generated. If low view distance leaves the candidate scan incomplete because chunks are unloaded, the temporary fallback Banker is also left unmarked so a later loaded scan can retry the structure. Claim and protection mods must register a guard for their rules to participate.

Generation is discovery-based rather than injected into vanilla jigsaw pools. This supports existing worlds, keeps Fabric and NeoForge aligned, and reduces conflict with world-generation mods.

## Banker safety and identity

Fallback Banker selection uses only an untouched adult villager with no profession, XP, custom name, or established trades. Otherwise the mod spawns a new Banker. Existing player-developed villagers are never repurposed.

Bankers are associated with persisted bank identity and the surrounding stable settlement record. Existing grid keys and bank anchors are honored for upgraded worlds; when a second stable village occupies an already-owned legacy grid, it receives a deterministic village-derived key rather than sharing that bank. The same scoped key selects Banker replacement eligibility, Village-page data, and the target of support/restoration actions. Banker replacement is suppressed while the settlement is Extinct or Abandoned. Player financial accounts are global, so losing a Banker or village never destroys player wealth.

Older saves do not contain per-block ownership for generated banks. If an old bank counter is missing, beta.3 does not rebuild over the original site or guess which nearby blocks belong to the mod; it preserves account access by restoring an eligible fallback Banker when the village lifecycle allows it.

## Village Prosperity structures

The first physical prosperity projects are Cottage, Warehouse, and Mine Entrance. They are not spawned instantly after offline catch-up. Approved projects enter a bounded queue and place only a small number of blocks while a player is nearby.

Candidate sites must be loaded, flat, naturally surfaced, dry, and clear. Project floors are placed above terrain rather than replacing it. Solid or protected placements defer construction with persistent exponential backoff instead of being overwritten. A verified obstruction can relocate an unstarted project; an unloaded boundary retains the reservation so a possibly written prefix is not orphaned. A partial deterministic template retains its exact bounds and resumes in place. Cottages contain real beds, and Warehouses use chests rather than barrels.

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
