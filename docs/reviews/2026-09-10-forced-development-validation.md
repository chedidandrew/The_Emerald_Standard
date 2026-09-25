# Forced development validation — 2026-09-10

Status: implemented locally; not published and not enabled in the user's world.
See [behavior and precautions](../FORCED_DEVELOPMENT.md).

## Checks

- Full common regression suite passed, including opt-in/off behavior, immediate
  economic labor, no resource/stat inflation, durable charters, duplicate rejection,
  restart persistence, normal gates after disable and bounded per-district catalogs.
- Focused final regression passed: 10,000 plot-index entries / 1,000 local queries
  in 13 ms on this machine. Tested moved/retired lots, negative coordinates,
  dimension isolation, cell boundaries and oversized conservative fallback.
  This is an index microbenchmark, **not** whole-city Minecraft performance.
- Shared grants cannot grow with 10,000 callers. Deadline/reset/lag checks passed.
  Final scheduling regression simulates Banks consuming all even-tick lag grants:
  both dimensions and both sites still receive exactly ten turns each. Independent
  frontier rotation visits all eight directions for each district.
- Fabric and NeoForge full Gradle builds passed. After the final scheduling
  fairness refinement, the focused regression was rerun and both final jars
  were recompiled/reassembled and package-verified.
- Both native-server integration suites passed. The forced fixture constructs a
  real cottage and terrain preparation with a paused, zero-resource city, exercises
  occupied terrain/build-cell waits and storage/protection vetoes, and verifies
  completion survives disabling and restart.
- Both actual clients passed first-launch/restart smoke checks and strict log
  validation. At GUI scales 2 and 4, confirmation text fits; Cancel/Escape do not
  enable the draft, Confirm does not apply it, and Reset clears the flag.
  The final compact warning screenshot was visually inspected.
- `git diff --check` passed.

Evidence logs are in ignored `build/forced-development-*`. Warning screenshots:
`build/client-smoke/forced-development-{fabric,neoforge}-20260910/screenshots/tes-reader-ci/forced-development-warning.png`.
The native server harness intentionally stops its disposable JVM after the
integration success marker; the resulting Gradle daemon-termination tail is expected.

## Final installable artifacts

| Loader | JAR | SHA-256 |
| --- | --- | --- |
| Fabric | `fabric/build/libs/the-emerald-standard-fabric-0.4.0-beta.5.jar` | `6a16f74682c4c909fea3ce0e5d965f8cfc38e05d6262b2d77511a8dc500cb420` |
| NeoForge | `neoforge/build/libs/the-emerald-standard-neoforge-0.4.0-beta.5.jar` | `e7e0a14cf04625e9be9edccd58e433b4eee036e6d5db3b926733da4735208c8b` |

Use the appropriate loader jar, not the sources jar. Existing unrelated changes
and the earlier architecture/settings/crew/investment work remain intact.

## Limits still requiring field testing

No prolonged multi-hour growing-city soak or constant-MSPT claim. Local work and
caches are bounded, but economic records, ordinary entities, loaded chunks and
whole-world checkpoints still grow in cost. The cooperative deadline cannot
interrupt a terrain preflight, Minecraft neighbor updates or a save barrier.
Expansion does not load new chunks; keep moving toward available frontier.
Disabling is not a rollback: structures, terrain changes, loot and queued work remain.
