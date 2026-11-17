# Beta80 — entrance access review

## Implementation

Fresh reviewed TES projects attach exterior access to `projectEntrance`, which resolves
the actual asset exit, mirroring and rotation. Do not apply the historical bounding-box
midpoint/assumed-height narrow approach to new approved assets. Historical cursors and
completed geometry retain their original reconstruction, without changing approach version
or architecture revision 20. Fresh Banks use their reviewed `walkwayExit`. New exact-vanilla
copies widen exterior access only; native cells and existing retrofit receipts stay intact.

`EntranceStaircase` surveys a straight three-wide descent, facing uphill toward the exit.
It adds matching non-falling full-block backing, a grounded final row and headroom. Flat
ground produces no stair/apron. Required columns, affected vegetation and unchanged natural
bearings are saved in the preparation receipt before writes. Preparation, outside grading
and optional road work cannot overwrite these access columns; nearby stair access expands
exclusive project reservations. The normal four-block terrain envelope remains in force.

Completion verifies frozen three-wide access columns and the bottom bearing, rather than
waiving a damaged flight. Missing/changed cells suspend handover and preserve edits. Native
imports already verify their complete foundation/access receipt; Bank handover checks its
complete saved cell plan. No blanket world replacement or live-save edit was performed.

## Validation inventory

- Shared regression suite, including handbook resource/mechanics and terrain wiring checks.
- Dedicated native stair fixture: four rotations, drops one through four, flat sites,
  three-wide straight stair shape, solid backing, physical villager travel out and back,
  exact receipt encode/decode, final-bearing removal, foreign side blocks/storage/water,
  claim veto and unloaded columns without force-loading.
- Imported-terrain suite: 16 historical rotated recessed/slope fixtures, 20 raised and
  20 flat real imports across five styles. Widen the disposable floor to include all
  three side lanes; the original ten-block fixture ended underneath a rotated taiga lane.
- Expanded approved-architecture suite adds five raised current core-only buildings to
  existing current/historical projects, Banks, fits, rotations, mirrors, restart,
  loot/storage, physical door/bed/workstation access and handover checks.
- Test villagers with explicit reviewed entrances are visiting nitwits so native job
  acquisition cannot replace the prescribed test movement goal. Actual navigation,
  collisions, door opening and physical travel remain enabled; production AI is unchanged.
- Native client compact/long handbook reader and preference persistence, fresh and restart.
- Both loader assemblies, candidate/source identity, immutable architecture asset and JAR checks.

## Handbook and recipes

The existing Building projects chapter already routes both terrain sections. Update the
guided terrain text with actual exits, three-wide backing/landing, flat-site behavior,
required safety checks, exact-import limits and frozen older-plan scope. Update compact
terrain text in its existing eight-line budget and corresponding regressions. Review
EmeraldHandbook routing, HandbookChapters, HandbookRecipes/HandbookScreen animations and
server definitions: no new item or recipe; the four Survival recipes and creative-only
spawn eggs are unchanged. Native reader bounds are checked before release.

Release artifacts are generated locally, not installed into Minecraft profiles. Existing
stairs in screenshots are not retroactively remodeled. These automated disposable-world
checks are not a visual audit of every structure on every natural hillside.

## Final execution results — October 7, 2026

- PASS all shared regressions, loader parity and pinned wrapper checksums
  (`build/beta80-common.log`).
- PASS focused native entrance, finishing, grounding and imported-terrain suites on
  Fabric and NeoForge (`build/beta80-fabric-entrance.log`,
  `build/beta80-neoforge-entrance.log`). Each loader checks 16 raised and four flat
  rotations, real villager outbound/return movement, backing, frozen receipt reload,
  removed final bearing, player/storage/fluid/claim vetoes and unloaded frontier reads.
  Imported coverage includes 16 legacy rotated plans and 20 raised plus 20 flat
  five-style real templates; exact authored cells remain unchanged.
- PASS all 153 fixtures in the expanded NeoForge approved-architecture server suite
  (`build/beta80-neoforge-architecture.log`). This includes five raised current
  core-only buildings and the current/historical Bank and project checks.
- PASS Fabric native compact/long handbook reader bounds and preference persistence
  on both fresh and restarted clients (`build/beta80-client-summary.log`).
- PASS both loader assemblies and current-source identity parity. Both packaged
  architecture audits verify all 12,375 immutable native assets and production adapters
  (`build/beta80-fabric-assets.log`, `build/beta80-neoforge-assets.log`).
- PASS staged binary/source JAR verification and SHA256SUMS; `git diff --check` is clean.

Version: `0.4.0-beta.80`; architecture revision remains 20.

Canonical source SHA-256:
`cdc4ef5612ec8b762488a42ec8478e78038a6d581dcecd8c544a256d20d120cd`.

Binary SHA-256:

- Fabric: `e6cb4512793a0c07def74746b5ab8292b72bed26dbe7483f98baadd63eb5e3ea`.
- NeoForge: `e88e6567d920170628cc46deab6c7b86950d25b8f52fc845333f5bdff320c90a`.

Local release: `build/release-beta80`, separate binary/source pairs per loader.
Not installed; no live worlds or Minecraft profiles were modified.
