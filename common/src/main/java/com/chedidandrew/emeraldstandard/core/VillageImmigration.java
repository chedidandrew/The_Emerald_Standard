package com.chedidandrew.emeraldstandard.core;

/** Saved fractional arrivals: no lottery, calendar reset or offline-only advantage. */
public final class VillageImmigration {
    public static final int MAX_PENDING = 8, MAX_DAILY_ARRIVALS = 4;
    private VillageImmigration() {}

    public static double dailyRate(EconomyState.VillageRecord v, boolean peaceful) {
        double safety = VillageGuardSecurity.effectiveSafety(v);
        int committed = v.population + v.pendingSettlers;
        if ((v.lifecycle != VillageProsperityEngine.Lifecycle.ACTIVE
                && v.lifecycle != VillageProsperityEngine.Lifecycle.RECOVERING)
                || v.expansionMode == VillageExpansion.Mode.PAUSED || v.expansionUpkeepShortfalls >= 3
                || safety < 45 || v.foodSupply < Math.max(1, committed) * 10.0) return 0;
        double prosperity = Math.max(0, Math.min(100, v.prosperity));
        double rate = (.65 + Math.min(64, Math.max(0,committed)) / 24.0)
                * (.6 + prosperity * .006) * (.65 + .35 * Math.max(0, Math.min(1,(safety-45)/55)));
        if (peaceful) rate *= 1.15;
        if (v.lifecycle == VillageProsperityEngine.Lifecycle.RECOVERING) rate *= .65;
        if (v.expansionUpkeepShortfalls > 0) rate *= .5;
        return Math.min(MAX_DAILY_ARRIVALS,rate);
    }

    public static void advance(EconomyState.VillageRecord v, long day, boolean physical, boolean peaceful) {
        if (day <= v.lastImmigrationDay) return;
        v.lastImmigrationDay = day;
        int committed = v.population + v.pendingSettlers;
        int room = Math.max(0, Math.min(VillageProsperityEngine.populationLimit(v), VillageProsperityEngine.effectiveHousingCapacity(v)) - committed);
        room = Math.min(room, Math.max(0,(int)Math.floor(v.foodSupply / 10.0) - committed));
        if (physical) room = Math.min(room, Math.max(0, MAX_PENDING - v.pendingSettlers));
        double rate = dailyRate(v,peaceful);
        if (room == 0 || rate == 0) { v.immigrationProgress = Math.min(v.immigrationProgress,.999); return; }
        double progress = v.immigrationProgress + rate;
        int arrivals = Math.min(room,Math.min(MAX_DAILY_ARRIVALS,(int)Math.floor(progress)));
        v.immigrationProgress = Math.min(.999,Math.max(0,progress-arrivals));
        if (physical) v.pendingSettlers += arrivals;
        else v.population += arrivals;
        v.foodSupply = Math.max(0,v.foodSupply - arrivals * 6.0);
    }
}
