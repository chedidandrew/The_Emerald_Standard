# The Emerald Standard

[![Build, test, and launch](https://github.com/chedidandrew/The_Emerald_Standard/actions/workflows/build.yml/badge.svg)](https://github.com/chedidandrew/The_Emerald_Standard/actions/workflows/build.yml)

A lightweight passive-investing and villager-economy mod for **Minecraft 26.2**, with separate Fabric and NeoForge builds.

> Current status: `0.1.0-alpha.2`. The economy, persistence, investments, crash-recovery journal, and command interface are implemented for testing. The Banker villager, workstation, charts, and full graphical interface remain the next gameplay milestone.

## Core rule

Players provide emerald capital to the villager economy. **Players can never borrow emeralds, hold a negative balance, or enter debt.** A villager business investment can lose some or all of the amount voluntarily invested, but it can never create an additional obligation.

## Implemented in alpha.2

- A world-wide Villager Exchange with expansion, bull, boom, stagnation, recession, crash, and recovery regimes.
- VILX, the diversified Villager Exchange Index, plus eight Minecraft-themed companies.
- Deterministic market returns with independently mixed Gaussian inputs, rare jumps, correlated company risk, and positive long-run company distributions.
- Savings with variable economic rates averaging near 3% over long simulations.
- CDs with 30, 90, 180, and 365-day terms, locked opening rates, maturity stops, and an early withdrawal penalty.
- Player-funded villager business lending with interest, partial default, and full default outcomes.
- Dynamic diamond, gold, netherite, and emerald-ore exchange markets.
- Fractional investing and a 0.25% trading spread on each side.
- Offline progression at one economic day per Minecraft day, or 20 real minutes while the world is closed.
- Partial-day progress preserved across short play sessions and restarts.
- Bounded and chunked catch-up after very long offline periods, with banking paused until catch-up completes.
- A private economy seed saved independently from the visible Minecraft world seed.
- Versioned atomic saves, backup recovery, mutation rollback, automatic-save batching, and retry backoff.
- A durable inventory transaction journal that reconciles interrupted deposits, withdrawals, and resource exchanges after reconnecting.
- UUID-isolated accounts and explicit no-debt validation.
- Fabric and NeoForge projects with automated common tests, loader builds, and dedicated-server launch smoke tests.

## Alpha command interface

Use `/emerald help` in-game.

```text
/emerald market
/emerald commodities
/emerald portfolio
/emerald recover
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

Accepted alpha exchange resources include diamonds and diamond blocks, diamond ores, gold ingots and blocks, raw gold and raw-gold blocks, Overworld and Nether gold ores, ancient debris, netherite scrap, netherite ingots and blocks, emerald blocks, and emerald ores. Values move with the simulated commodity markets.

Example:

```text
/emerald deposit 32
/emerald buy VILX 20
/emerald savings deposit 6
/emerald cd open 3 90
/emerald loan fund 3 180
```

## Safety limits

- Inventory-linked commands are limited to 100,000 items per transaction.
- Bank-only commands are limited to 1,000,000 emeralds per transaction.
- Only one inventory transaction may be pending for a player at a time.
- Banking is temporarily paused while a large offline catch-up backlog is processed.
- A pending transaction is automatically reconciled on login, logout, or the next bank command. `/emerald recover` is available for manual recovery.

## Project layout

```text
common/    Shared economy, persistence, command interface, and tests
fabric/    Fabric 26.2 lifecycle hooks, metadata, and Gradle build
neoforge/  NeoForge 26.2 lifecycle hooks, metadata, and Gradle build
docs/      Economy, architecture, recovery, and testing documentation
scripts/   Common regression and dedicated-server smoke-test runners
release/   Verified build status and artifact provenance
```

## Building

Minecraft 26.2 requires Java 25. Gradle is provisioned automatically in GitHub Actions.

```bash
bash scripts/run-common-tests.sh
gradle --no-daemon -p fabric build
gradle --no-daemon -p neoforge build
```

The workflow builds each loader independently and launches both dedicated-server development environments. Successful runs publish Fabric and NeoForge JARs as workflow artifacts. A formal public release remains gated on manual client gameplay testing.

## Documentation

- [Economy model](docs/ECONOMY.md)
- [Architecture and persistence](docs/ARCHITECTURE.md)
- [Inventory transaction recovery](docs/TRANSACTION_RECOVERY.md)
- [Testing and publication gate](docs/TESTING.md)
- [Build status](release/BUILD_STATUS.md)
- [Change history](CHANGELOG.md)

## License

MIT. See [LICENSE](LICENSE).
