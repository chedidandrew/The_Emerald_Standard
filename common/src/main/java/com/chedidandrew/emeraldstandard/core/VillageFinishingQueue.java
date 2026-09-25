package com.chedidandrew.emeraldstandard.core;

import java.util.*;

/** Session-only, disposable queue. Saved cursors remain the authority after unload/restart. */
public final class VillageFinishingQueue {
    private static final class Work {
        long refreshed = Long.MIN_VALUE;
        final Set<Long> audited = new HashSet<>();
        final ArrayDeque<Long> pending = new ArrayDeque<>();
    }
    private final Map<UUID, Work> villages = new LinkedHashMap<>(32, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<UUID, Work> entry) { return size() > 128; }
    };
    public void reset() { villages.clear(); }
    public boolean hasWork(EconomyState.VillageRecord village, long tick) {
        refresh(village, tick);
        return !villages.get(village.villageId).pending.isEmpty();
    }
    private void refresh(EconomyState.VillageRecord village, long tick) {
        Work work = villages.computeIfAbsent(village.villageId, id -> new Work());
        if (work.refreshed == Long.MIN_VALUE || tick < work.refreshed || tick - work.refreshed >= 100) {
            work.refreshed = tick;
            Set<Long> queued = new HashSet<>(work.pending);
            Set<Long> present = new HashSet<>();
            for (var p : village.projects) {
                present.add(p.projectId);
                if (eligible(p) && (unfinished(p) || !work.audited.contains(p.projectId))
                        && queued.add(p.projectId)) work.pending.add(p.projectId);
            }
            work.audited.retainAll(present);
        }
    }
    public EconomyState.VillageProject next(EconomyState.VillageRecord village, long tick) {
        refresh(village, tick);
        Work work = villages.get(village.villageId);
        while (!work.pending.isEmpty()) {
            long id = work.pending.remove();
            var p = village.projects.stream().filter(v -> v.projectId == id).findFirst().orElse(null);
            if (p == null || !eligible(p)) continue;
            work.audited.add(id);
            return p;
        }
        return null;
    }
    private static boolean eligible(EconomyState.VillageProject p) {
        return VillageArchitecture.isManagedStructureSchema(p.designSchema) && p.economicComplete
                && p.materializedComplete && !p.manualRepairRequired && !p.abstractOnly
                && p.originPos != 0 && p.trailAnchorSet;
    }
    private static boolean unfinished(EconomyState.VillageProject p) {
        return !p.trailMaterializedComplete || !p.entranceApproachComplete
                || p.entranceApproachVersion < EconomyState.ENTRANCE_APPROACH_VERSION
                || (VillageArchitecture.MODULAR_SCHEMA.equals(p.designSchema)
                    && p.trailCenterSurfaceMigrationCursor >= 0
                    && p.trailCenterSurfaceVersion < EconomyState.TRAIL_CENTER_SURFACE_VERSION);
    }
    /** Rendering/finishing context, deliberately excluding census, funds and other projects. */
    public static EconomyState.VillageRecord view(EconomyState.VillageRecord v, EconomyState.VillageProject p) {
        var view = new EconomyState.VillageRecord();
        view.villageId = v.villageId; view.centerPos = v.centerPos; view.dimensionKey = v.dimensionKey;
        view.bankAnchorPos = v.bankAnchorPos; view.developmentTier = v.developmentTier;
        view.architectureCharacter = v.architectureCharacter; view.architectureDialect = v.architectureDialect;
        view.projects.add(p); // Already a detached scheduler snapshot, not the saved authoritative record.
        return view;
    }
}
