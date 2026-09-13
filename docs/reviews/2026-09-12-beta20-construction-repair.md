# beta.20 — breakable construction, no drops and automatic finishing repairs

Date: 2026-09-12. Unreleased local development candidate; not installed in the user's Modrinth profile.

## Implemented behavior

Builder-supplied unfinished blocks can be mined or destroyed by ordinary explosions. Their
block loot and mining XP are suppressed until physical handover. Exact position/block receipts,
not a surrounding protection box, determine ownership. Player placement into unfinished reserved
blueprint cells is rejected before consuming the item. Nearby property remains outside this rule.

Ordinary projects continue independent safe work within the current structural phase when an
occupant blocks one cell. Missing supplied blocks are repaired during finishing, or earlier when
needed as supports. No player materials or personal bank funds are charged. The final required
occupied cells still delay completion; players and animals are not built through or deprived of
footing. No forced player/pet teleportation or deletion was introduced.

New storage is empty and unavailable before handover. Ordinary menus, hoppers, container-slot
contracts and neighboring double-chest access are guarded. Pistons cannot export supplied
blocks or import foreign blocks into reservations; falling construction blocks cannot land as
harvestable material; Endermen cannot export them through their ordinary pickup goal.

Starting-loot eligibility is saved once before handover. Chunks are flushed, then the economy
must durably accept completion before loot is attached. Replacement does not grant new loot.
Completed project receipts retain only a small handed-over-prefix marker so subsequent
append-only upgrades cannot gain regeneration authority over an already completed house.

## Preservation and migration limits

- Unrelated blocks, inventories, fluids and external land protection remain obstructions, not
  permission to overwrite property. Clearing a foreign obstruction can still require player action.
- Administrator/other-mod supplied inventory contents are preserved, even when that requires
  ordinary removal drops instead of blanket no-drop behavior. They are never replenished.
- Known active legacy prefixes may adopt matching non-container cells, excluding known player
  build clusters. Existing storage receives no new initial loot.
- Old Manual Repair records are deliberately not auto-enrolled. That same flag also represented
  completed player remodels, so blind repair would regenerate harvested completed buildings.
  Inspect the affected project's debug evidence before changing its authority.
- Completed buildings keep normal editing and drops and do not automatically regenerate.
- Economy format remains 37. Per-dimension construction_ownership SavedData is added. Keep full
  world backups and matching client/server builds; do not downgrade during active construction.
- Minecraft chunk, entity, SavedData and economy files are not one power-loss-atomic database.
  Interrupted loot handover may conservatively leave an empty chest; it must not replay a grant.

## Validation

All 89 common regression entrypoints and wrapper/version checks passed. Regression coverage
includes unfinished repair queue bounds, refusal for completed/manual records, persistence,
cosmetic repair ownership and both handbook forms.

Fabric and NeoForge dedicated-server integration suites passed against disposable smoke worlds.
ConstructionOwnershipSelfTest exercises real native mining/Silk Touch loot, block destruction,
explosion callbacks, chest destruction and empty replacement, container/hopper lockout, piston
import/export, falling-block export, mining XP, codec reload, delayed one-shot loot and return
to normal completed drops. The ordinary and forced cottage fixtures break a supplied block
behind the work cursor and verify automatic repair and completion. Live construction tests
cover player/villager/animal occupancy, footing, safe neighboring work and resumption.

Both full loader builds passed, including authored-structure admission, packet codecs and reader
settings checks. Packaged source fingerprints match current source and each other.

Both handbook forms were updated: 68 guided sections across 16 chapters and 61 compact pages.
Native Fabric client checks passed for compact-page real-font limits, chapter ends, search,
80/120 percent reader text and GUI scales 2/4. The Building projects scale-2 120-percent
screenshot was visually inspected. The compact safety page was shortened after its first
real-font test exceeded the 14-line limit.

An intermediate NeoForge run intermittently failed the existing Creative dispenser assertion
after the construction suite passed. Additional entity diagnostics were retained; subsequent
complete native runs passed. No production spawn-egg behavior was changed for that assertion.
Development-client Realms authentication and upstream/deprecation warnings remain visible.
The server-smoke harness terminates its tagged process after the explicit integration success
marker; trailing daemon shutdown messages are not interpreted as test failures.

This is native-loader coverage, not exhaustive multiplayer or arbitrary-third-party-modpack
certification. Mods that bypass vanilla block/container APIs need separate compatibility testing.
No live game installation, user save, or user inventory was changed.

## Candidate identity

Version: 0.4.0-beta.20

Source SHA-256:
143a16576f78337f1cb30c1c8d5a269f1914f8fbf0754504c35470313d5fef26

Fabric JAR SHA-256:
124AD46951CDBF2D25BCFF6A80D0BDAF9435256032E79A1BFA5EDEE6467BF19C

NeoForge JAR SHA-256:
53297670415734A10C5FC2143A8AC5F2176253D3911F7BF3C634CF10195AB9E9

Artifacts:
- fabric/build/libs/the-emerald-standard-fabric-0.4.0-beta.20.jar
- neoforge/build/libs/the-emerald-standard-neoforge-0.4.0-beta.20.jar
