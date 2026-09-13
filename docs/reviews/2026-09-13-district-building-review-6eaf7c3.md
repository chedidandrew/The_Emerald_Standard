# District and villager building system review: 2026-09-13

## Scope and version

Andrew requested a rating of the district villager building system. The intended experience is lightweight, vanilla-friendly village development with visible construction, not a colony-management game. Abstract economic simulation remains authoritative; visiting workers should add life without becoming a mandatory logistics or pathfinding dependency.

The development branch was rechecked before recording this review. Its head was `44ce75c41ded0948043b0c4a4c09aa54861f8da5`, the preceding news-review documentation commit. Reviewed gameplay remains `6eaf7c33ca8fb5e1ad09323884b55667564864ad`, version `0.4.0-beta.31`. Main remained `1ffc9bc06bef6f7203133f5c7dd0df666d9a57f3` and is not the evaluated implementation.

Production sources were extracted from the source JAR in Fabric artifact `10309891092`, workflow `34731884109`. Selected files were independently fetched at the gameplay SHA. Git blob hashes of VillageProsperityEngine, VillageTerritory, VillageArchitecture and VillageProsperityManager matched the independently fetched files exactly.

Reviewed areas included project selection, territory ownership/candidates, blueprint selection, normal and forced construction scheduling, visiting worker behavior, protection/repair documentation, progress messages and relevant CI evidence. This is a source-based system/design review with a small actual-code probe. It is not a full Minecraft playthrough, an architectural screenshot review, a measured large-server benchmark, or a release certification.

## Subjective rating

**8/10 overall for the intended lightweight village-building experience.**

| Area | Judgment |
| --- | --- |
| Fit to the mod's village-economy purpose | 9/10 |
| Need-driven growth and progression design | 8.5/10 |
| Architectural variety and save-stable selection | 8.5/10 |
| Construction presentation design | 8/10 |
| Casual-player explanation and predictability | 7.5/10 |
| Large-world readiness and validation confidence | 6.5/10 |

These are editorial judgments, not benchmark results, measured player satisfaction, or a mathematical certification. Visual execution is not independently scored from screenshots in this review.

The system's strongest quality is the connection between local needs, investment, gradual physical changes and a settlement that retains its identity. The main remaining priorities are normal-mode scaling, placement quality, varied late-game housing and clearer correspondence between worker behavior and construction state. Adding many more building types is less urgent.

## Existing strengths to preserve

### Need-driven choices and real progression

`VillageProsperityEngine.nextProjectPlan` prioritizes security pressure, food needs and crowding before many commercial/prestige projects. Warehouses, Granaries, Markets and Guard Posts can recur with population in organic territories. Ordinary physical admission is bounded to two unfinished, non-manual-repair development projects; the Bank is managed separately. This is more purposeful than choosing an unrelated building at random.

Source: `common/src/main/java/com/chedidandrew/emeraldstandard/core/VillageProsperityEngine.java:649-724` and `VillageStarterGrowth.java`.

### One natural settlement, one growing territory

The beta.29 system keeps stable identities for natural village structures, uses connected 16-block parcels and exclusive ownership relative to known neighboring centers, and no longer creates runtime child charters as its ordinary expansion mechanism. One generated Bank remains associated with a natural district. New records permit up to 512 projects and simulated residents. These limits are defensive bounds, not proof that this scale is inexpensive.

Legacy records retain their identities and legacy limits. Newly discovered neighboring villages can clip eligible territory without demolishing already-built structures. Keep those migration qualifications explicit.

Sources: `VillageTerritory.java`, `docs/reviews/2026-09-12-beta29-organic-villages.md`.

### A substantial architecture catalog already exists

An actual call to `VillageArchitecture.activeBlueprints()` returned 52 active descriptors across the ten project types: six Cottages, six Houses, and five each of the other eight roles. This count excludes separate Bank handling and is not a claim of 52 independently visually approved buildings.

Selection considers development tier, past usage, recent designs, village character, palette, dressing and mirroring. Stable template identity/revision prevents an active saved project silently becoming a different building after restart. Do not recommend adding basic variety as though it is absent.

### Construction presentation has the right lightweight architecture

Dedicated visiting builders use native biome clothing and a toolsmith apron rather than modern hard hats. They arrive through safe reachable approaches, use spaced exterior stations, stop hammering when work waits, and leave when assignments end. Workers are not ordinary resident villagers and do not inflate housing or food counts. Actual construction does not fail solely because a cosmetic worker cannot reach its station.

Support-first ordering, terrain preparation, fence preparation and paced placement are implemented. The default documented allowance is two authored block operations per second per active site at 20 TPS, subject to economic and safety gates. This is a maximum, not a completion-time promise.

Sources: `ConstructionBuilder.java`, `ConstructionSitePresentation.java`, `docs/CONSTRUCTION_CREWS.md`, `docs/PROGRESSIVE_CONSTRUCTION.md`.

### Ownership and recovery are unusually important strengths

The implementation distinguishes unfinished supplied blocks from completed player-editable structures. Existing protection checks, occupancy checks, unfinished repair, one-shot handover loot, anti-export checks, frozen plans and conservative legacy handling should remain intact. Completed building edits are not an invitation to regenerate materials or refill containers.

The relevant CI log contains passing native fixtures for the prior 944/948 Cottage and 3603/4873 Inn support cases, occupied work cells, repairs, ownership, paths and walkway lighting. Those particular historical stalls should not be described as unfixed based solely on older reports.

## Findings and recommended improvements

### Priority 1: Bound the whole normal-mode workload, not only each site's block writes

`VillageProsperityManager.materializeDevelopment`, around lines 730-748, loops over completed projects and executes `village.copy()` for each one before clearing the copied project list and retaining a single project. `EconomyState.VillageRecord.copy()` deep-copies residents, projects, fund data and other history. With many completed projects, this creates an avoidable quadratic project-copying pattern before determining whether every finishing task is already done.

The normal scheduler also refreshes full nearby-village snapshots, gathers dimension-wide project exclusions, and assigns normal construction allowances per site. These individual limits do not constitute a global server-time ceiling. `VillageSpatialIndex.nearAny` additionally scans organic territories after its center-index lookup so far-flung sites remain discoverable; that is functionally useful but another scaling cost to profile.

Recommended change: use compact project/worksite views, retain a queue only for outstanding finishing work, query nearby lot exclusions, cache coverage where valid, and apply a fair global time/operation budget to ordinary construction as well as the already bounded debug/sleep paths. Treat configured per-site throughput as a desired maximum when the server budget permits it. Preserve durability barriers and required state validation rather than moving live world access to unsafe asynchronous threads.

This is a source-derived scaling concern, not a measured frame-rate loss, memory leak, or proven cause of a user's crash. A repeatable long-running test at increasing nearby district/project counts is needed to establish actual cost.

### Priority 2: Make placement less dependent on parcel centers

`VillageTerritory.candidates` emits candidate centers at `cell * 16 + 8`. The controlled probe produced 57 candidates for 37 seeded parcels, all aligned to that lattice. The physical planner rotates blueprints and validates terrain/entrances, but these candidate centers do not explore every usable gap within a parcel. An organic outer boundary alone does not guarantee an organically composed street layout.

Recommended change: preserve parcel ownership while trying a small deterministic set of sub-parcel positions and road-facing/infill candidates. Score a bounded set for route length, terrain modification, facade alignment, usable common space and neighborhood clustering. Where a selected blueprint cannot fit, consider a compatible smaller design only before physical reservation, with explicit state/accounting handling. Never reroll a partly constructed building.

This is a placement-policy limitation and design opportunity, not proof that every generated village looks like a grid. Original village structures, terrain and different footprints still affect actual layouts.

### Priority 3: Avoid making Inns the routine answer to every mature housing shortage

The main crowding branch in `nextProjectPlan` chooses Cottage below tier 2, House at tier 2 and Inn at tier 3 or higher. A controlled healthy/crowded fixture confirmed Inn selection at tiers 3, 4 and 5. Other needs can still take precedence, and the catalog already varies individual Inn designs. This does not mean every project is an Inn.

Recommended change: use a weighted residential mix within the eligible tier. Keep family homes and compact cottages relevant; reserve a higher Inn preference for settlements with appropriate trade/visitor demand or existing residential composition. The goal is recognizable neighborhoods rather than treating every additional resident as a hotel guest. No player micromanagement is required.

### Priority 4: Match worker cues to the physical phase without making workers authoritative

The current `ConstructionBuilder` primarily exposes a hammering flag and a stone-hit sound. `ConstructionSitePresentation.workFocus` targets a nearby wall/foundation, not the exact changing construction phase. That is a sensible low-cost foundation, but leaves room for work animations to feel detached from what is appearing.

Recommended change: expose a compact phase/material cue from the existing construction job. Use appropriate occasional wood/stone sounds, small material-carrying poses, inspection or cleanup moments, and phase-aware station choices only when justified. Keep positions stable between changes and keep actual building progress independent of exact actor movement. Avoid continuous high-cost planning, decorative inventory economies or multiplying workers solely for spectacle.

### Priority 5: Explain waits as clearly as progress

Town > What next? and its progress report, detailed search diagnostics, plus separate simulated/on-site progress strings already exist. Extend them rather than creating a second management interface.

Recommended change: surface one dominant reason, whether it resolves automatically, and the smallest useful action. Distinguish economic labor, site search, waiting for loaded terrain, an occupied cell, protected property and final inspection. A site at 99% should not look like an unexplained failure when it is correctly preserving a resident's position or a player's chest. Show the affected location in the existing map when known; do not promise an exact ETA for unresolved prerequisites.

## Validation evidence and limits

GitHub workflow: https://github.com/chedidandrew/The_Emerald_Standard/actions/runs/34731884109

The fetched job list shows common regression tests and both Fabric/NeoForge build jobs succeeded, while the three client and two dedicated-server smoke jobs failed. This is not an all-green candidate.

The fetched Fabric dedicated-server job `103657202560` ran with `the_emerald_standard.integrationSmoke=true`. Its log records many passing construction fixtures, including exact Cottage/Inn recovery, real worker walking/animation, guarded terrain changes, cottage occupancy/restart, Bank construction, one-shot ownership, lighting and route connections. It later ended with a 180-second single-tick watchdog shutdown at 02:12:44 UTC. The startup source synchronously invokes the integration suite when the opt-in property is enabled. The failed full smoke run therefore must not be presented as proof that ordinary construction necessarily takes 180-second ticks; the suite/harness and any expensive work it invokes need isolation and diagnosis.

The already-downloaded Fabric Mod Menu client artifact `10309906104` reports `reader preference did not survive process restart`. That is not evidence of a district building defect and is not used to rate building aesthetics. No fresh client playthrough or rendering inspection was performed here.

## Local actual-code probe

The 55 loader-neutral core Java files plus `client/MarketDisplay.java` and `debug/ConstructionWorkload.java` were compiled successfully with the available OpenJDK 21.0.11. A separate review-only probe executed their selection/candidate APIs. The distributed Minecraft Java 25 binary was not changed or launched. No Minecraft world or account data was loaded by the probe.

Fixture: an organic VillageRecord with UUID `00000000-0000-0000-0000-000000000001`, packed center `64` (X=0, Y=64, Z=0), population/housing 30, food 3000, safety/prosperity 80; manually vary tier to isolate the crowding branch. This is not a natural long-duration growth simulation.

Observed output:

```text
crowded healthy fixture tier=0 housingProject=COTTAGE
crowded healthy fixture tier=1 housingProject=COTTAGE
crowded healthy fixture tier=2 housingProject=HOUSE
crowded healthy fixture tier=3 housingProject=INN
crowded healthy fixture tier=4 housingProject=INN
crowded healthy fixture tier=5 housingProject=INN
territory cells=37 candidates=57 centersAligned16=57
active blueprint descriptors=52
COTTAGE templates=6
WAREHOUSE templates=5
MINE_ENTRANCE templates=5
HOUSE templates=6
INN templates=5
MARKET_SQUARE templates=5
SMITHY templates=5
GRANARY templates=5
GUARD_POST templates=5
EXCHANGE_HALL templates=5
two unfinished physical projects allowsThird=false
one unfinished physical project allowsNext=true
```

Reproduce the selection output by calling package-visible `VillageProsperityEngine.nextProjectPlan(v,112,1)` from a class in the core package. Call `VillageTerritory.seed(v,List.of(v))`, then `candidates(v,0)` and count positions whose X and Z floor-modulo 16 both equal 8. Count `VillageArchitecture.activeBlueprints()` and `blueprints(type)`. To check admission, add two economically complete but not materialized, non-abstract House projects and call `VillageStarterGrowth.hasRoom(v,true)`; then mark the first physically complete and call again.

## Handbook and change record

Reviewed AGENTS.md, the construction/organic-territory documentation and the packaged English simulated/on-site progress labels for alignment with the evaluated behavior. No gameplay, recipes, controls, caps, templates, saves or handbook explanations were changed. No unrelated handbook text was edited merely to accompany a review.

This commit adds only this assessment and evidence record. It does not fix the identified design/scaling limitations, rerun GitHub Actions, merge branches, certify a release or revise the prior news review. Future implementation should update relevant handbook guidance, regression coverage and validation notes with the actual behavior delivered.
