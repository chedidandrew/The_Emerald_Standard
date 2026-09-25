# District terrain and crouch-placement validation

## Implemented behavior

- District map renders a small surface raster using native Minecraft map colors behind
  district coverage, site footprints, center markers and the foreground sidebar legend.
- Coverage shares the existing food survey calculation: center +/-64 blocks, expanded
  to developed project bounds and the Bank with field margins. Saved district centers
  remain separate from coverage midpoints. It is a survey area, not a land claim;
  neighboring areas can overlap, and planned-only projects do not enlarge coverage.
- Area tooltips show dimensions and coordinate limits. Exact building/center hits win
  over broad area hits. Focus fits district coverage; scroll zoom anchors at the pointer.
- Terrain only queries already-loaded client chunks. Unknown cells are checkered.
  Last-seen samples survive chunk unloading while the map remains open; closing/removing
  the screen releases the texture and cache. Changing client worlds resets observations.
- Limits: 130x82 pixels, 512 column samples per 50 ms pulse, soft 2 ms sampling deadline,
  32,768 cached samples, 32-block maximum transparent-surface descent. No chunk generation,
  network terrain requests, world edits, underground survey or permanent exploration data.
  Per-frame work still depends on the bounded current page; metadata collection traverses
  saved city records. These safeguards do not promise unlimited-world performance.
- Both loader interaction hooks pass crouched block users through before desk validation.
  Main-hand and offhand blocks use normal Minecraft placement; ordinary clicks retain GUI
  behavior. Empty-handed or non-block crouch interactions keep existing banking behavior.

## Verification

- Common regression suite: coverage and signed centers, 404 markers across pages, packet
  revisions, pointer-anchored zoom, sample/frame/cache budgets, unloaded-sample retention,
  extreme zoom/world limits, handbook coverage, loader pass-through ordering.
- Fabric and NeoForge: assemble, signed-short Banker packet codec and reader/settings tests pass.
- Dedicated-server integration passes on both loaders, including the native game-mode
  interaction path through loader hooks: successful Survival placements consume exactly
  one block, occupied placements consume none, offhand placement works, Creative retains
  the stack, and crouch-placement does not open a menu. Native grass/log/water/stone palette
  samples and transparent-surface handling pass.
- Focused client smoke passes on both loaders at GUI scales 2 and 4. Normal map, building
  hover and coverage hover screenshots were captured. Inspected screenshots confirm layer
  ordering, visible coverage borders and sidebar, readable limits and unclipped terrain label.
  Terrain in these rendering fixtures is deterministic test scenery, not the user's world.
  Actual map-color sampling is separately checked by the native server fixture.
- Handbook accuracy review: added the guided district-map section and expanded desk-use
  instructions; updated compact terrain/desk pages. All 60 guided sections resolve,
  all 50 compact pages fit the native 14-line limit, and long-form chapters pass actual-font
  wrapping, end navigation, 80/120-percent reader sizes and search checks.
- Initial checks caught and corrected a compact-page overflow, missing JSON comma, stale
  section-count assertion and a clipped terrain label. Final client/package checks pass.
- Both binary/source JAR pairs pass packaged resource/class/JSON checks with SHA256SUMS.
  Final Fabric binary SHA256: e3243f3798643f1e60e982b7a8969ea624dfd07326b625bf2d6e743546870cc7
  Final NeoForge binary SHA256: 8c680890d575d23eb6c51d3ddce6ad923f354c6d7cc12dc6d4a50b4a8608b567

Logs: build/map2-common-final.log, build/map2-*-server-final.log,
build/map2-*-client-final.log, build/map2-*-package.log and build/map2-*-verify.log.
Screenshots: build/client-smoke/map2-{fabric,neoforge}-final/screenshots/tes-reader-ci/.
Host OSHI/Perflib warnings and deliberate smoke-harness server termination are distinct
from the successful mod test markers.

Existing worlds and installed profile JARs were not modified. Marker transport changed;
multiplayer clients and servers should install matching builds. No save migration is needed.
