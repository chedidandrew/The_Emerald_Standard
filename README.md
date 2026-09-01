# The Emerald Standard

[![Build, test, and launch](https://github.com/chedidandrew/The_Emerald_Standard/actions/workflows/build.yml/badge.svg)](https://github.com/chedidandrew/The_Emerald_Standard/actions/workflows/build.yml)

A lightweight villager banking, investing, and commodity-exchange mod for **Minecraft 26.2**, with Fabric and NeoForge builds.

> Current status: `0.2.0-alpha.2`. The player experience is centered on Banker villagers and a graphical bank dashboard. Commands are retained for administrators and testing only.

## Core rule

Players provide emerald capital to the villager economy. **Players can never borrow emeralds, hold a negative balance, or enter debt.** Villager business lending can lose some or all of the amount voluntarily invested, but it can never create an additional obligation.

## Natural village banking

When a player discovers a loaded village region, the mod automatically attempts to build a compact **Village Bank and Exchange** on a safe plot near the village. The building uses vanilla blocks, includes a banking counter, and contains a persistent Banker villager.

This discovery-time generation works in both new and existing worlds. If no safe bank plot is available, an adult village resident is designated as the local Banker instead, so the economy remains accessible without commands.

Right-click a villager named **Banker** to open The Emerald Standard dashboard. Bankers use the vanilla librarian profession and lectern behavior, remain close to their bank, and are replaced at the persisted bank counter if lost.

## Casual-friendly dashboard

The graphical interface is split into four simple pages:

- **Overview:** net worth, cash, savings, physical emeralds, current market regime, market news, and a market chart.
- **Market:** nine sector-labeled Minecraft-themed investments, current prices, holdings, risk labels, chart history, buy, sell 25%, and sell-all actions.
- **Banking:** savings, locked-rate CDs, and player-funded villager business lending with visible rates and estimated opening default risk.
- **Exchange:** diamonds, gold, netherite materials, valuable ores, and blocks converted into bank cash at dynamic commodity prices.

Common transaction amounts are one click away: `1`, `5`, `10`, `32`, `64`, or `All`. Destructive or risky actions require confirmation, hover text explains the trade-offs, and charts include scale labels and point inspection.

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

The simulation includes expansion, bull, boom, stagnation, recession, crash, and recovery regimes. Rare market events create company- and commodity-specific shocks, while `VILX` tracks a weighted basket plus the broader village economy. Market prices, company risk, commodity prices, savings rates, CD rates, and villager-lending outcomes evolve persistently and continue while the world is closed.

## Reliability and persistence

- One unified economic-time accumulator prevents online and offline time from being counted twice.
- Up to 180 economic days of chart history are persisted per investment.
- Save format 5 uses a required magic identifier, mandatory core fields, and SHA-256 checksums.
- Unsupported future save formats stop loading instead of silently falling back to stale backups.
- Corrupt current-format saves can recover from a validated backup.
- Inventory-linked deposits, withdrawals, and exchanges use a durable recovery journal. Overflow recovery remains journal-protected instead of dropping item entities into the world.
- Generated village-bank regions and exact Banker anchors are persisted so the same area does not repeatedly generate banks and lost Bankers return to the correct location.
- A short configurable server-side transaction cooldown prevents button and packet spam.

## World configuration

The first server start creates `the_emerald_standard-config.properties` in the world's `data` directory. It controls village-bank generation, village scan frequency, region size, Banker home radius, and the transaction cooldown. Administrators can inspect or reload it without restarting:

```text
/emerald config show
/emerald config reload
```

## Administrator commands

Normal gameplay does not require commands. The `/emerald` command tree now requires permission level 2 and is intended for administrators, diagnostics, and development testing.

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

## Project layout

```text
common/    Economy, persistence, GUI, village banks, shared gameplay, and tests
fabric/    Fabric 26.2 lifecycle hooks, metadata, and Gradle build
neoforge/  NeoForge 26.2 lifecycle hooks, metadata, and Gradle build
docs/      Economy, GUI, architecture, recovery, and testing documentation
scripts/   Common regression and dedicated-server smoke-test runners
release/   Verified build status and artifact provenance
```

## Building

Minecraft 26.2 requires Java 25. Each loader project includes a pinned Gradle 9.5.1 wrapper.

```bash
bash scripts/run-common-tests.sh
bash fabric/gradlew --no-daemon -p fabric build
bash neoforge/gradlew --no-daemon -p neoforge build
```

On Windows PowerShell, use `fabric\gradlew.bat --no-daemon -p fabric build` and the corresponding `neoforge\gradlew.bat` command.

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
