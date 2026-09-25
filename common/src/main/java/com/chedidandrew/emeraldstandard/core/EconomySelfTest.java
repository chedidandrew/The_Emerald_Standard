package com.chedidandrew.emeraldstandard.core;

/** Minimal standalone smoke test. The full suite lives under common/src/test. */
public final class EconomySelfTest {
    private EconomySelfTest() {
    }

    public static void main(String[] args) throws Exception {
        var state = EconomyState.fresh(42L, 0, 0);
        double startingPrice = state.prices.get("VILX");
        for (long day = 1L; day <= 100L * EconomyEngine.DAYS_PER_YEAR; day++) {
            state.advanceOneDay();
            double price = state.prices.get("VILX");
            if (!Double.isFinite(price) || price <= 0.0) {
                throw new AssertionError("Invalid VILX price");
            }
            double before = price;
            StockIndex.reprice(state);
            if (Math.abs(state.prices.get("VILX") - before) > 1e-8 * before)
                throw new AssertionError("VILX diverged from the actual company basket");
        }
        state.validate();
        // Price-only summary, not a promised return or an independent broad-market multiplier.
        double cagr = Math.pow(state.prices.get("VILX") / startingPrice, 1.0 / 100.0) - 1.0;
        System.out.printf("PASS 100-year actual VILX basket smoke test, price CAGR %.2f%%%n", cagr * 100.0);
    }
}
