package com.chedidandrew.emeraldstandard.debug;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Capture-local whole tickServer timings. One bounded ring; no allocations or sorting per tick.
 * Call once after the entire wrapped tick returns, including loader END_SERVER_TICK callbacks.
 * World/game time is never used: sleep and /time cannot manufacture performance samples.
 */
public final class ServerTickMetrics {
    public static final int RECENT_TICKS = 1_200;
    private final long[] recent = new long[RECENT_TICKS];
    private int cursor, retained;
    private long count, interruptedTicks, invalidTicks, maximumNanos, over50, over100, over250;
    private double totalNanos;
    private final long startedNanos;

    public ServerTickMetrics(long nowNanos) {
        startedNanos = nowNanos;
    }

    public void record(long duration, boolean completed) {
        if (!completed) { interruptedTicks++; return; }
        if (duration <= 0) { invalidTicks++; return; }
        recent[cursor] = duration;
        cursor = (cursor + 1) % recent.length;
        retained = Math.min(recent.length, retained + 1);
        count++;
        totalNanos += duration;
        maximumNanos = Math.max(maximumNanos, duration);
        if (duration > 50_000_000L) over50++;
        if (duration > 100_000_000L) over100++;
        if (duration > 250_000_000L) over250++;
    }

    public Map<String, Object> snapshot(long nowNanos) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("source", "Monotonic wall time around tickServer including loader callbacks; excludes scheduled inter-tick sleep");
        out.put("sampledTicks", count);
        out.put("interruptedTicksExcluded", interruptedTicks);
        out.put("invalidTicksExcluded", invalidTicks);
        out.put("meanMspt", count == 0 ? null : totalNanos / count / 1_000_000.0);
        out.put("maxMspt", count == 0 ? null : maximumNanos / 1_000_000.0);
        out.put("ticksOver50Ms", over50);
        out.put("ticksOver100Ms", over100);
        out.put("ticksOver250Ms", over250);
        double seconds = Math.max(0L, nowNanos - startedNanos) / 1_000_000_000.0;
        out.put("elapsedSeconds", seconds);
        out.put("observedTicksPerSecond", count == 0 || seconds <= 0 ? null : count / seconds);
        out.put("tpsMeaning", "Completed sampled ticks / monotonic capture time; includes pauses, excludes incomplete boundary ticks; not 1000/MSPT");
        long[] sorted = Arrays.copyOf(recent, retained);
        Arrays.sort(sorted);
        Map<String, Object> window = new LinkedHashMap<>();
        window.put("sampledTicks", retained);
        window.put("capacityTicks", RECENT_TICKS);
        window.put("p50Mspt", percentile(sorted, 0.50));
        window.put("p95Mspt", percentile(sorted, 0.95));
        window.put("p99Mspt", percentile(sorted, 0.99));
        out.put("recentWindow", window);
        return out;
    }

    private static Double percentile(long[] sorted, double fraction) {
        return sorted.length == 0 ? null
                : sorted[(int) Math.ceil(sorted.length * fraction) - 1] / 1_000_000.0;
    }
}
