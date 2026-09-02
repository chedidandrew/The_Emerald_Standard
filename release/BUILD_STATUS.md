# Build status for 0.3.0-beta.2

## Candidate scope

Beta 2 hardens Village Prosperity around world safety, population consistency, lifecycle behavior, configuration, and scaling.

Key fixes include:

- Physical settlers are required before an empty visually simulated village resumes productive abstract population.
- Simulation-only recovery remains supported without physical entities.
- Village development no longer replaces the existing terrain surface and failed block placements are not counted as successful work.
- Settler spawning requires available real beds and reconciles long-running abstract populations.
- Long-absent residents emigrate and zombie-villager residents can be marked Infected.
- Bell-centered observation and tighter identity reuse reduce village identity drift.
- Functional development tiers may decline after collapse.
- Village supplies and treasury now include upkeep, spoilage, shortages, and rare local shocks.
- Market integration and automatic recovery are independently configurable.
- Snapshot fundamentals are calculated once per village-list read instead of once per village.

## Verification

The branch includes expanded Village Prosperity regression coverage for:

- Physical-first recovery
- Simulation-only recovery
- Automatic-recovery disablement
- Market-integration disablement
- Resident emigration
- Infection and cure reconciliation
- Stable identity preference and persistence
- Functional tier decline after extinction
- Existing project, lifecycle, market-cap, and no-debt invariants

The beta.2 source has passed the loader-neutral regression suite. A full Fabric and NeoForge GitHub Actions build, packaged-JAR verification, dedicated-server launch, and client-bootstrap run is the merge gate for this candidate.

## Publication status

- Source version: `0.3.0-beta.2`
- Fabric artifact: pending verified workflow
- NeoForge artifact: pending verified workflow
- Public GitHub prerelease: pending verified workflow
