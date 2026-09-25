# Beta.5 terrain finishing validation — 2026-09-10

## Scope

Local follow-up to terrain-aware development: supported primary-road grading, short Bank
approach extensions and grounded hillside retaining courses. No commit/push, release publication,
modpack installation, gallery regeneration or user-world mutation was performed.

Only newly reserved sites acquire these plans. Existing completed buildings and saved authored
recipes are unchanged (authored revision 9, Bank version 8). Economy format is now **23**;
format-22 clearance payloads decode as the original removal-only plans. Keep a pre-upgrade backup.

## Evidence

- Common regression suite passed, including 100 seeded grade profiles, endpoint preservation,
  two-block road earthwork bounds, one-step grades, flat valley landings, cliff rejection,
  before/after serialization and reading a hand-written legacy format-22 payload.
- Fabric and NeoForge dedicated-server smoke tests passed on the final implementation.
- Final full Gradle builds passed: Fabric in 3m 3s and NeoForge in 3m 14s, including
  exhaustive authored-catalog/legacy-geometry gates, Bank checks and menu/settings checks.
- Production site-preparation replay built a sloping path across a shallow crater with stairs
  and solid supports. Each invocation wrote at most one block; a serialized-plan restart
  retained the exact work, and a complete replay wrote no additional blocks.
- Real-server fixtures checked three-course retaining borders, storage/claim vetoes and exact
  resulting terrain cells. Existing wooded-hillside Bank and starter-house construction,
  concurrency, restart, role loot and settler tests also passed.
- Package verification passed for both loader JARs and requires both new terrain planner classes.
- `git diff --check` passed.

Natural, seed-generated terrain was additionally surveyed without editing it:

| Loader | World seed | Sampled dry route starts | Accepted graded connections |
| --- | --- | ---: | ---: |
| Fabric | -8203310491721592170 | 31 | 19 |
| NeoForge | -867990475224933090 | 35 | 18 |

These are bounded eight-column route samples near the disposable test world's origin, not
measurements of whole-village growth success. Fixture-only code explicitly loads test chunks;
production surveys do not force-load chunks.

The smoke harness terminates only its own uniquely tagged server processes after PASS. A
subsequent Gradle daemon-disappeared/exit-143 cleanup message is not a failed server test.
Existing Windows OSHI/Perflib warnings and NeoForge LAN network warnings remain environmental.
Synchronous exhaustive catalog checks can produce a startup long-tick warning; this is not
a production terrain-planning benchmark.

## Final packaged artifacts

- Fabric: `6927e99c7e48ab92f89a3429b0706a5ea3307656fde6d6b5bbc5880f5ef33d4e`
- Fabric sources: `c258069fd9a2d09c6b70282995af670ec6059bec37d7db651266611a3e2ab937`
- NeoForge: `10a9c784df65407c988086dec0fca70265eadd8440be90b24bf4f9a10b1431d8`
- NeoForge sources: `1fb632d56993433465ea1eaf813d9b41c8125a041c5eca6bedb56fb35b105fac`

Names remain `the-emerald-standard-<loader>-0.4.0-beta.5.jar` in each loader's `build/libs/`.
Each directory also contains `SHA256SUMS`.

## Limits / manual follow-up

- No in-game visual review or villager navigation playtest was performed in this pass.
- Primary routes keep existing horizontal coordinates. This does not add road rerouting,
  river bridges, side-branch grading or distant-road searches for Banks.
- A protected, wet, too-steep or authored-cell-conflicting optional connection is omitted
  as a whole, preserving the building's eligibility and the existing best-effort trail system.
  Retaining columns are independently optional; not every hillside receives a continuous wall.
- Retaining masonry stays within the lot's surveyed border and avoids authored cells and the
  entrance route. No arbitrary landscaping outside the reserved border is added.
- Terrain work uses the existing per-site maximum of 2 authored operations/second at 20 TPS.
  It adds work before the building, rather than silently exceeding that requested rate.
- Construction scaffolding/presentation, new blocked-site recovery UI, and broader profiling
  remain separate future work; they were not part of this focused implementation.

Inspect newly generated sites in a copied survival world before publication, especially
turning stair routes, cut-bank corners and paths approaching player-modified infrastructure.
