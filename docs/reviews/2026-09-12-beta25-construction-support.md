# Beta.25: recover unsupported optional construction dressing

## Evidence

Read-only inspection of the user's supplied debug archive
`TES-debug-20260912-180610-3F57E73D.zip` identifies beta.20,
courtyard Inn project 6, economic progress 100%, physical cursor 3603/4873.
The capture spans five minutes; all 292 matching timeline snapshots retain cursor 3603.
The saved economy still has that cursor and the
frozen construction-order range `2941,4873`.

Replaying the exact revision-10 template, seed, timber-forward palette, lived-in
dressing, Agrarian/Taiga character, rotation and mirror gives the same 4873 cells.
Operation 3603 is an optional spruce-fence cell at world 1496,68,1367. It depends
on the mossy-cobblestone foot at 1496,67,1367, consumed earlier at operation 3481.

A read-only region-block probe found grass at that foot coordinate and air above.
The optional foot cannot replace that solid terrain and is legitimately waived.
The support gate nevertheless deferred its dependent optional fence as if it
were required structure, before the ordinary cosmetic-skip policy could run.
Prefix integrity checks also correctly ignored the never-supplied optional foot,
so no recovery was scheduled. A timeout that bypassed support would create
floating decoration or overwrite the terrain, not fix this policy contradiction.

## Change and safety boundaries

Never-supplied optional dressing now waives an unsupported cell in the ordinary
bounded construction pass, as it already does for other unsafe cosmetic placement.
This advances the existing saved cursor on its next eligible loaded pulse.
No timer, plan/hash/order rewrite, new pillar, terrain overwrite or free repair loop.

The exception uses `isCosmetic()`, which excludes required safety fixtures, and
requires the target not to be construction-owned. Required structural cells,
supplied unfinished repairs, handover verification, entity collision, player
property, storage protection, loaded-chunk checks and block budgets remain intact.
Completed buildings are not reopened or regenerated.

Support observations identify the proposed block/world position and up to three
planned support alternatives with their actual block or unloaded state.
The same bounded fresh observation is included as `observedWork` in
`/emerald debug` village/project snapshots; stale observations are not reported as live.
When other safe work actually occurs, the phase is Building instead of Waiting.

## Handbook review

Expanded the guided construction-safety chapter with the optional/required distinction,
immediate cosmetic recovery, remaining obstruction limits and exact support diagnostics.
Updated the compact site-repair page and handbook regression coverage.
No new item or recipe; no economy or SavedData format change.

## Verification

- All 90 common regression entrypoints passed (`build/beta25-common.log`).
- Both full loader builds passed (`build/beta25-fabric-build.log`,
  `build/beta25-neoforge-build.log`).
- Native frozen-Inn replay passed on both loaders: cursor advances beyond 3603 promptly,
  reaches 4873/4873 with verified handover, survives economy reload, preserves the grass
  foot and leaves the unsupported fence empty. Fresh/stale support-report cases passed.
- Fabric's full server integration passed (`build/beta25-support-recovery-4.log`),
  including existing repair, handover, entity occupancy and property safety cases.
- Native Fabric handbook checks passed for all 61 compact pages and guided real-font
  wrapping, chapter ends and 80/120 text sizes (`build/beta25-fabric-client.log`).
- One NeoForge full run passed the Inn case but hit the previously intermittent,
  unrelated CreativeContentSelfTest dispenser fixture (empty entity query).
  Retained in `build/beta25-neoforge-server.log`; the unchanged-source fresh rerun
  passed that fixture and the complete suite (`build/beta25-neoforge-server-recheck.log`).
- Candidate verifier passed packaged versions, current-source and cross-loader parity.
  No claim of a live-profile gameplay test; all writes were in disposable test worlds.

## Candidate artifacts

Version `0.4.0-beta.25`; source SHA-256:
`57f4a234c6c67260674d3396c277fba58b30c52b7a1e7883ac2353f7da72ae6e`.

- Fabric: `fabric/build/libs/the-emerald-standard-fabric-0.4.0-beta.25.jar`
  SHA-256 `82dc9baac2d554a49e57ba112686ed85c314876ce98d77ed0c545fca80e279d3`.
- NeoForge: `neoforge/build/libs/the-emerald-standard-neoforge-0.4.0-beta.25.jar`
  SHA-256 `899abc09bd853e0325f337670932b40fac1af46a20fe36dd191a5ba5478e11f1`.

The live Modrinth profile, world blocks and economy files have not been modified.
