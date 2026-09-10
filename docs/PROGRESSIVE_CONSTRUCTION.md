# Progressive construction and accessible entrances

The unreleased beta.5 follow-up gives **each active site one authored block operation every
10 server ticks**: two blocks per second at 20 TPS. Ten concurrent sites can each advance at
that rate; they do not share two blocks between them. Server lag lowers wall-clock throughput.
This is a maximum work rate, not a promise to build through paused, unfunded, obstructed or
unloaded sites. Site searches and economic labor can still introduce waiting time.

All due village projects are visited each pulse. New-site clearance uses the same site's
allowance. Completed sites finish their approach and public trail with their own one-operation
allowance. Existing administrative repair/upgrade tools and instant comparison-gallery commands
are not normal growth construction. Native Minecraft neighbor updates can change connected block
states; those are not extra authored construction placements.

The two old construction speed configuration keys remain accepted for save/config compatibility
but normalize to `construction_interval_ticks=10` and `construction_blocks_per_tick=1`.
Other explicit world settings remain unchanged. There is no longer a shared 16-village
construction-pass cap. Loaded-chunk, proximity, protection and economic constraints remain.

## Banks

New and relocated Banks now reserve an exact terrain-supported plan before changing the world.
The plan contains the origin, Banker anchor, village identity, authored version and the original
and intended block states. Bank plans were introduced in format 21; the current **economy format 26**
also retains frozen terrain work and adds durable per-container loot receipts. Restarting compares the
saved intent with Minecraft's actual saved blocks instead of trusting a potentially ahead-of-world
progress cursor. Completed older Banks are not demolished or rebuilt for this feature.

Approved outdoor clearance cells run first, followed by foundations and lower courses. Supported decorations wait for their supports.
Player blocks or inventories that differ from the reserved original state are not overwritten;
obstructed cells remain pending. There is currently no new cancellation UI for an obstructed Bank.
Each nearby loaded Bank has an independent placement allowance. The Bank completion marker is
saved only after its blocks are flushed; queue removal is committed in that same economy save.
Only then is the managed Banker finalized. Initial role-appropriate loot is attached only when
the builder actually places a fresh container, never when it encounters an existing matching
container. Each handled storage position is saved before attaching a loot table. A removed recorded
container stays missing rather than being recreated by a pending plan; an emptied container stays
empty, and a player replacement keeps its contents. A receipt-save failure cannot grant loot.
Older unfinished plans without receipt provenance finish remaining storage empty; existing storage
is unchanged. A crash between creating an empty container and saving/attaching loot may leave empty
or missing storage, never permission to reroll it. Restore a missing container manually if needed to
finish that pending Bank. Completed Banks remain unchanged. Existing fallback
Bankers are retained during construction and moved to the completed Bank.

The background-life follow-up adds persistent builder assignments, cosmetic delivery loads and
supported display-only worksite props. It does not add a custom arm animation or require a
particular villager for each block. Background retry diagnostics replace any proposed construction
management UI. See [background village life](BACKGROUND_VILLAGE_LIFE.md) for cleanup and save safety.

## Live worksite safety

Immediately before changing a cell, construction checks actual collision shapes against living
non-spectator players, villagers and animals. A wall/slab cannot intersect an occupant; excavation
or path lowering cannot remove its current footing. Matching-height floor replacement and safe
adjacent work remain allowed. Dropped items and display-only worksite decorations are not blockers.

This is a temporary work wait, not a lot-admission veto. Ordinary projects keep the exact cell cursor;
Banks can work on another safe pending cell. Both retry at the existing ten-tick site cadence once
otherwise eligible. Occupancy does not increment permanent-obstruction counters, spend a founding
home's relocation allowance or waive an optional road/approach. Workers hold their deliveries;
construction logs `waiting_for_entities` in the background. No player-facing management is required.

Checks cover authored placement, frozen terrain work, roads, entrances and managed connection
updates. They prevent direct collision and lost footing at the moment of the mod's write; they are
not a guarantee of an escape route through every partly built layout, nor control over another mod's
movement/physics or subsequent native neighbor updates. No entities are pushed or teleported.

## Entrances

New project site searches prefer a level approach among each bounded batch of safe candidates.
If none is level, the candidate needing the fewest approach steps wins. Banks rank their valid
lots by the smallest approach elevation drop before the existing distance/orientation priorities.
This reduces raised entrances without making mildly uneven terrain unusable.

Existing authored thresholds, porches, and reserved structures are unchanged. New lots can use
median-grade floors with up to four blocks of cutting and four blocks of supported filling.
Managed village projects try all four orientations; Banks keep their fixed north-facing layout
and raise the selected grade when needed to meet the front approach. This is a stronger preference
for accessible, near-grade entrances, not a guarantee of a flush threshold on every building.
See [terrain development](TERRAIN_DEVELOPMENT.md) for vegetation and protection boundaries.

Back up before upgrading. Older binaries must not open the upgraded format-26 save.

Explicit [no-build areas](DEVELOPMENT_PROTECTION.md) also suspend overlapping pending lots.
Workers now share construction's eligibility checks; paused, abandoned and repair-required
projects release assignments, while temporary obstructions put deliveries on hold. A blocked
founding home has a bounded, funded replacement path without demolishing its original site.
