# Beta 65: terrain placement, rare yards and bridge-first expansion

## Evidence and diagnosis

The user's beta63 debug archive TES-debug-20261005-034047-E2DAAA35.zip was inspected read-only. Its 215.7-second capture contains 16,267 events, 2,822 plot searches (mean 5.52 ms; 15.58 seconds aggregate), 1,052,691 survey misses and 11,535 hits. TPS was mostly 20; mean MSPT was about 15 and p95 about 24.4. These figures identify repetitive search work, not proof of a universal server lag problem. No before/after live-world timing benchmark is claimed.

North Cedarbury Crossing's project 3, MINE_ENTRANCE, froze at cursor 1,338 of 1,728 operations. The expected east-west rail at (-1094,78,749) was actually ascending_west in the saved region. Its west neighbor is legitimately one block higher. Offline region inspection used scripts/inspect-region-cell.py without writing the save. The construction verifier rejected the native neighbor-derived slope indefinitely. Rail verification now accepts a surviving dry rail of the same block type after native shape updates; a different block, missing support or incompatible fluid remains a real obstruction. The captured seed, rotation, mirror, stage and biome are reproduced in a native fixture.

A full stepped-yard runtime fixture additionally exposed a real outer-template cache-key omission: it omitted selected outdoor fit and frozen terrain offsets. Planning could reuse an unshifted template while construction used the shifted, hashed geometry. All affected caches now include both fit and immutable offsets. The initial failed logs are retained, not discarded to hide the finding.

## Implementation

- Stable approximately 10% optional landscaping for new ordinary reviewed TES projects, selected once from saved identity. Other projects begin with the intact building and essential access. Required agricultural/service blocks, public roads and essential lights are not made rare. Banks keep their separate landmark rules; vanilla templates remain native templates.
- Core-only searches widen immediately after failed sweeps. Decorated searches retain their full/rear/front-side/core fallback. Known occupied metadata envelopes are skipped before native inspection; forced mode takes the first fully safe orientation. Search, expansion and cooperative tick budgets remain bounded and loaded-only.
- Natural coal, iron and copper ore can be rocky footing; recorded placements, claims, block entities, occupants and cut/fill limits still veto modification.
- Fresh optional yard columns follow surveyed dry terrain, within four blocks of the rigid building base and one-block neighboring terrace steps. Paths, planting and fence columns can terrace; connected non-flexible props/fluid features move as whole groups, with at most one block of group ground variation. An unsuitable scene falls back to a smaller outdoor fit. Offsets are saved atomically with the lot and its geometry hash and preserved on reload/relocation. Existing approvals are unchanged. All 1,875 approved asset files remain byte-identical.
- New organic expansion checks a water crossing route before accepting a distant destination. Preview does not reserve, fund, pave or build. A safe destination and approaches are checked, including exclusion of bridge bounds overlapping the future building. Crossing reservations are checkpointed first; lot, crossing dependencies and terrain offsets commit together in the economy journal.
- A durable requires_destination flag prevents even another road dispatcher from funding or writing an orphan crossing after a crash between those saves. Historical ordinary bridge plans default to false. The destination stays held until every required crossing is complete and its actual deck remains walkable. Harvested bridges are not silently regenerated.
- New configuration default bridge_max_water_length is 96; supported maximum is 128. Existing saved configuration values, including 48, are retained. Shore/approach, loaded route, foundation depth, protection, cost, concurrency and native block budgets still apply. This is not deep-ocean or unlimited-distance expansion.
- Bridge-first preview results and live candidate bindings are bounded; a full eight-candidate admission queue explicitly waits rather than accidentally approving a destination without its dependency binding. The native fixture fills that queue and verifies overflow refusal. Resource reload discards transient preview caches. Required crossing progress is accelerated only within the existing forced shared allowance.
- Debug reports add observed rejection histograms, candidate position/orientation, fit, frontier, retry timer, terrain offsets and required crossings. Up to eight pending cells include actual versus expected state, position, role, phase, native survival and obstruction evidence. Wait reasons distinguish dry-land shortage, crossing required, excessive grade, unloaded chunks, occupied/reserved lots, protected ground, entrances and template obstructions. Diagnostics are read-only and do not load chunks.

## Verification

Native disposable-server fixtures passed on both Fabric and NeoForge:

- Exact mine rail repro, unsupported/wrong rail rejection, exposed-ore classification, full mine construction/reload/handover.
- Full terraced plains cottage: 5,859 operations, frozen nonzero offsets, reload, handover and real villager navigation through the door to bed/workstation and back to the street.
- Bridge-first: loaded 128-block water span, read-only preview, atomic reservation, durable crash-window guard, no orphan spending/writes, bounded placement, partial-ledger reload, one funding receipt, verified opening and no regeneration after a harvested deck cell.
- Existing broad Fabric bridge suite: 34 rotated/variable-length designs, shores/piers, navigation, claims/storage/lava/ice, reverse sharing, complete onward routes, atomic admission and no chunk loads.

Logs: build/server-smoke/beta65-{fabric,neoforge}-{terrain,bridge-first}.log and beta65-fabric-bridges.log. Final admission-cap/crash-guard runs passed again on both loaders in beta65-{fabric,neoforge}-bridge-first-final.log. Fabric terrain/debug/ownership/safety tests passed in beta65-fabric-terrain-debug.log, including a real ZIP performance capture. Initial terrain failures remain in beta65-fabric-terrain-initial.log and beta65-fabric-terrain-cache-failed.log. A mine fixture initially assumed every mine opening had a door; it now verifies the captured design's two-block open passage. Common fixture fixes use the current journal epoch after reload and escaped translation percent syntax. The loader settings test's outdated 48 default was corrected to 96, with explicit retention of saved 48 and admission of 128. None removes protection, survival, handover or navigation assertions. Smoke harness cleanup can append daemon-disappearance messages after the integration success marker; the harness deliberately terminates only its uniquely marked disposable server after that marker.

The full common regression suite passed, including rarity distribution, fit selection, atomic persistence/reload, malformed terrain/dependency rejection, forced budgets, funding, handbook formats/recipes and loader parity. The final admission-cap wiring test also passed after that safety guard was added. Full Fabric build passed in 6m 10s and full NeoForge build passed in 5m 3s, including native approved/authored/vanilla architecture, Bank, packet, settings and UI checks. The final bounded-slot guard was additionally exercised by fresh native bridge-first runs on both loaders; both final binary/source pairs were reassembled afterward from the current canonical input fingerprint. Windows OSHI/Perflib, upstream Unsafe and deprecation messages are environment/toolchain warnings, not assertions ignored by these checks.

## Handbook, recipes and compatibility

Both long reader chapters and compact book pages explain rarity, immutable approvals, terrain fitting, bounded search, explicit diagnostics and bridge-first limits. New wiring regressions cover admission ordering, cache identity, durable destination gating and both handbook forms. Existing handbook resource/mechanics checks remain enabled. Recipes were reviewed against the existing server definitions: no items, blocks or recipes are introduced; vanilla parts and existing bridge economics are reused. No unrelated recipe edits are needed.

Economy format advances from 44 to 45 with strict bounded validation for canonical local terrain columns and crossing UUIDs. Copies, saves, journal replay and migration are covered. Test a backed-up world copy; do not downgrade a changed save by swapping old JARs. No profile or user world was modified. No commit, push, merge or pull request was made. Native tests are not a visual/manual playtest of the user's modpack, and instant mode still cannot create a safe lot where none exists.

## Verified artifacts

Both packaged loaders passed exact checks of 1,875 native asset hashes, metadata/resources, binary/source pairs, current-source identity and cross-loader parity. git diff --check passed. Staged binary/source JARs and SHA256SUMS are under build/release-beta65/fabric and build/release-beta65/neoforge.

- Canonical source SHA-256: 7538502ca93cadf76dc4db3c53703f480d1853ee979d1173b1de5fa67656a481.
- Fabric binary SHA-256: 9a5717606eabbc8697b94940b72293489d8b5195952582dd73448dbe640d8cae.
- NeoForge binary SHA-256: e0dda48f00a1baa20cca6d9d4b7838e2bcd1e038df1960c0e4ddc7b7c0aa6f12.
