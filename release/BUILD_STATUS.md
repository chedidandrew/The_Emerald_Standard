# Build status for 0.1.0-alpha.1

## Locally verified common core

- Java source compilation: PASS on Java 21 compatibility mode
- Gaussian distribution regression: PASS
- Deterministic replay: PASS
- 250-seed x 75-year market and company calibration: PASS
- VILX mean CAGR: 9.74%
- VILX negative-year frequency: 27.2%
- Observed VILX annual range: -60.6% to +124.4%
- All eight individual companies: positive and plausible mean long-run CAGR
- Stressed villager-loan distribution: 8.24% default, 0.96% full default, 59.5% conditional recovery
- Save/reload, backup recovery, rollback, maturity, backward-clock, and no-debt tests: PASS

## Loader publication gate

- Fabric Minecraft 26.2 build: PENDING new CI run
- NeoForge Minecraft 26.2 build: PENDING new CI run
- Fabric in-game launch test: PENDING
- NeoForge in-game launch test: PENDING

The initial repository CI failure was traced to an explicit Fabric Mojang-mappings declaration that is not used by the official Minecraft 26.2 Fabric template. The build configuration has been aligned with the official template. No loader JAR should be published until the new CI run is green and both JARs launch in Minecraft 26.2.
