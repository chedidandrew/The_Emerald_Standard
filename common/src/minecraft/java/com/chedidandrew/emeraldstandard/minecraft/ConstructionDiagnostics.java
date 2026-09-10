package com.chedidandrew.emeraldstandard.minecraft;

import java.util.*;
import org.slf4j.LoggerFactory;

/** Bounded diagnostics: transitions immediately, progress buckets at most once a minute. */
final class ConstructionDiagnostics {
    private record Status(String phase, int bucket, long tick) { }
    private static final Map<String, Status> LAST = new LinkedHashMap<>();
    static void reset() { LAST.clear(); }
    static boolean waitingForEntities(String job) {
        Status status = LAST.get(job);
        return status != null && status.phase.equals("waiting_for_entities");
    }
    static boolean waiting(String job) {
        Status status=LAST.get(job);
        return status != null && !status.phase.equals("building");
    }
    static void record(String job, String phase, int done, int total, long tick, String reason) {
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
