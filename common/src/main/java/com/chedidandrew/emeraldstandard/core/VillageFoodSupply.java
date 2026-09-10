package com.chedidandrew.emeraldstandard.core;

/** Renewable, observed food-production capacity. Never creates physical items or counts loot. */
public final class VillageFoodSupply {
    private VillageFoodSupply() { }
    public record ChunkObservation(double crops,double livestock) {
        public ChunkObservation {
            if(!Double.isFinite(crops)||!Double.isFinite(livestock)||crops<0||livestock<0||crops>1_000_000||livestock>1_000_000)
                throw new IllegalArgumentException("Invalid food observation");
        }
    }

    public static double bonus(EconomyState.VillageRecord village, long day) {
        double crops = village.observedCropUnits;
        double livestock = village.observedLivestockUnits;
        double age = Math.max(0L, day - village.lastFoodSourcesDay - 1L);
        double freshness = Math.max(0.0, 1.0 - age / 7.0);
        // Every additional source helps at equal freshness, but never exceeds +150%.
        return (crops / (crops + 96.0) + 0.5 * livestock / (livestock + 12.0)) * freshness;
    }

    public static void observe(EconomyState.VillageRecord village, double crops, double livestock, long day) {
        village.observedCropUnits = crops;
        village.observedLivestockUnits = livestock;
        village.lastFoodSourcesDay = day;
    }
}
