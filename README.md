# The Emerald Standard

[![Build, test, and launch](https://github.com/chedidandrew/The_Emerald_Standard/actions/workflows/build.yml/badge.svg)](https://github.com/chedidandrew/The_Emerald_Standard/actions/workflows/build.yml)

A lightweight villager banking, investing, commodity-exchange, and settlement-economy mod for **Minecraft 26.2**, with Fabric and NeoForge builds.

> Current status: [`0.4.0-beta.2` public prerelease](https://github.com/chedidandrew/The_Emerald_Standard/releases/tag/v0.4.0-beta.2). Its exact source commit passed the full automated gate; hands-on matrix results are still `Not run`. Normal gameplay is centered on Banker villagers, Village Banks, the graphical bank dashboard, and the optional Village Prosperity System. Commands are reserved for administrators and diagnostics.

## Core rule

Players provide emerald capital to the villager economy. **Players can never borrow emeralds, hold a negative balance, or enter debt.** Villager business lending can lose some or all of the amount voluntarily invested, but it can never create an additional obligation.

## Quick start

1. Back up the world before installing or upgrading this beta.
2. Download exactly one playable JAR from the [`0.4.0-beta.2` prerelease](https://github.com/chedidandrew/The_Emerald_Standard/releases/tag/v0.4.0-beta.2), then install that same Fabric or NeoForge JAR on the server and every connecting client. Never install both loader JARs, do not use a `-sources.jar`, and do not mix builds from different commits.
3. Enter a loaded Overworld village. The mod searches periodically for a safe Village Bank site and supplies a Banker even when terrain prevents a structure.
4. Right-click the Banker or an Exchange Desk to open the seven-page dashboard.
5. On **Overview**, choose an amount and deposit physical emeralds into bank cash. Use **Savings** for a safe liquid return, or choose a CD, villager lending position, commodity exchange, or market asset according to its displayed risk.

Each player receives a one-time discovery hint on first join. Their first successful Banker visit awards the advancement **The Emerald Standard** and gives a short deposit and risk explanation. Both normal play and discovery are command-free.

## Banking and investing

When a player discovers a loaded Overworld village, The Emerald Standard can establish a detailed civic **Village Bank and Exchange** nearby. The larger biome-aware building has a sealed stepped roof, sheltered entrance, storage and service furnishings, one dedicated Exchange Desk workstation, and a persistent villager with the registered Banker profession. New banks accept dry natural lots with at most two blocks of height variation, level their floor above the highest sampled surface, bridge only the resulting shallow gaps with biome-matched foundations, preflight every block through the cooperative protection API, and verify every resulting block state before recording generation. If no site is safe, the mod uses an untouched unemployed adult villager or spawns a new Banker. Established villagers with professions, XP, trades, custom names, or other player investment are never repurposed.

New settlements receive stable per-village bank identities even when two villages share the same legacy 256-block region. Existing bank-region associations and anchors remain authoritative for upgraded worlds. Village Bank generation is intentionally Overworld-only in this beta; Village Prosperity records may exist in other dimensions.

Right-click a Banker or any Exchange Desk to open the dashboard. Exchange Desks are craftable, face the player when placed, and appear under Creative inventory's Functional Blocks tab and search; an unscoped desk or naturally employed Banker uses the nearest managed settlement in the same dimension within 160 blocks for its Village and Fund pages, while the player's financial account remains globally available. Managed Bankers keep their registered profession and generated-desk job-site memory across ordinary work cycles and reloads. Lectern counters from older worlds remain valid only at their persisted Overworld bank locations.

The dashboard has seven pages:

- **Account:** net worth, inventory emeralds, bank cash, savings, total contributions, realized and unrealized performance, current economic day and market regime, and personal net-worth history.
- **Market:** a previous/next carousel for VILX plus eight Minecraft-themed businesses, with holdings, allocation, average purchase price, cost basis, chart, market bulletin, buy, and sell actions for the selected investment.
- **Banking:** separate Transfers, CDs, and Villager Loans views. Transfers explicitly route Inventory -> Bank Cash, Bank Cash -> Inventory, Bank Cash -> Savings, or Savings -> Bank Cash; term-product views show source and projected balances for up to eight independently selectable positions of each type.
- **Exchange:** diamonds, gold, netherite materials, valuable ores, and blocks converted into bank cash at dynamic commodity prices. All 18 supported resource forms derive chart history from the same canonical commodity inputs and conversion formula as their live quote.
- **Village:** local population, housing, prosperity, safety, supplies, production, development tier, current project, backlog, lifecycle, incidents, and restoration status.
- **Fund:** voluntary Direct Grants, protected-principal Endowments, and Project Sponsorships for the associated settlement.
- **Activity:** lifetime bank inflow and withdrawals plus a mouse-wheel and arrow-scrollable view of the player's complete retained transaction ledger, newest first, with cycling category filters.

Transactions and Prosperity Fund contributions accept an exact typed whole-emerald amount from `1` through `1,000,000`, with Apply, Cancel, Enter, and `All` controls. The server validates every applied amount again and caps it against the live source; the Fund's applied contribution amount remains server-owned. Risky, destructive, and irreversible actions use a time-limited two-step confirmation owned by the server rather than trusted client state.

The selected amount and each action's source, destination, and projected balances are shown in the page or its tooltip. Supporting explanations wrap within compact hover tooltips, and the dashboard grows or shrinks within safe bounds to use the current logical window. Custom labels retain Minecraft's native glyph size to match button and amount-entry text while their anchors and available widths follow the responsive panel. Account balances expose exact values on hover, and disabled or state-dependent controls explain whether a CD is mature or early and why a Fund purpose is fixed. Savings must first transfer to Bank Cash before it can be withdrawn to inventory or invested.

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

The global simulation includes expansion, bull, boom, stagnation, recession, crash, and recovery regimes. Rare events create company- and commodity-specific shocks when enabled. By default, prices, rates, lending outcomes, commodities, and settlement fundamentals continue to evolve while the world is closed; a world can disable trusted wall-clock progression without pausing ordinary game-time progression.

## Village Prosperity System

The 0.4 beta expands visible settlement progression to **10 curated village projects** with need-driven priorities, biome-aware templates, and bounded sector effects. Threatened villages can prioritize defenses, food-poor villages can prioritize storage, crowded villages can prioritize housing, and mature villages can grow into markets, smithies, inns, and an Exchange Hall.

Village Prosperity connects the market to persistent local settlements without turning villagers into expensive autonomous agents. Once a village is known, its compact economic record can advance while every player is elsewhere and through trusted offline catch-up on the next server start. This data-only work does **not** force-load chunks, pathfind villagers, mine real ores, simulate raids while offline, or place structures during startup.

Each managed village receives a stable identity and tracks:

- Population and observed residents
- Housing and food supply
- Material supply and treasury
- Prosperity and safety
- Farming, mining, trade, redstone, alchemy, transport, and security output
- Development tier
- Resident status and incident history
- Development projects and physical construction progress

The curated project catalog contains **Cottage, House, Village Inn, Warehouse, Mine Entrance, Market Square, Smithy, Granary, Guard Post, and Exchange Hall**. The Village page distinguishes economic Planning from physical Building: once planning reaches 100%, the project counts economically even if nobody is nearby, while its blocks enter a bounded visual queue. Building advances only while a player is within the same-dimension horizontal activation radius and the required chunks are already loaded. That radius defaults to the recommended 256 blocks, can be configured from 48 through 512, ignores Y separation, and never force-loads chunks. Each project receives one of three deterministic, save-stable detail presets. Its natural dirt-path/gravel trail branches to the nearest earlier structure or a stable settlement-edge hub, and safe append-only detail layers are added at town and city development tiers without removing earlier work if the tier later falls.

The beta hardening rules include:

- Recovery with visual progression enabled queues real settlers first. An empty village does not resume production or market influence until those settlers actually materialize and are observed.
- Threatened or Devastated settlements with living survivors can enter Recovering after the seven-day stabilization window once safety and prosperity meet the minimums, giving them a bounded route back to growth.
- Simulation-only mode can recover abstractly because no physical representation is requested.
- Physical settler spawning requires real available beds. With visuals enabled, ordinary population growth creates committed settlers that count in the economic simulation immediately and remain queued until matching villager entities can materialize and be observed. Empty-village recovery remains the safety exception described above: it waits for real settlers before restarting production.
- Long-absent residents move from Active to Away and eventually Emigrated instead of remaining productive forever.
- Zombie villagers are tracked as Infected using persisted village tags plus nearest-resident reconciliation. Infection suspends that resident from productive population, and a cured villager observed near the infection site is reconciled back into the settlement.
- Functional development tiers can fall after collapse even though completed physical buildings remain.
- Villages pay maintenance, lose a small amount of stored food to spoilage, and experience rare local positive and negative shocks, so prosperity is not a one-way ladder.
- Development structures use a conservative natural-ground whitelist, accept at most two blocks of terrain variation, place their floor above the highest sampled surface, and bridge only shallow air gaps with deterministic foundation columns. They do not replace sound natural support, terrain surfaces, existing paths, farmland, player floors, containers, or rejected protected placements. A verified obstruction before the first placement releases the site so another lot can be selected; an unloaded boundary retains its reservation and retries later so a possibly written prefix is not orphaned.
- Village Bank and prosperity construction expose `VillageDevelopmentProtection.register(PlacementGuard)` for claim/protection integrations. Every guard is consulted before placement and exceptions deny the placement; a claim mod must register a guard for its rules to participate.
- Obstructed projects retry with persistent exponential backoff. Exact project bounds prevent overlap, unstarted projects may relocate, and a partial deterministic template keeps its site for safe continuation after restart.
- Richer structure templates preserve the original placement prefix for upgraded worlds. Every appended structural position must be empty or replaceable, free of block entities, and accepted by the protection hook before the upgrade suspends benefits or changes its persisted bounds. Shared trail cells are non-authoritative public infrastructure: safe natural-ground cells are paved, existing TES-style paths are adopted, and protected, occupied, or non-terrain cells are skipped without disabling the building. Without a registered claim guard, Minecraft cannot distinguish a player-placed dirt, grass, sand, or stone-family block from matching natural terrain.
- Curated structures use continuous roofs, clear residential entrances, and a limited set of theme-appropriate vanilla utility and job-site blocks; newly generated Village Banks author only their single Exchange Desk job site.
- Active villager professions provide small sector-specific output bonuses capped at 12 percent. Nearby residents show bounded work particles, looks, and arm swings while construction advances; these are visual cues, not autonomous custom AI.
- Economic completion activates a project's housing and production effects even while its physical template is still queued. A low-frequency integrity audit can suspend the benefits of a structure that had materialized and is later found missing an authored block; the player must restore that block before the audit reactivates it, so furnishings cannot become renewable drops. Safe append-only template upgrades still use the guarded construction queue.
- Construction activity can issue an occasional low-speed, one-shot navigation request to at most two nearby suitable residents, in addition to bounded looks, arm swings, and particles. Entity and construction-theatre searches keep their local 48-block cap even if the development radius is raised; a new settler's assigned home radius is separately capped at 32 blocks. These rules do not install a persistent AI goal or control economic progress.
- Physical census and construction are dimension-aware, select by horizontal distance from loaded players, consider at most 16 eligible villages per construction pass, share a rotating global block budget, and operate only in already-loaded chunks. Offline catch-up batch size adapts to the number of stored settlements and accounts.
- Settler placement checks food, beds, fluids, support, and entity collision. The pending-settler queue changes only after a later authoritative census observes the arrival.
- Village centers prefer a nearby bell when one exists. Persisted villager tags are also preferred when resolving an already-known settlement, reducing identity drift and accidental merging as villagers move around. Once a settlement is known, Bank keying and plot search use that persisted stable center rather than the discovering player's moving position.
- Player-caused projectile deaths are attributed through projectile ownership when Minecraft exposes the owner.
- The first player-caused casualty captures the village's exact pre-damage state and market contribution as a persistent counterfactual. That no-player-damage branch advances and is re-priced on each enabled simulation day, and genuine non-player casualties are applied to it. Repeated player hits do not recapture or rebase it; the damage cooldown and full recovery are both required before the live village replaces it.
- The Village dashboard reports the cause and age of the latest local incident when restoration status is not displayed.
- The persisted bank key scopes Banker replacement and routes the Village dashboard plus Prosperity Fund and restoration contributions to that exact associated settlement.

## World configuration

The first server start creates `the_emerald_standard-config.properties` in the world's `data` directory.

The full [configuration reference](docs/CONFIGURATION.md) lists every setting, default, accepted range, interaction, and safe reload behavior. Unknown keys and invalid values are rejected as a whole, so a failed `/emerald config reload` leaves the previous configuration active. The optional one-time discovery message is controlled by `onboarding.join_hint_enabled`; market events and trusted offline progression have independent world controls.

Village Prosperity can be configured independently:

```properties
market.events_enabled=true
economic_clock.offline_progression_enabled=true
economic_clock.max_offline_days=25000
village_prosperity.simulation_enabled=true
village_prosperity.visual_progression_enabled=true
village_prosperity.market_integration_enabled=true
village_prosperity.automatic_recovery_enabled=true
village_prosperity.development_radius=256
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

Construction is deliberately bounded. Defaults are two blocks every ten server ticks, one active visual project per settlement, the recommended 256-block horizontal activation radius, and at most 16 eligible villages considered per pass. Larger values are intended only for servers that already keep the relevant village chunks loaded: matching the radius never force-loads a chunk. The wider activation range also does not widen 48-block entity/theatre searches or the separate 32-block settler-home cap.

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
- A completed item transfer checkpoints only the affected player's synchronized NBT file and verifies the complete persisted inventory before releasing its journal.
- Whole-economy replacement saves cache the SHA-256 identity of the last validated generation, avoiding a redundant full parse only when the old file's exact bytes are unchanged.
- Overflow recovery remains journal-protected instead of dropping recoverable value into the world.
- Player accounts are world-level and are never owned by one Banker or one village.
- Share cost basis, realized performance, contributions, a 256-entry transaction ledger, up to eight CDs, and up to eight villager-lending positions are world-persistent.
- Village-owned Prosperity Fund balances, protected endowment principal, project sponsorships, bounded contribution history, and donor recognition are persistent and never become player debt.
- Village identities, residents, incidents, full pre-player-damage village counterfactuals, projects, exact project bounds, construction retry state and progress, bank anchors, and lifecycle state are persistent.
- A configurable transaction cooldown protects servers from repeated button or packet spam.

The 0.4 line first upgraded beta.4 format-7 worlds to format 8 for the expanded project catalog. Beta.2 advances those worlds to format 9 for portfolio analytics, multiple term positions, commodity and personal history, Prosperity Funds, and donor records. Older holdings receive an explicitly inferred opening cost basis because their historical executions were not recorded. Older builds deliberately reject format 9 instead of silently discarding its fields or falling back to stale data. Keep a pre-upgrade world backup if you may need to downgrade.

Materialized projects are audited gradually. A missing authored block removes that project's economic authority but is not regenerated; replacing the authored block lets a later audit restore the benefits without creating an item farm. Append-only template upgrades remain guarded and never overwrite solid player blocks, block entities, protected placements, or unloaded chunks. Bank markers and Minecraft chunk saves are still not cross-file atomic, so a crash between those writes can yield fallback Banker access without an automatically rebuilt bank. Player financial data remains world-level and intact.

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
Use the same exact build on the server and every client; the Banker menu's synchronized slot layout is part of the network protocol.

## Building

Minecraft 26.2 requires Java 25. Each loader project includes a pinned Gradle 9.5.1 wrapper.

```bash
bash scripts/run-common-tests.sh
bash fabric/gradlew --no-daemon -p fabric build
bash neoforge/gradlew --no-daemon -p neoforge build
```

## Verification

GitHub Actions runs the common economy, persistence, and Village Prosperity regression suites, builds and inspects both packaged JARs, launches both dedicated-server environments, and launches both clients under a virtual display to verify initialization and screen registration.

Hands-on visual, transaction, terrain, raid, recovery, and multiplayer checks remain part of the beta test plan. Record exact-commit results with the repository's [manual beta test form](https://github.com/chedidandrew/The_Emerald_Standard/issues/new?template=manual_beta_test.yml); a failure or ambiguous result should include the ZIP from `/emerald debug`.

## Documentation

- [Debug flight recorder](docs/DEBUGGING.md)
- [World configuration](docs/CONFIGURATION.md)
- [Village Prosperity System](docs/VILLAGE_PROSPERITY.md)
- [Banker GUI and village banks](docs/GUI_AND_VILLAGE_BANKS.md)
- [Economy model](docs/ECONOMY.md)
- [Architecture and persistence](docs/ARCHITECTURE.md)
- [Inventory transaction recovery](docs/TRANSACTION_RECOVERY.md)
- [Testing and publication gate](docs/TESTING.md)
- [Release procedure and checksum gate](docs/RELEASING.md)
- [Build status](release/BUILD_STATUS.md)
- [Change history](CHANGELOG.md)

## License

MIT. See [LICENSE](LICENSE).
