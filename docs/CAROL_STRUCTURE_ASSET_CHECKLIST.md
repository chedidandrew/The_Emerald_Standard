# Carol Structure Asset Checklist

This is the working evidence ledger for gallery content revision 9 and capture schema 3. It is
deliberately separate from the narrative findings in `CAROL_STRUCTURE_REVIEW.md`: an asset may
advance only after all of its required screenshots exist, are unobstructed, and visibly show the
named subject. A multi-view bundle counts as one review of that asset, not one review per PNG.

## Scorecard fields

Each accepted review records these fields. Weighted criterion values sum to 10.0.

| Field | Range | Required evidence |
| --- | ---: | --- |
| Exterior silhouette/detail | 0–1.3 | Complete front and rear/scene frames at readable scale. |
| Roof integrity/composition | 0–1.0 | Ridge, eaves, intersections, caps and attachments visible; no daylight seam or floating block. |
| Facade depth | 0–1.0 | Front plus a rear/side reading of frames, bays, recesses and support rhythm. |
| Interior layout/detail | 0–1.4 | Primary room; a second meaningful zone for every Large/Landmark and Bank. |
| Lighting/spawn safety | 0–1.4 | Exact no-skylight validator plus night frames showing natural-looking fixtures. |
| Role readability | 0–1.0 | Role must read visually without the manifest label. |
| Biome-palette cohesion | 0–0.8 | Representative role across Plains, Desert, Savanna, Taiga and Snowy. |
| Terrain/entrance integration | 0–1.0 | Grounded foundation and clear supported three-wide approach. |
| Doodad/scene craftsmanship | 0–1.1 | Grounded focal prop, secondary detail, negative space and wear/context. |
| Critical gates | pass/fail | Roof, access, grounding, panes and every usable floor at block light 7+. |
| Evidence | paths | Manifest path plus exact PNG sequence/file names used. |
| Review accounting | 1–5 | One accepted scored bundle increments once; camera/missing-view rejection does not increment; no asset may exceed five scored reviews. |
| Finding/action | text | Exact visible reason for every score below 8.0 and one bounded next change. |

`8.0` is the minimum passing score. A score of 8.0 or higher still fails when any critical gate
fails. Static lighting proof and visual fixture quality are recorded separately: the former can
clear spawn safety but cannot by itself raise the aesthetic score.

## Active geometry masters (52 masters / 260 biome renders)

Small and Medium masters require front, rear/doodad and primary-interior frames. Large and
Landmark masters also require a distinct secondary-interior frame. The rev9/capture-v3 bundle is
complete and file-admitted. Fifty-one master bundles are visually admitted for iteration 3;
`guard_blockhouse_03@2` has a non-consuming primary-interior camera reject and remains at review 2.

| # | Geometry master | Role | Scale | Required views | Valid reviews | Last score | Current evidence state |
| ---: | --- | --- | --- | ---: | ---: | ---: | --- |
| 1 | `cottage_hearth_01@2` | Cottage | Small | 3 | 3 | 7.0 | Rev9 #1–3; roof plane below 8 |
| 2 | `cottage_garden_02@2` | Cottage | Medium | 3 | 3 | 8.0 | **PASS** (#4–6) |
| 3 | `cottage_courtyard_03@2` | Cottage | Large | 4 | 3 | 8.0 | **PASS** (#7–10) |
| 4 | `cottage_bay_04@2` | Cottage | Small | 3 | 3 | 7.0 | Rev9 #11–13; flat dormer cap |
| 5 | `house_cross_01@2` | House | Medium | 3 | 3 | 7.5 | Rev9 #21–23; cupola/loft roof |
| 6 | `house_dormer_02@2` | House | Medium | 3 | 3 | 7.0 | Rev9 #24–26; slab-flat dormer |
| 7 | `house_arcade_03@2` | House | Large | 4 | 3 | 8.0 | **PASS** (#27–30) |
| 8 | `house_hall_04@2` | House | Small | 3 | 3 | 7.0 | Rev9 #31–33; rear access |
| 9 | `inn_gallery_01@2` | Inn | Medium | 3 | 3 | 6.5 | Rev9 #41–43; roof intersections |
| 10 | `inn_coachhouse_02@2` | Inn | Large | 4 | 3 | 8.0 | **PASS** (#44–47) |
| 11 | `inn_wayfarer_03@2` | Inn | Small | 3 | 3 | 7.5 | Rev9 #48–50; entrance identity |
| 12 | `warehouse_bay_01@2` | Warehouse | Medium | 3 | 3 | 6.5 | Rev9 #58–60; open roof monitor |
| 13 | `warehouse_crane_02@2` | Warehouse | Large | 4 | 3 | 7.0 | Rev9 #61–64; roof/loading identity |
| 14 | `warehouse_gabled_03@2` | Warehouse | Small | 3 | 3 | 7.0 | Rev9 #65–67; exposed roof panes |
| 15 | `granary_loft_01@2` | Granary | Medium | 3 | 3 | 7.5 | Rev9 #75–77; loading identity |
| 16 | `granary_windmill_02@2` | Granary | Landmark | 4 | 3 | 6.5 | Rev9 #78–81; windmill silhouette |
| 17 | `granary_cruck_03@2` | Granary | Small | 3 | 3 | 7.5 | Rev9 #82–84; rear loading identity |
| 18 | `smithy_courtyard_01@2` | Smithy | Medium | 3 | 3 | 7.5 | Rev9 #92–94; forge canopy |
| 19 | `smithy_hammerhall_02@2` | Smithy | Large | 4 | 3 | 7.6 | Rev9 #95–98; forge bays/spine |
| 20 | `smithy_lane_03@2` | Smithy | Small | 3 | 3 | 7.8 | Rev9 #99–101; role readability |
| 21 | `mine_headframe_01@2` | Mine | Large | 4 | 3 | 7.7 | Rev9 #109–112; mining machinery |
| 22 | `mine_winding_house_02@2` | Mine | Landmark | 4 | 3 | 7.7 | Rev9 #113–116; winding drum |
| 23 | `mine_adit_03@2` | Mine | Small | 3 | 3 | 7.4 | Rev9 #117–119; adit workflow |
| 24 | `market_cloister_01@2` | Market | Large | 4 | 3 | 7.3 | Rev9 #127–130; stall rhythm |
| 25 | `market_guildcourt_02@2` | Market | Large | 4 | 3 | 7.6 | Rev9 #131–134; civic focal point |
| 26 | `market_crossroads_03@2` | Market | Medium | 3 | 3 | 7.5 | Rev9 #135–137; canopy/merchandise |
| 27 | `guard_watch_01@2` | Guard | Medium | 3 | 3 | 7.9 | Rev9 #145–147; guard role detail |
| 28 | `guard_bastion_02@2` | Guard | Landmark | 4 | 3 | 8.1 | **PASS** (#148–151) |
| 29 | `guard_blockhouse_03@2` | Guard | Small | 3 | 2 | — | Rev9 #154 camera reject; no count |
| 30 | `exchange_hall_01@2` | Exchange | Large | 4 | 3 | 8.1 | **PASS** (#163–166) |
| 31 | `exchange_countinghouse_02@2` | Exchange | Landmark | 4 | 3 | 7.9 | Rev9 #167–170; teller/archive story |
| 32 | `exchange_branch_03@2` | Exchange | Medium | 3 | 3 | 8.0 | **PASS** (#171–173) |
| 33 | `cottage_longhouse_05@2` | Cottage | Medium | 3 | 3 | 7.0 | Rev9 #14–16; roof fins/valley |
| 34 | `cottage_orchardstead_06@2` | Cottage | Large | 4 | 3 | 7.5 | Rev9 #17–20; blank gable |
| 35 | `house_splitwing_05@2` | House | Medium | 3 | 3 | 7.5 | Rev9 #34–36; blank upper gable |
| 36 | `house_towercourt_06@2` | House | Landmark | 4 | 3 | 8.0 | **PASS** (#37–40) |
| 37 | `inn_tavern_04@2` | Inn | Medium | 3 | 3 | 7.5 | Rev9 #51–53; interior role story |
| 38 | `inn_courtyard_05@2` | Inn | Landmark | 4 | 3 | 8.0 | **PASS** (#54–57) |
| 39 | `warehouse_wharf_04@2` | Warehouse | Medium | 3 | 3 | 7.0 | Rev9 #68–70; crane support |
| 40 | `warehouse_basilica_05@2` | Warehouse | Landmark | 4 | 3 | 8.5 | **PASS** (#71–74) |
| 41 | `granary_stilt_04@2` | Granary | Medium | 3 | 3 | 7.0 | Rev9 #85–87; upper-bay workflow |
| 42 | `granary_silocomplex_05@2` | Granary | Large | 4 | 3 | 7.8 | Rev9 #88–91; chute/catwalk story |
| 43 | `smithy_corner_04@2` | Smithy | Medium | 3 | 3 | 7.4 | Rev9 #102–104; forge corner |
| 44 | `smithy_foundry_05@2` | Smithy | Landmark | 4 | 3 | 7.9 | Rev9 #105–108; casting workflow |
| 45 | `mine_drift_04@2` | Mine | Medium | 3 | 3 | 7.5 | Rev9 #120–122; rail/loading story |
| 46 | `mine_quarry_05@2` | Mine | Large | 4 | 3 | 7.8 | Rev9 #123–126; quarry machinery |
| 47 | `market_lane_04@2` | Market | Medium | 3 | 3 | 7.4 | Rev9 #138–140; stall variety |
| 48 | `market_bazaar_05@2` | Market | Landmark | 4 | 3 | 8.0 | **PASS** (#141–144) |
| 49 | `guard_gatehouse_04@2` | Guard | Large | 4 | 3 | 8.0 | **PASS; freeze** (#155–158) |
| 50 | `guard_citadel_05@2` | Guard | Landmark | 4 | 3 | 7.5 | Rev9 #159–162; interior role story |
| 51 | `exchange_loggia_04@2` | Exchange | Large | 4 | 3 | 8.0 | **PASS; freeze** (#174–177) |
| 52 | `exchange_bourse_05@2` | Exchange | Landmark | 4 | 3 | 7.9 | Rev9 #178–181; trading-floor story |

## Standalone Banks (5 dialects)

Every Bank requires front, rear, public/teller and secure-zone views. The exact no-skylight floor
gate is separate evidence and must remain green.

| Dialect | Required views | Valid reviews | Last score | Current evidence state |
| --- | ---: | ---: | ---: | --- |
| Plains | 4 | 3 | 7.8 | Rev9 #182–185; below 8 |
| Desert | 4 | 3 | 7.6 | Rev9 #186–189; below 8 |
| Savanna | 4 | 3 | 7.7 | Rev9 #190–193; below 8 |
| Taiga | 4 | 3 | 7.7 | Rev9 #194–197; below 8 |
| Snowy | 4 | 3 | 7.6 | Rev9 #198–201; below 8 |

## Reusable doodads (D1–D16)

The rev9 pass admitted fifteen current close-ups. D6 remains a camera-QA reject and consumes no
review. D2, D5, D7, D8, D11, D12, D13 and D16 pass; the other seven admitted motifs remain below 8.

| ID | Doodad | Required views | Valid reviews | Last valid score | Current evidence state |
| --- | --- | ---: | ---: | ---: | --- |
| D1 | Forecourt plant pedestal | 1 | 4 | 7.2 | Rev9 #202; below 8 |
| D2 | Forecourt cargo/material pedestal | 1 | 4 | 8.0 | **PASS** (#203) |
| D3 | Freestanding lamp post | 1 | 4 | 7.8 | Rev9 #204; below 8 |
| D4 | Rail-bound stacked log rack | 1 | 4 | 7.7 | Rev9 #205; below 8 |
| D5 | Slab-and-fence bench | 1 | 4 | 8.0 | **PASS** (#206) |
| D6 | Safe campfire nook | 1 | 1 | — | Rev9 #207 camera reject; no count |
| D7 | Garden work corner | 1 | 3 | 8.1 | **PASS** (#208) |
| D8 | Planter run | 1 | 4 | 8.2 | **PASS** (#209) |
| D9 | Crate cluster | 1 | 4 | 7.5 | Rev9 #210; below 8 |
| D10 | Hand cart | 1 | 3 | 7.8 | Rev9 #211; below 8 |
| D11 | Hitching rail | 1 | 4 | 8.0 | **PASS** (#212) |
| D12 | Feed trough | 1 | 4 | 8.1 | **PASS** (#213) |
| D13 | Hay pile | 1 | 4 | 8.0 | **PASS** (#214) |
| D14 | Smithy/mine material pile | 1 | 4 | 7.8 | Rev9 #215; below 8 |
| D15 | Tool rack | 1 | 2 | 7.8 | Rev9 #216; below 8 |
| D16 | Guard target rack | 1 | 4 | 8.1 | **PASS** (#217) |

## Biome-cohesion evidence (50 frames)

The complete capture must contain one representative Cottage, House, Inn, Warehouse, Granary,
Smithy, Mine, Market, Guard and Exchange front in each dialect below. This checks all ten roles
across five palettes while the 52 master rows continue to represent all 260 role/palette renders.

| Dialect | Roles required | Valid reviews | Last cohesion | Current evidence state |
| --- | ---: | ---: | ---: | --- |
| Plains | 10 | 3 | 7.7 | Rev9 complete; below 8 |
| Desert | 10 | 3 | 7.7 | Rev9 complete; below 8 |
| Savanna | 10 | 3 | 6.6 | Rev9 complete; below 8 |
| Taiga | 10 | 3 | 6.9 | Rev9 complete; below 8 |
| Snowy | 10 | 3 | 7.0 | Rev9 complete; below 8 |

## Complete-capture admission result

The rev9/capture-v3 `complete-pass-03` set passed every file/schema gate below. It contains 267 decoded
1920x1080 PNGs and a matching completion marker/manifest. Contact-sheet generation produced 17
verified pages and the synthetic verifier regression passed. Visual admission rejected only
master #154 and doodad #207; those two bundles did not consume a review. Formal outcomes are in
`CAROL_STRUCTURE_REVIEW.md`.

- A capture-v3 root and completion marker that agree on the layout signature and state gallery
  content revision 9, capture schema 3, exactly 267 shots, uniform dimensions, and a valid completion
  instant. Partial-range completion markers are camera-QA artifacts, not complete scoring evidence.
- Exactly the canonical sequence set 1–267 as unique `captured` rows: 181 masters, 20 Banks,
  16 doodads and 50 cohesion views.
- Exactly 52 Plains geometry subjects; all 181 master rows use dialect `plains`, and 25
  Large/Landmark subjects have a second interior.
- Exactly five Banks with four views each; every Bank row uses subject `bank`. D1-D16 each have
  one independent close-up.
- Biome cohesion is the exact `plains`, `desert`, `savanna`, `taiga`, and `snowy` dialect set,
  with ten role subjects per dialect and all five dialects for each subject.
- Every row has a vertical FOV from 50 through 70 degrees and a decoded, uniform 16:9 PNG of at
  least 1920x1080 whose dimensions match both manifest and completion marker.
- `isolated-clone` is allowed only for master/Bank front or rear exterior evidence. Master/Bank
  interiors, all doodads, and all biome-cohesion fronts must use their real `gallery` context.
- No stale layout/content revision, missing PNG, duplicate filename, HUD/chat, wall crop, blocked
  focal object, repeated non-zone interior or subject too small to judge.
- Lighting proof must state exact no-skylight coverage and any excluded cells. A missing lighting
  proof is a critical evidence gap, not an aesthetic deduction.
- Fifty-one master entries and all five Banks advanced to review 3. `guard_blockhouse_03@2` stayed
  at review 2 because #154 does not show a reviewable usable floor. Fifteen doodads advanced once
  under their existing per-item accounting; D6 stayed at review 1 because #207 is foreground-occluded.
- Any asset below 8.0 receives an exact bounded fix and fresh evidence. Stop after scored review 5;
  never convert a missing view or exhausted asset into a pass.
