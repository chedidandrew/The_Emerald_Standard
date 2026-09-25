package com.chedidandrew.emeraldstandard.core;

/** A temporary observed defense modifier, never added to permanent Safety or saved as population. */
public final class VillageGuardSecurity {
    public static final int MAX_COUNT = 64;
    public static final int STALE_DAYS = 7;
    public static double effectiveSafety(EconomyState.VillageRecord v) {
        return Math.clamp(v.safety + v.guardSafetyBonus, 0, 100);
    }
    public static void observe(EconomyState.VillageRecord v, int count, int perGuard, int cap, long day) {
        v.observedGuards = Math.clamp(count, 0, MAX_COUNT);
        v.observedGuardBonus = Math.min(Math.clamp(cap, 0, 30), v.observedGuards * Math.clamp(perGuard, 0, 10));
        v.lastGuardObservationDay = Math.max(0, day);
        refresh(v, day);
    }
    public static void refresh(EconomyState.VillageRecord v, long day) {
        long age = Math.max(0, day - v.lastGuardObservationDay);
        v.guardSafetyBonus = v.observedGuardBonus * Math.clamp(1.0 - age / (double) STALE_DAYS, 0, 1);
        if (age >= STALE_DAYS) v.observedGuards = 0;
    }
    /** Repair only exact format-28 scores produced by the omitted temporary guard bonus.
     * Unrelated corruption remains untouched for normal strict validation to reject. */
    static void recoverLegacyShadow(EconomyState.VillageMarketShadow shadow, long day) {
        if (shadow.counterfactualVillage == null
                || VillageProsperityEngine.isMarketShadowCurrent(shadow, day)) return;
        var village = shadow.counterfactualVillage;
        for (int points = 1; points <= 30; points++) {
            for (int age = 0; age < STALE_DAYS; age++) {
                village.guardSafetyBonus = points * Math.clamp(1.0 - age / (double) STALE_DAYS, 0, 1);
                if (VillageProsperityEngine.isMarketShadowCurrent(shadow, day)) {
                    village.guardSafetyBonus = 0;
                    VillageProsperityEngine.refreshMarketShadow(shadow, day);
                    return;
                }
            }
        }
        village.guardSafetyBonus = 0;
    }
    private VillageGuardSecurity() {}
}
