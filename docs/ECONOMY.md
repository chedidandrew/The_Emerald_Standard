# Economy model

## Economic clock

One Minecraft day equals one economic day. A standard Minecraft day lasts 20 real minutes, so 365 economic days equal about 5 real days, 1 hour, and 40 minutes.

The largest forward delta among accumulated server game time, the commandable Overworld clock, and trusted wall-clock time advances one unified remainder so overlapping clocks are not double-counted. This lets `/time add`, forward `/time set`, and faster `/time rate` changes advance the economy while ordinary server ticks still work when the Overworld clock is paused. Explicit backward `/time set` commands move forward to the next requested sky phase: Day 23 midnight followed by `/time set 0` becomes economic Day 24 dawn. Repeating an identical numeric time or same-phase named marker adds no progress; different commands in the same tick are handled separately. Sub-quote clock jitter is retained when already in the requested quote slot, so a few wall-clock milliseconds do not manufacture an extra day. Passive backward observations (for example, a restored world clock) only rebase, without subtracting or inventing progress. Partial progress and the last observed Overworld-clock baseline persist across restarts. Older saves initialize a missing baseline from the current world once; new saves can therefore recover a Minecraft-saved command jump even if the process stopped before the next economy tick. Offline catch-up is bounded, and banking pauses while a backlog remains so players cannot trade against a stale market. World configuration can disable wall-clock progression without pausing game-time or Overworld-clock progression and can lower the maximum credited offline gap from its protected 25,000-day default.

## Intraday quotes (format 36)

Eighty deterministic substeps per economic day publish real shared quotes, normally every 300 server ticks (15 seconds at 20 TPS). Drift is scaled by 1/80, volatility by sqrt(1/80); rare daily shocks are selected once, not rerolled eighty times. Stocks and commodities move first; VILX and VCIX derive each quote from their actual baskets. Price splits adjust open, live tapes, daily history and archives consistently. Daily news, savings, maturities and village settlement still occur once per completed economic day.

Format 36 persists the session slot, opening quotes, both intraday tapes and older archives. Migration preserves current prices, holdings and daily history, beginning intraday recording at the observed upgrade time. Mid-session migration reports an unknown full-day return until a genuine opening baseline exists. Invalid or missing current-format tapes fail validation rather than silently rerolling prices.

Catch-up work counts all eighty quote steps in its budget: at most 25 days per normal batch and 200 at startup, reduced further for accounts and villages. Money mutations remain unavailable while whole-day backlog exists. The absolute pending limit remains 25,000 days; capped explicit commands retain the requested time-of-day phase.

## Global market regimes

The global economy transitions among expansion, bull, boom, stagnation, recession, crash, and recovery. Regimes are persistent and probabilistic rather than scripted. Twelve Minecraft-themed companies combine broad-market exposure, company-specific risk, rare deterministic events, and a private economy seed.

Stocks, commodities and the shared broad economic factor use smooth, world-seeded positive underlying growth targets sampled between 1 and 15 percent, with lower targets much more common. This is a game-balance bias, not promised CAGR. Actual years can lose money or exceed the target. Earlier fixed-drift calibration numbers are historical and must not be presented as current forecasts. See [investment behavior](INVESTMENT_BEHAVIOR.md) and [The Emerald Wire](EMERALD_WIRE.md).

VILX now follows all twelve company stocks by simulated market capitalization, initially equal and then drifting with prices. Its price is total company capitalization divided by a persisted divisor; it has no independent market return, extra news/village effect or upside damping. It inherits an indicative positive 1-15 percent fundamental bias from its companies without guaranteeing any realized annual return. Company and index splits preserve capitalization, ownership value and historical return continuity. Pre-34 worlds retain their existing VILX quote, holdings and history; tracking starts on upgrade. Rare event shocks can still be disabled without removing ordinary regimes or volatility.

## Village fundamentals

When Village Prosperity simulation and market integration are both enabled, eligible settlements contribute a deliberately small fundamental factor:

- Mining influences Deepdelve Mining and commodity supply.
- Agriculture influences Golden Harvest Cooperative.
- Trade influences Nether Spice and Ender Freight.
- Transportation influences Minecart Transit.
- Security influences Iron Golem Security.
- Specialized prosperity contributes modestly to Redstone Dynamics and Potionworks.

The annual per-asset contribution is capped at approximately plus or minus 1.2 percentage points. Normally, Empty, Extinct, Abandoned, and temporarily market-suppressed settlements do not contribute. A player-damage counterfactual is the deliberate exception: before the first player-caused casualty changes an eligible village, the exact village state and current contribution are persisted. That no-player-damage branch advances under the normal abstract simulation and is re-priced on each enabled simulation day; genuine non-player casualties are applied to it as well. Repeated player hits do not recapture the baseline. The counterfactual remains authoritative until the cooldown has elapsed and the live village has fully recovered. Global regimes, volatility, and company events remain dominant.

`village_prosperity.market_integration_enabled=false` removes these settlement fundamentals without disabling the local simulation or visual progression.

## Local village economy

Local settlements track food, materials, treasury, prosperity, safety, and several industry outputs. They are not guaranteed to become permanently richer. Daily simulation includes consumption, small storage spoilage, infrastructure and material upkeep, food-shortage penalties, and rare local positive or negative events.

With visual progression enabled, pending settlers do not produce output until the actual villager entity exists and a census observes it. Infection and long-term emigration also remove residents from productive population without inventing a death event.

## Savings and CDs

Savings rates vary by regime and average near 3 percent across the long-run regime distribution.

CDs support 30, 90, 180, and 365-day terms. The rate locks at opening, interest stops at maturity, and closing early returns principal minus a 1 percent penalty while forfeiting accrued interest. Each player may hold up to eight independent CD positions and select the specific position to inspect or close.

## Villager business lending

Players can fund villager businesses but can never borrow from them. Lending can fully repay, partially default, or fully default. A player's maximum loss is the amount voluntarily funded. There is no debt balance and no additional repayment obligation. Each player may hold up to eight independent lending positions and select the specific resolved position to collect.

Current path-based tests target approximate expected annualized returns after defaults of roughly 6.7 percent for 30 days, 7.5 percent for 90 days, 8.0 percent for 180 days, and 12.2 percent for 365 days.

## Commodity exchange

Diamond, gold, netherite, and emerald-ore values follow mean-reverting markets with regime and event sensitivity. Resource forms use conservative material-equivalent pricing. Village mining and trade can add only a small capped supply pressure when market integration is enabled.

## Trading friction

Stock and index trades use a 0.25 percent spread on each side to discourage cost-free rapid trading.

## Portfolio accounting and history

Stock purchases add their actual execution cost to a per-symbol cost basis. Partial sales remove the proportional basis and record the difference between proceeds and removed basis as realized gain or loss. The portfolio view also reports average purchase price, unrealized gain or loss, total cost basis, allocations, lifetime external contributions and withdrawals, and the value of open term positions.

A bounded 256-entry transaction ledger records authoritative deposits, withdrawals, savings movement and interest, stock executions, CD and lending activity, and Prosperity Fund contributions. Repeated passive savings-interest credits coalesce into an aggregate entry so routine accrual does not crowd active transactions out of the bounded history. Prices and personal net worth retain 3,651 daily observations (ten years including the opening close). Market investments additionally retain today's and yesterday's actual intraday quotes, plus a bounded 512-point progressively coarsened archive of older closes. The investment range cycle is 1 Day, 30 Days, 90 Days, 1 Year, 3 Years, 5 Years, 10 Years, All. Missing pre-installation, pre-listing or previously pruned history is never fabricated. Home and physical Trade charts remain daily histories; the live intraday view is for investments.

Format-8 and older holdings did not record their executions. During format-9 migration, the mod initializes their basis from the migration-day market price and marks it as inferred rather than presenting it as exact historical performance.

## Village Prosperity Fund

Contributions are voluntary, irreversible transfers from Bank Cash plus ordinary loose inventory emeralds to a village-owned fund. They never create a player receivable, promised return, borrowing balance, or debt. The three contribution types are:

- **Direct Grant:** enters a purpose-specific spendable balance. Non-restoration grants place the configured fraction into an emergency reserve.
- **Endowment:** preserves principal permanently and creates only a configurable annual payout, 4 percent by default, for bounded village spending.
- **Project Sponsorship:** follows the village's current economically unfinished project, derives its accounting purpose from that project type, and funds its labor. Once the economic project completes, any unused sponsorship balance becomes ordinary spendable funding for that derived purpose.

Purposes are General, Housing, Food, Infrastructure, Security, Trade, and Restoration. An abandoned or extinct village automatically routes a Direct Grant to Restoration. Purpose balances affect ordinary local inputs rather than directly writing investment returns or market output. The Fund debits only value that its bounded destination can accept, so saturated inputs retain unused funding. Routine automatic spending respects a configured monthly treasury ceiling, converted to a daily cap, and uses the emergency reserve only during restoration, acute food shortage, or low safety.

The default fast-track capital policy removes that time throttle only for player-origin liquid capital and one concrete project need. It can close the selected building's exact material, treasury, and development deficit, retry approval, and then buy its exact remaining labor. Dedicated sponsorship is consumed first, followed by matching-purpose Direct Grant capital and then General capital. The labor debit is atomic: if the full remainder is unavailable, no fast-track money moves and ordinary capped progress continues. Passive Endowment payout and emergency reserves remain rate-limited, and acute food relief takes priority over every capital project. Fast-track never spends protected Endowment principal, selects a second project, instantly places blocks, or writes market prices directly. Physical work remains loaded-chunk-only, paced, and protected. Servers that prefer all Fund movement to remain rate-limited can disable it in the world configuration.

Lifetime contribution totals and non-financial donor titles may be displayed when recognition is enabled. Recognition changes no prices, payouts, permissions, or economic outcomes.


## Decision support

Town reports distinguish resident checks, economic labor, physical construction and city expansion.
Fund previews reuse the authoritative reserve-allocation rule; receipts describe accepted transfers,
not instant buildings or donor-specific later spending. Market browsing and favorites are read-only
navigation. See [five priorities](FIVE_PRIORITIES.md).
