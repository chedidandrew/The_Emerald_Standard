# Beta 63: natural hitching-rail supports

## Reference and scope

The supplied before/after screenshots show two vertical metal hangers outside the ends of a wooden hitching rail. The desired version extends each overhead bottom timber slab one cell outward so the tops of the metal hangers meet the timber. The matching historical motif is AuthoredVillageStructures.addHitchingRailZ, used by older authored Inn yards. The user did not know its gallery number. The actual historical catalog test finds 25 matching rails across all five biome palettes, including preservation of authored reserved air. The current approved 375 designs do not contain this exact motif and are not remodeled.

## Implementation and compatibility

HitchingRailFinishing recognizes the exact paired posts, rail, hangers, chains, original slab caps and ground bearings. It adds only the two slab extensions, using the original cap state. Saved construction plans, hashes, cursors, funding, interiors and loot remain unchanged. This is a separate one-shot additive presentation finish, not a template revision or general repair of arbitrary chains and bars.

Older managed Blueprint Inns through revision 11 receive the finish at handover, or during staggered nearby completed-project inspections in normal development mode. Fresh opt-in legacy gallery renders include the same caps; gallery content revision is 18. Existing static galleries are not automatically rewritten and must be regenerated to show the update. The existing forced-mode completed-project audit remains intentionally disabled.

Every pair requires loaded witness, target and neighboring columns; no chunk is force-loaded. Existing blocks, recorded player placements, claims/exclusions, construction reservations, inventories, fluids, world bounds and living occupants are respected. Authored explicit air targets remain reserved. Damaged or obstructed pairs are left untouched and recorded as skipped; temporary occupancy or unloaded chunks defer without consuming the receipt. A persistent per-dimension receipt is recorded before writes, preventing removed caps from regenerating. Receipts serialize through the SavedData codec. Existing unrecorded same-type historical edits remain inherently ambiguous; the exact intact-motif and placement guards limit that ambiguity.

## Validation

- Full loader-neutral common regression suite passed, including production-hook and handbook coverage.
- Both Fabric and NeoForge dedicated-server connection/construction/safety smoke suites passed with the final self-test source. Retained logs: build/server-smoke/beta63-fabric-connections.log and beta63-neoforge-connections.log.
- Each live suite passed 80 exact motif cases: five biome palettes, two sides, two mirror settings and four rotations. It verifies unchanged frozen plans, native contact grounding of the new caps and both metal hanger stacks, exact paired writes, protection/obstruction and damaged-rail negatives, player occupancy with safe retry, unloaded-chunk deferral, codec round trips and persistent no-regeneration. The production historical finishing wrapper is exercised.
- The actual historical catalog scan detects 25 rails across all five biome palettes without using fabricated catalog identities.
- Full Fabric build passed in 7m 10s and full NeoForge build passed in 5m 33s. Architecture, supported attachments, roof/interior, vanilla fidelity, packet, model and reader-setting checks passed. Completed-gallery attachment expectations passed for 3,221 entries including the new caps.
- After the final self-test-only catalog addition, both binary/source JAR pairs were regenerated. Their current canonical source identities and all 1,875 exact native architecture payload hashes passed verification. No archived/current approved structure payload was changed by this fix.
- Packaged metadata, required production resources, source JAR contents and checksums passed for both loaders. git diff --check passed.

No in-game visual UI inspection is claimed. Native server fixtures verify geometry/contact and safety. Existing Windows OSHI/Perflib and upstream deprecation warnings do not represent test failures. The smoke harness intentionally terminates its marked test server after the integration pass. Initial pre-fix smoke failures remain retained for diagnosis.

## Handbook and recipes

The long projects chapter documents the historical one-shot timber extensions, intact/clear/loaded/protected checks, normal-mode nearby inspections and non-regeneration. The compact planning/building page adds the one-shot hitch-cap note; both forms have regression coverage. Existing chapter and lectern routes already cover those pages. Recipe review confirms this uses existing vanilla wood slabs and introduces no custom item/block or recipe; unrelated recipes and loot guidance are unchanged.

## Deliverables

Binary and source JARs with SHA256SUMS are staged under build/release-beta63/fabric and build/release-beta63/neoforge. No Minecraft profile, existing world or user screenshot was modified. No commit, push, merge or pull request was made.

- Canonical source SHA-256: 2fbd2e501158882e0765b8451c52a2b44d3c30e682f7a617ae265eba638e17b1.
- Fabric binary SHA-256: d2badf8dfe91e54acbfd0389d79e1d3c93048c2a0176cc12fdda54c42c875b56.
- NeoForge binary SHA-256: 58c309e5487879bd2c2fb9aac13c3e83ccce6fb65c0b3558614c7d0c857bf1c9.
