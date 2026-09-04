package com.chedidandrew.emeraldstandard.core;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/** Statistical and invariant checks for the deterministic economy. */
public final class EconomyRegressionTest {
    private EconomyRegressionTest() {
    }

    public static void main(String[] args) {
        testGaussian();
        testDeterminism();
        testMarketAndAssets();
        testMarketEvents();
        testRegimeDuration();
        testLoanRisk();
        testLoanTermEconomics();
        testCommodities();
        testResourceQuotes();
        System.out.println("PASS EconomyRegressionTest");
    }

    private static void testMarketEvents() {
        long seed = 0x4556454E54544553L;
        int events = 0;
        for (long day = 1; day <= 100_000L; day++) {
            EconomyEngine.MarketEvent first = EconomyEngine.marketEvent(
                    seed, day, EconomyEngine.Regime.EXPANSION);
            EconomyEngine.MarketEvent second = EconomyEngine.marketEvent(
                    seed, day, EconomyEngine.Regime.EXPANSION);
            require(first == second, "Market events are not deterministic");
            events += first == EconomyEngine.MarketEvent.NONE ? 0 : 1;
        }
        require(events > 70 && events < 180,
                "Market-event frequency is outside target: " + events);
        require(EconomyEngine.eventAssetReturn(
                        EconomyEngine.MarketEvent.REDSTONE_REVOLUTION, "RSDN") > 0.0,
                "Redstone Revolution does not affect Redstone Dynamics");
        require(EconomyEngine.eventCommodityReturn(
                        EconomyEngine.MarketEvent.NETHER_SUPPLY_CRISIS, "netherite") > 0.0,
                "Nether crisis does not affect netherite");
        double weight = EconomyEngine.ASSETS.stream()
                .mapToDouble(asset -> EconomyEngine.vilxWeight(asset.ticker()))
                .sum();
        require(Math.abs(weight - 1.0) < 1.0e-9, "VILX constituent weights do not sum to one");
    }

    private static void testGaussian() {
        int samples = 500_000;
        double sum = 0.0;
        double sumSquares = 0.0;
        double minimum = Double.POSITIVE_INFINITY;
        double maximum = Double.NEGATIVE_INFINITY;
        for (int sample = 0; sample < samples; sample++) {
            double value = EconomyEngine.gaussianForTesting(
                    0x9E3779B97F4A7C15L * sample + 0x454D4552414C44L);
            sum += value;
            sumSquares += value * value;
            minimum = Math.min(minimum, value);
            maximum = Math.max(maximum, value);
        }
        double mean = sum / samples;
        double deviation = StrictMath.sqrt(sumSquares / samples - mean * mean);
        require(Math.abs(mean) < 0.012, "Gaussian mean is biased: " + mean);
        require(deviation > 0.975 && deviation < 1.025,
                "Gaussian deviation is wrong: " + deviation);
        require(minimum < -3.5 && maximum > 3.5, "Gaussian tails are truncated");
        System.out.printf("Gaussian mean %.5f, deviation %.5f%n", mean, deviation);
    }

    private static void testDeterminism() {
        EconomyState first = EconomyState.fresh(918_273_645L, 1_000L, 0L);
        EconomyState second = EconomyState.fresh(918_273_645L, 1_000L, 0L);
        for (int day = 0; day < 20_000; day++) {
            first.advanceOneDay();
            second.advanceOneDay();
        }
        require(first.regime == second.regime, "Regimes diverged");
        require(first.prices.equals(second.prices), "Asset prices diverged");
        require(first.commodityPrices.equals(second.commodityPrices),
                "Commodity prices diverged");
    }

    private static void testMarketAndAssets() {
        int seeds = 250;
        int years = 75;
        double[] cagrTotals = new double[EconomyEngine.ASSETS.size()];
        double[] vilxCagrs = new double[seeds];
        int negativeYears = 0;
        int totalYears = 0;
        double worstYear = Double.POSITIVE_INFINITY;
        double bestYear = Double.NEGATIVE_INFINITY;

        for (int seed = 0; seed < seeds; seed++) {
            EconomyState state = EconomyState.fresh(seed, 0L, 0L);
            UUID indexHolder = new UUID(0x56494C58L, seed);
            EconomyState.Account splitAdjusted = state.account(indexHolder);
            for (EconomyEngine.Asset asset : EconomyEngine.ASSETS) {
                splitAdjusted.shares.put(asset.ticker(), 1.0);
            }
            for (int year = 0; year < years; year++) {
                double openingVilx = state.prices.get("VILX")
                        * splitAdjusted.shares.get("VILX");
                for (int day = 1; day <= EconomyEngine.DAYS_PER_YEAR; day++) {
                    state.advanceOneDay();
                    for (EconomyEngine.Asset asset : EconomyEngine.ASSETS) {
                        double price = state.prices.get(asset.ticker());
                        require(Double.isFinite(price) && price > 0.0,
                                "Invalid asset price");
                    }
                }
                double annual = state.prices.get("VILX")
                        * splitAdjusted.shares.get("VILX") / openingVilx - 1.0;
                negativeYears += annual < 0.0 ? 1 : 0;
                totalYears++;
                worstYear = Math.min(worstYear, annual);
                bestYear = Math.max(bestYear, annual);
            }
            for (int asset = 0; asset < EconomyEngine.ASSETS.size(); asset++) {
                String ticker = EconomyEngine.ASSETS.get(asset).ticker();
                double value = state.prices.get(ticker) * splitAdjusted.shares.get(ticker);
                double cagr = StrictMath.pow(value / 100.0, 1.0 / years) - 1.0;
                cagrTotals[asset] += cagr;
                if (asset == 0) {
                    vilxCagrs[seed] = cagr;
                }
            }
        }

        Arrays.sort(vilxCagrs);
        double vilxMean = Arrays.stream(vilxCagrs).average().orElseThrow();
        double negativeRate = negativeYears / (double) totalYears;
        require(vilxMean > 0.08 && vilxMean < 0.13,
                "VILX CAGR is outside target: " + vilxMean);
        require(negativeRate > 0.20 && negativeRate < 0.38,
                "Negative-year frequency is outside target: " + negativeRate);
        require(worstYear < -0.40, "No severe bear-market year appeared");
        require(bestYear > 0.35, "No strong bull-market year appeared");

        System.out.printf(
                "VILX mean CAGR %.2f%%, p05 %.2f%%, median %.2f%%, p95 %.2f%%, negative years %.1f%%, annual range %.1f%% to %.1f%%%n",
                vilxMean * 100.0,
                vilxCagrs[(int) (seeds * 0.05)] * 100.0,
                vilxCagrs[seeds / 2] * 100.0,
                vilxCagrs[(int) (seeds * 0.95)] * 100.0,
                negativeRate * 100.0,
                worstYear * 100.0,
                bestYear * 100.0);

        for (int asset = 0; asset < cagrTotals.length; asset++) {
            double mean = cagrTotals[asset] / seeds;
            String ticker = EconomyEngine.ASSETS.get(asset).ticker();
            require(mean > 0.04 && mean < 0.20,
                    ticker + " has implausible long-run CAGR: " + mean);
            System.out.printf("%s mean CAGR %.2f%%%n", ticker, mean * 100.0);
        }
    }

    private static void testRegimeDuration() {
        long seed = 7_654_321L;
        EconomyEngine.Regime regime = EconomyEngine.initialRegime(seed);
        Map<EconomyEngine.Regime, Long> days = new EnumMap<>(EconomyEngine.Regime.class);
        Map<EconomyEngine.Regime, Integer> runs = new EnumMap<>(EconomyEngine.Regime.class);
        runs.merge(regime, 1, Integer::sum);
        for (long day = 1; day <= 1_000_000L; day++) {
            days.merge(regime, 1L, Long::sum);
            EconomyEngine.Regime next = EconomyEngine.nextRegime(regime, seed, day);
            if (next != regime) {
                runs.merge(next, 1, Integer::sum);
            }
            regime = next;
        }
        for (EconomyEngine.Regime value : EconomyEngine.Regime.values()) {
            double average = days.getOrDefault(value, 0L)
                    / (double) runs.getOrDefault(value, 1);
            require(average > (value == EconomyEngine.Regime.CRASH ? 15.0 : 70.0),
                    value + " regimes are too short: " + average);
        }
    }

    private static void testLoanRisk() {
        int samples = 100_000;
        int defaults = 0;
        int fullDefaults = 0;
        double recovery = 0.0;
        for (int sample = 0; sample < samples; sample++) {
            EconomyEngine.LoanResolution resolution = EconomyEngine.resolveLoan(
                    12_345L,
                    new UUID(sample, ~sample),
                    sample,
                    sample,
                    365,
                    50.0);
            if (resolution.outcome() != EconomyEngine.LoanOutcome.REPAID) {
                defaults++;
                recovery += resolution.recoveryRate();
                fullDefaults += resolution.outcome() == EconomyEngine.LoanOutcome.FULL_DEFAULT
                        ? 1 : 0;
            }
        }
        double defaultRate = defaults / (double) samples;
        double fullDefaultRate = fullDefaults / (double) samples;
        double conditionalRecovery = recovery / defaults;
        require(defaultRate > 0.04 && defaultRate < 0.15,
                "Loan default frequency is outside target");
        require(fullDefaultRate > 0.002 && fullDefaultRate < 0.03,
                "Full-default frequency is outside target");
        require(conditionalRecovery > 0.45 && conditionalRecovery < 0.75,
                "Loan recovery is outside target");
        System.out.printf(
                "Loans %.2f%% default, %.2f%% full default, %.1f%% conditional recovery%n",
                defaultRate * 100.0,
                fullDefaultRate * 100.0,
                conditionalRecovery * 100.0);
    }

    private static void testLoanTermEconomics() {
        int[] terms = {30, 90, 180, 365};
        double previous = Double.NEGATIVE_INFINITY;
        for (int term : terms) {
            int samples = 50_000;
            double payoutTotal = 0.0;
            for (int sample = 0; sample < samples; sample++) {
                long seed = 0x9E3779B97F4A7C15L * (sample + 1L);
                EconomyEngine.Regime opening = EconomyEngine.initialRegime(seed);
                EconomyEngine.Regime regime = opening;
                double stress = 0.0;
                for (int day = 1; day <= term; day++) {
                    regime = EconomyEngine.nextRegime(regime, seed, day);
                    stress += EconomyEngine.loanStressIncrement(regime);
                }
                double annualRate = EconomyEngine.villagerLoanAnnualYield(opening, term);
                double maturedClaim = StrictMath.pow(1.0 + annualRate, term / 365.0);
                EconomyEngine.LoanResolution resolution = EconomyEngine.resolveLoan(
                        seed,
                        new UUID(sample, ~sample),
                        sample + 1L,
                        0L,
                        term,
                        stress);
                payoutTotal += maturedClaim * resolution.recoveryRate();
            }
            double expectedMultiple = payoutTotal / samples;
            double annualized = StrictMath.pow(expectedMultiple, 365.0 / term) - 1.0;
            require(annualized > 0.035,
                    term + "-day villager lending underpays savings after risk: " + annualized);
            require(annualized + 0.01 >= previous,
                    "Longer villager lending terms do not compensate for risk");
            previous = annualized;
            System.out.printf(
                    "%d-day villager lending expected annualized return %.2f%%%n",
                    term,
                    annualized * 100.0);
        }
    }

    private static void testCommodities() {
        EconomyState state = EconomyState.fresh(998_877L, 0L, 0L);
        Map<String, Double> opening = Map.copyOf(state.commodityPrices);
        for (int day = 0; day < 5_000; day++) {
            state.advanceOneDay();
        }
        boolean changed = false;
        for (EconomyEngine.Commodity commodity : EconomyEngine.COMMODITIES) {
            double price = state.commodityPrices.get(commodity.id());
            require(Double.isFinite(price) && price > 0.0, "Invalid commodity price");
            changed |= Math.abs(price - opening.get(commodity.id())) > 0.01;
        }
        require(changed, "Commodity market did not move");
    }

    private static void testResourceQuotes() {
        EconomyState state = EconomyState.fresh(123L, 0L, 0L);
        long diamond = EconomyEngine.resourceExchangeValueMicro(
                "diamond", 1, state.commodityPrices);
        long diamondBlock = EconomyEngine.resourceExchangeValueMicro(
                "diamond_block", 1, state.commodityPrices);
        long gold = EconomyEngine.resourceExchangeValueMicro(
                "gold_ingot", 1, state.commodityPrices);
        long goldBlock = EconomyEngine.resourceExchangeValueMicro(
                "gold_block", 1, state.commodityPrices);
        long rawGoldBlock = EconomyEngine.resourceExchangeValueMicro(
                "raw_gold_block", 1, state.commodityPrices);
        long netherGoldOre = EconomyEngine.resourceExchangeValueMicro(
                "nether_gold_ore", 1, state.commodityPrices);
        long netheriteIngot = EconomyEngine.resourceExchangeValueMicro(
                "netherite_ingot", 1, state.commodityPrices);
        long netheriteBlock = EconomyEngine.resourceExchangeValueMicro(
                "netherite_block", 1, state.commodityPrices);

        require(diamondBlock == 9L * diamond, "Diamond block quote is inconsistent");
        require(goldBlock == 9L * gold, "Gold block quote is inconsistent");
        require(rawGoldBlock == goldBlock, "Raw gold block quote is inconsistent");
        require(netherGoldOre == gold / 2L, "Nether gold ore quote is inconsistent");
        require(netheriteBlock == 9L * netheriteIngot,
                "Netherite block quote is inconsistent");
        require(EconomyEngine.resourceExchangeValueMicro(
                        "unsupported", 1, state.commodityPrices) == -1L,
                "Unsupported resource received a quote");
        require(EconomyEngine.resourceExchangeValueMicro(
                        null, 1, state.commodityPrices) == -1L
                        && EconomyEngine.resourceExchangeValueMicro(
                                "diamond", 1, null) == -1L,
                "Malformed resource quote input was not rejected safely");
        Map<String, Double> malformedPrices = new java.util.HashMap<>(state.commodityPrices);
        malformedPrices.put("diamond", null);
        require(EconomyEngine.resourceExchangeValueMicro(
                        "diamond", 1, malformedPrices) == 0L,
                "Missing commodity data produced an unsafe quote");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
