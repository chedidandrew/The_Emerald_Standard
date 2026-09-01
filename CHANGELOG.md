# Changelog

All notable changes to The Emerald Standard are documented here.

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

### Added

- Locked-rate 30, 90, 180, and 365-day CDs that stop accruing at maturity.
- Deterministic partial and full villager-business defaults without any player-debt path.
- A private economy seed stored independently from the visible Minecraft world seed.
- Versioned save format 2 with migration from the prototype format.
- Atomic save replacement, backup recovery, state validation, and mutation rollback.
- Inventory-safe deposit, exchange, and withdrawal recovery behavior.
- Valuable block and ore exchange forms, including diamond blocks, gold ores, raw-gold blocks, netherite blocks, and emerald blocks.
- Backup preservation that refuses to overwrite a known-good backup with a corrupt primary save.
- Statistical, persistence, maturity, clock, and no-debt regression suites.
- Architecture, economy, testing, and publication-gate documentation.

## 0.0.1-prototype - 2026-08-31

- Initial public architecture prototype for Minecraft 26.2.
- Added a deterministic market proof of concept, command interface, Fabric project, and NeoForge project.
