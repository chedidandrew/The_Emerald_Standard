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
