# Fence-first construction validation — 2026-09-11

## Cause and change

Previously, the presentation scheduler placed up to 16 fence segments per site every
80 game ticks while both construction managers could independently place building cells.
Therefore structural work could overtake the fence batches. Some visible gaps were also
deliberate gates, terrain/claim exclusions, or vanilla tall plants that were not replaceable.

Normal Banks and village projects now wait on a persisted, dimension-local fence-ready
flag. Village terrain preparation finishes first; then fences are prepared in the existing
bounded batches; only then may the structural placement path run. Workers cannot start
working or arrive before readiness, but worker arrival/pathfinding never gates the building.
The diagnostic phase is preparing_fence, not a blocked/failed construction attempt.

Occupied cells, unloaded perimeter/neighbor columns and exhausted placement batches leave
preparation pending. Protected/unsuitable cells, gates and overlapping active footprints
are exclusions; they do not stall a site forever. The existing 16-sites-per-pass rotation
remains. There is no perimeter chunk forcing. Default structural speed remains two block
operations per second per site after setup.

Both halves of vanilla tall grass and large ferns can now be temporarily displaced and
saved in a fence receipt. Protection is checked on both cells. Cleanup restores the complete
pair only when supported and the upper cell is still empty. Player replacements remain.
The native ledger codec defaults missing readiness to false for older unfinished sites.
Completed preparation is not reopened to repair deliberately removed tape.

Forced debug development keeps its existing no-new-presentation behavior and bypasses the
gate. Banks also bypass it when visual progression is disabled. No economy-format change:
this build still uses format 30 from the commodity investment update.

## Validation

- 79 common regression PASS groups, including loader/version/wrapper parity.
- Both loaders: offline assemble, Banker packet codec and reader/settings tests passed.
- Fabric and NeoForge real dedicated-server smoke harnesses exited 0 with their full
  integration success markers.
- Real fence fixture: first batch is exactly 16 segments with no readiness or new crew;
  pause retains partial work; a living occupant leaves preparation pending; moving away
  allows completion; protected upper plant cells preserve both halves.
- Real two-block vegetation: complete tall-grass and large-fern state restoration after
  block-entity NBT reload; a replacement chest above a fence survives without an orphan plant.
- Current ledger readiness/crew receipts round-trip through native codecs; legacy entries
  without the field remain pending. Player-removed/replaced fences stay respected.
- A loaded-origin site straddling unloaded perimeter chunks remains unready and does not
  load its missing columns.
- Actual Bank placement changes zero planned cells before readiness, then builds a complete
  wooded/hillside Bank progressively; simultaneous sites, sleep allowance and storage remain safe.
- Actual normal district cottage waits after terrain preparation and before structural work,
  then completes with existing occupancy/settler/reload checks. Forced zero-resource construction
  still completes with its existing safety checks.
- Existing walking, gate access, animation, crew quota, departure, ownership, food, loot,
  inventory/commodity spending and full template-admission checks passed.
- Playable and source JAR verification for both loaders, SHA-256 output and git diff --check passed.

Earlier runs exposed interference between newly fenced, manually reserved safety fixtures.
The independent target cells were relocated away from preceding fence cells, while retaining
the tracked fixture chunk and all original occupancy assertions. No production occupancy
protection was weakened. Final runs passed on both loaders.

Logs: build/fence-first-common.log, build/fence-first-{fabric,neoforge}-build.log,
build/fence-first-{fabric,neoforge}-server.log; detailed server logs in build/server-smoke/.
The harness deliberately terminates its disposable server after the full PASS markers:
the later Gradle exit-143/daemon-disappeared messages reflect that cleanup, not test failure.
Windows OSHI/Perflib warnings and expensive opt-in startup-test lag remain; this is not a
claim of error-free Windows logs or a normal-gameplay performance benchmark.

No new client rendering code or resources were changed and no fresh client visual capture
was taken. These are real server/block/entity tests, not a replay of the user's screenshot.

## Test artifacts

Back up the entire world. Install only the matching loader's playable JAR; use matching
client/server builds. These beta.5 candidates replace earlier candidates with the same names.

- Fabric: fabric/build/libs/the-emerald-standard-fabric-0.4.0-beta.5.jar
  SHA-256: 9cddf8c123b0558b5751b2e0c51cf1163e75648817fb5b78b49b5316babb23ee
- NeoForge: neoforge/build/libs/the-emerald-standard-neoforge-0.4.0-beta.5.jar
  SHA-256: b9459626d93247b0282359c1d2e58a577db247f9fd0242b167818ffafedbb6ec

No installed mod, user world, external repository, commit or release was changed.
