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
- Format-7 to format-8 project-catalog migration and format-8 to format-9 migration for multiple term positions, portfolio accounting, commodity and personal history, Prosperity Funds, and donor records
- Legacy CD and lending promotion plus explicitly inferred basis for old holdings without execution history
- Share cost basis, average purchase price, realized and unrealized gain, allocation, contributions, withdrawals, bounded transaction ledger, and five-year net-worth history
- Five-year asset, commodity, and personal history retention plus bounded chart-sampling inputs
- Up to eight independently identified CDs and eight villager-lending positions, including position-specific close and collect behavior
- Direct Grants, protected-principal Endowments, Project Sponsorships, all seven purposes, emergency reserves, bounded spending, and donor recognition
- Inventory transaction-journal lifecycle, persisted-inventory comparison, full and partial
  inventory delivery accounting, exact undelivered-withdrawal refunds, and durable journal removal
  before later inventory changes are allowed
- Village Prosperity simulation and project approval
- Independent simulation, visual, market-integration, and automatic-recovery behavior
- Physical-first extinction recovery
- Simulation-only abstract recovery
- Market-influence bounds plus player-damage counterfactual persistence, repeat-hit isolation, and gated release
- Stable village identity persistence and preferred resident-tag identity
- Resident Away to Emigrated transitions
- Infection idempotence and cure reconciliation
- Committed settlers contributing off-screen, then transferring to physical population without double counting when observed
- Functional development-tier decline after collapse
- Empty-village discovery remaining Abandoned without invented settlers
- Bounded profession specialization and isolation between unrelated sectors
- Infected-resident death without a second productive-population decrement
- Persistent project bounds, exponential retry deadlines, and restart eligibility
- Economic project benefits remaining active while visual materialization is deferred, plus demotion after integrity loss, persistent manual-repair gating, and restoration after the authored block is replaced
- Dimension-filtered nearby village snapshots and account/settlement-aware catch-up batches
- Spatial-index equivalence, cross-dimension isolation, deterministic ties, measured query/save/load
  work at 100, 500, and 1,000 villages and accounts, and mature replacement-save timing
- No-op bank association persistence, net-worth overflow safety, and exact oversell rejection
- Debug capture ownership, watched-village filtering, privacy redaction, timeline limits, and separated timing categories
- Signed-short packing and reassembly round trips for full-width balances, gains, histories, position IDs, and Fund drafts
- Fixed dashboard geometry, Market-carousel mappings, non-overlapping hover regions, exact-value hover coverage, and state-aware CD/Fund tooltip policies

## Automated loader verification

GitHub Actions must pass for the exact candidate commit:

- Common regression tests
- Fabric 26.2 build and packaged-JAR validation
- NeoForge 26.2 build and packaged-JAR validation
- Fabric dedicated-server launch
- NeoForge dedicated-server launch
- Live registration and invariant checks for Exchange Desk creative visibility, all four facings, shape and POI mapping, managed Banker job-site retention, and Bank/project templates
- Real Minecraft container-data packet round trips for Banker full-width values
- Fabric client bootstrap under a virtual display
- NeoForge client bootstrap under a virtual display
- Screen-registration and fatal-log checks
- Artifact upload for both loaders and smoke logs

## Manual beta checklist

Use the repository's [manual beta test form](https://github.com/chedidandrew/The_Emerald_Standard/issues/new?template=manual_beta_test.yml) for exact-commit evidence. Select one test area per report; attach the `/emerald debug` ZIP for every failure or ambiguous outcome. The canonical coverage table remains [MANUAL_TEST_MATRIX-0.4.md](MANUAL_TEST_MATRIX-0.4.md).

Automated startup proves API compatibility and initialization. It cannot prove every physical-world edge case.

### Banking and GUI

On both Fabric and NeoForge:

- Open a new world and an upgraded existing world.
- Open the dashboard through a Banker, a new Exchange Desk, and an upgraded legacy bank lectern.
- Confirm the Village page shows the latest local incident cause and age.
- Verify all seven pages and all GUI scales, including empty, partial, and full Activity ledgers. Use both the mouse wheel and visible arrows, confirm the `1–5 / 256`-style position updates, and verify both controls disable at their respective bounds.
- Confirm the Market investment carousel wraps correctly in both directions, its selected ticker matches the chart and trade actions, and every long label remains within the 320x230 dashboard at each GUI scale.
- Hover the selected investment, performance summary, charts, Exchange panel, Banking products, Village panels, Fund controls, and Activity footer; confirm details appear without obscuring the inspected value. In particular, compare each abbreviated Overview balance with its exact two-decimal tooltip, verify the Village mode, local impact, restoration/news, and output rows have separate hover regions, and check early versus mature Close CD text plus every disabled Fund-purpose reason.
- Open Creative inventory on both loaders and confirm Exchange Desk appears under Functional Blocks and when searching for `desk` or `exchange`. Place it while facing all four directions and confirm its orientation and 13.5/16-block selection/collision height match the model.
- Confirm the first-join discovery hint appears once, does not repeat after reconnecting, and is suppressed when `onboarding.join_hint_enabled=false` before that player's first join.
- Exercise deposit, withdrawal, savings, buy/sell, eight simultaneous CDs, eight simultaneous lending positions, exchange, all Prosperity Fund types and purposes, and recovery.
- Verify a specific CD and lending position can be selected, closed, or collected without changing another position.
- Compare basis, average purchase price, realized/unrealized gain, allocation, contributions, ledger entries, and personal net worth against a hand-calculated transaction sequence.
- Compare the displayed and authoritative values after depositing 1, 10, and 100 emeralds and after assembling a Fund draft above 32,767 emeralds. Close and reopen the menu, then reconnect and reopen it; confirm no signed-short truncation after either resynchronization.
- Verify market, commodity, and personal charts switch among 30 days, 90 days, one year, and all retained history.
- Verify risky and irreversible actions require two matching packets inside the server-owned confirmation window; changing selection or waiting for expiry must cancel confirmation.
- Verify Fund Apply, Cancel, Enter, and `All` update only the server-owned applied contribution amount, accept exact values such as `3000`, and never overdraw Bank Cash.
- Test full and partially full inventories in both Survival and Creative. A withdrawal must debit
  only the emeralds actually inserted, refund every item that did not fit, and preserve net worth;
  then test interrupted transaction recovery.

### Configuration

- Confirm `/emerald config show` reports the normalized world-local configuration path and every active setting.
- Reload valid settings and confirm they apply together without restarting. For `village_prosperity.development_radius`, confirm `48`, `256`, and `512` are accepted.
- In separate attempts, use an invalid boolean, a non-integer, development radii of `47` and `513`, and a misspelled key. Each reload must identify the problem and leave the complete prior configuration active.
- Disable each Prosperity Fund subtype after recording existing balances and history. Confirm new contributions of that type are blocked without deleting prior village-owned state.

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
- Keep a known non-empty village outside every player's activation radius, advance economic days, and confirm committed settlers still affect its bounded economy while physical population and chunks remain unchanged. Return with its chunks normally loaded and confirm each census transfer preserves the committed total.

### Physical-development activation

- With the default configuration, confirm `/emerald config show` reports `village_prosperity.development_radius=256`.
- Place a known village within 256 blocks in X/Z but far above or below the player. Keep its chunks loaded and confirm it remains eligible; then move just beyond the horizontal boundary and confirm it does not. Repeat at the accepted 48- and 512-block limits.
- Keep a known village horizontally eligible but unload every chunk containing its pending settler, audit target, and construction footprint. Confirm the mod neither loads those chunks nor performs the world action; load them normally and confirm a later pass can proceed.
- Prepare 17 loaded, eligible known villages in one dimension. Confirm no more than 16 are considered during one construction pass and that rotation gives the omitted village an opportunity on a later pass.
- At a 512-block development radius, confirm settler and construction-theatre entity searches still stop at their local 48-block cap, spawned settlers receive no more than a 32-block home radius, no more than two workers receive one-shot movement cues, and no persistent AI goal is installed.

### Construction safety

Inspect Village Banks and all ten prosperity project types in all vanilla village biomes and difficult terrain.

- Confirm construction never replaces village paths, farmland, containers, player floors, or existing buildings.
- Confirm only naturally surfaced flat lots are accepted.
- Confirm a new bank floor is above the old surface, failed placement is not marked generated, and two villages in one legacy grid do not share a Banker identity. Move the discovering player within the village and confirm the persisted settlement center—not the player position—keeps Bank selection stable.
- Confirm mud and thin snow are rejected as Bank support, while an eligible full snow-block surface remains valid.
- Place a solid block in a reserved project area before construction and confirm the project stops safely.
- Block a project before its first placement and confirm it retries later on another lot without tight-loop scanning.
- Let a reserved project's boundary chunk unload and confirm the reservation survives the delayed retry instead of being released on incomplete world information.
- Block a partially built project and confirm its exact footprint survives restart and resumes without clearing intervening player blocks.
- Complete a project's economic plan while its chunks are unloaded and confirm its housing or production benefit applies without loading or changing those chunks. Then load the area normally and confirm the queued template materializes under the ordinary budget.
- Remove an authored block from a completed project and confirm its benefits suspend when the integrity audit finds it. Confirm the block is not regenerated, including after restart, then replace the authored block and confirm a later audit restores benefits.
- Put a different solid player block in the missing position and unload the chunk in separate runs; confirm the audit neither overwrites nor force-loads it. Confirm safe append-only upgrades still honor protection vetoes.
- Register a test guard with `VillageDevelopmentProtection.register(PlacementGuard)` and verify vetoes and thrown guard errors are not counted as progress for both banks and prosperity projects.
- Exercise the documented bank marker/chunk-save crash window in a disposable world; confirm the mod falls back to Banker access instead of rebuilding unknown blocks.
- With a low view distance, leave all Bank candidates partly unloaded and confirm fallback access is available without permanently marking generation; load the area and confirm a later scan can retry.
- Inspect each new Bank's connected, sealed roof, sheltered entrance, interior furnishings, lighting, and storage. Confirm it contains exactly one Exchange Desk, no barrels, and no other villager job site.
- Confirm residential entrances remain clear and beds are usable. Census every prosperity template's theme-appropriate job sites; confirm no template contains barrels, lecterns, cartography tables, or currency blocks, and an Exchange Hall contains exactly one Exchange Desk.
- Load a completed project from an older blueprint. With every appended position safe, confirm the richer suffix enters guarded construction and the persisted bounds expand with it. Repeat with a solid player block, block entity, or protection veto in one suffix position and confirm the older project stays operational and untouched.
- Confirm new Bankers use the registered Banker profession, claim the Exchange Desk POI, and retain scoped bank identity without gaining an unintended trade set.
- Observe a newly assigned Banker for at least one full in-game work period and after save/reload; confirm its Banker appearance and Exchange Desk job-site memory remain stable rather than flashing for one frame.
- Observe active construction and confirm no more than two suitable residents receive occasional low-speed movement/particle cues; confirm those cues stop when construction is idle and do not determine progress.

### Multiplayer and performance

- Connect at least two players and verify shared market state with UUID-isolated accounts.
- Interact with different banks simultaneously.
- With two nearby scoped banks, confirm replacement eligibility, Village-page data, and Prosperity Fund/restoration contributions all target the bank's associated settlement rather than the nearest unrelated record.
- Visit tracked settlements in multiple dimensions and confirm records, dashboard lookup, and construction stay dimension-local. Confirm Village Bank structures remain Overworld-only.
- Verify permission level 2 is still required for `/emerald` commands.
- Test dozens to hundreds of known settlement and account records and profile indexed lookup, census, catch-up, materialization, full-state save, and load time.
- While one operator owns `/emerald debug`, confirm another operator cannot mark, toggle, or stop the capture. Verify the ZIP contains only the initiating account and one watched village, omits resident UUIDs, and labels overlapping timing categories separately.

## Publication gate

Any 0.4 beta prerelease publisher must require a successful `main`-push `build.yml` run for the exact source commit, download rather than rebuild that run's exact Fabric and NeoForge binary/source artifacts, verify the complete public filename set and bytes, and record artifact IDs, workflow digests, and release-asset SHA-256 checksums.

Each loader artifact now includes a CI-generated `SHA256SUMS`. Use `scripts/prepare-release-assets.sh` from the exact clean source commit to verify both downloaded artifacts and produce one combined checksum file and release manifest. The complete step-by-step gate is in [RELEASING.md](RELEASING.md).

The manual checklist above remains required evidence before promoting the mod to a stable release. Automated startup cannot certify subjective structure appearance, third-party claim integrations, every GUI scale, long multiplayer behavior, project-block reconciliation after chunk rollback, or cross-file bank-marker/chunk atomicity; the 0.4 line remains a beta while that wider validation continues.

`scripts/smoke-server.sh <fabric|neoforge>` uses a fresh, uniquely named directory under
`build/server-smoke` for every run. It never clears either loader's normal `run` directory or a
playtest world, and its Windows cleanup targets only Java descendants carrying that run's unique
smoke marker.


## One-command diagnostic capture

For hands-on testing, run `/emerald debug`, reproduce the issue for up to five minutes, and run the same command again or let it expire. Use `/emerald debug mark` when a specific moment should be easy to find. Attach the generated `TES-debug-*.zip` from the world's `data/the_emerald_standard_debug` directory to the bug report and mention any marker numbers.

The capture should be used for GUI transactions, village lifecycle tests, construction placement, raid recovery, settler behavior, market progression, and persistence recovery. Confirm that the final `validation.txt` contains no unexplained failures and that no private economy seed or unrelated player account is present.
