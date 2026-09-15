# Company-size investment design: 2026-09-15

## Scope and decision

Andrew asked how large, medium and small company stocks should differ, following the long-term CAGR study and investment-balance recommendations.

Recommendation: introduce meaningful company-size distinctions, but not three guaranteed return tiers. Size should influence risk, financing sensitivity and the range of plausible business outcomes. Sector, profitability, financial strength and valuation should remain separate influences. A small company is not necessarily a startup, and a large company is not a safe account.

This is an advisory record only. No company classification, share count, return formula, stock history, UI, contract or save was changed. No size-tier simulation or new parameter calibration was run.

## Current implementation checked

Remote development head at inspection: `97cf5db48dd4b7f5ee1178ae6f68315dcf9f3f5b`, following beta.50 gameplay `60ad69e006de80223e0ab90bc2afd38436358631` and the prior advisory record. Main remains the older `1ffc9bc06bef6f7203133f5c7dd0df666d9a57f3`.

Live reads at the development head:

- `EconomyEngine.java`: Asset records sector, beta, annualAlpha, idiosyncratic volatility, investment type and commodity identity. It does not declare a company-size tier. Blob `8b9a5fa8745718945004e1edf498b5340cd9b096`.
- `StockIndex.java`: simulated company float is independent of player holdings. Initialization gives each company equal starting capitalization using `100 / openingPrice`, then weights drift with prices. It does not initialize economically distinct large/mid/small companies. Blob `d2205d4ed13a8ab6be681d8ba7b672f1c34b8928`.
- `AGENTS.md`: reviewed the requirement to keep both handbook forms accurate when player-facing behavior changes.

The previously measured ticker CAGRs cannot simply be relabeled as observed large/mid/small portfolio returns. A real size model would be additional behavior, not merely a visual label on the existing risk list.

## Proposed calibration, not simulated results

Initial design bands for median nominal emerald-denominated CAGR of diversified size-segment test portfolios over 20-30 economic years, across many independent seeds:

| Segment | Trial median CAGR band | Intended player-facing character |
| --- | --- | --- |
| Large companies | 5-7% | Generally more established operations, smaller company-specific surprises, but still meaningful equity losses. |
| Medium companies | 6-8% | Established regional businesses with expansion opportunities and intermediate financial resilience. |
| Small companies | 7-10% | A modest potential aggregate premium with a much wider spread of outcomes, including severe individual disappointments. |

These are game calibration proposals, not empirical historical averages, guaranteed APYs, bounds on any one company, or rules forcing small stocks to win. Many individual small-company paths can have negative CAGR even when their diversified segment has a positive median. Large-cap portfolios must sometimes outperform for long stretches.

These size bands refine the earlier broad stock-role targets; do not add them on top of existing sector/high-growth targets as automatic bonuses. Calibrate combined realized returns, volatility, events and transaction costs together. The growth parameter's meaning must be explicit: an arithmetic drift, logarithmic drift and measured CAGR are different quantities. Do not remove volatility drag simply to make every stock reach its assigned band.

An initial four-company-per-tier split in the current twelve-stock roster would still provide limited diversification. Segment tests should disclose this, use many worlds and entry dates, and not imply the breadth of a real small-cap index. Separate player-facing funds are optional and not a prerequisite for size-aware companies.

## Behavior matters more than the percentage ladder

### Large

Suggested flavor: established multi-village suppliers or transport networks. Smaller local incidents would usually have less effect on an otherwise diversified business, while broad recessions, expensive valuations and company failures can still produce major losses. Do not grant an automatic rescue or a cash-like risk label.

Illustrative candidates for a newly designed starting profile: Golden Harvest Cooperative and Ender Freight. These are proposed narrative assignments, not measurements of current capitalization or existing size metadata. AURM's special defensive exposure should remain a separate trait rather than treating every large business as a hedge.

### Medium

Suggested flavor: regional firms with proven operations but substantial remaining expansion opportunities. A new contract or factory can matter more than it would for an enormous incumbent, without making every setback terminal.

Illustrative candidates: Redstone Dynamics and Masons & Works. A technology company can be large or medium; its sector is not a size classification.

### Small

Suggested flavor: local producers, niche exporters or young ventures. A new customer, product setback or supply problem can have a large proportional effect. Include both dependable niche businesses and genuinely speculative ventures, rather than making every small company unprofitable or every small company a future winner.

Illustrative candidates: Tidewater Fisheries, Potionworks and Frontier Expedition Ventures. Frontier's asymmetric expedition risk should remain distinct from an established local fishery even if both begin in the same capitalization segment.

Size-related liquidity differences could be added later through small, transparent execution spreads, but are not needed for the first pass. Do not add hidden charges, arbitrary inability to sell or player-driven price manipulation to make the simulation seem sophisticated.

## Market cap, business scale and migration

Real market capitalization is share price multiplied by shares outstanding. Price per share alone does not determine company size. Simulated outstanding shares must remain independent of how many shares players happen to own.

For a true capitalization model, assign deliberate starting company scales and compatible simulated floats, in an emerald-denominated system suited to the game. Do not copy U.S. dollar thresholds into the mod. Define and version thresholds or relative-universe breakpoints explicitly.

Market capitalization can change with valuation, while operational scale, financial resilience and maturity need not change at the same speed. Keep these concepts separate. A price crash must not automatically give a distressed company an extra guaranteed growth bonus merely because it now falls into the small-cap segment.

Suggested classification review: once per economic quarter, with a buffer around thresholds so labels do not oscillate daily. Smooth changes to any economic exposure rather than applying an abrupt return discontinuity. Companies can move up or down, but no ticker is entitled to permanent rapid growth or a mandatory promotion.

A small company that becomes large should not retain an indefinite small-company premium solely because it started small. Conversely, a small-cap portfolio methodology must define what happens when holdings graduate: current-segment membership and buy-and-hold of an initial cohort are different strategies.

Existing VILX initialization uses equal capitalization. Introducing differentiated simulated floats changes future index weights. Any migration must preserve holdings, historical prices, cost basis and the current index level using value-neutral divisor treatment. Stock splits must preserve market capitalization and investor wealth. If size-aware VILX remains capitalization weighted, do not silently turn it into an equal-weight portfolio or promise the same result as the previous study.

## Minecraft presentation

Keep information in the existing market browser and handbook. Suggested summary:

`Company size: Small | Business: Established fishery | Risk: High`

Supporting prose can say that it has room to expand but a poor season can matter more to it than to a nationwide cooperative. Do not present the calibration band's upper value as guaranteed interest.

Size transitions and recorded business milestones can feed the existing news system. Examples are editorial concepts, not current in-world facts: a small potion supplier gaining regional distribution, or an expanding transport company becoming an established network. News reports the underlying modeled event; opening an article must not itself change company capitalization or return draws.

## Acceptance checks for a later implementation

- Run held-out seeds and multiple entry dates over short and long horizons. Preserve failures and losers in aggregate results rather than reporting only surviving small businesses.
- Compare initial-cohort buy-and-hold results separately from rebalanced current-size segment results. Specify membership, costs, graduation and distress treatment before presenting CAGR tables.
- Verify that realized return distributions overlap; size is not a guaranteed ranking. Compare same-sector, similar-quality businesses where possible to avoid attributing a sector effect to size.
- Retain VILX constituent accounting, split neutrality, no negative player balance and maximum-capital-at-risk behavior. Do not give empty accounts synthetic interest or impose player debt through company distress.
- Test threshold stability, no immediate price jump on reclassification, no automatic distressed-company rebound, deterministic reload and value-neutral save migration.
- Retain active bank/CD/loan contract terms. This size proposal is not a reason to rewrite unrelated term-product risk.
- Update both handbook forms, company summaries, changelog and tests together only when the mechanics are actually implemented.

## Primary reference context

The following sources support the conceptual distinctions, not the numerical game target bands:

- FINRA, Market Cap Explained: definition, limits and general size/stability distinctions. https://www.finra.org/investors/insights/market-cap
- Asness, Frazzini, Israel, Moskowitz and Pedersen, Size Matters, If You Control Your Junk (Journal of Financial Economics, 2018), author-hosted summary. Size-premium findings depend importantly on firm quality; the lowest-quality small firms are not automatically rewarded for being risky. https://www.aqr.com/Insights/Research/Working-Paper/Size-Matters-If-You-Control-Your-Junk
- CME Group, Equities: Can Size Rotation Outperform? (2024), historical analysis illustrating extended periods of relative performance in both directions. This is historical context, not a current market forecast. https://www.cmegroup.com/articles/2024/equities-can-size-rotation-outperform.html

## Change record

This commit adds only this design recommendation, preserving earlier studies and reviews. The only new numbers are explicitly labeled proposed calibration bands. No production edits, simulated performance claim, new financial product, JAR, merge or release is introduced. Because player behavior remains unchanged, no handbook text was artificially edited; a future implementation must explain the actual delivered size/risk model in both reader formats.
