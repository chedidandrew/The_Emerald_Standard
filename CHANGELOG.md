# Changelog

All notable changes to The Emerald Standard are documented here.

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
- Verified Java 25 Fabric and NeoForge 26.2 builds plus both dedicated-server startup smoke tests in GitHub Actions run `33461667400`.
- Commit-specific alpha.2 artifact IDs, ZIP digests, and SHA-256 JAR checksums under `release/ARTIFACTS-09307b13.md`.

## 0.1.0-alpha.1 - 2026-08-31

### Changed

- Reclassified the initial repository from a full `1.0.0` release to an honest alpha milestone.
- Rebuilt the market model around independently mixed Gaussian inputs.
- Removed the unexplained 19% market-drift offset and recalibrated VILX near a 10% long-run CAGR.
- Lengthened market regimes from a few days to Minecraft months and years.
- Reworked individual-company returns so every ticker has a plausible positive long-run distribution.
- Added a 0.25% per-side trading spread.
- Made valuable-resource exchange prices dynamic and regime-sensitive.
- Updated Fabric to the official Minecraft 26.2 Loom configuration without the unavailable explicit Mojang mappings layer.
- Updated CI to run common tests first and build Fabric and NeoForge independently with `fail-fast: false`.
- Consolidated duplicated Fabric and NeoForge command behavior into a shared Minecraft-facing source set.

### Added

- Locked-rate 30, 90, 180, and 365-day CDs that stop accruing at maturity.
- Deterministic partial and full villager-business defaults without any player-debt path.
- A private economy seed stored independently from the visible Minecraft world seed.
- Versioned save format 2 with migration from the prototype format.
- Atomic save replacement, backup recovery, state validation, and mutation rollback.
- Inventory-safe deposit, exchange, and withdrawal recovery behavior.
- Valuable block and ore exchange forms, including diamond blocks, gold ores, raw-gold blocks, netherite blocks, and emerald blocks.
- Backup preservation that refuses to overwrite a known-good backup with a corrupt primary save.
- Statistical, persistence, maturity, clock, resource-quote, and no-debt regression suites.
- Architecture, economy, testing, and publication-gate documentation.
- Verified Java 25 Fabric and NeoForge 26.2 JAR builds from GitHub Actions run `33455350158`.
- Commit-specific artifact provenance and SHA-256 checksums under `release/`.

## 0.0.1-prototype - 2026-08-31

- Initial public architecture prototype for Minecraft 26.2.
- Added a deterministic market proof of concept, command interface, Fabric project, and NeoForge project.
