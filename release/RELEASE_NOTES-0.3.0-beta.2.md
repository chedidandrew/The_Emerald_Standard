# The Emerald Standard 0.3.0-beta.2

This beta hardens the first Village Prosperity implementation before wider public testing.

## Highlights

- Recovery no longer creates productive invisible residents before physical settlers arrive when visual progression is enabled.
- Simulation-only worlds can still recover abstractly because no physical settlement representation is requested.
- Added independent market-integration and automatic-recovery configuration toggles.
- Long-absent residents eventually emigrate instead of remaining productive forever.
- Added zombie-villager infection and cure reconciliation. Infection suspends productive population without inventing a death, and repeated scans cannot decrement the same resident twice.
- Village centers prefer nearby bells, persisted resident tags take priority for known settlements, and fallback proximity matching is tighter to reduce identity drift and accidental merges.
- Functional settlement tiers can decline after collapse while completed structures remain in the world.
- Added food spoilage, infrastructure upkeep, material upkeep, shortages, and rare local shocks so local prosperity is not guaranteed to increase forever.
- Construction no longer replaces the existing surface layer, uses a conservative natural-ground whitelist, counts a placement only when Minecraft accepts it, and releases a site that is blocked before construction begins.
- Cottage structures include real beds and settler materialization requires available physical beds.
- Warehouses use chests instead of barrels to avoid unintentionally creating fisherman workstations.
- With visual progression enabled, normal population growth queues physical settlers instead of creating productive invisible residents. Population is credited after the real villager is observed.
- Default construction pacing reduced to two blocks every ten server ticks.
- Village snapshot lists reuse one fundamentals calculation rather than recomputing the global village aggregate for every settlement.
- Player-owned projectiles receive player-cause attribution when Minecraft exposes the projectile owner.

## No-debt guarantee

None of these changes introduce player borrowing. Player balances remain nonnegative and village support remains a voluntary contribution rather than a debt instrument.

## Compatibility

- Minecraft 26.2
- Fabric Loader 0.19.3+ and Fabric API 0.158.0+26.2+
- NeoForge 26.2.0.72+
- Java 25

The persistent save format remains format 6. No new persisted fields were added in beta.2, so beta.1 village records remain compatible.

## Verification target

The release candidate must pass the loader-neutral economy, persistence, and Village Prosperity regression suites, both loader builds, packaged-JAR inspection, both dedicated-server smoke tests, and both client bootstrap tests before publication.
