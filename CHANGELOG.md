# Changelog

All notable changes to The Emerald Standard are documented here.

## 0.4.0-beta.2 - Unreleased

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

- Expanded the dashboard from five pages to seven with dedicated Fund and compact Activity Log pages, commodity and personal charts, richer portfolio fields, and collision-safe term and position selectors.
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

- Automated common tests, dual-loader builds, packaged-JAR checks, both dedicated-server startups, and both client bootstraps passed for earlier candidate implementation commit `49e1e7cfb4df5d68970162b2da66170d1f6b7efd` in workflow `33894198970`; the current unpublished changes require a new exact-commit workflow.
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
