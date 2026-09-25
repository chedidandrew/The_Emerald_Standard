# Emerald Wire, player news and variable growth validation

Date: 2026-09-11. Candidate: 0.4.0-beta.5, Minecraft 26.2, Java 25.

## Scope and outcome

Implemented smoothly changing positive fundamentals targets for stocks, commodities and
VILX's broad-economy component; commodity reference growth and correlated shocks; a
five-outlet, server-authored news archive; a reusable Village Newspaper and read-only
reader; and evidence-based local player-action reports. The Exchange Desk still provides
access to the reader. Existing banking, investments and previously requested village
features are retained. No live profile installation or user-world edit was performed.

See [player-facing behavior and limits](../EMERALD_WIRE.md) and
[investment behavior](../INVESTMENT_BEHAVIOR.md).

## Verification

- All 77 common regression entry points passed. The long statistical EconomyRegressionTest
  ran separately; the remaining entries are recorded in build/news-common-final.log.
- NewsGrowthRegressionTest covers target distributions, smoothness, losses, bounded archive,
  UUID attribution across renames, persistence, format-30 migration, and identical prices
  despite player-news traffic evicting the archive. Event cooldowns live outside the archive.
- Fabric full build, including authored structure validation, Banker packet codec and reader
  settings validation: PASS (build/news-fabric-build.log).
- NeoForge full build plus verifyReaderSettings and verifyBankerMenuPacket: PASS
  (build/news-neoforge-build.log). Final preview-only changes were followed by jar/sourcesJar
  generation and successful native client checks.
- Dedicated server smoke on both loaders: PASS (build/server-smoke/fabric.log and
  build/server-smoke/neoforge.log). The harness terminates its disposable server after
  successful markers; a trailing Gradle runServer failure/daemon termination is cleanup,
  not a failed self-test.
- New native checks passed on both loaders: actual/cancelled block removal, player-property
  exclusion, real container pickup/return and shift-clicks, different-player replant credit,
  round-robin fairness with unloaded observations, native SavedData round trip, actual
  ItemStack packet serialization, read-only menu click safety, paging cooldown and recipe.
- Existing native integrations also passed: bank ownership/fallback, construction protection,
  Creative content, district map/desk placement, commodities and unified inventory funds.
- Native news-reader client checks passed on Fabric and NeoForge at GUI scales 2 and 4:
  article navigation, search, outlet selection, scrolling and four animated handbook recipes.
  Both loaders verified all 54 compact pages against the actual Minecraft font.
- The title-screen screenshot fixture delegates rendering to the real newspaper screen
  through a tick-free outer screen; native container ticking otherwise requires an in-world
  LocalPlayer. Server interaction/packet behavior is separately exercised by native tests.
- Final handbook resource and mechanics regression rerun: PASS.
- Both packaged JAR/resource/metadata checks and git diff --check: PASS.

## Visual review

Inspected the Fabric newspaper at both scales and its scale-4 recipe card. Text, paper
background, headings, navigation and recipe cells are legible; longer articles scroll at
the larger GUI scale. NeoForge's equivalent client checks also passed.

- [Fabric newspaper, scale 2](../../build/news-client-fabric/screenshots/tes-reader-ci/newspaper-scale-2.png)
- [Fabric newspaper, scale 4](../../build/news-client-fabric/screenshots/tes-reader-ci/newspaper-scale-4.png)
- [Fabric newspaper recipe, scale 4](../../build/news-client-fabric/screenshots/tes-reader-ci/newspaper-recipe-scale-4.png)
- [NeoForge newspaper, scale 4](../../build/news-client-neoforge/screenshots/tes-reader-ci/newspaper-scale-4.png)

## Statistical behavior, not a guarantee

The economy regression simulated 250 seeds over 75 years. The sampled positive target is
not a promised realized CAGR. Results retain volatility drag, down years and losing
long-term outcomes; no year-end adjustment forces a profitable result.

VILX: mean CAGR 1.92%, median 1.75%, fifth percentile -1.39%, 95th percentile 5.63%.
44.4% of observed annual returns were negative; annual extremes were -59.3% and +77.0%.

| Asset | Mean simulated CAGR |
| --- | ---: |
| RSDN | 2.81% |
| DPMN | -3.84% |
| NSPC | -3.06% |
| ENDR | 0.75% |
| GLDH | 1.26% |
| POTN | -3.09% |
| IRNG | 3.28% |
| MCRT | -5.27% |
| TREA | 3.31% |
| AURM | 7.83% |
| BRCK | 2.56% |
| FISH | 3.71% |
| VENT | -1.33% |
| Gold | 3.77% |
| Iron | 3.83% |
| Coal | 2.95% |
| Diamond | 3.06% |
| Copper | 4.20% |
| Redstone | 4.14% |
| Lapis | 3.57% |
| Netherite Scrap | 3.53% |

These are game-model results, not empirical real-world forecasts or a profit guarantee.
Full output: build/news-economy-statistics.log.

## Handbook accuracy review

Reviewed and updated long-form chapters, English localization, compact/lectern pages and
animated recipe cards in the same change. There are now 16 chapters / 64 long-form sections,
54 compact pages and four animated recipes. Guidance explains targets versus actual returns,
commodity volatility, the Paper + Ink Sac newspaper recipe, reader controls, player evidence,
replant/return windows, archive limits and lack of news-created punishments.
Compact wording was shortened where the actual font exceeded the native page limit.

## Limits and safe use

- The 256-article archive is intentionally bounded; the reader loads 64 at a time, and
  search/outlet filters apply to the loaded batch. Wording uses a finite authored library,
  seeded variation and recent-repeat avoidance, not unlimited AI-generated prose.
- Evidence does not prove malicious intent or renovation permission. Unknown ownership is
  skipped. Older unrecorded player modifications can be ambiguous. Creative/Spectator
  block/container actions are ignored; automation does not invent a player culprit.
- Observation work and ownership checks are bounded and do not force-load chunks. Saturated
  player-property tracking fails closed; very large histories can miss a report rather than
  perform an unbounded search. Pending observations persist but crash-before-save loss remains
  possible, as with other world state.
- News does not create fines, debt, inventory transfers, guard hostility or extra market
  shocks. It reports actual simulation changes without applying their effects twice.
- Economy format 31 preserves existing cash, holdings, cost bases, quotes and recorded price
  history. New references start at current quotes and the archive starts empty on migration.
  No synthetic investment history was added. Back up the whole world before updating, use
  matching client/server builds, and do not downgrade a migrated save.

## Verified binary SHA-256

Fabric: 7680984a6bd4c2205739239841d44c04af88b9e5a6f5b4963d878bf8719f6e32

NeoForge: 2e863cebfbcb14a322a162180337349fc12952512688269cd64810e4b7bbd75c

Matching binary and sources artifacts are in each loader's build/libs directory, with
SHA256SUMS regenerated by scripts/verify-built-jar.sh.
