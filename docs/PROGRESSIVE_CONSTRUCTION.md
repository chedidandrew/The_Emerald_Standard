# Progressive construction and accessible entrances

## Breakable, no-drop construction and finishing repairs (beta.20)

Exact builder-supplied cells remain breakable before physical handover. Their normal block loot
and mining XP are suppressed; neighboring property is not covered. New chests/barrels remain
empty and locked against menus and ordinary hopper access. Starting loot is issued once at
handover, never per replacement. Piston export/import, falling-material export and Enderman
pickup are guarded. Normal block placement into reserved blueprint cells is refused before
consuming the item.

Ordinary projects verify their finishing pass and rewind missing work automatically without
manual blueprint repairs or player inventory/account charges. Missing supports can be repaired
earlier if needed. Occupied cells defer other independent work within the current phase; later
structural phases still respect supports. Players and living entities are never built through
or deprived of footing. The final required occupied cells may delay completion. The report
identifies the waiting location; players and pets are never forcibly teleported or deleted.

Existing foreign blocks, containers, fluids and external land protection remain preserved and
can need an obstruction cleared. Administrator/other-mod supplied inventory contents take
priority over no-drop suppression: they are preserved with ordinary removal behavior and are
never replenished.

Per-dimension construction_ownership SavedData records reservations, exact ownership and one-shot
loot eligibility. Pending economy authority gates the receipts; handover releases them. A receipt
is saved and chunks are flushed before granting completion. Starting loot is attached only after
completion is accepted by the durable economy record. An interrupted
grant can conservatively leave an empty chest. Minecraft chunk/entity/SavedData and economy files
are not a power-loss-atomic database; keep complete backups and do not downgrade an active site.

Legacy active prefixes may adopt matching non-container blocks on first enrollment, excluding
known player-build clusters. Existing containers receive no fresh loot. Old Manual Repair records
are ambiguous: the flag also described completed remodels. They are not automatically enrolled;
inspect their debug evidence before restoring authority. Completed buildings retain normal drops
and editing, never automatically regenerate harvested blocks and never refill containers.

## Site-search responsiveness and diagnostics (beta.17)

An unreserved project's failed sweep waits 10 seconds, then 20, then at most 30 seconds
at 20 TPS. This is cooldown time, not a promise to finish a complete search in 30 seconds.
The existing one-position-per-construction-pulse budget, up to four orientations and bounded
expanding candidate rings remain. Loaded chunks, activation radius, pause state and all terrain,
entrance, storage, claim and reservation checks still apply. No chunk is force-loaded.

Old long unreserved-search deadlines are clamped when the district is next processed, preserving
the candidate cursor and failure count. The persisted deadline does not slide forward on each
pulse; repeated same-tick deferrals cannot stack minutes of site-search delay. Reserved-site
materialization backoff is unchanged. Final preparation rejection now checkpoints candidate
progress instead of potentially revisiting the same position forever.

Town > What next? Progress report shows search progress, cooldown and the last observed check.
The normal /emerald debug toggle records actual candidate locations, rotations, rejection
categories, per-sweep outcome counts, recent checks and every project's persisted search/retry
state. Run it near the affected district, allow a search to occur, then run it again to package
the ZIP. No special subcommand is needed. Evidence is bounded and session-only; missing historical
reasons are explicitly unknown. A zero Y coordinate in a column-level rejection is a marker,
not a claimed underground obstruction. Cached map terrain is not proof that a footprint is loaded.

## Support-first build sequence (beta.14)

Normal development sites prepare their approved terrain and eligible caution fencing before
the building. The remaining structural work prefers ground foundations and floor, frame,
walls, roof, openings, furnishings and lighting. Supports take precedence over a cosmetic
phase label: an upper floor is not a ground foundation, and a beam waits for its connected
frame or masonry. Sloping stair/slab roof courses can connect along their normal step edges.
Hanging lamps wait for their attachment; chains are installed from the top down.

This changes execution order, not authored geometry, prices, labor or block throughput.
Ordinary projects durably register versioned sequence boundaries before new placements.
An older unfinished project keeps its already-processed prefix; only remaining work is
reordered. Later upgrades append separate ranges, so restart and upgrade do not reinterpret
previous progress. Existing floating pieces are not removed. Banks apply the same support-first
schedule to pending saved-intent cells while preserving their original states and loot receipts.

Live loaded-world support checks also prevent dependent structural work from proceeding over
a support that has gone missing. Unfinished damage follows the beta.20 finishing-repair policy
above; completed buildings retain modification protection and never regenerate harvested blocks.
Legacy disconnected details are deferred until connected work is exhausted, not silently
redesigned or allowed to permanently prevent completion. This is a visual schedule, not an
engineering simulation or a guarantee for every modified/modded layout. Intentional access
gaps and protected or unsuitable fence positions remain open.

The unreleased beta.5 follow-up defaults to **two authored block operations per second per
active site** at 20 TPS (one every ten ticks). Settings can change this to 1–100 using
`village_prosperity.construction_blocks_per_second`. Ten concurrent sites can each advance at
the selected rate; they do not share one allowance. Server lag lowers wall-clock throughput.
This is a maximum work rate, not a promise to build through paused, unfunded, obstructed or
unloaded sites. Site searches and economic labor can still introduce waiting time.

All due village projects are visited each pulse. New-site clearance uses the same site's
allowance. Completed sites finish their approach and public trail with their own independent
allowance. Existing administrative repair/upgrade tools and instant comparison-gallery commands
are not normal growth construction. Native Minecraft neighbor updates can change connected block
states; those are not extra authored construction placements.

The two old construction speed configuration keys remain accepted for save/config compatibility;
a legacy-only configuration keeps the two-block default. An explicit new speed key takes
precedence. Work is spread over twenty ticks even for rates such as 3 or 7, and unused allowance
is discarded rather than accumulated from ordinary ticks. Skipped-night work is the bounded
exception described below.
Other explicit world settings remain unchanged. There is no longer a shared 16-village
construction-pass cap. Loaded-chunk, proximity, protection and economic constraints remain.

## Starter growth and sleeping

Healthy young settlements mobilize limited local construction supplies and use two economic
work shifts per day. The target is one or two useful small structures per Minecraft day, not a
guaranteed completion count in difficult terrain. The boost requires four living residents,
Active status, Safety >= 45, food >= ten units per resident, and no city-upkeep deficit. Its
strength tapers through eight lifetime project approvals and is divided by the square root of
the city's district count. The last starter project retains its tapered labor until it finishes.
The serial is persistent: relocation, demolition and a replacement desk cannot reset the boost.

Supplies are limited local construction support, not money for the player or donated Fund capital.
Project inputs still pay their full ordinary costs; sponsor prices, refunds, principal and receipts
are unchanged. Normal admission waits once two non-repair-required development sites remain
unfinished in a district. Existing extra projects are kept, not cancelled. Banks are independent.
Early population growth is also more reliable, but still needs beds, food, safety and a safe
physical arrival; no more than one new arrival is planned per day.

The economic clock already counts the skipped portion of a sleeping night once (using the
maximum overlapping wall/game/daylight elapsed time). Physical construction now also detects
that skip. Sites seen within the previous 40 ticks may earn extra work opportunities based on
the configured construction speed, capped at 12,000 skipped ticks per site. They are drained on
ordinary construction pulses: at most eight extra operations per site and 32 across both builders,
selected with a rotating queue. A soft three-millisecond deadline further limits extra block work.
Normal per-site allowances are unaffected; no new scans, chunks or entities are forced by this queue.

These are best-effort opportunities, not permission to place every owed block regardless of
obstructions. Unused granted work is discarded; all protection, occupancy, economic-progress,
support and receipt checks remain. New sites cannot inherit old nights. Credits are session-only,
expire after 1,200 ticks without a visit, and reset on world restart, backwards clocks, speed changes
or switching into forced development. A huge time command cannot build an offline city's backlog
in one tick. Finished blocks and economic state remain saved as before.

## Banks

New and relocated Banks now reserve an exact terrain-supported plan before changing the world.
The plan contains the origin, Banker anchor, village identity, authored version and the original
and intended block states. Bank plans were introduced in format 21; the current **economy format 36**
retains frozen terrain work and durable per-container loot receipts, and adds versioned village
project construction-order boundaries. Restarting compares the
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

The latest worksite follow-up adds persistent visiting builder assignments, articulated hammer
animations and temporary caution fencing. It does not require a particular worker for each block
or transfer physical inventory supplies. Background retry diagnostics replace a construction
management UI. See [construction crews](CONSTRUCTION_CREWS.md) for cleanup and save safety.
Normal sites finish bounded fence preparation before the main structural build; village terrain
clearing/leveling is done first so it does not undo the perimeter. Occupied/unloaded fence cells
wait, while intentional entrances and protected/unsuitable cells remain open. Both halves of
vanilla tall grass and large ferns can be restored safely when owned fences are removed.

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

## Unsupported optional dressing

Since beta.25, a never-supplied optional yard decoration cannot hold a whole building
at "Waiting for support" when its planned foot was legitimately skipped on solid terrain,
protected ground or other unsuitable surroundings. That unsupported decoration is waived
on an eligible construction pulse, leaving a gap without placing floating blocks.
Saved stalled projects use the same rule without changing their blueprint or sequence.

Required structure, required safety fixtures and already-supplied unfinished repair work
are not waived. Missing supplied supports still use the repair pass, and players, foreign
storage, land protection and missing chunks still block unsafe writes. A timer does not
bypass those protections. Live support waits name the proposed block and support locations;
`/emerald debug` includes the same fresh observation and actual block/chunk state.

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

Back up before upgrading. Older binaries must not open the upgraded format-36 save.

Explicit [no-build areas](DEVELOPMENT_PROTECTION.md) also suspend overlapping pending lots.
Workers now share construction's eligibility checks; paused, abandoned and repair-required
projects release assignments, while temporary obstructions put deliveries on hold. A blocked
founding home has a bounded, funded replacement path without demolishing its original site.
