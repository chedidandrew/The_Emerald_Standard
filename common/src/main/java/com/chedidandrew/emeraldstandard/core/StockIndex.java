package com.chedidandrew.emeraldstandard.core;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** VILX capitalization basket. Simulated company float is independent of player holdings. */
public final class StockIndex {
    public static final List<String> CONSTITUENTS = EconomyEngine.ASSETS.stream()
            .filter(a -> a.type() == EconomyEngine.AssetType.STOCK)
            .map(EconomyEngine.Asset::ticker).toList();

    private StockIndex() {}

    /** Only new worlds and pre-34 migration may establish equal starting capitalization. */
    static void initialize(EconomyState state) {
        state.stockIndexShares.clear();
        for (String ticker : CONSTITUENTS) {
            state.stockIndexShares.put(ticker, 100.0 / positive(state.prices.get(ticker)));
        }
        // Anchor to the saved quote, not 100: upgrades must not manufacture a return.
        state.stockIndexDivisor = capitalization(state) / positive(state.prices.get("VILX"));
        state.stockIndexStartedDay = state.economicDay;
    }

    private static double positive(Double value) {
        if (value == null || !Double.isFinite(value) || value <= 0.0)
            throw new IllegalStateException("Invalid VILX capitalization input");
        return value;
    }

    private static double capitalization(EconomyState state) {
        double total = 0.0;
        for (String ticker : CONSTITUENTS) {
            total += positive(state.prices.get(ticker)) * positive(state.stockIndexShares.get(ticker));
        }
        return positive(total);
    }

    static double value(EconomyState state) {
        return positive(capitalization(state) / positive(state.stockIndexDivisor));
    }

    /** Reprice from actual final company quotes, including their news, drift and price floors. */
    static void reprice(EconomyState state) {
        state.prices.put("VILX", value(state));
    }

    public static Map<String, Double> weights(EconomyState state) {
        double total = capitalization(state);
        Map<String, Double> weights = new LinkedHashMap<>();
        for (String ticker : CONSTITUENTS) {
            weights.put(ticker, state.prices.get(ticker) * state.stockIndexShares.get(ticker) / total);
        }
        return java.util.Collections.unmodifiableMap(weights);
    }

    /** Indicative fundamental bias, not a promised CAGR or a second price-growth adjustment. */
    public static double growthTarget(EconomyState state, long day) {
        double target = 0.0;
        for (var entry : weights(state).entrySet()) {
            target += entry.getValue() * InvestmentGrowth.target(state.seed, day, entry.getKey());
        }
        return target;
    }

    /** Called with the same denomination factor used for prices, histories and owned shares. */
    static void split(EconomyState state, String ticker, double factor) {
        positive(factor);
        if (state.stockIndexShares.containsKey(ticker)) {
            state.stockIndexShares.put(ticker, positive(state.stockIndexShares.get(ticker) * factor));
        } else if ("VILX".equals(ticker)) {
            state.stockIndexDivisor = positive(state.stockIndexDivisor * factor);
        }
    }

    static void validate(EconomyState state) throws IOException {
        if (!state.stockIndexShares.keySet().equals(new java.util.HashSet<>(CONSTITUENTS))
                || state.stockIndexStartedDay < 0 || state.stockIndexStartedDay > state.economicDay)
            throw new IOException("Invalid VILX constituents or tracking start day");
        try {
            double expected = value(state);
            double quote = positive(state.prices.get("VILX"));
            if (Math.abs(expected / quote - 1.0) > 1.0e-9)
                throw new IOException("VILX quote does not match capitalization and divisor");
        } catch (IllegalStateException e) {
            throw new IOException("Invalid VILX capitalization", e);
        }
    }
}
