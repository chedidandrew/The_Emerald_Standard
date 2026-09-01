# Build status for 0.1.0-alpha.1

## Verified common core

- Java compilation: PASS
- Gaussian distribution regression: PASS
- Deterministic replay: PASS
- 250-seed x 75-year market and company calibration: PASS
- VILX mean CAGR: 9.74%
- VILX negative-year frequency: 27.2%
- Observed VILX annual range: -60.6% to +124.4%
- All eight individual companies: positive and plausible mean long-run CAGR
- Stressed villager-loan distribution: 8.24% default, 0.96% full default, 59.5% conditional recovery
- Save/reload, backup recovery, rollback, maturity, backward-clock, resource-quote, and no-debt tests: PASS

## Verified loader builds

GitHub Actions run `33455350158` built commit `651c1dcc47403f144d0e456f7375740ed5f47ab7` with Java 25.

- Common regression job: PASS
- Fabric 26.2 Gradle build: PASS
- NeoForge 26.2 Gradle build: PASS
- Fabric workflow artifact upload: PASS
- NeoForge workflow artifact upload: PASS
- Fabric metadata and entrypoint inspection: PASS
- NeoForge metadata and entrypoint inspection: PASS
- Compiled class target: Java 25, class-file major version 69

Artifact names and exact JAR checksums are recorded in `release/ARTIFACTS-651c1dcc.md`.

## Manual publication gate

- Fabric in-game launch test: PENDING
- NeoForge in-game launch test: PENDING
- Deposit, withdrawal, maturity, offline catch-up, and multiplayer gameplay checks: PENDING
- Banker villager, workstation, and graphical interface: PLANNED FOR A LATER ALPHA

The build system and both loader JARs are now real and verified by CI. This remains an alpha until both JARs are launched and the manual Minecraft checklist in `docs/TESTING.md` is completed.
