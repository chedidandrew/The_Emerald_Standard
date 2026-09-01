# The Emerald Standard

[![Build and verify](https://github.com/chedidandrew/The_Emerald_Standard/actions/workflows/build.yml/badge.svg)](https://github.com/chedidandrew/The_Emerald_Standard/actions/workflows/build.yml)

A lightweight passive-investing and villager-economy mod for **Minecraft 26.2**, with separate Fabric and NeoForge builds.

> Current status: `0.1.0-alpha.1`. The economy, persistence, investments, and command interface are implemented for testing. The Banker villager, workstation, charts, and full graphical interface are the next gameplay milestone.

## Core rule

Players provide emerald capital to the villager economy. **Players can never borrow emeralds, hold a negative balance, or enter debt.** A villager business investment can lose some or all of the amount voluntarily invested, but it can never create an additional obligation.

## Implemented in the alpha

- A world-wide Villager Exchange with expansion, bull, boom, stagnation, recession, crash, and recovery regimes.
- VILX, the diversified Villager Exchange Index, plus eight Minecraft-themed companies.
- Corrected deterministic return generation with correlated market and company risk.
- Savings with variable economic rates averaging near 3% over long simulations.
- CDs with 30, 90, 180, and 365-day terms, locked opening rates, maturity stops, and an early withdrawal penalty.
- Player-funded villager business loans with interest, partial default, and full default outcomes.
- Dynamic diamond, gold, netherite, and emerald-ore exchange markets.
- Fractional investing and a small bid/ask spread to prevent cost-free rapid trading.
- Offline progression at one economic day per Minecraft day, or 20 real minutes while the world is closed.
- A private economy seed that is saved independently from the visible Minecraft world seed.
- Atomic saves, backup recovery, mutation rollback, and UUID-isolated accounts.
- Fabric and NeoForge projects with automated GitHub Actions builds.

## Alpha command interface

Use `/emerald help` in-game.

```text
/emerald market
/emerald commodities
/emerald portfolio
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

## Project layout

```text
common/    Shared economy, persistence, command interface, and tests
fabric/    Fabric 26.2 lifecycle hook, metadata, and Gradle build
neoforge/  NeoForge 26.2 lifecycle hook, metadata, and Gradle build
docs/      Economy, architecture, and testing documentation
scripts/   Reproducible common-core regression test runner
```

## Building

Minecraft 26.2 requires Java 25. Gradle is provisioned automatically in GitHub Actions.

```bash
bash scripts/run-common-tests.sh
gradle --no-daemon -p fabric build
gradle --no-daemon -p neoforge build
```

Successful CI runs publish Fabric and NeoForge JARs as workflow artifacts. Do not publish a public release until both loader jobs are green and each JAR has been launched in Minecraft 26.2.

## Documentation

- [Economy model](docs/ECONOMY.md)
- [Architecture and persistence](docs/ARCHITECTURE.md)
- [Testing and publication gate](docs/TESTING.md)
- [Change history](CHANGELOG.md)

## License

MIT. See [LICENSE](LICENSE).
