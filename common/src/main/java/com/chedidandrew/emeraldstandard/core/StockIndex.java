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

    /** New worlds and pre-34 migration establish a profile-weighted basket without a quote jump. */
    static void initialize(EconomyState state) {
        initializeCompanies(state);
        state.stockIndexShares.clear();
        for (String ticker : CONSTITUENTS) {
            state.stockIndexShares.put(ticker, state.companySharesOutstanding.get(ticker));
        }
        // Anchor to the saved quote, not 100: upgrades must not manufacture a return.
        state.stockIndexDivisor = capitalization(state) / positive(state.prices.get("VILX"));
        state.stockIndexStartedDay = state.economicDay;
    }

    /** Adoption never changes the saved index basket, historical quotes or player-owned shares. */
    static void initializeCompanies(EconomyState state) {
        state.companySharesOutstanding.clear();
        for(String ticker:CONSTITUENTS) state.companySharesOutstanding.put(ticker,
                CompanyProfiles.get(ticker).startingCapital()/positive(state.prices.get(ticker)));
        state.stockIndexRebalancedDay=state.economicDay;
    }

    static void review(EconomyState state) {
        if(state.economicDay-state.stockIndexRebalancedDay<365)return;
        double quote=positive(state.prices.get("VILX"));
        var remaining=new java.util.LinkedHashSet<>(CONSTITUENTS);
        var weights=new LinkedHashMap<String,Double>();double budget=1;
        while(!remaining.isEmpty()) {
            double total=remaining.stream().mapToDouble(t->positive(state.prices.get(t))
                    *positive(state.companySharesOutstanding.get(t))).sum();
            var capped=new java.util.ArrayList<String>();
            for(String t:remaining) if(budget*state.prices.get(t)*state.companySharesOutstanding.get(t)/total>.20)
                capped.add(t);
            if(capped.isEmpty()) {
                for(String t:remaining)weights.put(t,budget*state.prices.get(t)*state.companySharesOutstanding.get(t)/total);
                break;
            }
            for(String t:capped){weights.put(t,.20);budget-=.20;remaining.remove(t);}
        }
        for(String t:CONSTITUENTS)state.stockIndexShares.put(t,weights.get(t)/positive(state.prices.get(t)));
        state.stockIndexDivisor=capitalization(state)/quote;
        state.stockIndexRebalancedDay=state.economicDay;
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
            state.companySharesOutstanding.computeIfPresent(ticker,(t,shares)->positive(shares*factor));
            state.stockIndexShares.put(ticker, positive(state.stockIndexShares.get(ticker) * factor));
        } else if ("VILX".equals(ticker)) {
            state.stockIndexDivisor = positive(state.stockIndexDivisor * factor);
        }
    }

    static void validate(EconomyState state) throws IOException {
        if(!state.companySharesOutstanding.keySet().equals(new java.util.HashSet<>(CONSTITUENTS))
                ||state.stockIndexRebalancedDay<0||state.stockIndexRebalancedDay>state.economicDay)
            throw new IOException("Invalid company float/rebalance date");
        for(double shares:state.companySharesOutstanding.values())
            if(!Double.isFinite(shares)||shares<=0)throw new IOException("Invalid company float");
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
