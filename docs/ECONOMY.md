# Economy model

## Economic clock

One Minecraft day equals one economic day. A standard Minecraft day lasts 20 real minutes, so 365 economic days equal about 5 real days, 1 hour, and 40 minutes.

The larger of accumulated game time and trusted wall-clock time advances one unified remainder so ordinary online play is not double-counted. Partial progress persists across restarts. Offline catch-up is bounded, and banking pauses while a backlog remains so players cannot trade against a stale market.

## Global market regimes

The global economy transitions among expansion, bull, boom, stagnation, recession, crash, and recovery. Regimes are persistent and probabilistic rather than scripted. VILX and eight Minecraft-themed companies combine broad-market exposure, company-specific risk, rare deterministic events, and a private economy seed.

The committed regression suite continues to target roughly 10 percent long-run VILX CAGR while allowing severe negative years, unusually strong recoveries, and long sideways periods. These are simulation targets, not guaranteed player returns.

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

A bounded 256-entry transaction ledger records authoritative deposits, withdrawals, savings movement and interest, stock executions, CD and lending activity, and Prosperity Fund contributions. Repeated passive savings-interest credits coalesce into an aggregate entry so routine accrual does not crowd active transactions out of the bounded history. Market prices, commodity quotes, and personal net worth retain up to 1,825 economic days (five in-game economic years). Dashboard range selection offers 30 days, 90 days, one year, and all retained data.

Format-8 and older holdings did not record their executions. During format-9 migration, the mod initializes their basis from the migration-day market price and marks it as inferred rather than presenting it as exact historical performance.

## Village Prosperity Fund

Contributions are voluntary, irreversible transfers from bank cash to a village-owned fund. They never create a player receivable, promised return, borrowing balance, or debt. The three contribution types are:

- **Direct Grant:** enters a purpose-specific spendable balance. Non-restoration grants place the configured fraction into an emergency reserve.
- **Endowment:** preserves principal permanently and creates only a configurable annual payout, 4 percent by default, for bounded village spending.
- **Project Sponsorship:** follows the village's current economically unfinished project, derives its accounting purpose from that project type, and supplies bounded material and development inputs. Once the economic project completes, any unused sponsorship balance becomes ordinary spendable funding for that derived purpose.

Purposes are General, Housing, Food, Infrastructure, Security, Trade, and Restoration. An abandoned or extinct village automatically routes a Direct Grant to Restoration. Purpose balances affect ordinary local inputs rather than directly writing investment returns or market output. The Fund debits only value that its bounded destination can accept, so saturated inputs retain unused funding. Automatic spending respects a configured monthly treasury ceiling, converted to a daily cap, and uses the emergency reserve only during restoration, acute food shortage, or low safety.

Lifetime contribution totals and non-financial donor titles may be displayed when recognition is enabled. Recognition changes no prices, payouts, permissions, or economic outcomes.
