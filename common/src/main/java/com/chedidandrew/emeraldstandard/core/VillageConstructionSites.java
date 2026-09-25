package com.chedidandrew.emeraldstandard.core;

import java.util.*;

/** Rebuildable presentation index: no residents, fund histories, completed lots or market copies. */
final class VillageConstructionSites {
    private EconomyState indexedState;
    private long day = -1;
    private int villageCount;
    private final Map<UUID, List<EconomyState.VillageProject>> unfinished = new LinkedHashMap<>();
    private final Map<UUID, EconomyState.VillageRecord> sourceRecords = new HashMap<>();
    private final Map<UUID, Integer> projectCounts = new HashMap<>();
    private final Set<UUID> dirty = new HashSet<>();
    void changed(UUID id) { dirty.add(id); }
    private void update(EconomyState.VillageRecord village) {
        unfinished.remove(village.villageId);
        sourceRecords.put(village.villageId, village);
        projectCounts.put(village.villageId, village.projects.size());
        var projects = village.projects.stream().filter(p -> p.originPos != 0
                && !p.materializedComplete && !p.abstractOnly).toList();
        if (!projects.isEmpty()) unfinished.put(village.villageId, projects);
    }
    List<EconomyService.ConstructionSiteSnapshot> collect(EconomyState state, String dimension) {
        if (state == null) return List.of();
        if (indexedState != state || day != state.economicDay || villageCount != state.villages.size()) {
            unfinished.clear(); sourceRecords.clear(); projectCounts.clear(); state.villages.values().forEach(this::update);
            indexedState = state; day = state.economicDay; villageCount = state.villages.size();
        } else {
            // Constant-size metadata checks also catch direct project admission, rollback and
            // reconciliation. Do not traverse historical projects unless this record changed.
            for (var village : state.villages.values()) {
                UUID id = village.villageId;
                if (sourceRecords.get(id) != village || projectCounts.getOrDefault(id, -1) != village.projects.size())
                    dirty.add(id);
            }
            for (UUID id : dirty) {
                var village = state.existingVillage(id);
                if (village == null) { unfinished.remove(id); sourceRecords.remove(id); projectCounts.remove(id); } else update(village);
            }
        }
        dirty.clear();
        var result = new ArrayList<EconomyService.ConstructionSiteSnapshot>();
        unfinished.forEach((id, projects) -> {
            var village = state.existingVillage(id);
            if (village == null || !dimension.equals(village.dimensionKey)) return;
            for (var p : projects) result.add(new EconomyService.ConstructionSiteSnapshot(
                    id, p.projectId, p.originPos, p.boundsMinPos, p.boundsMaxPos,
                    p.retryAfterGameTick, VillageConstructionPolicy.eligible(village, p), p.sitePreparationComplete));
        });
        return List.copyOf(result);
    }
}
