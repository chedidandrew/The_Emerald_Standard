# Build status for 0.3.0-beta.3

## Scope

This beta completes the remaining high-priority safety, lifecycle, identity, and scaling work identified after the Village Prosperity beta.2 audit:

- Conservative Village Bank support/placement verification, stable-center site selection, neighbor-safe rollback, and temporary fallback access with later retry after incomplete unloaded-chunk scans
- Cooperative fail-closed placement vetoes through `VillageDevelopmentProtection.register(PlacementGuard)`
- Persisted project bounds and exponential obstruction retry state
- Dimension-aware, nearby-only physical settlement work under one rotating global block budget
- Account/settlement-aware economic catch-up batches
- Empty/extinct lifecycle, survivor recovery, infected-death, settler confirmation, and spawn-safety fixes
- Persisted full-village no-player-damage counterfactuals, advanced and re-priced daily with genuine non-player casualties while resisting repeat-hit recapture until cooldown and full recovery
- Bounded profession effects and lightweight worker activity cues
- Recent local incident cause and age on the Village dashboard
- Scoped bank routing for Banker replacement, dashboard data, and support/restoration
- Fabric and NeoForge main-hand and zombie-villager-death parity
- Expanded regression, migration, architecture, testing, changelog, and release documentation

Beta.3 writes save format 7. Beta.1 and beta.2 format-6 saves upgrade, but those older builds reject the world after beta.3 saves it rather than silently removing the new counterfactual and construction-safety data. Restore a pre-upgrade backup to downgrade.

## Verification gate

The release workflow must use artifacts from a successful main-branch run of `.github/workflows/build.yml` for the exact release source commit. That run must pass:

- Loader-neutral economy, persistence, and Village Prosperity regression suites
- Fabric 26.2 build and packaged-JAR verification
- NeoForge 26.2 build and packaged-JAR verification
- Fabric and NeoForge dedicated-server startup
- Fabric and NeoForge client bootstrap and screen registration
- Fatal-log checks and artifact upload

The local common suites passed during candidate preparation. Exact tagged-commit CI provenance is intentionally not filled in ahead of the successful run; the release publisher writes `release/ARTIFACTS-<short-sha>.md` with artifact IDs, workflow digests, and release JAR checksums after verifying the source branch and tag.

## Known durability boundaries

- Project progress trusts its persisted template prefix and completion flag. Blocks already counted are not reconciled or rebuilt after chunk rollback or later player removal.
- A bank marker save and the corresponding Minecraft chunk save are not cross-file atomic. In that narrow crash window beta.3 restores an eligible fallback Banker instead of rebuilding a site whose per-block ownership is unknown.

## Remaining beta scope

Automated verification does not replace broad hands-on testing across claim mods with registered guards, modded terrain, unusual village layouts, GUI scales, long multiplayer construction sessions, and very large persistent settlement counts. Village Banks remain intentionally Overworld-only. The release remains a prerelease until those environments receive wider player testing.
