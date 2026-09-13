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

    public static VanillaConstructionPlan choose(EconomyState.VillageRecord village,
            VillageProsperityEngine.ProjectType type, long serial) {
        if (village.naturalVillageStyle.isBlank() || !village.naturalVillageStyle.equals(village.architectureDialect)
                || (Long.bitCount(serial * 0x9E3779B97F4A7C15L ^ village.villageId.getLeastSignificantBits()) & 1) != 0) return null;
        var candidates = available.stream().filter(p -> p.style().equals(village.naturalVillageStyle)
                && p.eligible(type)
                && (!p.role().equals("civic") || (village.developmentTier >= 2
                        && village.projects.stream().noneMatch(existing -> existing.vanillaPlan != null
                                && existing.vanillaPlan.role().equals("civic"))))).toList();
        if (candidates.isEmpty()) return null;
        Map<String, Long> usage = new HashMap<>();
        for (var project : village.projects) if (project.vanillaPlan != null)
            usage.merge(project.vanillaPlan.templateId(), 1L, Long::sum);
        long min = candidates.stream().mapToLong(p -> usage.getOrDefault(p.templateId(), 0L)).min().orElse(0);
        candidates = candidates.stream().filter(p -> usage.getOrDefault(p.templateId(), 0L) == min).toList();
        return candidates.get(Math.floorMod(village.villageId.hashCode() + serial * 31, candidates.size()));
    }
}
