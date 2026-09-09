# Beta.4 handbook and settings handoff

## Scope and final source

Andrew requested a larger, less cluttered starting handbook with smaller text, and a working Mod Menu configuration action. The implementation is on `codex/modular-village-architecture`; this work does not merge that branch into main or publish a stable release.

Final code source: `c6cdad3b81718331b35e3d3823ee12f6435a91a2`.

Implementation history:

- `2ab64cbc1241b5157fe1981c55d02dcb49f30cb6`: custom held-handbook reader, local text preferences, optional loader settings integrations, guarded world editor, regression coverage, and resource-ready client checks.
- `5560ec764f3ce53e64019e1b9f7ad159f0ead889`: screenshot-driven heading contrast, numbered-step spacing, and chapter-sidebar improvements.
- `c6cdad3b81718331b35e3d3823ee12f6435a91a2`: refresh reader preference when returning to Settings and keep numeric drafts in numeric controls.

Both loader builds identify themselves as `0.4.0-beta.4`, target Minecraft Java Edition 26.2 and Java 25, and retain economy format 18.

## User-facing changes

Using the existing handbook from the player's hand now opens a wide, responsive reader rather than the narrow vanilla book screen. Its 16 chapters have search, section headings, scrollable body text, previous/next buttons, and keyboard navigation. Repeated inline navigation and decorative object glyphs are removed from this view. Numbered starting instructions remain separate, and crafting-grid instructions are spelled out.

Body text defaults to 90 percent, with A-/A+ controls from 80 through 120 percent. The preference persists in `config/the_emerald_standard-client.properties` without changing global Minecraft GUI scale. The item id, one-time delivery behavior, existing stacks, and replacement recipe are retained. Other written books are unaffected; lecterns retain the vanilla book view and the original 46-page written content.

Fabric now registers an optional Mod Menu configuration factory, compiled against Mod Menu 20.0.1. NeoForge registers its loader config-screen extension. Both use the same Settings screen, also reachable from Handbook > Settings without Mod Menu.

All 27 existing world settings are editable while the owning single-player world is open. Apply validates and saves the entire draft on the server thread; Done discards unapplied world edits. Reader-size changes save immediately. Invalid, empty, unknown, out-of-range, stale-world, and detected externally conflicting edits are rejected before replacement. Main-menu and remote-server sessions expose local reader preferences but cannot write remote or unopened world settings. The starting-book toggle affects future eligible first-join delivery, not an already received book.

## Validation: what passed and what did not

The initial implementation's workflow [34308228995](https://github.com/chedidandrew/The_Emerald_Standard/actions/runs/34308228995) passed all eight jobs. That result applies to source `2ab64cb`, not automatically to its successors.

The final source's workflow [34309499608](https://github.com/chedidandrew/The_Emerald_Standard/actions/runs/34309499608), including a retry of its two failed jobs, ends with six successes and two failures:

| Check | Final result |
| --- | --- |
| Common economy regressions | Passed |
| Fabric build, build-time reader/settings tests, and package checks | Passed |
| NeoForge build, build-time reader/settings tests, and package checks | Passed |
| Fabric dedicated-server startup | Passed |
| NeoForge dedicated-server startup | Passed |
| NeoForge client check | Passed |
| Fabric client with Mod Menu | Failed overall; reader and factory assertions passed |
| Fabric client without Mod Menu | Failed overall; reader assertions passed |

The final Fabric client logs show successful original 46-page layout validation and successful reader/settings checks. The Mod Menu variant also logs `The Emerald Standard Mod Menu configuration factory verified`. Both variants produced real 1280x800 screenshots of the reader and settings screen, which were inspected.

The overall Fabric jobs fail because the strict log check also sees this X11 error during rendering:

```text
65547: X11: Standard cursor shape unavailable
```

One retry of the failed jobs was requested without changing code or weakening the checker. The Mod Menu retry reproduced that cursor error while again passing the handbook and factory assertions. This is distinct from the previous untranslated-handbook timing failure, which did not recur. The cursor message concerns the Linux virtual-display cursor path; its full environmental cause has not been isolated. It is not evidence that the ordinary Windows launch crashes, but Windows launch behavior was not tested here and is not guaranteed.

The cursor error has not been suppressed or called fixed. The final workflow must not be represented as fully green. These are experimental test binaries, not a fully validated or stable release. The all-green handoff goal in the implementation plan remains unmet for the final Fabric client jobs.

## Local checks of the final artifacts

- Both downloaded artifact ZIPs match GitHub's reported SHA-256 digests.
- Both binary and source JARs match their artifact-provided SHA256SUMS.
- ZIP entry integrity checks passed.
- Every JSON resource parsed: 15 Fabric and 14 NeoForge.
- Required reader, settings, custom item, preference, and test classes are present.
- Fabric has 401 class files; NeoForge has 400. All have Java class major version 69.
- Fabric metadata contains the optional Mod Menu entry point without making Mod Menu a required or bundled dependency.
- Comparison with the preceding `5560ec7` playable JARs shows the sole changed entry is `EmeraldSettingsScreen.class`, as expected for the final UI-state correction.
- The exact final source archive's loader-neutral core, reader geometry, preferences, and configuration classes were compiled with local Java 21 and `ReaderSettingsSelfTest` passed. This supplemental check does not recompile the delivered Java 25 binaries and is not a local Minecraft launch.

The file tests cover actual preference persistence, all 27 setting values, valid writes, invalid edits leaving disk/runtime unchanged, stale snapshots, wrong-world paths, and external-file conflicts. Screenshot inspection covers the reader and title-screen settings view. A human-driven in-world configuration click-through, every chapter at every GUI scale, multiplayer play, and the full manual test matrix remain unclaimed.

## Exact downloadable binary identity

### Fabric

- GitHub artifact ID: `10087969531`.
- Outer ZIP SHA-256: `37675f16ba754d95704edf83ee9e0ebca4f68593be765e602d1fb4bcdc824e24`.
- Handoff filename: `the-emerald-standard-fabric-0.4.0-beta.4-mc26.2-c6cdad3.jar`.
- Bytes: 1,519,390.
- JAR SHA-256: `8c93482a86c89f67187281be19b5a39ac835f2ee34ee5d7ea86773234348c1f7`.
- Requirements: Minecraft 26.2, Java 25, Fabric Loader >=0.19.3, and compatible Minecraft 26.2 Fabric API >=0.158.0+26.2. Mod Menu is optional; integration was tested with 20.0.1.

### NeoForge

- GitHub artifact ID: `10087978400`.
- Outer ZIP SHA-256: `3f955fddcfa0c147a292b9d0667ddc81dc415c36286aeafe663c20a71e638a0c`.
- Handoff filename: `the-emerald-standard-neoforge-0.4.0-beta.4-mc26.2-c6cdad3.jar`.
- Bytes: 1,511,932.
- JAR SHA-256: `c12f6c07f394c9d4346e7f9533dd013f22d736198b8b4c2960b3322200a96352`.
- Requirements: Minecraft 26.2, Java 25, and a compatible Minecraft 26.2 NeoForge build >=26.2.0.72.

The handoff files are existing, unmodified CI binaries. Only external filenames add the Minecraft version and short source commit.

## Installation and limits

Replace the old mod JAR with exactly one matching loader build. Keep matching exact builds on server and clients. Back up the world or use a disposable test copy. An existing held handbook uses the new reader; a replacement copy is not required.

To edit world settings in single player, enter that world, open Mods > The Emerald Standard > Configure or Handbook > Settings, and press Apply after editing. Reader size is available outside a world too. Remote server world configuration remains administrator-owned.

Economy format stays 18. Previously documented inventory-recovery isolation concerns and whole-economy save costs remain open; this patch does not change those financial paths. No manual test result was marked passed, no release was published, and no player world was edited during this development task.

## Documentation change record

This file records the final code, checksums, observed checks, screenshot review, and remaining CI failure. Its documentation-only commit skips redundant CI; the failed final-source workflow remains visible and is not bypassed or relabeled as successful.
