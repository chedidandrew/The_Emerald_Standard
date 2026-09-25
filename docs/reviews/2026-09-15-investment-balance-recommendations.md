# Investment balance recommendations: 2026-09-15

## Decision and scope

Andrew asked whether the measured investment returns should change for greater realism or remain as implemented. Recommendation: targeted rebalancing, not a blanket increase and not guaranteed positive returns for every investment. Preserve cash, Savings, the CD ladder, most commodity behavior and the general Treasury Fund role. Prioritize structural stock disadvantages, AURM's repeatable advantage, lending's term/risk structure, and VILX concentration.

This is an advisory design record. None of the proposed ranges below has been implemented or validated by a new parameter search. They are proposed game calibration goals, not historical constants, promised investor returns, confidence intervals or configuration values to copy literally into the model.

## Evidence checked

- Prior executed study: `docs/reviews/2026-09-14-beta49-investment-cagr-15f6eeb.md`, gameplay `15f6eeb0c91cda5ab3f99d1ccd907255a0a5dad2`, beta.49.
- Reopened the supplied `The_Emerald_Standard_beta49_CAGR_study.zip`, checked archive integrity, read the driver, methodology, aggregation script, execution log and summaries, and parsed every raw TSV file.
- All 7,104 raw records matched `simulation_results.json`; every reported CAGR matched its terminal multiple and horizon within 1e-9. JSON SHA-256 remains `12a3af70c9cba5e804607cd809936492327257f761ba1cea63a821d908267e81`.
- The remote development branch had advanced to beta.50 commit `60ad69e006de80223e0ab90bc2afd38436358631`. Live reads of EconomyEngine, InvestmentGrowth and StockIndex had the same Git blob hashes as their beta.49 source-archive counterparts: `8b9a5fa8745718945004e1edf498b5340cd9b096`, `2ad0e3511989e81afe6c5ec27a8ff97ee7097366`, and `d2205d4ed13a8ab6be681d8ba7b672f1c34b8928` respectively. The inspected beta.50 commit concerns construction recovery, route handling and Bank styles. This session did not rerun the full 64-world experiment on beta.50 and does not relabel the old observations as a new run.

The experiment is a neutral-village baseline with seeds 0-63, 1,000 emeralds per strategy, no contributions, buy-and-hold market positions, same-day whole-emerald term renewal, default resolution, spread costs, 80 intraday slots, and actual capital limits. Lending terminal book values are not independently marked market prices. Those assumptions matter to the recommendation.

## Realism versus game calibration

Historical evidence supports distinct risk/liquidity roles and the usefulness of diversification, not a universal positive CAGR for each named company. UBS's 2025 Global Investment Returns Yearbook reports equities outperforming bonds, bills and inflation over its long country histories. Bessembinder's stock-lifetime research emphasizes that relatively few companies account for aggregate stock-market wealth creation. Permanent loss-making companies and disappointing speculative investments can therefore be realistic.

The design concern here is a small permanent roster in which the same business identities tend to win or lose across worlds. Players can learn a fixed answer instead of responding to changing business conditions. A modest positive ensemble median for established, permanent sector representatives is a game-design choice, not an empirical requirement that every real stock should win.

Keep nominal emerald return separate from real purchasing-power return. The study contains no consumer-price deflator. Do not compare its 6.44% VILX CAGR directly with an inflation-adjusted real equity return, or add cash dividends to price growth without accounting for the total-return consequences. Any future price-level or dividend mechanism needs explicit accounting and its own calibration. Do not add universal inflation solely to make the outputs resemble a USD dataset.

## Proposed direction

Current figures below are the prior study's 100-year median CAGRs unless stated otherwise. Proposed bands are initial desired multi-seed long-horizon outcomes under the same benchmark, not annual floors, guarantees, or a mandate to force every seed into the band. Validate shorter horizons separately.

| Product/role | Existing study | Recommendation |
| --- | --- | --- |
| Bank Cash | 0.00% | Keep 0%; it is the spendable balance. |
| Savings | 2.93% | Keep approximately this balance; initial desired baseline 2-3.5%. |
| Renewed CDs | 3.93%, 4.43%, 4.82%, 5.23% | Keep the existing term ladder initially. |
| TREA | 3.26% | Keep roughly 3-4% with its liquidity and interest-rate risk, not forced superiority to CDs. |
| Commodity holdings / VCIX | Commodities 3.00-4.11%; VCIX 3.70% | Broadly keep; approximately 3-4.5% is a reasonable game baseline, not a historical commodity forecast. |
| VILX | 6.44% | Preserve a broad 6-8% calibration objective; derive every quote from its actual constituents, never an independent guarantee. |
| Established company roles | Several negative medians; many positive ones below 4.1% | Recalibrate structurally disadvantaged roles toward a rough 4-7% ensemble baseline; preserve overlapping distributions and bad decades. |
| High-growth company roles | RSDN 3.99%, POTN -2.55% | Consider roughly 5-9% as a trial ensemble band for established high-growth businesses, with genuinely wider outcomes. This does not require every speculative stock to share that median. |
| AURM | 8.20% | Reduce persistent mechanical advantage; initially test about 4-6% while preserving conditional defensive usefulness. |
| Diversified 30/90/180-day lending | 6.19%, 7.14%, 7.70% | Broadly preserve a 6-8% baseline while correcting term/risk consistency. |
| Diversified 365-day lending | 10.82% at 100 years; 12.15% at 30 years | Initially test 8-10% after defaults and realistic renewal, without relying on caps to suppress returns. |
| VENT | -0.92% | No automatic increase solely because its median is negative. A clearly speculative, asymmetric opportunity can have a disappointing median. Test its useful upside, loss distribution and player communication. |
| Village Fund gifts | No personal investment CAGR | Preserve their village-benefit purpose rather than invent donor redemption or interest. |

Increasing established-stock prospects will also alter VILX and portfolio correlations. These bands cannot be implemented or assessed independently.

## Priority 1: Correct persistent stock disadvantages through the active model

The prior neutral baseline produced negative 100-year medians for DPMN, NSPC, POTN, MCRT and VENT. MCRT lost nominal capital in all 64 hundred-year paths. That is not proof that all seeds or trading strategies lose, but it is strong evidence to investigate a structural disadvantage in a permanent catalog.

Audit the contributions to log return separately: company fundamentals, market regimes, market exposure, volatility, specialist effects and event jumps. For example, MCRT receives adverse rail disruption, catastrophe and credit-scare moves as well as its smaller favorable events. Check event frequency, cooldowns and the expected logarithmic effect, not only whether there is at least one positive headline.

Do not remove volatility drag indiscriminately. It is normal for compounding to differ from arithmetic average return. Clarify whether growth targets mean arithmetic expected returns, log-price drift or desired realized geometric growth. Recheck how multiplying an already variance-adjusted market log return by positive or negative beta affects the intended model. A deliberate factor model and a calibrated total-variance model must not be mixed accidentally.

An implementation warning: the inspected `assetReturn` path does not read `Asset.annualAlpha()`. Changing that catalog field alone will not change this path. Likewise, VILX's listing parameters do not replace StockIndex's constituent calculation. Use the actual production levers and prove their effect with tests.

Prefer evolving prospects, recoveries and observable sector conditions over giving a specific permanent ticker the same winning or losing status across every world. Poor individual-world outcomes must remain possible; do not force a year-end recovery or catch-up payment.

## Priority 2: Reduce AURM's structural advantage, not its identity

AURM combines beta -0.35 with a specialist recession/crash premium and favorable stress-event responses. Given the existing market regime and factor drift, those mechanisms can add to one another. This is a source-supported mechanism to test by ablation, not a measured decomposition of its 8.20% CAGR.

The study's AURM result is not low-risk cash: the median maximum drawdown across its 100-year daily-mark paths is 59.35%. It is inaccurate to describe it as a safe 8% account or strictly dominant on every risk measure. Its repeated cross-world return advantage and index dominance are the design concern.

Preserve crisis diversification and weak performance in some booms. Reduce the persistent expected advantage through measured calibration of exposure, premiums and event effects. Treat the proposed 4-6% median as an initial design target; do not hard-cap profitable worlds at 6% or guarantee a floor. Higher systematic or idiosyncratic risk does not entitle every individual asset to a higher realized return.

## Priority 3: Rework lending's term/risk structure before cutting rates blindly

The 365-day eight-loan strategy earns 12.15% median CAGR over 30 years, before the capital limit becomes the universal hundred-year constraint. Its median maximum book-value drawdown over that horizon is 15.56%, versus 50.20% for VILX. This comparison is suggestive, but loan book marks omit market repricing/illiquidity and the study assumes prompt renewal. It does not prove that the loan book is economically safer than a traded index.

The code uses default bases of 0.6%, 1.8%, 3.5% and 5.5% per 30/90/180/365-day term. The stress exposure multiplier saturates at 180 days, while the 365-day contractual yield is higher. This helps explain why long loans deserve an annualized hazard review rather than only a coupon edit. Under a constant annual default intensity h, a coherent starting model is `p(default over T days) = 1 - exp(-h*T/365)`; with changing conditions, accumulate the daily hazard. Borrower quality, recovery severity and economic stress can then modify meaningful parameters.

Default probabilities already share the world's regime/stress. Do not claim that the current loans have no common economic risk. However, the inspected resolution key distinguishes account ID, serial and opening day rather than a persistent borrower/sector exposure. Splitting one economic exposure should not masquerade as eight unrelated businesses. A lightweight common borrower/sector shock plus idiosyncratic risk can preserve useful diversification without allowing it to remove common shocks. Explicit exposure identity and persistence would need careful design; no such mechanics are implemented here.

Do not increase catastrophic wipeouts solely to lower average profit. First make term exposure, recovery, correlation and liquidity internally consistent, then adjust new-loan rate spreads modestly and rerun the study. The proposed 8-10% net diversified long-loan target is game calibration, not a claim about universal real-world private-credit returns.

A single concentrated loan can reasonably default completely. Keep clear size-at-risk communication and an accessible way to diversify genuine exposures. Players still never borrow and losses never exceed their committed capital.

## Priority 4: Decide and document what VILX represents

The existing VILX is a capitalization basket, initialized with equal capitalization and then allowed to drift. That is not a bug by itself. The prior hundred-year study has a median largest-constituent weight of 86.26%, and AURM is the largest company in 41/64 worlds.

First retest concentration after changing stock economics. For a permanent twelve-company roster marketed as a simple diversified choice, the preferred optional design is a capped-capitalization methodology: for example, no more than 20% in one company at each annual rebalance, with weights allowed to move between rebalances. A 20% threshold is a proposal specific to this small universe, not the S&P 500 rule. Capped indices are a real methodology, but are not inherently more authentic than an uncapped market index. Retaining uncapped VILX with prominent concentration disclosure is also coherent.

A cap can reduce participation in a runaway winner; it does not guarantee higher returns or eliminate common market risk. Every index quote must continue to derive from constituents. Rebalance at then-current values and adjust the divisor or equivalent self-financing quantities so unchanged underlying prices do not manufacture a jump or destroy investor value. Do not periodically force the quoted index itself to a chosen CAGR.

## Validation and migration plan for a later authorized change

1. Preserve the existing baseline and test a small set of hypotheses separately: stock log-drift/event balance, AURM mechanics, lending term model, then any index-methodology change. Retest the combination because its components interact.
2. Use at least several hundred fresh seeds and multiple lending account IDs, with held-out seeds not used for calibration. Test different market entry dates, actual interest/default resolution and split-adjusted holdings.
3. Retain 10/30/100-year outputs but add 1/3/5-year and rolling windows, median/mean CAGR, downside percentiles, drawdowns, recovery time, default clustering, cap hits, concentration and comparison with cash/CDs. A hundred-year median can hide an unfun first few hours.
4. Keep per-position limits in player-strategy tests and report when they distort growth; separately diagnose pre-cap product economics. Test one versus several genuinely distinct exposures and reinvestment delays. Reinvestment is not automatic in current gameplay.
5. Include neutral, prosperous and stressed village scenarios without optimizing the seed or ignoring loss-making paths. Include the existing vanilla-trade/resource-exchange system so monetary changes do not create an obvious conversion exploit.
6. Preserve existing cash, quantities, cost basis and historical quotes. Do not retroactively change an open CD's promised rate or an existing loan's economic contract/risk model. Version future pricing/risk regimes or provide explicit new-world/opt-in migration. Do not reroll existing loan outcomes on reload.
7. Update both handbook forms, disclosures, risk labels, tests, changelog and exact-candidate evidence together. Keep the no-player-debt and maximum-at-risk guarantees. No global inflation or dividend subsystem is required for this focused rebalance.

## External reference context

These primary sources informed distinctions, not the exact proposed game target bands:

- UBS Global Investment Returns Yearbook 2025: https://www.ubs.com/global/en/wealthmanagement/insights/2025/global-investment-returns-yearbook.html
- ASU, Hendrik Bessembinder's stock-lifetime research: https://wpcarey.asu.edu/department-finance/faculty-research/do-stocks-outperform-treasury-bills
- NYU Stern historical-return dataset, specifying stock total return including dividends and separate asset definitions: https://pages.stern.nyu.edu/adamodar/New_Home_Page/datafile/histretSP.html
- S&P Dow Jones Indices, capped-weight example: https://www.spglobal.com/spdji/en/education/article/introducing-the-sp-500-3-capped-index-tracking-us-equities-with-a-focus-on-lower-concentration/
- S&P index mathematics and divisor continuity: https://www.spglobal.com/spdji/en/methodology/article/index-mathematics-methodology/
- Basel Committee explanatory note on probability of default, loss severity and exposure as distinct inputs: https://www.bis.org/publications/explanatory-note-basel-ii-irb-risk-weight-functions

## Handbook review and change record

Read the current AGENTS.md and compared the packaged beta.49 risk-compass, investments, growth-target and Savings/CD explanations with the inspected return engine. They correctly distinguish target growth from guaranteed return. This advisory change introduces no player-facing mechanics or new promised rates, so no unrelated handbook edits were made. A later implementation must revise those explanations to match its actual behavior.

This commit adds only this recommendation and evidence record. It changes no production formula, quoted rate, risk setting, save, existing investment, test, branch relationship or release. No new Monte Carlo calibration or gameplay test is claimed. Previous studies and reviews remain intact.
