# Architecture review revision 10 — outdoor rooms and working yards

Branch: `codex/biome-architecture-preview`. Review-only; not merged into main or
selected by normal development. Existing approved buildings and earlier review
saves are untouched. The building catalog still contains 375 designs, including
110 compact designs. The initial yard pass retained building geometry and interior
arrangements; later refinements are recorded below, including Taiga roof overhangs.

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

## Outdoor ground-level refinement

All 375 review landscapes move down exactly one block as complete sidecars.
Their soil and paving now replace the gallery's surrounding ground layer at
local Y=-1 instead of adding a raised rectangular platform at Y=0. Gardens,
fences, lamps, tents, work areas, carts, trees and other outdoor modules move
with their supports; pond and cistern bases, retained water and planting move
together too. Horizontal footprints, block states and feature selections are
unchanged. Original building cells, foundations, porches, doors and interiors
retain their authored elevation; this is not a whole-building translation.

Outdoor route coordinates and validation bounds follow the same offset. The
shape-contact audit now distinguishes original building foundations from
outdoor props at Y=0: those props require an actual connected bearing instead
of being accepted as ground themselves. Independent doorway, route headroom,
water containment and native block-survival checks remain enabled.

Native regression coverage verifies the exact one-block transform, unchanged
site identity and bounds, flush perimeter ground and unchanged original
building cells across every catalog design. An unsupported Y=0 outdoor prop
is rejected. Fabric admission/assemble and the filtered NeoForge loader test
pass; both the 375-design catalog and 13 earlier prototypes retain zero
disconnected shape cells. This exhaustive result is automated admission,
not a manual inspection of every elevation or a hillside-placement guarantee.

The common regression suite also passes. Fresh profile
`build/biome-preview-catalog-r10-06` completed all 375 comparison pairs (750
actual structures), retaining identifying signs and native survival gates.
Its signature is `d53c79885a20212b`, content revision 17, schema 1. An actual
Savanna #204 exterior frame was inspected: its outdoor surface is flush and
the former rectangular dirt ledge is gone. The optional five-view capture
batch did not complete because that design's interior camera pose intersects
a solid block; it is not presented as a completed capture batch. Interactive
review is reopened without capture at Savanna #204's front entrance.

Handbook accuracy review: checked chapter routing, building-catalog,
construction-safety and terrain reader guidance and compact terrain routing.
This remains an opted-in architecture-review catalog, not released village
generation, a new recipe or automatic repair of existing worlds. Those
explanations remain accurate without unrelated edits. Existing review worlds
are preserved and `main` remains untouched pending user approval.

## Taiga roof depth and compact review layout

Explicit Taiga roof courses now draft one-block front/rear end-grain projections
and lateral log eaves. Gables, turned ridges, lean-to ranges, courtyards,
multiple ranges and low roof decks keep their original pitch and ridge heights.
The projecting material is bark-on spruce log with the axis along its course.
Final composition adds only vacant cells: occupied chimneys, another roof range,
doorways and decorative fixtures are retained. Catalog and compact roofs stay
within their existing reserved padding; the two early Taiga prototypes reserve
a one-block border and translate their original shells into it. Normal gameplay
blueprints and other biomes' architecture are not migrated or reskinned.

The full comparison gallery now uses 112-block pair columns and 72-block rows
instead of 160 and 128. Biome districts occupy a three-by-two grid with 16-block
separators instead of a long five-district strip. Every complete outdoor site
and actual rotated vanilla reference is checked against its reserved half-parcel
before placement; compressing the gallery never clips yards or overlays another
design. Existing worlds retain their previous arrangement and signature.

Camera clearance finds the nearest actual preceding parcel in the same column,
including across the new district separators, and admits columns with no previous
parcel. It no longer assumes that every nonzero row has a predecessor exactly
one row pitch away. Front/rear visits and identifying signs are retained.

Native tests cover all ten roof forms, connected supports, one-block front/rear
and side projections, occupied-cell preservation and other-biome isolation.
All 375 complete outdoor footprints fit disjoint compact parcels. Layout tests
cover ordinary rows, lower-district boundaries, absent predecessors and rejection
of oversized references. Existing roof joins, rooms, beds, seating, lighting,
doorways and native survival gates remain enabled.

Fabric native admission/assemble, the common regression suite and the filtered
NeoForge loader test pass. All 375 designs and 13 early prototypes retain zero
disconnected shape cells; all 858 doors retain clear sides. Fresh compact profile
`build/biome-preview-catalog-r10-07` completed 375 pairs / 750 actual structures.
Its signature is `859759bfcddce8b1`, content revision 17, comparison schema 1.
After the camera-boundary correction, its five-view Taiga #233 capture completed;
the actual exterior and paired views were inspected for roof projection and
tighter spacing. Interactive review opens at Taiga #233 in daylight. Earlier
review worlds are preserved, and `main` remains untouched pending approval.

Handbook accuracy review: checked reader building-catalog guidance, chapter
routing and compact/terrain guidance against unchanged production behavior.
These are opt-in review geometry and gallery navigation refinements, not a
released generation mode, recipe or automatic repair of existing saves; no
unrelated handbook text is changed to advertise unfinished production adoption.

## Doorway glazing refinement

The final facade pass now replaces only existing glass-pane cells immediately
beside a door with solid, biome-matched jambs. Plains uses oak logs, Desert uses
cut sandstone, Savanna uses acacia logs, and Taiga/Snowy use spruce logs. An
affected jamb also finishes its existing pane cap up to the lintel height.
Existing opaque sills, lintels, neighbouring doors, fixtures, air and the rest
of the glazing remain unchanged. This avoids pretending that Minecraft's thin
door has a full face for pane attachment; ordinary native connection updates
still join the remaining panes to real frames after placement.

All final preview routes use the correction: early samples, legacy Plains,
full catalog, compact designs and aliased Banks. Final admission rejects any
pane left beside either door half. Native fixtures cover five palettes, four
door directions, both hinges, ground/upper floors, tinted panes, double doors,
iron bars, unchanged surrounding cells and idempotence. Existing open-door,
reachable-room, furniture, roof, lighting and native-shape support checks remain
active. The 375-design census found 12 affected designs and 46 pane-to-jamb
edits, with zero remaining unframed joins, zero disconnected shape cells and
858 doors with zero blocked sides. Production's frozen 52 revision-11 designs
and version-12 Bank remain unchanged.

Fabric native admission/assemble, the common regression suite and filtered
NeoForge loader test pass. Fresh profile `build/biome-preview-catalog-r10-08`
completed 375 pairs / 750 actual structures; signature `9df95e027dff37a9`,
content revision 17, comparison schema 1. The ten actual Minecraft views of
Taiga #229 and #270 completed; their exterior views and #229's doorway from
inside were inspected for clean pane termination against the new spruce jambs.
Interactive review starts at the corrected Taiga #229 in daylight, with labels
and `/emerald comparison visit <number>` navigation retained. Earlier worlds
are preserved, and this work stays on the unmerged architecture review branch.

Handbook accuracy review: checked building-catalog, construction-safety and
terrain explanations, long-form chapter routing and compact handbook routing.
This is isolated review geometry, not production adoption, a new recipe or an
automatic repair of existing villages. No unrelated handbook prose was changed
to advertise this unfinished production rollout.

## Meaningful windows and restrained lantern placement

Final review composition now checks glazing after room partitions, attached
wings, roof framing and facade detail are present. A retained window needs open
viewing space on both sides of an axis, including two cells of viewing depth;
a wall across a one-block service gap is not a meaningful view. Genuine room
windows and roof skylights remain. Blind panes/full glass are replaced in place
with nearby matching opaque wall material. No walls are carved, floors cut or
furnishings removed to manufacture a view. Door jamb edits retain their separate
audit scope and census.

Room lighting now accounts for all authored lanterns before proposing additional
fixtures. The final pass removes redundant close, visible lantern pairs and their
unused vertical pendant chains. Where a light is indispensable, relocation uses
vacant positions on existing desk or ceiling supports in the same space, outside
walking headroom; it never hangs new pendants from glazing. Close-pair detection
uses four-block spatial separation and opaque-wall occlusion, so lights serving
separate enclosed rooms do not count as one cluster.

Every removal or move must preserve spawn safety and at least block-light seven
on previously brighter reachable floor cells; originally dimmer cells retain
their previous level. Thus reducing visual clutter does not merely rely on
daytime sunlight. Final admission rejects blind windows and close lantern pairs
in every non-market building type. Eleven pairs remain across seven open-market
designs at separate task-light positions where neither deletion nor a supported
move preserves that coverage; these are an explicit market exception, not an
unreported zero-cluster claim.

Native fixtures cover five palettes, four window directions, direct and recessed
wall-backed views, one-block parallel gaps, retained two-block-deep room views,
skylights, protected-cell preservation and idempotence. Lighting fixtures cover
negative admission, adjacent lamp removal, chain cleanup, midnight floor-light
coverage, real supports and separation by an opaque partition. The final census
records 4,386 blind glazing cells replaced and 741 redundant lanterns removed
across 375 designs. All 375 designs and 13 early prototypes pass existing native
support/room/roof/door admission; no disconnected shape cells remain. Production's
frozen 52 revision-11 designs and version-12 Bank remain unchanged.

Fabric native admission/assemble, the common regression suite and the filtered
NeoForge loader test pass. Fresh compact profile `build/biome-preview-catalog-r10-10`
completed 375 pairs / 750 actual structures with signature `355369e80246f773`,
content revision 17 and comparison schema 1. Its ten-view Plains #14 / Taiga #239
capture completed. Actual exterior and entrance views were inspected; this
revealed the one-block service-gap case and prompted the stricter depth check
before the final rebuild. Interactive review reopens at Plains #14 in daylight,
with identifying signs and visit/back commands retained. Previous profiles,
including that first visual-check pass, are preserved. Main is unchanged.

Handbook accuracy review: checked long-form building-catalog, construction-safety
and terrain reader guidance, chapter routing and compact handbook routing. This
is opt-in review geometry only, not production adoption, a recipe change or an
automatic repair of existing saves. Existing explanations remain accurate;
unrelated handbook prose is not changed to advertise an unfinished rollout.

## Finished room ceilings and three-block minimum height

Room dividers previously ended three blocks above the floor while the roof could
be much higher. This left separated rooms open into a shared, unfinished roof
void. The attic admission check also accepted two-block walking clearance.

Enclosed review rooms now receive supported, biome-matched finished ceilings.
Ground-floor rooms have three clear structural blocks above their floor; compact
upper rooms use the same clearance between their second floor and ceiling.
Dividers extend to the actual ceiling and record every top column, including
door lintels, for final-composition validation. Existing climbable floor hatches
and functional approaches remain open. Low ground-floor beams are raised with
their supporting posts and attached fixtures rather than leaving disconnected
trim behind. Narrow appendages retain their authored roof where no fully carried
ceiling plate can be added.

New attics require three-block headroom, not two. Shallow roof edges become
finished knee walls outside the usable loft. Where a safe loft cannot fit, it is
not added; this preserves the regional exterior roof instead of stretching every
building into the same box. Vanilla two-block door openings and ladder hatches
remain normal transitions, not habitable rooms. Open market stalls stay open.

Native admission checks the final decorated plan, not only the room draft. All
375 designs and 13 early prototypes pass room/access, furniture, bed, seating,
roof, native-shape support and night-lighting checks. The catalog has 4,438 closed
divider columns and 67 accessible upper levels, with zero usable floor areas
below three-block structural headroom, including both halves of every bed.
Five-biome fixtures prove that a
three-block loft is accepted, a two-block loft is not built, and deliberately
lowered ceilings (over walking space or beds) or uncapped partitions are rejected.
Final glazing and lamp composition now records 5,921 blind-glazing infills and 936 redundant lantern
removals; the existing 11 coverage-dependent close pairs are confined to open
market designs. No production design, recipe or growth-selection behavior changes.

Validation: Fabric native admission and assemble, the complete common regression
suite and filtered NeoForge loader test pass. Fresh compact profile
`build/biome-preview-catalog-r10-12` completed 375 pairs / 750 actual structures,
signature `49fddb6fbdd3cdd0`, content revision 17 and comparison schema 1. Its
ten-view Plains #14 / Taiga #233 capture completed and actual interior screenshots
were inspected for finished ceilings and capped partitions. The earlier #57
compact-home batch in profile r10-11 stopped safely at a blocked camera pose;
that partial batch is not counted as completed visual evidence. Compact upper
rooms are covered by native height/access checks. Both intermediate profiles
remain preserved alongside earlier review saves. Interactive review opens at
Plains #14 in daylight, with signs and navigation commands retained. Main and
production's frozen 52 revision-11 designs / version-12 Bank remain unchanged.

Handbook accuracy review: rechecked the full building-catalog, construction-safety
and terrain reader text, their compact pages and chapter/lectern routing against
the implementation. This is opt-in review geometry only; the guidance on frozen
production plans, existing saves and construction safety remains accurate.
Unrelated handbook prose remains unchanged pending approved production adoption.

## Circulation, nighttime comfort and finished upper floors

Parallel room partitions now reserve at least three air columns between their
wall rows and reject layouts that introduce long, one-block-wide passages.
Existing cramped enclosed corridors are opened into neighboring rooms where
safe, including doubled interior wall courses. Narrow connecting wings can gain
a locally widened foundation, wall and carried ceiling without replacing the
whole building. Protected furnishings, fixture attachments and required routes
are retained. Standard doors and short entrance transitions remain vanilla-sized;
this is not a claim that every niche or doorway is three blocks wide.

Upper floor plates now replace intersecting pendant-chain and lantern cells with
solid flooring. Chains above the inserted floor are removed. Deliberate indoor
loft openings receive supported biome-matched fences or sandstone walls, while
ladder hatches and access targets stay usable. Final admission rejects unguarded
indoor upper-floor drops and long enclosed one-block corridors.

Roofed reachable walking cells must have block light at least seven without
skylight. Additional supported lights are placed only where native propagation
actually illuminates the dim target; the existing four-block, wall-aware lamp
separation and coverage-preserving pruning remain in force. This addresses dark
hallway ends without relying on daylight or adding a cluster of fixtures.

Five-biome native fixtures cover widening, deliberate dark-room rejection,
supported and repeatable lighting, chain-pierced floor repair, missing-floor
rejection and safe regional barriers around intentional openings. All 375 final
designs pass: zero long enclosed one-block corridors, zero unguarded indoor loft
drops, three-block structural headroom, 67 accessible upper levels and 3,611
closed divider columns. The final composition records 5,561 blind-glazing infills,
952 redundant lamp removals and zero close lantern pairs. Production's frozen
52 revision-11 designs and version-12 Bank remain unchanged.

Validation: Fabric native admission/assemble, the complete common regression
suite and filtered NeoForge loader test pass. Fresh compact labeled profile
`build/biome-preview-catalog-r10-13` placed all 375 pairs / 750 actual structures;
signature `1779fe71f53429a6`, content revision 17 and comparison schema 1. Its
15-view Plains #14 / Desert #90 / Taiga #233 capture completed. Actual nighttime
interiors were inspected for finished ceilings, readable lighting and clear
circulation. Upper-floor edge coverage is native validation, not a claim of
visually inspecting every loft. Earlier review worlds remain preserved.

Handbook accuracy review: rechecked the complete building-catalog,
construction-safety and terrain reader guidance, corresponding compact pages and
chapter/lectern routes. This remains an opt-in review catalog, not production
adoption, a recipe change or an automatic alteration of existing saves. The
existing guidance remains accurate; unrelated handbook prose is unchanged.
