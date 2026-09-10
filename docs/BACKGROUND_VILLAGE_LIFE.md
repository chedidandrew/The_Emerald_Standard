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

Up to two suitable adult residents retain each worksite's assignment using saved native entity
tags. A villager is not assigned to multiple loaded jobs. The same builders are reused, with
replacement candidates for missing/dead/unavailable assignments. Bankers and children are not
recruited. Sleeping, trading and recently attacked residents are not steered. Professions,
inventories and normal economic output are unchanged, and a missing worker never stalls growth.
Paused, abandoned, repair-required and completed jobs release their assignments. Temporarily
blocked sites stop deliveries while awaiting retry. Cleanup removes only a route that this mod
installed; it does not erase an unrelated walk target or interrupt unrelated navigation.

Builders make short staging-to-site delivery trips. A small display-only load accompanies the
delivery leg. Ground-supported scaffolding and a material pile are also vanilla block-display
entities, not real blocks: they cannot drop items, block access, or become a resource exploit.
The scaffolding is visual, not climbable. There is no custom unfolding-arm animation. This is
construction theatre backed by the existing abstract economy, not physical item logistics.

Only tagged mod-owned displays and assignments are cleaned up when a site ends, moves, or visual
progression is disabled. Loaded entities are reconciled periodically, including after their chunks
return; no chunks are forced to load for cleanup. Real player blocks and unrelated entities are
untouched. Displays are skipped when their ground support, clearance or protection check fails.

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
