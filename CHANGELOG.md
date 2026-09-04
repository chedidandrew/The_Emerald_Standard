## 0.4.0-beta.1 - 2026-09-03

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

# Changelog

All notable changes to The Emerald Standard are documented here.

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
