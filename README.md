# The Emerald Standard

[![Build, test, and launch](https://github.com/chedidandrew/The_Emerald_Standard/actions/workflows/build.yml/badge.svg)](https://github.com/chedidandrew/The_Emerald_Standard/actions/workflows/build.yml)

A lightweight villager banking, investing, commodity-exchange, and settlement-economy mod for **Minecraft 26.2**, with Fabric and NeoForge builds.

> Current status: `0.3.0-beta.2`. Normal gameplay is centered on Banker villagers, Village Banks, the graphical bank dashboard, and the optional Village Prosperity System. Commands are reserved for administrators and diagnostics.

## Core rule

Players provide emerald capital to the villager economy. **Players can never borrow emeralds, hold a negative balance, or enter debt.** Villager business lending can lose some or all of the amount voluntarily invested, but it can never create an additional obligation.

## Banking and investing

When a player discovers a loaded village, The Emerald Standard can establish a compact **Village Bank and Exchange** nearby. The bank uses a biome-aware palette, includes a banking counter, and contains a persistent Banker villager. If a bank site cannot be placed safely, the mod uses an untouched unemployed adult villager or spawns a new Banker. Established villagers with professions, XP, trades, custom names, or other player investment are never repurposed.

Right-click a Banker or a generated bank lectern to open the dashboard.

The dashboard has five pages:

- **Overview:** net worth, cash, savings, physical emeralds, current market regime, news, and chart history.
- **Market:** VILX plus eight Minecraft-themed businesses, holdings, sectors, risk labels, charts, buy, and sell actions.
- **Banking:** savings, locked-rate CDs, and player-funded villager business lending with visible yield and default risk.
- **Exchange:** diamonds, gold, netherite materials, valuable ores, and blocks converted into bank cash at dynamic commodity prices.
- **Village:** local population, housing, prosperity, safety, supplies, production, development tier, current project, backlog, lifecycle, and restoration support.

Common transaction amounts are one click away: `1`, `5`, `10`, `32`, `64`, or `All`. Risky or destructive actions require confirmation.

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

The first development projects are a **Cottage**, **Warehouse**, and **Mine Entrance**. Completed economic projects enter a bounded visual queue and materialize only while players are nearby and the required chunks are already loaded.

The beta hardening rules include:

- Recovery with visual progression enabled queues real settlers first. An empty village does not resume production or market influence until those settlers actually materialize and are observed.
- Simulation-only mode can recover abstractly because no physical representation is requested.
- Physical settler spawning requires real available beds. With visuals enabled, ordinary population growth queues settlers instead of increasing productive population until those villagers actually materialize and are observed.
- Long-absent residents move from Active to Away and eventually Emigrated instead of remaining productive forever.
- Zombie villagers are tracked as Infected using persisted village tags plus nearest-resident reconciliation. Infection suspends that resident from productive population, and a cured villager observed near the infection site is reconciled back into the settlement.
- Functional development tiers can fall after collapse even though completed physical buildings remain.
- Villages pay maintenance, lose a small amount of stored food to spoilage, and experience rare local positive and negative shocks, so prosperity is not a one-way ladder.
- Development structures use a conservative natural-ground whitelist, place their floor above the existing surface, and only replace air or replaceable blocks. They do not overwrite terrain surfaces, paths, farmland, player floors, containers, or rejected protected placements. A project blocked before its first placement releases the site and can choose another lot later.
- Village centers prefer a nearby bell when one exists. Persisted villager tags are also preferred when resolving an already-known settlement, reducing identity drift and accidental merging as villagers move around.
- Player-caused projectile deaths are attributed through projectile ownership when Minecraft exposes the owner.

## World configuration

The first server start creates `the_emerald_standard-config.properties` in the world's `data` directory.

Village Prosperity can be configured independently:

```properties
village_prosperity.simulation_enabled=true
village_prosperity.visual_progression_enabled=true
village_prosperity.market_integration_enabled=true
village_prosperity.automatic_recovery_enabled=true
```

Typical combinations:

| Simulation | Visual progression | Result |
|---|---|---|
| On | On | Full local economy plus visible development |
| On | Off | Local economies progress without placing prosperity structures or settlers |
| Off | On | Loaded villages may finish visual development without affecting the simulated market |
| Off | Off | Original banking, investing, and global economy only |

`market_integration_enabled` independently controls whether village fundamentals influence assets and commodities. `automatic_recovery_enabled` independently controls automatic recovery from recoverable extinction.

Construction is deliberately bounded. Defaults are two blocks every ten server ticks, one active visual project per settlement, no forced chunks, and no surface replacement.

Administrators can inspect or reload configuration without restarting:

```text
/emerald config show
/emerald config reload
```

## Reliability and persistence

- One unified economic-time accumulator prevents online and offline time from being counted twice.
- Up to 180 economic days of chart history are persisted per investment.
- Save format 6 uses a required magic identifier, mandatory core fields, and SHA-256 checksums.
- Unsupported future formats stop loading instead of silently falling back to stale backups.
- Corrupt current-format saves can recover from a validated backup.
- Inventory-linked deposits, withdrawals, and exchanges use a durable recovery journal.
- Overflow recovery remains journal-protected instead of dropping recoverable value into the world.
- Player accounts are world-level and are never owned by one Banker or one village.
- Village identities, residents, incidents, projects, construction progress, bank anchors, and lifecycle state are persistent.
- A configurable transaction cooldown protects servers from repeated button or packet spam.

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
/emerald cd close
/emerald loan fund <emeralds> <30|90|180|365>
/emerald loan collect
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
