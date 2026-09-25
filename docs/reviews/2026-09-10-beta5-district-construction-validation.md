# Beta.5 district coverage and progressive construction validation

Date: 2026-09-10. Local, unpublished follow-up on `codex/modular-village-architecture`.
Earlier dirty beta.5 work was retained. No existing gallery, inspection or player save was opened,
regenerated or upgraded, and no commit or push was performed in this pass.

## Implemented

- Food coverage grows from a starter neighborhood to the rectangle enclosing developed plots
  and the Bank, including a 24-block field/pen margin and intervening land. No vertical cutoff.
  Old projects without bounds use a local origin envelope, not the world origin. Overlaps have
  deterministic exclusive ownership. Crop scans use loaded sections with a 4,096-inspection
  per-tick allowance and skip non-crop sections; observation totals retain the existing numeric cap.
- New/relocated Banks reserve frozen terrain-supported plans before placement. Format 21 saves
  those plans. Actual saved blocks determine progress on restart. Foundations precede upper
  courses; support-dependent cells wait. Completion flushes chunks before atomically recording
  the Bank marker and removing its queue entry. Only freshly placed containers get initial loot;
  already matching player storage is not seeded, and the managed Banker is finalized at completion.
- Every due site receives one authored block operation each ten server ticks. Active site counts
  no longer divide a global budget. Finished sites independently complete approach/trail work.
  Legacy valid speed settings normalize to 10 ticks/1 block and appear read-only in Settings.
- New building candidates prefer flatter entrance approaches; Bank candidates rank approach
  elevation drop first. Existing plans/interiors are not changed, and authored doorsteps remain.

## Automated verification

- Complete common regression suite: PASS, including new immutable Bank-plan persistence,
  duplicate rejection, restart and matching-anchor atomic completion checks.
- Fabric and NeoForge full Gradle builds: PASS, including 52-master authored catalogs, frozen
  plan compatibility, biome variants, Bank geometry, packet codec and reader/config validation.
- Final scanner observation-cap adjustment: both loader assemblies rebuilt successfully,
  followed by the final runtime and packaged checks below.
- Final isolated Fabric and NeoForge dedicated-server smoke tests: PASS.
  - Full founding-home construction at one-block-per-pulse pace and settler/restart workflow.
  - Actual wheat maturity/harvest loss, adult/baby/dead/out-of-range animals, excluded pets,
    overlapping district ownership, distant developed plots and above-old-limit field coverage.
  - Complete authored Bank at one-cell pace, independent concurrent-site placement, partial
    restart, protected obstructing storage, no loot assignment to an existing matching container,
    completed-cell survival after neighbor updates, and persisted completion.
  - Existing ten-table loot/1,000-roll/container-NBT and Banker integration gates remain passing.
- Both packaged-JAR gates: PASS; binary/source contents and metadata verified, SHA256SUMS refreshed.
- `git diff --check`: PASS.

The smoke worlds are disposable fixtures under `build/server-smoke`, not user saves. Windows
OSHI/Perflib diagnostics and the existing heavy test/admission startup tick warning still appear;
they did not fail the test gates. This is not a long-running multiplayer performance benchmark
or a new screenshot-based visual review. Human pacing and terrain-selection review remains useful.

## Final local artifacts

Version: `0.4.0-beta.5`, Minecraft 26.2. Binary JAR SHA-256:

- Fabric: `697d504452cf7c401e35c9ae70de9a1360bd575354787cda28b4e2e8c8b8b9b5`
- NeoForge: `9a644d26bf3ef4411face0da9f58dd3f934927a69be23192d3a0731f4142f181`

JARs and matching sources are in each loader's `build/libs` directory. Before testing with an
existing world, retain a pre-upgrade backup: the upgraded format-21 economy must not be opened
by an older binary. No persistent builder assignments, custom villager arm animation or Bank
construction cancellation UI were added. See [construction behavior](../PROGRESSIVE_CONSTRUCTION.md)
and [food coverage](../VILLAGE_FOOD_SOURCES.md).
