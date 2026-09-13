# Investment diversity and Banker dashboard validation

Date: 2026-09-10. Unreleased 0.4.0-beta.5 candidate, economy format 27.
Baseline: `7a512c973ad498f2f9d7cbb73278710d01bce62f`.
This pass is local and has not been committed or pushed.

## Scope

- Correct Town / City expansion text and button overlap.
- Explain empty first-day price history and zero Bank Cash instead of implying a failed trade.
- Add TREA, AURM, BRCK, FISH and VENT with distinct mechanisms, risk labels and explanations.
- Preserve existing investments, VILX weights, positions and price history on upgrade.
- No changes to village growth rules, structures or the user's test worlds.

See [investment behavior](../INVESTMENT_BEHAVIOR.md) for mechanics and compatibility notes.

## Completed checks

- Full `scripts/run-common-tests.sh`: passed, including pricing, all fourteen buy/sell flows,
  proportional cost basis, migration from format 26, corruption rejection, deterministic restart,
  layout geometry and handbook resource regressions.
- Fabric and NeoForge Gradle `build`: passed. NeoForge binary and source jars were then refreshed
  after the final dashboard fixture/wording adjustment.
- Both dedicated-server smoke harnesses: passed startup and Banker integration checks, including
  catalog-sized menu storage and signed-short packet round trips. Disposable server worlds only.
- Real Fabric and NeoForge client launches: passed 47-page handbook layout, settings/navigation,
  actual-font dashboard fit and production-renderer dashboard captures at GUI scales 2 and 4.
- Client-smoke log-validator fixture suite: passed. The full combined two-process/restart client
  script was not rerun as a single pipeline in this pass; individual client runs are the evidence.
- `scripts/verify-built-jar.sh fabric` and `neoforge`: passed packaged metadata/resources and
  binary/source-jar checks.
- `git diff --check`: passed.

The dedicated-server harness intentionally stops its uniquely marked processes after the success
marker. Gradle can report a terminated run task/daemon afterward; this is harness cleanup, not a
failed assertion. Windows OSHI system-information warnings also appeared, without preventing startup.

## Behavior diversity measurements

Twelve deterministic seeds, 3,650 economic days each. Correlation is daily return correlation
against the broad market; volatility is annualized from daily observations. These are test
measurements, not guaranteed player returns or fixed outcomes.

| Listing | Correlation | Volatility |
| --- | ---: | ---: |
| TREA | -0.231 | 3.5% |
| AURM | -0.316 | 18.0% |
| BRCK | 0.754 | 35.0% |
| FISH | 0.167 | 19.1% |
| VENT | 0.170 | 42.5% |

Additional regressions check Treasury rate sensitivity, construction regime changes, defensive
event responses, bounded fishing seasons, both expedition jump directions and save/reload stability.

## Local visual evidence

Final captures: `build/client-smoke/investment-review-neoforge-20260910/screenshots/tes-reader-ci/`.
Includes `town-expansion-scale-{2,4}.png` and
`market-{irng,trea,aurm,brck,fish,vent}-scale-{2,4}.png`.
Fabric captures are in `build/client-smoke/investment-review-20260910/screenshots/tes-reader-ci/`.

Town expansion and Market screenshots were visually inspected for panel, text and control overlap.
These are opt-in fixtures using the actual Banker renderer and Minecraft font, not a connected
player's account. Charts use 365 actual deterministic simulation days. This does not replace a
multiplayer playthrough or long-running balance feedback. Captures and test worlds remain ignored
local build artifacts, not repository source assets.

## Test binaries

- Fabric: `fabric/build/libs/the-emerald-standard-fabric-0.4.0-beta.5.jar`
  SHA-256: `e30b49659618a955500cf5b3b8900a09da66acb4fc12d83b4b6b02b72418f3cc`
- NeoForge: `neoforge/build/libs/the-emerald-standard-neoforge-0.4.0-beta.5.jar`
  SHA-256: `473f972cf2b216094f3edc832840db548e5d6e9a4d3f7ef86aa32f9407037ef7`

Install only the binary matching the loader, not the `-sources.jar`. Back up the entire world;
format 27 is not downgrade-compatible. Clients and server need matching new mod builds.
