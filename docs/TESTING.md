# Testing and publication gate

## Automated common regression suite

Run:

```bash
bash scripts/run-common-tests.sh
```

The suite verifies:

- Gaussian distribution, deterministic replay, market calibration, regimes, events, and commodities
- Villager-lending defaults and expected returns by term
- Savings, CD, and lending maturity
- Unified wall-clock/game-tick progression and bounded catch-up
- Save migration, checksums, backup recovery, rollback, retry backoff, and no-debt invariants
- Genuine format-5 account/bank-anchor migration, beta.1/beta.2 format-6 upgrade, safe defaults for older project records, and rejection of future formats without stale-backup fallback
- Inventory transaction-journal lifecycle
- Village Prosperity simulation and project approval
- Independent simulation, visual, market-integration, and automatic-recovery behavior
- Physical-first extinction recovery
- Simulation-only abstract recovery
- Market-influence bounds plus player-damage counterfactual persistence, repeat-hit isolation, and gated release
- Stable village identity persistence and preferred resident-tag identity
- Resident Away to Emigrated transitions
- Infection idempotence and cure reconciliation
- Physical-mode population growth waiting for materialized settlers
- Functional development-tier decline after collapse
- Empty-village discovery remaining Abandoned without invented settlers
- Bounded profession specialization and isolation between unrelated sectors
- Infected-resident death without a second productive-population decrement
- Persistent project bounds, exponential retry deadlines, and restart eligibility
- Dimension-filtered nearby village snapshots and account/settlement-aware catch-up batches
- No-op bank association persistence, net-worth overflow safety, and exact oversell rejection

## Automated loader verification

GitHub Actions must pass for the exact candidate commit:

- Common regression tests
- Fabric 26.2 build and packaged-JAR validation
- NeoForge 26.2 build and packaged-JAR validation
- Fabric dedicated-server launch
- NeoForge dedicated-server launch
- Live Banker invariant checks
- Fabric client bootstrap under a virtual display
- NeoForge client bootstrap under a virtual display
- Screen-registration and fatal-log checks
- Artifact upload for both loaders and smoke logs

## Manual beta checklist

Automated startup proves API compatibility and initialization. It cannot prove every physical-world edge case.

### Banking and GUI

On both Fabric and NeoForge:

- Open a new world and an upgraded existing world.
- Open the dashboard through both a Banker and bank lectern.
- Confirm the Village page shows the latest local incident cause and age.
- Verify all five pages and all GUI scales.
- Exercise deposit, withdrawal, savings, buy/sell, CD, lending, exchange, Village support, and recovery.
- Verify risky actions require confirmation.
- Test full-inventory and interrupted transaction recovery.

### Village identity and lifecycle

- Test two villages closer than 100 blocks and confirm identities do not merge.
- Move tagged villagers around and confirm their original settlement remains stable unless intentionally resettled.
- Kill residents with pillagers, zombies, direct player attacks, and player-owned projectiles.
- Confirm the first player-caused casualty captures the village's exact pre-damage state and contribution, including across save/reload.
- Advance economic days with Village Prosperity simulation enabled and confirm the no-player-damage branch simulates and re-prices rather than holding a static score. Record a genuine non-player casualty and confirm it also changes the counterfactual.
- Cause additional player casualties and confirm they do not recapture or rebase the branch. Confirm release requires both cooldown expiry and full live-village recovery.
- Confirm player-caused extinction becomes Abandoned and does not automatically replenish victims.
- Convert a villager to a zombie villager, confirm productive population pauses, cure it, and confirm population reconciles once.
- Move a resident away for more than the emigration window and confirm it stops contributing without being recorded dead.
- Confirm a dead or extinct Banker never deletes player financial data.

### Recovery and physical population

- Wipe a village with hostile mobs and allow the recovery cooldown to expire.
- Confirm the abstract population remains zero until a real settler spawns when visual progression is enabled.
- Confirm settlers do not spawn with nearby threats or without a free real bed.
- Disable automatic recovery and confirm extinction persists.
- Run simulation with visuals disabled for a long period, then re-enable visuals and verify physical population converges gradually.

### Construction safety

Inspect Village Banks, Cottage, Warehouse, and Mine Entrance in all vanilla village biomes and difficult terrain.

- Confirm construction never replaces village paths, farmland, containers, player floors, or existing buildings.
- Confirm only naturally surfaced flat lots are accepted.
- Confirm a new bank floor is above the old surface, failed placement is not marked generated, and two villages in one legacy grid do not share a Banker identity. Move the discovering player within the village and confirm the persisted settlement center—not the player position—keeps Bank selection stable.
- Confirm mud and thin snow are rejected as Bank support, while an eligible full snow-block surface remains valid.
- Place a solid block in a reserved project area before construction and confirm the project stops safely.
- Block a project before its first placement and confirm it retries later on another lot without tight-loop scanning.
- Let a reserved project's boundary chunk unload and confirm the reservation survives the delayed retry instead of being released on incomplete world information.
- Block a partially built project and confirm its exact footprint survives restart and resumes without clearing intervening player blocks.
- Remove an already-counted project block or restore an older chunk and confirm the known beta behavior is understood: persisted progress is trusted and the missing block is not auto-repaired.
- Register a test guard with `VillageDevelopmentProtection.register(PlacementGuard)` and verify vetoes and thrown guard errors are not counted as progress for both banks and prosperity projects.
- Exercise the documented bank marker/chunk-save crash window in a disposable world; confirm the mod falls back to Banker access instead of rebuilding unknown blocks.
- With a low view distance, leave all Bank candidates partly unloaded and confirm fallback access is available without permanently marking generation; load the area and confirm a later scan can retry.
- Confirm Cottage beds are usable and Warehouse storage does not create fisherman workstations.

### Multiplayer and performance

- Connect at least two players and verify shared market state with UUID-isolated accounts.
- Interact with different banks simultaneously.
- With two nearby scoped banks, confirm replacement eligibility, Village-page data, and support/restoration all target the bank's associated settlement rather than the nearest unrelated record.
- Visit tracked settlements in multiple dimensions and confirm records, dashboard lookup, and construction stay dimension-local. Confirm Village Bank structures remain Overworld-only.
- Verify permission level 2 is still required for `/emerald` commands.
- Test dozens to hundreds of known settlement records and profile census, catch-up, materialization, and save time.

## Publication gate

The beta.3 prerelease publisher requires a successful `main`-push `build.yml` run for the exact source commit, downloads rather than rebuilds that run's exact Fabric and NeoForge binary/source artifacts, stages them in a draft, verifies the complete public filename set and bytes, and records artifact IDs, workflow digests, and release-asset SHA-256 checksums.

The manual checklist above remains required evidence before promoting the mod to a stable release. Automated startup cannot certify subjective structure appearance, third-party claim integrations, every GUI scale, long multiplayer behavior, project-block reconciliation after chunk rollback, or cross-file bank-marker/chunk atomicity; beta.3 is intentionally published as a prerelease while that wider validation continues.
