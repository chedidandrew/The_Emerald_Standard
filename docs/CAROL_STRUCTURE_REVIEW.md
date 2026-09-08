# Carol Structure Quality Review

Carol is the independent visual reviewer for Blueprint V2. This ledger groups the 260 biome
renders by their 52 immutable geometry masters while retaining a separate biome-cohesion check.
The five standalone Banks and every reusable yard-doodad archetype are scored independently.

## Scoring contract

A `5` is a coherent casual-player build. An `8` is a polished build that could credibly have been
hand-authored by an experienced Minecraft builder. A `10` is portfolio-grade. The weighted score
uses the following ten-point rubric:

| Criterion | Points | Review question |
| --- | ---: | --- |
| Exterior silhouette and detail | 1.3 | Does the massing remain distinctive from ordinary play distance? |
| Roof integrity and composition | 1.0 | Is the roof sealed, supported, layered, and free of floating ornaments or accidental gaps? |
| Facade depth | 1.0 | Do frames, bays, eaves, plinths, and recesses create deliberate depth on several sides? |
| Interior layout and detail | 1.4 | Is the interior navigable, furnished, zoned, and visually complete rather than an empty shell? |
| Lighting and spawn safety | 1.4 | Are all usable interior floors naturally lit while fixtures remain plausible and attractive? |
| Role readability | 1.0 | Can a player identify the building's purpose before reading its name? |
| Biome-palette cohesion | 0.8 | Does the material dialect suit the biome without flattening the building into one texture? |
| Terrain and entrance integration | 1.0 | Does the approach meet grade cleanly with a supported, unobstructed three-wide route? |
| Doodad and scene craftsmanship | 1.1 | Do props form believable scenes with shape, support, and negative space? |

An asset passes only when its weighted score is at least `8.0` **and** none of these critical gates
fail: weatherproof roof, reachable entrance/workstations, grounded attachments, connected panes,
or any usable interior floor below the required block-light threshold. One accepted, scored
evidence bundle increments that asset's review count once, regardless of whether the bundle contains
one, three, or four PNGs. The provisional iteration-1 assessment counts as the first review;
camera/missing-view rejection does not count. No asset may exceed five scored reviews.

## Evidence status

Iteration 1 was a provisional code-and-gallery pass using the authored catalog, validators, the
existing gallery overview, and the supplied dark-interior screenshots. The authoritative visual
pass is now the completed rev9/capture-v3 267-shot set recorded below. A score at or above 8 is
final only when that current evidence also confirms every critical gate. Earlier rev4/rev5 capture
records are retained as historical review-accounting evidence; they cannot admit or authorize a
current complete batch.

The first three-shot automation smoke proved that deterministic capture works, but Carol rejected
that batch as scoring evidence. The cottage occupied only about one quarter of each exterior frame,
the rear props were too small to judge, the interior camera faced a tight wall/corner crop, and HUD
chat obscured the images. Rejected camera-QA smoke does **not** increment the cottage review count.
The accepted capture gate is a clean frame in which the subject occupies roughly 60–75% of the
image; rear props are legible; a room is viewed across its usable space; and Large/Landmark masters
show at least two meaningful interior zones.

`scripts/carol-structure-contact-sheets.ps1` converts a capture manifest into paged master, Bank,
doodad, and biome comparison sheets. With `-RequireCompleteReviewSet`, it fails closed unless the
capture-v3 root, rev9/schema3 completion marker, layout signature, canonical sequences and coverage,
contexts, FOV, declared dimensions, and decoded PNG dimensions all satisfy the current contract.
Running it without that switch may produce navigation sheets for a partial camera smoke, but those
sheets do not qualify as complete scoring evidence.

### Current rev9/capture-v3 evidence checklist

- Accept only a capture-v3 directory whose completion marker identifies gallery content revision 9,
  capture schema 3, the same layout signature encoded by the capture root, and exactly 267 shots.
  Its manifest must have exactly 267 unique, canonical `captured` rows and audited per-row vertical
  FOV, capture context, declared dimensions, and matching decoded PNG dimensions:
  181 master views, 20 Bank views, 16 independent doodad close-ups, and 50 biome-cohesion fronts.
- Require every master row to use dialect `plains`, every Bank row to use subject `bank`, and the
  cohesion matrix to contain exactly ten role subjects across each of `plains`, `desert`,
  `savanna`, `taiga`, and `snowy` (five dialect frames per role).
- Reject the batch before scoring if its persisted gallery content revision predates the final
  lighting/craft geometry, any PNG is missing, filenames repeat, HUD/chat obscures a subject, or a
  required subject occupies too little of its frame. A camera-QA rejection does not consume one of
  an asset's five review iterations.
- Review all 52 Plains geometry masters from front, rear/doodad, and primary interior; also review
  the second interior for all 25 Large/Landmark masters. Inspect SMALL roof/eave seams and compact
  circulation first because the latest craft pass changed those shared systems.
- Review each Bank in front, rear, public/teller, and secure-zone views. Prioritize trim-course
  dominance, rear-ledger proportion, the 5x5 cupola-cap overhang, Desk sightline, and terrain-grade
  integration; static startup checks do not decide these visual questions.
- Review D1-D16 from their independent connected-cluster close-ups. Inspect D3 bracket support and
  D10 wheel/axle/bed/handle coherence first, then require each prop to read as one deliberate scene.
- Compare ten representative roles across all five biome dialects. A master cannot pass globally
  when its geometry scores 8+ in Plains but a dialect collapses its depth or material hierarchy.
- For every accepted asset review, record iteration number, evidence filenames, weighted score,
  failed criteria, and one actionable recommendation. Advance exactly once per accepted bundle.
  Assets below 8 return for a targeted fix and fresh evidence; stop after iteration 5 and report
  the remaining shortfall rather than silently granting a pass.

### Historical camera QA — rev4/schema2 (superseded)

Camera QA for layout signature `6ff0f165d54125cd`, content revision 4, capture schema 2 was
accepted at the time as a three-shot framing smoke. Its three 3840x2160 frames were clean and
HUD-free; the front and rear preserved the entire roof plus the authored approach/prop scene at a
readable scale, and the night interior looked across the usable room. That camera-only approval
consumed no asset review iteration. It authorized only the then-planned rev4/schema2 run and is not
admissible proof for, or authorization of, the current rev9/schema3 267-shot capture.

The final static whole-building lighting run covers all 52 masters. Every weather-covered,
two-block-clear structural floor cell receives authored block light 7 or higher under exact
no-skylight propagation, including exterior-connected stalls, loading bays, mine sheds,
undercrofts, and work areas. All 52 passed in 5,204 ms with zero dim/spawn-unsafe floors, closed
pockets, or reserved-air collisions. Composition adds 367 emitted fixtures (1–19 per master; the
maximum remains `exchange_bourse_05@2`). The expanded gate initially caught four real dim areas and
one fixture-chain circulation collision; the final craft uses short orchard-pergola lanterns,
grounded loading/hammer-hall inspection lights, a quarry rail-work lamp, and a global reserved-air
mount/chain guard. This is valid critical-gate evidence, but it does not raise an aesthetic score
until those fixtures are visible and natural-looking in accepted screenshots.

The revised standalone Bank has a separate exact night-interior proof. Its target set contains
every usable two-block-clear floor cell, including the green-carpet public aisle, and its
distributed lobby, teller, vault, and chandelier fixtures keep that full set at block light 7 or
higher with skylight excluded. The Bank also has startup guards for its three-wide approach,
Exchange Desk sightline and anchor, public/teller/secure zoning, roof and cupola continuity,
chimney support, and five distinct biome signatures. This clears the Bank's static lighting gate;
the current rev9 four-view visual scores are recorded below.

## Formal rev9/capture-v3 visual audit — iteration 3

The current authoritative capture completed all 267 canonical frames at 1920x1080. The strict
contact-sheet verifier admitted the layout signature, revision 9/schema 3 completion marker,
uniform decoded dimensions, canonical sequence/coverage/context/FOV fields, five-dialect cohesion
matrix, and all expected subjects. It generated 17 sheets; the synthetic positive/negative verifier
regression also passed.

- Evidence root: fabric/run/gallery-26.2/screenshots/tes-structure-review-3aa7414fb066bc3d-capture-v3/complete-pass-03
- Manifest: complete-pass-03/manifest.csv
- Contact sheets: complete-pass-03/carol-contact-sheets
- Independently regenerated verified sheets: complete-pass-03/carol-contact-sheets-verified
- Static critical evidence: live 276/276 structure admission remains green, including
  weatherproof-roof, access, grounding, pane-connectivity, reserved-air and exact no-skylight
  block-light-7 checks. Those checks do not substitute for visual craft.

All Bank and biome bundles are admissible. Fifty-one master bundles are admissible. The sole
master camera rejection is guard_blockhouse_03 frame #154, which points at its ceiling/ladder and
does not show enough usable floor to judge the interior; that asset does not advance. D6 is likewise
still camera-occluded and does not advance. No asset exceeds five valid reviews.

### Doodads first

Fifteen close-ups are admissible and advance exactly once; D6 remains a non-consuming camera
rejection. Eight of the fifteen scored motifs meet 8.0.

| ID | Evidence | Valid review | Score | Gates | Result / bounded next change |
| --- | ---: | ---: | ---: | --- | --- |
| D1 | #202 | 4 | 7.2 | Pass | Join the isolated pot and lamp cubes with an L-shaped low planter, leaf beat and continuous wear. |
| D2 | #203 | 4 | 8.0 | Pass | **PASS.** Preserve the unified strapped cargo/perch micro-scene. |
| D3 | #204 | 4 | 7.8 | Pass | Give the lamp a stronger stone foot, diagonal knee and compact cap so it reads as a crafted standalone fixture. |
| D4 | #205 | 4 | 7.7 | Pass | Expose log ends on both sides and add contrasting bindings plus one visible diagonal rack brace. |
| D5 | #206 | 4 | 8.0 | Pass | **PASS.** Preserve the armed/end-capped bench and side planting. |
| D6 | #207 | 1 | — | QA reject | Recapture from the open opposite side; a foreground wall/bench still hides the campfire core. |
| D7 | #208 | 3 | 8.1 | Pass | **PASS.** Preserve the composed garden-work scene. |
| D8 | #209 | 4 | 8.2 | Pass | **PASS.** Preserve its asymmetric planted run and grounded edge. |
| D9 | #210 | 4 | 7.5 | Pass | Replace the gray rail-topped monolith with three readable wooden crate faces at staggered heights on a pallet. |
| D10 | #211 | 3 | 7.8 | Pass | Lower and expose both wheel cues beneath a narrower bed; keep the axle and twin handles visibly separate. |
| D11 | #212 | 4 | 8.0 | Pass | **PASS.** Preserve the thin tied rail, companion feed and worn ground. |
| D12 | #213 | 4 | 8.1 | Pass | **PASS.** Preserve the filled trough, lips and site context. |
| D13 | #214 | 4 | 8.0 | Pass | **PASS.** Preserve the staggered, bound bales and straw spill. |
| D14 | #215 | 4 | 7.8 | Pass | Remove the furnace-like black square/pillars and compose an irregular stair/slab rubble pile with ash and ore accents. |
| D15 | #216 | 2 | 7.8 | Pass | Open/lower the heavy canopy and hang recognizable iron-bar/chain tools against a contrasting backboard. |
| D16 | #217 | 4 | 8.1 | Pass | **PASS.** Preserve the two-height targets, backstop and firing-line story. |

### Geometry masters

Thirteen of 51 admissible master bundles meet 8.0. Thirty-eight remain below the target, with a
mean score of 7.55. The largest shared defect is roof finish: unresolved stair valleys, flat dormer
caps, exposed monitors and oversized blank roof planes remain visible even after the substantial
interior craft pass. The second shared defect is role programming: large rooms often contain
several good props but lack one dominant machine/counter/workflow that visually organizes the
space.

| Master | Evidence | Review | Score | Gates | Result / bounded next change |
| --- | ---: | ---: | ---: | --- | --- |
| cottage_hearth_01@2 | #1–3 | 3 | 7.0 | Pass | Replace the stacked front-left/high roof collision with one continuous stepped plane from ridge to eave. |
| cottage_garden_02@2 | #4–6 | 3 | 8.0 | Pass | **PASS.** Preserve its greenhouse/workspace craft. |
| cottage_courtyard_03@2 | #7–10 | 3 | 8.0 | Pass | **PASS.** Preserve the newly distinct occupied zones. |
| cottage_bay_04@2 | #11–13 | 3 | 7.0 | Pass | Replace the flat five-wide dormer cap with a pitched mini-gable tied into the main roof. |
| cottage_longhouse_05@2 | #14–16 | 3 | 7.0 | Pass | Remove the upright dark stair fins at the left cross-gable valley and close one continuous roof plane. |
| cottage_orchardstead_06@2 | #17–20 | 3 | 7.5 | Pass | Add one centered capped dormer to break the uninterrupted front roof and support the orchard-processing story. |
| house_cross_01@2 | #21–23 | 3 | 7.5 | Pass | Seat the cupola on a 3x3 curb and close the surrounding roof stairs continuously around its base. |
| house_dormer_02@2 | #24–26 | 3 | 7.0 | Pass | Replace the slab-flat upper cap with a pitched five-wide dormer roof and one-block overhang. |
| house_arcade_03@2 | #27–30 | 3 | 8.0 | Pass | **PASS.** Preserve the occupied arcade and room zoning. |
| house_hall_04@2 | #31–33 | 3 | 7.0 | Pass | Move the rear wagon/stall two blocks off the doorway and continue the path to the threshold. |
| house_splitwing_05@2 | #34–36 | 3 | 7.5 | Pass | Add a centered two-pane upper window with a dark lintel to the blank front gable. |
| house_towercourt_06@2 | #37–40 | 3 | 8.0 | Pass | **PASS.** Preserve the distinct towercourt zones and circulation. |
| inn_gallery_01@2 | #41–43 | 3 | 6.5 | Pass | Consolidate the front roof stack into one centered gable and close both dangling stair valleys. |
| inn_coachhouse_02@2 | #44–47 | 3 | 8.0 | Pass | **PASS.** Preserve the differentiated tack/service and guest rooms. |
| inn_wayfarer_03@2 | #48–50 | 3 | 7.5 | Pass | Add a three-block-deep covered entry and traveler sign centered on the front door. |
| inn_tavern_04@2 | #51–53 | 3 | 7.5 | Pass | Build a compact five-block bar/backbar along the long blank wall beneath the existing light. |
| inn_courtyard_05@2 | #54–57 | 3 | 8.0 | Pass | **PASS.** Preserve the clearly different occupied wings. |
| warehouse_bay_01@2 | #58–60 | 3 | 6.5 | Pass | Enclose the roof monitor on four sides and flash its base with one continuous stair/slab ring. |
| warehouse_crane_02@2 | #61–64 | 3 | 7.0 | Pass | Cut a centered five-wide loading dormer above the entrance and tie it to the crane workflow. |
| warehouse_gabled_03@2 | #65–67 | 3 | 7.0 | Pass | Give the exposed clerestory panes full side cheeks and a continuous weatherproof cap. |
| warehouse_wharf_04@2 | #68–70 | 3 | 7.0 | Pass | Add diagonal knees to both crane uprights and one crossbeam tying the portal to the shed. |
| warehouse_basilica_05@2 | #71–74 | 3 | 8.5 | Pass | **PASS.** Preserve the strongest nave/rack/hoist composition. |
| granary_loft_01@2 | #75–77 | 3 | 7.5 | Pass | Add a centered three-wide rear loft hatch with a one-block hoist canopy. |
| granary_windmill_02@2 | #78–81 | 3 | 6.5 | Pass | Replace the comb-like pole with four balanced radial sails around a visible hub clear of the eave. |
| granary_cruck_03@2 | #82–84 | 3 | 7.5 | Pass | Set a capped three-wide loading dormer into the rear roof above the wagon. |
| granary_stilt_04@2 | #85–87 | 3 | 7.0 | Pass | Add a visible hanging light and a chute/bin workflow to both otherwise token-furnished upper bays. |
| granary_silocomplex_05@2 | #88–91 | 3 | 7.8 | Pass | Join both silo volumes with a visible exterior chute/catwalk and continue that grain route through both interiors. |
| smithy_courtyard_01@2 | #92–94 | 3 | 7.5 | Pass | Cluster forge, anvil, grindstone and stock racks beneath one authored canopy on the bare stone apron. |
| smithy_hammerhall_02@2 | #95–98 | 3 | 7.6 | Pass | Break the monolithic roof/front into two vented forge bays and connect the stations with a material/rail spine. |
| smithy_lane_03@2 | #99–101 | 3 | 7.8 | Pass | Make the front side a visible tool-display/forge bay and replace the T-shaped flue with a tapered supported vent. |
| smithy_corner_04@2 | #102–104 | 3 | 7.4 | Pass | Open the chimney-side corner as the principal forge and group hearth, anvil, tools and fuel there. |
| smithy_foundry_05@2 | #105–108 | 3 | 7.9 | Pass | Add a central casting pit with overhead hoist plus mold/ingot staging linking both halls. |
| mine_headframe_01@2 | #109–112 | 3 | 7.7 | Pass | Add paired sheaves, suspended cage/cable, winch drum and ore-cart loading chute to the skeletal frame. |
| mine_winding_house_02@2 | #113–116 | 3 | 7.7 | Pass | Make the winding drum/gear assembly dominant and visibly connect it to the rails/cable route. |
| mine_adit_03@2 | #117–119 | 3 | 7.4 | Pass | Extend a timber-shored tunnel behind the stone portal and stage an ore cart, tool rack and graded ore pile. |
| mine_drift_04@2 | #120–122 | 3 | 7.5 | Pass | Add a rail fork, cart-loading platform and dense shoring/tool storage to replace the empty post corridor. |
| mine_quarry_05@2 | #123–126 | 3 | 7.8 | Pass | Complete the yard with a supported derrick/cutting bench, scaffold and three graded material piles. |
| market_cloister_01@2 | #127–130 | 3 | 7.3 | Pass | Slim/recess repeated columns, give four stalls distinct merchandise silhouettes and add a central civic focal point. |
| market_guildcourt_02@2 | #131–134 | 3 | 7.6 | Pass | Give every pavilion a different craft counter/sign/material set and anchor the empty center. |
| market_crossroads_03@2 | #135–137 | 3 | 7.5 | Pass | Add one taller central sign/canopy and three visibly different merchandise groups. |
| market_lane_04@2 | #138–140 | 3 | 7.4 | Pass | Alternate counter depths, signs and inventories to break the repeated post rhythm. |
| market_bazaar_05@2 | #141–144 | 3 | 8.0 | Pass | **PASS.** Preserve its layered occupied market lanes. |
| guard_watch_01@2 | #145–147 | 3 | 7.9 | Pass | Add one exterior stair/landing or machicolation beat and finish the watch room with a compact weapon/map station. |
| guard_bastion_02@2 | #148–151 | 3 | 8.1 | Pass | **PASS.** Preserve the armory/briefing program and strong massing. |
| guard_blockhouse_03@2 | #152–154 | 2 | — | QA reject | Recapture a level, wide interior view; #154 shows almost only ceiling and ladder, not the usable floor. |
| guard_gatehouse_04@2 | #155–158 | 3 | 8.0 | Pass | **PASS.** Preserve/freeze the established geometry. |
| guard_citadel_05@2 | #159–162 | 3 | 7.5 | Pass | Furnish the empty central passage as a war-room/barracks node and expose patrol stairs between towers. |
| exchange_hall_01@2 | #163–166 | 3 | 8.1 | Pass | **PASS.** Preserve the teller/ledger office composition. |
| exchange_countinghouse_02@2 | #167–170 | 3 | 7.9 | Pass | Partition the long hall into teller queue, writing desks and a dense archive wall. |
| exchange_branch_03@2 | #171–173 | 3 | 8.0 | Pass | **PASS.** Preserve the compact customer/ledger scene. |
| exchange_loggia_04@2 | #174–177 | 3 | 8.0 | Pass | **PASS.** Preserve/freeze the established geometry. |
| exchange_bourse_05@2 | #178–181 | 3 | 7.9 | Pass | Replace column-dominated corridors with a central trading pit, continuous counters and a prominent rate board/balcony. |

### Standalone Banks

All five four-view bundles are admissible and advance to review 3. Bank v4 remains functional,
bright and well zoned, but its compact box and broad stepped roof still fall short of the detailed
reference bar.

| Dialect | Evidence | Review | Score | Gates | Bounded next change |
| --- | ---: | ---: | ---: | --- | --- |
| Plains | #182–185 | 3 | 7.8 | Pass | Deepen windows/facade and enlarge the civic cupola/roof composition in a future immutable Bank revision. |
| Desert | #186–189 | 3 | 7.6 | Pass | Add carved red-sandstone shadow courses and a deeper shaded portico. |
| Savanna | #190–193 | 3 | 7.7 | Pass | Break acacia dominance with neutral masonry structure and a stronger green civic accent. |
| Taiga | #194–197 | 3 | 7.7 | Pass | Separate roof, body and base with mossy stone/spruce hierarchy and deeper openings. |
| Snowy | #198–201 | 3 | 7.6 | Pass | Add pale/cold trim, dark-timber contrast and a snow-aware cap/entry identity. |

### Biome cohesion

Each dialect has all ten representative roles and advances to review 3. The palettes did not
receive the hierarchy changes requested after rev8, so none reaches 8.0.

| Dialect | Evidence | Review | Score | Gates | Bounded next change |
| --- | ---: | ---: | ---: | --- | --- |
| Plains | #218,223,228,233,238,243,248,253,258,263 | 3 | 7.7 | Pass | Reduce uninterrupted tan/brown fields with restrained stone, stripped-log and green accents. |
| Desert | #219,224,229,234,239,244,249,254,259,264 | 3 | 7.7 | Pass | Add deeper red/cut-stone shadow bands, shade devices and darker joinery. |
| Savanna | #220,225,230,235,240,245,250,255,260,265 | 3 | 6.6 | Pass | Replace all-orange surfaces with neutral masonry bases, dark structural lines and selective acacia. |
| Taiga | #221,226,231,236,241,246,251,256,261,266 | 3 | 6.9 | Pass | Separate brown-on-brown masses with mossy stone, stripped spruce and warm-light contrast. |
| Snowy | #222,227,232,237,242,247,252,257,262,267 | 3 | 7.0 | Pass | Establish a cold identity with pale stone, dark timber and snow-aware caps. |

Formal rev9 result: 13/51 admissible masters, 0/5 Banks, 8/15 admissible doodads and 0/5
biome-cohesion sets meet 8.0. guard_blockhouse_03 and D6 are camera rejects and do not consume a
review. No item exceeds its five-review ceiling.

## Previous formal rev8/capture-v3 visual audit — iteration 2

The authoritative capture completed all 267 canonical frames at 1920x1080 with no missing files,
duplicate filenames, stale revision/schema, HUD contamination, or manifest/cohesion mismatch.
`scripts/carol-structure-contact-sheets.ps1 -RequireCompleteReviewSet` generated 17 review sheets,
and `scripts/carol-contact-sheet-verifier-regression.ps1` passed its positive and fail-closed
negative cases.

- Evidence root: `fabric/run/gallery-26.2/screenshots/tes-structure-review-ba43d8e12eaabd49-capture-v3/complete-pass-02`
- Manifest: `complete-pass-02/manifest.csv`
- Contact sheets: `complete-pass-02/carol-contact-sheets`
- Static critical evidence: all 52 masters and all five Bank dialects pass weatherproof-roof,
  access, grounding, pane-connectivity, reserved-air, and exact no-skylight block-light-7 gates.
  Those gates do not substitute for visual craft.

### Doodads first

Fourteen close-ups are admissible. D6 and D15 are camera rejects, so neither consumes a review.
The other accepted rows advance exactly once. Four of the fourteen scored motifs meet 8.0.

| ID | Evidence | Valid review | Score | Gates | Result / bounded next change |
| --- | ---: | ---: | ---: | --- | --- |
| D1 | #202 | 3 | 6.6 | Pass | Add a stepped two-height planter tied into continuous edging and wear. |
| D2 | #203 | 3 | 7.2 | Pass | Unify hay, log and perch with a pallet, straps and one continuous worn patch. |
| D3 | #204 | 3 | 7.8 | Pass | Slim and shorten the five-block lamp bracket with a visible knee and cap. |
| D4 | #205 | 3 | 7.5 | Pass | Expose oriented log ends, bindings and one diagonal brace. |
| D5 | #206 | 3 | 7.7 | Pass | Break the long slab back/seat with arms, end caps and a side prop. |
| D6 | #207 | 1 | — | QA reject | Recapture opposite the raised three-quarter side; the bench blocks the campfire. |
| D7 | #208 | 2 | 8.1 | Pass | **PASS.** Preserve the composed garden-work scene. |
| D8 | #209 | 3 | 8.2 | Pass | **PASS.** Preserve its asymmetric planted run and grounded edge. |
| D9 | #210 | 3 | 7.6 | Pass | Add framed or strapped crate faces, varied sizes and a visible pallet. |
| D10 | #211 | 2 | 6.8 | Pass | Make wheels/axle sit unmistakably below a deeper bed; expose both handles and strap the cargo. |
| D11 | #212 | 3 | 7.5 | Pass | Open and thin the rail, then show tie points, a ring and broader trampled ground. |
| D12 | #213 | 3 | 8.0 | Pass | **PASS.** Preserve the readable filled trough and site context. |
| D13 | #214 | 3 | 7.9 | Pass | Loosen the footprint with one more offset/rotated bale, binding and spilled straw. |
| D14 | #215 | 3 | 7.5 | Pass | Replace the square dark center with graded stair/slab/wall rubble and surrounding ash. |
| D15 | #216 | 1 | — | QA reject | Recapture from a low tool-facing three-quarter angle; the opaque back/lintel hides the tools. |
| D16 | #217 | 3 | 8.1 | Pass | **PASS.** Preserve the two-height targets, backstop and firing-line story. |

### Geometry masters

All 52 bundles are camera-admissible and advance to valid review 2. Only two masters meet 8.0.
The dominant shared failure is interior program and craft: many large shells show broad bare plank
floors, token chests/beds/tables, and primary/secondary frames that read as the same undecorated
zone. Passing masters are frozen unless a shared change proves no visual regression.

| Master | Evidence | Review | Score | Gates | Result / bounded next change |
| --- | ---: | ---: | ---: | --- | --- |
| `cottage_hearth_01@2` | #1–3 | 2 | 7.7 | Pass | Add hearth-side shelving, rug/beam texture and one crafted side elevation. |
| `cottage_garden_02@2` | #4–6 | 2 | 7.9 | Pass | Turn the glass loft/pergola into a clearer greenhouse workspace with storage and planting rhythm. |
| `cottage_courtyard_03@2` | #7–10 | 2 | 7.2 | Pass | Partition the oversized room into kitchen, dining and sleeping zones with rugs and storage. |
| `cottage_bay_04@2` | #11–13 | 2 | 7.7 | Pass | Make the bay a furnished window seat/work nook and enrich the rear trim. |
| `cottage_longhouse_05@2` | #14–16 | 2 | 7.4 | Pass | Build a hearth-centered long table, exposed rafters and distinct storage bays. |
| `cottage_orchardstead_06@2` | #17–20 | 2 | 6.9 | Pass | Divide the huge hall into orchard processing, storage and bedroom scenes. |
| `house_cross_01@2` | #21–23 | 2 | 7.3 | Pass | Furnish the central crossing and give the cupola/loft a visible access story. |
| `house_dormer_02@2` | #24–26 | 2 | 7.4 | Pass | Add a real stair/loft route and a furnished dormer alcove. |
| `house_arcade_03@2` | #27–30 | 2 | 6.9 | Pass | Occupy the arcade and front rooms with wall bays, seating, storage and floor zoning. |
| `house_hall_04@2` | #31–33 | 2 | 7.0 | Pass | Open the corridor-like interior around a hearth, table and storage composition. |
| `house_splitwing_05@2` | #34–36 | 2 | 7.5 | Pass | Give each wing a distinct role and make the recessed connective court purposeful. |
| `house_towercourt_06@2` | #37–40 | 2 | 6.5 | Pass | Add tower stairs/gallery, an upper guard or study loft and strong floor zoning. |
| `inn_gallery_01@2` | #41–43 | 2 | 7.2 | Pass | Add a bar, grouped tables, guest partition and readable gallery railing. |
| `inn_coachhouse_02@2` | #44–47 | 2 | 6.9 | Pass | Create a tack/carriage bay plus distinct guest and service rooms. |
| `inn_wayfarer_03@2` | #48–50 | 2 | 7.9 | Pass | Add one facade-depth beat and tie cake, brewing, bed and hearth into a tighter traveler scene. |
| `inn_tavern_04@2` | #51–53 | 2 | 7.6 | Pass | Build a real bar with stools, clustered tables and cask/service storage. |
| `inn_courtyard_05@2` | #54–57 | 2 | 6.7 | Pass | Program the wings and courtyard; make the second interior a visibly different occupied zone. |
| `warehouse_bay_01@2` | #58–60 | 2 | 7.5 | Pass | Add pallet/rack stacks and a legible receiving-to-storage workflow. |
| `warehouse_crane_02@2` | #61–64 | 2 | 6.5 | Pass | Add a visible crane/winch/hook and multi-tier cargo storage to the vast shell. |
| `warehouse_gabled_03@2` | #65–67 | 2 | 7.3 | Pass | Frame the loading door, add a hoist and fill one dense storage bay. |
| `warehouse_wharf_04@2` | #68–70 | 2 | 7.2 | Pass | Create a dock-edge hoist/cargo scene and separate the covered travel route. |
| `warehouse_basilica_05@2` | #71–74 | 2 | 7.0 | Pass | Fill the nave with aisles, tall racks, crane/catwalks and distinct work zones. |
| `granary_loft_01@2` | #75–77 | 2 | 7.3 | Pass | Add grain chutes, sacks, bins and a connected loft/undercroft workflow. |
| `granary_windmill_02@2` | #78–81 | 2 | 6.7 | Pass | Add unmistakable sails/hub plus visible gears, millstones and grain handling. |
| `granary_cruck_03@2` | #82–84 | 2 | 7.3 | Pass | Expose the cruck-frame identity and add sacks, chute and threshing detail. |
| `granary_stilt_04@2` | #85–87 | 2 | 7.2 | Pass | Add supported stairs/hoist and use the undercroft as a composed storage scene. |
| `granary_silocomplex_05@2` | #88–91 | 2 | 7.6 | Pass | Connect silos with catwalk/chute details and a visible processing line. |
| `smithy_courtyard_01@2` | #92–94 | 2 | 7.4 | Pass | Zone forge, anvil, tool bench and materials as one working courtyard. |
| `smithy_hammerhall_02@2` | #95–98 | 2 | 6.7 | Pass | Open a forge bay and add anvil/grindstone stations, roof vent and wall rhythm. |
| `smithy_lane_03@2` | #99–101 | 2 | 7.6 | Pass | Densify the compact workbench/tool wall and articulate the awning edge. |
| `smithy_corner_04@2` | #102–104 | 2 | 6.6 | Pass | Open the forge corner and compose chimney, hearth, tools and materials around it. |
| `smithy_foundry_05@2` | #105–108 | 2 | 7.5 | Pass | Add casting pit/gantry/furnaces and make the second view a distinct production zone. |
| `mine_headframe_01@2` | #109–112 | 2 | 7.7 | Pass | Add winch drum, cage/hoist hardware and an ore-cart loading point. |
| `mine_winding_house_02@2` | #113–116 | 2 | 6.4 | Pass | Install a monumental winding drum, gears, rail and gantry so the role reads immediately. |
| `mine_adit_03@2` | #117–119 | 2 | 7.4 | Pass | Deepen timber shoring and add an ore cart, tools and staged extracted material. |
| `mine_drift_04@2` | #120–122 | 2 | 7.5 | Pass | Add a branching track/loading beat and denser support/tool storytelling. |
| `mine_quarry_05@2` | #123–126 | 2 | 7.6 | Pass | Add derrick/cutting bench/scaffold and graded material piles. |
| `market_cloister_01@2` | #127–130 | 2 | 6.9 | Pass | Slim/open the columns, vary stall identities and add a central civic focal point. |
| `market_guildcourt_02@2` | #131–134 | 2 | 7.2 | Pass | Give each pavilion a different craft/merchandise story and anchor the court center. |
| `market_crossroads_03@2` | #135–137 | 2 | 7.4 | Pass | Add a taller sign/canopy hierarchy and visibly varied merchandise. |
| `market_lane_04@2` | #138–140 | 2 | 7.1 | Pass | Break repeated posts with different vendor bays, signs and counter depths. |
| `market_bazaar_05@2` | #141–144 | 2 | 7.6 | Pass | Diversify canopy/stall silhouettes and add a central fountain or auction focal. |
| `guard_watch_01@2` | #145–147 | 2 | 7.7 | Pass | Add map/weapon storage and a crafted ladder landing to the sparse room. |
| `guard_bastion_02@2` | #148–151 | 2 | 7.8 | Pass | Build an armory/briefing cluster and strengthen the plain rear elevation. |
| `guard_blockhouse_03@2` | #152–154 | 2 | 7.8 | Pass | Add an upper watch/weapon story and more facade depth below the platform. |
| `guard_gatehouse_04@2` | #155–158 | 2 | 8.0 | Pass | **PASS. Freeze geometry; regression-check any shared edits.** |
| `guard_citadel_05@2` | #159–162 | 2 | 7.0 | Pass | Furnish barracks, armory and war room and expose patrol stairs between towers. |
| `exchange_hall_01@2` | #163–166 | 2 | 7.9 | Pass | Differentiate the second zone with teller queue, offices and ledger storage. |
| `exchange_countinghouse_02@2` | #167–170 | 2 | 7.7 | Pass | Partition offices and add counters, queue control and dense ledger storage. |
| `exchange_branch_03@2` | #171–173 | 2 | 7.9 | Pass | Add one compact customer/writing zone and richer rear facade framing. |
| `exchange_loggia_04@2` | #174–177 | 2 | 8.0 | Pass | **PASS. Freeze geometry; regression-check any shared edits.** |
| `exchange_bourse_05@2` | #178–181 | 2 | 7.8 | Pass | Fill the double-height hall with trading pits/counters, balcony and rate boards. |

### Standalone Banks

All five four-view bundles are admissible and advance to review 2. Bank v4 fixes the former secure-
room visibility failure: the chest, ledger shelf and lamp are visible through a true two-wide
opening. The interior is coherent and well zoned, but the compact box and broad stepped roof remain
well below the user's detailed reference bar.

| Dialect | Evidence | Review | Score | Gates | Bounded next change |
| --- | ---: | ---: | ---: | --- | --- |
| Plains | #182–185 | 2 | 7.8 | Pass | For future/replacement v5, deepen windows/facade and enlarge the civic cupola/roof composition. |
| Desert | #186–189 | 2 | 7.6 | Pass | Add carved red-sandstone shadow courses and a deeper shaded portico. |
| Savanna | #190–193 | 2 | 7.7 | Pass | Break acacia dominance with neutral masonry structure and a stronger green civic accent. |
| Taiga | #194–197 | 2 | 7.7 | Pass | Separate roof, body and base with mossy stone/spruce hierarchy and deeper openings. |
| Snowy | #198–201 | 2 | 7.6 | Pass | Add pale/cold trim, dark-timber contrast and a snow-aware cap/entry identity. |

### Biome cohesion

Each dialect has all ten representative roles and advances to review 2. None reaches 8.0.

| Dialect | Evidence | Review | Score | Gates | Bounded next change |
| --- | ---: | ---: | ---: | --- | --- |
| Plains | #218,223,228,233,238,243,248,253,258,263 | 2 | 7.7 | Pass | Reduce uninterrupted tan/brown fields with restrained stone, stripped-log and green accents. |
| Desert | #219,224,229,234,239,244,249,254,259,264 | 2 | 7.7 | Pass | Add deeper red/cut-stone shadow bands, shade devices and darker joinery. |
| Savanna | #220,225,230,235,240,245,250,255,260,265 | 2 | 6.6 | Pass | Replace all-orange surfaces with neutral masonry bases, dark structural lines and selective acacia. |
| Taiga | #221,226,231,236,241,246,251,256,261,266 | 2 | 6.9 | Pass | Separate brown-on-brown masses with mossy stone, stripped spruce and warm-light contrast. |
| Snowy | #222,227,232,237,242,247,252,257,262,267 | 2 | 7.0 | Pass | Establish a cold identity with pale stone, dark timber and snow-aware caps instead of Plains/Taiga similarity. |

Formal iteration-2 result: 2/52 masters, 0/5 Banks, 4/14 admissible doodads and 0/5 biome
cohesion sets meet the 8.0 target. D6 and D15 remain unscored camera rejects. No item exceeds its
five-review ceiling.

### Historical rev5/schema2 doodad close-up audit — iteration 2

The 16-shot doodad capture completed with layout signature `54bfe7284cf71b81`, gallery content
revision 5, and capture schema 2. Its manifest contains one unique captured PNG for every D1-D16
focus id. Camera QA accepted twelve frames as visual iteration 2. Four frames were evidence rejects:
D6 and D7 are hidden behind a tall horizontal timber/bench face, D10 is head-on and conceals the
wheel/axle/bed relationship that defines a cart, and D15 hides its iron/tool face behind a solid
timber board. Those four remain at review count 1; their new screenshots will be iteration 2, not
iteration 3. Camera rejection never consumes one of the five permitted asset reviews.

Evidence manifest:
`fabric/run/gallery-26.2/screenshots/tes-structure-review-54bfe7284cf71b81-capture-v2/doodads-pass-02/manifest.csv`.
The corrected contact sheet is in that directory at
`carol-contact-sheets-v2/doodads-01.png`.

| ID | Rev5 evidence | Score | Accepted review | Exact visual reason below 8 / required next change |
| --- | ---: | ---: | ---: | --- |
| D1 | #202 | 6.2 | 2 | A single flower pot on one full stone block still reads as scatter. Build a stepped low plinth with a second-height planting/leaf beat and attached moss/path edging. |
| D2 | #203 | 6.6 | 2 | The hay block, tiny fence/slab perch, and isolated wear tile do not resolve into one cargo scene. Add a supported pallet/crate face, binding, staggered secondary cargo, and continuous ground wear. |
| D3 | #204 | 7.4 | 2 | The hanging bracket is supported and readable, but its full-log arm and single post remain chunky and vanilla-basic. Add a knee brace/cap and a slimmer, more deliberate arm-to-lantern connection. |
| D4 | #205 | 7.4 | 2 | Rails and end stops communicate a bound log rack, yet the broad solid beam and square dirt feet flatten the silhouette. Expose oriented logs, side bindings and diagonal/end bracing with less symmetrical footing. |
| D5 | #206 | 5.8 | 2 | The tall uninterrupted timber back dominates the seat and makes the bench look like a billboard. Replace it with an open stair/slab back, visible arms and a human-scale two- or three-seat silhouette. |
| D6 | #207 | — | QA reject; count 1 | Smoke is visible but the campfire, curb, seating relationship and utility prop are hidden by the foreground timber face. Recapture from a raised rear three-quarter angle after opening the sightline. |
| D7 | #208 | — | QA reject; count 1 | The same foreground timber face masks the moss/plant/crate gardening composition, so role and craftsmanship cannot be judged. Recapture from the left-rear three-quarter side with the focal objects exposed. |
| D8 | #209 | 6.7 | 2 | Two pots on full masonry blocks plus one moss block form a short row, not a crafted planter. Add a low continuous box/edge, asymmetric heights, foliage overlap and an integrated worn strip. |
| D9 | #210 | 6.9 | 2 | The non-container stack is safe but reads as abstract logs/stairs more than crates. Give each box a recognizable framed face or strap, stagger their heights and add a pallet/packing remnant. |
| D10 | #211 | — | QA reject; count 1 | The head-on pose collapses wheels, axle, bed and handles into a table-like rectangle. Recapture at 35–45 degrees and slightly above axle height; if still ambiguous, separate the stair wheels and extend both handles visibly. |
| D11 | #212 | 7.2 | 2 | The grounded rail and central tie are legible, but the long plain beam is still a one-note prop. Add multiple tie loops/pegs, a short companion trough or hay cue, and a wider trampled strip. |
| D12 | #213 | 7.6 | 2 | The supported U-shape reads as a trough, but it has no visible feed/water surface or crafted end caps. Deepen the basin, vary its contents and connect it to muddy/trampled ground. |
| D13 | #214 | 7.3 | 2 | The staggered bales improve the silhouette, but the square footing and straight stack remain mechanical. Rotate or offset another bale, show binding/support, and add a loose feed/wear edge. |
| D14 | #215 | 7.1 | 2 | Coal, timber and wall pieces give height variation, but full cubes on symmetric stone feet look algorithmic. Use smaller stair/slab/wall rubble, a containment curb, and an ash/gravel work patch. |
| D15 | #216 | — | QA reject; count 1 | The visible face is an opaque timber board; no tool proxy or rack organization can be judged. Recapture inward from a low three-quarter angle and keep iron-bar/chain tools visible on the camera-facing plane. |
| D16 | #217 | 7.8 | 2 | The target, shelter, upright arrow cue and worn lane make the clearest mini-scene, but the shallow backstop and uniform target height stop it short of expert craft. Deepen the backstop, vary target elevation and extend the firing-line/storage story. |

No doodad passed at rev5. At that time Carol recommended recapturing all sixteen for rev6. That
capture instruction is now superseded by the rev8/schema3 contract, but its review accounting and
findings remain historical evidence: the twelve accepted rows next advance to review 3, while D6,
D7, D10 and D15 next advance only to review 2 because their camera rejects never counted. D5, D6,
D7, D10, D11 and D15 need inward or side three-quarter framing; D1 and D2 need a tighter cluster
crop; D4 benefits from a slightly raised end view. All additions must remain grounded, outside
circulation, survival-plausible and non-container/non-loot.

## Active geometry masters — iteration 1

| # | Master | Scale | Initial | Iter. | Primary finding |
| ---: | --- | --- | ---: | ---: | --- |
| 1 | `cottage_hearth_01@2` | Small | 7.3 | 1 | Strong hearth identity; compact shell and interior need another detail layer and verified light coverage. |
| 2 | `cottage_garden_02@2` | Medium | 7.8 | Glasshouse/pergola read well; connect the garden into a fuller lived-in composition. |
| 3 | `cottage_courtyard_03@2` | Large | 8.1* | Good layered massing; screenshot-gated for courtyard lighting and rear roof joins. |
| 4 | `cottage_bay_04@2` | Small | 7.4 | Bay gives identity, but side/rear elevations and tiny-room furnishing remain sparse. |
| 5 | `house_cross_01@2` | Medium | 7.8 | Cross plan is recognizable; lantern volume and interior intersections need visual confirmation. |
| 6 | `house_dormer_02@2` | Medium | 7.9 | Promising skyline; dormer joins, upper-floor access, and room lighting are the likely failure points. |
| 7 | `house_arcade_03@2` | Large | 8.2* | Strong formal frontage; verify that rear and interior match the front's finish. |
| 8 | `house_hall_04@2` | Small | 7.3 | Readable hall but too close to a polished vanilla house without richer interior storytelling. |
| 9 | `inn_gallery_01@2` | Medium | 8.0* | Gallery and gatehouse identify the inn; large shared rooms need complete light coverage. |
| 10 | `inn_coachhouse_02@2` | Large | 8.3* | Distinct court and carriage program; verify balconies, guest rooms, and service corners. |
| 11 | `inn_wayfarer_03@2` | Small | 7.2 | At iteration 1 the massing and interior appeared modest for an expert-build target despite the clear roadside function. |
| 12 | `warehouse_bay_01@2` | Medium | 7.6 | Loading role reads; repetitive bay walls need more depth and believable storage clusters. |
| 13 | `warehouse_crane_02@2` | Large | 8.1* | Crane and galleries are distinctive; inspect support logic and shadowed loading bays. |
| 14 | `warehouse_gabled_03@2` | Small | 7.2 | Practical but visually plain; improve gable hardware, loading canopy, and interior stacks. |
| 15 | `granary_loft_01@2` | Medium | 7.5 | Raised loft is readable; undercroft and grain-handling story need denser craft detail. |
| 16 | `granary_windmill_02@2` | Landmark | 8.3* | Excellent landmark premise; sail hub, tower transition, and interior mechanism need screenshot proof. |
| 17 | `granary_cruck_03@2` | Small | 7.3 | Cruck frame helps silhouette, but loading face and yard scene need a stronger focal composition. |
| 18 | `smithy_courtyard_01@2` | Medium | 7.8 | Twin flues and open court read well; add nuanced tool/material grouping and safe warm light. |
| 19 | `smithy_hammerhall_02@2` | Large | 8.2* | Strong industrial hall; verify roof/flue joins and dark peripheral work bays. |
| 20 | `smithy_lane_03@2` | Small | 7.4 | Role is clear, yet the compact façade and side shelter need finer shaping. |
| 21 | `mine_headframe_01@2` | Large | 8.0* | Headframe/rails communicate role; machinery and shed interiors need finish verification. |
| 22 | `mine_winding_house_02@2` | Landmark | 8.4* | Distinct industrial silhouette; audit the many enclosed/covered low-light pockets. |
| 23 | `mine_adit_03@2` | Small | 7.5 | Good shoring language; exterior staging and tunnel lighting need another authored beat. |
| 24 | `market_cloister_01@2` | Large | 8.1* | Strong civic geometry; stall variety and pavilion lighting must avoid repeated modules. |
| 25 | `market_guildcourt_02@2` | Large | 8.4* | Rich program and formal court; inspect every arcade bay for repetition and shadows. |
| 26 | `market_crossroads_03@2` | Medium | 7.6 | Legible small market, but stalls and central focus need more craft and asymmetry. |
| 27 | `guard_watch_01@2` | Medium | 7.8 | Defensive profile works; tower interior and roof access are likely visually thin. |
| 28 | `guard_bastion_02@2` | Landmark | 8.3* | Strong twin-mass silhouette; verify battlement rhythm, patrol paths, and protected lighting. |
| 29 | `guard_blockhouse_03@2` | Small | 7.4 | Compact defensible form, but façade and watch-room interior remain simple. |
| 30 | `exchange_hall_01@2` | Large | 8.3* | Basilica/cupola reads as civic; large aisles require deliberate chandelier spacing. |
| 31 | `exchange_countinghouse_02@2` | Landmark | 8.5* | Strong civic façade and program; screenshot-gated for office detail and teller circulation. |
| 32 | `exchange_branch_03@2` | Medium | 7.5 | Counter reads clearly, but exterior and public room need more hierarchy and finish. |
| 33 | `cottage_longhouse_05@2` | Medium | 7.7 | Linear silhouette and hearth work; repeated bays and rear yard need more variation. |
| 34 | `cottage_orchardstead_06@2` | Large | 8.2* | Orchard scene gives identity; verify wing joins, bedroom detail, and nighttime paths. |
| 35 | `house_splitwing_05@2` | Medium | 8.0* | Recessed court is promising; confirm independent roof masses meet cleanly. |
| 36 | `house_towercourt_06@2` | Landmark | 8.5* | Strong hierarchy and tower landmark; verify gallery, stairs, and upper lighting. |
| 37 | `inn_tavern_04@2` | Medium | 8.0* | Public porch/taproom read; service wing and guest interior need evidence. |
| 38 | `inn_courtyard_05@2` | Landmark | 8.6* | Excellent multi-wing premise; complex roof seams and distant room lighting are the risks. |
| 39 | `warehouse_wharf_04@2` | Medium | 8.0* | Stepped docks and hoist distinguish it; loading deck props need close-up quality. |
| 40 | `warehouse_basilica_05@2` | Landmark | 8.5* | Monumental storage hall reads strongly; deep nave shadows need lighting verification. |
| 41 | `granary_stilt_04@2` | Medium | 7.8 | Stilt silhouette is useful; undercroft, stair, and grain fixtures need richer composition. |
| 42 | `granary_silocomplex_05@2` | Large | 8.4* | Multi-volume processing identity is strong; inspect elevated links and silo caps. |
| 43 | `smithy_corner_04@2` | Medium | 7.8 | Good open-court idea; corner elevations and work-bay clutter need another pass. |
| 44 | `smithy_foundry_05@2` | Landmark | 8.7* | Best-in-class industrial concept; verify stacks, gantries, fire safety, and full floor light. |
| 45 | `mine_drift_04@2` | Medium | 8.0* | Strong fortified adit and ore handling; enclosed shed detail remains screenshot-gated. |
| 46 | `mine_quarry_05@2` | Large | 8.5* | Broad cutting yard and machinery create a unique scene; inspect edge supports and night legibility. |
| 47 | `market_lane_04@2` | Medium | 8.0* | Linear public space works; individual stalls must avoid copy-paste appearance. |
| 48 | `market_bazaar_05@2` | Landmark | 8.7* | Excellent civic-market ambition; court roofs, stall identity, and shadow pockets need evidence. |
| 49 | `guard_gatehouse_04@2` | Large | 8.2* | Pass-through massing is distinctive; verify arch clearance, flank rooms, and battlement lighting. |
| 50 | `guard_citadel_05@2` | Landmark | 8.8* | Strongest defensive master; multi-level patrol spaces remain a critical lighting/navigation audit. |
| 51 | `exchange_loggia_04@2` | Large | 8.3* | Arcaded civic frontage reads well; teller hall and offices need equal rear/interior polish. |
| 52 | `exchange_bourse_05@2` | Landmark | 8.9* | Strongest civic master; pass depends on roof-lantern integrity and complete trading-floor lighting. |

`*` means “provisionally at target, pending screenshot and critical-gate confirmation.”

## Biome-cohesion matrix — iteration 1

The same 52 ratings apply to geometry in every dialect. These modifiers capture palette quality and
must be checked on at least one representative of every role in every biome.

| Dialect | Initial cohesion | Iter. | Finding |
| --- | ---: | ---: | --- |
| Plains | 8.0* | 1 | Oak/dark-oak/stone contrast is dependable; guard against excessive brown on large walls. |
| Desert | 7.5 | 1 | Sandstone is coherent but risks low contrast; acacia joinery and shade structures should provide stronger hierarchy. |
| Savanna | 7.4 | 1 | Acacia is appropriate but often dominates walls, roofs, doors, and props; stone/neutral accents need more visual cadence. |
| Taiga | 7.8 | 1 | Spruce/cobble is cohesive; large interiors and roofs can become uniformly dark. |
| Snowy | 7.5 | 1 | At iteration 1 dark roof contrast worked, but spruce-heavy bodies needed pale/cold accents and the Banks appeared identical to Taiga. |

## Reusable doodad archetypes — iteration 1

| # | Doodad | Initial | Iter. | Primary finding |
| ---: | --- | ---: | ---: | --- |
| D1 | Forecourt plant pedestal | 6.4 | 1 | One pot on one block reads as scatter, not a composed micro-build; pair height/shape and tie it to the façade. |
| D2 | Forecourt cargo/material pedestal | 6.5 | 1 | Role-readable but too literal; add supports, adjacent small cargo, or an intentional work surface. |
| D3 | Freestanding lamp post | 7.1 | 1 | Functional and safe, but foundation-fence-lantern is vanilla-basic; use bracket/crossbeam variants. |
| D4 | Rail-bound stacked log rack | 7.4 | 1 | Distinctive idea; rails look improvised unless side braces/end stops make the binding believable. |
| D5 | Slab-and-fence bench | 6.7 | 1 | Recognizable but generic; stairs/trapdoors/sign arms and site context would raise craftsmanship. |
| D6 | Safe campfire nook | 7.7 | 1 | Best domestic prop scene; improve seating asymmetry, fire surround, and nearby utility clutter. |
| D7 | Garden work corner | 7.2 | 1 | Useful narrative; needs planter/composter/tool relationships rather than separated markers. |
| D8 | Planter run | 6.8 | 1 | Repetition supplies color but little form; vary edging, plant height, gaps, and corner treatment. |
| D9 | Crate cluster | 6.6 | 1 | Chest/timber shorthand is too blocky; combine barrels, trapdoors, slabs, and staggered heights. |
| D10 | Hand cart | 7.6 | 1 | Strong role prop; refine wheels, handles, bed depth, and cargo restraint. |
| D11 | Hitching rail | 6.8 | 1 | Clear function but extremely simple; add trough/tie points/ground wear to make it a scene. |
| D12 | Feed trough | 6.7 | 1 | Reads from context, not silhouette; deepen the trough and add water/feed variation. |
| D13 | Hay pile | 6.6 | 1 | Material stack is generic; shape with slabs/trapdoors/fence binding and uneven tiers. |
| D14 | Smithy/mine material pile | 6.5 | 1 | Alternating full blocks look algorithmic; add containment, fragments, and worksite wear. |
| D15 | Tool rack | 7.0 | 1 | Frame is sound, but it needs visible tool proxies and a workbench/material relationship. |
| D16 | Guard target rack | 7.3 | 1 | Strong role signal; add firing line, arrows/storage, backstop, and worn ground. |

## Standalone Banks — iteration 1

| Gallery | Dialect | Initial | Iter. | Primary finding |
| ---: | --- | ---: | ---: | --- |
| 272 | Plains | 7.0 | 1 | Functional and readable, but rectangular shell and stepped full-block roof lag behind Blueprint V2 masters. |
| 273 | Desert | 6.8 | 1 | Coherent palette, yet low contrast makes the simple massing flatter. |
| 274 | Savanna | 6.7 | 1 | Acacia-heavy palette exaggerates repetition; add masonry hierarchy and shaded civic detail. |
| 275 | Taiga | 6.9 | 1 | Appropriate materials but dark, plain elevations and roof need more depth. |
| 276 | Snowy | 6.7 | 1 | At iteration 1 its geometry/palette duplicated Taiga and needed a distinct cold-climate roof, trim, and snow-aware entrance identity. |

## Prioritized recommendations after iteration 1

1. **Critical lighting gate:** derive usable interior floor cells from the authored navigation model,
   calculate actual block light after placement, and reject any enclosed master with a spawnable
   floor at block light zero. Add lights as role-native fixtures: hearth glow, chandeliers, wall
   sconces, forge light, guarded braziers, clerestory lanterns, and low storage-bay lamps.
2. **Raise the compact set first:** the twelve lowest masters are the small cottage, house, inn,
   warehouse, granary, smithy, mine, guard, and exchange variants. Add side/rear façade depth,
   roof-end craft, interior zoning, and one memorable role prop rather than scaling up their size.
3. **Rebuild the standalone Bank art:** preserve its production/lifecycle contract but bring its
   shell to Blueprint V2 quality with a deeper civic entrance, layered roof/eaves, teller-hall
   hierarchy, exterior vault/ledger cues, and a distinct Snowy dialect.
4. **Treat doodads as mini-scenes:** every prop needs support, a focal object, secondary detail,
   negative space, and ground wear. Rotate among multiple authored compositions per archetype;
   do not rely on material swaps alone.
5. **Biome contrast pass:** preserve geometry while adding dialect-specific secondary accents—shade
   and carved sandstone in Desert, neutral masonry against acacia in Savanna, warm light/cobble in
   Taiga, and pale stone/dark timber/snow-aware caps in Snowy.
6. **Screenshot gates:** capture front exterior, rear/doodad exterior, and a primary metadata-
   derived interior view for all 52 masters; add a second meaningful interior zone for all 25
   Large/Landmark masters and every Bank. Add a 10-role × 5-dialect exterior sample plus one
   independently framed close-up for each of D1–D16. The current deterministic itinerary is 267
   clean images. Record filename, template, dialect, view, doodads, iteration, and score in a
   stable manifest.

## Cross-cutting 8+ acceptance criteria

These are visual gates for the current rev8 pass; satisfying code validators alone does not award a higher
score.

### Compact masters

- The role and one unmistakable signature feature remain identifiable at a 24–32 block view.
- The silhouette has at least two intentional depth/height beats (for example a bay plus porch, a
  shifted roof mass, or a role-specific shelter) without making the footprint materially larger.
- No principal front, side, or rear wall presents an unbroken plain rectangle wider than five
  blocks; frames, recesses, windows, buttresses, shelves, or attached work elements must break it.
- The roof is sealed and supported, has a crafted eave/ridge/end treatment, and contains no
  hovering cap, orphan slab, or accidental daylight seam.
- The interior has a clear circulation path plus at least three related visual beats: one role
  focal point, one utility/storage group, and one human-scale furnishing or personal detail.
- Every usable interior floor is at block light 7 or higher at night, and the fixture scale suits
  the room rather than using an oversized chandelier by default.
- The three-wide approach meets a supported entrance naturally and leaves the exterior scene clear.

### Standalone Banks

- Preserve the Bank's production footprint, Exchange Desk lifecycle, and safe entrance contract,
  but give the building a civic silhouette comparable to the Exchange masters.
- The entrance has at least two blocks of façade depth, a readable three-wide landing, a sheltered
  threshold, and a focal emblem/bell/ledger cue visible from the village path.
- Replace the monolithic stepped full-block roof reading with supported eaves, shaped gables or
  hips, a deliberate ridge, and one integrated civic roof feature.
- Zone the interior into public waiting, teller/service, and secure ledger/vault areas. The Desk
  must be visible, reachable, supported, and framed as the functional centerpiece.
- Distributed role-native lights keep every usable floor at block light 7 or higher without an
  obvious lantern grid.
- Plains, Desert, Savanna, Taiga, and Snowy each need distinct secondary trim/roof treatment;
  Snowy may not remain visually identical to Taiga.

### Doodads

- Each archetype is a deliberate mini-scene with a grounded/supporting base, one focal object, one
  secondary detail, readable negative space, and contextual ground wear or edging.
- The silhouette reads without relying on a tooltip; avoid a single full block on a pedestal as
  the entire composition.
- Use at least two authored composition variants where repetition is common. Rotation/mirroring
  must preserve supports, facing, and attachment states.
- Props must not block entrances, the three-wide approach, workstations, or villager paths, and
  must not require loading adjacent chunks to remain attached.
- Fire, hanging features, rails, wheels, handles, and stacked cargo must look physically supported;
  no valuable or functional prop may become a renewable duplication source through repair logic.
- A rear/doodad screenshot must score at least 8 on craftsmanship and scene coherence before the
  archetype passes, even when every host building already passes.
