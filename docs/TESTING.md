# Testing and publication gate

The chimney regression compares all 52 masters in five dialects against revision 4, admits only
chimney-material replacements/removals, preserves dressing stages and the sculpted hearth,
and checks local-roof sizing, supported wall tips, capped-stack preservation, reserved-cell
preflight and idempotence. Frozen revision-3 and revision-4 placement-stream fingerprints are
checked independently of the current catalog's geometry, route and lighting admission.

For rapid visual review, use the isolated [Structure Gallery](STRUCTURE_GALLERY.md). Its dedicated launch auto-builds 276 production-derived structures: 260 active revision-9 master-by-biome views, 11 controlled revision-9 comparisons, and five Bank variants. It remains deliberately separate from smoke tests and ordinary development saves; retired revision-1, revision-2, revision-3, revision-4, revision-5, revision-6, revision-7, and revision-8 masters are compatibility fixtures rather than new-gallery selections.

## Exact structure admission without opening a world

For biome/color A/B inspection, use the separate [Village Comparison Gallery](VILLAGE_COMPARISON_GALLERY.md): 265 mod/vanilla pairs plus five small vanilla context courts. It does not replace or repair the older review world. Current authored plans are revision 9, Banks are version 8, and the regular gallery content revision is 17. The [low-profile smithy review](SMITHY_EAVES_REVIEW.md) provides the current isolated profile and visit commands.

Use Java 25 and run the appropriate command from its loader directory (on Windows, use
`.\gradlew.bat` instead of `./gradlew`):

- **Fabric:** `./gradlew verifyAuthoredVillageStructures verifyVillageBankStructure`.
  Both tasks are standalone `JavaExec` tests. The authored-catalog test bootstraps Minecraft and
  registers the real Exchange Desk in its disposable test registry; it never accepts a lectern
  as an Exchange Desk role signal. The separate Bank task uses the limited Desk-cell fallback
  described under automated loader verification below.
- **NeoForge:** `./gradlew verifyAuthoredVillageStructures`.
  This task depends on the loader-aware JUnit `test` task configured through ModDevGradle's
  [supported `unitTest` bootstrap](https://github.com/neoforged/ModDevGradle#unit-testing-with-junit).
  `AuthoredVillageStructuresLoaderTest` calls the same common
  catalog self-test after the loader environment is initialized. A plain `JavaExec` bootstrap
  cannot initialize NeoForge-patched Minecraft classes, so the Fabric launch method is not
  reused here. There is no NeoForge `verifyVillageBankStructure` task; live Bank registration
  and admission remain part of its dedicated-server smoke.

The authored test executes the exact production admission gate for all 52 active masters, five
dialects, semantic palettes, dressing stages, architectural characters, and bounded doodad seeds.
It checks roof/support continuity, reachable rooms/workstations, connected panes, and a conservative
no-skylight light projection of at least 7 on usable weather-covered floors using real Minecraft
block states. This is an authored-plan gate, not a recorded live-engine light measurement. Neither loader opens or
changes a save or touches a running game. Both loader `check` tasks include this catalog gate;
Fabric also includes its standalone Bank gate. These tests prove functional safety, not Carol's
subjective aesthetic score.

Before catalog admission, the shared test also checks revision-3 raised approaches at several
porch depths and the front envelope, including all three stair lanes, headroom, atomic obstruction
skips, and unchanged historical revisions. The market-lane test verifies continuous canopies,
fully borne front pediments, longitudinal beams, retained role fixtures/bell arch/three-wide aisle,
and atomic rejection of unexpected recipe conflicts in all five dialects. The approach coverage
line reports which canonical optional approaches actually acquired a step; a safely skipped
transition is not reported as an installed improvement.

Revision-4 checks cover contrasting roof materials in all five dialects and three palettes,
rendered partial-block support contacts, sparse guard-only targets, supported biome planting,
and an exact revision-3 placement fingerprint across all 52 masters in all five dialects.
The Bank gate additionally locks 25 historical version-2-through-6 placement snapshots.
The complete regular-gallery census requires zero isolated rendered cells and zero ungrounded
assemblies across 271 fixtures and their cumulative stages. Only actual positive-edge contact
between roof-phase stair courses counts as a roof join; air gaps and point-only contacts do not.
Exact fixture checks cover lantern chains, crane mounts, 80 structural joints, 100 workshop
ties/purlins, and three four-cell courtyard roof caps in all five dialects. Greenery coverage
requires real plants or persistent foliage, not empty flower pots. These are authored-geometry
and survival checks, not a claim that every possible terrain or third-party block behavior was tested.
The comparison capture helper records actual camera pose and screenshot checksums; neither
these comparisons nor safety tests restart Carol's exhausted numerical review counters.

The same test audits fragile decorations in all 276 current gallery fixtures, including cumulative
stages and all five Banks. Azalea substrates are resolved against bundled vanilla block tags; rails
need rigid base support and the additional raised-end support required by ascending shapes. The
runtime gallery separately checks actual presence and `canSurvive` after placement, clone
synchronization, and camera preflight. These are read-only checks: failures stop capture and do not
repair decorations or erase dropped items. Third-party datapack rules remain outside the bundled
vanilla test contract.

## Automated common regression suite

Run:

```bash
bash scripts/run-common-tests.sh
```

The suite verifies:

- Gaussian distribution, deterministic replay, market calibration, regimes, events, and commodities
- Villager-lending defaults and expected returns by term
- Savings, CD, and lending maturity
- Unified wall-clock/game-tick/Overworld-clock progression, command jumps, overlap prevention, rollback rebasing, paused-clock fallback, restart continuity including a command jump saved before the next economy tick, and bounded catch-up
- Save migration, checksums, backup recovery, rollback, retry backoff, no-debt invariants, and inert service state after a corrupt or future-format world switch is rejected
- Genuine format-5 account/bank-anchor migration, beta.1/beta.2 format-6 upgrade, safe defaults for older project records, and rejection of future formats without stale-backup fallback
- Format-7 to format-8 project-catalog migration, format-8 to format-9 financial-state migration, format-9 to format-10 architecture migration that keeps completed, partial, and unstarted projects on `legacy_v1` without reroll or modular conversion, format-10 to format-11 safe fallback-provenance migration, format-11 to format-12 Bank-version migration, format-12 to format-13 migration that does not trust or invent relocation, retired-site, or canonical-Banker provenance, format-13 to format-14 migration that marks only eligible physical modular roads for an independent one-time center-surface scan, format-14 to format-15 migration that leaves eligible modular entrance approaches safely pending without trusting spoofed future fields, format-15 to format-16 migration that cannot invent generated-Banker death authority, format-16 to format-17 migration that distrusts pre-barrier death tombstones while adding exact typed conversion transactions, and format-17 to format-18 migration that preserves every old `legacy_v1` or `modular_v1` project while refusing to infer Blueprint identity or hashes
- Bank structure-version persistence and monotonicity, exclusion from Banker-only fallbacks, exact-anchor upgrade validation, and in-memory rollback after a failed durable save
- Deterministic village character and biome dialect; all 52 active revision-9 Blueprint V2 descriptors; tier-gated small, medium, large, and landmark selection weights; per-scale least-used template selection; immediate-repeat avoidance; semantic palette, dressing, descriptor-safe mirroring, canonical signatures and plan hashes; cardinal road-facing rotation; exact persistence of every Blueprint identity, visual stage, road anchor, and independent road cursor; and continued resolution of every retired revision-1, revision-2, revision-3, revision-4, revision-5, revision-6, revision-7, and revision-8 descriptor at its original envelope. Historical modular recipe selection and signatures remain covered for format-10-through-17 compatibility.
- Atomic append-only visual-stage commits, fixed building totals during partial suffix placement, road progress that cannot alter building completion, fair rotation among unfinished roads, a versioned center-surface migration whose frozen target/cursor resume independently while preserving ordinary road progress and gravel accents and refusing non-coarse targets, and an independently persisted entrance approach whose initialize/advance/waive lifecycle remains unchanged by unfinished road state
- Deterministic foundation support planning through the four-block terrain tolerance, bounded support depth, support below negative-Y entrance stairs without filling their walkable cells, and no tunnelling below sound natural ground
- Deterministic prosperity-building entrance planning for flat and one-to-four-block drops, reachable rises, incomplete or over-depth profiles, immutable results, save/reload cursor durability, and guarded one-time waiver
- Flat, one-block-drop, and two-block-drop Bank entrance planning plus rejection of incomplete, unsafe-rising, over-depth, or three-lane cross-slope approach profiles
- Legacy CD and lending promotion plus explicitly inferred basis for old holdings without execution history
- Share cost basis, average purchase price, realized and unrealized gain, allocation, contributions, withdrawals, bounded transaction ledger, and five-year net-worth history
- Five-year asset, commodity, and personal history retention plus bounded chart-sampling inputs
- Up to eight independently identified CDs and eight villager-lending positions, including position-specific close and collect behavior
- Direct Grants, protected-principal Endowments, Project Sponsorships, all seven purposes, emergency reserves, bounded spending, and donor recognition
- Inventory transaction-journal lifecycle, persisted-inventory comparison, full and partial
  inventory delivery accounting, exact undelivered-withdrawal refunds, and durable journal removal
  before later inventory changes are allowed
- Village Prosperity simulation and project approval
- Independent simulation, visual, market-integration, and automatic-recovery behavior
- Physical-first extinction recovery
- Simulation-only abstract recovery
- Market-influence bounds plus player-damage counterfactual persistence, repeat-hit isolation, and gated release
- Stable village identity persistence and preferred resident-tag identity
- Resident Away to Emigrated transitions
- Infection idempotence and cure reconciliation
- Committed settlers contributing off-screen, then transferring to physical population without double counting when observed
- Functional development-tier decline after collapse
- Empty-village discovery remaining Abandoned without invented settlers
- Bounded profession specialization and isolation between unrelated sectors
- Infected-resident death without a second productive-population decrement
- Persistent active and retired project bounds, exponential retry deadlines, restart eligibility, and bounded append-only exclusion of every prior replacement lot
- Economic project benefits remaining active while initial visual materialization is deferred, plus demotion after integrity loss, partial-damage manual restoration, severe-demolition relocation, and suspended benefits until the authored structure or replacement is complete
- Bounded retired-Bank history, rejection of old generated Exchange Desks, canonical Banker UUID/assigned-anchor uniqueness and convergence across unload/death/relocation, durable UUID-bound death tombstones, bounded in-session retry after a failed death save, no replacement before persistence, restart authorization after a durable death, exact-live cancellation after rollback, atomic canonical replacement, and full-plan Bank integrity decisions at the 12-cell/30-percent threshold
- Dimension-filtered nearby village snapshots and account/settlement-aware catch-up batches
- Spatial-index equivalence, cross-dimension isolation, deterministic ties, measured query/save/load
  work at 100, 500, and 1,000 villages and accounts, and mature replacement-save timing
- No-op bank association persistence, net-worth overflow safety, and exact oversell rejection
- Debug capture ownership, watched-village filtering, privacy redaction, timeline limits, and separated timing categories
- Signed-short packing and reassembly round trips for full-width balances, gains, histories, position IDs, and Fund drafts
- Fixed eight-tab dashboard geometry, Market-carousel mappings, canonical Trade item visuals, bounded News/article/guidance regions, deterministic village bulletin policy, non-overlapping hover regions, exact-value hover coverage, and state-aware CD/Fund tooltip policies
- Exact Exchange Desk replacement recipe, emerald/book recipe-book discovery, packaged resources, and normal self-drop behavior
- Starter Handbook item definition and localization contract, shapeless Book plus Emerald replacement recipe, recipe-book discovery advancement, and packaged handbook resources

## Automated loader verification

GitHub Actions must pass for the exact candidate commit:

- Common regression tests
- Fabric 26.2 build and packaged-JAR validation
- NeoForge 26.2 build and packaged-JAR validation
- Fabric dedicated-server launch
- NeoForge dedicated-server launch
- Live registration and invariant checks for Exchange Desk creative visibility, all four facings, shape, supporting-floor visibility, and POI mapping, managed Banker job-site retention, connected Bank pane normalization, and Bank/project templates. Bank validation requires the three-wide landing/top stair and all 19 panes with the correct wall-axis connections. Blueprint V2 validation expands all 52 active revision-9 descriptors across five biome dialects, three semantic palettes, three dressing kits, four village characters, and deterministic exterior-scene seeds, then verifies exact bounds, disjoint stages, circulation and vertical access, complete supports, sealed enclosures, connected panes, and required bed/light/utility/Exchange Desk invariants. Catalog gates also enforce role coverage, scale diversity, distributed detail regions and bands, multi-sided façade articulation, architectural depth, fixture spread, roof relief, and normalized top/front/side/roof-height distinctiveness independent of rotation or reflection. The isolated 276-structure auto-gallery adds visual coverage of every active master plus controlled stage, palette, dressing, mirror, and rotation axes. Retired revision-1 descriptors and the historical 54-recipe modular system remain covered for saved-project compatibility.
- Exact no-skylight lighting admission for every cumulative Blueprint V2 stage and all five current Bank dialects. The target set contains every authored, weather-covered, Zombie-spawn-valid floor with two clear standing cells, including the Bank porch, apron, and landing; real emission and conservative state dampening must leave each target at block light 7 or greater. A shared read-only attachment gate separately verifies the Minecraft 26.2 predicates: standing lanterns and torches require a `SupportType.CENTER`-bearing floor face, hanging lanterns require a contiguous vertical chain (if present) terminating at a `SupportType.CENTER`-bearing ceiling face, and wall lights require sturdy backing. Source-wiring regressions preserve those checks across all three Blueprint stages and every current Bank dialect, along with the centered Bank-portico pendant, runtime hanging-chain and terminal-beam authority, and exclusion of safety promotion from canonical Blueprint hashes.
- Fabric's headless `verifyVillageBankStructure` task executes the complete version-8 geometry, three-wide entrance, secure-room circulation, fixture-attachment, and no-skylight-lighting gate in all five biome dialects. A vanilla lectern stands in only for the custom Desk at that exact counter cell because a plain bootstrapped JVM has already frozen registries; the ordinary Fabric and NeoForge dedicated-server smokes execute the no-fallback production gate after their real Exchange Desk and POI registrations are live. The authored-catalog gate also checks all 25 frozen Bank2–6 plan hashes and Bank7 palette/planter changes and the exact Bank7-to-8 chimney-only delta after registering its exact Desk fixture. The headless tasks launch no Minecraft client and mutate no world.
- Source-wiring regression checks that both loaders route an actual Zombie Banker death through Bank lifecycle state before general village-casualty accounting
- Real Minecraft container-data packet round trips for Banker full-width values
- Fabric client bootstrap under a virtual display
- NeoForge client bootstrap under a virtual display
- Screen-registration and fatal-log checks
- Artifact upload for both loaders and smoke logs

## Manual beta checklist

Use the repository's [manual beta test form](https://github.com/chedidandrew/The_Emerald_Standard/issues/new?template=manual_beta_test.yml) for exact-commit evidence. Select one test area per report; attach the `/emerald debug` ZIP for every failure or ambiguous outcome. The canonical coverage table remains [MANUAL_TEST_MATRIX-0.4.md](MANUAL_TEST_MATRIX-0.4.md).

Automated startup proves API compatibility and initialization. It cannot prove every physical-world edge case.

Dedicated-server smoke runs create a fresh temporary world with an ephemeral port. Only that
test world's `max-tick-time` is raised to 180 seconds because the opt-in exhaustive catalog
validation runs synchronously during startup; normal gameplay worlds keep their own settings.
The smoke script still has a 360-second outer deadline, fatal-log checks, and uniquely scoped
process cleanup. A regression protects these isolation and timeout boundaries.

### Banking and GUI

On both Fabric and NeoForge:

- Open a new world and an upgraded existing world.
- Open the dashboard through a Banker, a new Exchange Desk, and an upgraded legacy bank lectern.
- Confirm the Village page shows the latest local incident cause and age.
- Verify all eight pages and all GUI scales, including empty, partial, and full Activity ledgers. Use both the mouse wheel and visible arrows, confirm the `1–5 / 256`-style position updates, and verify both controls disable at their respective bounds.
- On Trade, cycle all 18 resource forms and verify the vanilla item icon, emerald icon, name, quote, and chart stay synchronized. On News, cover unlinked, restoration, recovery, incident, low-safety, low-food, full-housing, active-project, prosperous, and steady states; verify the headline, visual, article, and prosperity/safety tips match only authoritative synchronized facts and expand fully on hover.
- Confirm the Market investment carousel wraps correctly in both directions, its selected ticker matches the chart and trade actions, and every long label remains within the 320x230 dashboard at each GUI scale.
- Hover the selected investment, performance summary, charts, Trade panel, Banking products, Village panels, Fund controls, and Activity footer; confirm details appear without obscuring the inspected value. In particular, compare each abbreviated Account balance with its exact two-decimal tooltip, verify the Village mode, local impact, restoration/news, and output rows have separate hover regions, and check early versus mature Close CD text plus every disabled Fund-purpose reason.
- Open Creative inventory on both loaders and confirm Exchange Desk appears under Functional Blocks and when searching for `desk` or `exchange`. Place it while facing all four directions and confirm its orientation and 13.5/16-block selection/collision height match the model. Inspect its inset base from floor level and confirm the supporting floor's top surface remains visible through the lower geometry, with no sky or void slit.
- In Survival, acquire an emerald or book and confirm the Exchange Desk appears in the recipe book. Craft one with an emerald above a leather-book-leather row and three planks, place it, break it normally, and use the dropped desk to replace it.
- With `onboarding.join_hint_enabled=true`, join as two new players and confirm each receives exactly one Starter Handbook and the discovery message in that world. Confirm the item enters inventory without opening the book screen, neither onboarding action repeats after reconnecting, and the same player remains independently eligible in a different world.
- Open the handbook at multiple GUI scales. Verify its colored and labeled risk cues, diagrams, recipe layouts, hover explanations, table-of-contents links, and previous/contents/next controls render within every page and jump to the intended destinations. Check coverage for getting started, all eight dashboard pages, investments and Trade resources, Village Prosperity and safety, the Fund, projects and physical construction, economic time and debug guidance, transaction recovery, troubleshooting, crafting, and finance definitions.
- Open Creative inventory on both loaders and confirm the Starter Handbook appears under Tools & Utilities and in search.
- Fill an eligible player's inventory before joining and confirm the handbook is neither inserted nor dropped and onboarding is not marked complete. Free a slot, reconnect, and confirm delivery succeeds once; reconnect again and confirm no duplicate.
- Set `onboarding.join_hint_enabled=false` before an eligible player's join and confirm neither the automatic handbook nor discovery message is sent. Re-enable it and confirm the still-eligible player receives both on the next join.
- In Survival, acquire a normal Book or Emerald and confirm the handbook recipe appears in the recipe book. Craft a replacement shapelessly from one Book and one Emerald in multiple grid arrangements, then verify the crafted copy opens the same current content and navigation even when automatic onboarding is disabled.
- Exercise deposit, withdrawal, savings, buy/sell, eight simultaneous CDs, eight simultaneous lending positions, exchange, all Prosperity Fund types and purposes, and recovery.
- Verify a specific CD and lending position can be selected, closed, or collected without changing another position.
- Compare basis, average purchase price, realized/unrealized gain, allocation, contributions, ledger entries, and personal net worth against a hand-calculated transaction sequence.
- Compare the displayed and authoritative values after depositing 1, 10, and 100 emeralds and after assembling a Fund draft above 32,767 emeralds. Close and reopen the menu, then reconnect and reopen it; confirm no signed-short truncation after either resynchronization.
- Verify market, commodity, and personal charts switch among 30 days, 90 days, one year, and all retained history.
- Verify risky and irreversible actions require two matching packets inside the server-owned confirmation window; changing selection or waiting for expiry must cancel confirmation.
- Verify Fund Apply, Cancel, Enter, and `All` update only the server-owned applied contribution amount, accept exact values such as `3000`, and never overdraw Bank Cash.
- Test full and partially full inventories in both Survival and Creative. A withdrawal must debit
  only the emeralds actually inserted, refund every item that did not fit, and preserve net worth;
  then test interrupted transaction recovery.

### Configuration

- Confirm `/emerald config show` reports the normalized world-local configuration path and every active setting.
- Reload valid settings and confirm they apply together without restarting. For `village_prosperity.development_radius`, confirm `48`, `256`, and `512` are accepted.
- In separate attempts, use an invalid boolean, a non-integer, development radii of `47` and `513`, and a misspelled key. Each reload must identify the problem and leave the complete prior configuration active.
- Disable each Prosperity Fund subtype after recording existing balances and history. Confirm new contributions of that type are blocked without deleting prior village-owned state.

### Village identity and lifecycle

- Test two villages closer than 100 blocks and confirm identities do not merge.
- Move tagged villagers around and confirm their original settlement remains stable unless intentionally resettled.
- Kill residents with pillagers, zombies, direct player attacks, and player-owned projectiles.
- Confirm the first player-caused casualty captures the village's exact pre-damage state and contribution, including across save/reload.
- Advance economic days with Village Prosperity simulation enabled and confirm the no-player-damage branch simulates and re-prices rather than holding a static score. Record a genuine non-player casualty and confirm it also changes the counterfactual.
- Cause additional player casualties and confirm they do not recapture or rebase the branch. Confirm release requires both cooldown expiry and full live-village recovery.
- Confirm player-caused extinction becomes Abandoned and does not automatically replenish victims.
- Convert a villager to a zombie villager, confirm productive population pauses, cure it, and confirm population reconciles once. Repeat with the canonical Banker and verify each conversion's new entity UUID becomes canonical only after its exact predecessor marker and chunk are saved, any conversion-time source tombstone is cleared atomically, and no replacement spawns. Repeat with saving disabled across infection and cure to confirm the unresolved A-to-B-to-C lineage converges directly to C when saving resumes.
- In separate disposable worlds on both loaders, kill the canonical Villager Banker and a converted Zombie Banker. Confirm Bank death handling occurs before general casualty accounting, restart with the replacement still pending, and verify exactly one replacement is accepted only after the matching death tombstone is durable. Simulate an economy-save failure in another copy and confirm no replacement is authorized until persistence recovers; if the exact canonical entity is still alive after a rollback, confirm the stale tombstone is cleared instead.
- Move a resident away for more than the emigration window and confirm it stops contributing without being recorded dead.
- Confirm a dead or extinct Banker never deletes player financial data.

### Recovery and physical population

- Wipe a village with hostile mobs and allow the recovery cooldown to expire.
- Confirm the abstract population remains zero until a real settler spawns when visual progression is enabled.
- Confirm settlers do not spawn with nearby threats or without a free real bed.
- Disable automatic recovery and confirm extinction persists.
- Run simulation with visuals disabled for a long period, then re-enable visuals and verify physical population converges gradually.
- Keep a known non-empty village outside every player's activation radius, advance economic days, and confirm committed settlers still affect its bounded economy while physical population and chunks remain unchanged. Return with its chunks normally loaded and confirm each census transfer preserves the committed total.

### Physical-development activation

- With the default configuration, confirm `/emerald config show` reports `village_prosperity.development_radius=256`.
- Place a known village within 256 blocks in X/Z but far above or below the player. Keep its chunks loaded and confirm it remains eligible; then move just beyond the horizontal boundary and confirm it does not. Repeat at the accepted 48- and 512-block limits.
- Keep a known village horizontally eligible but unload every chunk containing its pending settler, audit target, and construction footprint. Confirm the mod neither loads those chunks nor performs the world action; load them normally and confirm a later pass can proceed.
- Prepare 17 loaded, eligible known villages in one dimension. Confirm no more than 16 are considered during one construction pass and that rotation gives the omitted village an opportunity on a later pass.
- At a 512-block development radius, confirm settler and construction-theatre entity searches still stop at their local 48-block cap, spawned settlers receive no more than a 32-block home radius, no more than two workers receive one-shot movement cues, and no persistent AI goal is installed.

### Construction safety

Inspect Village Banks and all ten prosperity project types in all vanilla village biomes and difficult terrain.

- Confirm construction never replaces village paths, farmland, containers, player floors, or existing buildings.
- Confirm only already-loaded natural lots within the supported four-block foundation range are accepted, and that deterministic foundation columns ground every exterior feature across bounded drops without tunnelling below sound natural ground or filling space above a descending entrance stair.
- Confirm a new bank floor is above the old surface, failed placement is not marked generated, and two villages in one legacy grid do not share a Banker identity. Move the discovering player within the village and confirm the persisted settlement center—not the player position—keeps Bank selection stable.
- Confirm mud and thin snow are rejected as Bank support, while an eligible full snow-block surface remains valid.
- Place a solid block in a reserved project area before construction and confirm the project stops safely.
- Block a project before its first placement and confirm it retries later on another lot without tight-loop scanning.
- Let a reserved project's boundary chunk unload and confirm the reservation survives the delayed retry instead of being released on incomplete world information.
- Block a partially built project and confirm its exact footprint survives restart and resumes without clearing intervening player blocks.
- Complete a project's economic plan while its chunks are unloaded and confirm its housing or production benefit applies without loading or changing those chunks. Then load the area normally and confirm the queued template materializes under the ordinary budget.
- Remove fewer than 12 or fewer than 30 percent of authored structure cells from a completed project and a versioned generated Bank. Confirm both become unsafe, the scoped Banker and authored benefits suspend, no block is regenerated, and no duplicate is created, including after restart. Confirm the active Bank's surviving Exchange Desk still opens unscoped personal banking with a warning, then restore the full authored plans and confirm later audits reactivate managed access. Place replaceable dry vegetation in an outdoor clearance cell and confirm it does not suspend the Bank.
- Demolish at least 12 and at least 30 percent of the authored cells in disposable project and Bank fixtures. Confirm the old sites are recorded but never repaired or cleared, benefits/access remain suspended there, and replacements use different safe sites. Repeat relocation and restart to prove all retained old bounds/anchors remain excluded; at the 16-entry safety cap, confirm further damage remains unsafe rather than forgetting an older site.
- Interact with an Exchange Desk left at a retired Bank and confirm it does not fall through as a personal desk and reports why it is inert. Confirm a loaded scoped Banker moves to the replacement, a null lookup or unloaded canonical UUID prevents a duplicate, and only a durable matching Villager/Zombie Villager death tombstone permits one later replacement entity after Minecraft saves that entity and chunk.
- Put a different solid player block in the missing position and unload the chunk in separate runs; confirm the audit neither overwrites nor force-loads it. Confirm safe append-only upgrades still honor protection vetoes.
- Register a test guard with `VillageDevelopmentProtection.register(PlacementGuard)` and verify vetoes and thrown guard errors are not counted as progress for both banks and prosperity projects.
- Exercise the documented bank marker/chunk-save crash window in a disposable world; confirm the mod falls back to Banker access instead of rebuilding unknown blocks.
- With a low view distance, leave the ordinary Bank candidates partly unloaded and confirm the no-candidate state and canonical fallback Banker persist across restart. Step just outside the vanilla village boundary while remaining within 192 horizontal blocks, unload the original center while loading an outer safe lot naturally, and confirm the capped recovery pass continues without force-loading chunks or creating a second Banker.
- Discover a naturally flat Bank lot containing short grass, flowers, or snow and confirm replaceable vegetation does not force a fallback. Exercise all 48 ordinary Bank candidates, all 96 fallback-recovery candidates, and all 64 project candidates at their 82-/128-/84-block outer limits without force-loading chunks. For an explicitly persisted Banker-only fallback, make both a closer interior lot and a farther fully outside-village lot available and confirm the outside lot wins, the paced retry builds exactly one Bank, the original scoped Banker is reused, and fallback provenance is durably cleared. Verify format-10 center-anchored, damaged, and crash-interrupted markers without that provenance are never rebuilt.
- Generate Banks with flat, one-block-lower, and two-block-lower natural terrain in front. Confirm each has a three-block-wide landing and top stair, adds only the descending rows needed to meet natural grade, keeps two blocks of headroom, and never leaves a floating or unreachable doorway. Also try a cross-slope where the three lanes would require different stair plans and confirm that lot is rejected.
- Inspect each new Bank's connected, sealed roof, sheltered entrance, interior furnishings, lighting, and storage. Confirm all 19 green panes connect along their wall axis—including the transom—without stray perpendicular arms, and that the Bank contains exactly one Exchange Desk, no barrels, and no other villager job site.
- At night, inspect every roof-covered two-clear spawnable floor in all five new Bank dialects, including the full porch, apron, and landing. Confirm the debug light level is at least 7 with no sky contribution and that the centered portico pendant is visibly attached to its roof support.
- Load intact authored version-2 through version-7 Banks, first allowing the documented same-block pane-connection normalization to settle, then record every block state and wait through repeated Bank scans plus a save/reload. Confirm each version remains unchanged and no version-8 chimney cell is added or replaced. In disposable copies, cross the severe-demolition threshold and confirm each old ruin remains block-for-block and state-for-state untouched after retirement while the different-site replacement uses the complete version-8 plan and marker.
- Upgrade a copied format-11 world containing an intact unversioned 13x11 Bank. Confirm the guarded entrance is added once, its structure version becomes 2 only after Minecraft's chunk-save barrier and the durable marker write, pane connections normalize, and reload does not add another suffix. In separate copies, enable `/save-off`, obstruct or protect one required approach cell, unload a required chunk, break a signature block, and force the marker save to fail; confirm no version is committed, newly placed entrance cells roll back on save failure, failed retries are paced, and missing or player-replaced blocks are never rebuilt.
- Confirm residential entrances remain clear and beds are usable. Census every prosperity template's theme-appropriate utility and job-site blocks, including intentionally authored storage barrels and reading lecterns. Confirm they agree with the exact role plan, do not obstruct access, and an Exchange Hall contains exactly one Exchange Desk. Standalone Banks retain their separate no-other-job-site rule above.
- In several new format-18 villages, compare multiple project types and confirm each village keeps one coherent architectural character and one biome dialect across reloads. Verify that all 52 active revision-9 masters are distinguishable by footprint, massing, roofline, entrance, scale, and role landmark before considering block palette, while still preserving coherent circulation, multi-sided façade depth, distributed roof detail, type-specific interiors/workyards, and later-stage additions. Confirm semantic palettes, dressing kits, and approved mirror choices vary a master without erasing its identity or breaking the village theme.
- Complete repeatable projects at several village tiers. Confirm selection respects the tier-eligible scale pool and least-used template balancing within that scale, with immediate-repeat avoidance when an eligible alternative exists. Cover all six Cottage and House masters and the five masters for each other role across the required tiers. Restart between approvals and confirm the template ID/revision, palette, dressing, mirror choice, signature, and plan hash never reroll.
- Place Blueprint V2 lots north, east, south, and west of their selected road anchors. Confirm each entrance faces its connection, its adjacent road start remains stable after reload, and rotation does not break doors, beds, stairs, rails, roofs, panes, or interior access.
- Generate each prosperity building on flat terrain and on one-, two-, three-, and four-block entrance drops. Confirm the fixed top stair continues down the frozen center-road route—even when that route turns immediately—with solid supports and two blocks of headroom, and appears as soon as the building completes rather than waiting for the road. Confirm steeper, rising, fluid, occupied, unloaded, and protection-vetoed candidates are rejected before a new reservation. Upgrade a format-14 copy and confirm an unobstructed completed modular building receives the approach once; in separate copies obstruct a required cell before and during the retrofit and confirm player blocks remain untouched, the remaining approach is waived, and reload never repairs or retries it.
- Raise a village through the base, town, and city visual stages. Record the exact existing structure before each rise, confirm only a preflighted suffix is added, and confirm a later tier decline removes nothing. Obstruct one proposed suffix cell and verify the older completed stage remains operational and untouched.
- At each Blueprint V2 visual stage, confirm every authored roof-covered two-clear Zombie-spawn-valid floor remains at block light 7 or greater with skylight excluded. After completion, break a required lamp, one middle link of a hanging chain, its terminal beam, and a wall or standing-light support in separate copies. Each project must become unsafe and manual-restoration-required without recreating the removed cell; restoring the exact authored support chain must allow a later audit to reactivate it.
- Confirm a prosperity road is three blocks wide along straight segments, both sides of every turn, and both endpoints, with no shoulder inside the building envelope. Its finished center should contain dirt path and the deterministic gravel accents but no historically planned coarse-dirt accents; both shoulders should retain coarse dirt. Block or protect one shoulder and unload a later road chunk: safe loaded cells should advance best-effort, the unsafe shoulder should become a gap, the unloaded cell should wait, and the building's bounds, completion, integrity result, and benefits should not change. Create another unfinished road and confirm the waiting route does not starve it. Load an older modular road and confirm it finishes its frozen ordinary plan before the separate center-surface migration starts. Verify the migration's target/cursor do not change the ordinary road total/cursor (including an inflated development-build total), restart partway through and confirm it resumes exactly, and confirm dirt path and gravel are adopted, coarse dirt changes to dirt path, solid blocks remain untouched, and a protection-vetoed coarse-dirt cell remains unchanged. After completion, changing a path cell must not restart the migration.
- Upgrade copied format-9-and-earlier and format-10-through-17 worlds containing unstarted, partial, and completed projects. Confirm the former remain `legacy_v1` and the latter remain `modular_v1` at the same origins, bounds, block order, design identity, and progress with no reroll or conversion. Then approve a new project in the format-18 world and confirm only that project uses a persisted `blueprint_v2` selection and resumes from the identical plan hash after restart.
- Load saved revision-1, revision-2, revision-3, revision-4, revision-5, revision-6, revision-7 and revision-8 Blueprint projects in unstarted, partial, and completed states. Confirm each exact historical descriptor still resolves at its original envelope and canonical plan hash, resumes without rerolling into revision 9, and remains excluded from the selection pool for a newly approved project. Where an append-only suffix is independently eligible, confirm every new position is preflighted; repeat with a solid player block, block entity, or protection veto and confirm the older project stays operational and untouched.
- Confirm new Bankers use the registered Banker profession, claim the Exchange Desk POI, and retain scoped bank identity without gaining an unintended trade set.
- Observe a newly assigned Banker for at least one full in-game work period and after save/reload; confirm its Banker appearance and Exchange Desk job-site memory remain stable rather than flashing for one frame.
- Observe active construction and confirm no more than two suitable residents receive occasional low-speed movement/particle cues; confirm those cues stop when construction is idle and do not determine progress.

### Multiplayer and performance

- Connect at least two players and verify shared market state with UUID-isolated accounts.
- Interact with different banks simultaneously.
- With two nearby scoped banks, confirm replacement eligibility, Village-page data, and Prosperity Fund/restoration contributions all target the bank's associated settlement rather than the nearest unrelated record.
- Visit tracked settlements in multiple dimensions and confirm records, dashboard lookup, and construction stay dimension-local. Confirm Village Bank structures remain Overworld-only.
- Verify permission level 2 is still required for `/emerald` commands.
- Test dozens to hundreds of known settlement and account records and profile indexed lookup, census, catch-up, materialization, full-state save, and load time.
- While one operator owns `/emerald debug`, confirm another operator cannot mark, toggle, or stop the capture. Verify the ZIP contains only the initiating account and one watched village, omits resident UUIDs, and labels overlapping timing categories separately.

## Publication gate

Any 0.4 beta prerelease publisher must require a successful `main`-push `build.yml` run for the exact source commit, download rather than rebuild that run's exact Fabric and NeoForge binary/source artifacts, verify the complete public filename set and bytes, and record artifact IDs, workflow digests, and release-asset SHA-256 checksums.

Each loader artifact now includes a CI-generated `SHA256SUMS`. Use `scripts/prepare-release-assets.sh` from the exact clean source commit to verify both downloaded artifacts and produce one combined checksum file and release manifest. The complete step-by-step gate is in [RELEASING.md](RELEASING.md).

The manual checklist above remains required evidence before promoting the mod to a stable release. Automated startup cannot certify subjective structure appearance, third-party claim integrations, every GUI scale, long multiplayer behavior, project-block reconciliation after chunk rollback, or cross-file bank-marker/chunk atomicity; the 0.4 line remains a beta while that wider validation continues.

`scripts/smoke-server.sh <fabric|neoforge>` uses a fresh, uniquely named directory under
`build/server-smoke` for every run. It never clears either loader's normal `run` directory or a
playtest world, and its Windows cleanup targets only Java descendants carrying that run's unique
smoke marker.

`scripts/smoke-client.sh <fabric|neoforge>` likewise launches the client in a fresh directory under
`build/client-smoke`. The ordinary loader `run` directory—and any manual test world stored there—is
never reused, cleared, or modified by the automated client smoke test.


## One-command diagnostic capture

For hands-on testing, run `/emerald debug`, reproduce the issue for up to five minutes, and run the same command again or let it expire. Use `/emerald debug mark` when a specific moment should be easy to find. Attach the generated `TES-debug-*.zip` from the world's `data/the_emerald_standard_debug` directory to the bug report and mention any marker numbers.

The capture should be used for GUI transactions, village lifecycle tests, construction placement, raid recovery, settler behavior, market progression, and persistence recovery. Confirm that the final `validation.txt` contains no unexplained failures and that no private economy seed or unrelated player account is present.
