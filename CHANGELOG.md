# Changelog

## Unreleased: 0.4.0-beta.5

- Save per-container Bank loot receipts before attaching loot; never recreate issued/adopted storage when broken during unfinished construction. Preserve replacement inventories. Older unfinished plans without provenance finish remaining storage empty. Economy format 26 retains the previous format-25 data and adds these receipts; back up before upgrading.
- Defer live-occupied construction cells and occupied footing for players, villagers and animals. Recheck at the existing construction cadence without rejecting the lot, accumulating obstruction failures or triggering founding-home relocation. Apply checks to buildings, progressive Banks, terrain preparation, roads, approaches and managed connection updates; items and cosmetic displays do not stall construction.
- Add real-server Bank break/empty/replacement/restart and legacy-loot regressions, entity wait/resume tests for Banks and ordinary projects, and a loaded/unloaded food census plus checkpoint/restart regression. Retain the prior per-chunk food-cache fix: unknown chunks do not become empty observations.

- Add built-in `/nobuild add <name> <x1> <z1> <x2> <z2>`, `list` and owner/operator `remove`. Persist inclusive full-height, dimension-local areas; protect new lots and stop overlapping pending construction without removing existing blocks. No third-party claim mod is required.
- Record meaningful native player block placements and removals; protect connected surviving clusters and recognize older supported raw-log frames. Ordinary torches and vegetation do not reserve a lot. Preserve existing storage/crafted-block guards; ambiguous old dirt/vegetation builds still need explicit zones.
- Recover a persistently obstructed founding home once per district after five loaded, unpaused minutes. Preserve partial lots, transfer unused escrow and charge the consumed share again. Let other eligible districts expand instead of freezing the entire city behind that home.
- Align project and Bank workers with construction eligibility; wait on temporary blockages and release mod-owned routes for paused, abandoned, repair-required or unavailable work. Preserve unrelated villager navigation.
- Cache loaded villager/display membership and chunk food observations; distribute food surveys and reduce their cell budget under lag. Keep each site's independent two-operations-per-second pace. Journal changed district fields between full snapshots, retaining checksums and synchronous financial saves. Economy format 25 introduced recovery evidence and food caches (retained in format 26); back up the entire world before upgrading.

- Fix villager head clearance at new Bank entrances by setting the first carpet row back in Bank revision 9. Preserve revision-8 and earlier structures/plans unchanged.
- Add background construction diagnostics and safe stalled-site recovery. Persist a write-ahead start marker in economy format 24 so only provably untouched reservations may relocate; partial work and player edits stay protected. Pace stalled Bank retries.
- Retain up to two builder assignments per site with native saved entity tags. Add cosmetic delivery loads, ground-supported scaffolding/material displays, and exact mod-owned cleanup; no new construction-management UI, inventory transfers, or free item drops.
- Stagger fresh village lots deterministically and add optional supported roadside squares, gardens and gathering nooks alongside existing branching connections. Preserve reserved geometry and the two-operations-per-second site pace.
- Add actionable upkeep advice in the existing City expansion details and representative real-villager walking regressions. See [background village life](docs/BACKGROUND_VILLAGE_LIFE.md) for behavior, compatibility and testing limits.

- Add frozen, terrain-graded primary connections for fresh village projects and short north approach extensions for fresh Banks. Smooth up to two blocks of road cut/fill, orient stairs uphill, support raised landings and preserve existing shared roads. Unsafe optional connections do not veto building growth; this is not a bridge/rerouting system.
- Finish suitable new hillside lot borders with grounded, biome-palette retaining courses that follow the original slope. Preserve authored structures and entrances. Save original/final terrain states in economy format 23; read older format-22 clearance plans unchanged. All terrain work shares each site's existing two-blocks-per-second pace.
- Add 100 seeded grade-profile regressions, real-server terrain finishing/restart/storage/claim tests and read-only route surveys on natural seed-generated terrain.

- Make new Banks and village projects share natural vegetation clearance: tall connected trunks and branches, natural leaves, flowers, grass and common bushes no longer require an empty lot. Freeze approved removals before construction, preserve storage/crafted evidence and recheck protection for every mutation.
- Add bounded hillside leveling (up to four blocks cut and four filled, eight blocks total surface variation), median-grade floors and four-way orientation fallback for managed village projects. Preserve authored geometry, existing buildings and the four-block support/entrance limit. Banks retain their fixed north-facing blueprint.
- Persist project clearance plans in economy format 22. Clearing uses each site's existing two-operations-per-second budget; restarts resume frozen intent, not a permissive new excavation survey. Add real-server wooded-hillside construction, branch/treehouse, claim/storage, orientation fallback, unloaded-frontier and cut/fill regressions. See [terrain development](docs/TERRAIN_DEVELOPMENT.md).

- Expand food coverage with the district's developed building footprint plus nearby farm/pen space; remove the vertical cutoff, assign overlapping sources once, and skip non-crop loaded sections within the shared scan budget.
- Add saved progressive Bank plans in economy format 21, with restart recovery, protected edits and completion-gated Banker finalization. Only newly placed storage receives initial loot; existing matching containers are never seeded. Completed existing Banks are preserved.
- Give every active construction site one block operation every ten server ticks (2/second at 20 TPS), independent of concurrent sites. Normalize legacy speed keys to this rate; retain loaded/protected/economic gates. Prefer flatter new entrance approaches and keep existing interiors/thresholds unchanged. See [construction details](docs/PROGRESSIVE_CONSTRUCTION.md).

- Add automatic food-production bonuses from nearby growing crops and living livestock. Weight crop maturity and baby animals, reduce the bonus after harvesting/removal, use diminishing returns and fade stale observations. Share a bounded loaded-chunk scan across ticks, attribute overlapping farms to one district, stack with Peaceful and show food-source status in the Banker dashboard. Persist observations in economy format 20 with zero-bonus defaults for older saves; existing architecture and storage remain unchanged.
- Add automatic, open-ended city expansion through bounded districts, funded starter homes and real settler queues. Use tier-3 entry, short sustained-health/cooldown gates, easier Peaceful thresholds and no fixed city district cap. Share municipal surpluses; superlinear administration and unpaid-upkeep pressure naturally taper expansion without demolishing buildings or creating player debt.
- Add Banker > Village > City expansion with Automatic (default), Approval required and Paused modes, readable waiting reasons, upkeep and sampled outdoor light coverage. Enforce world-owner/server-operator authority on the server; omit expansion-direction and no-build-zone setup.
- Let new project sites clear ordinary torches and recognizable natural tree remnants with bounded, persisted preparation. Keep real construction, inventories, workstations and protection vetoes intact; retain shallow-terrain bridging and never retro-clear existing structures. Add a capped, freshness-limited safety-recovery bonus for distributed block-light coverage.
- Add district links, approvals, counters, lighting and preparation progress in format 19 (superseded by format 21); preserve existing architecture. Settler attempts default to 600 ticks. The later per-site construction rate above supersedes the initial shared eight-block budget.

- Automatically apply an easier village-growth profile on Peaceful: 1.75x production, 35% lower food consumption, 3.5x development-point generation and workforce contribution, 20x eligible settler growth chance, faster safety/prosperity recovery and a reachable 100 prosperity target. Suppress local harvest/trade setbacks on Peaceful only. This difficulty profile retains the shared Easy/Normal/Hard baseline, per-district housing/project caps and real-housing requirement; the city features above apply on all difficulties.
- Refresh difficulty from the authoritative world before startup catch-up and each server tick on both loaders; use the same profile for market-isolation shadows. No direct player-money grant.
- Add ten vanilla-format loot tables for new project and Bank chests/barrels: household, inn, warehouse, mine, market, smithy, granary, guard post, Exchange Hall and Bank. Use 2–4 low-value rolls per container; no enchanted gear, diamonds, netherite or emerald windfalls. Preserve existing inventories and deferred tables; never refill old storage. Progressive Banks seed only freshly placed containers after durable plan reservation.
- Add deterministic growth/bounds/difficulty-switch and district lifecycle/persistence tests, real-Minecraft site-policy and loot registry/roll/NBT tests, packaged-table checks and updated growth documentation. Geometry and saved architecture revisions are unchanged.

## Beta.4 handbook and configuration validation

- Retain the new responsive 16-chapter handbook, search/navigation, 80–120% local text preference (90% default), original item/lectern compatibility, optional Fabric Mod Menu and NeoForge Settings integrations, and validated editor for all 27 world settings. Economy format remains 18; existing beta.3/beta.4 saves and one-time book delivery state are preserved.
- Extend real-client tests through chapter/search/scroll controls, both Settings-return paths, text-size bounds and two-process restart persistence. Verify actual loader config registrations and optional Mod Menu absence. Expand invalid-draft and valid-write/reload coverage to every world configuration key.
- Correct README source status to beta.4 and explicitly label its badge as main-only; development artifacts remain distinct from published prereleases.

- Repair the Linux client-smoke desktop environment with an explicit Adwaita cursor theme and Xcursor dependency. Add a real GLFW cursor probe; retain strict rejection of cursor and other unexpected client errors. See the beta.4 final-validation review for the original failure evidence and verification status.

All notable changes to The Emerald Standard are documented here.

## Unreleased (target: 0.4.0-beta.3)

### Low-profile smithy roof and continuous eaves

- Authored revision 9 follows the user's roof reference for `smithy_courtyard_01`: flatter, broad slab-capped workshop roofs with a continuous projecting half-slab border around the joined wings and courtyard returns. Plains uses Dark Oak Slabs; other dialects retain their complementary roof palettes.
- Preserve the chimney collector and slim pots, interior clearance, workstations, lights and yard dressing. Other masters and saved revisions 1–8 remain unchanged. Bank version 8 is unchanged; gallery content 17 uses a separate smithy-eaves review profile.
- Two small front-post sconces light the additional floor row sheltered by the new overhang; the original light sources stay in place.
- Where later-stage rear paving lies beneath the new eave, the existing lighting pass adds a small rear-wall sconce without moving or removing furnishings.

### Complete single-column chimney treatment

- Authored revision 8 finishes the short and single-capped utility stacks missed by the earlier minimum-height rule. The exposed upper half uses Brick Walls (Sandstone Walls in Desert palettes), retaining lower masonry and supported caps.
- The foundry's one-column upper flues receive the same taper without changing their broad furnace shoulders. The reviewed sculpted hearth cottage is preserved. Eight additional masters change across five dialects; furnishings, light sources and unrelated architecture remain identical to revision 7.
- Bank version 8 changes only the two upper brick chimney courses in Plains, Savanna and Taiga Banks. Non-brick Bank stacks and all other cells are unchanged. Existing authored revisions 1–7 and Bank versions 2–7 retain their historical plans.
- Gallery content 16 uses a new isolated chimney-completion review profile. Existing worlds are not retroactively remodeled.

### Courtyard smithy roof composition

- Authored revision 7 replaces the courtyard smithy's flat canopy silhouette with two pitched workshop wings, inset timber gable framing and a lower connecting rear roof. A masonry collector supports two shorter wall-block chimney pots instead of the tall brick panel.
- This is a fixed roof-only composition for `smithy_courtyard_01`, retaining the open court, workstations, lights and yard dressing. Other masters and saved revisions 1–6 remain unchanged. Gallery content 15 uses an isolated smithy-review profile.

### Interior ceiling clearance

- Authored revision 6 raises low domestic ceiling ties from the third to the fourth course above the floor, leaving three clear blocks beneath affected sections. Four masters change: the compact hall house and hearth, glasshouse, and bay cottages. Higher interiors, including the split-wing house, remain unchanged.
- Furnishings, existing lights, gardens, and chimneys are preserved. Revisions 1–5 remain reproducible for saved projects. Gallery content revision 14 uses a fresh ceiling-review profile; existing worlds are not remodeled.

### Slimmer chimney silhouettes

- New authored revision-5 plans shorten eligible plain utility stacks to their local roofline, with a full masonry collar and two slim brick-wall courses (sandstone walls in Desert palettes).
- Preserve sculpted hearth chimneys, contrasting caps, industrial crowns, all existing player worlds, and the exact placement streams of published revision-3 and revision-4 plans. The new regular gallery content revision is 13; use a fresh isolated review profile instead of rebuilding older saves.

### Complementary palettes and village comparison

- The complementary-palette pass introduced authored revision 4 and Bank version 7. Snowy roofs contrast dark timber against pale walls; desert roofs use warm acacia against sandstone. All earlier authored revisions and Bank versions remain reproducible; existing buildings and player changes are not remodeled.
- Replaced generic target-block merchandise and cargo with role-appropriate materials. Targets are limited to a single deliberate outdoor training fixture per guard building.
- Corrected partial-block support gaps in authored furniture and added biome-specific potted plants and persistent shrubs on preflighted supports, without blocking doorways or working lanes.
- Added an opt-in, separate village comparison world: all 52 current masters plus the Bank in every village dialect, paired with actual bundled vanilla structures, plus five small vanilla context courts. This is a curated exhibition, not a claim of natural village generation or new Carol ratings.

### Resumed architecture refinement

- The earlier resumed pass introduced revision-3 versions of the 52 authored masters. Revision-1 and revision-2 plans remained frozen and resolvable for saved projects; no placed building was reshaped by that change.
- Refined selected roof junctions, loading dormers, windmill sails, porch and crane supports, role-specific interior work areas, and seven yard motifs. All visual stages still require supported fixtures, reachable rooms/workstations, and conservative no-skylight block light of at least 7 on usable covered floors.
- Added scoped room-level furnishing groups and non-overlapping floor inlays, reduced oversized rear seating on four compact cottages, and finished eligible raised three-wide entrances with stone side lanes and a supported full-width step. The approach refinement preserves the center dirt path and existing terrain/protection rules; it does not change the building origin or rebuild player-edited approaches.
- Added Bank version 6 with integrated lower belfries, deeper window reveals, quieter masonry, and slimmer secure-room framing. Existing version-2 through version-5 Banks retain their historical plans.
- Finished the lane market's supported continuous stall canopies and added selected framed loft windows. Bank 6's Taiga masonry now uses restrained wear, while Snowy roof trim has stronger material contrast without changing collision or lighting.
- Fixed live azalea decoration loss by giving revision-3 garden shrubs valid moss substrates. Added exact shrub/rail support regressions and read-only runtime checks in the isolated review harness; unsupported decorations fail capture instead of being hidden or recreated.
- Added the exact production authored-catalog safety gate to both loaders' build checks, using standalone Fabric bootstrap and NeoForge's supported loader-aware unit tests.
- Gave only fresh integration-smoke worlds a bounded 180-second tick allowance for synchronous exhaustive catalog tests, retaining the 360-second outer deadline, fatal-log rejection, and scoped process cleanup. Normal server watchdog settings are unchanged.
- Refreshed the isolated production gallery and corrected review cameras for the blockhouse interior, campfire nook, and revised yard motifs. Carol's screenshot scores remain an independent visual gate; automated safety passes do not certify an 8/10 aesthetic rating.
- Fixed completed-gallery attachment audits to inspect final authored layers without replaying superseded construction checks. Photography can route through ordinary doors, rails, and ladders while camera positions still require clear, supported space; dedicated regressions preserve both boundaries.

### Added

- Added a server-paged Activity ledger with mouse-wheel scrolling, visible newer/older controls, a scroll-position indicator, exact row tooltips, and access to all 256 retained player transactions.
- Added server-authoritative Activity filters for cash/transfers, investments, bank products, exchange, and Village Fund entries.
- Added an exact transaction-amount field with Apply, Cancel, Enter, and All controls across banking, investing, lending, CDs, and exchange.
- Added an investment carousel with previous/next controls, compact page summaries, and contextual hover explanations throughout the Banker dashboard.
- Added distinct top, front, and side artwork plus a layered furniture model for the Exchange Desk.
- Added live integration checks for managed Banker profession retention, Exchange Desk POI ownership, and creative-inventory visibility.
- Added persisted, branchable village roads that connect each new prosperity structure to the nearest eligible earlier project or a stable settlement-edge hub.
- Added deterministic Blueprint V2 prosperity architecture: 52 immutable whole-building gold masters cover all ten project roles without recombining floorplans, roofs, frontages, or interiors. Cottages and Houses each have six masters and every other role has five, distributed across nine small, 18 medium, 14 large, and 11 landmark plans. Twenty additional plans introduce longhouses, orchard estates, split-wing homes, tower courts, taverns, courtyard inns, wharf stores, warehouse basilicas, stilt granaries, silo complexes, corner forges, foundries, drift mines, quarries, market lanes, bazaars, gatehouses, citadels, exchange loggias, and a monumental bourse. The expanded catalog, initially revision 2 and now revision 4, gives every master a distinct identity through role-specific footprints, secondary masses, rooflines, circulation, functional spaces, and dense architectural detailing instead of recoloring one shared shell. Small and medium plans are available immediately, large plans unlock at tier 2, and landmarks unlock at tier 4; deterministic tier weights increasingly favor ambitious buildings as the village develops. Each village retains its shared visual character and biome dialect, while a per-scale least-used shuffle bag, three semantic material palettes, three dressing kits, descriptor-approved mirroring, road-facing rotation, and append-only town/city stages provide controlled save-stable variety. Every shipped revision-1, revision-2, and revision-3 descriptor remains resolvable for saved projects but is excluded from new selection.
- Added save-stable, role-specific yard scenes to production Blueprint V2 buildings, including rail-bound log stacks, campfire seating, carts, benches, planters, cargo and hay piles, hitching rails, and tool or target racks. Each project uses its persisted design seed for variety; optional props yield to unsafe terrain and player blocks, never control building operation, and are never repaired or regenerated after removal.
- Added live validation across every active Blueprint V2 template, character, biome dialect, semantic palette, and dressing kit for exact descriptor bounds, disjoint stages, navigable interiors, vertical access, complete supports, sealed enclosures, connected panes, required beds and utility blocks, and the Exchange Hall's single Exchange Desk. Above-vanilla quality admission rejects token decoration, single-sided flat façades, shallow shells, one-feature rooflines, concentrated clutter, unsupported or half-block-floating roof ornaments, interior framing falsely presented as exterior depth, broad roof sheets counted as several skyline features, floating yard props, and buildings whose immutable fixtures do not visibly communicate their economic role. A rotation/reflection-invariant structural distinctiveness gate also rejects active masters whose top, front, side, and roof-height signatures are too similar. Retired revision-1 masters and the legacy 54-recipe `modular_v1` system remain covered for saved-project compatibility.
- Added an exact no-skylight block-light admission gate for every cumulative Blueprint V2 stage and all five current Bank dialects. Every authored, weather-covered floor that Minecraft accepts as a Zombie spawn support and that has two clear standing cells must receive at least block light 7; Bank coverage uses the complete plan, including its roofed porch, apron, and landing. Natural supported fixtures include pendants, wall lights, and a centered Bank-portico pendant. Every cumulative authored stage also mirrors Minecraft 26.2 attachment survival: standing and hanging lanterns require `CENTER`-bearing floor or ceiling faces, while wall lights require a sturdy backing face. Required emitters, hanging chains, and terminal attachment supports remain structural across cumulative stages without changing canonical Blueprint hashes. Player damage to one of those required cells makes the structure unsafe or manual-restoration-required and is never repaired automatically.
- Added an isolated, auto-built Blueprint V2 review gallery with 260 gold-master-by-biome views, 11 controlled stage/palette/dressing/mirror/rotation comparisons, and five production Bank variants. Its 276 structures are paged across a dynamically sized collision-safe grid, use the production planners, render the master matrix at its complete prosperous stage, and spawn reviewers facing that matrix rather than the controlled lab. A versioned render signature invalidates stale pre-polish galleries, while gallery-only JVM properties plus the exact `TES_Blueprint_V2_Gallery` save name keep the tool isolated from normal worlds.
- Added a deterministic 267-shot Carol review capture with fail-closed camera admission, clean 16:9 screenshots at 1920x1080 or greater, exact manifest and completion-marker dimensions, all-master/Bank/doodad/biome coverage, and an exterior-only exact-fixture annex when a crowded gallery row has no unobstructed camera. The contact-sheet gate independently decodes every PNG and rejects stale, partial, misnumbered, wrongly framed, path-escaping, or low-resolution evidence.
- Added vanilla resource and emerald icons to the Trade page so every commodity conversion has a visual identity as well as a name.
- Added an eighth News page with a Minecraft item visual, deterministic local headline, fact-based article, and actionable prosperity and safety guidance derived only from the synchronized village snapshot.
- Added a comprehensive Starter Handbook with colored diagrams and risk cues, recipe visuals, hover explanations, clickable contents and navigation, instructions for all eight dashboard pages, Village Prosperity and construction guidance, recovery help, and an in-game finance glossary.
- Added safe one-time handbook delivery per player per world without auto-opening it, retry-on-next-join behavior when the inventory is full, and a shapeless Book plus Emerald recipe for replacement copies. The existing `onboarding.join_hint_enabled` setting controls the first-join handbook and discovery message together.
- Added bounded append-only histories for up to 16 retired lots per project and 16 retired Bank anchors per region, plus one canonical persisted Banker UUID and last assigned anchor per generated Bank, so repeated relocation cannot reclaim earlier player-edited sites or duplicate or strand a merely unloaded Banker.
- Added recipe-book discovery and exact packaged-resource regression coverage for the affordable Exchange Desk replacement recipe; normally broken desks continue to drop themselves.

### Changed

- Earlier in the beta.3 development cycle, advanced new and safely relocated replacement Banks to authored structure version 5; current approvals now use version 6 as described above. The historical v5 plan retains the v4 secure-record room while adding a shaped front dormer, glazed bell cupola, articulated civic ridge, deep lobby-window reveals, paved forecourt wings, and rear ledger pediment. Its palette courses strengthen each biome identity: carved red sandstone and shade in Desert, neutral masonry against acacia in Savanna, moss-and-stone hierarchy in Taiga, pale cold trim against dark timber with a snow-aware crown in Snowy, and more masonry with less uninterrupted brown in Plains. Intact version-2 through version-5 Banks remain frozen against their own exact historical plans and are never reshaped or retro-lit in place.
- Reworked the production revision-2 authored structures with scale-aware façade rhythms, material hierarchy, layered eaves and porches, deep physically connected window frames, supported skyline punctuation, three-wide dirt-path approaches, connected forecourts and rear service aprons, and grounded role-specific yard compositions. Six previously weak compact masters now have unique structural massing: a cottage catslide hearth wing, clerestory loading warehouse, cross-gabled cruck granary, roofed crossroads market with bell pavilion, cantilevered blockhouse fighting gallery, and civic branch-exchange frontispiece. Large and landmark industrial buildings gained functional loading, dispatch, monitor, gantry, ventilator, and chimney masses where the stricter presentation gate found flat silhouettes. Directional Market awnings now face and cover their stalls correctly; arbitrary timber speckling was removed in favor of structural beams, brackets, bays, and supports; roof, partial-slab, window-reveal, and detached-path gaps were sealed; and every entrance, aisle, workstation, ladder, hatch, and villager access route remains clear.
- Replaced the overly restrictive all-purpose Prosperity Fund throttle with a configurable hybrid: routine and passive Endowment releases keep the monthly cap, while player-origin liquid capital can close the exact input deficit and remaining labor cost for one valid village project. The debit is atomic, so an underfunded fast-track changes nothing; surplus funds, emergency reserves, protected Endowment principal, one-project pacing, physical construction, and player-build safeguards remain intact.
- Made approved physical projects break ground immediately and reveal their authored structure progressively with economic progress; the final block and operational completion remain gated on finished labor.
- Widened prosperity-building roads to a continuous three-block route with a central lane and both shoulders through straight sections, turns, and endpoints. New roads use dirt path with deterministic gravel accents in the center while coarse dirt remains on the shoulders. Existing modular roads finish their exact frozen construction plan first, then a separately versioned, bounded one-time migration scans every historically coarse center coordinate without changing the ordinary road total or cursor. Its frozen target and cursor persist independently, every successful advance saves immediately, dirt path and gravel are adopted, and only unprotected surviving coarse dirt is eligible for replacement.
- Expanded the physical-development activation radius from 96 to 256 blocks by default, widened its configurable range to 48–512, and made eligibility horizontal so altitude does not deactivate a local village. Construction considers at most 16 eligible villages per pass, continues to use loaded chunks only without force-loading, and does not expand the existing local settler/worker range.
- Renamed the player-facing Home and Bank pages to Account and Banking, split Banking into Transfers, CDs, and Villager Loans, and replaced ambiguous money actions with explicit source-to-destination routes and projected balances.
- Added selected-amount and post-action previews for inventory deposits/withdrawals, savings transfers, stock trades, term products, resource exchange, and village gifts.
- Replaced the Fund's additive preset row with the same exact typed Apply, Cancel, Enter, and `All` controls used by other transactions; the applied contribution remains server-owned and live-bounded.
- Made the dashboard responsively fit the current logical window while preserving its tested layout, hitboxes, wrapped tooltips, and EditBox behavior; custom labels now retain the same native Minecraft glyph size as button and input text.
- Distinguished village-project Planning from physical Building and explained their progressive relationship.
- Reworked Village Banks into larger biome-aware civic buildings with foundations, trim, taller windows, a sealed stepped roof, a portico and bell, lighting, storage, and a more complete service counter.
- Advanced newly generated and safely relocated replacement Banks to authored structure version 3. That historical plan remains frozen for intact version-3 Banks alongside the independently frozen version-2 plan.
- Enriched all ten Village Prosperity roles with grounded whole-building architecture, biome-matched entrances, useful interiors, lighting, type-specific work areas and landmarks, persisted rotations and road anchors, and later-tier upgrades while preserving in-progress placement prefixes.
- Advanced persistence through format 18. Formats 10 through 17 retain the historical modular architecture, Banker fallback provenance, Bank versions, relocation and retired-site state, canonical Banker UUIDs, road resurfacing, entrance approaches, death tombstones, and typed conversion transactions. Format 18 adds immutable Blueprint V2 template ID/revision, semantic palette, dressing, mirror choice, and canonical plan-hash fields. Format-9-and-earlier projects remain `legacy_v1`, and already-saved format-10-through-17 projects remain `modular_v1`; neither is rerolled, repositioned, or converted. Only newly approved format-18 projects use `blueprint_v2`, and migration never guesses Blueprint metadata for old in-progress structures. Format-16 tombstones remain intentionally distrusted because they predate the Minecraft entity-removal save barrier.
- Simplified dense Account, Market, Banking, Village, Fund, and Activity text; clipped variable-width values safely, wrapped contextual tooltips, and moved supporting detail into hover explanations.
- Added the Exchange Desk to the Functional Blocks creative tab and creative search on Fabric and NeoForge.
- Expanded the conservative search from 12 to 48 deterministic Bank candidates and from 20 to 64 project candidates, reaching 82 and 84 blocks from the stable village center respectively without loading chunks. Natural lots now tolerate up to four blocks of surface variation through bounded supports, while solid construction, block entities, protected cells, paths, and farmland remain disqualifying.
- Added a 96-site Bank recovery search reaching 128 blocks for villages that have a Banker but no structure. Safe lots wholly beyond vanilla village POI influence are preferred nearest-first, with equal sites favoring the Bank's north-facing entrance toward the settlement.
- Changed completed-structure integrity handling so partial player edits make a project or versioned generated Bank unsafe and non-operational without repair or duplication. Severe demolition requires at least 12 mismatches and 30 percent of authored structure cells, then retires the old site and searches elsewhere while leaving every old block untouched.

### Fixed

- Isolated automated client-smoke game directories under build output instead of deleting a loader's ordinary `run` directory, preserving manual test worlds and local screenshots during verification.
- Prevented public village trails from paving stone, andesite, diorite, granite, snow blocks, or any other block outside the narrow dirt-like and sandy-ground whitelist. Player-placed natural-looking soil still relies on the cooperative protection guard because vanilla has no placement provenance for it.
- Added terrain-adaptive, road-connected stair descents to all ten physical prosperity building types. A new lot is rejected unless its complete one-to-four-step route, supports, and two-block headroom are safe; older completed modular buildings receive one guarded planning pass, and any player obstruction is preserved and permanently waived instead of overwritten or repaired.
- Prevented an active generated Bank's Exchange Desk from becoming silently inert when a harmless replaceable clearance block, an older blueprint difference, or a genuine partial edit makes the full structure fail integrity. The scoped Banker still suspends operation, but the surviving desk now opens as personal banking access; retired Bank counters remain inert, and failed menu opens report an error instead of consuming the click silently.
- Sealed the Exchange Desk's floor-level footprint with an opaque toe-kick while retaining non-occluding block behavior, preventing bright floor or sky slits at shallow viewing angles.
- Fixed `/time add`, forward `/time set`, and faster Overworld time rates leaving the economic day unchanged. The unified clock now takes the largest forward wall-clock, server-tick, or Overworld-clock delta once; backward world-clock changes only rebase the persisted baseline. Persisting that observation also recovers a Minecraft-saved command jump when the process stops before the next economy tick.
- Allowed Village Bank lot selection to clear harmless replaceable vegetation instead of treating grass and flowers as solid obstructions, and made explicitly persisted Banker-only fallbacks retry safely without rebuilding ambiguous legacy, damaged, or crash-interrupted Bank anchors.
- Fixed Banker-only villages becoming stuck when the player stepped outside the vanilla village boundary, unloaded the original village-center chunk, or unloaded part of the first lot scan. A no-candidate result is now durably retryable before its Banker is created, one capped pass keeps recovery active across the 192-block outskirts, and the canonical fallback Banker is reused when the eventual Bank is farther away instead of creating a duplicate.
- Prevented Creative-mode withdrawals from destroying Bank Cash when the inventory is full or only partially accepts emeralds; delivery is now measured from the actual inventory change and every undelivered emerald is refunded through the durable journal.
- Fixed missing exchange charts for resource variants such as ingots, blocks, and ores by deriving all 18 displayed histories from their canonical commodity series and the same formulas used for live quotes.
- Distinguished commodity exchanges and inventory-withdrawal refunds from ordinary deposits in Activity, and retained the exchanged resource and item count for new entries.
- Prevented managed Banker villagers from losing their custom profession immediately after assignment by using vanilla career-lock semantics and binding generated Bankers to their Exchange Desk job site.
- Rebuilt the Banker and zombie Banker profession textures on the correct jacket UV with shaded pixel-art tailoring; removed every stray head, hat-rim, body, and leg pixel.
- Removed the reported dashboard text collisions, tab-label cutoff, crowded investment grid, and long village/fund value overflow.
- Bridged shallow natural terrain under Village Banks and all prosperity templates, including the reported porch posts, bell accent, lantern fences, and masonry flues.
- Replaced the Village Bank's isolated entrance step with a three-block-wide landing and terrain-adaptive stair rows that safely descend as much as two blocks to natural grade without lowering the building into terrain; all three lanes must independently agree on the stair plan so cross-slopes cannot leave a floating edge.
- Authored the correct wall-axis connections for all new Bank window panes and added live shape normalization for surviving panes in signature-matched existing Banks.
- Expanded existing-Bank maintenance into a one-time, durably versioned entrance retrofit. It requires the exact persisted structure signature, preflights the complete addition, honors protection guards, places only into air or replaceable cells, runs Minecraft's chunk-save barrier before committing its version marker, defers under `/save-off`, paces failed retries, rolls back on failure, and never rebuilds missing furnishings.
- Made expanded legacy prosperity templates preflight structural additions before placing them, while optional trail cells skip protected, occupied, or non-terrain positions instead of blocking later upgrades.
- Prevented completed prosperity structures and versioned generated Banks from regenerating collectible furnishings after players break them. Partial damage remains available for manual restoration, while severe demolition uses a different safe site and permanently excludes every retired footprint; old generated Exchange Desks cannot fall through as personal desks.
- Made canonical Banker replacement restart-safe and fail-closed. Both loader death hooks stage a matching Villager or Zombie Villager death before general casualty accounting; only a later entity-inclusive Minecraft save of the observed removal permits format-17 UUID-bound replacement authority. Infection, cure, and terminal conversion persist an exact root/source/target `PREPARED` transaction before insertion, then record a typed durable target, source, root, or retirement outcome only after the required entity-save barrier. The final economy save atomically transfers or retires ownership and clears matching death state. A merely loaded target after restart never proves that a remote predecessor was removed; unresolved transactions stay locked. Chained conversions retain the original root and immediate source, terminal Witch conversion retires the lineage, and multiple exact successors lock the region rather than choosing one. Missing or unloaded entities alone never authorize replacement, and failed saves cannot unlock replacement.
- Made a rejected corrupt or future-format world switch leave the economy service inert, preventing the prior world's in-memory state from being associated with and later overwriting the rejected world's path during shutdown or a save attempt.
- Decoupled prosperity-road progress from building bounds, totals, completion, integrity audits, and economic authority. Unloaded road cells wait without force-loading, unsafe cells become gaps, and fair route selection prevents one waiting road from starving later routes.

## 0.4.0-beta.2 - 2026-09-04

### Added

- Added persistent portfolio accounting for share cost basis, average purchase price, realized and unrealized gain, total contributions and withdrawals, allocation, a bounded transaction ledger, and personal net-worth history.
- Extended asset history to five economic years and added matching commodity history plus 30-day, 90-day, one-year, and all-history dashboard ranges.
- Added up to eight independently identified and selectable CD positions and eight villager business-lending positions per player.
- Added the village-owned Prosperity Fund with Direct Grants, protected-principal Endowments, Project Sponsorships, seven targeted purposes, emergency reserves, bounded spending, contribution records, and non-financial donor recognition.
- Registered a true Banker villager profession, craftable Exchange Desk block and acquirable POI, direct Banker/desk dashboard access, cross-loader assets, and scoped legacy lectern compatibility.
- Added low-frequency authored-project integrity reconciliation and guarded repair.
- Added a rebuildable per-dimension village spatial index and measured query/save/load regression coverage at 100, 500, and 1,000 villages and accounts.
- Added focused debug ownership, watched-village filtering, privacy, report-limit, and timing-boundary regression coverage.
- Added a one-time, configurable first-join discovery hint and a clearer first-Banker deposit and risk explanation.
- Added a one-time **The Emerald Standard** advancement for the first successful Banker visit and a structured GitHub form for exact-commit manual beta evidence.
- Added independent world controls for market events, offline economic progression, and the maximum credited wall-clock gap.
- Added a complete world-configuration reference and an exact-commit release staging procedure.

### Changed

- Expanded the dashboard from five pages to seven with dedicated Fund and compact Activity pages, commodity and personal charts, richer portfolio fields, and collision-safe term and position selectors.
- Fund amounts now use an additive server-owned `+1`, `+5`, `+10`, `+25`, `+100`, `All`, and `Clear` draft.
- Project Sponsorship now binds to the displayed active economically unfinished project, derives its purpose from the project type, preserves unused value at saturated inputs, and rolls any post-completion remainder into that purpose.
- Passive savings-interest ledger events now coalesce so routine accrual cannot evict active transactions from the bounded history.
- CD closure and matured-loan collection commands accept exact stable position IDs and refuse ambiguous no-ID requests.
- Sell-all, CD closure, lending funding, and Fund contributions now use a time-limited two-step confirmation enforced by the server.
- Visual-mode housing and production benefits now wait for verified physical materialization. If an authored block later disappears, the project loses those benefits until safe repair completes.
- Construction theatre may issue an occasional one-shot, low-speed navigation request to at most two suitable residents without installing persistent AI or influencing economic progress.
- Fabric and NeoForge candidate versions advance together to `0.4.0-beta.2`.
- Configuration reload now rejects unknown keys as well as malformed or out-of-range values, reports the exact file, and confirms that the previous settings remain active after a failure.
- Packaged-JAR verification now checks exact binary and sources filenames, embedded loader identity and version, manifest version, required sources, and emits SHA-256 checksums for both files.
- Removed project-owned Gradle 10 deprecations from both loader builds while preserving the existing artifact names and metadata.
- VILX now progressively dampens only exceptional trailing-year upside above 50 percent toward an 80 percent soft guardrail, preserving ordinary gains and every down day while reducing the former +126.8 percent calendar-year diversified-index tail.
- Whole-economy replacement saves now reuse an exact-byte SHA-256 validation result when the primary generation is unchanged; a before/after mature-state benchmark measured roughly a threefold replacement-save improvement.
- Unstarted village projects now inspect 20 bounded, deterministic lot candidates instead of 12, improving placement odds on rough terrain without force-loading chunks or weakening site protection.

### Fixed

- Removed deterministic Market, Banking, Overview, and Fund layout collisions; long and extreme values now stay inside their assigned dashboard regions.
- Fund confirmations now bind the exact draft, type, effective purpose, village lifecycle, village identity, and sponsored project, so changed or stale terms require a fresh confirmation.
- Fund contributions can no longer bypass offline catch-up or an unresolved inventory journal, and exhausted accounting counters reject the contribution before debiting the donor.
- Rejected zero-proceeds stock dust sales without mutating holdings, basis, cash, or activity history.
- Hardened malformed quote handling, persisted chronology and identifier validation, and economic-day, project-ID, casualty, and collapse counter exhaustion.
- Invalid release artifact sets now fail before creating an output directory or copying any release files.
- Banker dashboard synchronization now losslessly packs every logical 32-bit value into signed 16-bit menu slots, preventing client-side truncation of balances, holdings, histories, position IDs, activity, and large Fund drafts.
- Inventory-linked transactions now checkpoint and read back only the affected player's NBT before releasing the live journal, instead of flushing every online player through an API that suppresses individual write failures.

### Configuration and compatibility

- Added independent Prosperity Fund toggles plus configurable endowment payout, emergency-reserve share, and monthly spending cap. Defaults preserve endowment principal and release 4 percent annually.
- Added `onboarding.join_hint_enabled`; existing config files safely receive its default without being rewritten.
- Added `market.events_enabled`, `economic_clock.offline_progression_enabled`, and bounded `economic_clock.max_offline_days`; omitted keys preserve prior behavior in upgraded worlds.
- Pinned both Gradle 9.5.1 distribution downloads to the official SHA-256 digest and verify both wrapper JARs against Gradle's published checksum in the common gate.
- Advanced persistence to format 9. Format-8 and earlier holdings without execution history receive an explicitly inferred migration-day basis, and legacy scalar CD and lending products are promoted to identified positions.
- Format-9 saves preserve five-year asset, commodity, and personal history, portfolio analytics, multiple term positions, village Fund balances, and donor records. Older builds reject the future format instead of silently stripping it.
- Player borrowing, negative balances, and debt remain impossible. Fund contributions are voluntary, irreversible gifts and never become player assets or claims.

### Verification status

- Automated common tests, dual-loader builds, packaged-JAR checks, both dedicated-server startups, and both client bootstraps passed for exact release-source commit `ae8e5d8a2a4eeea8ea8846291efbe0a75515d07a` in [workflow `33908389175`](https://github.com/chedidandrew/The_Emerald_Standard/actions/runs/33908389175). The tagged artifacts and checksums are published in the [`v0.4.0-beta.2` prerelease](https://github.com/chedidandrew/The_Emerald_Standard/releases/tag/v0.4.0-beta.2).
- Every hands-on row in `docs/MANUAL_TEST_MATRIX-0.4.md` remains `Not run` until tested by a person on the exact Fabric and NeoForge candidate.

## 0.4.0-beta.1 - 2026-09-04

### Added
- Expanded visible Village Prosperity progression from 3 to 10 curated project types: Cottage, House, Inn, Warehouse, Mine Entrance, Market Square, Smithy, Granary, Guard Post, and Exchange Hall.
- Added adaptive project prioritization so threatened, food-poor, crowded, and mature villages choose different development paths instead of following a fixed order.
- Added biome-aware bounded physical templates for every new project while preserving the no-force-load and protected-placement rules.
- Added visible local economic-impact guidance on the Village dashboard without exposing deterministic return formulas.
- Added regression coverage for the expanded project catalog and template size bounds.

### Changed
- Village production now responds to relevant physical development: granaries help agriculture, smithies help mining, markets and inns help trade, guard posts help security, and exchange halls help mature trade and transport.
- New need-driven projects add late-game variety while preserving the proven development-tier thresholds used by existing worlds.
- Fabric and NeoForge versions advanced together to `0.4.0-beta.1`.

### Safety and compatibility
- Existing project enum identifiers keep their original names and order, so beta.4 worlds remain migration-safe.
- The abstract simulation remains authoritative. New physical construction stays bounded, never force-loads chunks, never mines arbitrary terrain, and continues honoring `VillageDevelopmentProtection` guards.
- Player borrowing and negative balances remain impossible.

### Recovered and fixed

- Rebuilt the candidate from the last verified beta.4 main branch instead of merging generated payloads and self-modifying workflows.
- Fixed the Minecraft 26.2 first-visit message API so both loader projects compile.
- Fixed a duplicate Inn placement that could permanently block construction.
- Removed unintended barrel, lectern, and cartography-table job sites from prosperity templates.
- Removed the Exchange Hall emerald block and added a smoke-test ban on generated emerald, diamond, gold, and netherite blocks so village construction cannot mint investable currency.
- Advanced persistent storage to format 8 so beta.4 cleanly rejects saves containing the expanded project catalog instead of attempting to parse unknown identifiers.
- Updated diagnostic report metadata to identify `0.4.0-beta.1` correctly.
- Added live template validation to server smoke tests and integrated the 100, 500, and 1,000-village scale guard into the normal read-only regression suite.
- Restored the standard read-only GitHub Actions workflow and excluded temporary payload, transformation, finalizer, and validation workflows from the recovered candidate.

## 0.3.0-beta.4 - 2026-09-03

### Added

- One-command `/emerald debug` diagnostic flight recorder with a five-minute default and 15-minute maximum.
- Automatic market, portfolio, village, construction, settler, GUI-action, validation, and performance capture.
- Incremental JSON Lines timeline for crash resilience.
- Shareable ZIP reports with human-readable summaries and sanitized state snapshots.
- Optional `/emerald debug mark` moment markers and `/emerald debug stop` explicit stop command.
- Automatic recovery and packaging of interrupted capture directories on the next server start.
- Construction, village-census, casualty, settler, and Banker GUI instrumentation.

### Changed

- Bumped Fabric and NeoForge versions to `0.3.0-beta.4`.
- Debug capture remains entirely dormant when disabled and records only the initiating tester's account.

### Privacy and safety

- Debug reports omit the private economy seed, world seed, player chat, server address, and unrelated accounts.
- Reports rotate automatically and retain the newest five ZIP files.

## 0.3.0-beta.3 - 2026-09-02

### Added

- A cooperative, loader-neutral `VillageDevelopmentProtection.register(PlacementGuard)` veto API for Village Bank and prosperity-project placement; guard exceptions fail closed.
- Persisted project footprint bounds, retry deadlines, and materialization-failure counts, with migration coverage for format-5 accounts/bank anchors and beta.1/beta.2 format-6 saves.
- Bounded resident profession bonuses for agriculture, mining, trade, redstone, alchemy, transport, and security output.
- Lightweight look, arm-swing, and particle activity cues for nearby villagers while projects advance.
- Server-synchronized local incident cause and age on the Village dashboard.
- Loader hooks for tracked zombie-villager deaths.
- Persisted full-village market counterfactuals containing the exact state and contribution captured before the first player-caused casualty.

### Changed

- Bumped Fabric and NeoForge versions to `0.3.0-beta.3`.
- Advanced persistence to format 7. Beta.1 and beta.2 format-6 saves upgrade in place; older beta readers reject the resulting format-7 file instead of silently stripping the new safety data.
- Village Prosperity census and materialization now operate dimension-aware across loaded server levels; Village Bank generation remains intentionally Overworld-only.
- Physical development queries only settlements near loaded players, rotates its bounded global block budget, and uses exact persisted project bounds for overlap checks.
- Offline catch-up batch size now adapts to known account and settlement counts.
- New villages receive stable per-village bank keys when a coarse legacy grid key is already owned, while existing region associations remain compatible.
- Bank discovery now uses the persisted stable settlement center for keying and site selection instead of using the player's current position after identity resolution.
- Scoped bank identity now routes Banker replacement, Village dashboard lookup, and support/restoration actions to the associated settlement.
- Active resident professions can improve their relevant sector by at most 12 percent; modded and unmatched professions retain the baseline.
- NeoForge Banker and bank-counter handling now ignores off-hand interaction, matching Fabric behavior.
- Replaced the obsolete alpha.3 publication workflow with an exact-source beta.3 prerelease publisher and refreshed issue forms for the beta line.
- Player-damaged villages now use a daily-advancing no-player-damage counterfactual rather than a static contribution freeze. It is re-priced after each enabled simulation day and genuine non-player casualty, while repeated player hits do not recapture it; cooldown and full recovery still gate release.

### Fixed

- Prevented Village Banks from replacing terrain, paths, containers, solid blocks, or occupied space; floors now sit above flat natural ground and every applied block state is verified before generation is recorded.
- Rejected mud and thin snow as Bank support, rolled failed builds back by authored block identity so neighbor-updated panes and fences are included, and left failed or incompletely loaded searches unmarked so a safe candidate can be tried later while a fallback Banker remains available.
- Replaced permanent project obstruction with persisted exponential retry backoff. Unstarted projects relocate; partial deterministic prefixes retain their site for safe continuation.
- Retained an unstarted project's reservation when its boundary chunk is unloaded, preventing a possibly written but not yet journaled prefix from being orphaned while the retry delay runs.
- Prevented extinct or newly discovered empty settlements from constructing buildings or inventing automatic settlers.
- Restored a bounded recovery path for small survivor settlements after the seven-day stabilization window and minimum safety/prosperity conditions.
- Prevented a spawned settler from consuming the queue before the authoritative census observes it, and added food, fluid, support, and collision checks to spawn selection.
- Prevented an infected resident's later zombie-form death from decrementing productive population twice while preserving casualty attribution.
- Prevented two nearby or cross-dimension villages from silently taking the same bank association.
- Prevented a same-coordinate lectern in another dimension from being mistaken for an Overworld bank counter.
- Prevented valid large account balances from overflowing during net-worth calculation and rejected epsilon-sized investment oversells.

## 0.3.0-beta.2 - 2026-09-02

### Added

- Independent `village_prosperity.market_integration_enabled` and `village_prosperity.automatic_recovery_enabled` world settings.
- Physical-first population reconciliation when visible settlement progression is enabled.
- Persisted resident-tag preference when resolving an already-known village identity.
- Zombie-villager infection and cure reconciliation that suspends productive population without inventing a death.
- Long-absence emigration behavior for residents who remain away from a repeatedly observed settlement.
- Real beds in Cottage projects and physical-bed checks before settlers may materialize.
- Local food spoilage, infrastructure upkeep, material upkeep, shortages, and rare positive or negative village shocks.
- Regression coverage for physical-first recovery, simulation-only recovery, disabled automatic recovery, market-integration isolation, emigration, infection idempotence, cure reconciliation, stable tagged identity, physical population growth, and declining functional tiers.

### Changed

- Bumped Fabric and NeoForge versions to `0.3.0-beta.2`.
- Normal population growth queues a physical settler instead of creating a productive invisible resident when visual progression is enabled.
- Village centers prefer a nearby bell and fall back to the observed resident cluster.
- Existing tagged resident identity is preferred before proximity-based village reuse.
- Unassociated proximity reuse is tighter to reduce accidental merging of neighboring settlements.
- Functional development tier may decline after collapse while completed physical structures remain intact.
- Default development construction pacing is reduced to two blocks every ten server ticks.
- Village snapshot lists reuse one global-fundamentals calculation rather than recomputing it for every settlement.
- Warehouses use chests instead of barrels so prosperity structures do not create unintended fisherman workstations.
- Player-owned projectile deaths use the projectile owner for player-cause attribution when Minecraft exposes it.

### Fixed

- Prevented Extinct villages from resuming production or market influence before physical settlers actually exist when visual progression is enabled.
- Prevented normal simulated population growth from getting ahead of physical residents in visual worlds.
- Prevented prosperity structures from replacing the existing terrain surface, village paths, farmland, player floors, containers, and other solid blocks.
- Restricted development lots to a conservative natural-ground whitelist and made failed Minecraft block placements stop progress instead of being counted as successful.
- Released a project site when it is blocked before the first physical placement so another safe lot can be selected later.
- Removed the old eight-settler convergence ceiling when visuals are re-enabled after long simulation-only periods.
- Prevented repeated zombie observations from decrementing the same productive resident more than once.
- Allowed cured residents to reconcile stale infection records when entity UUIDs change across conversion.
- Prevented an Extinct settlement from retaining a permanently elevated functional development tier.

## 0.3.0-beta.1 - 2026-09-02

### Added

- The first Village Prosperity System with persistent settlement identities and loader-neutral abstract simulation.
- Village population, housing, food, materials, treasury, prosperity, safety, farming, mining, trade, redstone, alchemy, transportation, security, and development-point tracking.
- Active, Threatened, Devastated, Extinct, Recovering, and Abandoned village lifecycle states.
- Persistent resident and incident records with explicit player, hostile, raid, environmental, and unknown casualty categories.
- Cottage, Warehouse, and Mine Entrance development projects with economic and physical progress.
- Bounded gradual construction while players are nearby and chunks are already loaded.
- Capped village fundamentals for the global investment and commodity simulation.
- A fifth Banker dashboard page for village status, development, and restoration support.
- Save format 6 persistence for villages, residents, incidents, projects, construction state, and bank associations.
- Village Prosperity regression tests.

### Changed

- Promoted the project from alpha to the first beta line.
- Connected the global market to local Minecraft settlement fundamentals while keeping offline progression data-only.
- Suppressed free Banker replacement for Extinct and Abandoned settlements.
- Added restoration funding for player-abandoned settlements without introducing player borrowing or debt.

## 0.2.0-alpha.3 - 2026-09-01

### Added

- Region-scoped Banker identity tags so nearby village banks cannot share or replace one another's villager.
- Direct interaction with the lectern at a generated bank counter.
- Biome-aware bank palettes for plains, desert, savanna, snowy, and taiga villages.
- Live dedicated-server invariants proving that only safe, untouched unemployed villagers can be converted.
- Fabric and NeoForge client bootstrap smoke tests under a virtual display.
- Packaged-JAR content and language-file validation in CI.
- Crash, world-generation, and economy-balance GitHub issue templates.
- Additional English translation keys for dashboard actions, confirmations, tooltips, risks, and operation results.

### Changed

- Bumped Fabric and NeoForge versions to `0.2.0-alpha.3`.
- Fallback Banker selection now prefers an untouched unemployed adult and otherwise spawns a new Banker.
- Existing unscoped alpha Bankers are migrated to a persisted village-region identity.
- Generated banks now adapt core building materials to the village biome while retaining the same compact footprint.
- Client and server smoke workflows now reject fatal log entries and verify expected mod integration markers.
- Updated README, GUI documentation, test gate, and build status for the release-candidate workflow.

### Fixed

- Prevented farmers, librarians, traded villagers, experienced villagers, babies, dead villagers, and custom-named villagers from being repurposed or reset as Bankers.
- Prevented two nearby generated banks from adopting the same Banker.
- Preserved dashboard access through the bank counter if a Banker is temporarily missing.
- Cleared transient per-player action cooldown state on disconnect.
- Corrected the NeoForge bank-counter interaction to use Minecraft 26.2's available server-level API.

## 0.2.0-alpha.2 - 2026-09-01

### Added

- World-local configuration for village-bank generation, scan frequency, region size, Banker home radius, and transaction cooldown.
- `/emerald config show` and `/emerald config reload` administrator commands.
- Sector labels, rare market news events, company- and commodity-specific event shocks, and weighted `VILX` constituent behavior.
- Chart scale labels, a midpoint guide, bounded visual scaling, and hover values.
- Rate and risk tooltips plus confirmation clicks for sell-all, early CD closure, and funding villager lending.
- Persistent bank-counter anchors and migration coverage for replacement Bankers.

### Changed

- Bankers now use the vanilla librarian profession and lectern behavior while retaining their Banker identity.
- Bankers receive a configurable home restriction around their bank or fallback village anchor.
- Player mutations now snapshot only the affected account and journal instead of cloning the entire world economy before each transaction.
- Recovery retains items in the durable journal when inventory space is unavailable instead of spawning recoverable value into the world.
- Bumped Fabric and NeoForge versions to `0.2.0-alpha.2`.
- Bumped the persistent data format from 4 to 5.
- Added pinned Gradle 9.5.1 wrappers to both loader projects and switched CI to use them.

### Fixed

- Moved generated Bankers out of the lectern block and behind the counter.
- Prevented multiple players in the same village region from triggering duplicate work during one scan.
- Persisted the exact generation anchor so lost Bankers are replaced at the bank instead of near whichever player revisits the region.
- Checked chunk availability before terrain height queries and corrected the center lantern to use its hanging state.
- Closed the Banker menu when a player dies, is removed, or moves out of interaction range.
- Rebuilt action buttons after server state updates so CD and lending controls no longer remain stale.
- Added a configurable action cooldown and handled worlds whose game time moves backward.
- Avoided redundant full player-data flushes during successful journal recovery.
- Rejected malformed boolean configuration values instead of silently treating them as disabled.
- Made dedicated-server smoke tests fail on fatal log entries even if normal startup markers also appear.

## 0.2.0-alpha.1 - 2026-09-01

### Added

- A full graphical Banker dashboard with Overview, Market, Banking, and Exchange pages.
- Interactive 180-day market charts backed by persistent price history.
- One-click amount presets for 1, 5, 10, 32, 64, or all available units.
- GUI-based deposits, withdrawals, savings transfers, investment purchases and sales, CDs, villager lending, resource exchange, and transaction recovery.
- Automatic Village Bank and Exchange buildings generated on safe plots near discovered villages.
- Persistent Banker villagers placed inside generated banks.
- Natural village fallback that designates an adult village resident as Banker when no safe bank plot is available.
- Persistent generated-bank region tracking to prevent repeated structures in the same village area.
- Fabric and NeoForge client screen registration.
- English interface translations.
- Regression tests for mixed online and offline clocks, checksum recovery, future-format handling, chart history, and generated-bank persistence.

### Changed

- Moved normal player interaction from commands to right-clicking Banker villagers.
- Restricted the complete `/emerald` command tree to permission level 2 for administrators and diagnostics.
- Bumped Fabric and NeoForge versions to `0.2.0-alpha.1`.
- Bumped the persistent data format from 3 to 4.
- Replaced separate wall-clock and game-tick remainders with one unified economic-time accumulator.
- Extended market snapshots with bounded chart history.
- Updated Fabric and NeoForge source sets to include shared client code and shared resources.

### Fixed

- Aligned the Banker milestone with the Minecraft 26.2 entity, permissions, colored-block, and entity-tag APIs.
- Prevented overlapping wall-clock and game-tick progress from double-counting economic time.
- Prevented empty or truncated current saves from being accepted as fresh worlds.
- Added SHA-256 save checksums so silent balance and history corruption is detected.
- Prevented a future-format primary save from silently falling back to and overwriting an older backup.
- Preserved valid legacy format 1, format 2, and format 3 migrations.

## 0.1.0-alpha.2 - 2026-08-31

### Fixed

- Preserved partial Minecraft-day and wall-clock progress across short sessions and restarts.
- Prevented very large offline gaps from blocking server startup with an unbounded single-thread catch-up loop.
- Added crash-recoverable coordination between the separate bank save and Minecraft player inventory save.
- Rejected save files created by a newer unsupported data format instead of interpreting them as current data.
- Added stronger validation for active and inactive CDs, villager loans, holdings, and pending inventory transactions.
- Corrected short-term villager lending economics so 30-day and 90-day terms offer a meaningful expected premium over savings.
- Prevented failed automatic saves from retrying every server tick.
- Prevented unbounded command amounts from creating excessive inventory loops or overflow risk.

### Changed

- Bumped the persistent data format from 2 to 3.
- Bumped Fabric and NeoForge versions to `0.1.0-alpha.2`.
- Replaced routine per-day synchronous saves with 30-second save batching while keeping account mutations durable.
- Added exponential automatic-save retry backoff from 2 seconds to 60 seconds.
- Limited trusted startup catch-up to 25,000 economic days and processed it in bounded startup and tick batches.
- Paused banking while catch-up remains so players cannot trade against an economy that has not reached the current day.
- Switched deterministic transcendental calculations to `StrictMath` for more consistent cross-platform replay.
- Pinned Fabric Loom to `1.17.20` instead of a snapshot plugin.
- Required Fabric API `0.158.0+26.2` or newer in metadata.
- Changed command errors to Brigadier failures and added ticker, term, and resource suggestions.
- Added lightweight market and portfolio snapshots so ordinary reads no longer clone every account.
- Updated CI to launch Fabric and NeoForge dedicated-server development environments after successful builds.
- Used vanilla's public online-player data flush before clearing completed inventory journals because the single-player save method is protected.

### Added

- Durable `PREPARED` and `BANK_COMMITTED` inventory transaction journal stages.
- Automatic transaction reconciliation on Fabric and NeoForge player login and logout.
- `/emerald recover` for manual reconciliation.
- Synchronous online-player data flushes before a completed inventory transaction journal is cleared.
- Tests for partial-day restarts, bounded catch-up, catch-up transaction blocking, inventory journals, future-format rejection, trading friction, save retry backoff, and term-by-term villager lending economics.
- Dedicated transaction-recovery documentation.
- Dedicated-server smoke-test script and CI log artifacts.

## 0.1.0-alpha.1 - 2026-08-31

- Rebuilt and calibrated the deterministic market model.
- Added locked-rate CDs, risky player-funded villager lending, dynamic commodities, private economy seeds, atomic saves, backup recovery, migration, and dual-loader builds.

## 0.0.1-prototype - 2026-08-31

- Initial public architecture prototype for Minecraft 26.2.
