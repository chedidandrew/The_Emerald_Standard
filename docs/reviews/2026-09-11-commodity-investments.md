# Commodity investments — 2026-09-11

Unreleased 0.4.0-beta.5 working-tree candidate. No installed JAR, live world, Git commit
or remote repository was changed.

## Implemented

- Eight cash-settled listings in the existing Market cycle: GOLD, IRON, COAL, DIAM, COPR,
  RDST, LAPS and NETH. Total catalog: 22. Visible Stock / Index / Fund / Commodity labels.
- Commodity-specific units and payment previews, existing combined cash/inventory funding,
  normal spreads and portfolio accounting. No physical commodity delivery/redemption.
- One authoritative underlying quote for commodity investments and supported Trade items.
  New markets have differentiated industry/fuel/automation/enchanting sensitivities and
  seeded volatility/events. Existing company/index/fund and legacy commodity formulas remain.
- Disjoint investment button IDs (200+) avoid collisions with resource selections (30+).
- Format 30 migration preserves existing balances, positions, cost basis, clock and prices.
  New listings start today at the current underlying quote. Synthetic history remains out
  of scope. New short commodity histories do not shorten the existing emerald-block chart.
- Current-format missing fields and inconsistent investment/underlying quotes are rejected.

## Verification

| Check | Result |
| --- | --- |
| Common regression suite | PASS — 79 result groups |
| Two-year underlying quote/history equality; defensive vs cyclical/event differences | PASS |
| Legacy format-29 upgrade, unchanged account/stock/Trade history, restart determinism | PASS |
| Missing fields/mismatched quotes; negative, NaN, infinite and excessive requests | PASS |
| Fractional units, quarter/full sales, basis persistence, repeated-sale rejection and spread | PASS |
| Fabric + NeoForge assemble, menu packet codec and reader/settings checks | PASS |
| Both real dedicated-server integration harnesses | PASS |
| Eight inventory-funded commodities, exact emerald debit, cooldown, resource selector isolation | PASS on both loaders |
| Commodity sale/restart, no physical resource consumption or item creation | PASS on both loaders |
| Fabric real-client type-label/behavior text fit, 22 listings at GUI scales 2 and 4 | PASS — 44 captures, normal shutdown |
| Packaged playable/source JAR checks and whitespace | PASS |

The Gold screen at GUI scale 4 was visually inspected. Existing ellipsis behavior protects
the overall title/footer at high GUI scale; the Commodity heading, quote, units and actions
are visible without overlap.

The client log is **not error-free**: it contains the pre-existing Windows OSHI/Perflib 009
counter-name ERROR and a Mojang profile lookup timeout warning. Neither prevented the GUI
assertions/captures or normal shutdown. No Windows registry settings were changed. Dedicated
server startup intentionally performs expensive opt-in catalog tests and logs startup lag;
these are not normal gameplay benchmarks. The full two-process general client smoke suite
was not rerun; the focused commodity renderer was used.

Logs: `build/commodity-common.log`, `build/commodity-{fabric,neoforge}-build.log`,
`build/commodity-{fabric,neoforge}-server.log`, `build/commodity-client.log`.
Detailed dedicated logs also remain under `build/server-smoke/`.
Screenshots: `build/client-smoke/commodities-20260911-1033/screenshots/tes-reader-ci/`.

## Test JARs

Back up the **entire world** first. Format 30 cannot be read by older builds.
Use matching client/server JARs. Install only the matching loader's playable JAR, not both
loaders or a sources JAR. These replace earlier beta.5 candidates with the same filenames.

- Fabric: `fabric/build/libs/the-emerald-standard-fabric-0.4.0-beta.5.jar`
  SHA-256: `cab05627256cf82fc415238b6786aeb8ad0b041643f458d02f8526498d368752`
- NeoForge: `neoforge/build/libs/the-emerald-standard-neoforge-0.4.0-beta.5.jar`
  SHA-256: `1ae718f1bceca2797c10d1e5b5067701d96b20eaf52af701bea96b934c978d62`

No blanket guarantee against all hostile mods, corruption or crashes is implied. Existing
inventory receipt/recovery protections and their regression coverage are reused.
