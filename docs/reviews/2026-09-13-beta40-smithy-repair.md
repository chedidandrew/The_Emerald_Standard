# beta.40 — Smithy construction repair loop

## Evidence and root cause

The user's two beta.33 / 760be6e8e613 captures (054226-C0E9C035 and
061407-FDA7FC4D) both contain project 7, `smithy_corner_04`, at
1,180 / 1,975 operations. The repair observation repeatedly names
(-308, -60, 237), relative (10, 0, -4) to origin (-318, -60, 241).
There were 1,629 and 329 automatic-repair events respectively; other
projects progressed. This was not a funding, support-pillar, or unloaded-chunk wait.

A read-only inspection of the corresponding saved project supplied the frozen
revision 10, plains/rustic, timber_forward/prosperous, stage 1, rotation 1,
unmirrored design and construction order [0, 1975]. No player world was modified.

The native fixture regenerates 1,975 operations and identifies operation 1,180
as cosmetic dirt path beneath iron bars. Vanilla's actual path survival check
and scheduled-tick implementation turn this covered path into dirt. The old
finish pass sees the saved path supply receipt, declares the dirt damaged,
rewinds to 1,180, skips replacing the solid dirt, and fails verification again.
The unfixed Fabric fixture reproduced exactly 1,180 / 1,975 after 300 attempts.

Normal and instant development share this placement and finish logic, so this
can affect both; debug acceleration is not its exclusive cause.

## Fix and safety boundaries

Recognize builder-supplied dirt path settling into dirt, without changing the
frozen blueprint, operation order, saved count, or block in the world. Only
optional yard ground gets the finish-pass equivalence; required floors retain
their exact physical checks. A retained dirt foot also remains a valid planned
support and keeps the unfinished site's no-drop ownership rule.

Unowned dirt, air, stone, inventories, and arbitrary replacement blocks do not
satisfy this exception. Missing required structures, loaded-chunk checks,
occupancy, protection, construction budgets, and final handover remain enforced.
No new recovery command, timeout bypass, generated pillar, or player-save rewrite.

Debug-only snapshots now include frozen blueprint inputs and order cuts; live
repair reasons include the expected state, actual state, and placement role.
The design seed is an authored building variation seed, not the private world
or economy seed.

## Verification

- Before fix: native Fabric reproduction fails at the exact reported cursor.
- After fix: fresh and resumed Smithies finish in both modes in the disposable
  native fixture, using vanilla path survival/tick logic. Normal-mode fixture
  uses the default two-blocks-per-second allowance. Completion is saved and read
  back by a fresh economy service.
- Receipt, no-drop, missing/foreign block and required-floor assertions included.
- PASS full loader-neutral common suite, including handbook and runtime wiring checks.
- PASS expanded focused Fabric and NeoForge dedicated-server suites: all four
  exact Smithy scenarios, Cottage and Inn recovery, live mode-switch scheduling,
  storage/occupant safety and ownership/loot behavior.
- PASS Fabric build (4m13s) and NeoForge build (4m09s), including catalog,
  Bank geometry, fence, reader/settings and packet checks.
- PASS candidate verifier: current source fingerprint, packaged version and
  cross-loader parity. PASS git diff whitespace checks.

The default, broader dedicated-server suite was not rerun for this fix; its
previous navigation limitation is still recorded below. There was no human
modpack or shader gameplay test.

### Candidate artifacts

Both loaders: `0.4.0-beta.40`.
Source SHA-256:
`216cd5347b530aa56ebfc9bb8e10372a7de4f713ba7a1e075133910b6143d8e8`.

- Fabric: `fabric/build/libs/the-emerald-standard-fabric-0.4.0-beta.40.jar`,
  16,117,065 bytes; SHA-256
  `004f728ad2f09a7e36c36782455887875d7476a6ebe0bf150bfea0afe93209a7`.
- NeoForge: `neoforge/build/libs/the-emerald-standard-neoforge-0.4.0-beta.40.jar`,
  16,105,530 bytes; SHA-256
  `6bd85a2a32aac6aa152e6cbba95734221650b4c2fd32d65a2b9184a6eb81889b`.

Focused native logs: `build/server-smoke/fabric.log` and
`build/server-smoke/neoforge.log` (replaced by future smoke runs).

The focused server suite also runs the reported cottage/Inn finish regressions,
mode-switch scheduling, occupant/storage safety, and construction ownership/loot.
Startup-only synchronous fixtures are not a representative server MSPT benchmark.

## Handbook review

Updated guided `construction_safety` with settled decorative path behavior and
its limits, and compact `construction_safety` with “Covered paths settle.”
The guided recovery/debug chapter describes the extra diagnostic data. Reviewed
`HandbookChapters` and `EmeraldHandbook`: existing construction-safety and recovery
routing remains correct; no items, recipes, or new page routes are needed.
The handbook mechanics regression now requires both descriptions and the
required-floor limitation. Ordinary newspaper/story text was not changed.

## Release scope

Includes the preceding uncommitted beta.38 Bank-activation-radius and beta.39
Bank-walkway work requested in this task. Those candidates' validation records
remain separate historical evidence, not certification of beta.40. Builds are
local candidates, not installed into the user's Modrinth profile.

Known broader-suite limitation from beta.38/39: a starter-cottage workstation
navigation fixture failed outside the focused Bank suites. Do not interpret
focused green checks as certification of the full gameplay matrix.
