# Construction fences and visiting builders: validation

Date: 2026-09-10. Local, uncommitted/unpublished 0.4.0-beta.5 candidate.
Baseline commit: `7a512c973ad498f2f9d7cbb73278710d01bce62f`.
Includes the earlier investment/dashboard changes; economy format remains 27.
No user world or manually copied test save was edited.

## Implementation

See [construction crews](../CONSTRUCTION_CREWS.md) for mechanics and limitations.
Both loaders register the connected caution block/item, restoration block entity and builder mob.
The client uses a custom articulated worker model with native Minecraft material textures.
Dedicated visitors replace earlier resident recruitment and display-only delivery/scaffold props.
Authored building geometry, loot, population and two-operations-per-second construction are unchanged.

## Passing checks

- Full common regression suite, including the earlier investment/migration/UI coverage.
- Fabric and NeoForge full Gradle `build`, including the authored-catalog, Bank, reader and menu
  packet checks. Final binary/source jars refreshed with `assemble` after the final connected-rail
  occupancy guard; both updated loaders then passed their native-server checks.
- Both dedicated-server smoke harnesses: complete Banker integration and construction activity
  checks passed. Real custom blocks and fence connections; protection veto; flower restoration;
  player chest replacement/manual fence break preserved; fence NBT and ledger codec round trips;
  builder NBT assignment round trip; size scaling; eight cardinal/diagonal actual walking routes;
  paused worker evacuation; camera cone/near-player rejection; departure/cleanup; retained pockets.
- Both clients launched and restarted in disposable profiles. Production block/entity renderer
  captures and hammer-arm transform assertions passed, as did earlier handbook/dashboard checks.
  Combined first/second logs passed the strict client validator for each loader. Mod Menu absent.
- Strict client-log negative fixtures passed, including rejection of missing crew-render evidence.
- Both `verify-built-jar.sh` checks passed, including newly required crew classes, restoration
  receipt, caution blockstate/models/item and native fence tag. Checksums regenerated.
- `git diff --check` passed.

The dedicated-server harness intentionally terminates its uniquely marked processes after
success. Later Gradle daemon/run-task termination messages are cleanup, not failed assertions.
Windows OSHI system-information warnings were present in server logs and did not prevent startup.

## Fixes found during this pass

- Initialize arrival candidates as grounded before requesting a navigation path.
- Set explicit cardinal fence connections when placing blocks programmatically.
- Check actual connected collision shapes, including neighboring rail additions, before placement.
- Finish the currently placeable perimeter before admitting new visitors; replan routes as the
  building changes and follow waypoint centers to avoid fence-end corner clipping.
- Evacuate waiting workers from the footprint instead of freezing an occupant in the next wall cell.
- Correct fixture entity-age advancement to match ServerLevel's tick wrapper. Refresh the food
  fixture's animal baseline after its additional chunk loads; no production food rules changed.
- Avoid transparent folded-arm texture regions on articulated limbs. Render costume/arms with
  opaque native materials; retain the recognizable villager face.

## Evidence locations (local ignored build output)

- `build/server-smoke/fabric.log`, `build/server-smoke/neoforge.log`
- `build/construction-common-tests.log`
- `build/construction-fabric-build.log`, `build/construction-neoforge-build.log`
- `build/construction-fabric-package.log`, `build/construction-neoforge-package.log`
- `build/construction-{fabric,neoforge}-client-{1,2}.log`
- `build/construction-{fabric,neoforge}-jar-verification.log`
- `build/client-smoke/construction-final-fabric-20260910/screenshots/tes-reader-ci/`
- `build/client-smoke/construction-final-neoforge-20260910/screenshots/tes-reader-ci/`

The `construction-crew-frame--1.png` capture shows the fence; frames 0 and 3 show the production
walking/hammering models at different animation times. These are renderer previews, not screenshots
of a live player's construction site. Server fixtures exercise the actual world behavior.

## Artifact SHA-256

| Artifact | SHA-256 |
| --- | --- |
| Fabric binary | `b8a9ae89b79434a696f4fa48ae8e1724c2b0501071e4943958413ca687af4ed0` |
| Fabric sources | `f48f86924ebf883666a26234df69c19ae2a5ebd7d29ca312af8ba81f11dfbed9` |
| NeoForge binary | `06dfa2805438dab383494e576c464c59ae13c4b70c891b707204a9b69cb132d5` |
| NeoForge sources | `0cfc3ff5873ec575e7ac6948076387e89e2c4d18ef5a6ba830accd1a0f233b53` |

Jars live in each loader's `build/libs/`; install only the matching binary, not the sources jar.
Back up the entire world and use matching client/server versions. The earlier investment-only
validation report describes an earlier local artifact; these hashes supersede its test binaries.

## Remaining gameplay boundaries

Arrival concealment is a server-side approximation, not exact third-person/FOV/freecam visibility.
Unloaded cleanup waits for chunks to return. Player-broken/replaced barriers intentionally do not
restore vegetation over those edits. Ordinary native save/load round trips are covered, not an
atomic multi-file crash guarantee. Crews can wait/retire when routing fails without stalling builds;
arbitrary rough/modded terrain and prolonged huge-city performance still warrant gameplay testing.
