package com.chedidandrew.emeraldstandard.core;

/** Rejects an execution when floating-point holdings cannot represent its value safely. */
public final class InvestmentTradeMath {
    private InvestmentTradeMath() {}
    public static double holdingAfter(double held, double quantity, double executionPrice, boolean buy) {
        if (!Double.isFinite(held) || held < 0 || !Double.isFinite(quantity) || quantity <= 0
                || !Double.isFinite(executionPrice) || executionPrice <= 0 || (!buy && quantity > held))
            return Double.NaN;
        double next = buy ? held + quantity : held - quantity;
        double changed = buy ? next - held : held - next;
        // Never debit money for a no-op purchase or credit money for a no-op sale.
        // Also reject material rounding error instead of quietly granting/destroying value.
        double errorMicro = Math.abs(changed - quantity) * executionPrice * EconomyState.MICRO;
        if (!Double.isFinite(next) || next < 0 || changed <= 0 || !Double.isFinite(errorMicro)
                || errorMicro > 0.5) return Double.NaN;
        return next;
    }
}
