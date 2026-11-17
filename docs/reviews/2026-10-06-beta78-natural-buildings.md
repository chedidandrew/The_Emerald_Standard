# Beta78 natural building finish

Completed and staged for Fabric and NeoForge under `build/release-beta78`.
Both loaders package canonical source identity
`3c9cb3ea9458279e2a84f2ebae522116cb865a01a2cbe3c0ad3156ceadfdbcad`.

## Scope and preserved behavior

- Architecture revision 20: standalone fit3 removes optional external yard dressing,
  with one flush natural approach footing. Essential floors, porches and steps remain.
  Optional decorated variants retain connected side/rear features, not the snowy front apron.
- New foundations use dirt or desert sandstone. Bounded hillside blending uses local
  natural soil, snow or unpolished rock rather than a polished retaining outline.
- Open-sided compartments replace wooden entrance doors with matching open fence gates.
  Both sides must connect outside at head height; sealed rooms, iron doors and exact
  vanilla imports remain unchanged. Gate-side rugs are set back to avoid headroom snagging.
- Supported exposed snowy roof courses receive deterministic one-to-four-layer drifts.
  73 of 75 snowy designs have varied heights; one has a single eligible snow tile and
  the guard blockhouse keeps its accessible roof platform clear. Flues and hatches stay clear.
- Fresh preparation receipts include complete affected natural logs and owned canopy,
  dependent vines and snow caps. Leaf-only clipping can identify the nearby natural trunk.
  Neighboring roots/shared crowns, recorded planted trees, persistent landscaping, storage,
  native structures, player edits and claims remain protected. Loaded-only bounded surveys
  reject incomplete/unloaded crowns instead of authorizing a partial tree wipe.
- Beta77's accelerated instant-development lot-search policies remain included. This
  validation is not a timing measurement of the user's reported 59-second search.
- Historical architecture releases and saved preparation receipts are preserved. Finished
  buildings are not automatically remodeled; old receipts cannot infer a larger tree after
  original evidence is gone. No installed profile or live save was changed.

## Verification

- All shared regressions, loader parity and pinned Gradle wrapper checks passed.
- Full export and all three outdoor fits were regenerated. Approved architecture and
  building usability gates passed: 375 current full designs, 1,125 fits, 1,632 upper-room
  checks, lamp orientation, furniture and authoring-pass idempotence.
- Fabric and NeoForge each passed all 148 native architecture fixtures, including physical
  villager navigation through produce-stall gates in all five styles, partial construction,
  restart, durable handover, historical geometry, Bank variants, adits and `/tesstructure`.
- Both native natural-building suites passed: 375 standalone entrances, 51 matching open
  gates, 73 varied snowy roofs, supported snow, clear smoke flues, historical bed preservation,
  complete affected canopy, claimed foliage vetoes, untouched shared crowns, production
  receipt replay/restart, local podzol/mycelium/andesite and grounding safety fixtures.
- Both final fast-lot suites passed normal and forced development, occupied terrain/cells,
  construction damage repair, rotation fallback, actual Bank footprint protection,
  bridge-first destination admission and vegetation compatibility.
  During validation, two older fixture assumptions were corrected: tree receipts now include
  the canopy as well as 26 logs, and the damage probe runs after the occupancy probe so a
  repair rewind is not mistaken for occupancy progress. No protection was relaxed for tests.
- Guided Building ID handbook prose and its compact page were updated. Native fresh and
  restarted client checks passed compact page bounds, all guided chapters and preferences.
  Recipe routing/animations were reviewed against the four server Survival recipes; no
  items or recipes were added, and creative-only spawn eggs remain creative-only.
- Both final assemblies succeeded. Current-source and cross-loader identity checks passed;
  each JAR contains all 12,375 exact current/historical frozen architecture assets.
  Staged binary/sources pairs passed metadata/class verification and have SHA256SUMS.
- `git diff --check` passed. No commit, push or installation was performed.

Evidence: `build/beta78-common.log`, `build/beta78-client-summary.log`,
`build/beta78-architecture-gate.log`, `build/beta78-export*.log`,
`build/beta78-*-natural.log`, `build/beta78-*-architecture.log`,
`build/beta78-*-fast-lots.log` and `build/beta78-*-assemble.log`.

Binary SHA-256:

- Fabric: `ce99606e0624b810822564fe4e99a396180d64c30d0cf734523bb4fa64fc0e07`
- NeoForge: `0ae9019955d7268e1fb2cfb6e6122d96f6313e107f62e19dddf154083f203823`

Native fixture verification does not replace a fresh user-world capture or visual review
of a newly generated hillside village. See `docs/NATURAL_BUILDING_FINISH.md` for limits.
