package com.chedidandrew.emeraldstandard.minecraft;

import java.util.*;
import org.slf4j.LoggerFactory;

/** Bounded diagnostics: transitions immediately, progress buckets at most once a minute. */
final class ConstructionDiagnostics {
    private record Status(String phase, int bucket, long tick) { }
    private static final Map<String, Status> LAST = new LinkedHashMap<>();
    record Observation(String phase, int done, int total, long tick, String reason, long observedNanos) {}
    private static final Map<String, Observation> RECENT = new LinkedHashMap<>();
    private static final Map<String, Long> PROGRESS = new LinkedHashMap<>();
    static void reset() { LAST.clear(); RECENT.clear(); PROGRESS.clear(); }
    /** Called only after an actual placement batch; latest phase may already be waiting again. */
    static void progressed(String job) {
        PROGRESS.put(job, System.nanoTime());
        if (PROGRESS.size() > 2048) PROGRESS.remove(PROGRESS.keySet().iterator().next());
    }
    static Observation recent(String job, long now) {
        Observation observation = RECENT.get(job);
        return observation != null && now >= observation.tick && now-observation.tick <= 1200
                ? observation : null;
    }
    static Map<String,Object> report(String job,long now) {
        Observation observation=recent(job,now);
        if(observation==null) return Map.of("observed",false);
        return Map.of("observed",true,"phase",observation.phase(),"done",observation.done(),
                "total",observation.total(),"gameTick",observation.tick(),"reason",observation.reason());
    }
    /** Five real seconds of bounded observations, not a claim that every queued site is loaded. */
    static Map<String, Object> workloadSnapshot(long nowNanos) {
        int workingProjects = 0, workingBanks = 0, waitingProjects = 0, waitingBanks = 0;
        Map<String, Integer> phases = new TreeMap<>();
        for (var entry : RECENT.entrySet()) {
            Observation observation = entry.getValue();
            long age = nowNanos - observation.observedNanos();
            if (age < 0 || age > 5_000_000_000L
                    || observation.phase().equals("complete")
                    || observation.phase().equals("founding_home_relocated")) continue;
            String phase = observation.phase();
            boolean workingPhase = phase.equals("building") || phase.equals("repairing")
                    || phase.equals("preparing_fence") || phase.equals("terrain");
            Long progress = PROGRESS.get(entry.getKey());
            boolean progressed = progress != null && nowNanos - progress >= 0
                    && nowNanos - progress <= 5_000_000_000L;
            boolean waiting = phase.startsWith("waiting_") || phase.equals("blocked")
                    || phase.equals("protected") || phase.equals("retry_in_place")
                    || phase.equals("economic_work") || phase.equals("manual_repair");
            boolean bank = entry.getKey().startsWith("bank:");
            if (workingPhase || progressed) { if (bank) workingBanks++; else workingProjects++; }
            if (waiting) { if (bank) waitingBanks++; else waitingProjects++; }
            phases.merge(phase, 1, Integer::sum);
        }
        return Map.of("windowSeconds", 5, "trackedWorksiteLimit", 2048,
                "recentlyWorkingProjects", workingProjects, "recentlyWorkingBanks", workingBanks,
                "recentlyWaitingProjects", waitingProjects, "recentlyWaitingBanks", waitingBanks,
                "recentlyWorkingSites", workingProjects + workingBanks,
                "latestObservationsByPhase", phases);
    }

    static boolean waitingForEntities(String job) {
        Status status = LAST.get(job);
        return status != null && status.phase.equals("waiting_for_entities");
    }
    static boolean waiting(String job) {
        Status status=LAST.get(job);
        return status != null && !status.phase.equals("building") && !status.phase.equals("repairing") && !status.phase.equals("preparing_fence");
    }
    static boolean preparingFence(String job) {
        Status status = LAST.get(job);
        return status != null && status.phase.equals("preparing_fence");
    }
    static void record(String job, String phase, int done, int total, long tick, String reason) {
        // Observations refresh even when the log message is suppressed; no world scan is added.
        if (phase.equals("building")) progressed(job);
        RECENT.put(job,new Observation(phase,done,total,tick,reason,System.nanoTime()));
        if (RECENT.size()>2048) RECENT.remove(RECENT.keySet().iterator().next());
        int bucket = total <= 0 ? 0 : Math.min(10, (int) ((long) done * 10 / total));
        Status previous = LAST.get(job);
        if (previous != null && previous.phase.equals(phase)
                && (previous.bucket == bucket || tick - previous.tick < 1200)) return;
        LAST.put(job, new Status(phase, bucket, tick));
        if (LAST.size() > 2048) LAST.remove(LAST.keySet().iterator().next());
        LoggerFactory.getLogger("the_emerald_standard_construction").info(
                "Worksite {}: {} ({}/{}) {}", job, phase, done, total, reason);
    }
}
