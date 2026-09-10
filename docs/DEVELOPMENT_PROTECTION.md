# Built-in development protection (unreleased beta.5)

No land-claim mod is needed. This is protection from The Emerald Standard's village development,
not an anti-grief system: it does not prevent players, mobs or other mods from changing blocks.

## Mark an area

```text
/nobuild add my_base 100 200 160 260
/nobuild list
/nobuild remove my_base
```

The two corners are **X/Z pairs**, inclusive, in your current dimension. Y is deliberately omitted:
the zone covers the entire world height. Reversed corners work. Equal side lengths create a
square; unequal lengths create a rectangle. Relative `~` coordinates work too, for example
`/nobuild add home ~-30 ~-30 ~30 ~30` reserves a 61-by-61 area centered on you.

- Names contain 1-48 letters, digits, underscores or hyphens and must be unique in the world.
- Ordinary players can create up to 16 zones, each at most 256 by 256 blocks. Operators may
  create larger/more zones within Minecraft's coordinate bounds.
- Only a zone's owner or an operator can remove it. `list` shows saved zones and dimensions.
- The operator-only `/emerald nobuild ...` alias offers the same operations.
- No blocks are placed, removed or moved by these commands. New intersecting lots are rejected;
  pending overlapping building/Bank lots wait. Protected road/detail cells are skipped or deferred.
- Removing a zone permits future safe development; it does not force a building or repair damage.

## Automatic player-build evidence

Successful native player block placements are observed on both loaders. Solid blocks and block
entities are recorded; ordinary torches, flowers, crops and vegetation are not land reservations.
The next server tick verifies the actual state before committing evidence, so canceled placements
are not treated as builds. Player breaks remove obsolete evidence after the actual removal is
verified. Nearby, face-connected groups of at least four surviving recorded solid placements
protect their immediate surroundings. One isolated temporary solid marker is not a land claim.

Existing storage and crafted-block safety checks still apply. Older recognizable raw-log frames
(a three-log horizontal beam on two upright posts) are protected during tree clearance. Natural
trees and ordinary vegetation remain clearable on otherwise approved sites. Mining holes and
missing logs do not themselves create protected territory; terrain must still be safe to support.

This cannot perfectly infer intent from old dirt landscaping, natural-looking vegetation, commands
or mod-specific placement systems. State-matching evidence can also become stale when blocks
are changed indirectly. Use an explicit zone for land you definitely want left alone. Installing
an unrelated claim mod does not automatically add an adapter; the existing cooperative guard API
remains available for future integrations.

## Automatic recovery and workers

A founding district's first home can recover after at least 6,000 ticks of observed, loaded,
unpaused physical obstruction (five minutes at 20 TPS). Economic waits, saving failures and
unloaded time do not advance that threshold. A replacement requires money/materials to repay
the already-used share; unused original construction escrow transfers, with no refund or free
blocks. A partial original lot is retired and excluded from future development, never demolished.

Only one such replacement is allowed per founding district. Ordinary started projects and Banks
still wait safely in place; provably untouched project reservations retain their existing retry
and relocation behavior. Once a founding home is persistently physically blocked, other districts
can proceed if their normal expansion requirements are met. The blocked district still needs a
completed safe home before settlers materialize.

Project and Bank workers share village eligibility. Paused/abandoned, repair-required or finished
jobs release workers and owned temporary props. Temporary blockages stop deliveries; suitable
existing assignments can wait for the next retry. Sleeping, trading, hurt or threatened villagers
are not steered. Cleanup removes only navigation installed by this mod, not unrelated AI routes.

## Performance and durability

- Loaded villager/display membership is cached with loader join/leave hooks; recruitment uses
  local entity queries. This avoids repeated enumeration of all loaded entities.
- Food surveys rotate across ticks and merge completed chunk observations. Unloaded chunks keep
  their last known counts instead of briefly deleting food production. A completed empty loaded
  chunk lowers its count. Coverage/overlap changes converge on subsequent surveys; cached values
  are observations, not a live census of unloaded territory.
- Sustained slow ticks reduce the food survey cell budget. Construction still gets one operation
  per ten server ticks **per site**, independent of other sites. No new city-size cap was added.
- Frequent durable district updates use forced checksummed journal frames: a full district record
  followed by changed-field deltas. Periodic full economy checkpoints and synchronous financial
  saves remain. A torn final frame is ignored; corruption of a complete frame fails closed.

Economy **format 26** retains recovery evidence and saved food chunk observations and adds Bank loot receipts. Back up the entire
world before upgrading and restore that backup to downgrade. Keep `world/data/emerald-development-land.journal`
and the economy snapshot's `.village-journal` sidecar with the primary/backup economy files.
Do not copy just the main economy file while the server is running. New no-build commands are
acknowledged only after their land record is saved. A land-store write failure suspends development
and logs an error instead of silently dropping protection.

These journals do not make Minecraft chunk writes and economy saves one transaction. Existing
cross-file crash limits still apply. Automated tests cover focused recovery/protection cases and
representative navigation; extended huge-city gameplay and every third-party AI are not certified.
