# Beta.51: village-matched walkway surfaces

## Scope and findings

The existing lamps and footbridges already select village-aware materials. The separate
Bank/building connection pass previously selected only desert/non-desert ground recipes:
all styles shared dirt path, gravel and coarse dirt. This change fills that gap without
replacing the existing routing, bridge, lighting or per-site recovery systems.

New connections use the village's saved architectural family, with the village-center
biome as the fallback before identity is established. They do not sample each path cell's
biome or inherit a random individual building palette.

| Family | Walking surface | Best-effort verge |
| --- | --- | --- |
| Plains | Worn dirt path, occasional gravel | Gravel and cobblestone |
| Desert | Smooth sandstone, occasional sandstone | Cut sandstone |
| Savanna | Dirt path and coarse dirt | Coarse dirt and terracotta |
| Taiga | Gravel and coarse dirt | Cobblestone and mossy cobblestone |
| Snowy | Andesite and cobblestone | Stone bricks; no ice |

A deterministic village-and-coordinate pattern gives shared intersections consistent
weathering without depending on project order. Each family has a distinct surface/edge
combination, not a promise that every village or individual path is globally unique.
Path width still follows terrain and safety; existing themed lamps/bridge silhouettes
and spacing are unchanged.

## Persistence and safety

- Freeze versioned surface IDs per connection, including an explicit historical
  desert/temperate recipe for migrated jobs. Reloading or changing the requested family
  cannot change the rest of a partly built connection.
- Old jobs, existing streets, entry steps, saved building blueprints and finished
  connections are not repainted. New and historical paving may coexist in an older town.
- New stone surface recognition requires an exact supplied-state receipt at that position.
  Natural sandstone, andesite or a player-built stone floor is not globally classified as
  a road just because its material appears in a palette.
- Receipts allow read-only traversal and lamp admission; they are not permission to
  regenerate removed paving. Protection, occupancy, reservations, fluids, headroom,
  chunk loading, write limits, three-survey deferral and real-connection checks remain.
- Both optional SavedData maps default empty for old worlds. No economy format change,
  new block registration, recipe, dependency or forced chunk load is introduced.
- Debug connection reports include the frozen surface style.

## Handbook review

Updated the guided Terrain chapter with the five treatments, village identity, safe
shoulders, unchanged lamps/bridges, and existing-world preservation. Added the compact
Local roads page (page 68) without removing previous guidance. Extended handbook
resource assertions. Existing recipe/creative discovery documentation remains accurate:
these are ordinary Minecraft blocks used by existing construction, not new craftables.

## Validation

Common regressions and both loader builds passed during implementation. Final native
focused checks cover the revised migration and lamp admission as well as the existing
Bank, entrance, walkway and bridge fixtures. See the final validation results below.

Native style fixtures exercise all five surface/edge combinations, two-write limits,
real route completion, a partial codec reload followed by a different requested family,
legacy data without the optional fields, removed paving, false natural-road recognition,
and project-independent patterns at negative coordinates. Production Bank and building
request tests check all five saved families. Lamp tests exercise actual admission on
all five new center surfaces alongside the existing 80 palette/orientation plans.

Tests use disposable repository worlds, not the user's Minecraft saves. These are
automated geometry/block-state and gameplay checks, not a fresh visual playthrough
or a large-world performance certification.

### Final results

- Common regression suite: passed, including handbook resources and beta.51 version parity.
- Fabric and NeoForge full builds: passed during implementation.
- Final revised sources: compiled and passed the focused dedicated-server suites on both
  loaders, including five surface families, historical recipe freezing and matching lamp
  admission. Both wrappers exited 0 with the integration success marker.
- Packaged the final compiled sources with both loader jar tasks: passed.
- Patch whitespace check: passed.
- An intermediate lamp fixture incorrectly expected placement in the planning pulse; it
  was corrected to test the actual two-pulse contract. One intermediate NeoForge disposable
  world hit an upstream MonsterRoomFeature spawner error and was rejected by the harness;
  a fresh run passed without suppressing that error.
- The harness intentionally stops its Gradle/Java processes after the success marker.
  Post-marker daemon-disappeared messages are cleanup, not a failed fixture. Windows OSHI
  performance-counter warnings are separate from the tested gameplay assertions.

Final package SHA-256:

- Fabric: `51dc696c0cdf2c9d5afcfedfe93bfec6318f24d017dd3ea8c5672000d371adcb`
- NeoForge: `a70331c7d356c880ee212d11790ee386a2fe0151566b3c33c37ee157803f1db4`

The implementation was validated locally before the user requested a GitHub push in a follow-up. No modpack installation or user-world modification was performed.
