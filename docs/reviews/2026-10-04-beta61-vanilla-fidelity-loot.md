# Beta 61: native blueprint fidelity, storage and entrances

## Sources and scope

The supplied `minecraft_village_blueprints.zip` contains 181 isometric reference PNGs across the five village styles, not machine-readable layer grids. Selected plains/taiga references were inspected. The stronger layer-level source is the actual Minecraft 26.2 packaged structure NBT, already pinned by SHA-256 in TES's 166-entry admission manifest. Wiki blueprint retrieval was attempted but blocked by robots; no unverified web layer reconstruction was substituted.

An independent NBT oracle now compares all 166 eligible templates (1,153 layers, 83,682 cells) against the resolved plans and production placements in all four rotations, including block states, interior air, doors, beds, furniture, windows, roofs, crops and exterior details. There are zero mismatches after documented normalization. These are imported native shapes, not TES-authored approximations. The supplied PNG archive is not a promise that every accessory in all 181 references is a separately selectable building.

Allowed differences: jigsaws resolve to their native final states; structure markers are omitted; all air variants become air; decorative leaves remain persistent; buried dirt paths become the stable dirt result. Entrance centering/rotation and floor-coordinate normalization preserve relative geometry. TES supplies surveyed shallow foundations and essential terrain access separately. Optional connected trees, inhabitants/animals, abandoned variants and independent roads/accessories are not imported; the unsupported plains well with a separately connected shaft remains excluded. These boundaries are documented in the handbook, rather than claiming full jigsaw-worldgen equivalence.

The audit found a genuine visible fidelity difference: CropBlock ages were reset to zero. New native farm plans now retain every authored crop age. Old saved plans remain frozen; they are not rerolled or rewritten.

## Empty storage diagnosis and policy

The original importer deliberately discarded source container NBT, and the placement ownership claim explicitly denied loot entitlement to all imported plans. That explains routine empty imported chests. The source catalog has 46 chest placements with native loot-table NBT and 16 empty barrel placements. Native inventories, entities or arbitrary block-entity payloads are still not blindly copied.

Newly placed imported chests/barrels now receive fresh-storage ownership receipts and the same protected one-shot handover mechanism as TES buildings. Profession identity chooses household supplies, farm seeds, workshop materials, food, leather/string, arrows or paper/books from existing balanced TES tables. These are intentionally TES supplies, not exact vanilla treasure rewards: each table has 2–4 nonempty modest rolls, item counts 1–8, no enchanted/high-value reward windfall. Construction remains funded, storage is locked until physical completion, and repair is not a loot source. Existing supplied/deferred inventories are never overwritten; emptied storage does not reroll after reload. Already completed old empty containers are not backfilled because they cannot reliably be distinguished from player-looted storage.

The existing durable handover mechanism prioritizes preventing duplicate grants: it consumes entitlement before granting after economic completion. An interruption in that existing crash window can lose a grant rather than duplicate one. This change fixes the systematic empty-container policy; it does not claim an absolute crash-proof never-empty guarantee or rewrite the shared transaction protocol.

## Native entrances

TES's separate terrain survey began the outside landing at indoor floor height, even when native porch stairs already met street grade. That added a block-high plinth and another step outside the complete porch. The survey now first joins at street grade, retains native porch blocks, and preserves the one-block doorstep of native desert houses. Real terrain drops still receive bounded supported stairs; no protection, storage, loaded-column or foundation-depth checks are waived.

The new five-style flat-site regression first exposed desert one-block doorsteps, then Taiga path-to-stair joins: dirt paths are 1/16 below full blocks while a bottom stair's bounding box includes its upper tread. A small stair-specific tolerance recognizes that normal join without allowing a two-block cliff. Both issues were fixed before the successful full runs. Initial failed logs are retained under `build/server-smoke/beta61-*-flat-entry-failed.log` and `beta61-*-native-tread-failed.log`.

Existing frozen terrain plans and completed player-world blocks are not demolished automatically. The new survey affects newly planned or newly surveyed access, not arbitrary old porch/player edits. Authored TES design assets are unchanged by beta 61.

## Validation and handbook review

The complete common regression suite passed, including persistence, economy, construction/ownership policy, vanilla-only catalog selection, tier benefits, handbook/recipe coverage, loader version parity and wrapper checks. Both disposable Fabric/NeoForge native construction suites passed: all eligible templates, native support survival, physical bed approaches, interrupted saved plans, four rotations of raised small homes in each style, additive legacy terrain repair and protected inventory rejection. The terrain fixture checks 16 synthetic rotated recessed/slope plans, 20 real raised and 20 real flat five-style plans. Loot checks validate 10 tables over 1,000 seeded rolls plus chest/barrel deferred NBT, first-access fill, reload stability, no refill after emptying, and preservation of supplied contents. Every new imported storage cell is also checked at production handover.

Successful smoke logs: `build/server-smoke/beta61-fabric-vanilla.log` and `beta61-neoforge-vanilla.log`. Test fixtures synchronously construct the entire catalog in a disposable tick, producing an expected test-only long-tick warning; this is not a production tick benchmark. No user save/profile was written.

Both long-form project guidance and compact vanilla-building pages now explain preserved native crop/porch geometry, modest one-shot storage supplies, no refill/repair rewards, terrain-only extra steps, frozen old approvals and catalog exclusions. Handbook regression assertions were updated. Existing recipes, affordability and creative discovery were reviewed through the regression suite; no recipe changed.

Both complete loader builds succeeded (Fabric 5m55s, NeoForge 4m53s). NeoForge's native NBT test independently reports the same 166 templates, 1,153 layers, 83,682 cells and four rotations with zero failures. Fabric's additional terrain/protection/ownership/debug smoke passed. Existing native OS performance-counter warnings are environmental; the smoke harness terminates only its own disposable processes after the pass marker, so a subsequent Gradle termination footer is not a failed fixture.

Packaged checks passed for both loaders: metadata/version/classes, shared handbook, current source fingerprint, and every SHA-256-pinned authored architecture asset (1,875 archived/current/fitted assets). Those authored assets are unchanged by beta 61. Binary and sources JARs are staged under `build/release-beta61/{fabric,neoforge}` with checked SHA256SUMS.

Version: `0.4.0-beta.61`.

Canonical source SHA-256: `3c9301bdeac8a8542d0bee5a4735011d7936c73cb01a0ecb320c8cd73d1667be`.

Fabric binary SHA-256: `ef36bb3d99ffd2b182fdb52056c63a4f2cca40728ba869617a49668668f8e8a9`.

NeoForge binary SHA-256: `768b6fb10574e5fc442045a6a36da351cae00f5a917f3e3291a3cabec65ff98f`.

Changes remain on `codex/biome-architecture-preview`, preserving the beta-60 work. No commit, main merge, push, PR, mod-profile installation or player-save alteration was performed. Test a backup copy of the world, use the matching loader, and replace the previous TES JAR rather than loading both versions together.
