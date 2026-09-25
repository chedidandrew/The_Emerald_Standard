package com.chedidandrew.emeraldstandard.core;

/** Shared authoritative eligibility for construction and cosmetic workers. */
public final class VillageConstructionPolicy {
    private VillageConstructionPolicy() { }
    /** Simulated completion has no outstanding physical work; explicit repairs still matter. */
    public static boolean needsAttention(EconomyState.VillageProject project) {
        return project != null && (project.manualRepairRequired || project.relocationPending
                || (!project.materializedComplete && !(project.abstractOnly && project.economicComplete)));
    }
    public static int attentionPriority(EconomyState.VillageProject project) {
        if (project.manualRepairRequired || project.relocationPending) return 0;
        return project.originPos != 0 && !project.abstractOnly ? 1 : 2;
    }
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
