# Architecture review revision 9 — composed elevations

Branch: `codex/biome-architecture-preview`. Review only; no merge to main and no
replacement of existing buildings or approved construction in ordinary worlds.

## Exterior work

All 375 review designs receive exterior work, including the 110 compact buildings,
the Plains legacy copies and the five stand-alone banks. Elevations are composed
after rooms and furniture exist. Exposure is established from the original plan,
not inferred from newly placed decorations. This keeps room partitions out of the
facade pass. Solid walls receive grouped glazing, alternating structural bays and
projecting pilasters or carried lantern brackets. Residential elevations receive
asymmetric, grounded planting where space permits. Existing windows are retained.
Open markets, mine hoists and other work platforms receive column bases and carried
lighting rather than artificial residential walls.

Composition depends on building purpose, design variant and elevation: broad
residential/civic glazing versus narrower store/workshop windows; different bay
spacing, framing, planted ends and bracket rhythms on each face. Existing distinct
roof/wing programs, loading courts, gardens, towers and galleries are retained.
Related buildings intentionally share a regional material vocabulary; this is not
a promise that no two buildings share a window or architectural component.

Plains uses oak/cobblestone framing and retains its legacy shape and oak roofs.
Desert uses pale cut/chiseled/smooth sandstone relief. Savanna uses acacia and
stripped-acacia framing. Taiga uses cobblestone and spruce framing, with its existing
horizontal spruce-log roofs untouched and no additional moss. Snowy retains its
spruce/stone language. No new triangular orange roof is introduced in Desert.

The pass changes exposed ground/upper-storey wall skins, suitable gable/curb infill
and outside details. Upper ties, vertical spines and paired diagonal braces break up
broad blank panels; clerestory/attic glazing is added only where there is actual empty
space behind it. Solid roof edges remain solid rather than receiving fake windows
with a wall immediately behind them. Stair/slab roof profiles and Taiga spruce-log
roof courses are never changed. Existing upper floors, room partitions, furniture,
ladders, doors, lighting and closed roof joins are retained. Front compositions
already filled with doors or service approaches are
protected rather than overwritten to force extra decoration. All four directions
are inspected; not every elevation requires a new ornament.

## Review world

Fresh profile: `build/biome-preview-catalog-r9-02`. Earlier signed review worlds are
preserved. The catalog contains 375 TES designs and 375 actual vanilla counterparts.
It opens behind Savanna **#173**, the loft granary, to make the rear work visible.

Use `/emerald comparison visit <number>` for the front comparison, or
`/emerald comparison visit <number> back` for the TES rear elevation. These commands
remain confined to the opted-in review save. All buildings have identifying signs.

| Style | Designs | Compact designs |
| --- | --- | --- |
| Plains | 1–75 | 54–75 |
| Desert | 76–150 | 129–150 |
| Savanna | 151–225 | 204–225 |
| Taiga | 226–300 | 279–300 |
| Snowy | 301–375 | 354–375 |

Useful Savanna examples: #151–156 cottages, #157–162 houses, #168–172 warehouses,
#203 stand-alone bank, #204–225 compact buildings. Fly around them to inspect sides.

## Safety and validation

The facade audit freezes all original non-wall-skin cells and allows only recorded
outside additions. Wall replacements must remain full collision cubes, including
full glass instead of empty cutouts. Door and service approach lanes are reserved.
New planting is grounded; each hanging lantern has a sturdy carried bracket.
Negative tests reject changes to beds/interior air, unsealed wall openings and missing
lantern bearings. Existing roof-gap, doorway, seating, bed-count, room/attic access,
lighting, furniture support, native survival and distinct-silhouette tests remain.

Fabric native admission passed all 375 designs and the new facade negatives; 858 doors
have zero blocked sides. 1,445 elevations receive work: 23,753 full wall/infill skin
edits and 7,548 outside detail cells. Existing exact-geometry uniqueness and normalized
silhouette checks remain in place. The first live review exposed an insufficiently
detailed high panel; the upper-elevation pass was added, and the first save preserved
rather than repainted. Final Fabric native admission/assemble and filtered NeoForge
loader tests pass. The complete common suite also passes, including handbook, loader
parity and preview-production isolation checks. Fabric's full `check build` passed
the production/frozen-blueprint gates; the final upper-elevation changes were then
re-admitted by the Fabric and NeoForge preview gates.

The final live profile completed all 375 signed pairs (750 actual structures), with
native fragile-block survival checks during placement. Five actual in-game QA views
of #173 were captured privately, including its rear elevation and both interiors.
The revised upper tie, framed attic window and lower bay rhythm were inspected in
the rear view. Capture completed successfully, HUD/FOV were restored, and the client
was returned to the rear of #173 and left open. Old saves, including the first
revision-9 trial, remain intact. No main-branch or ordinary world was changed.

## Handbook accuracy review

Reviewed long-form Building projects chapter routing, `building_catalog`,
`planning_building`, the compact legacy/lectern building pages and village design
localization against the unchanged production selector and frozen designs. This is
unreleased review-world art, so production handbook pages must not claim these new
facades are used in normal village development. No recipe, setting or economy behavior
changed. The handbook therefore requires no unrelated player-facing text edits.
