# Construction loot, food census and occupancy validation

Date: 2026-09-10. Minecraft 26.2, Java 25. Unreleased 0.4.0-beta.5, economy format 26.
Base commit: `f8b8435dcbb1acea5f3df7dd2755adc1656a5528`.
This is local working-tree work on top of the preceding development-protection pass. No commit,
GitHub push, release, client launch or installation into an existing user world was performed.
Authored buildings, ordinary loot contents and the independent two-operations-per-second site
cadence are unchanged.

## Behavior

- Pending Banks store handled-container positions in their durable plan. A receipt is saved
  synchronously before attaching new loot. Existing matching containers are adopted without
  changing contents. Broken recorded storage is not recreated; emptied storage is not refilled;
  player replacements retain their contents. A stale caller plan cannot reopen a receipt.
- Older serialized Bank plans without receipts retain their exact geometry, but finish unplaced
  storage empty because previous loot provenance is unknown. This suppression survives reload.
  Completed Banks and existing container inventories are not retroactively modified.
- The earlier per-chunk food observation fix remains: only completely surveyed loaded chunks
  replace counts. Unloaded chunks are unknown, not newly empty. Real loaded crop/livestock losses
  still reduce their contribution. A new real-server regression verifies this through a checkpoint
  and restart without loading the unknown chunk.
- Live occupancy guards use actual collision shapes, including slim/slab shapes and full-block
  footing. They cover players, villagers and animals. Direct intersections, fluid placement into
  a body and removal/lowering of occupied support defer work. Safe neighboring cells, same-height
  floor replacement, dropped items and display-only decorations remain allowed.
- Ordinary projects retain their exact cursor; Banks may advance another safe pending cell.
  Occupancy waits do not increase materialization failures or founding-home relocation evidence.
  Terrain preparation, roads, entrance approaches and managed connection updates use the guard.
  Temporary waits appear in background diagnostics and shared worker status, not a new UI.

## Executed checks

All final checks passed:

1. `bash scripts/run-common-tests.sh`: entire core/resource/wiring suite, including persistent
   Bank receipts, duplicate rejection, stale-plan claims, legacy payload migration, invalid receipt
   coordinates and simulated receipt-save failure with in-memory rollback. Existing food-cache,
   journal, protection, recovery and scaling regressions passed too.
2. Fabric `gradlew --no-daemon build`: PASS, 2m 15s. Full authored catalog, frozen revision,
   Bank, packet and reader checks.
3. NeoForge `gradlew --no-daemon build`: PASS, 2m 14s. Loader tests, catalog, packet and reader checks.
4. Fabric disposable dedicated-server smoke: PASS. `build/server-smoke/fabric-run.jadqCd`,
   integration completion 18:38:04 local time. Log: `build/server-smoke/fabric.log`.
5. NeoForge disposable dedicated-server smoke: PASS. `build/server-smoke/neoforge-run.I4Mg5k`,
   integration completion 18:38:05 local time. Log: `build/server-smoke/neoforge.log`.
6. Both packaged-JAR verification scripts passed, including required occupancy/work-status classes.
7. `git diff --check`: PASS.

The runtime suite exercises actual generated loot, empty/broken/replaced chests, restart with a
stale Bank plan, empty legacy barrels, villagers/cows/player occupancy, fluid/body intersection,
occupied floor removal and path lowering, same-height floor replacement, safe neighboring Bank
work and resumption. The ordinary founding-home fixture also tests occupied terrain and authored
cells through the production materializer, then completes the home and admits a real settler.
Real-block food tests cover unknown chunks, changed loaded counts and persisted retained samples.

During development, the new footing regression caught the original tolerance allowing a 1/16-block
path lowering. Comparing old and new support heights fixed this. Review also corrected terrain
preparation's caller so an entity wait cannot fall through to ordinary failure/backoff. Early food
fixture runs were corrected to use a current clock and perform a checkpoint before testing restart;
these were test setup errors, not evidence of an unloaded-food regression.

The Windows host reports pre-existing OSHI/Perflib performance-counter warnings. The synchronous,
exhaustive startup fixture also causes a startup tick-delay warning; it is not a gameplay TPS
benchmark. The smoke harness stops only its own tagged disposable processes after PASS, so the
trailing Gradle process-termination output is expected. No tagged smoke Java processes remained.

## Runtime JARs

```text
fabric/build/libs/the-emerald-standard-fabric-0.4.0-beta.5.jar
SHA-256 f148ff0155d1afccc61ea472bec3a5ea12751b8475258fa89c29ce3292398908

neoforge/build/libs/the-emerald-standard-neoforge-0.4.0-beta.5.jar
SHA-256 8fd7ba468b1a4cd4e06e60534e633f1693a857258801dd94c8e6aa3cb68259b2
```

## Limits and upgrade safety

Back up the entire world before loading format 26. Restore that backup to downgrade; include
the economy and development-land journals, not just the primary properties file.

Chunk files and the economy are separate durability boundaries. An interrupted loot claim may
leave empty or missing storage rather than risk a duplicate grant. A removed recorded container
must be restored manually if required to finish that Bank. This does not protect against explicitly
restoring older economy backups or external edits that remove receipts.

Occupancy checks prevent direct collision or loss of footing at the mod's write, not every possible
enclosure/escape-route problem in a partly built room. Native subsequent neighbor updates, other
mods' movement/physics and long-running multiplayer/huge-city behavior are not certified. Food
caches remain last-known observations, not a live census of unloaded territory; ownership/coverage
changes converge as relevant chunks are surveyed.
