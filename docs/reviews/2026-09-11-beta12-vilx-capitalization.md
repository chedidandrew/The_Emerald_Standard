# Beta 12: VILX capitalization-weighted basket

Date: 2026-09-11

## Behavior and migration

VILX now tracks all 12 company stocks, excluding commodities, VCIX and the Treasury Fund. Each company starts with equal simulated capitalization (about 8.33%). Saved simulated shares outstanding stay fixed except for stock splits, so weights drift with prices. Player holdings never affect weights.

The quote equals total constituent capitalization divided by a persistent divisor. There is no independent VILX price draw, second news/village shock, or upside limiter. Company price floors apply before basket valuation. All constituents declining makes VILX decline over the same interval, accounting for splits; a large winner can outweigh smaller losers.

The indicative fundamental-growth target is the current capitalization-weighted average of company targets. It remains within their 1–15% range but is not independently sampled or applied again. Actual returns are not guaranteed. Underlying company calibration is unchanged.

Save format advances from 33 to 34. Migration establishes equal capitalization at upgrade quotes and anchors the divisor to preserve the existing VILX quote. Cash, units, cost basis and historical prices remain unchanged. Pre-upgrade history still reflects the old method.

Simulated shares, divisor and migration day persist and copy with transaction state. Reloading never rebalances or reinitializes the basket. Company/index denomination splits adjust float/divisor together with existing holdings/history normalization. Missing, non-finite, non-positive, unexpected or inconsistent current-format basket data is rejected instead of silently reset.

Back up worlds before upgrading. Format 34 saves are not intended for older mod builds. Use matching client/server versions. No installed mod, live-world save, account or configuration was changed.

## Verification

- All 84 common regression programs passed: `build/beta12-common.log`.
- Fabric and NeoForge builds passed: `build/beta12-fabric-build.log`, `build/beta12-neoforge-build.log`.
- Both native client priority checks passed at GUI scales 2 and 4, including real-font handbook checks at 80% and 120% and all 57 compact pages: `build/beta12-fabric-client.log`, `build/beta12-neoforge-client.log`.
- Both dedicated-server smoke tests passed in isolated worlds: `build/beta12-fabric-server.log`, `build/beta12-neoforge-server.log`. Coverage includes native banking/inventory spending, restart/replay, news and Banker integration.
- Candidate identity, loader version parity and production-source fingerprint verification passed.
- `git diff --check` passed.

Focused coverage includes all 12 constituents, excluded assets, equal initialization, drift/concentration, up/down consistency, post-floor quotes, no independent index shocks/cap, weighted targets, 12 seeds over 730 days, split neutrality, wealth/cost-basis preservation, deterministic copy/reload, format-33 migration, malformed format-34 rejection and purchase/restart/sale replay safety.

## Simulation results and limits

The broad calibration test ran 250 seeded worlds for 75 years each (18,750 annual observations). VILX mean annualized growth was 5.85%; 5th percentile 2.94%, median 5.66%, 95th percentile 8.89%. Losses occurred in 36.2% of individual years. Observed individual-year extremes were -66.6% and +357.3%.

These are simulation outcomes, not forecasts or promises. Removing the independent limiter exposes rare large constituent gains and concentration effects. No company retuning or replacement index cap was introduced, because either would exceed the approved tracking change. The positive fundamental target is not a floor on realized returns.

## Handbook accuracy review

Updated and reviewed the long investment and growth-target chapters, compact investment page and index/market help. They explain the 12 stocks, equal initial capitalization, drifting weights, simulated float, concentration, splits, inherited targets, migration continuity and non-guaranteed returns. Portfolio ownership remains distinct from physical village support. Recipes and creative-only spawn-egg rules are unchanged.

The README, investment behavior guide, economy guide, Emerald Wire guide and changelog are aligned. Historical validation reports retain their historical descriptions.

## Built artifacts

- Fabric: `fabric/build/libs/the-emerald-standard-fabric-0.4.0-beta.12.jar`
  - SHA-256: `9E8C1DC53597503D3DB18605B5AA34B00B75E4C227B21175A973E8E6D1FB3410`
- NeoForge: `neoforge/build/libs/the-emerald-standard-neoforge-0.4.0-beta.12.jar`
  - SHA-256: `FA17D5231A38A7068E8546AB3B576D3B1E434F8D24276288C2B658398DFE2FCE`
- Production source SHA-256: `0047a3ef49062d4c0b84d2ab3f86073741d21f2f557c470efc5286005fb0fabe`

This is a separate development candidate, not an installation or release. No commit, push or publication was performed. Intraday charts, additional chart periods and census UI changes remain untouched.
