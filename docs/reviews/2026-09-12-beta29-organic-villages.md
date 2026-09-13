# Beta.29: one natural village, one territory

## Requested behavior and implementation

New natural village structures receive a stable dimension/start-chunk identity. Runtime
child charters are removed. Loaded village-tagged structure metadata supplies original
house/street footprints; rounded starter coverage and checked connecting parcels keep
the initial territory connected. Later reservations try rotating infill and frontier
batches and claim their connected parcels only with durable project intent.

The nearer original natural-village center owns each 16-block parcel (UUID tie-break).
Map outlines and new placement permission are clipped against every known same-dimension
natural neighbor. Housing and food surveys use those same exclusive parcels. A center
need not remain loaded after the village's style is saved; exact work cells still must
be loaded. New candidate exclusions query around the candidate, not only the old center.

New records allow 512 projects and 512 simulated residents, removing the six-home cap.
Population-scaled Granaries, Warehouses, Markets and Guard Posts can recur. Both their
need selection and the old unique-project rejection gate were updated. Initialization,
population observation, immigration, state validation and the counterfactual market
shadow use the new population bound. One generated Bank per natural district is
enforced; a known natural structure cannot be merged by phantom-Bank-bell cleanup.

Forced mode admits only two unfinished projects and actually rotates their work grants.
No player account charge, synthetic population, chunk forcing, forced unsafe handover,
container replacement or regeneration of completed loot is introduced.

## Construction evidence

Read-only inspection of the supplied test save found the reported Cottage at operation
944/948. The frozen template's next cell is a flowerpot above an optional double-slab
base. The region file contains air at both 1885,67,1216 and 1885,68,1216; the barrel
underneath remains. Existing beta.25 support recovery already handles this category.
The new exact fixture confirms it: omit unsupported never-owned dressing, verify the
required building, and do not invent a floating pot. It additionally puts the original
center 2,048 blocks away in an unloaded chunk while the Cottage site remains loaded.
The original 3603/4873 Inn replay remains in the native suite.

These cases do not prove that every possible 99% wait is the same bug. Occupants,
unloaded work cells, protected player property and incompatible blueprints still cause
legitimate waits. Debug mode must not silently bypass them.

## Save policy and remaining limits

Economy format 38 persists parcel ownership and the natural-village flag. Pre-update
districts retain their identities, inventories, blocks and legacy caps; no automatic
merger or physical demolition was attempted. Fresh test worlds are recommended.
Nearby villages not yet discovered cannot be anticipated; discovery clips eligible
territory, but never retroactively deletes existing buildings.

16,384 parcels per district, at most 256 site candidates per sweep and 384 map markers
per page are defensive bounds, not performance guarantees. Whole-state saves and
large numbers of entities still cost time and memory. Validation did not reproduce an
extended multi-player modpack stress run or claim constant-cost unlimited growth.

## Handbook review

Updated guided village, growth, territory, district-map and glossary explanations;
updated the compact Growing a Village page. Covered ownership, original footprints,
the higher caps, repeated services, loaded-only work, cached terrain, optional decoration
safety and the fresh-world recommendation. No recipes changed. Both native reader
formats retain real-font wrapping checks. Relevant development/debug documentation
and the changelog were updated.

## Validation

- Common territory regression covers nearby natural IDs, negative coordinates,
  exclusive ownership, original distant footprints, connected growth, rejected foreign
  and distant reservations, read-only rejection, bounded candidates, map codec/paging,
  ordinary and forced growth beyond old caps, two-site backlog, single-Bank enforcement,
  persistence and greater-than-64-resident market-shadow reload.
- Native structure metadata fixture checks distinct nearby starts, stable ties, original
  footprint discovery and no forced loading at an unknown distant position.
- Native Cottage and Inn replays exercise production support recovery.
- Actual Minecraft district-map fixtures render exclusive outlines and contained sites
  at GUI scales 2 and 4, with navigation/hover and compact/guided handbook checks.
- One Fabric suite run failed the unrelated existing Creative dispenser-egg fixture
  with an empty entity query. An unchanged-source rerun passed the entire suite.
  That intermittent test failure was not used as evidence of a village regression.
## Final results

- All 91 common regression entrypoints passed: build/beta29-common.log.
- Fabric full build passed: build/beta29-fabric-build.log.
- Fabric native server suite passed: build/beta29-fabric-server-retry.log; the subsequent
  shared population-initialization/shadow-cap hardening passed the common suite and
  the final NeoForge native suite.
- Final NeoForge native server suite passed: build/beta29-neoforge-server-final.log.
- Actual map UI and handbook checks passed on both loaders:
  build/beta29-fabric-client-final.log and build/beta29-neoforge-client.log.
  Screenshots were visually inspected under build/beta29-fabric-map-final and
  build/beta29-neoforge-map, screenshots/tes-reader-ci.
- Packaged versions and current-source cross-loader parity passed.

Shared source SHA-256:
`9c0883bf969f7bba692b40902e5b9c5ac6177219066aec5a93c2608d64352c49`

Fabric binary SHA-256:
`6c6d44b24ffcaffa469aee4f25be3634d03ec47a35f1bce20a47ded4c9453dfb`

NeoForge binary SHA-256:
`ebf74fc110797d8cd8eddbb86f8ffff69904cb4cadcc071d0987c654ef57e22b`

Final NeoForge full build passed in 2m 29s: build/beta29-neoforge-build-final.log.
Both final binary/source jars are present, with matching current-source fingerprints.

No installed mod jar or live player world was modified.
