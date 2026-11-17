# Village footbridges — beta.66

Even short routes inspect water and unloaded chunks. Preview results are bound to their complete immutable request and bridge context; changing a destination, start, lot exclusions or bridge settings restarts the approval. After surveying, the destination's current lot exclusions are checked again before the atomic reservation. Two projects waiting on routes cannot both commit an overlapping destination.

Walkways compare safe land detours with straight water crossings. A bridge is only approved as part of a route reaching a completed entrance or real village path, not an arbitrary opposite bank.

## Design

Three walkable blocks lie between exterior rails, capped posts and the village's inward-facing street lamps. Frozen village materials determine timber decks and trestles, masonry corbels, or sandstone arches with matching timber. Repeating sections fit the actual water length. Six dry approach rows per shore provide solid foundations, stairs and landings; shore differences up to two blocks are supported without excavating cliffs.

New-world default maximum water length is 96 blocks, excluding approaches, configurable up to 128. Existing worlds keep their saved limit, including earlier 48-block settings. The navigation opening has two full air blocks above the water and up to five rows across the river; short creeks use the available width. Exterior piers and arch details stay out of that opening. No causeway is filled across the river.

## Bridge-first organic expansion

New managed organic projects inspect a viable dry building lot first. Where water separates it from the existing connected district, they survey an onward route before committing either destination construction or bridge expenditure. A safe land detour is allowed. Crossing approaches must not overlap the future building. The exact crossing plans are checkpointed, then the destination lot and required bridge identifiers are committed together through the economy journal.

A destination-bound crossing cannot spend or build without that durable destination reservation, even if another connector picks it up after a crash. Unspent provisional reservations can be discarded. Destination preparation and building wait until every dependency is verified complete and still walkable. Changed, missing or unloaded crossings keep that gate closed; completed bridges are never regenerated to erase player edits. This extends the same district, not a separate village charter. Existing projects are not retroactively held behind new dependencies.

## Funding and saved work

Bridges are separate Infrastructure jobs, not new districts or Banks. Materials cost one village unit per sixteen changed blocks, rounded up. Treasury cost is four emeralds plus one per four water blocks, rounded up. A 640-change bridge spanning 24 water blocks costs 40 Materials and 10 Treasury. Infrastructure gifts replenish materials; General gifts can support treasury spending. Personal accounts and protected Endowment principal are never debited automatically.

A connected route reserves all its crossings atomically. Additional spans queue until a crew is free; sufficiently wide islands can have separate crossings. Each bridge is charged once after rechecking its saved plan. An exact design, before/after states, reservation, construction cursor, audit and handover progress live in a per-dimension saved ledger. Funding and territory claims use the economy journal.

Foundations precede decks, rails and lamps. Completion requires inspecting the whole bridge and its clear passage. Shared crossings retain their original ownership even when another road assists them. Turning forced development off or restarting resumes the same paid job; forced mode waives funding requirements only when first starting an uncharged job. Disabling bridges pauses planning and unfinished jobs, not use of intact completed crossings.

## Safety and performance

Surveys inspect four columns per pulse, followed by up to 24 state/protection snapshots. Work and audits inspect up to 32 pieces with a soft two-millisecond deadline and a separate block-write allowance. The global walkway cadence remains bounded rather than multiplying work by the number of bridge jobs. Individual Minecraft block updates and saves cannot be interrupted by that deadline.

Only loaded chunks are read or changed. Both shores, natural footing, water depth and onward access must be suitable. Ice, lava, falls, steep banks, occupied work cells, protected land and inventories are not bulldozed. Deep water can remain below open spans, but required piers need a natural riverbed within the configured depth. Underwater structures outside the actual bridge work and reserved opening are untouched. No chunk tickets, draining, automatic parallel crossings or player-structure replacement.

Reservations protect bridge footprints and navigation air from later mod development. Neighboring natural districts keep their own territory. Changed work or removed supports stop construction; completed bridges never regenerate automatically. Players may repair damage manually. The mod must not rebuild harvested bridge material indefinitely.

## Settings and limits

World settings: enabled by default; water length 2–128 (new-world default 96); pier depth 2–24 (default 12); active crews 1–4 (default 2). Saved designs are not reshaped when limits change. At most 512 saved bridge records exist per dimension. Existing route caps remain: 384-block endpoint Manhattan distance, 512 route cells and 8,192 search nodes. Long rivers can fit within those limits; oceans, cramped islands, deep lakes without pier footing and large suspension bridges remain outside the supported design.

Both handbook formats explain bridge behavior. `/emerald debug` includes village totals, active/queued/completed/altered counts, styles, payment, work progress and wait reasons, plus shared bridges in each road report. The newspaper does not expose these implementation details. Existing vanilla blocks are reused; no new item or recipe is added.
