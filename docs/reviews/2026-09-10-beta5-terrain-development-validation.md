# Beta.5 terrain development validation — 2026-09-10

Status: implemented and verified locally on the existing dirty beta.5 development branch.
No commit/push, game installation, or user world modification was performed in this pass.
The prior growth/food/loot/construction changes remain intact.

## Changes

- Shared new-lot survey for Banks/projects: natural vegetation, tall connected logs and branches;
  crafted-neighbor/storage evidence and registered placement vetoes remain protected.
- Median-grade cut/fill: four blocks cut plus four filled, at most eight blocks of surface spread;
  loaded-only ground checks, connected support, dry excavation and safe entrance constraints.
- Four-way orientation fallback for managed projects using the existing resumable position search.
  Banks retain their fixed north-facing blueprint and existing standard/recovery positions.
- Frozen project removal plans in format 22, persisted with reservations and copied/reloaded;
  matching block checks, natural state-change tolerance, top-down removal and completion save barrier.
- Bank clearing included in its existing frozen plan. Preparation and building share each site's
  one-operation-per-ten-ticks rate. Existing authored geometry and legacy support lists unchanged.

## Gates passed

- `scripts/run-common-tests.sh`: complete common regression suite including new cut/fill and
  clearance-persistence checks, plus existing economy, migration, scale, architecture and safety gates.
- Fabric `gradlew --no-daemon build`: complete build, all 52 authored masters and biome variants,
  frozen-revision compatibility, Bank geometry, reader/settings and packet checks.
- NeoForge `gradlew --no-daemon build`: complete build and equivalent loader verification.
- `scripts/smoke-server.sh fabric` and `neoforge`: final-code disposable real-server fixtures passed.
  Tall branched spruce, bushes, treehouse storage veto, six-block hillside, water/cliff rejection,
  unloaded frontier, same-position rotation fallback, real starter home/settler/restart and a full
  wooded-hillside Bank all passed. Newly placed storage and late protection vetoes stop clearing.
- Both `scripts/verify-built-jar.sh` gates passed, including the new preparation classes.
- `git diff --check` passed.

Known environmental Perflib/OSHI warnings remain. Exhaustive startup catalog validation causes
an intentional long fixture tick; these tests are not a production terrain-performance benchmark.
The smoke harness terminates only its own tagged processes after the integration success marker;
post-success Gradle exit/daemon messages reflect that teardown, not a failed integration test.

## Artifacts

| Loader | Binary | SHA-256 |
| --- | --- | --- |
| Fabric | `fabric/build/libs/the-emerald-standard-fabric-0.4.0-beta.5.jar` | `2e91e399742a85906ffaec95c198cee2d517f80035138b92ebdf32e9574fd860` |
| NeoForge | `neoforge/build/libs/the-emerald-standard-neoforge-0.4.0-beta.5.jar` | `600b015955c448112cc1ba40974b139a964b0d65fbcf98394a4c60df9a3ffc30` |

Source-jar checksums are recorded alongside binaries in each loader's `build/libs/SHA256SUMS`.

## Remaining manual review / limitations

Inspect a copied survival world before publishing. This pass did not take screenshots, visually
review every biome, certify third-party claim integrations, or install the jars in the user's game.
Terrain detection is contextual rather than perfect player-block ownership. Large cliffs, fluids,
unloaded evidence, oversized connected trees and recognizable construction can still reject a site.
Legacy reservations cannot gain excavation authority; completed structures are not rebuilt.
Back up before using format 22; older binaries must not open an upgraded economy save.

See `docs/TERRAIN_DEVELOPMENT.md` for exact behavior and boundaries.
