package com.chedidandrew.emeraldstandard.core;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Deterministic, loader-neutral Villager Exchange simulation. */
public final class EconomyEngine {
    public static final int DAYS_PER_YEAR = 365;
    public static final double TRADE_SPREAD = 0.0025;

    private static final double SQRT_DAYS_PER_YEAR = StrictMath.sqrt(DAYS_PER_YEAR);
    private static final double RISK_FREE_ANNUAL_RATE = 0.025;
    private static final long REGIME_SALT = 0x524547494D45L;
    private static final long MARKET_SALT = 0x4D41524B4554L;
    private static final long MARKET_JUMP_SALT = 0x53484F434BL;
    private static final long COMMODITY_SALT = 0x434F4D4D4F444954L;
    private static final long LOAN_SALT = 0x4C4F414E4F555443L;
    private static final long EVENT_SALT = 0x4556454E544E4557L;

    public enum Regime {
        EXPANSION(0.145, 0.13),
        BULL(0.19, 0.15),
        BOOM(0.25, 0.18),
        STAGNATION(0.02, 0.09),
        RECESSION(-0.08, 0.20),
        CRASH(-0.38, 0.35),
        RECOVERY(0.25, 0.18);

        private final double annualReturn;
        private final double annualVolatility;

        Regime(double annualReturn, double annualVolatility) {
            this.annualReturn = annualReturn;
            this.annualVolatility = annualVolatility;
        }

        public double annualReturn() {
            return annualReturn;
        }

        public double annualVolatility() {
            return annualVolatility;
        }
    }

    public record Asset(
            String ticker,
            String name,
            String sector,
            double beta,
            double annualAlpha,
            double annualIdiosyncraticVolatility) {
        public Asset {
            ticker = ticker.toUpperCase(Locale.ROOT);
        }
    }

    public static final List<Asset> ASSETS = List.of(
            new Asset("VILX", "Villager Exchange Index", "Diversified", 1.00, 0.000, 0.00),
            new Asset("RSDN", "Redstone Dynamics", "Redstone Technology", 1.30, 0.015, 0.22),
            new Asset("DPMN", "Deepdelve Mining", "Mining", 1.15, 0.005, 0.24),
            new Asset("NSPC", "Nether Spice Company", "Nether Trade", 1.25, 0.012, 0.28),
            new Asset("ENDR", "Ender Freight & Logistics", "Transportation", 1.05, 0.008, 0.19),
            new Asset("GLDH", "Golden Harvest Cooperative", "Agriculture", 0.70, 0.000, 0.13),
            new Asset("POTN", "Potionworks Laboratories", "Alchemy", 0.95, 0.006, 0.18),
            new Asset("IRNG", "Iron Golem Security", "Village Services", 0.75, 0.001, 0.14),
            new Asset("MCRT", "Minecart Transit", "Transportation", 0.85, 0.002, 0.15));

    public record Commodity(String id, String name, double anchorPrice, double annualVolatility) {
    }

    public static final List<Commodity> COMMODITIES = List.of(
            new Commodity("diamond", "Diamond", 12.0, 0.18),
            new Commodity("gold", "Gold Ingot", 2.0, 0.16),
            new Commodity("netherite", "Netherite Scrap", 20.0, 0.30),
            new Commodity("emerald_ore", "Emerald Ore", 1.0, 0.08));

    public enum LoanOutcome {
        REPAID,
        PARTIAL_DEFAULT,
        FULL_DEFAULT
    }

    public enum MarketEvent {
        NONE("Village Markets", "No extraordinary market event is active."),
        REDSTONE_REVOLUTION("Redstone Revolution", "Automation demand lifted redstone businesses."),
        NETHER_SUPPLY_CRISIS("Nether Supply Crisis", "Nether goods surged as trade routes tightened."),
        GOLDEN_HARVEST("Golden Harvest", "A strong harvest supported village agriculture."),
        END_EXPEDITION_BOOM("End Expedition Boom", "New expeditions boosted exotic freight demand."),
        CREEPER_CATASTROPHE("Creeper Catastrophe", "Damage raised security demand and disrupted transit."),
        VILLAGER_CREDIT_SCARE("Villager Credit Scare", "Risk assets fell as village lenders turned cautious."),
        DEEPVEIN_DISCOVERY("Deepvein Discovery", "A major ore discovery reshaped mining expectations.");

        private final String title;
        private final String detail;

        MarketEvent(String title, String detail) {
            this.title = title;
            this.detail = detail;
        }

        public String title() {
            return title;
        }

        public String detail() {
            return detail;
        }
    }

    public record LoanResolution(LoanOutcome outcome, double recoveryRate, double defaultProbability) {
    }

    private EconomyEngine() {
    }

    public static Regime initialRegime(long seed) {
        double draw = unit(mix64(seed ^ 0x454D4552414C44L));
        if (draw < 0.70) {
            return Regime.EXPANSION;
        }
        if (draw < 0.90) {
            return Regime.STAGNATION;
        }
        return Regime.RECESSION;
    }

    /** Regime persistence is measured in Minecraft months and years rather than a few days. */
    public static Regime nextRegime(Regime current, long seed, long day) {
        double draw = unit(mix(seed, day, REGIME_SALT));
        return switch (current) {
            case EXPANSION -> pick(draw,
                    new Regime[]{Regime.EXPANSION, Regime.BULL, Regime.STAGNATION, Regime.RECESSION},
                    new double[]{0.9965, 0.0015, 0.0012, 0.0008});
            case BULL -> pick(draw,
                    new Regime[]{Regime.BULL, Regime.EXPANSION, Regime.BOOM, Regime.RECESSION},
                    new double[]{0.9955, 0.0020, 0.0014, 0.0011});
            case BOOM -> pick(draw,
                    new Regime[]{Regime.BOOM, Regime.BULL, Regime.CRASH},
                    new double[]{0.9910, 0.0050, 0.0040});
            case STAGNATION -> pick(draw,
                    new Regime[]{Regime.STAGNATION, Regime.EXPANSION, Regime.RECESSION},
                    new double[]{0.9950, 0.0035, 0.0015});
            case RECESSION -> pick(draw,
                    new Regime[]{Regime.RECESSION, Regime.RECOVERY, Regime.CRASH, Regime.STAGNATION},
                    new double[]{0.9930, 0.0045, 0.0010, 0.0015});
            case CRASH -> pick(draw,
                    new Regime[]{Regime.CRASH, Regime.RECESSION, Regime.RECOVERY},
                    new double[]{0.9600, 0.0150, 0.0250});
            case RECOVERY -> pick(draw,
                    new Regime[]{Regime.RECOVERY, Regime.EXPANSION, Regime.BULL},
                    new double[]{0.9940, 0.0045, 0.0015});
        };
    }

    public static double marketReturn(Regime regime, long seed, long day) {
        double dailySigma = regime.annualVolatility() / SQRT_DAYS_PER_YEAR;
        double logReturn = StrictMath.log1p(regime.annualReturn()) / DAYS_PER_YEAR
                - 0.5 * dailySigma * dailySigma
                + dailySigma * gaussian(mix(seed, day, MARKET_SALT));

        double jumpDraw = unit(mix(seed, day, MARKET_JUMP_SALT));
        if (jumpDraw < 0.00015) {
            logReturn -= 0.08 + 0.15 * unit(mix(seed, day, 91L));
        } else if (jumpDraw > 0.99995) {
            logReturn += 0.04 + 0.08 * unit(mix(seed, day, 92L));
        }

        return StrictMath.expm1(clamp(logReturn, -0.60, 0.35));
    }

    public static double assetReturn(Asset asset, double marketReturn, long seed, long day) {
        return assetReturn(asset, marketReturn, seed, day, 0.0);
    }

    /**
     * Computes one company return with a small, capped annual village-fundamentals drift.
     * World activity can influence the market, but it can never guarantee a profit.
     */
    public static double assetReturn(
            Asset asset,
            double marketReturn,
            long seed,
            long day,
            double annualVillageDrift) {
        if (asset.ticker().equals("VILX")) {
            return marketReturn;
        }

        double marketLogReturn = StrictMath.log1p(clamp(marketReturn, -0.999999, Double.MAX_VALUE));
        double riskFreeDailyLogReturn = StrictMath.log1p(RISK_FREE_ANNUAL_RATE) / DAYS_PER_YEAR;
        double idiosyncraticSigma = asset.annualIdiosyncraticVolatility() / SQRT_DAYS_PER_YEAR;
        double idiosyncraticShock = idiosyncraticSigma
                * gaussian(mix(seed, day, stableHash(asset.ticker())));
        double safeVillageDrift = clamp(annualVillageDrift, -0.012, 0.012);

        double assetLogReturn = riskFreeDailyLogReturn
                + asset.beta() * (marketLogReturn - riskFreeDailyLogReturn)
                + StrictMath.log1p(asset.annualAlpha()) / DAYS_PER_YEAR
                + StrictMath.log1p(safeVillageDrift) / DAYS_PER_YEAR
                - 0.5 * idiosyncraticSigma * idiosyncraticSigma
                + idiosyncraticShock;

        return StrictMath.expm1(clamp(assetLogReturn, -0.70, 0.50));
    }

    public static MarketEvent marketEvent(long seed, long day, Regime regime) {
        double draw = unit(mix(seed, day, EVENT_SALT));
        double probability = switch (regime) {
            case BOOM, CRASH -> 0.0030;
            case BULL, RECESSION, RECOVERY -> 0.0020;
            default -> 0.0012;
        };
        if (draw >= probability) {
            return MarketEvent.NONE;
        }
        MarketEvent[] events = {
                MarketEvent.REDSTONE_REVOLUTION,
                MarketEvent.NETHER_SUPPLY_CRISIS,
                MarketEvent.GOLDEN_HARVEST,
                MarketEvent.END_EXPEDITION_BOOM,
                MarketEvent.CREEPER_CATASTROPHE,
                MarketEvent.VILLAGER_CREDIT_SCARE,
                MarketEvent.DEEPVEIN_DISCOVERY
        };
        int index = Math.min(
                events.length - 1,
                (int) (unit(mix(seed, day, EVENT_SALT ^ 0x4944454E54495459L))
                        * events.length));
        return events[index];
    }

    public static double eventAssetReturn(MarketEvent event, String ticker) {
        return switch (event) {
            case NONE -> 0.0;
            case REDSTONE_REVOLUTION -> switch (ticker) {
                case "RSDN" -> 0.10;
                case "MCRT" -> 0.025;
                case "VILX" -> 0.018;
                default -> 0.0;
            };
            case NETHER_SUPPLY_CRISIS -> switch (ticker) {
                case "NSPC" -> 0.09;
                case "ENDR" -> -0.035;
                case "VILX" -> 0.006;
                default -> 0.0;
            };
            case GOLDEN_HARVEST -> switch (ticker) {
                case "GLDH" -> 0.075;
                case "VILX" -> 0.010;
                default -> 0.0;
            };
            case END_EXPEDITION_BOOM -> switch (ticker) {
                case "ENDR" -> 0.085;
                case "POTN" -> 0.020;
                case "VILX" -> 0.014;
                default -> 0.0;
            };
            case CREEPER_CATASTROPHE -> switch (ticker) {
                case "IRNG" -> 0.070;
                case "MCRT" -> -0.055;
                case "VILX" -> -0.008;
                default -> -0.006;
            };
            case VILLAGER_CREDIT_SCARE -> ticker.equals("VILX") ? -0.035 : -0.050;
            case DEEPVEIN_DISCOVERY -> switch (ticker) {
                case "DPMN" -> 0.085;
                case "VILX" -> 0.012;
                default -> 0.0;
            };
        };
    }

    public static double eventCommodityReturn(MarketEvent event, String commodityId) {
        return switch (event) {
            case NETHER_SUPPLY_CRISIS -> commodityId.equals("netherite") ? 0.12 : 0.0;
            case GOLDEN_HARVEST -> commodityId.equals("gold") ? -0.025 : 0.0;
            case CREEPER_CATASTROPHE -> commodityId.equals("gold") ? 0.035 : 0.0;
            case VILLAGER_CREDIT_SCARE -> commodityId.equals("gold") ? 0.060 : -0.025;
            case DEEPVEIN_DISCOVERY -> commodityId.equals("diamond") ? -0.070 : 0.0;
            default -> 0.0;
        };
    }

    /** Constituent weights for the player-facing VILX fund. */
    public static double vilxWeight(String ticker) {
        return switch (ticker) {
            case "RSDN" -> 0.22;
            case "DPMN" -> 0.15;
            case "NSPC" -> 0.10;
            case "ENDR" -> 0.12;
            case "GLDH" -> 0.10;
            case "POTN" -> 0.10;
            case "IRNG" -> 0.08;
            case "MCRT" -> 0.13;
            default -> 0.0;
        };
    }

    public static double savingsAnnualRate(Regime regime) {
        return switch (regime) {
            case EXPANSION -> 0.031;
            case BULL -> 0.034;
            case BOOM -> 0.040;
            case STAGNATION -> 0.026;
            case RECESSION -> 0.018;
            case CRASH -> 0.015;
            case RECOVERY -> 0.027;
        };
    }

    public static double cdAnnualRate(Regime regime, int termDays) {
        double termPremium = switch (termDays) {
            case 30 -> 0.010;
            case 90 -> 0.015;
            case 180 -> 0.019;
            case 365 -> 0.023;
            default -> throw new IllegalArgumentException("Unsupported CD term: " + termDays);
        };
        return savingsAnnualRate(regime) + termPremium;
    }

    public static double villagerLoanAnnualYield(Regime regime, int termDays) {
        double termRate = switch (termDays) {
            case 30 -> 0.105;
            case 90 -> 0.115;
            case 180 -> 0.120;
            case 365 -> 0.150;
            default -> throw new IllegalArgumentException("Unsupported loan term: " + termDays);
        };
        double regimePremium = switch (regime) {
            case EXPANSION -> 0.000;
            case BULL -> -0.005;
            case BOOM -> 0.005;
            case STAGNATION -> 0.010;
            case RECESSION -> 0.030;
            case CRASH -> 0.060;
            case RECOVERY -> 0.015;
        };
        return clamp(termRate + regimePremium, 0.04, 0.25);
    }

    /** Opening estimate before future recession/crash stress is known. */
    public static double estimatedLoanDefaultProbability(Regime regime, int termDays) {
        double baseProbability = switch (termDays) {
            case 30 -> 0.006;
            case 90 -> 0.018;
            case 180 -> 0.035;
            case 365 -> 0.055;
            default -> throw new IllegalArgumentException("Unsupported loan term: " + termDays);
        };
        double regimeAdjustment = switch (regime) {
            case EXPANSION, BULL -> 0.0;
            case BOOM -> 0.002;
            case STAGNATION -> 0.006;
            case RECESSION -> 0.018;
            case CRASH -> 0.050;
            case RECOVERY -> 0.008;
        };
        return clamp(baseProbability + regimeAdjustment, 0.002, 0.35);
    }

    public static double loanStressIncrement(Regime regime) {
        return switch (regime) {
            case EXPANSION, BULL -> 0.00;
            case BOOM -> 0.02;
            case STAGNATION -> 0.10;
            case RECESSION -> 0.45;
            case CRASH -> 1.25;
            case RECOVERY -> 0.10;
        };
    }

    public static LoanResolution resolveLoan(
            long economySeed,
            UUID accountId,
            long loanSerial,
            long openDay,
            int termDays,
            double accumulatedStress) {
        double baseProbability = switch (termDays) {
            case 30 -> 0.006;
            case 90 -> 0.018;
            case 180 -> 0.035;
            case 365 -> 0.055;
            default -> 0.055;
        };
        double averageStress = accumulatedStress / Math.max(1.0, termDays);
        double durationExposure = Math.min(1.0, termDays / 180.0);
        double defaultProbability = clamp(
                baseProbability + averageStress * 0.20 * durationExposure,
                0.002,
                0.35);

        long identitySalt = accountId.getMostSignificantBits()
                ^ Long.rotateLeft(accountId.getLeastSignificantBits(), 17)
                ^ Long.rotateLeft(loanSerial, 31)
                ^ Long.rotateLeft(openDay, 9);
        long key = mix(economySeed ^ identitySalt, openDay + termDays, LOAN_SALT);
        double defaultDraw = unit(key);
        if (defaultDraw >= defaultProbability) {
            return new LoanResolution(LoanOutcome.REPAID, 1.0, defaultProbability);
        }

        double severityDraw = unit(mix64(key ^ 0x5345564552495459L));
        if (severityDraw < 0.12) {
            return new LoanResolution(LoanOutcome.FULL_DEFAULT, 0.0, defaultProbability);
        }

        double recoveryDraw = unit(mix64(key ^ 0x5245434F56455259L));
        return new LoanResolution(
                LoanOutcome.PARTIAL_DEFAULT,
                0.45 + 0.45 * recoveryDraw,
                defaultProbability);
    }

    public static double compoundDaily(double principal, double annualRate) {
        return principal * (StrictMath.pow(1.0 + annualRate, 1.0 / DAYS_PER_YEAR) - 1.0);
    }

    public static double nextCommodityPrice(
            Commodity commodity,
            double currentPrice,
            Regime regime,
            long seed,
            long day) {
        return nextCommodityPrice(commodity, currentPrice, regime, seed, day, 0.0);
    }

    /** Positive annual supply pressure gently lowers the commodity's mean-reversion target. */
    public static double nextCommodityPrice(
            Commodity commodity,
            double currentPrice,
            Regime regime,
            long seed,
            long day,
            double annualSupplyPressure) {
        double safePressure = clamp(annualSupplyPressure, -0.01, 0.01);
        double targetPrice = commodity.anchorPrice()
                * commodityRegimeMultiplier(commodity.id(), regime)
                * (1.0 - safePressure);
        double safeCurrent = clamp(currentPrice, commodity.anchorPrice() * 0.20, commodity.anchorPrice() * 5.0);
        double dailySigma = commodity.annualVolatility() / SQRT_DAYS_PER_YEAR;
        double meanReversion = 0.0030 * (StrictMath.log(targetPrice) - StrictMath.log(safeCurrent));
        double logReturn = meanReversion
                - 0.5 * dailySigma * dailySigma
                + dailySigma * gaussian(mix(seed, day, COMMODITY_SALT ^ stableHash(commodity.id())));
        double next = safeCurrent * StrictMath.exp(clamp(logReturn, -0.25, 0.25));
        return clamp(next, commodity.anchorPrice() * 0.20, commodity.anchorPrice() * 5.0);
    }

    public static long resourceExchangeValueMicro(
            String resourceId,
            int count,
            Map<String, Double> prices) {
        if (count <= 0) {
            return 0L;
        }
        double unitValue = switch (resourceId.toLowerCase(Locale.ROOT)) {
            case "diamond", "diamond_ore", "deepslate_diamond_ore" -> price(prices, "diamond");
            case "diamond_block" -> 9.0 * price(prices, "diamond");
            case "gold", "gold_ingot", "raw_gold", "gold_ore", "deepslate_gold_ore" ->
                    price(prices, "gold");
            case "nether_gold_ore" -> 0.5 * price(prices, "gold");
            case "gold_block", "raw_gold_block" -> 9.0 * price(prices, "gold");
            case "ancient_debris", "netherite_scrap" -> price(prices, "netherite");
            case "netherite", "netherite_ingot" ->
                    4.0 * price(prices, "netherite") + 4.0 * price(prices, "gold");
            case "netherite_block" -> 9.0
                    * (4.0 * price(prices, "netherite") + 4.0 * price(prices, "gold"));
            case "emerald_ore", "deepslate_emerald_ore" -> price(prices, "emerald_ore");
            case "emerald_block" -> 9.0;
            default -> -1.0;
        };
        if (unitValue < 0.0) {
            return -1L;
        }
        double microValue = unitValue * count * EconomyState.MICRO;
        if (!Double.isFinite(microValue) || microValue > Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return Math.max(0L, Math.round(microValue));
    }

    static double gaussianForTesting(long key) {
        return gaussian(key);
    }

    private static double commodityRegimeMultiplier(String commodityId, Regime regime) {
        return switch (commodityId) {
            case "diamond" -> switch (regime) {
                case BULL -> 1.10;
                case BOOM -> 1.22;
                case RECESSION -> 0.86;
                case CRASH -> 0.72;
                case RECOVERY -> 1.08;
                default -> 1.00;
            };
            case "gold" -> switch (regime) {
                case BULL, BOOM -> 0.94;
                case RECESSION -> 1.12;
                case CRASH -> 1.30;
                case RECOVERY -> 1.05;
                default -> 1.00;
            };
            case "netherite" -> switch (regime) {
                case BULL -> 1.12;
                case BOOM -> 1.30;
                case RECESSION -> 0.90;
                case CRASH -> 1.18;
                case RECOVERY -> 1.12;
                default -> 1.00;
            };
            case "emerald_ore" -> switch (regime) {
                case BOOM -> 1.08;
                case RECESSION -> 0.96;
                case CRASH -> 0.92;
                default -> 1.00;
            };
            default -> 1.00;
        };
    }

    private static double price(Map<String, Double> prices, String key) {
        return prices.getOrDefault(key, 0.0);
    }

    private static Regime pick(double draw, Regime[] regimes, double[] probabilities) {
        double cumulative = 0.0;
        for (int index = 0; index < regimes.length; index++) {
            cumulative += probabilities[index];
            if (draw < cumulative) {
                return regimes[index];
            }
        }
        return regimes[regimes.length - 1];
    }

    private static double gaussian(long key) {
        double first = Math.max(1.0e-12, unit(mix64(key ^ 0xD1B54A32D192ED03L)));
        double second = unit(mix64(key ^ 0x94D049BB133111EBL));
        return StrictMath.sqrt(-2.0 * StrictMath.log(first)) * StrictMath.cos(2.0 * StrictMath.PI * second);
    }

    private static long mix(long seed, long day, long salt) {
        return mix64(seed ^ (day * 0x9E3779B97F4A7C15L) ^ salt);
    }

    private static long mix64(long value) {
        long mixed = value;
        mixed = (mixed ^ (mixed >>> 30)) * 0xBF58476D1CE4E5B9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94D049BB133111EBL;
        return mixed ^ (mixed >>> 31);
    }

    private static long stableHash(String value) {
        long hash = 0xcbf29ce484222325L;
        for (int index = 0; index < value.length(); index++) {
            hash ^= value.charAt(index);
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    private static double unit(long value) {
        return (value >>> 11) * 0x1.0p-53;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
