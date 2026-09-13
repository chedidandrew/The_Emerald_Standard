# Beta.26: walkway connectivity

## Diagnosis and scope

The screenshot shows an existing path stopping short of the village, near trees.
It alone cannot identify the exact rejected block or saved endpoint.

The old runtime has two definite ways to leave a dead end: it follows a frozen geometric
route and permanently advances its optional cursor over unsafe/claimed cells; the first
connector targets an arbitrary point 14 blocks toward the new house from the district
center, not an observed village road. Neither tests physical end-to-end connectivity.
The old 192-center geometric route limit can also leave an empty legacy plan.

This candidate adds a separate finishing pass without rewriting any historical building,
trail sequence, cursor, inventory or economy state. No live save/profile is edited.

## Implementation

- Completed managed projects with finished or empty legacy trails are surveyed while the village
  is active/nearby and loaded. Destination: an earlier completed project entrance,
  otherwise an actual dirt-path block within 24 blocks of the district center.
  The project's own legacy center columns/shoulders and paving supplied by this new
  pass cannot satisfy the village-road goal. Natural gravel is not a village-road goal.
- Cardinal loaded-ground search can detour around trees, water, inventories, protected
  land, reserved sites and steep steps. Existing clear road cells in completed lots can
  be traversed without mutation; new paving cannot encroach into those lots.
  Existing stairs, slabs and conventional porch/paving materials within eight columns
  of either entrance are read-only access surfaces; the pass does not replace them.
  One-block grade changes, solid footing and two blocks of headroom are required.
- Endpoint horizontal Manhattan distance is limited to 384 blocks, route length to 512,
  search bounds to the endpoint rectangle plus a 24-block margin and discovered/expanded
  nodes to 8,192. Up to 96 queue entries per survey pulse with a cooperative 2 ms deadline.
  At most eight in-memory searches per dimension; abandoned searches expire.
- One connection pulse per second per dimension. Route snapshots/shoulders and completion
  checks advance in bounded batches; at most two block writes per paving pulse.
  Width is best effort; center cells are mandatory. No tree clearing, cliff excavation,
  water filling, chunk forcing or forced placement through entities.
- Exact before-state snapshots are rechecked before mutation. A changed mandatory cell
  causes a fresh detour search; optional shoulders can be skipped. Unloaded/occupied cells
  wait. Failed surveys retry after five seconds, subject to fair scheduling/work bounds.
- Per-dimension `walkway_connections` SavedData keeps the partial plan, cursor, supplied
  paving receipts, completion and status. Removed supplied paving is never regenerated;
  unfinished work can instead seek another route. Completed jobs remain editable and
  are not reopened on reload.
- The lamp pass follows verified detours using separate connection-v1 jobs and existing
  palette, lighting and spatial receipt safeguards. An existing partial old-route lamp
  finishes before switching routes. No duplicate or regenerated lamp posts.
- `/emerald debug` includes each project's connection status/reason. These diagnostics
  distinguish an unfinished connection from the old optional trail cursor being consumed.

## Handbook review

Guided terrain chapter documents real endpoints, detours, partial width, loaded-only
work, survey bounds, retries and one-shot editing policy. Compact terrain page
now includes detours. Updated handbook resource coverage; no new items or recipes.

## Validation

- All 90 common regression entrypoints passed on final source
  (`build/beta26-common-final.log`), including handbook resources and loader parity.
- Both complete builds passed: `build/beta26-fabric-build.log` and
  `build/beta26-neoforge-build-final.log`.
- Native walkway regression passed on both loaders: a legacy stub, tree wall, inventory,
  claim strip, water and a new mid-paving obstruction produce a continuous detour to the
  real destination. Grade/lot checks, unchanged entrance stairs, occupied-cell resume,
  two-write budgets, partial codec reload, removed-paving receipts, unloaded chunks,
  actual manager endpoint selection and empty legacy-plan eligibility passed.
- Fabric full dedicated-server integration passed
  (`build/beta26-fabric-server-5.log`).
- NeoForge full dedicated-server integration passed on an unchanged fresh rerun
  (`build/beta26-neoforge-server-3.log`). The preceding run passed walkway tests but
  hit the previously observed independent CreativeContentSelfTest dispenser query
  failure (`dispenser retains distinct egg data: []`); that failure is retained in
  `build/beta26-neoforge-server-2.log`, not filtered or suppressed.
- Native client verified all 61 written-book pages, guided chapter ends, real-font
  wrapping and 80/120 text sizes (`build/beta26-fabric-client-final.log`).
  The initial added compact sentence overflowed page 25; the final detour explanation
  belongs on the terrain page and passes the actual renderer.
- Initial native harness runs exposed an outdated debug reflection signature and
  incomplete synthetic fixtures (architecture IDs, same-height gravel footing and
  the distinction between a truncated segment and an empty legacy plan). These were
  corrected and the complete suites rerun, not bypassed.
- Candidate identity check passed against current source for both loader JARs.
  No live-world path inspection or shader gameplay visual check is claimed.
  Nothing installed into the user's Modrinth profile.

## Final fingerprints

Source SHA-256:
`98ac4bbc0ef04bd97804d58b819ba6aea60a7a9f9f4b62b4db1ba4dd8dd51995`

Fabric JAR SHA-256:
`ebd2d7e6ef9c0159b837b0af31bc188a3d76934b040f7097e834adb18710e736`

NeoForge JAR SHA-256:
`6d2537e138bf416bf93d8f77c29373c8449f0fe4be93bc7af7ec606ea5494ed1`
