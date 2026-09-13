package com.chedidandrew.emeraldstandard.core;

/** The same exact allocation rule powers acceptance and read-only previews. */
public final class FundAllocation {
    private FundAllocation() {}
    public static long reserve(EconomyState.ProsperityFundType type, EconomyState.DonationPurpose purpose,
            long amountMicro, double fraction) {
        if (type == null || purpose == null || amountMicro < 0 || !Double.isFinite(fraction) || fraction < 0 || fraction > .90)
            throw new IllegalArgumentException("Invalid fund allocation");
        return type == EconomyState.ProsperityFundType.DIRECT_GRANT && purpose != EconomyState.DonationPurpose.RESTORATION
            ? Math.max(0L, Math.round(amountMicro * fraction)) : 0L;
    }
}
