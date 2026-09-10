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
and intended block states. Bank plans were introduced in format 21; the current **economy format 24**
also freezes project terrain removal, road grading and retaining-wall plans. Restarting compares the
saved intent with Minecraft's actual saved blocks instead of trusting a potentially ahead-of-world
progress cursor. Completed older Banks are not demolished or rebuilt for this feature.

Approved outdoor clearance cells run first, followed by foundations and lower courses. Supported decorations wait for their supports.
Player blocks or inventories that differ from the reserved original state are not overwritten;
obstructed cells remain pending. There is currently no new cancellation UI for an obstructed Bank.
Each nearby loaded Bank has an independent placement allowance. The Bank completion marker is
saved only after its blocks are flushed; queue removal is committed in that same economy save.
Only then is the managed Banker finalized. Initial role-appropriate loot is attached only when
the builder actually places a fresh container, never when it encounters an existing matching
container or resumes after restart. Construction does not refill looted containers. Existing fallback
Bankers are retained during construction and moved to the completed Bank.

The background-life follow-up adds persistent builder assignments, cosmetic delivery loads and
supported display-only worksite props. It does not add a custom arm animation or require a
particular villager for each block. Background retry diagnostics replace any proposed construction
management UI. See [background village life](BACKGROUND_VILLAGE_LIFE.md) for cleanup and save safety.

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

Back up before upgrading. Older binaries must not open the upgraded format-24 save.
