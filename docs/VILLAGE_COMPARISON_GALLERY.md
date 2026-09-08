# Mod / vanilla village comparison gallery

This is a **separate, opt-in, curated comparison world**, not natural village generation and not
an aesthetic rerating of assets whose five-review limit is closed.

See the [8 September qualitative screenshot review](reviews/PALETTE_LANDSCAPE_COMPARISON_2026-09-08.md)
for the final 60-image sample, visual findings and remaining limitations.

## What it contains

Five material/biome districts: Plains, Desert, Savanna, Taiga and Snowy. Each district has all
52 active production masters and one standalone Bank on the right when viewed from the front,
paired with actual vanilla village NBT templates on the left:
**265 pairs / 530 buildings or reference structures**.
A small court per district adds a real vanilla town-center template and three homes, for
**550 total structures**. Its arrangement is curated; it does not test Minecraft's jigsaw
village-road generation, natural terrain fitting, settlement spawning or economic progression.

Vanilla structures are loaded through Minecraft's real structure-template manager. Their
blocks and block-entity data are not recolored or redesigned; jigsaw markers use their normal
final-state replacement and structure data markers are omitted. Template entities are not spawned.
Active data packs can override `minecraft:` template IDs; use the clean dedicated profile for
a stock comparison. An exact template ID is recorded for every pair. Normal direct `houses/`
and `town_centers/` resources are used; abandoned/zombie subfolders are excluded. Context courts
specifically use a town-center resource, not a meeting-point house annex.

| Mod role | Vanilla reference family | Comparison limitation |
| --- | --- | --- |
| Cottage / House | Small / medium / large houses | Dwelling comparison; not equal floor area |
| Inn | Butcher / fisher homes | Hospitality/scale analogue; vanilla has no inn |
| Warehouse | Tannery / shepherd workplace | Storage/workshop analogue |
| Granary | Farms | Agricultural function; not an enclosed granary |
| Smithy | Weaponsmith / toolsmith / armorer | Direct workplace comparison |
| Mine | Mason workplace | Stoneworking analogue; not a village mine |
| Market | Meeting point / fountain | Public-space analogue |
| Guard | Temple | Civic/tower analogue; not a guard post |
| Exchange / Bank | Library / cartographer | Civic/workplace analogue; not a financial building |

District ground and grass coloration are prepared in this isolated world only: sand in Desert,
dry Savanna biome grass, podzol/grass in Taiga, snow-block ground in Snowy and grass in Plains.
The vanilla templates retain their own authored ground/floor blocks. Both sides use the same
flat district grade and daylight. These display-ground changes are not a substitute for
production palette, planting or support improvements in the mod.

Every reference template starts at that grade, with its complete bottom layer supported by the
existing ground. Its authored air cells therefore cannot cut artificial trenches into the display
surface, and y=0 doorway bottoms remain usable. This placement does not alter the NBT or claim to
reproduce Minecraft's natural terrain projection; some references deliberately have raised floors.

## Safe startup and reopening

The exact save name is `TES_Village_Comparison`; the default Fabric profile is
`fabric/run/comparison-26.2`. The world is therefore located at:

```text
fabric/run/comparison-26.2/saves/TES_Village_Comparison
```

The existing `fabric/run/gallery-26.2/saves/TES_Blueprint_V2_Gallery`, archived galleries,
ordinary worlds and Carol's earlier screenshots are not altered by this tool.

For a first launch, create a **new Superflat / Creative** world with that exact save-folder name
in the dedicated profile. Disable normal generated structures for an unobstructed exhibition.
Do not copy another world's regions, entities, POI, economy or player data. If the prepared
comparison world already exists, simply run:

```powershell
.\scripts\open-village-comparison.ps1
```

The helper requires JDK 25, accepts `-JavaDirectory`, `-GameDirectory` and `-Loader neoforge`,
and refuses to copy, delete or replace a missing save. It starts the chosen loader's normal
development client using `scripts/village-comparison-client.init.gradle`.

Build proceeds one pair per server tick, followed by the five context courts; large plots can
make individual ticks longer. Biome filling is split into quart-aligned three-dimensional
slices, each checking at most 24,389 blocks against Minecraft's unchanged 32,768-block limit.
Progress appears in the game log. Commands inside the exact world:

```text
/emerald comparison info
/emerald comparison visit 1
/emerald comparison visit 53
/emerald comparison visit 54
/emerald comparison context 1
/emerald comparison build confirm
```

With 52 active masters, district ranges are Plains 1–53, Desert 54–106, Savanna 107–159,
Taiga 160–212, Snowy 213–265. The final pair in each district is its Bank. Signs identify
mod/vanilla sides; the visit response and the world's `comparison-index.md` provide full IDs,
relationships and context-court coordinates. Flying is enabled for visits.
Use `/emerald comparison context <1-5>` for the small vanilla village courts in district order.

A completed comparison save is **never automatically rebuilt, repainted or reconfigured** on
reopen. Player edits are left alone. A changed catalog signature refuses to reuse the old save;
preserve it as history and prepare a new isolated profile with a fresh same-name save.
Interrupted pairs are marked and fail closed rather than erasing partial blocks. Fully completed
pairs can be skipped when resuming the same unchanged catalog. No numerical review counter resets.

## Integration and optional capture

Both the `the_emerald_standard.structureGallery` and
`the_emerald_standard.villageComparison` JVM flags are required; comparison auto-build has its
own `.autoBuild` flag. The regular gallery auto-build/capture flags stay disabled.

Server hooks are `VillageComparisonGallery.autoBuildIfRequested(server)` at startup and
`VillageComparisonGallery.tick(server)` at end tick. Its `command()` branch is registered only
when comparison tooling is enabled, and every command checks the exact disposable save.

Read-only `entries(server)`, `contextCourts(server)` and `captureViews(server)` require
`isReady(server)`. Each pair exposes three exterior poses: a combined comparison, a mod detail
and a vanilla detail. They do not claim interior admission or natural village context.
`teleportForCapture(server, playerId, pose)` additionally requires the comparison `.capture`
flag and exact membership in the current pose list. All calls run on the integrated-server thread.

For an opt-in screenshot smoke, the init script accepts `-PtesComparisonCapture=true`,
`-PtesComparisonCapturePairs=1,54,107,160,213` and
`-PtesComparisonStopWhenComplete=true`. Without an explicit pair list, capture samples the first
master, twelfth master, final large exchange master and Bank in each district
(20 pairs / 60 views). The stop flag defaults
to false so the world remains available for manual inspection. These pictures are qualitative
comparison evidence, never a new numbered Carol review of an exhausted asset.
Capture polls the exact world's read-only failure status as well as readiness. A known build
failure stops waiting promptly; with the stop flag enabled it gracefully closes only that same
server/player session, preserving its partial save and diagnostic log for investigation.

`verifyPlan(level)` is read-only: it checks actual vanilla resources, every active master,
dialect coverage and plot envelopes without placing blocks or registering an economic village.

The Minecraft 26.2 bundled-resource audit decoded all 148 distinct normal candidates used by
the role families: all 55 dialect/role combinations have matches and every candidate has a
horizontal entrance connector. Their largest unrotated dimensions are 22 × 18 × 18 blocks,
within the reserved parcels in either horizontal rotation. This resource check is separate
from in-world placement and screenshot inspection.
