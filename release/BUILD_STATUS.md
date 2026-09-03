# Build status for 0.3.0-beta.3

## Candidate scope

Beta.3 is a prerelease hardening pass for Village Prosperity and Village Banks. Its source version is aligned at `0.3.0-beta.3` for Fabric and NeoForge.

The candidate includes:

- Conservative Village Bank support/preflight, stable-center placement, verified writes, neighbor-safe rollback, and temporary fallback access with later retry after incomplete unloaded-chunk scans
- Cooperative fail-closed `VillageDevelopmentProtection.register(PlacementGuard)` callbacks for bank and prosperity construction
- Persistent project bounds and exponential obstruction retry state
- Empty/extinct settlement, survivor recovery, infected-death, settler-confirmation, and spawn-safety fixes
- Persisted full-village no-player-damage counterfactuals that advance and re-price daily, accept genuine non-player casualties, resist repeat-hit recapture, and remain until cooldown plus full recovery
- Bounded profession-linked output and nearby worker visual cues
- Dimension-aware prosperity census/materialization with nearby snapshot filtering and a rotating global construction budget
- Account/settlement-aware catch-up batching
- Server-synchronized recent local incident news on the Village dashboard
- Scoped bank routing for Banker replacement, dashboard data, and support/restoration
- Main-hand and zombie-villager-death parity across Fabric and NeoForge
- Net-worth overflow and fractional oversell invariant fixes

The candidate writes save format 7. Beta.1 and beta.2 format-6 worlds upgrade, but those older builds deliberately reject a world after beta.3 has saved it. A downgrade requires restoring a pre-upgrade backup; the rejection prevents new safety data from being silently stripped.

## Local verification

The shared loader-neutral working tree passed on 2026-09-02:

- `EconomyRegressionTest`
- `PersistenceRegressionTest`
- `VillageProsperityRegressionTest`

This is preparation evidence, not a claim about the eventual tagged commit or GitHub artifacts.

## Publication gate

The release publisher waits for a successful `main`-push `build.yml` run for the exact source SHA. It downloads that run's Fabric and NeoForge binary/source artifacts, rejects unexpected filenames, verifies that `main` and any existing beta.3 tag still identify that source, and stages the complete release as a draft. The draft's title, notes, prerelease state, filename set, and bytes must all match before publication. Public releases are treated as immutable, release-specific concurrency prevents competing runs, and provenance records workflow artifact IDs and digests plus release-asset SHA-256 checksums.

The exact release commit must pass:

- Loader-neutral economy, persistence, and Village Prosperity regression suites
- Fabric 26.2 build and packaged-JAR verification
- NeoForge 26.2 build and packaged-JAR verification
- Fabric and NeoForge dedicated-server startup
- Fabric and NeoForge client bootstrap and screen registration
- Fatal-log checks and artifact upload

No release source SHA, workflow run, artifact IDs, or checksums are claimed here before that exact run succeeds. The publisher records provenance in `release/ARTIFACTS-<short-sha>.md` after publication.

## Known durability boundaries

- Project materialization trusts the persisted deterministic prefix and completion flag. It does not discover or repair already-counted blocks removed by a player or lost through chunk rollback.
- Economy-file bank markers and Minecraft chunk saves are not one atomic transaction. A crash after the marker save but before the bank's chunk save can leave no structure; beta.3 preserves banking through an eligible fallback Banker and does not guess at or rebuild legacy block ownership.

## Remaining beta validation

Automated verification cannot judge subjective structure appearance, every GUI scale, claim/protection mods that have not registered the cooperative guard, unusual or modded terrain, long multiplayer construction sessions, or very large persistent worlds. Village Banks also remain intentionally Overworld-only. Those hands-on checks remain appropriate before a stable release.
