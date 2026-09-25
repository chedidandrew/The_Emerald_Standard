# Construction clearance, paving, newspaper edges and recovery — beta.52

## Changes

- Authorized Bank/building placement and building terrain preparation use a separate
  creature-clearance path. Read-only surveys retain the original occupancy predicate.
- Attempts relocate non-player living blockers 1.25–3.25 blocks onto nearby loaded,
  collision-free, dry positions with solid footing. No direct entity deletion or damage
  is issued. Repeated obstruction at the same position and target state permits placement
  after 200 game ticks; normal Minecraft collisions may injure or kill the creature.
- Players and player-carrying vehicle groups are excluded. Block protection, inventory,
  chunk and native block-survival checks are not overridden. Wait records are bounded,
  transient and expire after inactivity; a new work cell or creature starts again.
- Normal road work requests at least eight operations on an existing construction pulse,
  sharing the original server budget. Paving accepts up to sixteen writes per call rather
  than two; normal connection admission is every five ticks rather than twenty.
  Route calculations, saved survey plans, bridge rules and occupancy protections are unchanged.
- Rolled/open newspaper models use 54/33 closed strips respectively, preserving the existing
  artwork, silhouette and hand transforms. Side UVs sample non-transparent interior pixels.
  Native ground, head, third-person and item-frame transforms are retained explicitly.
- Safety starts healing after one complete quiet economic day. A new incident restarts
  mourning. This does not shorten the distinct extinction/abandonment resettlement rules.

## Market investigation

The existing end-of-tick economy observer already reads the Overworld clock. A new native
test moves that clock from night to Minecraft's WAKE_UP_FROM_SLEEP marker: the economic day
and live quotes advance, and a repeated observation does not count the jump twice.
This passes on Fabric and NeoForge. No speculative market runtime change was made.
This exercises the native clock transition, not a human-operated bed or a third-party sleep
mod. An affected-world debug capture and installed version are still needed if the report persists.

## Handbook review

Reviewed guided chapters and compact/lectern pages. Updated construction risk, paving pace,
one-day mourning and solid newspaper edges; retained lamp, settled-path, support, storage and
handover guidance. Recipes and creative-only spawn eggs are unchanged. Existing market-clock
guidance already agrees with the verified behavior.

## Validation

- Focused native Fabric and NeoForge server suites passed: creature movement, timeout,
  fresh-cell/inactive-wait reset, player protection, Bank construction, walkways, lighting,
  bridges, native sleep clock and market packet round-trip.
- Resource regression checks require six faces per segment, bounded geometry and opaque
  side samples. Guided/compact handbook resource checks pass.
- Initial fixture failures were resolved by using the smoke harness's already entity-ready
  chunk; loading terrain alone does not make a newly spawned entity queryable.
- Full common suite passed, including both handbook forms, newspaper geometry/opaque
  edges, one-day recovery boundaries, economics, persistence, loader parity and wrapper checksums.
- Full Fabric and NeoForge builds passed. Final resource-only packaging was refreshed after
  preserving inherited item display transforms and compact handbook guidance.
- No fresh in-game visual inspection of the newspaper with the user's shaders/resource
  packs was performed. The supplied gameplay worlds were not edited.
