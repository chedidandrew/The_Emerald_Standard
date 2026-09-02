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
- Inventory transaction-journal lifecycle
- Village Prosperity simulation and project approval
- Independent simulation, visual, market-integration, and automatic-recovery behavior
- Physical-first extinction recovery
- Simulation-only abstract recovery
- Market-influence bounds
- Stable village identity persistence and preferred resident-tag identity
- Resident Away to Emigrated transitions
- Infection idempotence and cure reconciliation
- Physical-mode population growth waiting for materialized settlers
- Functional development-tier decline after collapse

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
- Verify all five pages and all GUI scales.
- Exercise deposit, withdrawal, savings, buy/sell, CD, lending, exchange, Village support, and recovery.
- Verify risky actions require confirmation.
- Test full-inventory and interrupted transaction recovery.

### Village identity and lifecycle

- Test two villages closer than 100 blocks and confirm identities do not merge.
- Move tagged villagers around and confirm their original settlement remains stable unless intentionally resettled.
- Kill residents with pillagers, zombies, direct player attacks, and player-owned projectiles.
- Confirm player-caused extinction becomes Abandoned and does not manipulate market fundamentals.
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

Inspect Cottage, Warehouse, and Mine Entrance in all vanilla village biomes and difficult terrain.

- Confirm construction never replaces village paths, farmland, containers, player floors, or existing buildings.
- Confirm only naturally surfaced flat lots are accepted.
- Place a solid block in a reserved project area before construction and confirm the project stops safely.
- Block a project before its first placement and confirm the site can be released for another lot.
- Verify failed placements from a protection or claim mod are not counted as progress.
- Confirm Cottage beds are usable and Warehouse storage does not create fisherman workstations.

### Multiplayer and performance

- Connect at least two players and verify shared market state with UUID-isolated accounts.
- Interact with different banks simultaneously.
- Verify permission level 2 is still required for `/emerald` commands.
- Test dozens to hundreds of known settlement records and profile census, catch-up, materialization, and save time.

## Publication gate

A public beta prerelease requires:

1. Green CI for the exact release commit.
2. Both packaged client JARs loading worlds.
3. Complete banking and Village page transactions on both loaders.
4. Manual construction-safety review in every supported vanilla village biome.
5. Live hostile-extinction, restoration, infection/cure, and emigration tests.
6. Multiplayer account and village-identity checks.
7. Recorded artifact IDs and SHA-256 checksums for the chosen source commit.
