package com.chedidandrew.emeraldstandard.core;

/** Read-only wording choices. Never changes funding, recovery dates or settler eligibility. */
public final class VillageRecoveryGuidance {
    private VillageRecoveryGuidance() {}
    public enum State { PAUSED, FUNDED, REQUIRED, OPTIONAL, WAITING }

    public static State state(VillageProsperityEngine.Lifecycle lifecycle, double fund,
            boolean recoveryEnabled, boolean fundAvailable) {
        if (!recoveryEnabled) return State.PAUSED;
        if (Double.isFinite(fund) && fund >= VillageProsperityEngine.RESTORATION_EMERALD_TARGET)
            return State.FUNDED;
        if (lifecycle == VillageProsperityEngine.Lifecycle.ABANDONED) return State.REQUIRED;
        return fundAvailable ? State.OPTIONAL : State.WAITING;
    }

    public static double remaining(double fund) {
        return Math.max(0, VillageProsperityEngine.RESTORATION_EMERALD_TARGET
                - (Double.isFinite(fund) ? Math.max(0, fund) : 0));
    }

    public static String key(State state) {
        return state.name().toLowerCase(java.util.Locale.ROOT);
    }
}
