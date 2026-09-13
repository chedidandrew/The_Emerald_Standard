package com.chedidandrew.emeraldstandard.core;

/** A bounded local building drive, not a player payout or a permanent output multiplier. */
public final class VillageStarterGrowth {
    public static final int MAX_PHYSICAL_BACKLOG = 2;

    private VillageStarterGrowth() {}

    public static double momentum(EconomyState.VillageRecord village) {
        if (village.population < 4 || village.districtFounding
                || village.lifecycle != VillageProsperityEngine.Lifecycle.ACTIVE
                || village.expansionMode == VillageExpansion.Mode.PAUSED
                || village.expansionUpkeepShortfalls > 0 || village.cityUpkeepDeficit
                || VillageGuardSecurity.effectiveSafety(village) < VillageProsperityEngine.GROWTH_SAFETY_THRESHOLD
                || village.foodSupply < village.population * VillageProsperityEngine.GROWTH_FOOD_PER_RESIDENT)
            return 0.0;
        // Serial is persistent and monotonic: deleting/relocating a project never resets the drive.
        long ordinal = village.projectSerial;
        // Let the final supported job finish at its tapered rate; do not cut its workforce
        // off halfway through simply because approving it incremented the serial to eight.
        if (village.projects.stream().anyMatch(p -> !p.economicComplete)) ordinal = Math.max(0, ordinal - 1);
        double local = Math.max(0.0, Math.min(1.0, (8.0 - ordinal) / 4.0));
        return local / Math.sqrt(Math.max(1, village.cityDistrictCount));
    }

    public static boolean hasRoom(EconomyState.VillageRecord village, boolean physical) {
        return !physical || village.projects.stream().filter(p -> !p.abstractOnly
                && !p.materializedComplete && !p.manualRepairRequired).count() < MAX_PHYSICAL_BACKLOG;
    }

    /** Locals mobilize construction supplies. Existing reserves are never reduced or multiplied. */
    static void mobilize(EconomyState.VillageRecord village, double momentum) {
        village.materialSupply += Math.min(320.0 * momentum, Math.max(0.0, 640.0 - village.materialSupply));
        village.treasury += Math.min(64.0 * momentum, Math.max(0.0, 128.0 - village.treasury));
        village.developmentPoints += Math.min(36.0 * momentum, Math.max(0.0, 72.0 - village.developmentPoints));
    }
}
