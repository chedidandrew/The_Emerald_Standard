# Beta 58: approved regional architecture enters normal construction

Branch: `codex/biome-architecture-preview`. This is a review/test build, not a main-branch merge.

## Implementation

The 375 approved review sites are exported as immutable native block-state assets with a
SHA-256 manifest: 360 ordinary buildings and 15 Banks (three variants in each of five
styles). Gameplay loads those assets; it does not call the mutable preview generators.
Parcel dimensions include the entire ground-level yard, not just the house shell.

New ordinary projects use revision 12. Their saved village identity controls the biome
family, tier gates select tiny/small through landmark designs, and mature villages can
still choose smaller buildings. The existing economic roles and funding remain unchanged;
physical housing uses the actual authored bed count. Later visual stages add independent
planting; roofs, rooms, circulation, functional furniture and lighting are present at the
initial structural handover.

Banks use versions 13/14/15 for civic hall/kiosk/savings branch. Exchange Desk and Banker
anchors retain their existing contracts. Saved styles control assets and road materials.
The actual clear yard exit controls the road connection; the full forecourt is excluded
from street-goal discovery, and yard bounds protect landscaping from adjacent projects.
Floor holes, circulation obstructions, missing doors and missing desks disable operation
without changing account or village ownership. Safe dirt-path paving remains walkable.

All existing ordinary revisions 1–11 and Bank versions 2–12 keep their ordered plans.
Completed buildings are not replaced. Pending work resumes its saved geometry and cursor.
Construction phases were appended, not renumbered; no persistence schema was rewritten.
New below-grade yards opt into surveyed natural-soil excavation with loaded-chunk,
protection, block-entity, fluid and ownership checks. The change does not relax old plans
or public-road excavation rules.

The live villager check found a real threshold issue: a villager is too tall to cross a
two-block door with a rug immediately beside it. Only 222 doorway-adjacent carpet tiles
were set back across the 375 designs; other approved cells remain unchanged. The review
and release now both apply this narrow correction.

## Verification

The gates include all 375 native assets, exact release/review equality, construction
support at every visual stage, doors, ladder bearings, bed counts, full-parcel reservations,
all 360 tier/dialect selections, frozen old plans, and the existing economy/UI regressions.
Live fixtures use disposable server worlds and the actual reservation, construction,
save/reload, handover, villager-walking and Bank/road managers rather than gallery placement.

Completed checks:

- `scripts/run-common-tests.sh`: PASS, including all 360 selections across five dialects
  and tiers 1–5, history/repeat avoidance, persistence, economy, handbook and version parity.
- Fabric complete dedicated-server integration suite: PASS, all 473 yielding fixtures.
  This includes the real newly built savings branch, normal progressive construction,
  villager access, ownership/menu identity, damage detection, saved road queue, tree detour,
  courtyard exclusion, shared placement budget, lamps and no completed-road regeneration.
- Focused approved-architecture dedicated-server suites on **both Fabric and NeoForge**:
  PASS, 16 fixtures per loader. Five ordinary buildings (including a large Taiga Inn) and
  five Banks spanning all three variants construct through actual managers; partial saves
  reload; houses/Banks admit real villager walks; Bank floor damage disables service and
  restoration re-enables it. Two historical incomplete projects also recover safely.
- NeoForge final `build`: PASS. Native loader geometry tests, preview regression negatives,
  frozen historical plans, vanilla imports, recipe/UI checks and compilation included.
- Fabric final `build`: PASS. Exact approved-review equality across 375 sites, native
  construction/support gates, preview negatives, legacy snapshots, vanilla imports,
  packet/recipe/UI checks and compilation included.
- `scripts/verify-approved-architecture-jars.ps1 fabric` and `neoforge`: PASS. Both binaries
  match current canonical source inputs, contain exactly 375 assets, and every uncompressed
  asset matches its manifest SHA-256. Both contain the production adapters.
- `scripts/verify-built-jar.sh` on isolated beta-58 staging directories: PASS for both
  loaders, including metadata, resources, recipes, models, required classes and checksums.
  Older local build JARs were retained.
- `git diff --check`: PASS.

Canonical source identity:
`bcf47d29cb0e46b77be67fe37c71c11a35c36701d835743bfc6e9de27e9bd5a7`.

Binary SHA-256:

- Fabric: `354237073bf6f2d5ea3a4a8176b933ac0374e43aa354d92e079b280d26ea1008`
- NeoForge: `05a976d96181bd0c184d96addb749fef6e4beb2275b4ae2bd97b273600b1abaf`

Disposable smoke servers are deliberately stopped after their success marker. Windows
Gradle can then print a daemon-disappeared message during cleanup; the smoke script's
zero exit code and final integration marker, not that cleanup message, determine success.
The opt-in exhaustive fixtures intentionally exceed normal tick workloads and log
"can't keep up" warnings; they are not enabled in an ordinary player session.

These checks do not certify every third-party modpack or every natural terrain layout.
Protected or unsuitable sites can legitimately defer construction; that is not permission
to overwrite a player's land.

## Handbook and recipe accuracy review

Added the regional-architecture chapter to the long-form reader and compact lectern text.
It explains tier availability, saved styles, yards, prerequisites, old-plan preservation and
backup/rollback limitations. Bank access text now covers regional forecourts and yard exits.
Updated handbook regression coverage together with the chapter catalog.

Reviewed the existing server recipes and handbook recipe cards: Exchange Desk uses emerald,
two leather, book and three planks; Construction Fence uses four sticks, yellow dye and black
dye to make four; Handbook uses book and emerald; Newspaper uses paper and ink sac.
No recipes, registered blocks/items, creative-only spawn-egg policy or creative-tab contents
were added or changed by this architecture integration.

## Player test instructions

Use the matching loader's beta-58 binary JAR, not the sources JAR. Replace the previous TES
JAR in a separate test profile so only one TES version is installed. Keep the prior JAR and
back up/copy the world first. Leave **Village: vanilla buildings only** off, enable visible
construction, and Apply. New funded projects use these designs; previously approved or
completed structures retain theirs. Forced instant development is optional and permanently
changes the test world; it is not needed for normal economic construction.

Returning to an older JAR does not remove newly built structures or safely downgrade newly
approved plans. Restore the pre-test world backup for a clean rollback. No user profile or
world was modified to deliver this JAR.
