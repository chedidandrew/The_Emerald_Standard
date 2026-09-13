# Investment behavior

The Market now offers 23 listings: 12 stocks, two indices, one treasury fund, and eight
cash-settled commodity investments. Each type is labeled above its ticker in the same
cycling selector. Stocks now share the variable positive-fundamentals philosophy in
[The Emerald Wire and variable growth](EMERALD_WIRE.md). VILX tracks all twelve company
stocks, including the four specialists. Commodities, VCIX and TREA are excluded.

## VILX capitalization tracking (beta.12)

Each company begins with equal simulated capitalization (approximately 8.33% of VILX),
implemented as 100 emeralds of nominal company capital divided by its starting quote.
These persistent simulated shares outstanding are independent of every player's portfolio.
Company value is quote times simulated shares; VILX is their sum divided by a saved divisor.
Weights therefore drift with relative company performance without periodic rebalancing.

News, village effects, cycles and price floors first enter actual company quotes. VILX
receives no independent return, news shock, village bonus or upside limiter. All constituents
falling over the same interval means the index falls; a large winner can outweigh many losers.
Company splits multiply simulated shares alongside player shares; an index denomination split
adjusts the divisor. Neither event changes basket weights, wealth or cost basis.

The indicative fundamental bias is the capitalization-weighted mean of the companies'
positive 1-15% targets, not a new draw and not a price top-up. Its distribution differs from
the individual target distribution; concentration can increase over time. Actual returns
may be negative or exceed 15%, with no recovery promise.

Format 34 saves persist twelve share counts, the divisor and the tracking-start economic day.
Pre-34 saves establish equal capitalization at their current quotes and anchor the divisor
to the existing VILX quote. All holdings, cost bases and actual history remain intact; no
pre-upgrade basket history is invented. Current-format missing, nonfinite, mismatched or
unexpected stock-basket data is rejected rather than silently reset. Back up before upgrade
and do not downgrade converted worlds.

| Ticker | What drives it | Main tradeoff |
| --- | --- | --- |
| TREA | Treasury income accrues in the share price; a two-year modeled duration loses value when economy interest rates rise and gains when rates fall. | Low volatility, lower expected growth; not guaranteed savings or a fixed-maturity CD. No additional cash dividend. |
| AURM | Gold-reserve business, negative broad-market exposure, defensive demand in recessions and credit scares. | Can lag badly in bull markets and still lose money. Not a physical-gold tracker or redeemable gold. |
| BRCK | High market exposure; booms/recoveries, rebuilding demand and modest village construction fundamentals. | Stronger boom/bust swings and credit-scare losses. |
| FISH | Seeded 84–132-economic-day catch cycles, independent business shocks and modest agriculture/trade fundamentals. | Seasonal ups and downs, not an accumulating free bonus. Simulated seasons are unrelated to actual weather or fish entities. |
| VENT | Low broad-market exposure, high independent volatility and rare expedition success/failure jumps. | Both large gains and large losses are possible; very high risk. |

All use the same buy/sell interface, fractional shares and trade spread. They are deterministic
from the world economy seed and day, so reopening a world cannot reroll outcomes. Risk labels
account for the magnitude of negative exposure as well as independent volatility; expedition
jump risk receives an explicit very-high rating. Village drift remains capped at +/-1.2%
annually, not a guaranteed return. Global news events remain optional; intrinsic price noise,
catch cycles and expedition outcomes are part of the price model, like existing market shocks.

## Existing worlds

Economy format 33 preserves existing positions, cost bases, prices, history and the economic
clock. Commodity investments begin at the existing underlying quote, where one exists,
with one initial observation on their listing day. New underlying markets begin at their
configured anchor. No synthetic pre-listing history is added. Gold, diamond, netherite and
emerald-ore Trade history is retained, including the fixed emerald-block chart's time span.
The five specialist listings added in format 27 still begin at 100 E when upgrading saves
that predate them. Missing fields in a current-format save remain corruption errors;
commodity investment prices must equal their underlying quotes.

Back up the entire world before testing. Use matching new jars on clients and server, and do
not downgrade a format-33 save. This is an unreleased beta.10 candidate, not a new published release.

## Commodity investments

| Ticker | Exposure / base unit | Behavior |
| --- | --- | --- |
| GOLD | Gold ingot | Defensive demand in recessions and credit scares; distinct from AURM company stock. |
| IRON | Iron ingot | Industrial/rebuilding demand; weaker recession demand. |
| COAL | Coal | Fuel demand, more modest regime sensitivity and independent volatility. |
| DIAM | Diamond | Gem demand; discoveries increase supply and can lower prices. |
| COPR | Copper ingot | Strong cyclical industrial/automation demand. |
| RDST | Redstone dust | Volatile technology input; automation events lift demand. |
| LAPS | Lapis lazuli | Enchanting/expedition demand; discoveries can lower prices. |
| NETH | Netherite scrap | Nether supply shocks; not a crafted netherite ingot. |

These holdings are fractional **units**, not company shares or physical inventory stacks.
Bank Cash is spent first, then only the shortfall is funded by ordinary inventory emeralds.
Sales return Bank Cash; investment buys/sales do not deliver, consume or redeem physical
gold, iron, coal, etc. No dividends, leverage, borrowing or debt are added. The existing
0.25% investment spread applies on each side; a same-quote round trip loses value.
Where physical Trade supports a resource, both screens share one underlying market price.
This feature does not add new physical-item exchange recipes or expand Trade's item list.

Commodity pricing uses numerically bounded supply/demand markets around slowly growing
reference values, with seeded common/independent noise, volatility clusters, regime
sensitivity and event responses, not the company-return model.
Commodity units do not undergo stock splits or silently change their resource exposure.
Migration preserves current quotes; future stock/index/commodity behavior follows the new
variable-target model. Treasury-fund behavior is unchanged.

## Dashboard corrections

City expansion now has one full-width information panel, two-line status/advice budgets and
separate mode, funding/status and navigation rows. Market explains each listing's behavior;
insufficient combined Bank Cash/inventory emeralds produce a clear funding message. A new
listing/day-zero chart starts from its first actual observation; investment quotes now update every 15 seconds of ordinary gameplay. None of these UI changes
alters expansion permissions, Automatic defaults, construction cadence or growth requirements.

See the [validation record](reviews/2026-09-10-investment-diversity-validation.md) for tests,
simulation diversity measurements, local screenshots and test-jar fingerprints.
See also [commodity investment validation](reviews/2026-09-11-commodity-investments.md).

## VCIX and comparison charts (beta.10)

VILX is named **Villager Stock Exchange Index**; beta.12 changes its former hybrid pricing
to the twelve-company capitalization model above, without changing its ticker.
VCIX, the **Villager Commodity Index**, is a buy-and-hold basket of all eight commodity listings.
It starts with equal capital weights (12.5% each), not equal item quantities. Weights drift with
prices and are persisted, validated and copied with the economy. Its return at each intraday quote is the
previous weights times each underlying's realized return, without independent shocks or drift.
The underlying positive 1–15% fundamental targets remain game-balance assumptions, not guaranteed
basket returns. Commodity index holdings are ordinary cash-settled investments with the existing spread.

Old worlds add VCIX at 100 E with one observation today. Existing quotes, positions, cost bases and
history remain intact; current-format missing or invalid basket weights are rejected, not reset.
Stock/index denomination normalization adjusts news comparison baselines to prevent fake crash headlines.

Listings and comparison columns show signed, colored changes from the current quote against the previous economic close. A day-zero
listing displays unavailable, not fabricated zero performance. Single charts use their selected-period
direction; comparison series are solid blue and dashed amber so both remain identifiable. Each Details
column scrolls independently. The exact cycle is 1 Day, 30 Days, 90 Days, 1 Year, 3 Years, 5 Years, 10 Years, All. Today live is the default, with Yesterday as a secondary session toggle. Comparison curves use matching real timestamps, normalize both to 100 at the common start, and leave the unobserved part of Today empty. Hover shows dates/time and observed prices; individual charts include a previous-close (or First observed) reference.

The retained daily window is ten years plus its opening close. All adds a bounded, progressively coarsened archive of older closes; detailed intraday tapes retain only Today and Yesterday. Existing pruned history is not recovered or invented. A bounded read-only transport document carries current quotes and all eight aligned comparison/individual curves. Mismatched selection or invalid data shows a loading/empty state instead of combining unrelated series.

The positive 1-15% long-run fundamental bias is unchanged, but actual future paths differ under the finer stochastic stepping. Drift scales linearly with elapsed time, noise by its square root. The focused calibration checks daily mean and variance, not a guaranteed annual return; indices inherit their baskets at every quote. See [beta.15 validation](reviews/2026-09-12-beta15-live-market.md).
