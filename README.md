# The Emerald Standard

[![Build, test, and launch](https://github.com/chedidandrew/The_Emerald_Standard/actions/workflows/build.yml/badge.svg)](https://github.com/chedidandrew/The_Emerald_Standard/actions/workflows/build.yml)

A lightweight villager banking, investing, and commodity-exchange mod for **Minecraft 26.2**, with Fabric and NeoForge builds.

> Current status: `0.2.0-alpha.3`. The player experience is centered on Banker villagers and a graphical bank dashboard. Commands are reserved for administrators and diagnostics.

## Core rule

Players provide emerald capital to the villager economy. **Players can never borrow emeralds, hold a negative balance, or enter debt.** Villager business lending can lose some or all of the amount voluntarily invested, but it can never create an additional obligation.

## Natural village banking

When a player discovers a loaded village region, the mod searches for a safe nearby plot and builds a compact **Village Bank and Exchange**. The building uses a village-biome palette, includes a banking counter, and contains a persistent Banker villager.

This discovery-time system works in new and existing worlds without replacing vanilla village template pools. If no safe plot exists, the mod uses an untouched unemployed adult villager or spawns a new Banker. It never converts a villager with a profession, experience, trades, or a custom name.

Each Banker is associated with one persisted village-bank region, stays near its service point, and can be replaced at the correct counter if lost. Right-click the Banker or the bank lectern to open the dashboard.

## Casual-friendly dashboard

The graphical interface is split into four pages:

- **Overview:** net worth, cash, savings, physical emeralds, current market regime, market news, and a market chart.
- **Market:** nine sector-labeled Minecraft-themed investments, current prices, holdings, risk labels, chart history, buy, sell 25%, and sell-all actions.
- **Banking:** savings, locked-rate CDs, and player-funded villager business lending with visible rates and estimated opening default risk.
- **Exchange:** diamonds, gold, netherite materials, valuable ores, and blocks converted into bank cash at dynamic commodity prices.

Common transaction amounts are one click away: `1`, `5`, `10`, `32`, `64`, or `All`. Destructive or risky actions require confirmation. Hover text explains trade-offs, and charts include scale labels and point inspection.

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

The simulation includes expansion, bull, boom, stagnation, recession, crash, and recovery regimes. Rare market events create company- and commodity-specific shocks, while `VILX` tracks a weighted basket plus the broader village economy. Prices, rates, risk, and lending outcomes continue to evolve while the world is closed.

## Reliability and persistence

- One unified economic-time accumulator prevents online and offline time from being counted twice.
- Up to 180 economic days of chart history are persisted per investment.
- Save format 5 uses a required magic identifier, mandatory core fields, and SHA-256 checksums.
- Unsupported future save formats stop loading instead of silently falling back to stale backups.
- Corrupt current-format saves can recover from a validated backup.
- Inventory-linked deposits, withdrawals, and exchanges use a durable recovery journal.
- Overflow recovery stays journal-protected instead of dropping item entities into the world.
- Generated bank regions and exact Banker anchors are persisted.
- A configurable server-side transaction cooldown prevents button and packet spam.
- Player disconnects clear transient cooldown state.

## World configuration

The first server start creates `the_emerald_standard-config.properties` in the world's `data` directory. It controls village-bank generation, scan frequency, region size, Banker home radius, and the transaction cooldown.

Administrators can inspect or reload it without restarting:

```text
/emerald config show
/emerald config reload
```

## Administrator commands

Normal gameplay does not require commands. The `/emerald` tree requires permission level 2 and is intended for administrators, diagnostics, and development testing.

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

Install Minecraft 26.2, Fabric Loader 0.19.3 or newer, Fabric API 0.158.0+26.2 or newer, and the Fabric JAR from a verified workflow artifact or prerelease.

### NeoForge

Install Minecraft 26.2, NeoForge 26.2.0.72 or newer, and the NeoForge JAR from a verified workflow artifact or prerelease.

The mod must be installed on the server and on every connecting client because it adds a custom graphical menu.

## Project layout

```text
common/    Economy, persistence, GUI, village banks, shared gameplay, and tests
fabric/    Fabric 26.2 lifecycle hooks, metadata, and Gradle build
neoforge/  NeoForge 26.2 lifecycle hooks, metadata, and Gradle build
docs/      Economy, GUI, architecture, recovery, and testing documentation
scripts/   Regression, JAR verification, client smoke, and server smoke runners
release/   Verified build status and artifact provenance
```

## Building

Minecraft 26.2 requires Java 25. Each loader project includes a pinned Gradle 9.5.1 wrapper.

```bash
bash scripts/run-common-tests.sh
bash fabric/gradlew --no-daemon -p fabric build
bash neoforge/gradlew --no-daemon -p neoforge build
```

On Windows PowerShell, use `fabric\\gradlew.bat --no-daemon -p fabric build` and the corresponding `neoforge\\gradlew.bat` command.

## Verification

GitHub Actions runs the common regression suite, builds and inspects both packaged JARs, launches both dedicated-server development environments, runs a live Banker invariant test, and launches both clients under a virtual display to verify client initialization and screen registration.

Manual visual, transaction, and multiplayer checks remain documented in [docs/TESTING.md](docs/TESTING.md).

## Documentation

- [Banker GUI and village banks](docs/GUI_AND_VILLAGE_BANKS.md)
- [Economy model](docs/ECONOMY.md)
- [Architecture and persistence](docs/ARCHITECTURE.md)
- [Inventory transaction recovery](docs/TRANSACTION_RECOVERY.md)
- [Testing and publication gate](docs/TESTING.md)
- [Build status](release/BUILD_STATUS.md)
- [Change history](CHANGELOG.md)

## License

MIT. See [LICENSE](LICENSE).
