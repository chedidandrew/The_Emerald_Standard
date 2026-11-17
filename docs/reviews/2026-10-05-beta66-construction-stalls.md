# Beta 66: frozen preparation and overlapping project recovery

## Evidence

The user's TES-debug-20261005-121414-E829AE5A.zip was inspected read-only. Stone Reedhill Gardens had a completed project 10 (merchant office, 983/983) and unfinished project 11 (garden cottage, 384/802) with overlapping saved envelopes. Their delayed route surveys completed one tick apart. Project 11 repeatedly expected grass at (-236,-60,243), where the completed building had oak planks. This was an own-mod reservation conflict, not evidence of player construction or a lingering animal. Killing entities cannot repair conflicting saved project records.

The separate mine project 13 had 383 frozen preparation cells, while its progress report remained zero. The saved-region inspection found an existing coarse-dirt lane where the final preparation cell requested gravel. Strict before-state matching rejected that usable surface. Partial preparation counts were only committed after finishing the whole plan, obscuring the real progress. Diagnostic bedrock beneath naturally grounded supports is not itself proof of a stalled foundation.

## Changes

- Recheck current project exclusions after a delayed route survey. A final synchronized economy mutation rejects horizontally overlapping current or retired lots, including other districts in the same dimension, with four-block separation. Starter-district commitments use the same final guard. A different Y coordinate cannot evade it.
- Recover a proven same-village overlap deterministically: completed buildings win, otherwise the older project wins. The unfinished loser retires its old bounds and searches anew. Recovery never edits world blocks, erases the finished building, or falsely marks the loser complete. Native ownership cleanup only closes the retired job. Ordinary replacement charges its consumed resource share; insufficient resources or retirement capacity explicitly wait. Forced mode waives resources, not site safety. Mines with an anchor outside their actual envelope are covered.
- Replay actual frozen terrain cells rather than blindly trusting a saved cursor. Save partial progress on every exit, allow natural grass/dirt transitions, and preserve an existing dry road material when optional paving requests another road material. Reconciliation is read-only and requires two clear blocks of headroom. Foreign structural blocks, inventories, fluids and protected writes remain vetoes.
- Keep exact terrain blocker evidence, separate unloaded/entity/save waits, and expose preparation counts and decoded bounds in debug snapshots. Validation warns about saved overlaps without making legacy saves unloadable. Existing captures no longer falsely report those conflicts as valid.
- Survey short creek routes too. Preview/search identities include the complete immutable request and bridge context; a changed destination, exclusions or settings cannot reuse the previous approval or failed retry timer.
- Remove occupied-cell timeout permission. Safe creature relocation must actually clear the cell; failed movement never permits suffocation or removal of occupied footing. Players remain protected.
- Restrict covered-path settlement to cosmetic yard cells, never required structural floors. Final native connector finishing uses the same safe animal clearance instead of indefinitely waiting on stationary non-player occupants. It reports exact unloaded neighboring columns and actual/expected shapes with protection/occupancy evidence.

## Verification

The full common regression suite passed after the final core edits, including captured bounds, deterministic recovery, neighboring-district reservation refusal, outside mine anchors, ordinary funding, retired lots, journal/reload, partial counts, handbook and recipe coverage, compatibility, and loader version parity.

Targeted disposable live-server fixtures passed on Fabric and NeoForge: frozen terrain replay, read-only coarse-dirt/gravel reconciliation, exact foreign-block rejection, one-write partial progress, completion/reload, obstructed headroom, slope/stair grading, crater supports, retaining courses, claims/storage vetoes, and real villager walking both directions. Creature clearance retains its no-timeout/player safeguards. Bridge-first checks passed a 128-block river, changed-preview identity, short creek admission, crash-window spending guard, reservation/reload, one funding receipt, shared allowances, verified passage, and no regeneration of harvested completed decks.

Logs: build/server-smoke/beta66-fabric-stalls.log and beta66-neoforge-stalls.log. Initial compilation/test failures are retained separately. The new fixture originally called a package-private helper; it now uses the public service startup API. An added deliberate obstruction was inside the existing sloped road fixture's two-block safety buffer. Moving that obstruction to a separate column made the original slope and walking assertions pass without weakening them or production protection. Windows OSHI/Perflib diagnostics are environment warnings. The smoke harness may append daemon-disappearance output after success because it terminates only its uniquely marked disposable server.

Both broad disposable server suites passed all 479 yielding fixtures, including debug capture, saved construction recovery, normal/forced Smithy and Inn completion, funding, protection, loot, entity safety, native connectors, roads, bridges, Bank behavior, creative content, and exhaustive legacy/authored/modular production template admission. Logs are build/server-smoke/beta66-fabric-full.log and beta66-neoforge-full.log. The final safe-connector-clearance and wait-count additions then passed fresh targeted runs on both loaders in beta66-fabric-stalls-final.log and beta66-neoforge-stalls-final.log. These include an actual tracked cow blocking connection shape expansion: authorized finishing safely moves it, preserves its life and updates the rail; occupied cells without safe alternate footing and players remain vetoes. The native replay fixture asserts that a terrain wait is counted as a waiting project in the debug workload snapshot.

The initial full Fabric build passed in 6m 25s and NeoForge in 5m 18s, including architecture, native vanilla fidelity, handbook/recipe, Bank, packet, settings and construction-fence model checks. Final full builds from the last tested source passed again: Fabric 6m 11s and NeoForge 5m 21s (build/beta66-*-build-final.log). The common suite's final recorded run passed in build/beta66-common-tests-final.log; the last native-only diagnostic/clearance delta was verified by the fresh targeted native suites above.

The first broad run exposed a removed cosmetic-role guard in the earlier path-settlement integration. Restoring it made all four fresh/resumed normal/forced Smithy cases pass; its wiring regression now checks the adjacent guard expression, not two independent substrings. The next broad run correctly waited at the mine's final connection because the disposable fixture omitted neighbor chunk 2016,272,2001. Its setup now explicitly loads adjacent columns; production never gains force-loading permission. Failed full-run logs remain under beta66-*-full-initial.log, beta66-*-full-connections.log and beta66-fabric-full-unloaded-fixture.log.

## Handbook and compatibility review

Both the long reader terrain chapter and compact terrain pages now explain collision retirement, preservation of old blocks, replacement resource costs, safe replay, existing-road preservation and partial debug counts. Existing chapter routing remains correct; no new chapter is needed. Recipe definitions and animated recipe pages were reviewed: no items, blocks, recipes, geometry assets or save-format change is introduced in beta66, so no unrelated recipe edits are warranted. This release also retains the previously verified beta60–65 building, loot, connection, doodad, lamp, rarity and terrain improvements.

No user profile or live save was modified. Test a backed-up world copy. Existing retired blocks are intentionally retained; recovery is not a demolition tool. Safe placement still requires loaded land and bounded terrain/crossing limits. No live-modpack timing benchmark or visual/manual gameplay test is claimed.

## Packaged artifacts

Both binary/source pairs passed scripts/verify-approved-architecture-jars.ps1 against the current canonical input manifest, loader metadata and production adapters. All 1,875 native asset SHA-256 hashes remain exact (750 archived/current full designs plus 1,125 reduced outdoor fits). Cross-loader source identity agrees. git diff --check passed. Binary/source pairs and SHA256SUMS are staged in build/release-beta66/fabric and build/release-beta66/neoforge; README.md explains safe testing and recovery behavior.

- Canonical source SHA-256: ba5086b09669876683c877f0fdfae1acea1d22f51cc18a78c829040531529cbe.
- Fabric binary SHA-256: 984d2ff5329cd76d3afc689ec77c90d3290ef24e7857df38731789433a22313e.
- NeoForge binary SHA-256: 63812581220a51b816980dfee7471d29ba6065fd2c6bd003a6f93f02f0618489.

No commit, push, installation or live-world repair was performed. Existing same-village conflict recovery is covered; historical cross-village overlaps are not claimed to relocate automatically, although new overlapping commitments are prevented across districts.
