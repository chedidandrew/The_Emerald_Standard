package com.chedidandrew.emeraldstandard.core;

import java.util.*;

/** Server-runtime catalog. Replaced atomically after validation; never consulted for a reserved plan. */
public final class VanillaBuildingCatalog {
    private static volatile List<VanillaConstructionPlan> available = List.of();
    private VanillaBuildingCatalog() {}
    public static void publish(Collection<VanillaConstructionPlan> plans) {
        available = plans.stream().sorted(Comparator.comparing(VanillaConstructionPlan::templateId)).toList();
    }
    public static void clear() { available = List.of(); }
    public static List<VanillaConstructionPlan> plans() { return available; }

    public static boolean waitingForCompatibleBuildings(EconomyState.VillageRecord village) {
        return village.vanillaOnlyBuildings && java.util.Arrays.stream(VillageProsperityEngine.ProjectType.values())
                .noneMatch(type -> supports(village, type));
    }

    public static VanillaConstructionPlan choose(EconomyState.VillageRecord village,
            VillageProsperityEngine.ProjectType type, long serial) {
        if (!matchingFamily(village) || (!village.vanillaOnlyBuildings
                && (Long.bitCount(serial * 0x9E3779B97F4A7C15L ^ village.villageId.getLeastSignificantBits()) & 1) != 0)) return null;
        var candidates = available.stream().filter(p -> eligible(village, type, p)).toList();
        if (candidates.isEmpty()) return null;
        Map<String, Long> usage = new HashMap<>();
        for (var project : village.projects) if (project.vanillaPlan != null)
            usage.merge(project.vanillaPlan.templateId(), 1L, Long::sum);
        long min = candidates.stream().mapToLong(p -> usage.getOrDefault(p.templateId(), 0L)).min().orElse(0);
        candidates = candidates.stream().filter(p -> usage.getOrDefault(p.templateId(), 0L) == min).toList();
        return candidates.get(Math.floorMod(village.villageId.hashCode() + serial * 31, candidates.size()));
    }

    /** Availability, not a probabilistic mixed-catalog roll. Used before approval or Fund release. */
    static boolean supports(EconomyState.VillageRecord village, VillageProsperityEngine.ProjectType type) {
        return matchingFamily(village) && available.stream().anyMatch(p -> eligible(village, type, p));
    }

    private static boolean matchingFamily(EconomyState.VillageRecord village) {
        return !village.naturalVillageStyle.isBlank()
                && village.naturalVillageStyle.equals(village.architectureDialect);
    }

    private static boolean eligible(EconomyState.VillageRecord village,
            VillageProsperityEngine.ProjectType type, VanillaConstructionPlan plan) {
        return plan.style().equals(village.naturalVillageStyle) && plan.eligible(type)
                && (!plan.role().equals("civic") || (village.developmentTier >= 2
                    && village.projects.stream().noneMatch(p -> p.vanillaPlan != null
                            && p.vanillaPlan.role().equals("civic"))));
    }
}
