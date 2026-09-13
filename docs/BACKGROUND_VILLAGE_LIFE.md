# Background village life (unreleased beta.5)

## Automatic work, not another management screen

Construction stays automatic. No construction panel, supply checklist, notifications, or site
administration buttons were added. The existing Banker > Village > City expansion page gains one
short upkeep recommendation: crops/animals or Food funding, Infrastructure materials/housing,
General treasury reserves, lighting, resident protection, or time to mature. Advice uses the
existing reported expansion reason and local village statistics; it is not a promised donation
amount or an instruction to refill physical construction containers.

The `the_emerald_standard_construction` logger records meaningful worksite state transitions,
coarse progress, automatic retries and completion in the normal Minecraft log. Repeated identical
wait states are suppressed; progress updates are limited to ten-percent buckets and at most once
per minute per site. Existing opt-in `/emerald debug` diagnostics remain available. Private player
balances and inventories are not included in these construction messages.

## Recovery and save safety

Fresh reservations persist `construction_started=false`. Immediately before the first terrain or
building write, the mod must durably save `construction_started=true`. A zero block cursor alone
is not proof of an untouched site: terrain work may already exist or a crash may have interrupted
the progress save. After repeated temporary failures, only a proven untouched project may release
its lot and search again. Ordinary started sites retain their frozen plan and retry in place. Existing
backoff, loaded-chunk restrictions and per-write protection checks remain. Changed player blocks
and containers are never erased to make recovery succeed.

A founding home's persistent physical obstruction is now a bounded exception: after 6,000
loaded, unpaused obstruction ticks, the district may fund one replacement. Its original partial
lot remains protected, unused escrow transfers, and the consumed share is paid again. No refund
or world demolition occurs. Other eligible districts no longer wait indefinitely behind that
founding home. See [development protection](DEVELOPMENT_PROTECTION.md) for exact rules.

Banks continue their frozen per-cell retry logic, including deferred unsupported cells; a stalled
Bank now waits ten seconds between block-obstruction retries instead of rescanning every half second.
Live entity occupancy instead retains the ordinary ten-tick retry cadence. Banks are
not automatically relocated after partial construction. Permanent player obstructions or claim
vetoes can leave work pending indefinitely. Recovery does not grant demolition permission.

Economy format **26** retains the durable project-start marker, obstruction recovery evidence and
per-chunk food observations, and adds per-container Bank loot receipts. Missing older start markers conservatively
mean started. Existing project recipes and old frozen terrain plans are not rerolled. Back up
before upgrading and use that backup to downgrade; older binaries must not open format-26 saves.

## Recognizable construction

The latest beta.5 pass supersedes the earlier resident-assignment and display-only delivery props
with dedicated visiting builder mobs and real temporary caution fences. Sites receive one to four
hard-hatted workers based on footprint, using actual walking and independently articulated hammer
arms. They do not recruit existing residents or alter population, inventory, production or build
speed. Temporary blockages stop hammering and send workers outside the footprint so a worker cannot
permanently occupy the next construction cell. Finished/retired jobs send crews away.

Fence receipts restore displaced low plants/snow when their owned blocks are removed after work.
Player replacements and deliberately broken barriers remain untouched. Ordinary chunk reload
retains crew assignments and fence receipts; unloaded cleanup waits for the chunks to return.
Arrival/departure visibility uses a best-effort server-side camera/obstruction check. See
[construction crews](CONSTRUCTION_CREWS.md) for placement rules, restoration, save boundaries and
third-person/freecam limitations. Old tagged display props and assignment tags are cleaned up
without deleting residents or unrelated entities. Physical item delivery logistics are not simulated.

## Neighborhoods

Fresh lot candidates receive a stable village-specific stagger of up to six blocks on each axis.
Existing search expansion and nearest-completed-neighbor street branching remain in effect; the
new spacing makes their results less grid-like. Reserved lots and existing street anchors stay put.

A safe, flat roadside patch may receive a five-by-five square, flower garden or seating/lantern
nook. These are deliberate repeated motifs, not random decorative blocks. They join an approved
flat road at its walking height, keep an open center, seat details on solid support, and use dry
planting on sand. Each pocket is optional and frozen in the new site's preparation plan. Claims,
occupied construction cells, storage, player landscaping, steep terrain or insufficient space
skip the pocket without rejecting the building. Terrain and permanent pocket placements share
the existing **two operations per second per site at 20 TPS** budget. Existing buildings are not
retrofitted with gardens during this pass.

## Background cost

Loader join/leave events maintain a loaded villager/display index, avoiding repeated scans of all
world entities. Food surveys rotate across ticks and retain last-known observations for chunks
that unload; genuinely empty completed loaded scans still remove the food bonus. Slow ticks
reduce the food survey cell budget, not each construction site's independent 2-operations/second
allowance. Frequent durable district changes use checksummed full/delta journal records between
periodic full snapshots; financial saves remain synchronous full-world economy checkpoints.

## Verification boundary

Opt-in disposable server fixtures exercise real villager navigation, AI and physical movement on
a graded street and through representative completed cottage/Bank layouts. Directed walking goals
may be retried; logs report tick counts and retries. Tests never teleport the walker between
destinations. These are representative regression checks, not a claim that every catalog variant,
terrain combination, or unsupervised villager schedule has been certified.

The Bank walking fixture exposed a low-clearance threshold: carpet directly behind the door
raised a villager into the lintel. New Bank structure revision 9 sets only that first carpet row
back. Revision 8 and earlier recipes remain frozen for existing Banks and pending construction;
the update does not rebuild, silently alter, or flag those older Banks as damaged.

Other checks cover serialized assignments, bounded props, owner-only cleanup, supported pocket
motifs, protection vetoes, deterministic layout variation, retry-frontier growth and upkeep advice.
Client appearance, long-running village AI and huge-city performance still need gameplay review.
