package com.chedidandrew.emeraldstandard.debug;

import java.util.LinkedHashMap;
import java.util.Map;

/** Capture-local, server-thread measurements. Inclusive scopes are not additive. */
public final class WorkMeasurements {
    public static final int LIMIT = 128;
    private final Map<String, Measurement> timings = new LinkedHashMap<>();
    private final Map<String, Long> counters = new LinkedHashMap<>();
    private final Map<String, Job> jobs = new LinkedHashMap<>(32, .75f, true);
    public static final int JOB_LIMIT = 256;
    private static final class Job {
        long visits, progressBatches, lastVisit = -1, lastProgress = -1;
        String phase = "not_selected";
    }
    private static final class Measurement {
        long calls, total, maximum;
    }

    public void record(String name, long nanos) {
        if (nanos < 0 || (!timings.containsKey(name) && timings.size() >= LIMIT)) return;
        Measurement value = timings.computeIfAbsent(name, ignored -> new Measurement());
        value.calls++;
        value.total += nanos;
        value.maximum = Math.max(value.maximum, nanos);
    }

    public void count(String name) {
        if (counters.containsKey(name) || counters.size() < LIMIT) counters.merge(name, 1L, Long::sum);
    }

    public void job(String key, long tick, String phase, boolean progress) {
        Job job = jobs.computeIfAbsent(key, ignored -> new Job());
        if (jobs.size() > JOB_LIMIT) jobs.remove(jobs.keySet().iterator().next());
        job.phase = phase;
        if (progress) { job.progressBatches++; job.lastProgress = tick; }
        else { job.visits++; job.lastVisit = tick; }
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> scopes = new LinkedHashMap<>();
        timings.forEach((name, value) -> scopes.put(name, Map.of(
                "calls", value.calls, "totalMs", value.total / 1_000_000.0,
                "meanMs", value.total / (double) value.calls / 1_000_000.0,
                "maxMs", value.maximum / 1_000_000.0)));
        Map<String,Object> work = new LinkedHashMap<>();
        jobs.forEach((key,j) -> work.put(key,Map.of("visits",j.visits,"lastVisitedGameTick",j.lastVisit,
                "successfulBatches",j.progressBatches,"lastProgressGameTick",j.lastProgress,"lastSelection",j.phase)));
        return Map.of("meaning", "Inclusive server-thread wall time; nested scopes overlap, so do not sum them. Not CPU attribution. Job visits are scheduler selections, not world changes; progress counts successful batches only. Capture-local, oldest inactive jobs are evicted.",
                "timings", scopes, "counters", new LinkedHashMap<>(counters), "keyLimit", LIMIT,
                "jobs",work,"jobLimit",JOB_LIMIT);
    }
}
