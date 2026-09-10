package com.chedidandrew.emeraldstandard.core;

/** Shared authoritative eligibility for construction and cosmetic workers. */
public final class VillageConstructionPolicy {
    private VillageConstructionPolicy() { }
    public static boolean villageEligible(EconomyState.VillageRecord village) {
        return village.expansionMode != VillageExpansion.Mode.PAUSED
                && village.lifecycle != VillageProsperityEngine.Lifecycle.EXTINCT
                && village.lifecycle != VillageProsperityEngine.Lifecycle.ABANDONED
                && (village.population > 0 || village.districtFounding);
    }
    public static boolean eligible(EconomyState.VillageRecord village, EconomyState.VillageProject project) {
        return villageEligible(village) && !project.materializedComplete
                && !project.manualRepairRequired && !project.abstractOnly;
    }
}
