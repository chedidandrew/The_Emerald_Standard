# Banker GUI and village banks

## Player experience

The Emerald Standard is designed as a natural village service rather than a command console.

1. Follow the one-time first-join hint and discover or visit an Overworld village.
2. Locate the Village Bank and Exchange or a villager named Banker.
3. Right-click a Banker or any Exchange Desk. A lectern in an upgraded legacy bank remains a valid access point only at its persisted bank location.
4. On Account, choose an amount and deposit physical emeralds into Bank Cash.
5. Use the seven-page graphical dashboard. The first successful Banker visit explains the distinction between safe deposits, locked CDs, and investments that can lose value.

No command is required for ordinary banking, investing, lending, exchange, or Prosperity Fund gameplay.

## Dashboard pages

The dashboard preserves a collision-tested 320x230 logical layout but responsively grows or shrinks within safe bounds for the current window. Custom labels keep Minecraft's native glyph size to match button and EditBox text while their anchors, clipping widths, panels, and hitboxes scale. Supporting explanations wrap in compact hover tooltips. Hovering an Account balance shows its exact two-decimal value, while mature/early CD closure and disabled Fund-purpose controls explain their current state. Every amount-bearing page identifies the applied amount, and transactional controls explain their source, destination, and projected balances.

### Account

Shows net worth, Bank Cash, Savings, invested value, physical inventory emeralds, current economic day, market regime, total external contributions, realized and unrealized performance, and personal net-worth history. Quick actions explicitly deposit Inventory items into Bank Cash, withdraw Bank Cash to Inventory, open Banking transfers, or recover an interrupted journaled inventory transaction.

### Market

The previous/next carousel shows one of the nine investments at a time. The selected investment controls the current price, chart, sector, risk, shares held, holding value, portfolio allocation, average purchase price, total cost basis, market bulletin, and Buy/Sell actions. Players can buy a selected emerald amount, sell 25 percent, or sell all. Sell-all requires confirmation. The 0.25 percent per-side trading spread remains in effect.

### Banking

Uses separate Transfers, CDs, and Villager Loans subviews. Transfers show Inventory, Bank Cash, and Savings together and provide four unambiguous routes: Inventory -> Bank Cash, Bank Cash -> Inventory, Bank Cash -> Savings, and Savings -> Bank Cash. A persistent note explains that Savings must pass through Bank Cash before withdrawal or investment.

The CD and Villager Loans views show the spendable Bank Cash source, total product value, position count, selected term/position, selected amount, and projected balances after opening a position. A player can hold up to eight positions of each type; selectors choose the exact CD to close or resolved loan to collect. The Close CD tooltip distinguishes a penalty-bearing early close from a penalty-free mature close. Lending visibly warns that principal can be lost while also stating that no debt can be created. Funding lending and closing a CD require confirmation.

### Exchange

Cycles through 18 supported valuable resource forms, shows current inventory count, dynamic emerald quote, derived quote history, selected amount, proceeds, remaining items, and resulting Bank Cash. Variant histories use the same ore/block/crafting formula as their live quote, so ingots, blocks, and ores no longer depend on nonexistent variant-specific save keys.

### Village

Shows the local settlement connected to the current bank access point:

- Lifecycle and development tier
- Productive population and housing
- Prosperity and safety
- Food, materials, and treasury
- Farming, mining, and trade output
- Current development project and visual backlog
- A distinct Planning or physical Building percentage; villagers start placing blocks only after planning reaches 100 percent
- Simulation/visual-progression mode and local market-impact summary
- Restoration progress, or the latest local incident cause and age when restoration is not active

### Fund

Shows the associated settlement's spendable balance, protected endowment principal, emergency reserve, lifetime receipts, and the current player's lifetime support and non-financial donor title. Players choose among Direct Grant, Endowment, and Project Sponsorship. Grants and endowments may target General, Housing, Food, Infrastructure, Security, Trade, or Restoration when targeted donations are enabled. A restoration Direct Grant is fixed to Restoration, while a sponsorship follows the displayed active economically unfinished project and derives its purpose from that project's type. The disabled purpose control explains whether targeting is unavailable, restoration-fixed, or project-fixed.

The contribution amount uses the same exact typed Apply, Cancel, Enter, and `All` controls as other money flows. The applied amount is server-owned, bounded against live Bank Cash, and uses a separate packet range from ordinary transactions. The contribution button requires a second matching click within the server confirmation window. Contributions are irreversible gifts to the settlement; they are not player loans or investments and never create debt.

### Activity

Shows lifetime bank inflow and withdrawals plus a scrollable, newest-first view of the player's complete retained transaction ledger. Five entries fit at once; the mouse wheel and visible newer/older controls securely request another bounded window from the server. A cycling filter selects All, Cash & Transfers, Investments, Bank Products, Exchange, or Village Fund before the server calculates totals and paging. The position indicator covers empty, partial, and full 256-entry ledgers. Deposits, withdrawals and refunds, cash/savings transfers, buy/sell activity, CDs, lending, commodity exchange, and Prosperity Fund support have distinct labels. Buy and sell entries include their asset ticker, while new exchange entries retain the resource and item count.

## Exact transaction amounts

Banking, investing, exchange, and Fund actions use one typed whole-emerald amount from `1` through `1,000,000`, plus Apply, Cancel, Enter, and `All`. Unapplied or invalid edits visibly block amount-dependent actions, and the server independently validates every applied value and re-caps it against live balances or inventory. Overview, Market, and Exchange charts cycle through 30-day, 90-day, one-year, and all-history ranges; retained market, commodity, and personal histories are bounded at five economic years.

## Village Bank and Exchange generation

The first time a player loads an Overworld village, the server searches deterministic positions for a natural, dry, loaded, and unoccupied bank plot with no more than two blocks of surface variation. The player is only the discovery trigger: once a settlement record exists, its persisted stable center drives Bank identity and site selection. Mud and thin snow are rejected as structural support. The floor sits above the highest sampled surface; deterministic biome-matched foundations bridge only shallow air gaps and stop when they meet sound natural ground. The footprint and margin must contain no solid blocks or block entities. The bank uses plains, desert, savanna, snowy, or taiga palettes. Its civic blueprint includes a masonry foundation and trim, a continuous stepped roof with sealed gables, a sheltered portico and public bell, a real door/transom/step, tall green windows, one Exchange Desk, chest and chiseled-bookshelf teller cabinetry, an Ender Chest, a crafting table, layered lighting, and a persistent Banker. The Exchange Desk is the only villager job-site authored by a new bank, so its counter cannot accidentally create a row of fishermen. Legacy barrel counter frames remain recognizable for old saves but are never added to new banks.

Before writing, the server preflights every proposed bank block through `VillageDevelopmentProtection.register(PlacementGuard)`. A veto or thrown guard exception denies the site. Each accepted `setBlock` is then compared with the resulting world state. Any failed build rolls back matching authored block types, including panes or fences changed by neighbor updates, and is not recorded as generated. If low view distance leaves the candidate scan incomplete because chunks are unloaded, the temporary fallback Banker is also left unmarked so a later loaded scan can retry the structure. Claim and protection mods must register a guard for their rules to participate.

Generation is discovery-based rather than injected into vanilla jigsaw pools. This supports existing worlds, keeps Fabric and NeoForge aligned, and reduces conflict with world-generation mods.

## Banker safety and identity

The mod registers a real Banker villager profession and a craftable, directional Exchange Desk block and point of interest on both Fabric and NeoForge. The desk uses a layered furniture model with a matching 13.5/16-block selection and collision height, faces the player when placed, and appears under Creative inventory's Functional Blocks tab and search. Unemployed villagers can naturally claim it, and both a naturally employed Banker and a player-placed Exchange Desk open the dashboard. When they do not carry a generated-bank identity, the Village and Fund pages use the nearest managed settlement in the same dimension within 160 blocks; the player's global financial pages remain available when no managed settlement is nearby. Legacy tagged Bankers using the librarian profession are migrated when safely eligible, while lectern counters remain interactive only at persisted Overworld legacy-bank locations.

Fallback Banker selection uses only an untouched adult villager with no profession, XP, custom name, or established trades, or an equally untouched villager that naturally claimed an Exchange Desk. Otherwise the mod spawns a new Banker. Existing player-developed villagers are never repurposed. Managed Bankers use vanilla career-lock semantics and remember the generated Exchange Desk as their job site, preventing the Banker appearance from reverting immediately or after an ordinary work cycle.

Bankers are associated with persisted bank identity and the surrounding stable settlement record. Existing grid keys and bank anchors are honored for upgraded worlds; when a second stable village occupies an already-owned legacy grid, it receives a deterministic village-derived key rather than sharing that bank. The same scoped key selects Banker replacement eligibility, Village-page data, and the target of Prosperity Fund and restoration contributions. Banker replacement is suppressed while the settlement is Extinct or Abandoned. Player financial accounts are global, so losing a Banker or village never destroys player wealth.

Older saves do not contain per-block ownership for generated banks. A narrowly signature-guarded retrofit recognizes the current 13x11 bank only when its persisted anchor, workstation, counter frame, roof mount, and hanging roof lantern all match; it can then fill only exact air cells beneath the two porch columns and bell accent. It never replaces a block or rebuilds furnishings. If an old bank counter is missing, the mod does not rebuild over the original site or guess which nearby blocks belong to it; it preserves account access by restoring an eligible fallback Banker when the village lifecycle allows it.

## Village Prosperity structures

The ten physical prosperity projects are Cottage, House, Village Inn, Warehouse, Mine Entrance, Market Square, Smithy, Granary, Guard Post, and Exchange Hall. They are not spawned instantly after offline catch-up. The GUI first labels economic progress as Planning; after that reaches 100 percent it switches to Building, enters the bounded visual queue, and places only a small number of blocks while a player is nearby.

Candidate sites must be loaded, naturally surfaced, dry, clear, and limited to two blocks of height variation. Project floors are placed above the highest surface rather than replacing terrain, while deterministic foundation cells bridge shallow drops and stop at sound natural ground. Solid or protected placements defer construction with persistent exponential backoff instead of being overwritten. A verified obstruction can relocate an unstarted project; an unloaded boundary retains the reservation so a possibly written prefix is not orphaned. A partial deterministic template retains its exact bounds and resumes in place. Housing and production effects remain inactive in visual mode until the authored structure is physically verified complete.

A low-frequency integrity pass audits one completed structure at a time. If an authored block is missing or replaced, its economic benefits are suspended immediately. Completed structures do not regenerate missing blocks, which prevents their chests, workstations, or decorations from becoming renewable drops; restoring the authored block in-world lets a later audit reactivate the project. Safe append-only template upgrades still use the ordinary guarded construction queue, and solid non-terrain blocks, block entities, or unloaded chunks are never overwritten or force-loaded. Trails are deliberately non-authoritative: safe dirt-path, gravel, and coarse-dirt cells connect the entrance to the nearest earlier completed-project branch or a stable village-edge hub, adopt an existing TES-style route, and skip protected, occupied, or non-terrain cells without suspending the building. A claim guard is required to distinguish player-placed natural-ground blocks from the same block types generated by Minecraft.

Cottages, Houses, and Inns contain real beds and clear entry paths, and Warehouses use chests rather than barrels. Each project now has a distinct silhouette and functional interior: connected framed eaves and covered entries use the local village palette, Market Squares have lit stalls, Guard Posts have battlements, mines have timber framing, and production or civic buildings contain a small set of theme-appropriate utility and job-site blocks. A deterministic village/project/type seed selects one of three save-stable exterior/interior presets. Town tiers append another lamp and storage/library detail; city tiers append a more formal entrance plus type-specific furnishing, and a later tier decline never removes an installed layer. Those vanilla workstations give residents ordinary reasons to visit the new district; they do not install custom AI or change the simulation's economic authority. During active placement, at most two suitable nearby residents may receive an occasional one-shot, low-speed navigation request toward a safe exterior waypoint, then look, swing, and emit a small particle. This theatre remains bounded.

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
