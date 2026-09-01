# Build status for 0.1.0-alpha.2

## Locally verified common core

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

## CI verification pending

The alpha.2 branch must pass:

- Common regression job
- Fabric 26.2 Gradle build
- NeoForge 26.2 Gradle build
- Fabric dedicated-server launch smoke test
- NeoForge dedicated-server launch smoke test
- Fabric artifact upload
- NeoForge artifact upload

## Manual publication gate

- Fabric client launch test: PENDING
- NeoForge client launch test: PENDING
- Packaged dedicated-server launch test: PENDING
- Deposit, withdrawal, journal recovery, maturity, partial-day, offline catch-up, and multiplayer gameplay checks: PENDING
- Banker villager, workstation, and graphical interface: PLANNED FOR A LATER ALPHA

No formal public GitHub release should be created until CI is green and both client JARs complete the manual checklist in `docs/TESTING.md`.
