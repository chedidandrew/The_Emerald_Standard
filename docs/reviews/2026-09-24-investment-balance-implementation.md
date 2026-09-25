# Investment balance implementation — beta.54 (2026-09-24)

## Scope

Targeted game-economy balance, not real-world investment advice. Operating profiles and
browser filters are included; evolving business maturity and company-size news remain later
work. Profiles are stable operating identities, not live market-cap rankings or guaranteed
return tiers. Existing independent volatility and sector/specialist behavior remain.

- Established companies receive revised operating-growth assumptions, with stronger
  recovery opportunities for rail, mining, spice trading and laboratories.
- AURM retains negative market exposure; its fundamental adjustment and extra recession
  benefit are reduced. No extra arbitrary loss mechanism was added.
- VILX uses separate company float and index basket quantities. New-world capital units
  are 10,000 / 5,000 / 1,500 for large / medium / small operating profiles.
  Annual capitalization reviews cap a constituent at 20%; weights drift between reviews.
  Rebalancing preserves the current quote through divisor adjustment and never changes
  company float or player holdings.
- New loans use continuous full-term hazard exposure (no 180-day stress cap), 32 simulated
  borrowers in four shared sector groups, and correlated borrower/sector outcome factors.
  Partial/full-default severity proportions are unchanged. New 365-day base quoted yield
  is 13% rather than 15%, before existing regime premiums.
- Format 42 saves each loan's risk version and borrower. Old contracts retain version zero,
  their locked rate and the unchanged historical resolver. Migration preserves quotes,
  historical prices, holdings, cost basis and the existing VILX basket. First index review
  waits a full economic year after adoption.
- Savings, CDs, TREA, commodities, VCIX, FISH and VENT formulas are unchanged.

## Executed stock comparison

Fresh baseline and final candidate: identical seeds 0–63, 30 economic years each.
The checked-in InvestmentBalanceStudy calls actual EconomyState.advanceOneDay with all
80 intraday slots, event cooldowns, split-adjusted account holdings and purchase/sale spreads.
All assets receive independent hypothetical 1,000-emerald starting allocations. No village
development is populated. Horizons 1, 3, 5, 10 and 30 are emitted, with maximum drawdown.
No production save is used. Baseline classes were compiled before any financial edits.

The first 16-seed candidate left some established businesses disadvantaged. The 64-seed
candidate exposed remaining mining underperformance and AURM advantage, prompting a second
operating-assumption adjustment. Final results below are a paired rerun, not values inferred
from the configured growth targets. The baseline reproduces the earlier beta.49 30-year
stock medians. Calibration seeds are not an independent holdout, and these medians are not
pass/fail guarantees for individual worlds.

| Asset | Baseline median CAGR | Final median CAGR | Final sampled p10–p90 CAGR | Median maximum drawdown |
| --- | ---: | ---: | ---: | ---: |
| AURM | 8.42% | 5.60% | 0.92% to 9.82% | 50.1% |
| BRCK | 2.87% | 4.36% | -5.07% to 13.33% | 85.8% |
| DPMN | -4.25% | 3.72% | -2.20% to 11.57% | 80.0% |
| ENDR | 1.53% | 4.96% | -2.66% to 11.96% | 75.5% |
| FISH | 4.36% | 4.36% | -0.20% to 7.85% | 61.6% |
| GLDH | 1.05% | 3.97% | 0.47% to 9.05% | 56.5% |
| IRNG | 3.99% | 6.00% | 1.29% to 9.62% | 55.8% |
| MCRT | -5.87% | 4.89% | -0.74% to 11.09% | 66.3% |
| NSPC | -1.46% | 4.64% | -4.66% to 13.05% | 82.6% |
| POTN | -3.26% | 4.34% | -0.69% to 11.56% | 71.0% |
| RSDN | 3.85% | 5.35% | -3.06% to 12.74% | 79.9% |
| TREA | 3.29% | 3.29% | 2.77% to 4.02% | 6.6% |
| VCIX | 3.54% | 3.54% | 3.02% to 4.14% | 44.3% |
| VENT | -2.05% | -2.05% | -13.97% to 13.55% | 94.2% |
| VILX | 5.15% | 7.06% | 3.87% to 12.10% | 46.4% |

Percentiles above use sorted sample positions 6 and 57 of 64 (nearest sample), not interpolated
confidence intervals. Drawdowns are peak-to-trough over the whole 30-year path. TREA and
all commodity/VCIX output paths are unchanged in this paired run. Final annual review weights
are at most 20%; this is not a continuous intrayear weight ceiling.

Raw local outputs: build/investment-baseline64.csv and build/investment-release.csv.
Research runner: common/src/test/java/com/chedidandrew/emeraldstandard/core/InvestmentBalanceStudy.java.

A final 100-year check on seeds 0–15 also completed using the final stock formulas:
median CAGR VILX 7.55%, AURM 5.85%, DPMN 6.03%, MCRT 5.08%, NSPC 3.88%,
POTN 5.60%, VENT -3.40%. This smaller long-horizon sample is not the old 64-world
century study and should not be presented as one.

Output SHA-256 hashes:

- Baseline64: `87de55a29980c28b6e940312d15a82e4438102321988fc70891b65c1096688c7`
- Final64: `537d1ea019636e21bcae6d26a863c51f199eb568e4af6b8832d69ef76dca581e`
- Final century: `4b39c3911b9f3c10dcd2de27688e206ee228a64a5f24e0305d0ca9dbae52d134`
- Lending: `dc7287b0a37bc8ebd09c5e4d5b98aa7ada2df27b86eff69dcac8b4932414b1b7`

## Lending comparison

LendingBalanceStudy compares versions 0 and 1 over 128 seeds, four fixed account identities,
four terms and 30 years, renewing eight equal positions at common maturity. It uses production
regime transitions, rates, stress increments and resolution. Outstanding loans are marked at
accrued book value; this is not a liquidity guarantee or a continuously risk-priced quote.
The runner isolates underlying product economics: it does not impose opening amount caps,
whole-emerald rounding, transaction cooldowns or automatic rollover on the actual game.

| Term | Historical median CAGR | New median CAGR |
| --- | ---: | ---: |
| 30 days | 6.22% | 6.42% |
| 90 days | 7.12% | 7.29% |
| 180 days | 7.71% | 7.76% |
| 365 days | 12.14% | 8.82% |

Raw local output: build/lending-release.csv. Outcomes share yearly borrower/sector factors;
they do not represent actual tracked villager entities. Splitting positions is no longer
equivalent to eight independently modeled borrowers.

## Verification and handbook

New regression coverage checks legacy contract settlement against the old resolver, current
contract persistence, missing risk-field rejection, saved-price/holding/history preservation,
copy/reload determinism, annual risk exposure, shared borrower losses, concentration limits,
value-neutral rebalancing and company-float independence. Existing VILX and browser tests
were updated for deliberate starting company sizes and size filters.

The full common suite passed. The final handbook wording additionally passed its focused
resource/mechanics checks. Full Fabric dedicated-server integration passed all 98 fixtures;
opt-in heavyweight construction fixtures emit tick-lag warnings, so this is not a gameplay
performance benchmark. No player profile or live world was modified.
Both final Fabric and NeoForge beta.54 builds passed. Packaged JAR inspection confirmed
the company-profile class and updated migration/rebalance handbook text in both loaders.
Changes remain local and uncommitted; no GitHub push or live-profile installation occurred.

Guided Markets, Investments, Lending and Growth & Risk chapters and compact Investments,
Lending and Growth & Risk pages were reviewed and updated. Existing handbook routes remain
correct; recipes and creative-only items are unchanged. The compact trading page retains
numeric-limit, spread and tiny-holding guidance.

Limitations: neutral-village baseline and initial entry date only in the full-state study.
This is not yet an exhaustive prosperous/struggling-village or multiple-entry-date study.
Operating profiles do not dynamically mature, and no new size funds or business-maturity
news is included. Those are explicitly later work, not implemented features.
