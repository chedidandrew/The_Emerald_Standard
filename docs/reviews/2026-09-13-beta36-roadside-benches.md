# beta.36 — roadside bench direction

## Cause

The pictured three-stair bench with shrubs beside a gravel square is a roadside pocket from
`VillageTerrainFinishing.pocket`, shared by new Bank approaches and project streets.
It is not one of the six Bank front-terrace seats corrected in beta.30.

The pocket generator interpreted stair FACING as the direction a seated villager would look.
Minecraft places the stair's full-height half on that side, so this put the raised back against
the paving and opened the low seat toward the grass. Reverse that one direction selection:
the back points along the outward pocket vector, and the seat opens toward its paved center.

No palette, cell coordinate, footprint, planter, lantern, path grade, bench size or Bank blueprint
version changes. The road-stair uphill calculation is unchanged. Previously saved construction
plans retain their exact states and completed benches are not scanned or rotated automatically.
This is a new-plan correction for the shared pocket generator, not permission to overwrite
player-modified furniture. Older benches can be turned manually after construction ends.

## Regression coverage

The existing disposable construction fixture now calls RoadsideBenchSelfTest against its
pristine, already-loaded patch. It exercises the actual road and pocket planner on eight-cell
Bank approaches and fourteen-cell project routes: all four travel directions, both path sides
(including forced fallback through a reserved preferred pocket) and all three styles.

Checks cover 48 plans, 96 seats and 16 garden-only cases; correct three-cell outer-edge placement,
native collision-shape high back and low seat, solid support, unchanged spruce/paving materials,
open centers, no writes during planning, deterministic recipes, claims/reservations and exact
new/legacy frozen-plan serialization. Replay must reject the opposite-facing stair as unfinished.
The existing Bank v11 six-seat and actual terrain-replay tests remain enabled.

## Handbook review

Expanded the guided Bank access section to distinguish the separate roadside nook from the
front-terrace seats, describe inward-opening seating and clarify the new-plan/non-retrofit scope.
Added compact roadside-seat direction and old-seat guidance without removing Bank access advice.
Updated handbook regression assertions. Chapter routing, recipes, item registration and page
count are unchanged; both handbook formats remain part of native client verification.

## Validation

- Full common regression suite: PASS (`build/beta36-common-tests.log`).
- NeoForge disposable dedicated-server fixtures: PASS (`build/beta36-neoforge-server.log`).
- Both loaders passed the full 48-plan/96-seat/16-garden roadside check.
- Fabric full server retest: PASS (`build/beta36-fabric-server-retest.log`), including the
  bench fixture, dispenser test and final integration success. The initial full run failed in
  the unrelated CreativeContentSelfTest dispenser-entity assertion (empty result), after the
  bench and walkway checks had passed. The same code passed on retest; no spawn-egg code
  or assertion was changed. Initial details are retained in
  `build/beta36-fabric-server-initial-detail.log`; this does not establish that the
  intermittent dispenser test issue has been fixed.
- Full server wrappers stop only their disposable process trees after successful markers.
  Gradle daemon termination messages following that cleanup are expected.
- Full Fabric build: PASS, 3m 33s (`build/beta36-fabric-build.log`).
- Full NeoForge build: PASS, 3m 19s (`build/beta36-neoforge-build.log`).
- Candidate verifier: PASS current-source fingerprint, version and cross-loader parity.
- Final Fabric and NeoForge handbook client checks: PASS
  (`build/beta36-fabric-client-final.log`, `build/beta36-neoforge-client-final.log`).
  Verified all 61 compact pages, guided chapter ends/search, GUI scales 2/4 and text 80/120%.
  Initial native layout checking caught an 18-line compact Bank access page; the final wording
  fits while retaining the 192-block/retry, unsafe Bank and menu-reopen advice.
- After that compact-text correction, reran HandbookResourceRegressionTest successfully
  (`build/beta36-handbook-resources-final.log`) and reassembled both final JARs
  (`build/beta36-*-package.log`). Production construction code was unchanged by the text fix.
- `git diff --check`: PASS. No world migration, live-world modification or GitHub push.

## Candidate identity

Version: 0.4.0-beta.36.

Shared source SHA-256:
`190316eb4e2bb5aea91a37f37c9acdb17b39edc2bfcb99d317b0f3b48a7b078e`

Fabric binary SHA-256:
`aac42ca04f68e7f0688334b04378a72331a7376af320b551aa090f27cebb8811`

NeoForge binary SHA-256:
`d52cf8cd568d7e9372ec8fc8c81934f49b2c5c380efd92dfd3eaed512bc893eb`

Playable JARs are in each loader's build/libs directory. Older candidates remain untouched.

No installed profile, user world or GitHub remote has been changed.
