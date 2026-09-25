# Beta.5 food-source follow-up verification

Date: 2026-09-10. Local, uncommitted/unpublished source candidate on
`codex/modular-village-architecture`, following the earlier beta.5 growth/loot pass.
This record does not certify GitHub CI, a published release or a human gameplay session.

## Changes in this follow-up

- Observe nearby growing food crops and live livestock automatically on loaded village
  censuses, with crop reads spread across ticks and exclusive nearest-district attribution.
- Scale agriculture output by weighted crop maturity and adult/baby livestock; diminish
  returns toward +150%, replace counts after harvesting/removal, and expire stale observations.
- Stack the food bonus with Peaceful, mirror physical observations into existing market
  counterfactuals, and expose the bonus/source units in the City expansion dashboard.
- Save observed units/date in economy format 20. Older saves default to zero until observed;
  existing architectural identities and container inventories remain unchanged.
- Verify and document the existing role-specific loot tables; no new loot rolls or automatic
  replenishment were added. Correct the obsolete finite-city statement in the README.

See [the player guide](../VILLAGE_FOOD_SOURCES.md) for exact units, range, timing and caveats.

## Completed automated checks

- Full `scripts/run-common-tests.sh`, including `VillageFoodSupplyRegressionTest`: exact
  +65% example, agriculture/reserve gains, Peaceful stacking, diminishing returns, empty
  observations, stale expiry, invalid inputs, copies, format-20 round trip, format-19 defaults
  and matching market-shadow observations. Existing finance, expansion, structure, menu,
  lifecycle and persistence regressions also passed.
- Fabric `gradlew build`: successful, including authored architecture/lighting and historical
  structure preservation, Bank geometry, menu packet codecs and reader/settings checks.
- NeoForge `gradlew build`: successful, including loader-aware authored-catalog tests and
  the shared menu/reader checks.
- Final isolated dedicated-server smoke on **both loaders**, after the last production edits.
  `VillageFoodEnvironmentSelfTest` counted a real 64-block mature/replanted wheat field,
  split overlapping districts without duplication, counted actual cows/pig/calf, excluded a
  wolf, observed death and range removal, and measured zero after field/livestock removal.
  The existing production founding/materialization/settler fixture and ten-table loot/NBT
  checks also passed. Both smoke harnesses exited 0.
- Both `scripts/verify-built-jar.sh` gates passed with required food classes, all ten loot
  tables, loader metadata, resource checks and refreshed per-loader `SHA256SUMS` files.
- `git diff --check` passed. No existing user save, gallery or game profile was edited/opened.

The disposable smoke harness intentionally stops its marked server process after success.
On Windows its cleanup may append Gradle exit-143/daemon-disappearance messages; the final
integration success markers and harness exit 0 precede those shutdown messages. Existing
Windows performance-counter/OSHI warnings occurred during startup, and NeoForge still reports
the existing API-deprecation warnings. These are not new food-fixture failures.

## Local playable artifacts

Both are version `0.4.0-beta.5`; install only the one matching the loader. These hashes identify
the checked local binaries, not a published or exact-commit CI build.

- `fabric/build/libs/the-emerald-standard-fabric-0.4.0-beta.5.jar`
  SHA-256: `72414ca4c3a666372f4ef0b0378fe9f6d2bc7366bb0d88d9b975f3bd5c7ac7ef`
- `neoforge/build/libs/the-emerald-standard-neoforge-0.4.0-beta.5.jar`
  SHA-256: `f74f6e6722dacfca9e303bc41444ffdbc7b5e6f77542f65e2f9a9a93ae65bdc0`

Back up before upgrading to format 20. Source JARs and checksums are beside the playable JARs.

## Remaining human review

- Actual farming/harvesting/breeding cadence in a played village and food/upkeep balance over
  many economic days, including distant district farms and offline freshness decay.
- City expansion dashboard readability at different GUI scales. No new client screenshots
  or visual-review scores were produced in this follow-up.
- Large-city entity and scan performance on target hardware. The shared read budget and
  diminishing returns are safeguards, not a claim of cost-free unlimited animals or districts.
