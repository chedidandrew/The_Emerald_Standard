# Banker GUI and village banks

## Player experience

The Emerald Standard is designed as a natural village service rather than a command console.

1. Follow the one-time first-join hint and discover or visit an Overworld village.
2. Locate the Village Bank and Exchange or a villager named Banker.
3. Right-click a Banker or any Exchange Desk. A lectern in an upgraded legacy bank remains a valid access point only at its persisted bank location.
4. On Overview, choose an amount and deposit physical emeralds into bank cash.
5. Use the seven-page graphical dashboard. The first successful Banker visit explains the distinction between safe deposits, locked CDs, and investments that can lose value.

No command is required for ordinary banking, investing, lending, exchange, or Prosperity Fund gameplay.

## Dashboard pages

### Overview

Shows net worth, bank cash, savings, invested value, physical emeralds, current economic day, market regime, market news, total external contributions, realized and unrealized performance, and personal net-worth history. Quick actions include Deposit, Withdraw, Save, and Recover.

### Market

The nine investments expose current price, chart history, sector, risk, shares held, holding value, portfolio allocation, average purchase price, and total cost basis. Players can buy a selected emerald amount, sell 25 percent, or sell all. Sell-all requires confirmation. The 0.25 percent per-side trading spread remains in effect.

### Banking

Shows savings, locked-rate 30/90/180/365-day CDs, and player-funded villager business lending. A player can hold up to eight CDs and eight lending positions at once; position selectors choose the exact CD to close or resolved lending position to collect. Lending visibly warns that principal can be lost while also stating that no debt can be created. Funding lending and closing a CD require confirmation.

### Exchange

Cycles through supported valuable resources, shows current inventory count, dynamic emerald quote, and commodity history, and converts the selected amount into bank cash.

### Village

Shows the local settlement connected to the current bank access point:

- Lifecycle and development tier
- Productive population and housing
- Prosperity and safety
- Food, materials, and treasury
- Farming, mining, and trade output
- Current development project and visual backlog
- Latest local incident cause and age
- Current production output and restoration state

### Fund

Shows the associated settlement's spendable balance, protected endowment principal, emergency reserve, lifetime receipts, and the current player's lifetime support and non-financial donor title. Players choose among Direct Grant, Endowment, and Project Sponsorship. Grants and endowments may target General, Housing, Food, Infrastructure, Security, Trade, or Restoration. A sponsorship instead follows the displayed active economically unfinished project and derives its accounting purpose from that project's type; the purpose selector is disabled while sponsorship is selected.

The contribution amount is assembled in a server-owned draft with `+1`, `+5`, `+10`, `+25`, `+100`, `All`, and `Clear`. The contribution button requires a second matching click within the server confirmation window. Contributions are irreversible gifts to the settlement; they are not player loans or investments and never create debt.

### Log

Shows lifetime deposits and withdrawals plus the five newest entries from the player's persistent transaction ledger, newest first. Buy and sell entries include their asset ticker. The complete bounded ledger remains persisted even though the compact dashboard intentionally shows only the most recent entries.

## Amount presets

Ordinary banking, investing, and exchange actions use `1`, `5`, `10`, `32`, `64`, and `All`. Fund contributions use the additive server draft described above. Overview, Market, and Exchange charts cycle through 30-day, 90-day, one-year, and all-history ranges; retained market, commodity, and personal histories are bounded at five economic years.

## Village Bank and Exchange generation

The first time a player loads an Overworld village, the server searches deterministic positions for a fully flat, natural, dry, loaded, and unoccupied bank plot. The player is only the discovery trigger: once a settlement record exists, its persisted stable center drives Bank identity and site selection. Mud and thin snow are rejected as structural support. The floor sits in air above the existing surface; the footprint and margin must contain no solid blocks or block entities. The bank uses plains, desert, savanna, snowy, or taiga palettes and includes an Exchange Desk counter, barrel storage, bookshelves, lighting, and a persistent Banker.

Before writing, the server preflights every proposed bank block through `VillageDevelopmentProtection.register(PlacementGuard)`. A veto or thrown guard exception denies the site. Each accepted `setBlock` is then compared with the resulting world state. Any failed build rolls back matching authored block types, including panes or fences changed by neighbor updates, and is not recorded as generated. If low view distance leaves the candidate scan incomplete because chunks are unloaded, the temporary fallback Banker is also left unmarked so a later loaded scan can retry the structure. Claim and protection mods must register a guard for their rules to participate.

Generation is discovery-based rather than injected into vanilla jigsaw pools. This supports existing worlds, keeps Fabric and NeoForge aligned, and reduces conflict with world-generation mods.

## Banker safety and identity

The mod registers a real Banker villager profession and a craftable, placeable Exchange Desk block and point of interest on both Fabric and NeoForge. Unemployed villagers can naturally claim the desk, and both a naturally employed Banker and a player-placed Exchange Desk open the dashboard. When they do not carry a generated-bank identity, the Village and Fund pages use the nearest managed settlement in the same dimension within 160 blocks; the player's global financial pages remain available when no managed settlement is nearby. Legacy tagged Bankers using the librarian profession are migrated when safely eligible, while lectern counters remain interactive only at persisted Overworld legacy-bank locations.

Fallback Banker selection uses only an untouched adult villager with no profession, XP, custom name, or established trades, or an equally untouched villager that naturally claimed an Exchange Desk. Otherwise the mod spawns a new Banker. Existing player-developed villagers are never repurposed.

Bankers are associated with persisted bank identity and the surrounding stable settlement record. Existing grid keys and bank anchors are honored for upgraded worlds; when a second stable village occupies an already-owned legacy grid, it receives a deterministic village-derived key rather than sharing that bank. The same scoped key selects Banker replacement eligibility, Village-page data, and the target of Prosperity Fund and restoration contributions. Banker replacement is suppressed while the settlement is Extinct or Abandoned. Player financial accounts are global, so losing a Banker or village never destroys player wealth.

Older saves do not contain per-block ownership for generated banks. If an old bank counter is missing, the mod does not rebuild over the original site or guess which nearby blocks belong to it; it preserves account access by restoring an eligible fallback Banker when the village lifecycle allows it.

## Village Prosperity structures

The ten physical prosperity projects are Cottage, House, Village Inn, Warehouse, Mine Entrance, Market Square, Smithy, Granary, Guard Post, and Exchange Hall. They are not spawned instantly after offline catch-up. Approved projects enter a bounded queue and place only a small number of blocks while a player is nearby.

Candidate sites must be loaded, flat, naturally surfaced, dry, and clear. Project floors are placed above terrain rather than replacing it. Solid or protected placements defer construction with persistent exponential backoff instead of being overwritten. A verified obstruction can relocate an unstarted project; an unloaded boundary retains the reservation so a possibly written prefix is not orphaned. A partial deterministic template retains its exact bounds and resumes in place. Housing and production effects remain inactive in visual mode until the authored structure is physically verified complete.

A low-frequency integrity pass audits one completed structure at a time. If an authored block is missing or replaced, its economic benefits are suspended immediately and its verified prefix re-enters the ordinary construction queue. Repair can fill only safe air or replaceable positions through the same chunk, collision, and protection checks; solid player blocks, block entities, and unloaded chunks are never overwritten or force-loaded.

Cottages, Houses, and Inns contain real beds, and Warehouses use chests rather than barrels. During active placement, at most two suitable nearby residents may receive an occasional one-shot, low-speed navigation request toward a safe exterior waypoint, then look, swing, and emit a small particle. This theatre is bounded and never becomes persistent custom AI or economic authority.

## Interaction safety

The server remains authoritative for all actions. Sell-all, CD closure, lending funding, and Fund contributions require the same risky action to be submitted twice within a short server-owned confirmation window. Changing the selection or allowing the window to expire cancels it; a client cannot bypass it by locally changing a button label. The menu closes when the player dies, is removed, or leaves the allowed interaction range. A configurable cooldown prevents duplicate button and packet spam.

## Configuration

The world `data/the_emerald_standard-config.properties` file controls onboarding, bank generation, transaction throttling, and Village Prosperity. See the [complete configuration reference](CONFIGURATION.md) for every setting, bound, and dependency. The four key prosperity switches are:

```properties
village_prosperity.simulation_enabled=true
village_prosperity.visual_progression_enabled=true
village_prosperity.market_integration_enabled=true
village_prosperity.automatic_recovery_enabled=true
village_prosperity.donations_enabled=true
village_prosperity.endowments_enabled=true
village_prosperity.project_sponsorship_enabled=true
village_prosperity.targeted_donations_enabled=true
village_prosperity.donor_recognition_enabled=true
village_prosperity.endowment_annual_payout_bps=400
village_prosperity.minimum_emergency_reserve_percent=20
village_prosperity.max_monthly_treasury_spending=24
```

The Fund settings independently enable contribution features and recognition, choose the protected endowment's annual payout, reserve a fraction of ordinary grants for emergencies, and cap how quickly village-owned balances enter the simulated local economy.

Administrators can inspect or reload configuration with `/emerald config show` and `/emerald config reload`.
