# Beta 64: forced-mode street-lamp batches

## Diagnosis and implementation

The pictured post/knee/arm/chain/lantern assembly matches AuthoredVillageStructures.walkwayLamp. Optional roadside lighting used a shared one-second admission hold and a two-write limit even while forced instant development was enabled. VillageProsperityManager also capped its lighting grant at two blocks regardless of development mode. A ten-cell lamp therefore needed a survey visit followed by several seconds of incremental placement.

Forced mode now admits roadside lighting once per game tick per dimension and passes the remaining shared road/construction allowance, after connector writes, into the lamp queue. The same path handles completed ordinary building routes and Bank routes. WalkwayLighting preserves its normal single-step behavior outside forced mode. In forced mode it performs up to 32 bounded station/placement steps, checks the shared cooperative deadline between steps, and stops immediately if an unloaded or occupied job makes no progress. A successful preflight saves its exact reservation before placing the tiny support-first assembly in the same visit. Existing pending lamps can finish their remaining cells in that visit too.

There is no new private unlimited placement budget. Written blocks are deducted from the existing forced grant. Each lamp plan is already bounded to at most 16 cells (the current design uses ten); a small remaining allowance can still split an assembly. One admitted tiny assembly can finish after its preflight before yielding to an expired cooperative deadline, so slow preflight cannot permanently starve it. These native calls are not preemptible and do not promise a hard tick-time bound. Other stations, jobs and districts resume on later admitted visits. The route must already be ready for lighting. This removes deliberate normal-mode delays, not safety or work admission.

Disabling forced mode immediately restores the one-second dimension-wide timer and two-write limit without changing saved lamp progress. When development is not running, its dispatcher does not perform these lamp batches. Geometry, spacing, palette selection, shared-route receipts and the persistent save format are unchanged.

## Safety and compatibility

All original preflight and pending-assembly checks remain in the shared placement path: loaded neighboring columns, clear road passage, natural footing, intact supplied supports, block entities/inventories, fluids, construction reservations, lot/Bank setbacks, protection vetoes and living-occupant clearance. No chunks are force-loaded, terrain excavated or occupants moved. Native supports are placed before the hanging light. Existing alterations are preserved. One-shot receipts still prevent duplication or regeneration after harvesting or reload; speed does not create repair rights.

## Coverage and results

- Full common regression suite passed, including forced shared-budget wiring, both handbook forms and loader version parity.
- Fabric dedicated-server lighting suite passed: build/server-smoke/beta64-fabric-lighting.log.
- NeoForge dedicated-server Bank walkway suite passed: build/server-smoke/beta64-neoforge-bank-walkways.log. This includes real Bank construction/backfill, route endpoints, reload, protection, roads, vegetation, terrain and bridges, plus all lighting tests.
- ForcedWalkwayLightingSelfTest exercises the production ordinary/Bank-shared lamp dispatcher with an old partial lamp; immediate forced admission without duplicate same-tick admission; three complete spaced lights; deterministic multi-station draining; three-write and expired-deadline limits; disabling forced mode mid-job; SavedData round trips; no harvested-light regeneration; claims, occupancy/safe retry and remote unloaded columns.
- Existing WalkwayLightingSelfTest passes its 80 biome/character/orientation plans, five styled road surfaces, normal incremental backfill/budgets, native hanging survival, clear passage, spacing/shared roads, protection, stored items, water, lots and damaged supports.
- Full Fabric build passed in 5m 40s, including native architecture, vanilla template, Bank and UI checks.
- Full NeoForge build passed in 4m 24s, including tests, native architecture, vanilla template and UI checks.
- Both packaged JARs match the current canonical source identity and all 1,875 exact native architecture asset hashes. Metadata/source/resource package checks and git diff --check passed.

Initial smoke fixture failures are retained in beta64-fabric-fixture-initial-failed.log, beta64-neoforge-fixture-initial-failed.log, beta64-fabric-fixture-version-failed.log and beta64-fabric-fixture-reload-failed.log. The fixture needed a real initialized economy, a completed connector identity rather than an unspecified historical blueprint, and an explicit admission after codec reload because pacing timers are intentionally session-only. Assertions were corrected to exercise valid production state; protection/occupancy/budget assertions were not weakened. No in-game visual UI inspection is claimed. Windows OSHI/Perflib warnings and upstream deprecations are environment/toolchain warnings. The smoke harness terminates its marked test server after the integration success marker.

## Handbook and recipes

The long planning/building section explains the ordinary timer, forced immediate support-first batches, shared limits, route readiness, loaded/clear/protected requirements, persisted no-regeneration and restoring normal pace on disable. The compact planning/building page adds “Forced: lamp bursts.” Both have regression coverage. Existing chapter/lectern routing already includes these sections. Recipes were reviewed: this reuses vanilla lamp parts and adds no registered content, so existing recipes and loot guidance remain unchanged.

## Artifacts

Binary/source JAR pairs and SHA256SUMS are staged under build/release-beta64/fabric and build/release-beta64/neoforge. No profile or existing world was modified; no commit, push, merge or pull request was made. The waterfront terrain-planning suggestions from the preceding discussion remain recommendations, not changes in this release.

- Source SHA-256: 058d7fdb579fc96b8502a7ec52d54ce460f41e453e12f6614a6e60a5715d6320.
- Fabric binary SHA-256: 6aa1591456074aa4690ab39b4a455d08596d9f6e4251d30aff449c90cd102de1.
- NeoForge binary SHA-256: 069224f28d975e79ccbc680b9db1edda75e9677106a841613080e244d40bb77c.
