# One natural village, one growing territory

Beta.29 replaces automatic child-district charters with a connected territory for each
natural village. A fresh test world is recommended. The save format is **38**.

## Discovery and ownership

The server reads already-loaded structure starts/references tagged as villages.
A dimension-and-start-chunk identity stays stable as residents wander. Building a
mod house or Bank bell cannot found another natural village. Two nearby natural
structures keep distinct identities rather than merging by a distance threshold.
Unknown or unloaded structure starts wait for normal discovery; they are never force-loaded.

A new district starts with its original generated structure-piece footprints and rounded
center coverage, joined by connected 16-block parcels. Site search
tries a bounded rotating infill batch, followed by adjacent frontier candidates.
Reserving a valid building site adds its footprint and a short connecting chain;
failed or unloaded candidates claim nothing. Extensions can connect across at most
four parcels, rather than claiming an isolated outpost. The resulting outline follows
where sites fit, not a continually enlarged square.

Each parcel has one eligible natural-village owner: the nearest original village
center, with a stable UUID tie-break. This watershed clips boundaries even when a
neighbor is discovered later. Neither structures nor their terrain-clearance plans
can be newly reserved across that boundary. The map renders the actual saved parcel
outline, including gaps and irregular edges, without drawing internal parcel borders.
Farm and housing observations use the same exclusive ownership. This is a development
boundary, not a new permission system for player mining or building.

## Growth and Banks

New districts support up to 512 project records and 512 simulated residents. The old
six-housing-project cap is removed for them. Warehouses, Markets, Granaries and Guard
Posts can recur as population grows. Tier remains 0–5. Immigration, food, ordinary
project funding, upkeep, safe placement and the small work backlog still constrain
normal growth; these are ceilings, not population targets or guaranteed performance.

The village keeps its original identity, architecture character and biome palette.
Extending territory does not create another Bank. Existing Bank upgrade/replacement
and inventory-protection rules remain; a second independently generated Bank for the
same natural district is rejected.

**Automatic** permits safe connected extensions. **Approval required** consumes one
approval when a project needs new parcels, not for infill. **Paused** pauses development.
The owning single-player user/server operators control these settings.
Forced development bypasses the economic/mode gates, but not boundary, occupant,
property, loaded-chunk or foundation safety. It admits at most two unfinished
projects in a new district so one obstructed site does not block independent work.

## Construction near completion

The reported Cottage stopped at operation 944 of 948: a final flowerpot depended
on a missing decorative base. The earlier Inn stopped at a structural-support wait.
Native fixtures replay both frozen templates through the production materializer.
Required supports must still be built or repaired safely. Never-built optional
dressing may be omitted when its support is unavailable; it must not strand an
otherwise valid building or create a floating decoration. Previously owned blocks
follow the existing repair/no-drop rules, and completed buildings never regenerate loot.

Use Town's Progress report and `/emerald debug` for the actual latest blocker.
A screenshot percentage alone cannot distinguish occupants, unloaded work cells,
protected property, an incompatible blueprint or a missing support.

## Limits and old saves

No existing districts, blocks, inventories or resident identities are automatically
merged/deleted. Legacy multi-district saves stay readable and retain their old record
limits and identity links, but the runtime no longer creates artificial child charters.
This intentionally prioritizes clean new-world testing over a risky migration.

Search and physical work remain budgeted and loaded-only. A district stores at most
16,384 parcels; a map packet carries at most 384 markers, with pagination for larger
outlines/site lists. Village-wide surveys use a broad-phase bounding box but apply
parcel ownership to individual observations. Very large settlements still cost memory,
entity ticks and full-state checkpoint time; these changes do not promise constant cost.

See [forced development](FORCED_DEVELOPMENT.md),
[construction](PROGRESSIVE_CONSTRUCTION.md) and
[development protection](DEVELOPMENT_PROTECTION.md).
