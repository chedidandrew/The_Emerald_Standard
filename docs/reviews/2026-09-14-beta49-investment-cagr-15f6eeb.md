# Beta.49 long-term investment CAGR experiment

## Scope and identity

Andrew asked for the simulated long-term CAGR of all investments. This report records an executed, multi-seed experiment rather than treating configured fundamental growth targets as investor returns.

- Gameplay source: `15f6eeb0c91cda5ab3f99d1ccd907255a0a5dad2`, version `0.4.0-beta.49`.
- Development branch before this documentation change: `0d8b3144fffaac8859d1d5f8eae1256c49481596`, containing the same gameplay code and the preceding review document. Branch identity was checked again before writing.
- Source artifact: Fabric Actions artifact `10372873251` from run `34906869489`.
- Artifact ZIP SHA-256: `31c91f855648a927024f67d51057595e2e20728d717cf53292a039f4577514d0`.
- Source JAR SHA-256: `7569904a07ebc18a088fb6e183f839d5f6ae0992831582fad93126731ec743fc`.
- Independent Git blob checks matched EconomyEngine, InvestmentGrowth, EconomyState and EconomySelfTest at the exact gameplay revision.

The unchanged loader-neutral production classes were compiled using OpenJDK 21.0.11 with `--release 21`. The Minecraft distribution still requires Java 25; this experiment did not launch Minecraft, modify a playable JAR, load a player save, or rerun the entire regression suite.

## Method

64 fresh worlds used seeds **0 through 63**, each advanced for **100 economic years**, with observations after 10, 30 and 100 years. Every year contains 365 economic days. The experiment called the actual `EconomyState.advanceOneDay()` implementation, retaining all **80 intraday slots**, market regimes, extraordinary events and cooldowns, commodity reference growth, price limits, share splits and daily account settlement.

That is 2,336,000 economic-day advances and 186,880,000 intraday steps. No villages were populated, so the study isolates a neutral-village baseline rather than inventing a particular player-developed economy. It is not a forecast for Andrew's existing world seed or village configuration.

Each independent investment strategy starts with **1,000 emeralds**, without later contributions or withdrawals. Market holdings buy once and hold. Their entry price includes the production 0.25% purchase spread and final valuation includes the 0.25% sale spread. Final sale values are before micro-emerald rounding. Actual account-held share quantities are retained so share splits do not falsely appear as investment losses. No separate dividends are invented; TREA's income already accrues in its share price.

Savings compounds automatically through the production account-settlement path. Each CD term is tested as a separate continuously renewed position. Lending is tested both as one active loan and as **eight equal loans of the same term**, collecting and rebalancing surviving value at each common maturity. New positions use the production rate functions, supported terms, serial rules and one-million-emerald per-position opening limit. The actual account settlement, micro-emerald rounding, stress accumulation and `resolveLoan` logic determine outcomes. Whole-emerald principal is reinvested, while cash remainders and capital above opening limits remain idle.

The research driver opens and renews account records directly, mirroring the supported EconomyService operations without disk writes, UI clicks or transaction cooldowns. It does not replace the daily return, interest, default or split formulas. Loan accounts have fixed, strategy-specific UUIDs produced from `TES-CAGR-BETA49:` plus the strategy label; no identities were selected for favorable outcomes. Multiple strategies share each world's price/regime path but use separate hypothetical capital allocations.

At a reporting horizon, an outstanding CD or loan is marked at accrued book value. This is not an assertion that it can be liquidated early at that value or that a remaining loan term cannot default. Same-day reinvestment is a modeled strategy, not an automatic-rollover feature added to the mod.

CAGR is `(ending value / starting value)^(1 / economic years) - 1`. Returns are nominal, emerald-denominated and not purchasing-power-adjusted. A table's central value is the **median of the 64 per-world CAGRs**, not the CAGR of average terminal wealth. The middle-80% range uses linearly interpolated sample 10th and 90th percentiles. It is not a confidence interval or a bound on all possible worlds.

## Market investments

All numbers below are annualized percentages. The range is across 100-year outcomes, not the range of individual calendar-year returns.

| Investment | 30-year median | 100-year median | 100-year p10 to p90 |
| --- | ---: | ---: | ---: |
| VILX: Villager Stock Exchange Index | 5.15% | 6.44% | 4.57% to 8.11% |
| VCIX: Villager Commodity Index | 3.54% | 3.70% | 3.54% to 3.95% |
| TREA: Village Treasury Fund | 3.29% | 3.26% | 2.92% to 3.60% |
| RSDN: Redstone Dynamics | 3.85% | 3.99% | -0.46% to 8.94% |
| DPMN: Deepdelve Mining | -4.25% | -3.30% | -7.92% to 0.85% |
| NSPC: Nether Spice Company | -1.46% | -2.60% | -6.28% to 2.28% |
| ENDR: Ender Freight & Logistics | 1.53% | 2.42% | -1.74% to 4.94% |
| GLDH: Golden Harvest Cooperative | 1.05% | 1.81% | -0.11% to 3.80% |
| POTN: Potionworks Laboratories | -3.26% | -2.55% | -5.82% to 0.09% |
| IRNG: Iron Golem Security | 3.99% | 3.57% | 1.21% to 6.57% |
| MCRT: Minecart Transit | -5.87% | -4.70% | -6.94% to -1.68% |
| AURM: Aurum Reserve Company | 8.42% | 8.20% | 5.78% to 10.05% |
| BRCK: Masons & Works | 2.87% | 4.01% | -2.63% to 8.93% |
| FISH: Tidewater Fisheries | 4.36% | 3.61% | 0.74% to 5.94% |
| VENT: Frontier Expedition Ventures | -2.05% | -0.92% | -6.90% to 4.82% |
| GOLD: Gold | 3.77% | 3.80% | 3.48% to 4.00% |
| IRON: Iron | 3.73% | 3.78% | 3.40% to 4.12% |
| COAL: Coal | 2.46% | 3.01% | 2.61% to 3.47% |
| DIAM: Diamond | 2.74% | 3.00% | 2.68% to 3.43% |
| COPR: Copper | 4.19% | 4.11% | 3.73% to 4.54% |
| RDST: Redstone | 4.00% | 4.10% | 3.66% to 4.53% |
| LAPS: Lapis Lazuli | 3.41% | 3.55% | 3.25% to 3.92% |
| NETH: Netherite Scrap | 3.25% | 3.57% | 3.22% to 4.01% |

These are investment-balance returns, not income from mining, farming, generating items, or active trading. Physical resource variants are not additional separately listed securities. Village Fund contributions are discussed separately below.

## Savings and continuously renewed CDs

The possible rate column comes from the production regime and term functions. The sample CAGR accounts for changing regimes and renewal, rather than assuming one favorable opening rate persists forever.

| Product | Possible quoted annual rate | 100-year median CAGR | 100-year p10 to p90 |
| --- | ---: | ---: | ---: |
| Uninvested Bank Cash | 0% | 0.00% | 0.00% to 0.00% |
| Savings | 1.50% to 4.00% | 2.93% | 2.85% to 2.99% |
| 30-day CD | 2.50% to 5.00% | 3.93% | 3.85% to 3.99% |
| 90-day CD | 3.00% to 5.50% | 4.43% | 4.35% to 4.49% |
| 180-day CD | 3.40% to 5.90% | 4.82% | 4.75% to 4.88% |
| 365-day CD | 3.80% to 6.30% | 5.23% | 5.15% to 5.30% |

The production code stops accruing a CD after maturity. The figures assume renewal without an idle delay and do not exercise early closure. Early CD closure forfeits the interest and applies the existing principal penalty, so its result would differ.

## Lending: quoted yield is not realized CAGR

The quoted yield is the contractual annual rate before possible partial or full default. The following realized results use eight equal same-term loans, reset to equal whole-emerald principal after each maturity. There is no additional capital after a loss.

| Term | Possible quoted annual yield | 30-year median CAGR | 100-year median CAGR | 100-year p10 to p90 |
| --- | ---: | ---: | ---: | ---: |
| 30 days | 10.00% to 16.50% | 6.47% | 6.19% | 5.64% to 6.99% |
| 90 days | 11.00% to 17.50% | 7.37% | 7.14% | 6.38% to 7.89% |
| 180 days | 11.50% to 18.00% | 7.78% | 7.70% | 6.83% to 8.26% |
| 365 days | 14.50% to 21.00% | 12.15% | 10.82% | 10.52% to 11.01% |

Every 100-year eight-loan/365-day portfolio eventually reached the maximum one-million-emerald opening amount per loan. Excess capital remained in cash. That is one reason its 100-year CAGR is lower than its 30-year CAGR. This is a capital-dependent strategy result, not an intrinsic 10.82% product APY, and it cannot be extrapolated to infinite time.

### One-loan comparison

Concentrating into one loan at a time produces a very different distribution. A full default can remove the whole amount in that position. Small cash remainders or cash retained after reaching the opening cap can remain outside it.

| One-active-loan strategy | 100-year median CAGR | Worlds ending below initial capital |
| --- | ---: | ---: |
| 30 days | -6.95% | 39/64, 60.94% |
| 90 days | -6.86% | 43/64, 67.19% |
| 180 days | -6.86% | 44/64, 68.75% |
| 365 days | 8.73% | 29/64, 45.31% |

All of those losing paths finished below 1% of initial capital in this sample. A negative annualized value here can represent a near-total loss followed by idle fractional cash, not a smooth small loss every year. The positive median for the one-loan 365-day case does not remove its substantial bad-outcome tail. These percentages depend on the explicit strategy and fixed account IDs, and are not universal loss probabilities.

## Interpretation and balance observations

1. The earlier 5.00% VILX example was a single seed, not a universal target. Seed 42 in this study has 4.996950% net annualized over 100 years, consistent with the earlier rounded result after the one-time spreads. The multi-seed median is 6.44%.
2. Positive fundamental targets do not guarantee positive investor growth. InvestmentGrowth supplies seeded target paths, while volatility, market regimes, specialist behavior and event shocks also affect quotes. DPMN, NSPC, POTN, MCRT and VENT have negative median 100-year CAGRs in this baseline. MCRT finished below its purchase value in all 64 tested worlds; that is an empirical sample outcome, not a theorem about all seeds.
3. AURM is the strongest median company stock in this experiment. Its economic behavior is a reserve-company share, not the same investment as physical/cash-settled GOLD.
4. VILX begins at equal company capitalization but does not continually rebalance to equal weights. Across these runs, its median largest-constituent weight is 23.53% at 10 years, 45.63% at 30 years and 86.26% at 100 years. The median AURM weight at 100 years is 78.17%; AURM is the largest constituent in 41 of 64 worlds. Long-horizon VILX behavior therefore increasingly depends on its winners, not the median individual-stock return.
5. Smooth-looking long-horizon CAGR ranges do not imply small interim losses. The median observed maximum drawdown over the 100-year path was 61.63% for VILX, 49.24% for VCIX and 7.84% for TREA. These drawdowns use daily marks, include the entry cost in the initial-capital peak, and exclude the hypothetical final sale spread; intraday extremes can be worse. VILX had a negative 10-year terminal return in 12 of 64 worlds despite all of the sampled 100-year terminal returns being positive.
6. The current balance favors diversified long-term lending and AURM more strongly than many high-risk growth stocks in this experiment. This report records that result without changing rates, risk or economic mechanics.

## Village Fund contributions

Direct Grants, Endowments and Project Sponsorships are irrevocable village contributions, not personally redeemable investment accounts. They have no personal investment CAGR comparable to the tables. Endowment principal is village-owned and its configured payout benefits the village, not the donor. Do not confuse that system with the investable TREA Treasury Fund.

The experiment did not simulate a particular donation-funded town, the value of newly created buildings, physical farming/mining income, or changes in buying power. There is no single comparable personal CAGR for those activities without an explicit valuation/cost model.

## Validation, reproducibility and limitations

All 64 seeds completed. The output contains **7,104 records**: 37 products/strategies, three horizons, 64 worlds. The indices were independently checked against the arithmetic mean of their equal-initial-capital constituent holding multiples at every recorded horizon. Maximum relative difference was approximately `5.59e-12`, consistent with exported decimal rounding. This confirms that the reported index comparisons retain the split-adjusted investor value rather than mistaking a share split for a loss.

The review-only driver is named `LongTermCagrStudy.java`. Its SHA-256 is `7fcd90223e7bc3df8dd0ce7c23149d4e3450078be32449da1d68a10f36ea41dd`. The machine-readable `simulation_results.json`, including full per-world results and aggregate statistics, has SHA-256 `12a3af70c9cba5e804607cd809936492327257f761ba1cea63a821d908267e81`.

The session handoff includes the driver, raw per-seed TSV files, JSON, aggregation script, run log and reproduction instructions. The driver should be compiled against the exact source revision above. It calls production daily settlement; its only investment decisions are the explicitly specified opening, maturity collection and renewal actions. It does not perform optimized timing, buy the past winner, select advantageous seeds, inject villages or tune growth rates.

A 64-world finite-horizon sample is an estimate, not a population expectation or return guarantee. Timing, seed, lending account identity, village effects, reinvestment delays, player actions, configuration and future code changes can change results. Commodity/reference ceilings, minimum prices and per-position limits also make infinite-horizon extrapolation inappropriate. This is not a financial safety test or a claim that all possible account/market paths have been validated.

## Handbook and change record

AGENTS.md and the packaged guidance on investments, account value, term products, quotes and village gifts were reviewed against the relevant production paths. No player-facing behavior, rates, settings, handbook content, recipes or save format changed. No unrelated handbook edit was made merely to accompany an experiment.

This commit adds only this analysis and evidence record. It modifies no production code or test, merges no branch, publishes no release and marks no manual test passed. Previous reviews remain intact.