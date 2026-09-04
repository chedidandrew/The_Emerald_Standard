# The Emerald Standard

[![Build, test, and launch](https://github.com/chedidandrew/The_Emerald_Standard/actions/workflows/build.yml/badge.svg)](https://github.com/chedidandrew/The_Emerald_Standard/actions/workflows/build.yml)

A lightweight villager banking, investing, commodity-exchange, and settlement-economy mod for **Minecraft 26.2**, with Fabric and NeoForge builds.

> Current status: `0.4.0-beta.2` candidate (unreleased). Normal gameplay is centered on Banker villagers, Village Banks, the graphical bank dashboard, and the optional Village Prosperity System. Commands are reserved for administrators and diagnostics.

## Core rule

Players provide emerald capital to the villager economy. **Players can never borrow emeralds, hold a negative balance, or enter debt.** Villager business lending can lose some or all of the amount voluntarily invested, but it can never create an additional obligation.

## Banking and investing

When a player discovers a loaded Overworld village, The Emerald Standard can establish a compact **Village Bank and Exchange** nearby. The bank uses a biome-aware palette, includes a dedicated Exchange Desk workstation, and contains a persistent villager with the registered Banker profession. New banks require flat natural ground and completely empty loaded space, place their floor above the existing surface, preflight every block through the cooperative protection API, and verify every resulting block state before recording generation. If no site is safe, the mod uses an untouched unemployed adult villager or spawns a new Banker. Established villagers with professions, XP, trades, custom names, or other player investment are never repurposed.

New settlements receive stable per-village bank identities even when two villages share the same legacy 256-block region. Existing bank-region associations and anchors remain authoritative for upgraded worlds. Village Bank generation is intentionally Overworld-only in this beta; Village Prosperity records may exist in other dimensions.

Right-click a Banker or any Exchange Desk to open the dashboard. Exchange Desks are craftable and placeable; an unscoped desk or naturally employed Banker uses the nearest managed settlement in the same dimension within 160 blocks for its Village and Fund pages, while the player's financial account remains globally available. Lectern counters from older worlds remain valid only at their persisted Overworld bank locations.

The dashboard has six pages:

- **Overview:** net worth, cash, savings, total contributions, realized and unrealized performance, current market regime, news, and personal net-worth history.
- **Market:** VILX plus eight Minecraft-themed businesses, holdings, allocation, average purchase price, cost basis, charts, buy, and sell actions.
- **Banking:** savings plus up to eight independently selectable locked-rate CDs and eight player-funded villager-lending positions.
- **Exchange:** diamonds, gold, netherite materials, valuable ores, and blocks converted into bank cash at dynamic commodity prices, with commodity history.
- **Village:** local population, housing, prosperity, safety, supplies, production, development tier, current project, backlog, lifecycle, incidents, and restoration status.
- **Fund:** voluntary Direct Grants, protected-principal Endowments, and Project Sponsorships for the associated settlement.

Common transaction amounts are one click away: `1`, `5`, `10`, `32`, `64`, or `All`. The Fund keeps a server-owned additive draft with `+1`, `+5`, `+10`, `+25`, `+100`, `All`, and `Clear`. Risky, destructive, and irreversible actions use a time-limited two-step confirmation owned by the server rather than trusted client state.

Portfolio accounting persists share cost basis, average purchase prices, total contributions and withdrawals, realized and unrealized gains, allocation, a bounded transaction ledger, and personal net-worth history. Market, commodity, and personal histories retain up to five economic years and can be viewed as 30 days, 90 days, one year, or all retained history.

## Investments

- `VILX` Villager Exchange Index
- `RSDN` Redstone Dynamics
- `DPMN` Deepdelve Mining
- `NSPC` Nether Spice Company
- `ENDR` Ender Freight and Logistics
- `GLDH` Golden Harvest Cooperative
- `POTN` Potionworks Laboratories
- `IRNG` Iron Golem Security
- `MCRT` Minecart Transit

The global simulation includes expansion, bull, boom, stagnation, recession, crash, and recovery regimes. Rare events create company- and commodity-specific shocks. Prices, rates, lending outcomes, commodities, and settlement fundamentals continue to evolve while the world is closed.

## Village Prosperity System

The 0.4 beta expands visible settlement progression to **10 curated village projects** with need-driven priorities, biome-aware templates, and bounded sector effects. Threatened villages can prioritize defenses, food-poor villages can prioritize storage, crowded villages can prioritize housing, and mature villages can grow into markets, smithies, inns, and an Exchange Hall.

Village Prosperity connects the market to persistent local settlements without turning villagers into expensive autonomous agents. Offline advancement updates compact data records only. It does **not** force-load chunks, pathfind villagers, mine real ores, simulate raids while offline, or place structures during startup.

Each managed village receives a stable identity and tracks:

- Population and observed residents
- Housing and food supply
- Material supply and treasury
- Prosperity and safety
- Farming, mining, trade, redstone, alchemy, transport, and security output
- Development tier
- Resident status and incident history
- Development projects and physical construction progress

The curated project catalog contains **Cottage, House, Village Inn, Warehouse, Mine Entrance, Market Square, Smithy, Granary, Guard Post, and Exchange Hall**. Completed economic projects enter a bounded visual queue and materialize only while players are nearby and the required chunks are already loaded.

The beta hardening rules include:

- Recovery with visual progression enabled queues real settlers first. An empty village does not resume production or market influence until those settlers actually materialize and are observed.
- Threatened or Devastated settlements with living survivors can enter Recovering after the seven-day stabilization window once safety and prosperity meet the minimums, giving them a bounded route back to growth.
- Simulation-only mode can recover abstractly because no physical representation is requested.
- Physical settler spawning requires real available beds. With visuals enabled, ordinary population growth queues settlers instead of increasing productive population until those villagers actually materialize and are observed.
- Long-absent residents move from Active to Away and eventually Emigrated instead of remaining productive forever.
- Zombie villagers are tracked as Infected using persisted village tags plus nearest-resident reconciliation. Infection suspends that resident from productive population, and a cured villager observed near the infection site is reconciled back into the settlement.
- Functional development tiers can fall after collapse even though completed physical buildings remain.
- Villages pay maintenance, lose a small amount of stored food to spoilage, and experience rare local positive and negative shocks, so prosperity is not a one-way ladder.
- Development structures use a conservative natural-ground whitelist, place their floor above the existing surface, and only replace air or replaceable blocks. They do not overwrite terrain surfaces, paths, farmland, player floors, containers, or rejected protected placements. A verified obstruction before the first placement releases the site so another lot can be selected; an unloaded boundary retains its reservation and retries later so a possibly written prefix is not orphaned.
- Village Bank and prosperity construction expose `VillageDevelopmentProtection.register(PlacementGuard)` for claim/protection integrations. Every guard is consulted before placement and exceptions deny the placement; a claim mod must register a guard for its rules to participate.
- Obstructed projects retry with persistent exponential backoff. Exact project bounds prevent overlap, unstarted projects may relocate, and a partial deterministic template keeps its site for safe continuation after restart.
- Active villager professions provide small sector-specific output bonuses capped at 12 percent. Nearby residents show bounded work particles, looks, and arm swings while construction advances; these are visual cues, not autonomous custom AI.
- In visual mode, housing and production benefits do not become active merely because the abstract project is paid for. They wait for verified physical materialization. A low-frequency integrity audit suspends a completed project's benefits when an authored block is missing and lets the normal guarded queue repair only safe air or replaceable positions.
- Construction activity can issue an occasional low-speed, one-shot navigation request to at most two nearby suitable residents, in addition to bounded looks, arm swings, and particles. This does not install a persistent AI goal or control economic progress.
- Physical census and construction are dimension-aware, query only settlements near loaded players, share a rotating global block budget, and never force chunks. Offline catch-up batch size adapts to the number of stored settlements and accounts.
- Settler placement checks food, beds, fluids, support, and entity collision. The pending-settler queue changes only after a later authoritative census observes the arrival.
- Village centers prefer a nearby bell when one exists. Persisted villager tags are also preferred when resolving an already-known settlement, reducing identity drift and accidental merging as villagers move around. Once a settlement is known, Bank keying and plot search use that persisted stable center rather than the discovering player's moving position.
- Player-caused projectile deaths are attributed through projectile ownership when Minecraft exposes the owner.
- The first player-caused casualty captures the village's exact pre-damage state and market contribution as a persistent counterfactual. That no-player-damage branch advances and is re-priced on each enabled simulation day, and genuine non-player casualties are applied to it. Repeated player hits do not recapture or rebase it; the damage cooldown and full recovery are both required before the live village replaces it.
- The Village dashboard reports the cause and age of the latest local incident when restoration status is not displayed.
- The persisted bank key scopes Banker replacement and routes the Village dashboard plus Prosperity Fund and restoration contributions to that exact associated settlement.

## World configuration

The first server start creates `the_emerald_standard-config.properties` in the world's `data` directory.

Village Prosperity can be configured independently:

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

Typical combinations:

| Simulation | Visual progression | Result |
|---|---|---|
| On | On | Full local economy plus visible development |
| On | Off | Local economies progress without placing prosperity structures or settlers |
| Off | On | Loaded villages may finish visual development without affecting the simulated market |
| Off | Off | Original banking, investing, and global economy only |

`market_integration_enabled` independently controls whether village fundamentals influence assets and commodities. `automatic_recovery_enabled` independently controls automatic recovery from recoverable extinction. Fund switches can disable contributions, endowments, sponsorships, targeted purposes, or visible donor titles separately. The default endowment payout is 4 percent annually; endowment principal itself is never spent. Non-restoration Direct Grants reserve 20 percent for emergencies by default, and all automatic Fund spending is limited by the configured monthly treasury cap.

Construction is deliberately bounded. Defaults are two blocks every ten server ticks, one active visual project per settlement, no forced chunks, and no surface replacement.

Administrators can inspect or reload configuration without restarting:

```text
/emerald config show
/emerald config reload
```

## Reliability and persistence

- One unified economic-time accumulator prevents online and offline time from being counted twice.
- Up to 1,825 economic days of investment, commodity, and personal net-worth history are persisted.
- Save format 9 uses a required magic identifier, mandatory core fields, and SHA-256 checksums.
- Unsupported future formats stop loading instead of silently falling back to stale backups.
- Corrupt current-format saves can recover from a validated backup.
- Inventory-linked deposits, withdrawals, and exchanges use a durable recovery journal.
- Overflow recovery remains journal-protected instead of dropping recoverable value into the world.
- Player accounts are world-level and are never owned by one Banker or one village.
- Share cost basis, realized performance, contributions, a 256-entry transaction ledger, up to eight CDs, and up to eight villager-lending positions are world-persistent.
- Village-owned Prosperity Fund balances, protected endowment principal, project sponsorships, bounded contribution history, and donor recognition are persistent and never become player debt.
- Village identities, residents, incidents, full pre-player-damage village counterfactuals, projects, exact project bounds, construction retry state and progress, bank anchors, and lifecycle state are persistent.
- A configurable transaction cooldown protects servers from repeated button or packet spam.

The 0.4 line first upgraded beta.4 format-7 worlds to format 8 for the expanded project catalog. Beta.2 advances those worlds to format 9 for portfolio analytics, multiple term positions, commodity and personal history, Prosperity Funds, and donor records. Older holdings receive an explicitly inferred opening cost basis because their historical executions were not recorded. Older builds deliberately reject format 9 instead of silently discarding its fields or falling back to stale data. Keep a pre-upgrade world backup if you may need to downgrade.

Materialized projects are now audited gradually. A missing authored block removes that project's economic authority and safely re-enters it into the guarded construction queue; repairs never overwrite solid player blocks, block entities, protected placements, or unloaded chunks. Bank markers and Minecraft chunk saves are still not cross-file atomic, so a crash between those writes can yield fallback Banker access without an automatically rebuilt bank. Player financial data remains world-level and intact.

## One-command debug flight recorder

Mod testing does not require memorizing a diagnostic command tree. An operator can run:

```text
/emerald debug
```

The command enables a full five-minute capture of the testing player's banking actions, market changes, watched village state, construction, settlers, persistence state, validation warnings, and performance sampling. Running the same command again stops early. `/emerald debug <1-15>` selects a duration, and `/emerald debug mark` adds an optional numbered moment marker. Only the operator who started a capture may mark or stop it.

The capture is written incrementally for crash resilience and then packaged under the world's `data/the_emerald_standard_debug` directory as a shareable `TES-debug-*.zip`. Reports exclude the private economy seed, world seed, chat, server address, resident UUIDs, and unrelated player or settlement data. Timing fields distinguish sampling, active recorder ticks, writes, snapshots, and full-state copies instead of presenting overlapping measurements as a subsystem profile. Interrupted captures are packaged automatically on the next server start.

## Administrator commands

Normal gameplay does not require commands. The `/emerald` tree requires permission level 2 and is intended for administration, diagnostics, configuration, and recovery.

```text
/emerald open
/emerald market
/emerald commodities
/emerald portfolio
/emerald recover
/emerald config show|reload
/emerald deposit <emeralds>
/emerald withdraw <emeralds>
/emerald savings deposit|withdraw <emeralds>
/emerald buy <ticker> <emeralds>
/emerald sell <ticker> <shares>
/emerald cd open <emeralds> <30|90|180|365>
/emerald cd close <position-id>
/emerald loan fund <emeralds> <30|90|180|365>
/emerald loan collect <position-id>
/emerald exchange <resource> <count>
```

## Installation

### Fabric

Install Minecraft 26.2, Fabric Loader 0.19.3 or newer, Fabric API 0.158.0+26.2 or newer, Java 25, and the Fabric JAR from a verified workflow artifact or prerelease.

### NeoForge

Install Minecraft 26.2, NeoForge 26.2.0.72 or newer, Java 25, and the NeoForge JAR from a verified workflow artifact or prerelease.

The mod must be installed on the server and on every connecting client because it adds a custom graphical menu.

## Building

Minecraft 26.2 requires Java 25. Each loader project includes a pinned Gradle 9.5.1 wrapper.

```bash
bash scripts/run-common-tests.sh
bash fabric/gradlew --no-daemon -p fabric build
bash neoforge/gradlew --no-daemon -p neoforge build
```

## Verification

GitHub Actions runs the common economy, persistence, and Village Prosperity regression suites, builds and inspects both packaged JARs, launches both dedicated-server environments, and launches both clients under a virtual display to verify initialization and screen registration.

Hands-on visual, transaction, terrain, raid, recovery, and multiplayer checks remain part of the beta test plan.

## Documentation

- [Debug flight recorder](docs/DEBUGGING.md)

- [Village Prosperity System](docs/VILLAGE_PROSPERITY.md)
- [Banker GUI and village banks](docs/GUI_AND_VILLAGE_BANKS.md)
- [Economy model](docs/ECONOMY.md)
- [Architecture and persistence](docs/ARCHITECTURE.md)
- [Inventory transaction recovery](docs/TRANSACTION_RECOVERY.md)
- [Testing and publication gate](docs/TESTING.md)
- [Build status](release/BUILD_STATUS.md)
- [Change history](CHANGELOG.md)

## License

MIT. See [LICENSE](LICENSE).
