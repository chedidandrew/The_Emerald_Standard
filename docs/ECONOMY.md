# Economy model

## Economic clock

One Minecraft day equals one economic day. A standard Minecraft day lasts 20 real minutes, so 365 economic days equal about 5 real days, 1 hour, and 40 minutes.

The larger of accumulated game time and trusted wall-clock time advances the economy so ordinary online play is not double-counted. Partial game and wall days are stored, so repeated sessions shorter than 20 minutes still accumulate correctly across restarts.

Moving the computer clock backward does not lower the trusted timestamp. Offline catch-up is capped at 25,000 economic days, advances no more than 2,000 days during startup, and continues in 250-day background batches. Banking is paused while catch-up remains so every player sees a fully current market before trading.

## Market regimes

The global economy transitions between expansion, bull, boom, stagnation, recession, crash, and recovery states. Transition probabilities are intentionally persistent. Long-run regression tests currently observe average runs of roughly:

| Regime | Typical simulated duration |
|---|---:|
| Expansion | about 286 days |
| Bull | about 222 days |
| Boom | about 111 days |
| Stagnation | about 200 days |
| Recession | about 143 days |
| Crash | about 25 days |
| Recovery | about 167 days |

Actual worlds vary because transitions are seeded and probabilistic.

## VILX and company returns

VILX is the common market factor. Each company combines:

```text
risk-free return
+ beta exposure to VILX
+ a small company alpha assumption
+ company-specific volatility
```

The Gaussian generator independently mixes both Box-Muller uniform inputs. Rare market jumps add fat tails, while future outcomes remain deterministic from the private economy seed and economic day. Deterministic transcendental calculations use `StrictMath` to improve replay consistency across platforms.

The committed regression suite evaluates 250 independent 75-year histories. Current calibration results are approximately:

| Investment | Mean long-run CAGR |
|---|---:|
| VILX | 9.7% |
| RSDN | 10.9% |
| DPMN | 8.2% |
| NSPC | 8.7% |
| ENDR | 9.0% |
| GLDH | 6.6% |
| POTN | 8.4% |
| IRNG | 7.0% |
| MCRT | 7.6% |

Across those simulations, roughly 27% of VILX years are negative. Severe crashes and unusually strong recoveries occur, but none of these figures is a guarantee for a particular world.

## Savings and CDs

Savings rates move with the economic regime and average near 3% across the long-run regime distribution.

CDs support 30, 90, 180, and 365-day terms. The rate is locked when the CD opens. Interest stops at maturity. Closing early returns principal minus a 1% penalty and forfeits accrued interest. Automatic renewal is not part of this alpha.

## Villager business lending

A player may fund one villager business lending position at a time in the alpha. The quoted yield is locked when funded. Economic stress accumulated during the term affects the deterministic default probability at maturity.

Possible outcomes are:

- Full repayment with accrued interest
- Partial default with 45% to 90% recovery of the matured claim
- Full default with zero recovery

A player's maximum loss is the amount voluntarily invested. No code path creates a debt balance or requires additional payment.

Alpha.2 increased the 30-day and 90-day quoted yields and slightly reduced the 30-day base default probability so short terms provide a meaningful expected premium over savings. The committed path-based test currently observes approximate expected annualized returns of:

| Term | Expected annualized return after defaults |
|---|---:|
| 30 days | 6.7% |
| 90 days | 7.5% |
| 180 days | 8.0% |
| 365 days | 12.2% |

The stressed 365-day default-distribution test produces about an 8.2% default rate, about a 1.0% full-default rate, and about 59.5% average recovery conditional on default.

## Commodity exchange

Diamond, gold, netherite, and emerald-ore values follow mean-reverting markets with regime-sensitive targets. Resource forms use material-equivalent pricing. Diamond, raw-gold, gold, netherite, and emerald blocks are supported alongside valuable ore and processed forms. A gold block is worth nine gold ingots, Nether gold ore is conservatively valued at half an ingot, and a netherite ingot includes four scraps and four gold ingots.

## Trading friction

Stock and index trades use a 0.25% spread on each side. This prevents cost-free rapid trading and gives future Banker upgrades room to improve execution costs.
