# Architecture review revision 10 — outdoor rooms and working yards

Branch: `codex/biome-architecture-preview`. Review-only; not merged into main or
selected by normal development. Existing approved buildings and earlier review
saves are untouched. The building catalog still contains 375 designs, including
110 compact designs. Building geometry and interior arrangements are unchanged.

## Outdoor composition

Each design now has a separate site reservation with a rear court, narrow side
verges, fences with deliberate openings, planted edges and carried lanterns.
Compact designs receive one row of outdoor rooms; larger designs receive two.
The occupied building envelope is immutable. The extra land is recorded separately
and is not used to inflate the building's tier or its housing capacity.

There are 28 feature families and 1,953 selected feature placements:

- Flower beds, herb gardens, vegetable plots, small orchards and hedges.
- Patios, pergolas, washing courts, covered wells, ponds, water basins and cisterns.
- Sheltered firewood, chopped logs, stumps and wood piles.
- Camping areas, open sleeping tents, carts, stacked crates and hay stores.
- Empty livestock pens with hay and drinking troughs, and decorative beehives.
- Forge yards, stonecutting work areas, textile awnings and drying racks.
- Fishing/rest areas and civic reading/notice courts.

Building purpose controls the available choices: houses get domestic yards;
inns get resting and traveling amenities; stores get loading and storage areas;
smithies and mines get work yards; granaries get food-related yards; markets get
display/craft areas; guard buildings get camp/storage areas; banks and exchanges
get restrained public courts. An explicit seeded shuffle selects distinct feature
types per site, without replacement. Unused outdoor rooms remain lawn. The same
identity always produces the same layout. Reusable regional components remain
intentional; this does not claim that every fence or planter is a unique model.

Plains uses oak, cobblestone and garden planting. Desert uses pale sandstone,
white shade cloth, botanical planters and small wash/cistern courts rather than
gravel, coarse dirt or acacia recoloring. Savanna uses acacia, earth-toned paving
and regional planting. Taiga uses spruce, cobblestone, log storage and conifer
planting without additional moss. Snowy sites use spruce, stone, sheltered storage
and packed-ice ponds rather than exposed water ponds or outdoor crop fields.

Campfires start unlit so walking through an outdoor camp does not cause damage.
Tents use supported, connected cloth shells with open entry lanes and carpets,
not extra beds or false doors. Pens/hives do not spawn animals or bees. Containers
are empty decorations in the creative review world. Full-canopy wash courts were
removed after visual review showed excessive repeated shelters in Desert yards.

## Admission and limits

Building cells, roofs, furniture, beds, doors and existing access points cannot be
overwritten by outdoor additions. Every outdoor cell is outside the original
building's bounding rectangle. Separate circulation checks account for the native
15/16-high dirt-path surface and require the complete yard path network to connect.
Every module joins that network. Fences and ornaments cannot occupy route headroom.
Water sources have full-cube retaining sides and a base; ponds have a buried base.
Lanterns, pots, carpets and other fixtures have real supporting blocks. Persistent
leaves prevent the authored small trees from decaying. Planting uses appropriate
substrates. Soil under stumps is dirt, not an unstable dirt-path block.

Native Minecraft placement checks every new outdoor cell's `canSurvive`, including
plants beyond the older gallery attachment whitelist. Negative admission tests
reject occupied-building overlap, blocked yard routes, floating lamps, uncontained
water and unstable dirt paths beneath props. The existing distinct building-shell,
roof-gap, door, chair, bed-count, room/attic, lighting and furniture checks remain.

This is an authored flat review site, not a promise of automatic landscaping on
arbitrary terrain. Production adoption still needs the complete outdoor footprint
reserved and surveyed, slope/foundation handling, protected-land/occupancy checks,
funding and construction receipts. None of those safeguards are bypassed here.
No production catalog revision or normal-world selection behavior is changed.

## Review world and validation

Fresh final review profile: `build/biome-preview-catalog-r10-03`. Trial profiles
`r10-01` and `r10-02`, and previous revision saves, are preserved. The comparison
plots are enlarged only for the opted-in architecture review. All buildings retain
their identifying signs. Use `/emerald comparison visit <number>` for a front visit
or `/emerald comparison visit <number> back` for the landscaped rear.

Ranges remain Plains 1–75, Desert 76–150, Savanna 151–225, Taiga 226–300 and
Snowy 301–375. Compact designs occupy the final 22 numbers of each range.

Final Fabric native admission/assemble passes all 375 designs, all 28 outdoor
families and the five negative cases. There are 400,868 outdoor site cells and
858 complete doors with zero blocked sides. Deterministic uncached outdoor replay
and no duplicate feature types per site are checked for all 375 designs. The full
Fabric check/build passed, followed by a final native admission/assemble after the
canopy/variety refinement. The final common suite and filtered NeoForge loader test
pass (one loader test, zero failures/errors, including native blueprint admission).

The final live Fabric world placed all 375 pairs (750 structures), with native
survival checks on the outdoor cells and the existing fragile-attachment checks.
Its signature is `5e07ae5c1a263f96`, content revision 17, comparison schema 1. A
25-view QA capture completed for #1, #76, #151, #226 and #301, with rear elevations
and unchanged interiors inspected across all five styles. The player is left behind
Plains #1, with the HUD restored and Minecraft open. Native survival is verified for
all sites; screenshot inspection is representative, not a claim of manually walking
through each of the 375 yards.

Handbook accuracy review: checked the chapter routing, reader building-catalog
and terrain guidance, and corresponding compact lectern terrain text against the
unchanged production selection. This is an internal opted-in art review, not a new
live generation feature, so no unrelated recipe or production handbook text is
changed to advertise unreleased landscapes. Production-isolation coverage now
also guards `PreviewOutdoorPrograms`.

## Desert jungle-door refinement

All 75 Desert review designs now use jungle doors, including entrance doors and
room-partition doors. Room partitions reuse the shared regional palette instead
of a separate oak fallback. Door positions, facing, hinges and paired halves are
unchanged; no other region's palette or geometry is changed. Native admission
checks reject any non-jungle Desert door in both prototypes and the full catalog.

Fabric native admission/assemble, the common regression suite and the filtered
NeoForge loader test pass. The doorway audit still finds 858 complete doors with
zero blocked sides. A fresh live profile, `build/biome-preview-catalog-r10-04`,
placed all 375 pairs (750 structures) with the existing native survival gates.
Its signature is `e8c5f5160fb2f0cc`, content revision 17, comparison schema 1.
Minecraft is left open at Desert #76's front entrance, with identifying signs
throughout the catalog. Previous review saves are preserved.

Handbook accuracy review: rechecked chapter routing, building-catalog reader
guidance and the compact terrain page. This remains an opted-in review catalog,
not a change to released village generation, recipes or saved production plans;
those explanations remain accurate and need no unrelated edits. The review
branch remains separate from `main` pending the user's approval.

## Whole-catalog floating-geometry audit

Desert front-frieze teeth now sit immediately on the frieze instead of one block
above it. Open forge ranges could previously leave those sandstone ornaments
suspended above their pergola. The 20 compact home variants now include stone
footing below their side-garden fences. Freestanding grindstones in the catalog
and the earlier Desert forge prototype are explicitly floor-mounted rather than
using the wall-mounted default.

`PreviewSupportAudit` traces native outline-box contact from authored ground
cells through the complete building and its outdoor site. A disconnected group
fails just as an isolated block does. Full-block and half-slab gaps are distinguished;
continuous stepped roof edges are admitted, but sole diagonal point contact is
not. Fence, pane and wall arms are resolved against neighboring blueprint cells.
Lantern attachment hooks, rooted crops over farmland and lily pads over retained
water have explicit geometry handling; these still need a connected bearing and
retain the independent native survival checks. This is a flat authored-site
geometry admission check, not structural physics or permission to repair terrain.

The check is required when validating each review site, before its blocks can be
returned for gallery placement. It never automatically deletes decorations or
fills arbitrary air. Negative tests cover isolated trim, half-slab air gaps,
ungrounded brackets carrying lamps, point-only contact and connected-but-floating
canopies. Positive tests retain carried canopies, native fence arms, roof-edge
contact, supported pendant chains and rooted crops.

Fabric native admission/assemble and the filtered NeoForge loader test both pass
with zero disconnected shape cells in all 375 catalog designs and all 13 earlier
prototypes. The common regression suite passes, including the placement-wiring
and production-isolation guard. Existing room, bed, lighting, roof, doorway,
furniture and landscape admission checks continue to pass.

Fresh profile `build/biome-preview-catalog-r10-05` has completed all 375 comparison
pairs (750 structures), including native outdoor survival checks. Its signature
is `e9aa4a93a137eac5`, content revision 17, schema 1. Minecraft is left open at
Desert #76, with all identifying signs retained; earlier worlds are preserved.
The exhaustive claim here concerns automated blueprint admission, not a manual
visual inspection of every elevation or automatic edits to existing worlds.

Handbook accuracy review: checked chapter routing, building-catalog,
construction-safety and terrain reader guidance and compact terrain-page routing
against unchanged production behavior. No new recipe, live generation feature or
world-repair promise is introduced. Those explanations remain accurate; the
review-only check is documented here rather than advertised as a released change.
