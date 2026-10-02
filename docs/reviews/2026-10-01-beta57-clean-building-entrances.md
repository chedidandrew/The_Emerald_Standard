# Beta.57 clean building entrances

## Screenshot evidence and scope

Rechecked all five supplied screenshots from 2026-10-01 after their paths were repaired.
The 17.57.03/17.57.15 pair and 17.57.30/17.57.42 pair show the requested before/after:
remove the projecting dirt-path apron and extra masonry, retaining the original porch steps,
lamps and other yard fixtures. The 17.56.39 close-up shows an isolated raised path cube.

These cells came from authored presentation dressing, not the terrain-aware descending
entrance planner or public walkway route calculations. No blanket stair removal is appropriate.

## Implementation

- New approvals select authored revision 11. All 52 revision-10 descriptors remain resolvable;
  existing reservations, fingerprints, construction cursors and world blocks are not rewritten.
- Compose dressing, planting and lighting before omitting unused foundation/apron cells in the
  bounded front entrance strip. This avoids moving fixture anchors as a side effect of cleanup.
- Never edit the base shell/porch. Retain optional footings carrying lamps or other fixtures.
  Rear yards and unrelated side scenes remain outside the cleanup area.
- No world erasure, terrain replacement, inventory access or new clearing permission is added.
  Necessary terrain-aware stairs, foundations and biome-styled public paving are unchanged.
- Update the controlled gallery to revision 11 without changing its reference house or layout.

## Handbook accuracy review

Updated the guided Terrain chapter and compact/lectern terrain page together, explaining clean
new entrances, preserved porch/lamp bases, required slope access and unchanged older buildings.
Added regression assertions for both forms. No item, recipe, diagram or creative entry changed.

## Validation

The focused native production test passed for 52 masters across five village styles, comparing
revision-10 and revision-11 base cells and above-ground fixtures. Across those 260 prosperous
plans, 4,815 optional apron cells were omitted. Synthetic cases cover original porch reservations,
lamp supports, unrelated yards, idempotence and unchanged older/unknown revisions.

Full common regression suite passed (`build/entrance-cleanup-common-verified.log`), including
terrain foundations, entrance slopes, saved architecture, progression and both handbook forms.
Fabric full build passed (`build/entrance-cleanup-fabric-final.log`), including the complete
authored catalog gate over dialects, palettes, dressing and characters, exact shrub/rail survival,
support-first construction, frozen revision-3/4 snapshots and original Bank snapshot checks.
NeoForge full build passed (`build/entrance-cleanup-neoforge.log`). Both loaders passed reader,
tooltip, settings and vanilla catalog checks. Final assemblies were refreshed after the last
source/comment edits (`build/entrance-cleanup-{fabric,neoforge}-package.log`).
`git diff --check` passed. These are code/geometry checks, not a fresh in-game visual playthrough.
No installed mod or user world has been modified.

Existing completed buildings and already-approved plans intentionally keep their saved design.
Old decorative aprons may be removed manually after construction, preserving necessary access.
GitHub has not been updated for this request.
