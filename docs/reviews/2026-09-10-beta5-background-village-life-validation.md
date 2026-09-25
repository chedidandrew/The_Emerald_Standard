# Beta.5 background village life validation — 2026-09-10

## Scope and handoff

This local follow-up implements the six approved background-life recommendations over the
existing beta.5 growth/food/terrain work. That earlier work was preserved. At the time of this
validation, the changes had not yet been committed or pushed. No build was installed into a
player instance and the user's gallery was not regenerated. Subsequent source publication is
recorded in Git history; the artifact hashes below identify the locally verified test binaries.
Behavior and limitations are documented in [Background village life](../BACKGROUND_VILLAGE_LIFE.md).

- Representative native villager walking regressions for doors, beds, workstations and streets.
- Context-sensitive upkeep advice in the existing Banker City expansion page; no additional
  construction-management interface or construction notifications.
- Stable staggered lot candidates and optional five-by-five squares, gardens and seating nooks
  along existing neighborhood connections. Started/reserved geometry is not rerolled.
- Two saved builder assignments per site, display-only carried materials, ground-supported
  scaffold/material displays and owner-only cleanup. No physical inventory logistics or drops.
- Throttled construction transition/progress/retry logging.
- Write-ahead construction-start evidence, retry/backoff and safe untouched-lot reconsideration.
  Started sites keep their frozen plan; player edits and protection vetoes are not demolished.

The placement pace remains one operation per ten server ticks per eligible site: two per second
at 20 TPS, independent of concurrent sites. This includes permanent terrain and pocket work.

## Fix discovered by actual walking

The initial Bank fixture could compute a route but the villager physically stuck in the doorway.
Carpet immediately behind the entrance raised its collision box into the two-block lintel.
New Bank structure revision 9 removes only the first three green runner carpets at local
`x=5..7, y=1, z=1`. Every other revision-8 cell is unchanged, verified in all five dialects.
Revision 8 is now explicitly frozen and remains the integrity recipe for saved revision-8 Banks.
Pending older Bank plans retain their saved version. No retrofit is applied to existing Banks.

## Verification results

All final checks passed:

1. `bash scripts/run-common-tests.sh`: economy, persistence, new background-policy regressions,
   untouched/started reservation recovery, geometry, UI layout, compatibility, loader parity,
   scale guards and existing regression suites.
2. Fabric `gradlew.bat --no-daemon build`: full authored catalog (52 masters across dialects,
   palettes, dressing and characters), Bank version contracts, packet codec and reader tests.
3. NeoForge `gradlew.bat --no-daemon build`: corresponding loader/runtime catalog and client
   packet/reader checks.
4. `bash scripts/smoke-server.sh fabric` and `... neoforge`: opt-in real dedicated-server
   integration fixtures in newly created disposable worlds.
5. Both `scripts/verify-built-jar.sh` package checks, including the new feature classes and
   existing metadata/resources, plus `git diff --check`.

Successful runtime logs, relative to the repository:

- `build/server-smoke/fabric-run.kd1i9b/logs/latest.log`
- `build/server-smoke/neoforge-run.v8YVFG/logs/latest.log`
- NeoForge catalog report: `neoforge/build/reports/tests/test/index.html`
- Fabric rendered contact reports: `fabric/build/reports/authored-landscape-contacts.txt` and
  `fabric/build/reports/authored-landscape-assemblies.txt`

Actual walking logs include a graded street in both directions; cottage doorway, bed, workstation
and return to street; and Bank doorway, Exchange Desk and return to street. These use native
villager AI/navigation/physics without teleporting between destinations. The Bank routes took
28/33/43 ticks with no retries. Some cottage/street routes needed one or two directed retries,
which are reported rather than hidden. The isolated fixture clock is restored afterward.

Activity checks exercise native entity serialization, retaining the same workers, no duplicate
props, the 0.35-scale material display, pickup/set-down phase changes, cleanup, preservation of
an unrelated display and player chest, all three pocket styles, and claim vetoes. Phase-transition
fixtures position workers at the pickup/drop-off points; they are not proof of autonomous
long-running delivery behavior. The separate walking regressions test real movement.

The final catalog reports zero candidate isolated decorative cells and zero snapshots with
unanchored shape components in its existing coverage. This is structural evidence, not a new
screenshot-based aesthetic review.

Known host/library warnings were present: Windows OSHI/Perflib system-information failures,
JOML Unsafe deprecation and NeoForge light-emission deprecation. The dedicated-server startup
self-tests also exceed a normal tick budget because they deliberately run extensive fixtures.
Both smoke harnesses reached their explicit integration PASS condition; these startup timings
are not gameplay performance measurements.

## Artifacts

Fabric: `fabric/build/libs/the-emerald-standard-fabric-0.4.0-beta.5.jar`

SHA-256: `31dbc6508021e80374e472caee20b2071865fc9a0bc03bfa86c32b145f72c074`

NeoForge: `neoforge/build/libs/the-emerald-standard-neoforge-0.4.0-beta.5.jar`

SHA-256: `f311c60cc2a014693b2aeb56d1863097e07c4f09e6bb9adb893f7096bf7d38a9`

Binary/source checksums are also in each loader's `build/libs/SHA256SUMS`.

## Remaining gameplay review and compatibility

Walking coverage is representative, not exhaustive for every building variant/terrain layout.
Client appearance of displays/advice, unsupervised villager routines, long-running recovery and
huge-city performance still warrant gameplay review. Scaffolding is visual, not climbable;
villager arms remain the native folded model. Worker availability never gates construction.
Permanent protected obstructions can leave a partly built project pending; retries do not
authorize destroying player work.

Economy format 24 stores `construction_started`. Missing old markers conservatively mean started.
Back up before installing; use a pre-upgrade backup to downgrade. Existing structures are not
rewritten, and new optional pockets only apply to newly prepared sites.
