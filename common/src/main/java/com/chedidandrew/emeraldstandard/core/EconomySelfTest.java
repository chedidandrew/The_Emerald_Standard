package com.chedidandrew.emeraldstandard.core;

/** Minimal standalone smoke test. The full suite lives under common/src/test. */
public final class EconomySelfTest {
    private EconomySelfTest() {
    }

    public static void main(String[] args) {
        long seed = 42L;
        EconomyEngine.Regime regime = EconomyEngine.initialRegime(seed);
        double price = 100.0;
        for (long day = 1L; day <= 100L * EconomyEngine.DAYS_PER_YEAR; day++) {
            regime = EconomyEngine.nextRegime(regime, seed, day);
            price *= 1.0 + EconomyEngine.marketReturn(regime, seed, day);
            if (!Double.isFinite(price) || price <= 0.0) {
                throw new AssertionError("Invalid VILX price");
            }
        }
        double cagr = Math.pow(price / 100.0, 1.0 / 100.0) - 1.0;
        if (cagr < 0.02 || cagr > 0.22) {
            throw new AssertionError("Implausible VILX CAGR: " + cagr);
        }
        System.out.printf("PASS 100-year VILX smoke test, CAGR %.2f%%%n", cagr * 100.0);
    }
}
