# beta.10 — Newspaper edition and market comparison upgrade

Date: 2026-09-11. Unreleased local candidate; no install, publish, commit, or live-world modification.

## Delivered

- Portable Village Newspaper: paper-colored masthead, lead story, bylines, two-column articles, clickable table of contents/page numbers, and ranked Next/Previous navigation. Typed significance and recency determine order; quantities have a capped influence. Frozen editions and immediate synchronized privacy restrictions are retained.
- Exchange Desk: keeps the searchable section/outlet browser of the same archive. The server selects the presentation mode; neither reader offers financial actions.
- VCIX: eight-commodity buy-and-hold index, equal starting capital weights (12.5% each) which subsequently drift with prices. Current weights are saved and strictly validated. No independent index shock or positive-return guarantee. Existing investments and physical-resource routes are unchanged.
- VILX: renamed Villager Stock Exchange Index; its existing hybrid pricing was intentionally left unchanged pending the user's choice about the separately discussed true twelve-stock basket.
- Signed green/red daily movements in directory, selected investment and comparison. Single-chart color follows the displayed period; flat/unknown is neutral.
- Comparison: actual scrollable Details in each column, four range controls (30 days, 90 days, one year, All), rebased same-date curves, solid blue versus dashed amber, and signed period-return legends. No synthetic pre-listing data. All covers retained history (maximum 1,825 observations).
- One bounded read-only market document carries quotes and both curves as a coherent snapshot. Invalid/mismatched selections do not reuse unrelated charts. Selection spam only changes bounded read-only state; server refresh is throttled/cached.
- Denomination normalization also adjusts current news comparisons and tracked follow-up baselines, avoiding artificial split-day crash reports.

## Save and access safety

Economy format **33**. Pre-33 saves add VCIX at 100 E with one observation on migration day, preserving existing cash, positions, cost bases and history. Current-format missing/invalid basket data fails validation. Do not downgrade migrated saves; back up the whole world before testing and use matching beta.10 clients/server.

VCIX shares use the same existing fractional purchase, spread, sale, journal and persistence paths. Focused tests cover buying, restart, exit and rejected repeat sale. Reader transport books occupy unreachable slots with pickup/placement denied; normal clicks and quick-moves cannot obtain them.

## Validation

- All **83 common regression programs** passed (87 PASS log lines including multi-result programs and wrapper/version gates): build/beta10-common.log. That full run used beta.9 version labels before the version-only bump; subsequent current-version native builds and source/parity checks passed.
- New focused NewspaperMarketUpgradeRegressionTest passed again after extending its transaction tests: eight seeds x 365 days of weighted returns/weight drift, save/copy/reload, format-32 migration, missing current weights rejected, coherent display roundtrip, invalid/nonfinite data rejection, aligned new-listing dates, exact 30/90-day windows, positive first point=100, day-zero unavailable change, significance/age ordering, wire metadata, stable edition and immediate privacy change.
- Full Fabric and NeoForge Gradle builds passed, including genuine menu packet codecs, native structure gates, reader settings and NeoForge's loader test. Logs: build/beta10-fabric-build.log and build/beta10-neoforge-build.log.
- Both loaders' final newspaper native client checks passed at GUI scales 2 and 4: search/filter browser retained, stable incoming archive, 100 extra archive entries, empty search, contents navigation, paper without filters, ordered articles, bounded scrolling, and four actual recipe cards.
- Both loaders' final priority client checks passed at GUI scales 2 and 4: market search/edit controls, quotes/ownership, all four comparison ranges, independent left/right scrolling, inaccessible transport slots, reports and handbook.
- Actual cover, contents, article, browser and comparison screenshots were inspected. Small-screen subtitle/body overlap and chart legend/border contact found during review were corrected before the final builds.
- Fresh isolated Fabric and NeoForge dedicated-server smoke runs passed. Logs: build/beta10-fabric-server.log and build/beta10-neoforge-server.log. Native news/property/evidence/read-only packet, eight commodity purchases, unified inventory payments, exact confirmations, persistence and Banker integration gates passed. The harness intentionally terminates its uniquely tagged server process after all success markers; resulting Gradle termination text is not a startup failure.
- Current-source artifact verification passed. The isolated fingerprint regression accepted current jars and rejected two equally stale jars.
- Unrelated Windows OSHI/Perflib and unauthenticated development-client Realms warnings remain environmental. No gameplay corruption/error was inferred from those diagnostics.

## Handbook accuracy review

Updated long Market, Investments, Newspaper and Newspaper recipe text and the compact investment/browser/newspaper pages. Added VCIX behavior/type help and regression assertions. Guided text distinguishes daily movement from selected-period return, rebased comparison from forecasting, actual retained history from invented history, and newspaper reading from Desk search. The 16 long-form chapters, 64 sections, all 57 compact/lectern pages and all four animated recipes passed real-font checks; recipes, creative-only eggs and the grey item texture are unchanged.

## Previews

Paths are relative to repository root:

- Final Fabric newspaper cover/contents/article/browser (scales 2 and 4):
  build/beta10-fabric-news-9d967341d5ff4df5b95cdd5c59fc178e/screenshots/tes-reader-ci/
- Final NeoForge newspaper equivalents:
  build/beta10-neoforge-news-5afc507a997c42bba0c98c280453e31d/screenshots/tes-reader-ci/
- Final Fabric browser/comparison/report:
  build/beta10-fabric-priorities-827d6f4bd1cf412bad53f76f13475cc6/screenshots/tes-reader-ci/
- Final NeoForge browser/comparison/report:
  build/beta10-neoforge-priorities-2fbe5cc6a4a54669bfcbb430f5f726e5/screenshots/tes-reader-ci/

## Artifact identity

Version: **0.4.0-beta.10**. Shared production-source SHA-256:
`b884212010fa95bf5c406d9153e386d773713579b87276a0ac6b9a7d90caa31b`

| Loader | Jar | SHA-256 |
| --- | --- | --- |
| Fabric | fabric/build/libs/the-emerald-standard-fabric-0.4.0-beta.10.jar | `6E7C7EF912CD937E96FB89396957729726B5C2B2574EC9C11E8D928B51100E8C` |
| NeoForge | neoforge/build/libs/the-emerald-standard-neoforge-0.4.0-beta.10.jar | `5EE671C46C2E04624786ABE6C32E88C2B3FB2EB79ECFB941884EEE6C4C8FFDA9` |

This is verified development output, not a certification of every human multiplayer or modpack combination. Prior large-world save-latency findings are not addressed by this UI/index change.
