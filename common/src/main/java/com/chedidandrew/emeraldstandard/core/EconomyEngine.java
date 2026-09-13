package com.chedidandrew.emeraldstandard.core;

import java.util.ArrayList;
import java.util.HashMap;
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

    public enum AssetType { STOCK, INDEX, FUND, COMMODITY }

    public record Asset(
            String ticker,
            String name,
            String sector,
            double beta,
            double annualAlpha,
            double annualIdiosyncraticVolatility,
            AssetType type,
            String commodityId) {
        public Asset(String ticker, String name, String sector, double beta,
                double annualAlpha, double annualIdiosyncraticVolatility) {
            this(ticker, name, sector, beta, annualAlpha, annualIdiosyncraticVolatility,
                    AssetType.STOCK, "");
        }

        public Asset {
            ticker = ticker.toUpperCase(Locale.ROOT);
            if (type == null || commodityId == null
                    || (type == AssetType.COMMODITY) != !commodityId.isEmpty()) {
                throw new IllegalArgumentException("Invalid investment classification");
            }
        }

        public boolean isCommodity() { return type == AssetType.COMMODITY; }
    }

    public static final List<Asset> ASSETS = List.of(
            new Asset("VILX", "Villager Stock Exchange Index", "Diversified", 1.00, 0.000, 0.00, AssetType.INDEX, ""),
            new Asset("RSDN", "Redstone Dynamics", "Redstone Technology", 1.30, 0.015, 0.22),
            new Asset("DPMN", "Deepdelve Mining", "Mining", 1.15, 0.005, 0.24),
            new Asset("NSPC", "Nether Spice Company", "Nether Trade", 1.25, 0.012, 0.28),
            new Asset("ENDR", "Ender Freight & Logistics", "Transportation", 1.05, 0.008, 0.19),
            new Asset("GLDH", "Golden Harvest Cooperative", "Agriculture", 0.70, 0.000, 0.13),
            new Asset("POTN", "Potionworks Laboratories", "Alchemy", 0.95, 0.006, 0.18),
            new Asset("IRNG", "Iron Golem Security", "Village Services", 0.75, 0.001, 0.14),
            new Asset("MCRT", "Minecart Transit", "Transportation", 0.85, 0.002, 0.15),
            new Asset("TREA", "Village Treasury Fund", "Government Debt", -0.05, 0.000, 0.025, AssetType.FUND, ""),
            new Asset("AURM", "Aurum Reserve Company", "Defensive Reserves", -0.35, 0.012, 0.17),
            new Asset("BRCK", "Masons & Works", "Construction", 1.65, 0.006, 0.23),
            new Asset("FISH", "Tidewater Fisheries", "Fishing", 0.20, 0.025, 0.17),
            new Asset("VENT", "Frontier Expedition Ventures", "Exploration", 0.45, 0.085, 0.34),
            commodityAsset("GOLD", "Gold", "Precious Metals", "gold", 0.18),
            commodityAsset("IRON", "Iron", "Industrial Metals", "iron", 0.32),
            commodityAsset("COAL", "Coal", "Fuel", "coal", 0.45),
            commodityAsset("DIAM", "Diamond", "Gemstones", "diamond", 0.22),
            commodityAsset("COPR", "Copper", "Industrial Metals", "copper", 0.28),
            commodityAsset("RDST", "Redstone", "Technology Inputs", "redstone", 0.35),
            commodityAsset("LAPS", "Lapis Lazuli", "Enchanting Inputs", "lapis", 0.25),
            commodityAsset("NETH", "Netherite Scrap", "Nether Materials", "netherite", 0.30),
            new Asset("VCIX", "Villager Commodity Index", "Commodity Basket", 0, 0, 0.20, AssetType.INDEX, ""));

    private static Asset commodityAsset(String ticker, String name, String sector,
            String commodityId, double volatility) {
        return new Asset(ticker, name, sector, 0, 0, volatility, AssetType.COMMODITY, commodityId);
    }

    public record Commodity(String id, String name, double anchorPrice, double annualVolatility) {
    }

    public static final List<Commodity> COMMODITIES = List.of(
            new Commodity("diamond", "Diamond", 12.0, 0.22),
            new Commodity("gold", "Gold Ingot", 2.0, 0.18),
            new Commodity("netherite", "Netherite Scrap", 20.0, 0.40),
            new Commodity("emerald_ore", "Emerald Ore", 1.0, 0.08),
            new Commodity("iron", "Iron Ingot", 0.75, 0.32),
            new Commodity("coal", "Coal", 0.25, 0.45),
            new Commodity("copper", "Copper Ingot", 0.30, 0.28),
            new Commodity("redstone", "Redstone Dust", 0.20, 0.35),
            new Commodity("lapis", "Lapis Lazuli", 0.40, 0.24));

    public static boolean isNewCommodity(String id) {
        return switch (id) {
            case "iron", "coal", "copper", "redstone", "lapis" -> true;
            default -> false;
        };
    }

    public static double initialAssetPrice(Asset asset) {
        if (!asset.isCommodity()) return 100.0;
        return COMMODITIES.stream().filter(c -> c.id().equals(asset.commodityId()))
                .findFirst().orElseThrow().anchorPrice();
    }

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
        DEEPVEIN_DISCOVERY("Deepvein Discovery", "A major ore discovery reshaped mining expectations."),
        PORTAL_REOPENING("Portal Trade Resumes", "Off-screen Nether trade routes reopened after maintenance."),
        RAIL_DISRUPTION("Rail Freight Disruption", "An off-screen freight stoppage raised delivery costs."),
        COPPER_GRID_BUILDOUT("Copper Grid Buildout", "A regional automation buildout increased demand for wiring and machinery."),
        COAL_SURPLUS("Coal Supply Glut", "Increased off-screen fuel production outpaced demand."),
        ENCHANTING_FESTIVAL("Enchanting Festival", "A regional enchanting fair increased demand for magical supplies."),
        LUXURY_DEMAND_SLUMP("Luxury Demand Slump", "Weaker off-screen luxury demand reduced gemstone spending."),
        FISHERY_RECOVERY("Fishery Recovery", "An off-screen fishery recovered supply after a seasonal closure."),
        POTION_RECALL("Potion Recall", "An off-screen batch recall disrupted alchemy sales."),
        BANK_STRESS_TEST("Bank Stress Test", "Regional lenders passed a stress test, easing credit concerns."),
        REGIONAL_REBUILDING("Regional Rebuilding", "An off-screen reconstruction program increased materials demand.");

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
        return marketReturn(regime,seed,day,1.0,0);
    }
    static double marketReturn(Regime regime,long seed,long day,double fraction,int slot) {
        double cycle = switch (regime) {
            case EXPANSION -> 0; case BULL -> .035; case BOOM -> .065;
            case STAGNATION -> -.025; case RECESSION -> -.10;
            case CRASH -> -.32; case RECOVERY -> .07;
        };
        double dailySigma = regime.annualVolatility() * InvestmentGrowth.turbulence(seed, day) / SQRT_DAYS_PER_YEAR * StrictMath.sqrt(fraction);
        double logReturn = StrictMath.log1p(InvestmentGrowth.target(seed, day, "VILX") + cycle) / DAYS_PER_YEAR * fraction
                - 0.5 * dailySigma * dailySigma
                + dailySigma * gaussian(mix(seed, day, slotSalt(MARKET_SALT,slot)));

        double jumpDraw = unit(mix(seed, day, MARKET_JUMP_SALT));
        if (jumpSlot(seed,day,MARKET_JUMP_SALT,slot) && jumpDraw < 0.00015) {
            logReturn -= 0.08 + 0.15 * unit(mix(seed, day, 91L));
        } else if (jumpSlot(seed,day,MARKET_JUMP_SALT,slot) && jumpDraw > 0.99995) {
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
        return assetReturn(asset, marketReturn, seed, day, annualVillageDrift,
                Regime.EXPANSION, Regime.EXPANSION);
    }

    /** Company-specific fundamentals share market cycles, not guaranteed annual outcomes. */
    public static double assetReturn(
            Asset asset, double marketReturn, long seed, long day, double annualVillageDrift,
            Regime previousRegime, Regime currentRegime) {
        return assetReturn(asset,marketReturn,seed,day,annualVillageDrift,previousRegime,currentRegime,1.0,0);
    }
    static double assetReturn(Asset asset,double marketReturn,long seed,long day,double annualVillageDrift,
            Regime previousRegime,Regime currentRegime,double fraction,int slot) {
        if (asset.isCommodity()) {
            throw new IllegalArgumentException("Commodity holdings use their underlying quote");
        }
        if (asset.type() == AssetType.INDEX) {
            throw new IllegalArgumentException("Index prices must be derived from their constituents");
        }

        double marketLogReturn = StrictMath.log1p(clamp(marketReturn, -0.999999, Double.MAX_VALUE));
        double riskFreeDailyLogReturn = StrictMath.log1p(RISK_FREE_ANNUAL_RATE) / DAYS_PER_YEAR * fraction;
        double idiosyncraticSigma = asset.annualIdiosyncraticVolatility() / SQRT_DAYS_PER_YEAR * StrictMath.sqrt(fraction);
        double idiosyncraticShock = idiosyncraticSigma
                * gaussian(mix(seed, day, slotSalt(stableHash(asset.ticker()),slot)));
        double safeVillageDrift = clamp(annualVillageDrift, -0.012, 0.012);

        double assetLogReturn = riskFreeDailyLogReturn
                + asset.beta() * (marketLogReturn - riskFreeDailyLogReturn)
                + (asset.type() == AssetType.FUND ? 0 :
                    StrictMath.log1p(InvestmentGrowth.target(seed, day, asset.ticker()))
                    - StrictMath.log1p(RISK_FREE_ANNUAL_RATE)
                    - asset.beta() * (StrictMath.log1p(InvestmentGrowth.target(seed, day, "VILX"))
                        - StrictMath.log1p(RISK_FREE_ANNUAL_RATE))) / DAYS_PER_YEAR * fraction
                + StrictMath.log1p(safeVillageDrift) / DAYS_PER_YEAR * fraction
                - 0.5 * idiosyncraticSigma * idiosyncraticSigma
                + idiosyncraticShock;

        double specialist=specialistLogReturn(asset.ticker(),seed,day,previousRegime,currentRegime);
        if (slot==0) assetLogReturn+=specialist;
        else if (asset.ticker().equals("VENT")) {
            if(jumpSlot(seed,day,0x56454E54555245L,slot))assetLogReturn+=specialist;
        } else if(asset.ticker().equals("TREA")) {
            double rateJump=-2.0*(savingsAnnualRate(currentRegime)-savingsAnnualRate(previousRegime));
            assetLogReturn+=(specialist-rateJump)*fraction+(slot==1?rateJump:0);
        } else assetLogReturn+=specialist*fraction;
        return StrictMath.expm1(clamp(assetLogReturn, -0.70, 0.50));
    }

    private static double specialistLogReturn(
            String ticker, long seed, long day, Regime previous, Regime current) {
        return switch (ticker) {
            // Total-return share price: income accrues in NAV, not a second cash payout.
            // A short duration of two years makes rate rises hurt and rate cuts help.
            case "TREA" -> (StrictMath.log1p(savingsAnnualRate(current) - 0.002)
                    - StrictMath.log1p(RISK_FREE_ANNUAL_RATE)) / DAYS_PER_YEAR
                    - 2.0 * (savingsAnnualRate(current) - savingsAnnualRate(previous));
            // A reserve business, not a promise to redeem shares for physical gold.
            case "AURM" -> switch (current) {
                case RECESSION, CRASH -> 0.05 / DAYS_PER_YEAR;
                case BULL, BOOM -> -0.01 / DAYS_PER_YEAR;
                default -> 0.0;
            };
            case "BRCK" -> switch (current) {
                case BOOM, RECOVERY -> 0.12 / DAYS_PER_YEAR;
                case RECESSION, CRASH -> -0.12 / DAYS_PER_YEAR;
                case STAGNATION -> -0.04 / DAYS_PER_YEAR;
                default -> 0.02 / DAYS_PER_YEAR;
            };
            case "FISH" -> {
                long salt = stableHash(ticker);
                int period = 84 + (int) (unit(mix64(seed ^ salt)) * 49);
                double phase = 2.0 * StrictMath.PI * unit(mix64(seed ^ salt ^ 0x4341544348L));
                double angle = 2.0 * StrictMath.PI * Math.floorMod(day, period) / period + phase;
                // Bounded catch cycle, not an ever-growing seasonal subsidy. Actual weather is unrelated.
                yield 0.10 * (StrictMath.sin(angle)
                        - StrictMath.sin(angle - 2.0 * StrictMath.PI / period));
            }
            case "VENT" -> {
                double draw = unit(mix(seed, day, 0x56454E54555245L));
                double size = unit(mix(seed, day, 0x56454E5453495AL));
                if (draw < 0.0025) yield 0.08 + 0.16 * size;
                if (draw > 0.9980) yield -(0.10 + 0.20 * size);
                yield 0.0;
            }
            default -> 0.0;
        };
    }

    /** Includes negative-beta volatility and expedition jump risk; lower risk is not no risk. */
    public static String riskBand(Asset asset) {
        if (asset.ticker().equals("VENT")) return "very_high";
        double sigma = StrictMath.hypot(asset.beta() * 0.18,
                asset.annualIdiosyncraticVolatility());
        if (sigma < 0.11) return "lower";
        if (sigma < 0.20) return "moderate";
        if (sigma < 0.30) return "high";
        return "very_high";
    }

    public static boolean isSpecialist(String ticker) {
        return switch (ticker) {
            case "TREA", "AURM", "BRCK", "FISH", "VENT" -> true;
            default -> false;
        };
    }

    public static MarketEvent marketEvent(long seed, long day, Regime regime) {
        double draw = unit(mix(seed, day, EVENT_SALT));
        double probability = switch (regime) {
            case BOOM, CRASH -> 0.035;
            case BULL, RECESSION, RECOVERY -> 0.025;
            default -> 0.018;
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
                MarketEvent.DEEPVEIN_DISCOVERY,
                MarketEvent.PORTAL_REOPENING,
                MarketEvent.RAIL_DISRUPTION,
                MarketEvent.COPPER_GRID_BUILDOUT,
                MarketEvent.COAL_SURPLUS,
                MarketEvent.ENCHANTING_FESTIVAL,
                MarketEvent.LUXURY_DEMAND_SLUMP,
                MarketEvent.FISHERY_RECOVERY,
                MarketEvent.POTION_RECALL,
                MarketEvent.BANK_STRESS_TEST,
                MarketEvent.REGIONAL_REBUILDING
        };
        int index = Math.min(
                events.length - 1,
                (int) (unit(mix(seed, day, EVENT_SALT ^ 0x4944454E54495459L))
                        * events.length));
        return events[index];
    }

    public static double eventAssetReturn(MarketEvent event, String ticker) {
        // Index news is already reflected in constituent quotes; never apply a second shock.
        if ("VILX".equals(ticker) || "VCIX".equals(ticker)) return 0.0;
        if(event.ordinal()>=MarketEvent.PORTAL_REOPENING.ordinal()) return switch(event) {
            case PORTAL_REOPENING -> ticker.equals("NSPC") ? -0.035 : ticker.equals("ENDR") ? 0.045 : 0;
            case RAIL_DISRUPTION -> ticker.equals("MCRT") ? -0.07 : ticker.equals("ENDR") ? -0.025 : 0;
            case COPPER_GRID_BUILDOUT -> ticker.equals("RSDN") ? 0.055 : ticker.equals("BRCK") ? 0.035 : 0;
            case COAL_SURPLUS -> ticker.equals("DPMN") ? -0.025 : ticker.equals("MCRT") ? 0.02 : 0;
            case ENCHANTING_FESTIVAL -> ticker.equals("POTN") ? 0.055 : ticker.equals("ENDR") ? 0.02 : 0;
            case LUXURY_DEMAND_SLUMP -> ticker.equals("DPMN") ? -0.045 : ticker.equals("AURM") ? 0.015 : 0;
            case FISHERY_RECOVERY -> ticker.equals("FISH") ? 0.075 : ticker.equals("GLDH") ? -0.012 : 0;
            case POTION_RECALL -> ticker.equals("POTN") ? -0.085 : ticker.equals("IRNG") ? 0.01 : 0;
            case BANK_STRESS_TEST -> ticker.equals("BRCK") ? 0.04 : ticker.equals("VENT") ? 0.035 : 0;
            case REGIONAL_REBUILDING -> ticker.equals("BRCK") ? 0.06 : ticker.equals("IRNG") ? 0.025 : 0;
            default -> 0;
        };
        return switch (event) {
            default -> 0;
            case NONE -> 0.0;
            case REDSTONE_REVOLUTION -> switch (ticker) {
                case "RSDN" -> 0.10;
                case "MCRT" -> 0.025;
                default -> 0.0;
            };
            case NETHER_SUPPLY_CRISIS -> switch (ticker) {
                case "NSPC" -> 0.09;
                case "ENDR" -> -0.035;
                default -> 0.0;
            };
            case GOLDEN_HARVEST -> switch (ticker) {
                case "GLDH" -> 0.075;
                case "FISH" -> -0.015;
                default -> 0.0;
            };
            case END_EXPEDITION_BOOM -> switch (ticker) {
                case "ENDR" -> 0.085;
                case "VENT" -> 0.12;
                case "POTN" -> 0.020;
                default -> 0.0;
            };
            case CREEPER_CATASTROPHE -> switch (ticker) {
                case "IRNG" -> 0.070;
                case "BRCK" -> 0.045;
                case "AURM" -> 0.025;
                case "TREA" -> 0.004;
                case "MCRT" -> -0.055;
                default -> -0.006;
            };
            case VILLAGER_CREDIT_SCARE -> switch (ticker) {
                case "TREA" -> 0.008;
                case "AURM" -> 0.055;
                case "BRCK" -> -0.08;
                case "FISH" -> -0.012;
                case "VENT" -> -0.10;
                default -> -0.050;
            };
            case DEEPVEIN_DISCOVERY -> switch (ticker) {
                case "DPMN" -> 0.085;
                default -> 0.0;
            };
        };
    }

    public static double eventCommodityReturn(MarketEvent event, String commodityId) {
        if(event.ordinal()>=MarketEvent.PORTAL_REOPENING.ordinal()) return switch(event) {
            case PORTAL_REOPENING -> commodityId.equals("netherite") ? -0.065 : 0;
            case RAIL_DISRUPTION -> commodityId.equals("iron") ? 0.02 : 0;
            case COPPER_GRID_BUILDOUT -> commodityId.equals("copper") ? 0.085 : 0;
            case COAL_SURPLUS -> commodityId.equals("coal") ? -0.12 : 0;
            case ENCHANTING_FESTIVAL -> commodityId.equals("lapis") ? 0.085 : 0;
            case LUXURY_DEMAND_SLUMP -> commodityId.equals("diamond") ? -0.09 : 0;
            case POTION_RECALL -> commodityId.equals("lapis") ? -0.03 : 0;
            case BANK_STRESS_TEST -> commodityId.equals("gold") ? -0.025 : 0;
            case REGIONAL_REBUILDING -> commodityId.equals("iron") ? 0.065 : 0;
            default -> 0;
        };
        if (isNewCommodity(commodityId)) {
            return switch (event) {
                case REDSTONE_REVOLUTION -> switch (commodityId) {
                    case "redstone" -> 0.10;
                    case "copper", "iron" -> 0.035;
                    default -> 0.0;
                };
                case CREEPER_CATASTROPHE -> switch (commodityId) {
                    case "iron", "copper" -> 0.06;
                    case "coal" -> 0.025;
                    default -> 0.0;
                };
                case DEEPVEIN_DISCOVERY -> commodityId.equals("lapis") ? -0.08 : -0.045;
                case END_EXPEDITION_BOOM -> commodityId.equals("lapis") ? 0.075 : 0.0;
                case VILLAGER_CREDIT_SCARE -> commodityId.equals("coal") ? -0.015 : -0.04;
                default -> 0.0;
            };
        }
        return switch (event) {
            case NETHER_SUPPLY_CRISIS -> commodityId.equals("netherite") ? 0.12 : 0.0;
            case GOLDEN_HARVEST -> commodityId.equals("gold") ? -0.025 : 0.0;
            case CREEPER_CATASTROPHE -> commodityId.equals("gold") ? 0.035 : 0.0;
            case VILLAGER_CREDIT_SCARE -> commodityId.equals("gold") ? 0.060 : -0.025;
            case DEEPVEIN_DISCOVERY -> commodityId.equals("diamond") ? -0.070 : 0.0;
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
        return nextCommodityPrice(commodity, currentPrice, regime, seed, day,
                annualSupplyPressure, commodity.anchorPrice());
    }

    public static double nextCommodityPrice(Commodity commodity, double currentPrice,
            Regime regime, long seed, long day, double annualSupplyPressure, double reference) {
        return nextCommodityPrice(commodity,currentPrice,regime,seed,day,annualSupplyPressure,reference,1.0,0);
    }
    static double nextCommodityPrice(Commodity commodity,double currentPrice,Regime regime,long seed,long day,
            double annualSupplyPressure,double reference,double fraction,int slot) {
        double safePressure = clamp(annualSupplyPressure, -0.01, 0.01);
        double targetPrice = reference
                * commodityRegimeMultiplier(commodity.id(), regime)
                * (1.0 - safePressure);
        double safeCurrent = clamp(currentPrice, 0.000001, 1.0e9);
        double dailySigma = commodity.annualVolatility() * InvestmentGrowth.turbulence(seed, day) / SQRT_DAYS_PER_YEAR * StrictMath.sqrt(fraction);
        double meanReversion = 0.0030 * (StrictMath.log(targetPrice) - StrictMath.log(safeCurrent)) * fraction;
        double correlation = commodity.id().equals("gold") ? -.25 : .60;
        double noise = correlation * gaussian(mix(seed, day, slotSalt(COMMODITY_SALT,slot)))
                + StrictMath.sqrt(1 - correlation * correlation)
                    * gaussian(mix(seed, day, slotSalt(COMMODITY_SALT ^ stableHash(commodity.id()),slot)));
        double logReturn = meanReversion
                - 0.5 * dailySigma * dailySigma
                + dailySigma * noise;
        double next = safeCurrent * StrictMath.exp(clamp(logReturn, -0.25, 0.25));
        return clamp(next, 0.000001, 1.0e9);
    }

    public static long resourceExchangeValueMicro(
            String resourceId,
            int count,
            Map<String, Double> prices) {
        if (count <= 0) {
            return 0L;
        }
        if (resourceId == null || prices == null) {
            return -1L;
        }
        double unitValue = resourceExchangeUnitValue(
                resourceId.toLowerCase(Locale.ROOT), prices);
        if (unitValue < 0.0) {
            return -1L;
        }
        double microValue = unitValue * count * EconomyState.MICRO;
        if (!Double.isFinite(microValue) || microValue > Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return Math.max(0L, Math.round(microValue));
    }

    /**
     * Builds the displayed quote history for any exchangeable resource.
     *
     * <p>Underlying commodity quotes are persisted. Ore variants, storage blocks, and
     * crafted netherite therefore derive their chart from the same pricing formula as the live
     * quote. Histories are aligned from their newest observations so legacy saves with shorter
     * series remain safe and accurate.</p>
     */
    public static List<Double> resourceExchangeHistory(
            String resourceId,
            Map<String, List<Double>> commodityHistory) {
        if (resourceId == null || commodityHistory == null) {
            return List.of();
        }
        String normalized = resourceId.toLowerCase(Locale.ROOT);
        List<String> dependencies = resourceCommodityDependencies(normalized);
        if (dependencies == null) {
            return List.of();
        }

        int historyLength;
        if (dependencies.isEmpty()) {
            // Emerald blocks have a fixed nine-emerald quote, but their chart should still span
            // the same economic days as the commodity market rather than appearing unavailable.
            historyLength = COMMODITIES.stream()
                    .filter(c -> !isNewCommodity(c.id()))
                    .map(Commodity::id)
                    .map(commodityHistory::get)
                    .filter(values -> values != null && !values.isEmpty())
                    .mapToInt(List::size)
                    .max()
                    .orElse(0);
        } else {
            historyLength = Integer.MAX_VALUE;
            for (String dependency : dependencies) {
                List<Double> values = commodityHistory.get(dependency);
                if (values == null || values.isEmpty()) {
                    return List.of();
                }
                historyLength = Math.min(historyLength, values.size());
            }
        }
        if (historyLength <= 0 || historyLength == Integer.MAX_VALUE) {
            return List.of();
        }

        List<Double> result = new ArrayList<>(historyLength);
        for (int index = 0; index < historyLength; index++) {
            Map<String, Double> prices = new HashMap<>();
            for (String dependency : dependencies) {
                List<Double> values = commodityHistory.get(dependency);
                Double value = values.get(values.size() - historyLength + index);
                if (value == null || !Double.isFinite(value) || value <= 0.0) {
                    return List.of();
                }
                prices.put(dependency, value);
            }
            double quote = resourceExchangeUnitValue(normalized, prices);
            if (!Double.isFinite(quote) || quote < 0.0) {
                return List.of();
            }
            result.add(quote);
        }
        return List.copyOf(result);
    }

    private static double resourceExchangeUnitValue(
            String normalizedResourceId,
            Map<String, Double> prices) {
        return switch (normalizedResourceId) {
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
    }

    private static List<String> resourceCommodityDependencies(String normalizedResourceId) {
        return switch (normalizedResourceId) {
            case "diamond", "diamond_block", "diamond_ore", "deepslate_diamond_ore" ->
                    List.of("diamond");
            case "gold", "gold_ingot", "raw_gold", "gold_ore", "deepslate_gold_ore",
                    "nether_gold_ore", "gold_block", "raw_gold_block" -> List.of("gold");
            case "ancient_debris", "netherite_scrap" -> List.of("netherite");
            case "netherite", "netherite_ingot", "netherite_block" ->
                    List.of("netherite", "gold");
            case "emerald_ore", "deepslate_emerald_ore" -> List.of("emerald_ore");
            case "emerald_block" -> List.of();
            default -> null;
        };
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
            case "iron" -> switch (regime) {
                case BOOM, RECOVERY -> 1.25;
                case BULL -> 1.12;
                case RECESSION -> 0.80;
                case CRASH -> 0.65;
                default -> 1.00;
            };
            case "copper", "redstone" -> switch (regime) {
                case BOOM -> 1.40;
                case BULL, RECOVERY -> 1.18;
                case RECESSION -> 0.78;
                case CRASH -> 0.60;
                default -> 1.00;
            };
            case "coal" -> switch (regime) {
                case BOOM, RECOVERY -> 1.10;
                case RECESSION, CRASH -> 0.90;
                default -> 1.00;
            };
            case "lapis" -> switch (regime) {
                case BULL, BOOM -> 1.15;
                case RECESSION, CRASH -> 0.88;
                default -> 1.00;
            };
            default -> 1.00;
        };
    }

    private static double price(Map<String, Double> prices, String key) {
        Double value = prices.get(key);
        return value != null && Double.isFinite(value) && value > 0.0 ? value : 0.0;
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

    private static long slotSalt(long salt,int slot) { return slot==0?salt:salt^mix64(slot*0x9E3779B97F4A7C15L); }
    private static boolean jumpSlot(long seed,long day,long salt,int slot) {
        return slot==0 || slot==1+(int)(unit(mix(seed,day,salt^0x534C4F54L))*LiveMarket.SLOTS);
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
