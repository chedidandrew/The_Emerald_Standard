# Beta.15 live market and forward-only clock review

Date: 2026-09-12. Local candidate only; no player save was opened or edited, and no mod was installed or published.

## Implemented behavior

- Eighty actual quote steps per economic day (normally one every 15 seconds). Orders, both loaders, every desk, individual charts and comparisons share server prices.
- Today live defaults, optional recorded Yesterday, then the exact cycle: 1 Day, 30 Days, 90 Days, 1 Year, 3 Years, 5 Years, 10 Years, All.
- Actual dated points, previous-close reference, signed daily change, green/red individual curves, blue solid/amber dashed normalized comparison curves. Unobserved intraday time remains blank.
- Two detailed session tapes, 3,651 daily closes, bounded progressively coarsened older archive. Format-35 migration preserves quotes, holdings and retained histories, starts the tape at the observed upgrade time, and never invents old observations.
- Drift scales by elapsed time; diffusion by its square root. Rare daily jumps happen once. Stock and commodity baskets derive from their constituents every step. Savings, maturities, village simulation and news still settle daily.
- Direct command hooks capture numeric set, named day/night and add commands individually, including multiple commands in one tick. Backward commands advance to the next requested sky phase: economic Day 23 midnight plus /time set 0 becomes Day 24 dawn. Same numeric time or repeated same-phase named marker adds no economic day.
- Sub-quote wall-clock jitter cannot manufacture another day during alignment. Passive backward observations rebase without replay. Sleep and forward jumps are credited once. Large catch-up is bounded and blocks money mutations until current.
- Display cache includes the intraday slot even when prices happen to remain unchanged. Invalid tapes, false openings, missing middle observations and archive overlap fail validation.

## Validation

The common regression script runs 86 Java test entry points plus loader-version/wrapper checks. It covers live orders, slot timing, phase resets, same-tick commands, sleep, reload/batch determinism, partial migration, history transport, archive persistence, numeric bounds, all prior transaction and construction protections, and malformed data.

The ordinary full-state stochastic soak is now 12 worlds x 12 years because each day executes 80 actual market steps. The former 250 worlds x 75 years remains opt-in with `-Dthe_emerald_standard.extendedMarketSoak=true`; that extended soak was not run for this candidate. The 20,000-observation focused market-factor comparison measured a daily log-variance ratio of 0.9769 and mean daily log-return difference of -0.0000801 against daily stepping. These are regression/calibration checks, not a promised return distribution.

The default 12-world sample produced VILX mean CAGR 4.71%, 43.1% negative individual years, and individual annual outcomes from -39.3% to +91.3%. This small deterministic sample must not be advertised as an expected investor return or a bound on future outcomes.

Dedicated-server smoke tests exercise real Minecraft command dispatch and both loaders' injected hooks, plus the native 17-page ItemStack chart codec. They also rerun transaction/inventory, construction, settler, protection, death/recovery, news and configuration integration checks in disposable worlds.

Focused client fixtures cover browser, compare, Today, Yesterday, Town and progress reports at GUI scales 2 and 4. The chart fixture uses 400 genuinely simulated daily closes and 48 intraday slots, not synthetic animation. The compare fixture cycles all eight ranges. Native real-font checks cover all 60 compact handbook pages and 67 guided sections across 16 chapters, including long-form scrolling.

A visual review caught the daily-change caption overlapping the session toggle at a large GUI scale; its one-day label was shortened and bounded. Fixture-only displayed day/price metadata was corrected to agree with its actual history. Yesterday now labels its historical return inside the chart and the current tradable quote separately as Now, avoiding a mixed-date price/return pair.

## Final verified candidate

All checks below passed on the final source inputs:

| Check | Result |
| --- | --- |
| Common regressions | 86 Java entry points, version parity and wrapper checks passed; exit 0 |
| Fabric | Build + focused native client passed; final dedicated-server smoke passed, exit 0 |
| NeoForge | Build + focused native client passed; final dedicated-server smoke passed, exit 0 |
| Native commands/transport | Both loaders passed actual numeric/named/add command hooks and real 17-page ItemStack round trips (18,723 / 18,691 bytes in these fixtures) |
| Packaged content | All 135 required entries present in both candidate JARs; older candidate JARs preserved |
| Build identity | Packaged versions, current source fingerprint and cross-loader parity passed |
| Harness | Updated 60-page strict-log fixtures pass; missing checks/restart/error/nonzero-exit cases still reject |
| Final visual review | Selected Today, Yesterday and comparison screenshots inspected across GUI scales 2 and 4; native fixtures exercised every mode at both scales. Final Yesterday clearly separates historic return from Now quote |
| Whitespace | git diff --check passed |

Common log: `build/beta15-common-final.log`.
Final build/client logs: `build/beta15-fabric-verified-build.log`, `build/beta15-neoforge-verified-build.log`.
Final server wrapper logs: `build/beta15-fabric-verified-server.log`, `build/beta15-neoforge-verified-server.log`.
Screenshots: `build/beta15-{fabric,neoforge}-client-verified/screenshots/tes-reader-ci/`.

The dedicated smoke harness intentionally terminates its uniquely tagged test process after successful integration markers; a resulting Gradle-daemon-disappearance tail is expected from that controlled cleanup. The wrapper success marker and exit status were checked. Native Windows OSHI performance-counter warnings were present; no mod test failure was hidden by them.

Shared source SHA-256:
`7dabd0b5dc061330acca4fe38d2d557a0e91960321e3e7cef683dbef1b65a69f`

| Candidate | JAR SHA-256 |
| --- | --- |
| `fabric/build/libs/the-emerald-standard-fabric-0.4.0-beta.15.jar` | `B98E0EB83A0DCCEFD6B45BBD39FA06E8F08BD5C4C63CC3A887D0E4E21CE292DE` |
| `neoforge/build/libs/the-emerald-standard-neoforge-0.4.0-beta.15.jar` | `8A8570A1088832BE3120132E6292FFFEEC10E18666DE011887935B7B31BE6074` |

These are local beta candidates, not a certification of the extended human-play matrix or a published release.

## Handbook review

Reviewed and updated the long-form Markets and Getting started clock explanations against the code. Added three guided sections for live sessions, ranges/history and command/sleep behavior, with three appended compact fallback pages preserving existing links. Updated range tooltips and technical economy/configuration notes. No recipe or Creative-egg policy changed.

## Limitations and upgrade guidance

- Back up the complete world before format 36. Use matching beta.15 client/server builds; do not downgrade a migrated world.
- Quotes advance on economic time, not merely because a screen is open. Server lag changes real-time cadence; enabled wall-time and accelerated sky time can also advance economic time.
- Today/Yesterday intraday views are investment views. Home net-worth and physical Trade charts remain daily histories.
- All uses sampled older closes, not unlimited full-resolution storage. Previously pruned history cannot be recovered by upgrading.
- The 25,000-day absolute pending cap is retained. Extremely large commands are capped; catch-up takes bounded batches rather than freezing the server in one unbounded loop.
- Repeatedly resetting a changing sky with a command block intentionally advances economic time; repeating the exact same phase does not.
- Transaction durability and normal save/reload are tested. Forced termination can still lose unsaved passive progress since the last successful save; this is not a promise of crash-atomic saves shared with Minecraft's world clock.
