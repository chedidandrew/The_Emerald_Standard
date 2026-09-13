package com.chedidandrew.emeraldstandard.minecraft;

import java.util.*;

/** Bounded session-only evidence from real planner checks. Reading this never surveys the world. */
public final class SiteSearchDiagnostics {
    public enum Reason {
        UNLOADED_FOOTPRINT, GROUND_OR_TERRAIN, TERRAIN_HEIGHT, BLOCKED_VOLUME,
        BANK_SPACING, ENTRANCE_UNLOADED, ENTRANCE_UNSAFE, TEMPLATE_UNLOADED,
        TEMPLATE_OBSTRUCTION, PROJECT_OVERLAP, RESERVED_SITE_OVERLAP, BANK_OVERLAP,
        PROTECTED_AREA, PREPARATION_UNSAFE, AVAILABLE
    }
    public record Trial(long tick, int sweep, int candidate, int total, int rotation,
            int x, int y, int z, Reason reason, String detail) {}
    private static final int MAX_JOBS = 256;
    private static final int MAX_TRIALS = 16;
    private static final Map<String, Evidence> JOBS = new LinkedHashMap<>();
    private static final class Evidence {
        int sweep;
        final EnumMap<Reason, Integer> counts = new EnumMap<>(Reason.class);
        final ArrayDeque<Trial> recent = new ArrayDeque<>();
        Evidence(int sweep) { this.sweep = sweep; }
    }
    private SiteSearchDiagnostics() {}
    public static void reset() { JOBS.clear(); }
    public static void record(String job, Trial trial) {
        Evidence e = JOBS.get(job);
        if (e == null || e.sweep != trial.sweep()) {
            e = new Evidence(trial.sweep()); JOBS.put(job, e);
        }
        if (JOBS.size() > MAX_JOBS) JOBS.remove(JOBS.keySet().iterator().next());
        e.counts.merge(trial.reason(), 1, (a,b) -> a == Integer.MAX_VALUE ? a : a + b);
        e.recent.addLast(trial);
        while (e.recent.size() > MAX_TRIALS) e.recent.removeFirst();
    }
    public static Trial last(String job) {
        Evidence e = JOBS.get(job); return e == null ? null : e.recent.peekLast();
    }
    public static Map<String,Object> report(String job) {
        Evidence e = JOBS.get(job);
        if (e == null) return Map.of("observed", false,
                "note", "No candidate observed this session. Keep the district active during /emerald debug.");
        var result = new LinkedHashMap<String,Object>();
        result.put("observed", true); result.put("observedSweep", e.sweep);
        var counts = new LinkedHashMap<String,Integer>();
        e.counts.forEach((key,value) -> counts.put(key.name(), value));
        result.put("orientationOutcomeCounts", counts);
        result.put("recentTrials", e.recent.stream().map(t -> {
            var row = new LinkedHashMap<String,Object>();
            row.put("gameTick", t.tick()); row.put("candidate", t.candidate()); row.put("totalCandidates", t.total());
            row.put("rotation", t.rotation()); row.put("x", t.x()); row.put("y", t.y()); row.put("z", t.z());
            row.put("reason", t.reason().name()); row.put("detail", t.detail()); return row;
        }).toList());
        result.put("note", "Observed checks only; counts are orientations, not unique sites. Previous-session causes are unknown.");
        return result;
    }
}
