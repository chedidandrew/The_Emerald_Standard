# Build status for 0.1.0-alpha.2

## Verified common core

- Java compilation: PASS
- Gaussian distribution regression: PASS
- Deterministic replay: PASS
- 250-seed x 75-year market and company calibration: PASS
- VILX mean CAGR: 9.74%
- VILX negative-year frequency: 27.2%
- Observed VILX annual range: -60.6% to +124.4%
- All eight individual companies: positive and plausible mean long-run CAGR
- Stressed villager-lending distribution: 8.24% default, 0.96% full default, 59.5% conditional recovery
- Expected annualized returns after defaults: 30-day 6.65%, 90-day 7.46%, 180-day 7.97%, 365-day 12.16%
- Save/reload, partial-day, bounded catch-up, journal, backup, rollback, maturity, backoff, clock, spread, resource-quote, future-format, and no-debt tests: PASS
- Fabric and NeoForge version parity: PASS at `0.1.0-alpha.2`

## Verified CI and dedicated-server startup

GitHub Actions run `33461667400` built and launched source commit `09307b1397ff7ef33d27b52503d963c51bd2fc66` using Java 25.

- Common regression job: PASS
- Fabric 26.2 Gradle build: PASS
- NeoForge 26.2 Gradle build: PASS
- Fabric artifact upload: PASS
- NeoForge artifact upload: PASS
- Fabric dedicated-server development launch: PASS
- NeoForge dedicated-server development launch: PASS
- Fabric mod startup message observed: PASS
- NeoForge mod startup message observed: PASS
- Minecraft server-ready message observed for both loaders: PASS

Artifact IDs, ZIP digests, and exact JAR checksums are recorded in `release/ARTIFACTS-09307b13.md`.

## Manual publication gate

- Fabric client launch test: PENDING
- NeoForge client launch test: PENDING
- Packaged dedicated-server launch test outside the development environment: PENDING
- Deposit, withdrawal, journal recovery, maturity, partial-day, offline catch-up, and multiplayer gameplay checks: PENDING
- Banker villager, workstation, and graphical interface: PLANNED FOR A LATER ALPHA

The source, tests, loader builds, and automated dedicated-server startup checks are green. No formal public GitHub release should be created until both client JARs complete the manual checklist in `docs/TESTING.md`.
