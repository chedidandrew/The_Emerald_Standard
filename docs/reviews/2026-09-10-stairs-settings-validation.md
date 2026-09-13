# Roof stair and settings validation — 2026-09-10

Unreleased 0.4.0-beta.5 follow-up; preserves the earlier investment and construction-crew work.
No existing player save was edited, no running player game was replaced, and no GitHub push was made.

## Changes

- New Bank revision 10 reverses 187 reviewed bottom roof stairs per biome. Main slopes,
  dormers and gables now rise inward. Existing inverted brackets, entry steps, seating, lights,
  palette and occupied coordinates stay unchanged. Revision 9 and all previous Bank plans
  remain available to historical integrity checks and pending plans.
- New authored revision 10 corrects reviewed roof slopes in `mine_adit_03` and
  `warehouse_wharf_04`. All other 50 masters remain cell-for-cell unchanged from revision 9;
  furnishing stages remain unchanged in all 52. Stair corrections run after the prior detail,
  support and lighting passes so those refinements cannot drift as a side effect.
- Editable world-local construction rate: `village_prosperity.construction_blocks_per_second`,
  integer 1–100, default 2 at 20 TPS. Each Bank/project/finishing site has an independent,
  tick-distributed allowance. Paused or unloaded work does not accumulate credit. Clearance
  uses that allowance; temporary worksite presentation and native neighbor updates are not
  authored building operations. This is a maximum, not a promise of throughput through obstructions.
- Every one of the 26 settings has label/control hover help explaining its effect and default;
  numeric help also states units and range. Reset resets every page's draft; Apply saves the
  current world. Done discards unapplied world edits. Reader size is local and resets immediately
  to 90%. Remote/title screens cannot change a world's settings.
- Legacy interval/blocks-per-tick keys remain validated and accepted, but retain the previous
  effective two-block default unless the new explicit rate is set. Canonical saves use the new key.

## Verification

- Complete loader-neutral suite: `build/stairs-settings-common.log`, exit 0.
- Fabric and NeoForge full Gradle builds passed. Their production admission gates check the
  complete authored catalog's geometry, distinctiveness, routes, structural support and lighting.
- Exact stair audit: 52 masters × five biomes, 70,335 stair entries, all rotation/mirror
  combinations; 685 targeted authored slope changes. Separate Bank checks prove the 187
  direction-only changes in each biome. Frozen legacy Bank and authored snapshot tests passed.
- File-based config tests cover all editable keys, invalid/partial drafts, external-edit and
  stale-world rejection, persistence and all-key reset. Rates 1, 2, 3, 7, 20, 37 and 100 produce
  exactly their allowance over arbitrary 20-tick windows independently for five modeled sites.
  This is deterministic scheduling verification, not a many-city performance benchmark.
- Both clients passed first launch and restart, checked with the strict log validator.
  Actual settings widgets at GUI scales 2 and 4 exercise numeric edits across every page,
  label hit regions, all-page Reset, reader reset, footer separation and disabled world writes
  in a disposable preview. The settings screenshot was visually inspected.
- Both isolated dedicated-server smoke runs passed, including full progressive Bank placement,
  obstruction/occupancy, loot receipts, construction crews, restoration and walking regressions.
  Native terrain/world checks run only in generated disposable fixtures. Existing OSHI system-info
  warnings are environmental; each smoke harness deliberately terminates its uniquely tagged
  server after the integration PASS, which produces an expected trailing daemon-disappeared log.
- `git diff --check` passed. Binary packaging checks include the new settings-help and stair classes.

## Review artifacts

- `build/client-smoke/stairs-settings-fabric-20260910/screenshots/tes-reader-ci/settings-speed-reset.png`
- `build/client-smoke/stairs-settings-neoforge-20260910/screenshots/tes-reader-ci/settings-speed-reset.png`
- `build/stairs-settings-fabric-client-combined.log`
- `build/stairs-settings-neoforge-client-combined.log`
- `build/stairs-settings-fabric-server.log`
- `build/stairs-settings-neoforge-server.log`

Use the matching loader's binary JAR from its `build/libs` folder; do not install the sources
JAR or both loaders together. Each folder's generated `SHA256SUMS` records the packaged files.
Back up the whole world before replacing the mod. Existing roofs will not automatically be
retrofitted; inspect a newly generated Bank or fresh comparison-gallery placement to review this pass.

Verified binary SHA-256:

- Fabric: `fa53badbc9c86d615d6a5721a88df7f9a874b94d2df710aa0d2824d9a7f6442c`
- NeoForge: `b2baf07a6b1913b9a48080dd7d042d1df094008288d064e8e19fd363da3a0611`
