package com.chedidandrew.emeraldstandard.core;

/** Renewable, observed food-production capacity. Never creates physical items or counts loot. */
public final class VillageFoodSupply {
    private VillageFoodSupply() { }
    public record ChunkObservation(double crops, double livestock, long day) {
        public ChunkObservation(double crops, double livestock) { this(crops, livestock, -1); }
        public ChunkObservation {
            if(!Double.isFinite(crops)||!Double.isFinite(livestock)||crops<0||livestock<0||crops>1_000_000||livestock>1_000_000)
                throw new IllegalArgumentException("Invalid food observation");
            if (day < -1) throw new IllegalArgumentException("Invalid food observation day");
        }
    }

    public static double bonus(EconomyState.VillageRecord village, long day) {
        double crops = village.observedCropUnits;
        double livestock = village.observedLivestockUnits;
        double freshness = freshness(village.lastFoodSourcesDay, day);
        if (!village.foodChunks.isEmpty()) {
            crops = 0; livestock = 0; freshness = 1;
            for (var sample : village.foodChunks.values()) {
                double weight = freshness(sample.day() < 0 ? village.lastFoodSourcesDay : sample.day(), day);
                crops += sample.crops() * weight;
                livestock += sample.livestock() * weight;
            }
        }
        // Every additional source helps at equal freshness, but never exceeds +150%.
        return (crops / (crops + 96.0) + 0.5 * livestock / (livestock + 12.0)) * freshness;
    }

    public static void observe(EconomyState.VillageRecord village, double crops, double livestock, long day) {
        village.foodChunks.clear();
        village.observedCropUnits = crops;
        village.observedLivestockUnits = livestock;
        village.lastFoodSourcesDay = day;
    }

    private static double freshness(long observedDay, long day) {
        return Math.max(0.0, 1.0 - Math.max(0L, day - observedDay - 1L) / 7.0);
    }

    /** Unknown chunks age independently; seeing another chunk never renews their lease. */
    public static void merge(EconomyState.VillageRecord village,
            java.util.Map<Long, ChunkObservation> samples, long day) {
        village.foodChunks.replaceAll((key, sample) -> sample.day() < 0
                ? new ChunkObservation(sample.crops(), sample.livestock(), village.lastFoodSourcesDay) : sample);
        samples.forEach((key, sample) -> {
            long sampledDay = sample.day() < 0 ? day : Math.min(day, sample.day());
            var previous = village.foodChunks.get(key);
            if (previous == null || sampledDay >= previous.day())
                village.foodChunks.put(key, new ChunkObservation(sample.crops(), sample.livestock(), sampledDay));
        });
        village.foodChunks.values().removeIf(sample -> freshness(sample.day(), day) == 0);
        village.observedCropUnits = Math.min(1_000_000, village.foodChunks.values().stream()
                .mapToDouble(s -> s.crops() * freshness(s.day(), day)).sum());
        village.observedLivestockUnits = Math.min(1_000_000, village.foodChunks.values().stream()
                .mapToDouble(s -> s.livestock() * freshness(s.day(), day)).sum());
        village.lastFoodSourcesDay = day;
    }
}
