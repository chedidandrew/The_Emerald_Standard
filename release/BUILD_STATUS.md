# Build status for 0.3.0-beta.2

## Release scope

Beta 2 hardens Village Prosperity around world safety, physical and abstract population consistency, resident lifecycle handling, configuration, and settlement scaling.

Key fixes include:

- Empty villages with visual progression enabled require real settlers before productive population and market influence resume.
- Simulation-only recovery remains available without physical entities.
- Development structures no longer replace the existing terrain surface, player floors, paths, farmland, containers, or rejected protected placements.
- Settler spawning requires real bed capacity and reconciles long-running abstract populations.
- Long-absent residents can emigrate, and zombie-villager infection plus cure reconciliation is tracked without inventing deaths.
- Bell-centered observation, persisted villager identity tags, and tighter proximity reuse reduce village identity drift.
- Functional development tiers can decline after collapse while completed structures remain.
- Food spoilage, maintenance, shortages, and rare local shocks prevent settlement economies from becoming one-way growth ladders.
- Market integration and automatic recovery are independently configurable.
- Village snapshot lists calculate shared fundamentals once instead of recomputing them for every settlement.

## Verified source

Source commit: `6e905e55531780d8463ca0d0ad2b3d1f7df95240`

Pull request: `#3`

Branch verification run: `33635243507`

Merged-main verification run: `33635676343`

The exact merged source passed:

- Common economy, persistence, and Village Prosperity regression tests
- Fabric 26.2 build and packaged-JAR verification
- NeoForge 26.2 build and packaged-JAR verification
- Fabric dedicated-server startup
- NeoForge dedicated-server startup
- Fabric client bootstrap and screen registration
- NeoForge client bootstrap and screen registration
- Fatal startup-log checks and artifact uploads

Exact artifact IDs, workflow artifact digests, and JAR SHA-256 checksums are recorded in `release/ARTIFACTS-6e905e55.md`.

## Publication status

- Source version: `0.3.0-beta.2`
- Main branch: MERGED AND VERIFIED
- Fabric workflow artifact: VERIFIED
- NeoForge workflow artifact: VERIFIED
- Source JARs: VERIFIED
- Public GitHub beta prerelease: NOT YET PUBLISHED

## Remaining beta validation

Automated client bootstrap cannot judge subjective structure appearance, every GUI scale, every claim/protection mod, or long multiplayer behavior. Broader hands-on testing remains appropriate before a stable `1.0`, but there are no known automated regression, compilation, packaged-JAR, dedicated-server-startup, or client-bootstrap failures in beta.2.
