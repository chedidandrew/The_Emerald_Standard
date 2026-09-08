# Structure Gallery

For the latest complementary palettes and planting next to real vanilla templates, use the separate [Village Comparison Gallery](VILLAGE_COMPARISON_GALLERY.md). Existing review saves are retained as history, not remodeled. The active production catalog is authored revision 4 / Bank 7, with regular gallery content revision 12. Carol's last completed numerical review remains the historical content-11 evidence below; its scores do not certify these new changes.

The Structure Gallery is a developer-only review world that places every selected building immediately. It is intended for fast visual inspection and screenshots; it does not simulate village growth. The main matrix always follows the active catalog, which is currently 52 revision-4 masters spanning small, medium, large, and landmark scales; retired revision-1, revision-2, and revision-3 plans remain available to saved gameplay projects but are intentionally not mixed into the active review matrix.

## Safety and isolation

- The gallery is available only when the JVM property `the_emerald_standard.structureGallery=true` is set. Automatic construction independently requires `the_emerald_standard.structureGallery.autoBuild=true`.
- Gallery commands and automatic construction additionally require the exact world directory name `TES_Blueprint_V2_Gallery`.
- Use the dedicated game directory `fabric/run/gallery-26.2` (or an equivalent loader-specific directory). Do not put the gallery save in an ordinary development profile that contains manual test worlds.
- Building the gallery does not create economy villages, projects, bankers, balances, or other persistent economy state.
- Every displayed structure is generated through the production blueprint code. The gallery supplies deterministic inputs; it does not maintain a second set of showcase-only buildings.
- Gallery construction is intentionally one-shot. Create a fresh isolated save when a clean rebuild is needed.
- Automated capture of a completed gallery audits fragile attachments against final authored states, not superseded foundation layers. The audit never repairs a missing decoration or relaxes ordinary construction protection; it is not run by an ordinary interactive reopen.

These gates are defense in depth. Never enable the gallery property for a normal play world.

## Launching the gallery client

The gallery init script keeps the loader's normal launch arguments, sets the isolated run directory, enables both gallery properties, and appends Minecraft's quick-play arguments. On server start, the exact gallery save is preflighted and all 276 structures are built before the quick-play client joins. A completion marker keyed to both the catalog layout and explicit gallery render revision makes later launches idempotent while ensuring visual production changes invalidate stale galleries.

From the repository root, launch Fabric with:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.3+9' # Or your installed JDK 25 directory.
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\fabric\gradlew.bat --no-daemon -p .\fabric `
  -I "$PWD\scripts\structure-gallery-client.init.gradle" `
  -PtesGalleryGameDir="$PWD\fabric\run\gallery-26.2" `
  runClient
```

A fresh **Superflat** save must already exist at the following path because Quick Play can open an existing world but cannot create one:

```text
fabric/run/gallery-26.2/saves/TES_Blueprint_V2_Gallery
```

Use the exact directory name and leave the construction area untouched before its first dedicated launch. A normal terrain world is not suitable: the gallery intentionally places its fixed comparison grid at one shared surface height. The auto-builder sets the save to Creative, Peaceful, and the review-hub spawn after successful construction. New reviewers face north from the hub toward the complete gold-master matrix; turn around to reach the deliberately smaller controlled comparison lab.

Minecraft 26.2 stores world-generation settings separately in `data/minecraft/world_gen_settings.dat`.
Copying only `level.dat` does **not** create a usable fresh gallery. Prefer a newly created Superflat
save. If rebuilding from the existing gallery's settings, close Minecraft, archive the entire old
save outside `saves`, and preserve the world-generation, game-rule, weather, and world-clock metadata
along with `level.dat` in the new isolated save. Do not copy generated dimension chunks, player data,
economy data, or the old gallery completion marker. Keep the archive until the replacement opens and
the production gallery finishes successfully. Never apply this rebuild procedure to a play world.

The same init script supports NeoForge by replacing `fabric` with `neoforge` in the command and choosing a separate NeoForge gallery game directory. Keeping loader profiles separate prevents accidental save conversion.

## Automated review capture

The capture client uses the same production gallery and a deterministic 267-shot itinerary: 181 views covering all 52 geometry masters, 20 views covering all five Bank dialects, 16 close views covering D1-D16, and 50 front views covering ten representative roles across five biome dialects. From the repository root, run the next unused review pass with:

```powershell
.\fabric\gradlew.bat --no-daemon -p .\fabric `
  -I "$PWD\scripts\structure-gallery-capture-client.init.gradle" `
  -PtesGalleryGameDir="$PWD\fabric\run\gallery-26.2" `
  -PtesGalleryCaptureScope=complete `
  -PtesGalleryReviewPass=1 `
  runClient
```

The harness hides screens, chat, and the HUD; fixes the capture renderer at a 16:9 resolution of at least 1920x1080; validates every camera before recording; and stops without accepting a row when framing, line of sight, or foreign-block checks fail. If a normal gallery row cannot provide an unobstructed exterior view, the harness may stage the exact production-resolved fixture on a reusable isolated flat annex. That fallback is capture-only, compares the complete conservative structure prism at and above the authored origin after restoring the same pristine flat baseline, and is permitted only for master or Bank exterior views. Interiors, biome-cohesion views, and doodad close-ups always remain at their real gallery positions.

Every accepted row in the current `capture-v3` namespace records its vertical FOV, capture context, dimensions, identity, view, filename, and status in `manifest.csv`. A successful full pass also writes `capture-complete.txt` with the exact gallery layout signature, gallery content revision, capture schema revision, shot count, dimensions, and completion time. The last completed Carol review used gallery content revision 11 and capture schema 3; the strict contact-sheet verifier remains pinned to that historical evidence contract. New content-12 A/B evidence is produced by the separate comparison capture helper, not admitted as a new numerical Carol review. Previously admitted full passes retain their historical scores and review counts, but do not certify changed geometry. Partial-range completion markers remain camera-QA artifacts only. Generate Carol's evidence sheets only from a newly completed pass:

```powershell
.\scripts\carol-structure-contact-sheets.ps1 `
  -ManifestPath '<capture-pass>\manifest.csv' `
  -OutputDirectory '<capture-pass>\carol-contact-sheets' `
  -RequireCompleteReviewSet
```

That command decodes every PNG and fails closed unless all 267 canonical sequences and views exist, filenames stay inside the capture directory, dimensions are uniform 16:9 at 1920x1080 or greater and exactly match both manifest and completion marker, and isolated-clone contexts obey their restricted evidence scope. The contact sheets are navigation aids; Carol scores from the full-resolution originals. Any later visual-geometry change requires a gallery content-revision bump, a fresh disposable gallery build, and new evidence.

## Commands

The command index is one-based: valid structure numbers are `1` through `276`.

- `/emerald gallery build confirm` — manual fallback when the auto-build property is deliberately disabled. It generates the same complete gallery immediately; the final `confirm` token is required because this command places many blocks.
- `/emerald gallery info` — explain the gallery layout and structure ranges.
- `/emerald gallery overview` — move to an aerial overview for comparing rows and planning screenshots.
- `/emerald gallery visit <1-276>` — move directly to one structure and report its design metadata.

## Gallery index

| Index | Section | Coverage |
| ---: | --- | --- |
| 1–260 | Gold-master matrix | Fifty-two complete active revision-4 blueprints rendered at full visual stage with prosperous production dressing, once in each of the five biome dialects. No floorplan, roof, frontage, or interior is recombined. |
| 261–271 | Controlled Blueprint lab | The revision-4 `house_cross_01` Plains House compared one axis at a time across stages, semantic palettes, dressing kits, approved mirroring, and all four rotations. |
| 272–276 | Banks | One production Bank palette for each biome dialect. |

The main matrix is paged in catalog order, with at most 12 designs per page and one row for each of `Plains`, `Desert`, `Savanna`, `Taiga`, and `Snowy`. Pages one through five contain masters 1–12 (indices 1–60), 13–24 (indices 61–120), 25–36 (indices 121–180), 37–48 (indices 181–240), and 49–52 (indices 241–260), respectively. Every matrix entry uses stage 2 and the prosperous dressing kit so its richest production-authored architecture, yard, and role props are visible. The controlled lab remains the place to compare restrained and intermediate states. Each design should read as its own building before palette differences are considered:

| Column | Active master | Structural identity |
| ---: | --- | --- |
| 1 | `cottage_hearth_01@3` | Asymmetric saltbox cottage with a dominant masonry hearth and shed wing |
| 2 | `cottage_garden_02@3` | Garden cottage with a glasshouse, pergola, and clipped gable |
| 3 | `cottage_courtyard_03@3` | Large courtyard residence with offset wings, layered gables, porch, and garden enclosure |
| 4 | `cottage_bay_04@3` | Compact bay-window cottage with a sheltered stoop and dense domestic detail |
| 5 | `house_cross_01@3` | True cross footprint with crossed gables and a raised lantern |
| 6 | `house_dormer_02@3` | Tall mansard house with dormers and a projecting bay |
| 7 | `house_arcade_03@3` | Large arcaded residence with a multi-mass roofline and formal frontage |
| 8 | `house_hall_04@3` | Compact hall house with an emphasized hearth, porch, and readable interior |
| 9 | `inn_gallery_01@3` | Broad U-plan inn with a sheltered gallery and gatehouse frontage |
| 10 | `inn_coachhouse_02@3` | Grand coachhouse inn with carriage court, balconies, and multiple public rooms |
| 11 | `inn_wayfarer_03@3` | Small roadside inn with a covered entry and compact guest hall |
| 12 | `warehouse_bay_01@3` | Three-bay warehouse with a monitor roof, loading dock, and hoist |
| 13 | `warehouse_crane_02@3` | Large quay warehouse with a crane, loading galleries, and layered storage bays |
| 14 | `warehouse_gabled_03@3` | Compact gabled warehouse with a strong loading front and practical storage interior |
| 15 | `granary_loft_01@3` | Raised granary with an open undercroft, loft, and grain chute |
| 16 | `granary_windmill_02@3` | Landmark windmill granary with a tall mill tower, sail cross, and grain works |
| 17 | `granary_cruck_03@3` | Compact cruck-framed granary with raised storage and loading details |
| 18 | `smithy_courtyard_01@3` | Open L-plan smithy yard with twin forge flues |
| 19 | `smithy_hammerhall_02@3` | Grand hammer hall with an open forge loggia, work bays, and prominent chimneys |
| 20 | `smithy_lane_03@3` | Compact lane smithy with a working forge, side shelter, and material yard |
| 21 | `mine_headframe_01@3` | Freestanding headframe, adit, rails, and side tool sheds |
| 22 | `mine_winding_house_02@3` | Large winding house with headframe, rails, machinery bays, and service sheds |
| 23 | `mine_adit_03@3` | Compact adit works with rail access, timber shoring, and tool staging |
| 24 | `market_cloister_01@3` | Arcaded market with distinct bays and a central bell rotunda |
| 25 | `market_guildcourt_02@3` | Grand guild court with a formal arcade, pavilion, stalls, and civic centerpiece |
| 26 | `market_crossroads_03@3` | Compact crossroads market with readable stalls, shelter, and public circulation |
| 27 | `guard_watch_01@3` | Battered masonry guard tower with an overhanging timber watch room |
| 28 | `guard_bastion_02@3` | Landmark bastion with twin defensive masses, elevated watch spaces, and battlements |
| 29 | `guard_blockhouse_03@3` | Compact blockhouse with a tall watch profile and defensible entrance |
| 30 | `exchange_hall_01@3` | Basilica-like civic hall with aisles, clerestory, portico, and cupola |
| 31 | `exchange_countinghouse_02@3` | Grand countinghouse with wings, dormers, teller rail, offices, and a civic facade |
| 32 | `exchange_branch_03@3` | Compact branch exchange with a clear public counter and focused banking interior |
| 33 | `cottage_longhouse_05@3` | Low linear longhouse with a deep hearth, framed bays, and an attached working yard |
| 34 | `cottage_orchardstead_06@3` | Large orchard estate with offset domestic wings, garden frontage, and layered rural roofs |
| 35 | `house_splitwing_05@3` | Split-wing residence with a recessed entrance court and independently expressed roof masses |
| 36 | `house_towercourt_06@3` | Landmark courtyard manor organized around a tall residential tower and formal gallery |
| 37 | `inn_tavern_04@3` | Established roadside tavern with a public porch, taproom mass, guest wing, and service yard |
| 38 | `inn_courtyard_05@3` | Landmark courtyard inn with multiple lodging wings, balconies, carriage access, and a civic-scale roofline |
| 39 | `warehouse_wharf_04@3` | Wharf warehouse with stepped loading decks, strong storage bays, hoist details, and practical interior circulation |
| 40 | `warehouse_basilica_05@3` | Landmark aisled warehouse with clerestory storage hall, loading nave, and monumental trade frontage |
| 41 | `granary_stilt_04@3` | Raised stilt granary with a ventilated undercroft, external loading access, and distinct grain handling details |
| 42 | `granary_silocomplex_05@3` | Large multi-volume silo complex with elevated links, processing bays, and a dominant agricultural silhouette |
| 43 | `smithy_corner_04@3` | Corner smithy with an open forge court, covered work bays, material racks, and a compact chimney profile |
| 44 | `smithy_foundry_05@3` | Landmark foundry with furnace hall, tall stacks, gantries, casting yard, and heavy industrial massing |
| 45 | `mine_drift_04@3` | Drift-mine works with a fortified adit, rail approach, timber sheds, and visible ore-handling equipment |
| 46 | `mine_quarry_05@3` | Large quarry complex with a broad cutting yard, machine house, raised hoists, and layered service structures |
| 47 | `market_lane_04@3` | Linear market lane with varied covered stalls, merchant storage, strong entrances, and open public circulation |
| 48 | `market_bazaar_05@3` | Landmark covered bazaar with multiple courts, arcades, pavilion roofs, and a central civic focus |
| 49 | `guard_gatehouse_04@3` | Defensible gatehouse with a pass-through arch, flanking watch rooms, battlements, and guard fixtures |
| 50 | `guard_citadel_05@3` | Landmark citadel with layered defensive masses, elevated patrol spaces, towers, and commanding rooflines |
| 51 | `exchange_loggia_04@3` | Large exchange loggia with an arcaded public frontage, teller hall, offices, and a clear civic identity |
| 52 | `exchange_bourse_05@3` | Monumental bourse with a grand trading floor, deep portico, offices, roof lantern, and skyline-defining massing |

## Screenshot workflow

1. Start the isolated client. The dedicated launch auto-builds the untouched Superflat save, then Quick Play opens `TES_Blueprint_V2_Gallery` directly. The first launch takes longer while all 276 structures and their chunks are prepared.
2. Run `/emerald gallery overview` for an aerial pass. Capture wide shots of each section and note any obvious collisions, floating foundations, disconnected paths, broken windows, or roof artifacts.
3. Run `/emerald gallery visit 1`, inspect the exterior and interior, take screenshots, then advance through the range with `/emerald gallery visit 2`, and so on.
4. Include the structure index in each screenshot filename or review note. `/emerald gallery visit` reports the type, dialect, stage, rotation, immutable template revision, semantic palette, dressing kit, and mirror state needed to reproduce a problem.
5. Review the controlled lab after the gold-master matrix. Its one-axis comparisons make stage, palette, dressing, mirror, and rotation defects easy to isolate.
6. For a clean second pass, stop Minecraft, archive or remove the reviewed disposable save, and create a fresh Superflat save with the same exact `TES_Blueprint_V2_Gallery` directory name. Never rebuild over the reviewed copy.

For consistent comparisons, use Creative mode, clear weather, a fixed daytime, the same field of view, and the same graphics settings for the entire screenshot set.
